package com.blacksingularities.openamp.playback

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class FilterType { PEAK, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS, NOTCH }

data class EqBand(val freq: Float, val gain: Float, val q: Float = 1.41f, val type: FilterType = FilterType.PEAK)

data class DspPreset(val name: String, val bands: List<EqBand>, val preamp: Float = 0f)

/** Wbudowany preset: krzywa dla 10 pasm ISO (31 Hz … 16 kHz) i grupa, w której pokazuje go ekran presetów. */
data class BuiltInPreset(val name: String, val group: PresetGroup, val gains: List<Float>)

enum class PresetGroup { TONE, GENRE, DEVICE }

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
    /**
     * Automatyczny zapas: tor ścisza się o tyle, ile wynosi największe podbicie krzywej. Bez tego
     * każde podbicie głośnego nagrania wpada w limiter i zamiast „więcej basu” słychać pompowanie.
     */
    val autoGain: Boolean = true,
    val preset: String = "Płaski",
    val userPresets: List<DspPreset> = emptyList(),
    /** Kompresor dynamiki: próg (dB) i stosunek. */
    val compressor: Boolean = false,
    val compThreshold: Float = -18f,
    val compRatio: Float = 3f,
    /** Przesłuch słuchawkowy (0–1): łagodzi skrajną stereofonię nagrań. */
    val crossfeed: Float = 0f,
    /** Osobne ustawienia dla każdego wyjścia (słuchawki, Bluetooth, USB, głośnik). */
    val autoProfiles: Boolean = true,
    /** Bieżące wyjście dźwięku – aktualizowane zawsze, profil ładowany tylko przy [autoProfiles]. */
    val profile: String = "SPEAKER",
    /** Wyrównanie głośności według znaczników ReplayGain i dodatkowe wzmocnienie dla niego (dB). */
    val replayGain: ReplayGainMode = ReplayGainMode.OFF,
    val rgPreamp: Float = 0f,
)

/** ReplayGain: wyłączony, według utworu albo albumu (gdy brak znacznika – ten drugi). */
enum class ReplayGainMode { OFF, TRACK, ALBUM }

/** Odczyty z procesora na żywo – dla mierników na ekranie korektora (bez wpływu na dźwięk). */
object DspMeter {
    @Volatile var peakL = 0f
    @Volatile var peakR = 0f
    /** Bieżące ściszenie przez kompresor i limiter (dB, ≤ 0). */
    @Volatile var reductionDb = 0f
    @Volatile var sampleRate = 0
    @Volatile var channels = 0
    /** Format wejścia procesora: „16 bit”, „24 bit”, „32 bit”, „32 bit float”. */
    @Volatile var inputFormat = ""
    /** Czy wynik idzie do AudioTrack jako float (tor hi-res), czy 16 bit z ditheringiem. */
    @Volatile var floatOut = false
    /** Liczba filtrów aktywnych w torze (0 – korektor płaski albo wyłączony). */
    @Volatile var filters = 0
    /** Chwila ostatniego przetworzonego bufora (System.nanoTime) – czy silnik w ogóle dostaje dźwięk. */
    @Volatile var lastBufferAt = 0L

    /** Czy procesor przetwarzał dźwięk w ciągu ostatniej sekundy. */
    fun live(now: Long = System.nanoTime()) = lastBufferAt != 0L && now - lastBufferAt < 1_000_000_000L
}

/**
 * Silnik dźwięku OpenAMP – stan i jego zapis. Korektor parametryczny (dowolna liczba pasm, filtry
 * dwukwadratowe RBJ), półki basów i sopranów, przedwzmacniacz z automatycznym zapasem, balans,
 * szerokość stereo, mono, zamiana kanałów, crossfeed, kompresor i limiter. Samo przetwarzanie
 * robi [DspProcessor] w torze ExoPlayera, więc nie zależy od korektora systemowego telefonu.
 */
