package com.blacksingularities.openamp.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Dane dla wizualizacji: pasma widma 0..1 i poziom sygnału. */
class SpectrumFrame(val bands: FloatArray, val level: Float, val wave: FloatArray, val serial: Long)

object Spectrum {
    const val BANDS = 48
    const val WAVE = 128
    private val empty = SpectrumFrame(FloatArray(BANDS), 0f, FloatArray(WAVE), 0)
    internal val frames = MutableStateFlow(empty)
    val state: StateFlow<SpectrumFrame> = frames.asStateFlow()
    fun reset() { frames.value = empty }
}

/**
 * Procesor w torze ExoPlayera: przepuszcza dźwięk bez zmian, a z kopii próbek liczy FFT.
 * Dzięki temu wizualizacja nie wymaga uprawnienia do mikrofonu (jak android.media.audiofx.Visualizer).
 */
class SpectrumProcessor : BaseAudioProcessor() {
    private val n = 2048
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2 * PI * it / (n - 1))).toFloat() }
    private val ring = FloatArray(n)
    private var ringPos = 0
    private var filled = 0
    private var hop = 0
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val smooth = FloatArray(Spectrum.BANDS)
    private var channels = 2
    private var sampleRate = 44100
    private var float = false
    private var serial = 0L
    private var edges = IntArray(Spectrum.BANDS + 1)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Inny format niż 16-bit / float PCM – widmo nieaktywne, dźwięk przechodzi bez zmian.
        val enc = inputAudioFormat.encoding
        if (enc != C.ENCODING_PCM_16BIT && enc != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        float = enc == C.ENCODING_PCM_FLOAT
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        edges = bandEdges(sampleRate)
        return inputAudioFormat
    }

    /** Granice pasm w skali logarytmicznej 40 Hz – 16 kHz. */
    private fun bandEdges(rate: Int): IntArray {
        val lo = 40.0
        val hi = minOf(16000.0, rate / 2.0 - 1)
        return IntArray(Spectrum.BANDS + 1) { i ->
            val f = lo * (hi / lo).pow(i / Spectrum.BANDS.toDouble())
            (f * n / rate).toInt().coerceIn(1, n / 2 - 1)
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val src = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frameBytes = (if (float) 4 else 2) * channels
        while (src.remaining() >= frameBytes) {
            var sum = 0f
            for (c in 0 until channels) sum += if (float) src.float else src.short / 32768f
            ring[ringPos] = sum / channels
            ringPos = (ringPos + 1) % n
            if (filled < n) filled++
            if (++hop >= n / 4 && filled == n) {
                hop = 0
                analyze()
            }
        }
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    private fun analyze() {
        var rms = 0f
        for (i in 0 until n) {
            val v = ring[(ringPos + i) % n]
            rms += v * v
            re[i] = v * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val bands = FloatArray(Spectrum.BANDS)
        for (b in 0 until Spectrum.BANDS) {
            val from = edges[b]
            val to = max(edges[b + 1], from + 1)
            var peak = 0f
            for (k in from until to) {
                val m = re[k] * re[k] + im[k] * im[k]
                if (m > peak) peak = m
            }
            // Amplituda w dB (-70..0) → 0..1, lekkie podbicie wysokich pasm.
            val db = 10f * log10(peak + 1e-12f) - 20f * log10(n / 4f) + b * 0.25f
            val v = ((db + 70f) / 70f).coerceIn(0f, 1f)
            smooth[b] = if (v > smooth[b]) v else smooth[b] * 0.55f + v * 0.45f
            bands[b] = smooth[b]
        }
        val wave = FloatArray(Spectrum.WAVE) { i -> ring[(ringPos + n - Spectrum.WAVE * 4 + i * 4) % n] }
        val level = (sqrt(rms / n) * 3f).coerceIn(0f, 1f)
        Spectrum.frames.value = SpectrumFrame(bands, level, wave, ++serial)
    }

    override fun onFlush() {
        ringPos = 0; filled = 0; hop = 0
    }

    override fun onReset() {
        onFlush()
        smooth.fill(0f)
        Spectrum.reset()
    }

    companion object {
        /** FFT radix-2 w miejscu. */
        fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) {
                    var t = re[i]; re[i] = re[j]; re[j] = t
                    t = im[i]; im[i] = im[j]; im[j] = t
                }
            }
            var len = 2
            while (len <= n) {
                val ang = -2 * PI / len
                val wr = cos(ang).toFloat()
                val wi = sin(ang).toFloat()
                var i = 0
                while (i < n) {
                    var cr = 1f
                    var ci = 0f
                    for (k in 0 until len / 2) {
                        val a = i + k
                        val b = a + len / 2
                        val xr = re[b] * cr - im[b] * ci
                        val xi = re[b] * ci + im[b] * cr
                        re[b] = re[a] - xr; im[b] = im[a] - xi
                        re[a] += xr; im[a] += xi
                        val ncr = cr * wr - ci * wi
                        ci = cr * wi + ci * wr
                        cr = ncr
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}
