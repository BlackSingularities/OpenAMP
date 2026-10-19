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

data class EqBand(val freq: Float, val gain: Float, val q: Float = 1.41f)

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

    fun defaultBands() = ISO.map { EqBand(it, 0f) }

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
        val bands = user?.bands ?: builtIn.firstOrNull { it.first == name }?.second?.mapIndexed { i, gain -> EqBand(ISO[i], gain) } ?: return@update s
        s.copy(bands = bands, preset = name, enabled = true)
    }

    fun reset() = update { it.copy(bands = defaultBands(), preamp = 0f, bass = 0f, treble = 0f, balance = 0f, width = 1f, mono = false, swap = false, preset = "Płaski") }

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
        for (b in s.bands) if (b.gain != 0f) db += Biquad.peaking(b.freq, b.gain, b.q, rate).magnitudeDb(f, rate)
        if (s.bass != 0f) db += Biquad.lowShelf(BASS_FREQ, s.bass, rate).magnitudeDb(f, rate)
        if (s.treble != 0f) db += Biquad.highShelf(TREBLE_FREQ, s.treble, rate).magnitudeDb(f, rate)
        return db
    }

    fun bandResponseDb(b: EqBand, f: Float, rate: Float = 48000f) = Biquad.peaking(b.freq, b.gain, b.q, rate).magnitudeDb(f, rate)

    // ---------- JSON ----------

    private fun bandsJson(list: List<EqBand>) = JSONArray().apply {
        list.forEach { put(JSONArray().put(it.freq.toDouble()).put(it.gain.toDouble()).put(it.q.toDouble())) }
    }

    private fun bandsFrom(a: JSONArray?): List<EqBand>? = a?.let {
        List(it.length()) { i -> it.getJSONArray(i).let { b -> EqBand(b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat()) } }
    }

    private fun toJson(s: DspState) = JSONObject()
        .put("enabled", s.enabled).put("bands", bandsJson(s.bands)).put("preamp", s.preamp.toDouble())
        .put("bass", s.bass.toDouble()).put("treble", s.treble.toDouble()).put("balance", s.balance.toDouble())
        .put("width", s.width.toDouble()).put("mono", s.mono).put("swap", s.swap).put("limiter", s.limiter)
        .put("preset", s.preset)
        .put("user", JSONArray().apply { s.userPresets.forEach { put(JSONObject().put("name", it.name).put("bands", bandsJson(it.bands))) } })

    private fun fromJson(o: JSONObject): DspState {
        val d = DspState()
        val user = o.optJSONArray("user")?.let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { p -> DspPreset(p.getString("name"), bandsFrom(p.getJSONArray("bands")) ?: defaultBands()) } }
        } ?: emptyList()
        return DspState(
            enabled = o.optBoolean("enabled", d.enabled),
            bands = bandsFrom(o.optJSONArray("bands"))?.takeIf { it.size == ISO.size } ?: d.bands,
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

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
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
            s.bands.filter { abs(it.gain) > 0.05f }.forEach { list += Biquad.peaking(it.freq, it.gain, it.q, r) }
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
                    frame[0] = l * gainL; frame[1] = r * gainR
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