object Dsp {
    val ISO = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    const val MAX_GAIN = 15f
    const val MAX_PREAMP = 15f
    const val MAX_BANDS = 31
    const val F_MIN = 20f
    const val F_MAX = 20000f
    const val BASS_FREQ = 90f
    const val TREBLE_FREQ = 9000f
    /** Dobroć półek basów i sopranów (S = 1). */
    const val SHELF_Q = 0.707f
    /** Częstotliwość, dla której liczona jest charakterystyka na wykresie i zapas. */
    private const val REF_RATE = 48000f

    val BAND_COUNTS = listOf(5, 10, 15, 20, 31)
    val OUTPUTS = listOf("SPEAKER", "HEADPHONES", "BLUETOOTH", "USB")

    /** Częstotliwości pasm: 10 – oktawy ISO, 31 – tercje ISO, inne – rozkład logarytmiczny. */
    fun freqsFor(n: Int): List<Float> = when (n) {
        10 -> ISO.toList()
        31 -> listOf(20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f, 200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f, 2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f)
        1 -> listOf(1000f)
        else -> List(n) { i -> (25.0 * Math.pow(16000.0 / 25.0, i / (n - 1.0))).toFloat().let { f -> if (f >= 1000) (Math.round(f / 100f) * 100f) else Math.round(f).toFloat() } }
    }

    /** Dobroć dopasowana do gęstości pasm (węższe przy większej liczbie pasm). */
    fun qFor(n: Int) = when { n <= 5 -> 0.9f; n <= 10 -> 1.41f; n <= 15 -> 2.1f; n <= 20 -> 2.9f; else -> 4.3f }

    fun defaultBands(n: Int = 10) = freqsFor(n).map { EqBand(it, 0f, qFor(n)) }

    /** Wartość krzywej zdefiniowanej w punktach [freqs] → [gains] w dowolnej częstotliwości (interpolacja w skali log). */
    fun curveAt(freqs: List<Float>, gains: List<Float>, f: Float): Float {
        if (freqs.isEmpty()) return 0f
        if (f <= freqs.first()) return gains.first()
        if (f >= freqs.last()) return gains.last()
        val i = freqs.indexOfLast { it <= f }
        val a = ln(freqs[i]); val b = ln(freqs[i + 1])
        val t = (ln(f) - a) / (b - a)
        return gains[i] + (gains[i + 1] - gains[i]) * t
    }

    fun curveAt(gains: List<Float>, f: Float) = curveAt(ISO.toList(), gains, f)

    /** Pasma presetu wbudowanego dla [n] pasm (krzywa przeliczona z 10 pasm ISO). */
    fun presetBands(gains: List<Float>, n: Int): List<EqBand> =
        freqsFor(n).map { f -> EqBand(f, Math.round(curveAt(gains, f) * 2) / 2f, qFor(n)) }

    /** Filtr dla pasma (wg typu). */
    fun filterFor(b: EqBand, rate: Float): Biquad = when (b.type) {
        FilterType.PEAK -> Biquad.peaking(b.freq, b.gain, b.q, rate)
        FilterType.LOW_SHELF -> Biquad.lowShelf(b.freq, b.gain, b.q, rate)
        FilterType.HIGH_SHELF -> Biquad.highShelf(b.freq, b.gain, b.q, rate)
        FilterType.LOW_PASS -> Biquad.lowPass(b.freq, b.q, rate)
        FilterType.HIGH_PASS -> Biquad.highPass(b.freq, b.q, rate)
        FilterType.NOTCH -> Biquad.notch(b.freq, b.q, rate)
    }

    /** Czy pasmo w ogóle zmienia dźwięk (pasma z zerowym wzmocnieniem są pomijane). */
    fun isActive(b: EqBand) = when (b.type) {
        FilterType.PEAK, FilterType.LOW_SHELF, FilterType.HIGH_SHELF -> abs(b.gain) > 0.05f
        else -> true
    }

