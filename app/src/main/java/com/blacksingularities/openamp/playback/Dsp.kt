package com.blacksingularities.openamp.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

enum class FilterType { PEAK, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS, NOTCH }

data class EqBand(val freq: Float, val gain: Float, val q: Float = 1.41f, val type: FilterType = FilterType.PEAK)

data class DspPreset(val name: String, val bands: List<EqBand>, val builtIn: Boolean = false)

data class DspState(
    val enabled: Boolean = true,
    val bands: List<EqBand> = Dsp.defaultBands(),
    val preamp: Float = 0f,
    val bass: Float = 0f,
    val treble: Float = 0f,
    val balance: Float = 0f,
    val width: Float = 1f,
    val mono: Boolean = false,
    val swap: Boolean = false,
    val limiter: Boolean = true,
    val preset: String = "Płaski",
    val userPresets: List<DspPreset> = emptyList(),
    /** Kompresor dynamiki: próg (dB) i stosunek. */
    val compressor: Boolean = false,
    val compThreshold: Float = -18f,
    val compRatio: Float = 3f,
    /** Przesłuch słuchawkowy (0–1): łagodzi skrajną stereofonię nagrań. */
    val crossfeed: Float = 0f,
    /** Automatyczne profile korektora dla słuchawek, Bluetooth i głośnika. */
    val autoProfiles: Boolean = true,
    val profile: String = "SPEAKER",
)

/**
 * Silnik dźwięku OpenAMP: 10-pasmowy korektor parametryczny (filtry dwukwadratowe RBJ),
 * półki basów i sopranów, przedwzmacniacz, balans, szerokość stereo (mid/side), mono, zamiana
 * kanałów i limiter chroniący przed przesterowaniem. Działa w torze ExoPlayera, więc nie zależy
 * od korektora systemowego telefonu.
 */
object Dsp {
    val ISO = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    const val MAX_GAIN = 15f
    const val BASS_FREQ = 90f
    const val TREBLE_FREQ = 9000f

    val BAND_COUNTS = listOf(5, 10, 15, 20, 31)

    /** Częstotliwości pasm: 10 – oktawy ISO, 31 – tercje ISO, inne – rozkład logarytmiczny. */
    fun freqsFor(n: Int): List<Float> = when (n) {
        10 -> ISO.toList()
        31 -> listOf(20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f, 200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f, 2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f)
        else -> List(n) { i -> (25.0 * Math.pow(16000.0 / 25.0, i / (n - 1.0))).toFloat().let { f -> if (f >= 1000) (Math.round(f / 100f) * 100f) else Math.round(f).toFloat() } }
    }

    /** Dobroć dopasowana do gęstości pasm (węższe przy większej liczbie pasm). */
    fun qFor(n: Int) = when { n <= 5 -> 0.9f; n <= 10 -> 1.41f; n <= 15 -> 2.1f; n <= 20 -> 2.9f; else -> 4.3f }

    fun defaultBands(n: Int = 10) = freqsFor(n).map { EqBand(it, 0f, qFor(n)) }

    /** Wartość krzywej presetu (zdefiniowanej dla 10 pasm ISO) w dowolnej częstotliwości. */
    fun curveAt(gains: List<Float>, f: Float): Float {
        if (f <= ISO.first()) return gains.first()
        if (f >= ISO.last()) return gains.last()
        val i = ISO.indexOfLast { it <= f }
        val a = kotlin.math.ln(ISO[i]); val b = kotlin.math.ln(ISO[i + 1])
        val t = (kotlin.math.ln(f) - a) / (b - a)
        return gains[i] + (gains[i + 1] - gains[i]) * t
    }

    /** Zmiana liczby pasm z zachowaniem kształtu krzywej. */
    fun setBandCount(n: Int) = update { s ->
        val bandsOnly = s.copy(preamp = 0f, bass = 0f, treble = 0f)
        val q = qFor(n)
        val newBands = freqsFor(n).map { f -> EqBand(f, (responseDb(bandsOnly, f) * 2).let { Math.round(it) / 2f }.coerceIn(-MAX_GAIN, MAX_GAIN), q) }
        s.copy(bands = newBands)
    }

