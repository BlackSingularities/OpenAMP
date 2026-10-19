package com.blacksingularities.openamp.playback

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FxState(
    /** Przestrzenność 0–1000 (efekt systemowy, najlepiej działa na słuchawkach). */
    val virtualizer: Int = 0,
    /** Wzmocnienie głośności w milibelach (0–1500). */
    val loudness: Int = 0,
    val virtualizerSupported: Boolean = false,
    val loudnessSupported: Boolean = false,
)

/**
 * Efekty systemowe przypięte do sesji audio: przestrzenność i wzmocnienie głośności. Korektor
 * i pozostałe przetwarzanie robi [Dsp] w torze ExoPlayera.
 */
object AudioFx {
    private lateinit var prefs: android.content.SharedPreferences
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var session = 0

    private val _state = MutableStateFlow(FxState())
    val state: StateFlow<FxState> = _state.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("openamp_fx", Context.MODE_PRIVATE)
        _state.value = FxState(prefs.getInt("virt", 0), prefs.getInt("loud", 0))
    }

    fun attach(audioSession: Int) {
        if (audioSession == session && (virt != null || loud != null)) return
        release()
        session = audioSession
        if (audioSession == 0) return
        virt = runCatching { Virtualizer(0, audioSession) }.getOrNull()
        loud = runCatching { LoudnessEnhancer(audioSession) }.getOrNull()
        _state.value = _state.value.copy(
            virtualizerSupported = virt?.strengthSupported == true,
            loudnessSupported = loud != null,
        )
        apply()
    }

    fun release() {
        runCatching { virt?.release() }
        runCatching { loud?.release() }
        virt = null; loud = null
    }

    private fun apply() {
        val s = _state.value
        val on = Dsp.value.enabled
        runCatching { virt?.let { it.enabled = on && s.virtualizer > 0; if (s.virtualizerSupported) it.setStrength(s.virtualizer.toShort()) } }
        runCatching { loud?.let { it.enabled = on && s.loudness > 0; it.setTargetGain(s.loudness) } }
    }

    /** Wywoływane po włączeniu/wyłączeniu korektora – efekty systemowe idą za nim. */
    fun refresh() = apply()

    fun setVirtualizer(v: Int) {
        _state.value = _state.value.copy(virtualizer = v)
        prefs.edit().putInt("virt", v).apply()
        apply()
    }

    fun setLoudness(v: Int) {
        _state.value = _state.value.copy(loudness = v)
        prefs.edit().putInt("loud", v).apply()
        apply()
    }
}