    /** Czy typ filtra ma wzmocnienie (filtry przepustowe i wycięcie go nie mają). */
    fun hasGain(t: FilterType) = t == FilterType.PEAK || t == FilterType.LOW_SHELF || t == FilterType.HIGH_SHELF

    private fun g(vararg v: Float) = v.toList()

    /** Wbudowane krzywe (dB dla pasm ISO 31 Hz … 16 kHz). Nazwy są kluczami tłumaczeń. */
    val builtIn: List<BuiltInPreset> = listOf(
        BuiltInPreset("Płaski", PresetGroup.TONE, g(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        BuiltInPreset("Bas+", PresetGroup.TONE, g(6f, 5.5f, 4.5f, 2.5f, 0.5f, 0f, 0f, 0f, 0f, 0f)),
        BuiltInPreset("Głęboki bas", PresetGroup.TONE, g(9f, 8f, 6f, 3f, 0f, -1f, -1f, 0f, 0f, 0f)),
        BuiltInPreset("Soprany+", PresetGroup.TONE, g(0f, 0f, 0f, 0f, 0f, 1f, 2.5f, 4.5f, 5.5f, 6.5f)),
        BuiltInPreset("Ciepłe brzmienie", PresetGroup.TONE, g(3f, 3f, 2f, 1.5f, 1f, 0f, -1f, -2f, -2.5f, -3f)),
        BuiltInPreset("Jasne brzmienie", PresetGroup.TONE, g(-2f, -1.5f, -1f, 0f, 0f, 1f, 2f, 3.5f, 4.5f, 5f)),
        BuiltInPreset("Wokal", PresetGroup.TONE, g(-2f, -2.5f, -1.5f, 1f, 3.5f, 4.5f, 4f, 2f, 0.5f, -1f)),
        BuiltInPreset("Nocny", PresetGroup.TONE, g(5f, 4f, 2f, 0.5f, 0f, 0f, 0.5f, 2f, 3.5f, 4f)),
        BuiltInPreset("Rock", PresetGroup.GENRE, g(5f, 4f, 2.5f, 0.5f, -1f, -1f, 1f, 3f, 4f, 4.5f)),
        BuiltInPreset("Pop", PresetGroup.GENRE, g(-1f, 0.5f, 2f, 3.5f, 4f, 3f, 1.5f, 0f, -0.5f, -1f)),
        BuiltInPreset("Hip-hop", PresetGroup.GENRE, g(6f, 5.5f, 3f, 1f, -1f, -0.5f, 1f, 1f, 2f, 3f)),
        BuiltInPreset("Elektroniczna", PresetGroup.GENRE, g(5.5f, 4.5f, 1f, 0f, -2f, 1.5f, 1f, 2f, 4.5f, 5f)),
        BuiltInPreset("Jazz", PresetGroup.GENRE, g(3.5f, 2.5f, 1f, 1.5f, -1f, -1f, 0f, 1f, 2.5f, 3.5f)),
        BuiltInPreset("Klasyczna", PresetGroup.GENRE, g(4f, 3f, 2.5f, 1.5f, -1f, -1f, 0f, 2f, 3f, 3.5f)),
        BuiltInPreset("Akustyczna", PresetGroup.GENRE, g(3f, 2.5f, 2f, 1f, 1.5f, 1f, 2f, 2.5f, 2f, 1f)),
        BuiltInPreset("Słuchawki", PresetGroup.DEVICE, g(4f, 3f, 1.5f, 0f, -1f, -0.5f, 1f, 2.5f, 3f, 2f)),
        BuiltInPreset("Głośnik telefonu", PresetGroup.DEVICE, g(-6f, -4f, -1f, 2f, 3f, 3f, 2f, 1f, 0f, -1f)),
        BuiltInPreset("Podcast", PresetGroup.DEVICE, g(-4f, -3f, -1f, 1f, 3f, 4f, 3.5f, 1.5f, 0f, -2f)),
    )

    // ---------- Stan ----------

    private var prefs: SharedPreferences? = null
    private var handler: Handler? = null
    private val _state = MutableStateFlow(DspState())
    val state: StateFlow<DspState> = _state.asStateFlow()
    val value: DspState get() = _state.value

    private val versionCounter = AtomicLong()
    /** Numer wersji ustawień – procesor przelicza filtry, gdy się zmieni (zmiany idą z UI i z wątku audio). */
    val version: Long get() = versionCounter.get()

    private fun bump() { versionCounter.incrementAndGet() }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("openamp_dsp", Context.MODE_PRIVATE)
        handler = Handler(Looper.getMainLooper())
        _state.value = runCatching { fromJson(JSONObject(prefs!!.getString("state", "{}")!!)) }.getOrDefault(DspState())
        bump()
    }

    /** Zapis z opóźnieniem – przeciąganie suwaka nie pisze JSON-a w każdej klatce. */
    private val saver = Runnable { saveNow() }

    private fun saveNow() {
        prefs?.edit()?.putString("state", toJson(_state.value).toString())?.apply()
    }

    private fun scheduleSave() {
        val h = handler ?: return
        h.removeCallbacks(saver)
        h.postDelayed(saver, 400)
    }

    fun update(block: (DspState) -> DspState) {
        val next = block(_state.value)
        if (next == _state.value) return
        _state.value = next
        bump()
        scheduleSave()
    }

    // ---------- Historia (cofanie) ----------

    private val history = ArrayDeque<DspState>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    /** Punkt cofania: wywoływany na początku gestu albo przed zmianą jednym dotknięciem. */
    fun checkpoint() {
        val s = _state.value
        if (history.lastOrNull() == s) return
        history.addLast(s)
        while (history.size > 50) history.removeFirst()
        _canUndo.value = true
    }

    fun undo() {
        val prev = history.removeLastOrNull() ?: return
        val cur = _state.value
        // Cofanie dotyczy brzmienia – zapisane presety, wyjście i ReplayGain zostają.
        update { prev.copy(userPresets = cur.userPresets, profile = cur.profile, autoProfiles = cur.autoProfiles, replayGain = cur.replayGain, rgPreamp = cur.rgPreamp) }
        _canUndo.value = history.isNotEmpty()
    }

    private fun clearHistory() {
        history.clear()
        _canUndo.value = false
    }

    // ---------- Pasma i presety ----------

    private fun sanitize(b: EqBand) = b.copy(
        freq = b.freq.coerceIn(F_MIN, F_MAX),
        gain = b.gain.coerceIn(-MAX_GAIN, MAX_GAIN),
        q = b.q.coerceIn(0.1f, 16f),
    )

    fun setBand(i: Int, band: EqBand) = update { s ->
        if (i !in s.bands.indices) return@update s
        s.copy(bands = s.bands.toMutableList().also { it[i] = sanitize(band) }, preset = CUSTOM)
    }

    fun setBands(bands: List<EqBand>, preamp: Float? = null, name: String = CUSTOM) = update { s ->
        val list = bands.take(MAX_BANDS).map(::sanitize).sortedBy { it.freq }
        if (list.isEmpty()) return@update s
        s.copy(bands = list, preamp = (preamp ?: s.preamp).coerceIn(-MAX_PREAMP, MAX_PREAMP), preset = name, enabled = true)
    }

    /** Zmiana liczby pasm z zachowaniem kształtu krzywej (półki i filtry przepustowe zamieniane na pasma). */
    fun setBandCount(n: Int) {
        checkpoint()
        update { s ->
            val q = qFor(n)
            val newBands = freqsFor(n).map { f -> EqBand(f, (bandsDb(s.bands, f) * 2).let { Math.round(it) / 2f }.coerceIn(-MAX_GAIN, MAX_GAIN), q) }
            s.copy(bands = newBands)
        }
    }

    /** Wyzerowanie wzmocnień przy zachowaniu częstotliwości i dobroci pasm. */
    fun flatten() {
        checkpoint()
        update { s -> s.copy(bands = s.bands.map { if (hasGain(it.type)) it.copy(gain = 0f) else it }, preset = "Płaski") }
    }

    /** [undoable] – false dla presetów dobieranych samoczynnie (wg gatunku), żeby nie zaśmiecały historii. */
    fun applyPreset(name: String, undoable: Boolean = true) {
        val s0 = _state.value
        val user = s0.userPresets.firstOrNull { it.name == name }
        val builtin = builtIn.firstOrNull { it.name == name }
        if (user == null && builtin == null) return
        if (undoable) checkpoint()
        update { s ->
            if (user != null) s.copy(bands = user.bands, preamp = user.preamp, preset = name, enabled = true)
            else s.copy(bands = presetBands(builtin!!.gains, s.bands.size.takeIf { it in BAND_COUNTS } ?: 10), preamp = 0f, preset = name, enabled = true)
        }
    }

    /** Pasma presetu do miniatury i porównania (wbudowany – w bieżącej liczbie pasm). */
    fun bandsOf(name: String, s: DspState = value): List<EqBand>? =
        s.userPresets.firstOrNull { it.name == name }?.bands
            ?: builtIn.firstOrNull { it.name == name }?.let { presetBands(it.gains, s.bands.size.takeIf { n -> n in BAND_COUNTS } ?: 10) }

    fun reset() {
        checkpoint()
        update {
            it.copy(
                bands = defaultBands(it.bands.size.takeIf { n -> n in BAND_COUNTS } ?: 10), compressor = false, crossfeed = 0f, preamp = 0f,
                bass = 0f, treble = 0f, balance = 0f, width = 1f, mono = false, swap = false, preset = "Płaski", enabled = true,
            )
        }
    }

    /**
     * Import z tekstu ([EqText]). Przy automatycznym zapasie przedwzmacniacz z pliku jest pomijany –
     * zapas liczy się sam, a oba razem ściszyłyby dźwięk dwa razy. False – w tekście nie ma filtrów.
     */
    fun importText(text: String): Boolean {
        val p = EqText.parse(text, value.bands.size) ?: return false
        checkpoint()
        setBands(p.bands, if (value.autoGain) 0f else (p.preamp ?: 0f), IMPORTED)
        return true
    }

    fun savePreset(name: String) = update { s ->
        s.copy(userPresets = s.userPresets.filterNot { it.name == name } + DspPreset(name, s.bands, s.preamp), preset = name)
    }

    fun renamePreset(old: String, new: String) = update { s ->
        if (new.isBlank() || s.userPresets.none { it.name == old }) return@update s
        s.copy(
            userPresets = s.userPresets.filterNot { it.name == new }.map { if (it.name == old) it.copy(name = new) else it },
            preset = if (s.preset == old) new else s.preset,
        )
    }

    fun deletePreset(name: String) = update { s -> s.copy(userPresets = s.userPresets.filterNot { it.name == name }) }

    // ---------- Profile wyjścia (słuchawki / Bluetooth / głośnik / USB) ----------

    /**
     * Zmiana urządzenia wyjściowego. Przy włączonych profilach bieżące ustawienia trafiają do
     * starego profilu, a ustawienia nowego (jeśli były zapisane) są wczytywane; bez profili
     * zapamiętywane jest tylko, które wyjście gra.
     */
    fun switchProfile(kind: String) {
        val cur = _state.value
        if (cur.profile == kind) return
        if (!cur.autoProfiles) { update { it.copy(profile = kind) }; return }
        prefs?.edit()?.putString("profile_" + cur.profile, toJson(cur).toString())?.apply()
        val loaded = prefs?.getString("profile_$kind", null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }
        clearHistory()
        // ReplayGain i presety są wspólne dla wszystkich wyjść – nie przełączają się z profilem.
        update { s -> (loaded ?: s).copy(userPresets = s.userPresets, autoProfiles = true, profile = kind, replayGain = s.replayGain, rgPreamp = s.rgPreamp) }
        saveNow()
    }

    fun setAutoProfiles(on: Boolean) {
        val cur = _state.value
        if (cur.autoProfiles == on) return
        // Po włączeniu bieżące ustawienia stają się profilem obecnego wyjścia.
        if (on) prefs?.edit()?.putString("profile_" + cur.profile, toJson(cur).toString())?.apply()
        update { it.copy(autoProfiles = on) }
    }

    /** Bieżące ustawienia jako profil każdego wyjścia. */
    fun copyToAllProfiles() {
        val cur = _state.value
        val e = prefs?.edit() ?: return
        OUTPUTS.forEach { e.putString("profile_$it", toJson(cur.copy(profile = it)).toString()) }
        e.apply()
    }

    /** Czy dla wyjścia [kind] są zapisane osobne ustawienia. */
    fun hasProfile(kind: String) = prefs?.contains("profile_$kind") == true

    // ---------- Porównanie A/B i ReplayGain ----------

    /** Porównanie A/B: przytrzymanie przycisku chwilowo omija korektor i efekty (bez zapisu). */
    @Volatile
    var bypass = false
        private set

    fun setBypass(on: Boolean) {
        if (bypass == on) return
        bypass = on
        bump()
    }

    /** Znaczniki ReplayGain bieżącego utworu (dB) – ustawiane przez serwis odtwarzania. */
    @Volatile var rgTrack: Float? = null
        private set
    @Volatile var rgAlbum: Float? = null
        private set

    fun setReplayGain(track: Float?, album: Float?) {
        if (track == rgTrack && album == rgAlbum) return
        rgTrack = track; rgAlbum = album
        bump()
    }

    /** ReplayGain następnego utworu, czekający na początek jego dźwięku w procesorze. */
    @Volatile private var stagedRg: Pair<Float?, Float?>? = null

    /** Znaczniki utworu, który właśnie trafia na wyjście – zaczną obowiązywać przy najbliższej granicy w procesorze. */
    fun stageReplayGain(track: Float?, album: Float?) {
        stagedRg = track to album
    }

    /** Wywoływane przez procesor na granicy strumienia (flush): od tej próbki gra nowy utwór. */
    fun commitReplayGain() {
        val rg = stagedRg ?: return
        stagedRg = null
        setReplayGain(rg.first, rg.second)
    }

    /** Wzmocnienie ReplayGain w dB dla ustawień [s] (0, gdy wyłączone albo utwór nie ma znaczników). */
    fun replayGainDb(s: DspState): Float {
        val tag = when (s.replayGain) {
            ReplayGainMode.OFF -> return 0f
            ReplayGainMode.TRACK -> rgTrack ?: rgAlbum
            ReplayGainMode.ALBUM -> rgAlbum ?: rgTrack
        } ?: return 0f
        return (tag + s.rgPreamp).coerceIn(-24f, 12f)
    }

    // ---------- Charakterystyka (wykres, zapas) ----------

    /** Suma pasm korektora w dB dla częstotliwości [f]. */
    fun bandsDb(bands: List<EqBand>, f: Float, rate: Float = REF_RATE): Float {
        var db = 0f
        for (b in bands) if (isActive(b)) db += filterFor(b, rate).magnitudeDb(f, rate)
        return db
    }

    /** Kształt krzywej: pasma oraz półki basów i sopranów – bez przedwzmacniacza i zapasu. */
    fun eqDb(s: DspState, f: Float, rate: Float = REF_RATE): Float {
        if (!s.enabled) return 0f
        var db = bandsDb(s.bands, f, rate)
        if (abs(s.bass) > 0.05f) db += Biquad.lowShelf(BASS_FREQ, s.bass, SHELF_Q, rate).magnitudeDb(f, rate)
        if (abs(s.treble) > 0.05f) db += Biquad.highShelf(TREBLE_FREQ, s.treble, SHELF_Q, rate).magnitudeDb(f, rate)
        return db
    }

    /** Największe podbicie krzywej (dB, ≥ 0) – siatka logarytmiczna plus środki pasm, żeby nie ominąć wąskich szczytów. */
    fun peakBoostDb(s: DspState): Float {
        if (!s.enabled) return 0f
        var peak = 0f
        for (i in 0 until 160) peak = max(peak, eqDb(s, F_MIN * (F_MAX / F_MIN).pow(i / 159f)))
        for (b in s.bands) peak = max(peak, eqDb(s, b.freq))
        return peak
    }

    /** Automatyczne ściszenie (dB, ≤ 0) równe największemu podbiciu – gdy [DspState.autoGain] włączony. */
    fun autoGainDb(s: DspState): Float = if (s.enabled && s.autoGain) -peakBoostDb(s) else 0f

    /** Całkowite wzmocnienie toru przed filtrami: przedwzmacniacz i automatyczny zapas (bez ReplayGain). */
    fun outputGainDb(s: DspState): Float = if (s.enabled) s.preamp + autoGainDb(s) else 0f

    /** Odpowiedź całego toru w dB (bez limitera i ReplayGain). */
    fun responseDb(s: DspState, f: Float, rate: Float = REF_RATE): Float =
        if (!s.enabled) 0f else eqDb(s, f, rate) + outputGainDb(s)

    fun bandResponseDb(b: EqBand, f: Float, rate: Float = REF_RATE) = filterFor(b, rate).magnitudeDb(f, rate)

    const val CUSTOM = "Własny"
    const val IMPORTED = "Zaimportowany"

    // ---------- JSON ----------

    private fun bandsJson(list: List<EqBand>) = JSONArray().apply {
        list.forEach { put(JSONArray().put(it.freq.toDouble()).put(it.gain.toDouble()).put(it.q.toDouble()).put(it.type.ordinal)) }
    }

    private fun bandsFrom(a: JSONArray?): List<EqBand>? = a?.let {
        List(it.length()) { i -> it.getJSONArray(i).let { b -> EqBand(b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), FilterType.entries.getOrElse(b.optInt(3, 0)) { FilterType.PEAK }) } }
    }