    /** Filtr dla pasma (wg typu). */
    fun filterFor(b: EqBand, rate: Float): Biquad = when (b.type) {
        FilterType.PEAK -> Biquad.peaking(b.freq, b.gain, b.q, rate)
        FilterType.LOW_SHELF -> Biquad.lowShelf(b.freq, b.gain, rate)
        FilterType.HIGH_SHELF -> Biquad.highShelf(b.freq, b.gain, rate)
        FilterType.LOW_PASS -> Biquad.lowPass(b.freq, b.q, rate)
        FilterType.HIGH_PASS -> Biquad.highPass(b.freq, b.q, rate)
        FilterType.NOTCH -> Biquad.notch(b.freq, b.q, rate)
    }

    /** Czy pasmo w ogóle zmienia dźwięk (pasma z zerowym wzmocnieniem są pomijane). */
    fun isActive(b: EqBand) = when (b.type) {
        FilterType.PEAK, FilterType.LOW_SHELF, FilterType.HIGH_SHELF -> kotlin.math.abs(b.gain) > 0.05f
        else -> true
    }

    // ---------- Profile wyjścia (słuchawki / Bluetooth / głośnik / USB) ----------

    /**
     * Przełączenie profilu po zmianie urządzenia wyjściowego: bieżące ustawienia trafiają do
     * starego profilu, a ustawienia nowego (jeśli były zapisane) są wczytywane.
     */
    fun switchProfile(kind: String) {
        val cur = _state.value
        if (cur.profile == kind) return
        if (!cur.autoProfiles) { update { it.copy(profile = kind) }; return }
        if (::prefs.isInitialized) prefs.edit().putString("profile_" + cur.profile, toJson(cur).toString()).apply()
        val saved = if (::prefs.isInitialized) prefs.getString("profile_$kind", null) else null
        update { s ->
            val loaded = saved?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }
            (loaded ?: s).copy(userPresets = s.userPresets, autoProfiles = s.autoProfiles, profile = kind)
        }
    }

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(DspState())
    val state: StateFlow<DspState> = _state.asStateFlow()
    val value: DspState get() = _state.value

    @Volatile
    var version = 0L
        private set

    private fun g(vararg v: Float) = v.toList()

    /** Wbudowane krzywe (dB dla pasm ISO 31 Hz … 16 kHz). */
    val builtIn: List<Pair<String, List<Float>>> = listOf(
        "Płaski" to g(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bas+" to g(6f, 5.5f, 4.5f, 2.5f, 0.5f, 0f, 0f, 0f, 0f, 0f),
        "Głęboki bas" to g(9f, 8f, 6f, 3f, 0f, -1f, -1f, 0f, 0f, 0f),
        "Rock" to g(5f, 4f, 2.5f, 0.5f, -1f, -1f, 1f, 3f, 4f, 4.5f),
        "Pop" to g(-1f, 0.5f, 2f, 3.5f, 4f, 3f, 1.5f, 0f, -0.5f, -1f),
        "Hip-hop" to g(6f, 5.5f, 3f, 1f, -1f, -0.5f, 1f, 1f, 2f, 3f),
        "Elektroniczna" to g(5.5f, 4.5f, 1f, 0f, -2f, 1.5f, 1f, 2f, 4.5f, 5f),
        "Jazz" to g(3.5f, 2.5f, 1f, 1.5f, -1f, -1f, 0f, 1f, 2.5f, 3.5f),
        "Klasyczna" to g(4f, 3f, 2.5f, 1.5f, -1f, -1f, 0f, 2f, 3f, 3.5f),
        "Akustyczna" to g(3f, 2.5f, 2f, 1f, 1.5f, 1f, 2f, 2.5f, 2f, 1f),
        "Wokal" to g(-2f, -2.5f, -1.5f, 1f, 3.5f, 4.5f, 4f, 2f, 0.5f, -1f),
        "Podcast" to g(-4f, -3f, -1f, 1f, 3f, 4f, 3.5f, 1.5f, 0f, -2f),
        "Soprany+" to g(0f, 0f, 0f, 0f, 0f, 1f, 2.5f, 4.5f, 5.5f, 6.5f),
        "Słuchawki" to g(4f, 3f, 1.5f, 0f, -1f, -0.5f, 1f, 2.5f, 3f, 2f),
        "Głośnik telefonu" to g(-6f, -4f, -1f, 2f, 3f, 3f, 2f, 1f, 0f, -1f),
        "Nocny" to g(5f, 4f, 2f, 0.5f, 0f, 0f, 0.5f, 2f, 3.5f, 4f),
        "Ciepłe brzmienie" to g(3f, 3f, 2f, 1.5f, 1f, 0f, -1f, -2f, -2.5f, -3f),
        "Jasne brzmienie" to g(-2f, -1.5f, -1f, 0f, 0f, 1f, 2f, 3.5f, 4.5f, 5f),
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences("openamp_dsp", Context.MODE_PRIVATE)
        _state.value = runCatching { fromJson(JSONObject(prefs.getString("state", "{}")!!)) }.getOrDefault(DspState())
        version++
    }

    fun update(block: (DspState) -> DspState) {
        val next = block(_state.value)
        if (next == _state.value) return
        _state.value = next
        version++
        if (::prefs.isInitialized) prefs.edit().putString("state", toJson(next).toString()).apply()
    }

    fun setBand(i: Int, band: EqBand) = update { s ->
        s.copy(bands = s.bands.toMutableList().also { if (i in it.indices) it[i] = band.copy(gain = band.gain.coerceIn(-MAX_GAIN, MAX_GAIN)) }, preset = "Własny")
    }

    fun applyPreset(name: String) = update { s ->
        val user = s.userPresets.firstOrNull { it.name == name }
        val n = s.bands.size
        val bands = user?.bands ?: builtIn.firstOrNull { it.first == name }?.second?.let { gains ->
            freqsFor(n).map { f -> EqBand(f, (Math.round(curveAt(gains, f) * 2) / 2f), qFor(n)) }
        } ?: return@update s
        s.copy(bands = bands, preset = name, enabled = true)
    }

    fun reset() = update { it.copy(bands = defaultBands(it.bands.size), compressor = false, crossfeed = 0f, preamp = 0f, bass = 0f, treble = 0f, balance = 0f, width = 1f, mono = false, swap = false, preset = "Płaski") }

    fun savePreset(name: String) = update { s ->
        s.copy(userPresets = s.userPresets.filterNot { it.name == name } + DspPreset(name, s.bands), preset = name)
    }

    fun deletePreset(name: String) = update { s -> s.copy(userPresets = s.userPresets.filterNot { it.name == name }) }

    /** Automatyczny przedwzmacniacz: ujemny o tyle, ile wynosi największe podbicie (bez przesteru). */
    fun autoPreamp() = update { s ->
        val peak = (40..20000 step 20).maxOf { responseDb(s.copy(preamp = 0f), it.toFloat()) }
        s.copy(preamp = (-peak).coerceIn(-15f, 0f))
    }

    // ---------- Charakterystyka (do wykresu) ----------

    /** Odpowiedź całego toru w dB dla częstotliwości [f] (bez limitera). */
    fun responseDb(s: DspState, f: Float, rate: Float = 48000f): Float {
        if (!s.enabled) return 0f
        var db = s.preamp
        for (b in s.bands) if (isActive(b)) db += filterFor(b, rate).magnitudeDb(f, rate)
        if (s.bass != 0f) db += Biquad.lowShelf(BASS_FREQ, s.bass, rate).magnitudeDb(f, rate)
        if (s.treble != 0f) db += Biquad.highShelf(TREBLE_FREQ, s.treble, rate).magnitudeDb(f, rate)
        return db
    }

    fun bandResponseDb(b: EqBand, f: Float, rate: Float = 48000f) = filterFor(b, rate).magnitudeDb(f, rate)

    // ---------- JSON ----------

    private fun bandsJson(list: List<EqBand>) = JSONArray().apply {
        list.forEach { put(JSONArray().put(it.freq.toDouble()).put(it.gain.toDouble()).put(it.q.toDouble()).put(it.type.ordinal)) }
    }

    private fun bandsFrom(a: JSONArray?): List<EqBand>? = a?.let {
        List(it.length()) { i -> it.getJSONArray(i).let { b -> EqBand(b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), FilterType.entries.getOrElse(b.optInt(3, 0)) { FilterType.PEAK }) } }
    }

    private fun toJson(s: DspState) = JSONObject()
        .put("enabled", s.enabled).put("bands", bandsJson(s.bands)).put("preamp", s.preamp.toDouble())
        .put("bass", s.bass.toDouble()).put("treble", s.treble.toDouble()).put("balance", s.balance.toDouble())
        .put("width", s.width.toDouble()).put("mono", s.mono).put("swap", s.swap).put("limiter", s.limiter)
        .put("preset", s.preset)
        .put("comp", s.compressor).put("compT", s.compThreshold.toDouble()).put("compR", s.compRatio.toDouble())
        .put("xfeed", s.crossfeed.toDouble()).put("autoProf", s.autoProfiles).put("profile", s.profile)
        .put("user", JSONArray().apply { s.userPresets.forEach { put(JSONObject().put("name", it.name).put("bands", bandsJson(it.bands))) } })

    private fun fromJson(o: JSONObject): DspState {
        val d = DspState()
        val user = o.optJSONArray("user")?.let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { p -> DspPreset(p.getString("name"), bandsFrom(p.getJSONArray("bands")) ?: defaultBands()) } }
        } ?: emptyList()
        return DspState(
            enabled = o.optBoolean("enabled", d.enabled),
            bands = bandsFrom(o.optJSONArray("bands"))?.takeIf { it.size >= 3 } ?: d.bands,
            preamp = o.optDouble("preamp", 0.0).toFloat(),
            bass = o.optDouble("bass", 0.0).toFloat(),
            treble = o.optDouble("treble", 0.0).toFloat(),
            balance = o.optDouble("balance", 0.0).toFloat(),
            width = o.optDouble("width", 1.0).toFloat(),
            mono = o.optBoolean("mono", false),
            swap = o.optBoolean("swap", false),
            limiter = o.optBoolean("limiter", true),
            preset = o.optString("preset", d.preset),
            userPresets = user,
            compressor = o.optBoolean("comp", false),
            compThreshold = o.optDouble("compT", -18.0).toFloat(),
            compRatio = o.optDouble("compR", 3.0).toFloat(),
            crossfeed = o.optDouble("xfeed", 0.0).toFloat(),
            autoProfiles = o.optBoolean("autoProf", true),
            profile = o.optString("profile", "SPEAKER"),
        )
    }
}

