package com.blacksingularities.openamp.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln
import kotlin.math.roundToInt

data class EqPreset(val name: String, val levels: List<Int>, val builtIn: Boolean)

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    /** Częstotliwości środkowe pasm w Hz. */
    val freqs: List<Int> = emptyList(),
    /** Poziomy pasm w milibelach. */
    val levels: List<Int> = emptyList(),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val preset: String = "",
    val bass: Int = 0,
    val virtualizer: Int = 0,
    val loudness: Int = 0,
    val bassSupported: Boolean = true,
    val virtualizerSupported: Boolean = true,
    val userPresets: List<EqPreset> = emptyList(),
)

/**
 * Efekty systemowe (korektor, wzmocnienie basów, przestrzenność, wzmocnienie głośności) przypięte
 * do sesji audio odtwarzacza. Ustawienia są zapamiętywane i przywracane przy każdej nowej sesji.
 */
object AudioFx {
    private lateinit var prefs: android.content.SharedPreferences
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var session = 0

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    /** Krzywe wbudowanych presetów: wzmocnienie w dB dla 31 Hz … 16 kHz (10 pasm oktawowych). */
    private val curves: List<Pair<String, FloatArray>> = listOf(
        "Płaski" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bas+" to floatArrayOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f),
        "Rock" to floatArrayOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f),
        "Pop" to floatArrayOf(-1f, 0f, 2f, 4f, 5f, 4f, 2f, 0f, -1f, -1f),
        "Jazz" to floatArrayOf(4f, 3f, 1f, 2f, -1f, -1f, 0f, 1f, 3f, 4f),
        "Klasyczna" to floatArrayOf(5f, 4f, 3f, 2f, -1f, -1f, 0f, 2f, 3f, 4f),
        "Elektroniczna" to floatArrayOf(6f, 5f, 1f, 0f, -2f, 2f, 1f, 2f, 5f, 6f),
        "Wokal" to floatArrayOf(-2f, -3f, -2f, 1f, 4f, 5f, 4f, 2f, 0f, -1f),
        "Soprany+" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 7f),
    )
    private val curveFreqs = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("openamp_eq", Context.MODE_PRIVATE)
        _state.value = _state.value.copy(
            enabled = prefs.getBoolean("enabled", false),
            preset = prefs.getString("preset", "Płaski") ?: "Płaski",
            bass = prefs.getInt("bass", 0),
            virtualizer = prefs.getInt("virt", 0),
            loudness = prefs.getInt("loud", 0),
            userPresets = loadUserPresets(),
        )
    }

    val builtInNames: List<String> get() = curves.map { it.first }

    fun attach(audioSession: Int) {
        if (audioSession == session && eq != null) return
        release()
        session = audioSession
        if (audioSession == 0) return
        eq = runCatching { Equalizer(0, audioSession) }.getOrNull()
        bass = runCatching { BassBoost(0, audioSession) }.getOrNull()
        virt = runCatching { Virtualizer(0, audioSession) }.getOrNull()
        loud = runCatching { LoudnessEnhancer(audioSession) }.getOrNull()
        val e = eq
        if (e == null) {
            _state.value = _state.value.copy(available = false)
            return
        }
        val bands = e.numberOfBands.toInt()
        val range = e.bandLevelRange
        val freqs = List(bands) { e.getCenterFreq(it.toShort()) / 1000 }
        val saved = prefs.getString("levels", null)?.split(',')?.mapNotNull { it.toIntOrNull() }
        val levels = if (saved != null && saved.size == bands) saved else List(bands) { 0 }
        _state.value = _state.value.copy(
            available = true,
            freqs = freqs,
            levels = levels,
            minLevel = range[0].toInt(),
            maxLevel = range[1].toInt(),
            bassSupported = bass?.strengthSupported == true,
            virtualizerSupported = virt?.strengthSupported == true,
        )
        applyAll()
    }

    fun release() {
        listOf(eq, bass, virt, loud).forEach { runCatching { it?.release() } }
        eq = null; bass = null; virt = null; loud = null
    }

    private fun applyAll() {
        val s = _state.value
        runCatching {
            eq?.let { e ->
                e.enabled = s.enabled
                s.levels.forEachIndexed { i, l -> e.setBandLevel(i.toShort(), l.coerceIn(s.minLevel, s.maxLevel).toShort()) }
            }
        }
        runCatching { bass?.let { it.enabled = s.enabled && s.bass > 0; if (s.bassSupported) it.setStrength(s.bass.toShort()) } }
        runCatching { virt?.let { it.enabled = s.enabled && s.virtualizer > 0; if (s.virtualizerSupported) it.setStrength(s.virtualizer.toShort()) } }
        runCatching { loud?.let { it.enabled = s.enabled && s.loudness > 0; it.setTargetGain(s.loudness) } }
    }

    private fun persist() {
        val s = _state.value
        prefs.edit()
            .putBoolean("enabled", s.enabled)
            .putString("preset", s.preset)
            .putString("levels", s.levels.joinToString(","))
            .putInt("bass", s.bass)
            .putInt("virt", s.virtualizer)
            .putInt("loud", s.loudness)
            .apply()
    }

    private fun change(block: (EqState) -> EqState) {
        _state.value = block(_state.value)
        applyAll()
        persist()
    }

    fun setEnabled(on: Boolean) = change { it.copy(enabled = on) }
    fun setBand(index: Int, level: Int) = change { s ->
        s.copy(levels = s.levels.toMutableList().also { if (index in it.indices) it[index] = level }, preset = "Własny")
    }
    fun setBass(v: Int) = change { it.copy(bass = v) }
    fun setVirtualizer(v: Int) = change { it.copy(virtualizer = v) }
    fun setLoudness(v: Int) = change { it.copy(loudness = v) }

    fun reset() = change { s -> s.copy(levels = List(s.levels.size) { 0 }, preset = "Płaski", bass = 0, virtualizer = 0, loudness = 0) }

    fun applyPreset(name: String) {
        val s = _state.value
        val user = s.userPresets.firstOrNull { it.name == name }
        val levels = when {
            user != null && user.levels.size == s.freqs.size -> user.levels
            else -> curves.firstOrNull { it.first == name }?.second?.let { curve ->
                s.freqs.map { f -> (interpolate(curve, f.toFloat()) * 100).roundToInt().coerceIn(s.minLevel, s.maxLevel) }
            } ?: return
        }
        change { it.copy(levels = levels, preset = name) }
    }

    /** Wzmocnienie krzywej dla dowolnej częstotliwości: interpolacja w skali logarytmicznej. */
    private fun interpolate(curve: FloatArray, f: Float): Float {
        if (f <= curveFreqs.first()) return curve.first()
        if (f >= curveFreqs.last()) return curve.last()
        val i = curveFreqs.indexOfLast { it <= f }
        val a = ln(curveFreqs[i]); val b = ln(curveFreqs[i + 1])
        val t = ((ln(f) - a) / (b - a))
        return curve[i] + (curve[i + 1] - curve[i]) * t
    }

    fun saveUserPreset(name: String) {
        val s = _state.value
        val list = s.userPresets.filterNot { it.name == name } + EqPreset(name, s.levels, false)
        storeUserPresets(list)
        _state.value = s.copy(userPresets = list, preset = name)
        persist()
    }

    fun deleteUserPreset(name: String) {
        val list = _state.value.userPresets.filterNot { it.name == name }
        storeUserPresets(list)
        _state.value = _state.value.copy(userPresets = list)
    }

    private fun loadUserPresets(): List<EqPreset> = runCatching {
        val a = JSONArray(prefs.getString("user_presets", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            val l = o.getJSONArray("levels")
            EqPreset(o.getString("name"), List(l.length()) { l.getInt(it) }, false)
        }
    }.getOrDefault(emptyList())

    private fun storeUserPresets(list: List<EqPreset>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("levels", JSONArray(it.levels))) }
        prefs.edit().putString("user_presets", a.toString()).apply()
    }
}