    internal fun toJson(s: DspState) = JSONObject()
        .put("enabled", s.enabled).put("bands", bandsJson(s.bands)).put("preamp", s.preamp.toDouble())
        .put("bass", s.bass.toDouble()).put("treble", s.treble.toDouble()).put("balance", s.balance.toDouble())
        .put("width", s.width.toDouble()).put("mono", s.mono).put("swap", s.swap).put("limiter", s.limiter)
        .put("autoGain", s.autoGain).put("preset", s.preset)
        .put("comp", s.compressor).put("compT", s.compThreshold.toDouble()).put("compR", s.compRatio.toDouble())
        .put("xfeed", s.crossfeed.toDouble()).put("autoProf", s.autoProfiles).put("profile", s.profile)
        .put("rg", s.replayGain.name).put("rgPre", s.rgPreamp.toDouble())
        .put("user", JSONArray().apply { s.userPresets.forEach { put(JSONObject().put("name", it.name).put("bands", bandsJson(it.bands)).put("pre", it.preamp.toDouble())) } })

    internal fun fromJson(o: JSONObject): DspState {
        val d = DspState()
        val user = o.optJSONArray("user")?.let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { p -> DspPreset(p.getString("name"), bandsFrom(p.getJSONArray("bands"))?.takeIf { it.isNotEmpty() } ?: defaultBands(), p.optDouble("pre", 0.0).toFloat()) } }
        } ?: emptyList()
        return DspState(
            enabled = o.optBoolean("enabled", d.enabled),
            bands = bandsFrom(o.optJSONArray("bands"))?.takeIf { it.isNotEmpty() }?.take(MAX_BANDS) ?: d.bands,
            preamp = o.optDouble("preamp", 0.0).toFloat(),
            bass = o.optDouble("bass", 0.0).toFloat(),
            treble = o.optDouble("treble", 0.0).toFloat(),
            balance = o.optDouble("balance", 0.0).toFloat(),
            width = o.optDouble("width", 1.0).toFloat(),
            mono = o.optBoolean("mono", false),
            swap = o.optBoolean("swap", false),
            limiter = o.optBoolean("limiter", true),
            autoGain = o.optBoolean("autoGain", true),
            preset = o.optString("preset", d.preset),
            userPresets = user,
            compressor = o.optBoolean("comp", false),
            compThreshold = o.optDouble("compT", -18.0).toFloat(),
            compRatio = o.optDouble("compR", 3.0).toFloat(),
            crossfeed = o.optDouble("xfeed", 0.0).toFloat(),
            autoProfiles = o.optBoolean("autoProf", true),
            profile = o.optString("profile", "SPEAKER"),
            replayGain = ReplayGainMode.entries.firstOrNull { it.name == o.optString("rg") } ?: ReplayGainMode.OFF,
            rgPreamp = o.optDouble("rgPre", 0.0).toFloat(),
        )
    }
}