/** Filtr dwukwadratowy (Audio EQ Cookbook, R. Bristow-Johnson), znormalizowany a0 = 1. */
class Biquad(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float) {
    fun magnitudeDb(f: Float, rate: Float): Float {
        val w = 2 * PI * f / rate
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2; val ni = -(b1 * s1 + b2 * s2)
        val dr = 1 + a1 * c1 + a2 * c2; val di = -(a1 * s1 + a2 * s2)
        val mag = sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
        return (20 * log10(mag.coerceAtLeast(1e-9))).toFloat()
    }

    companion object {
        private fun norm(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
            Biquad((b0 / a0).toFloat(), (b1 / a0).toFloat(), (b2 / a0).toFloat(), (a1 / a0).toFloat(), (a2 / a0).toFloat())

        fun peaking(f: Float, gainDb: Float, q: Float, rate: Float): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val w = 2 * PI * f.coerceIn(10f, rate * 0.45f) / rate
            val alpha = sin(w) / (2 * q.coerceAtLeast(0.1f))
            return norm(1 + alpha * a, -2 * cos(w), 1 - alpha * a, 1 + alpha / a, -2 * cos(w), 1 - alpha / a)
        }

        private fun shelf(f: Float, gainDb: Float, rate: Float, high: Boolean): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val w = 2 * PI * f.coerceIn(10f, rate * 0.45f) / rate
            val cw = cos(w)
            val alpha = sin(w) / 2 * sqrt(2.0) // S = 1
            val sa = 2 * sqrt(a) * alpha
            return if (!high) norm(
                a * ((a + 1) - (a - 1) * cw + sa), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sa),
                (a + 1) + (a - 1) * cw + sa, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sa,
            ) else norm(
                a * ((a + 1) + (a - 1) * cw + sa), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - sa),
                (a + 1) - (a - 1) * cw + sa, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sa,
            )
        }

        fun lowShelf(f: Float, gainDb: Float, rate: Float) = shelf(f, gainDb, rate, false)

        private fun wAlpha(f: Float, q: Float, rate: Float): Pair<Double, Double> {
            val w = 2 * PI * f.coerceIn(10f, rate * 0.45f) / rate
            return w to sin(w) / (2 * q.coerceAtLeast(0.1f))
        }

        fun lowPass(f: Float, q: Float, rate: Float): Biquad {
            val (w, al) = wAlpha(f, q, rate); val c = cos(w)
            return norm((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
        }

        fun highPass(f: Float, q: Float, rate: Float): Biquad {
            val (w, al) = wAlpha(f, q, rate); val c = cos(w)
            return norm((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
        }

        fun notch(f: Float, q: Float, rate: Float): Biquad {
            val (w, al) = wAlpha(f, q, rate); val c = cos(w)
            return norm(1.0, -2 * c, 1.0, 1 + al, -2 * c, 1 - al)
        }
        fun highShelf(f: Float, gainDb: Float, rate: Float) = shelf(f, gainDb, rate, true)
    }
}

class DspProcessor : BaseAudioProcessor() {
    private var channels = 2
    private var rate = 44100
    private var float = false
    private var seen = -1L
    private var filters: Array<Biquad> = emptyArray()
    // Stan filtrów: [kanał][filtr][z1, z2]
    private var z: Array<Array<FloatArray>> = emptyArray()
    private var snapshot = DspState()
    private var preGain = 1f
    private var gainL = 1f
    private var gainR = 1f
    private var limGain = 1f
    private var compEnv = 0f
    private var xfL = 0f
    private var xfR = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        // Nieobsługiwany format (np. 24/32-bit PCM z FLAC hi-res) – przepuszczamy bez obróbki zamiast blokować odtwarzanie.
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        float = enc == C.ENCODING_PCM_FLOAT
        seen = -1
        return inputAudioFormat
    }

    private fun refresh() {
        if (seen == Dsp.version) return
        seen = Dsp.version
        val s = Dsp.value
        snapshot = s
        val r = rate.toFloat()
        val list = ArrayList<Biquad>()
        if (s.enabled) {
            s.bands.filter { Dsp.isActive(it) }.forEach { list += Dsp.filterFor(it, r) }
            if (abs(s.bass) > 0.05f) list += Biquad.lowShelf(Dsp.BASS_FREQ, s.bass, r)
            if (abs(s.treble) > 0.05f) list += Biquad.highShelf(Dsp.TREBLE_FREQ, s.treble, r)
        }
        if (list.size != filters.size || z.size != channels) {
            z = Array(channels) { Array(list.size) { FloatArray(2) } }
        }
        filters = list.toTypedArray()
        preGain = if (s.enabled) 10f.pow(s.preamp / 20f) else 1f
        val bal = if (s.enabled) s.balance else 0f
        gainL = if (bal > 0) 1f - bal else 1f
        gainR = if (bal < 0) 1f + bal else 1f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        refresh()
        val s = snapshot
        val src = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val out = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)
        val bytes = if (float) 4 else 2
        val frames = remaining / (bytes * channels)
        val frame = FloatArray(channels)
        val active = s.enabled
        val stereo = channels == 2
        for (n in 0 until frames) {
            for (c in 0 until channels) frame[c] = if (float) src.float else src.short / 32768f
            if (active) {
                for (c in 0 until channels) {
                    var x = frame[c] * preGain
                    val st = z[c]
                    for (k in filters.indices) {
                        val f = filters[k]
                        val zz = st[k]
                        val y = f.b0 * x + zz[0]
                        zz[0] = f.b1 * x - f.a1 * y + zz[1]
                        zz[1] = f.b2 * x - f.a2 * y
                        x = y
                    }
                    frame[c] = x
                }
                if (stereo) {
                    var l = frame[0]; var r = frame[1]
                    if (s.swap) { val t = l; l = r; r = t }
                    if (s.mono) { val m = (l + r) * 0.5f; l = m; r = m }
                    else if (s.width != 1f) {
                        val m = (l + r) * 0.5f
                        val sd = (l - r) * 0.5f * s.width
                        l = m + sd; r = m - sd
                    }
                    if (s.crossfeed > 0f) {
                        // Filtr dolnoprzepustowy ~700 Hz na sygnale z przeciwnego kanału.
                        val k = 1f - kotlin.math.exp(-2f * PI.toFloat() * 700f / rate)
                        xfL += (l - xfL) * k; xfR += (r - xfR) * k
                        val g = s.crossfeed * 0.45f
                        val nl = (l + xfR * g) / (1f + g); val nr = (r + xfL * g) / (1f + g)
                        l = nl; r = nr
                    }
                    frame[0] = l * gainL; frame[1] = r * gainR
                }
                if (s.compressor) {
                    var peak = 0f
                    for (c in 0 until channels) peak = maxOf(peak, abs(frame[c]))
                    // Detektor obwiedni: atak ~5 ms, powrót ~120 ms.
                    val att = 1f - kotlin.math.exp(-1f / (rate * 0.005f))
                    val rel = 1f - kotlin.math.exp(-1f / (rate * 0.12f))
                    compEnv += (peak - compEnv) * (if (peak > compEnv) att else rel)
                    val envDb = 20f * log10(compEnv.coerceAtLeast(1e-6f))
                    val over = envDb - s.compThreshold
                    if (over > 0f) {
                        val gainDb = -over * (1f - 1f / s.compRatio.coerceAtLeast(1f))
                        val makeup = -s.compThreshold * (1f - 1f / s.compRatio.coerceAtLeast(1f)) * 0.5f
                        val g = 10f.pow((gainDb + makeup) / 20f)
                        for (c in 0 until channels) frame[c] *= g
                    } else {
                        val makeup = -s.compThreshold * (1f - 1f / s.compRatio.coerceAtLeast(1f)) * 0.5f
                        val g = 10f.pow(makeup / 20f)
                        for (c in 0 until channels) frame[c] *= g
                    }
                }
                if (s.limiter) {
                    var peak = 0f
                    for (c in 0 until channels) peak = maxOf(peak, abs(frame[c]))
                    val ceiling = 0.97f
                    if (peak * limGain > ceiling) limGain = ceiling / peak
                    for (c in 0 until channels) frame[c] *= limGain
                    // Powrót wzmocnienia ~150 ms.
                    limGain += (1f - limGain) * (1f / (rate * 0.15f))
                }
            }
            for (c in 0 until channels) {
                val v = frame[c]
                if (float) out.putFloat(v)
                else {
                    val clipped = if (abs(v) > 0.99f) tanh(v.toDouble()).toFloat() else v
                    out.putShort((clipped * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
                }
            }
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    override fun onFlush() {
        z.forEach { ch -> ch.forEach { it.fill(0f) } }
        limGain = 1f
    }
}