/**
 * Filtr dwukwadratowy (Audio EQ Cookbook, R. Bristow-Johnson), znormalizowany a0 = 1.
 * Współczynniki w podwójnej precyzji – przy niskich pasmach (20–60 Hz) pojedyncza precyzja daje szum i odchyłki.
 */
class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
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
            Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

        private fun wAlpha(f: Float, q: Float, rate: Float): Pair<Double, Double> {
            val w = 2 * PI * f.coerceIn(10f, rate * 0.45f) / rate
            return w to sin(w) / (2 * q.coerceAtLeast(0.1f))
        }

        fun peaking(f: Float, gainDb: Float, q: Float, rate: Float): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val (w, alpha) = wAlpha(f, q, rate)
            return norm(1 + alpha * a, -2 * cos(w), 1 - alpha * a, 1 + alpha / a, -2 * cos(w), 1 - alpha / a)
        }

        /** Półka z dobrocią [q] (0,707 – klasyczna półka o nachyleniu S = 1). */
        private fun shelf(f: Float, gainDb: Float, q: Float, rate: Float, high: Boolean): Biquad {
            val a = 10.0.pow(gainDb / 40.0)
            val (w, alpha) = wAlpha(f, q, rate)
            val cw = cos(w)
            val sa = 2 * sqrt(a) * alpha
            return if (!high) norm(
                a * ((a + 1) - (a - 1) * cw + sa), 2 * a * ((a - 1) - (a + 1) * cw), a * ((a + 1) - (a - 1) * cw - sa),
                (a + 1) + (a - 1) * cw + sa, -2 * ((a - 1) + (a + 1) * cw), (a + 1) + (a - 1) * cw - sa,
            ) else norm(
                a * ((a + 1) + (a - 1) * cw + sa), -2 * a * ((a - 1) + (a + 1) * cw), a * ((a + 1) + (a - 1) * cw - sa),
                (a + 1) - (a - 1) * cw + sa, 2 * ((a - 1) - (a + 1) * cw), (a + 1) - (a - 1) * cw - sa,
            )
        }

        fun lowShelf(f: Float, gainDb: Float, q: Float, rate: Float) = shelf(f, gainDb, q, rate, false)
        fun highShelf(f: Float, gainDb: Float, q: Float, rate: Float) = shelf(f, gainDb, q, rate, true)

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
    }
}
