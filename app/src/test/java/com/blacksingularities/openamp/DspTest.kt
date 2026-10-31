package com.blacksingularities.openamp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.blacksingularities.openamp.playback.Dsp
import com.blacksingularities.openamp.playback.DspProcessor
import com.blacksingularities.openamp.playback.DspState
import com.blacksingularities.openamp.playback.DspMeter
import com.blacksingularities.openamp.playback.EqBand
import com.blacksingularities.openamp.playback.EqText
import com.blacksingularities.openamp.playback.FilterType
import com.blacksingularities.openamp.playback.ReplayGain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class DspTest {
    private val rate = 44100

    @After
    fun neutral() = Dsp.update { DspState() }

    /** Stereo 16-bit: sinus [hz] o amplitudzie [amp] (0–1), [frames] ramek. */
    private fun sine(hz: Double, amp: Double, frames: Int): ShortArray =
        ShortArray(frames * 2) { i -> (sin(2 * PI * hz * (i / 2) / rate) * amp * 32767).toInt().toShort() }

    private fun process(input: ShortArray): ShortArray {
        val p = DspProcessor()
        p.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        p.flush()
        val out = ArrayList<Short>()
        // Kawałkami, jak w odtwarzaczu.
        for (chunk in input.toList().chunked(4096)) {
            val buf = ByteBuffer.allocateDirect(chunk.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            chunk.forEach { buf.putShort(it) }
            buf.flip()
            p.queueInput(buf)
            val o = p.output.order(ByteOrder.LITTLE_ENDIAN)
            while (o.remaining() >= 2) out += o.short
        }
        return out.toShortArray()
    }

    @Test
    fun neutralIsBitExact() {
        Dsp.update { DspState() }
        // Poniżej progu limitera (−0,3 dBFS) – przy neutralnym torze nic nie może się zmienić.
        val input = sine(440.0, 0.9, 20000)
        val out = process(input)
        assertEquals(input.size, out.size)
        // Tor ma stałe opóźnienie limitera z wyprzedzeniem – poza nim próbki muszą być identyczne.
        val lag = (input.indices.firstOrNull { out[it] != 0.toShort() } ?: 0) - (input.indices.firstOrNull { input[it] != 0.toShort() } ?: 0)
        for (i in 0 until input.size - lag) assertEquals("próbka $i", input[i], out[i + lag])
    }

    @Test
    fun boostStaysBelowFullScale() {
        Dsp.update { s -> s.copy(enabled = true, limiter = true, bands = s.bands.map { if (it.freq == 1000f) it.copy(gain = 12f) else it }) }
        val out = process(sine(1000.0, 0.9, 30000))
        val peak = out.drop(4000).maxOf { abs(it.toInt()) }
        assertTrue("szczyt $peak", peak <= 32767)
        assertTrue("limiter ścisza, a nie przycina: $peak", peak < 32700)
    }

    @Test
    fun eqChangesLevel() {
        Dsp.update { s -> s.copy(enabled = true, limiter = false, bands = s.bands.map { EqBand(it.freq, if (it.freq == 1000f) -12f else 0f, it.q) }) }
        val out = process(sine(1000.0, 0.5, 30000))
        val peak = out.drop(4000).maxOf { abs(it.toInt()) }
        // −12 dB ≈ ×0,25: z ~16 400 do ~4 100.
        assertTrue("szczyt $peak", peak in 3000..5500)
    }

    @Test
    fun hiResNeutralIsExact() {
        Dsp.update { DspState() }
        val p = DspProcessor(floatOutput = true)
        p.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_24BIT))
        p.flush()
        // 24-bit: wartości, których 16 bitów by nie oddało.
        val frames = 4000
        val input = IntArray(frames * 2) { i -> ((sin(2 * PI * 440 * (i / 2) / rate) * 0.9 * 8388607).toInt()) or 1 }
        val buf = ByteBuffer.allocateDirect(input.size * 3).order(ByteOrder.LITTLE_ENDIAN)
        input.forEach { v -> buf.put(v.toByte()); buf.put((v shr 8).toByte()); buf.put((v shr 16).toByte()) }
        buf.flip()
        p.queueInput(buf)
        val out = p.output.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val got = FloatArray(out.remaining()).also { out.get(it) }
        assertEquals(input.size, got.size)
        val lag = got.indexOfFirst { it != 0f } - input.indexOfFirst { it != 0 }
        for (i in 0 until input.size - lag) assertEquals("próbka $i", (input[i] / 8388608.0).toFloat(), got[i + lag], 0f)
    }

    @Test
    fun replayGainStartsAtTrackBoundary() {
        Dsp.update { DspState(replayGain = com.blacksingularities.openamp.playback.ReplayGainMode.TRACK) }
        Dsp.setReplayGain(null, null)
        val p = DspProcessor()
        p.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
        p.flush()
        // Znacznik następnego utworu nie działa, dopóki procesor nie dojdzie do granicy strumienia.
        Dsp.stageReplayGain(-6f, null)
        assertNull(Dsp.rgTrack)
        p.queueEndOfStream()
        p.flush()
        assertEquals(-6f, Dsp.rgTrack!!, 0f)
        Dsp.setReplayGain(null, null)
    }

    /** Podbicie basów: bez zapasu głośne nagranie wpada w limiter, z zapasem gra czysto. */
    @Test
    fun autoGainKeepsBoostOutOfLimiter() {
        val boosted = DspState().bands.map { if (it.freq <= 125f) it.copy(gain = 6f) else it }
        Dsp.update { DspState(bands = boosted, autoGain = false) }
        process(sine(80.0, 0.9, 40000))
        assertTrue("bez zapasu limiter ścisza: ${DspMeter.reductionDb}", DspMeter.reductionDb < -2f)

        Dsp.update { DspState(bands = boosted, autoGain = true) }
        assertTrue("zapas ≈ −podbicie", Dsp.autoGainDb(Dsp.value) < -5f)
        val out = process(sine(80.0, 0.9, 40000))
        assertTrue("z zapasem limiter nie pracuje: ${DspMeter.reductionDb}", DspMeter.reductionDb > -0.3f)
        val peak = out.drop(8000).maxOf { abs(it.toInt()) }
        assertTrue("poziom zbliżony do wejścia: $peak", peak in 20000..32767)
    }

    @Test
    fun flatCurveNeedsNoHeadroom() {
        assertEquals(0f, Dsp.autoGainDb(DspState()), 0f)
        val cut = DspState().bands.map { it.copy(gain = -6f) }
        assertEquals(0f, Dsp.autoGainDb(DspState(bands = cut)), 0f)
    }

    @Test
    fun parsesAutoEqParametric() {
        val text = """
            Preamp: -6.4 dB
            Filter 1: ON LSC Fc 105 Hz Gain 6.5 dB Q 0.70
            Filter 2: ON PK Fc 2410,5 Hz Gain -3.2 dB Q 1.89
            Filter 3: OFF PK Fc 5000 Hz Gain 9 dB Q 1
            Filter 4: ON HSC Fc 10000 Hz Gain -2.0 dB Q 0.70
            Filter 5: ON HP Fc 20 Hz Q 0.71
        """.trimIndent()
        val p = EqText.parse(text)!!
        assertEquals(-6.4f, p.preamp!!, 0.001f)
        assertEquals(4, p.bands.size)
        assertEquals(EqBand(20f, 0f, 0.71f, FilterType.HIGH_PASS), p.bands[0])
        assertEquals(EqBand(105f, 6.5f, 0.70f, FilterType.LOW_SHELF), p.bands[1])
        assertEquals(EqBand(2410.5f, -3.2f, 1.89f, FilterType.PEAK), p.bands[2])
        assertEquals(FilterType.HIGH_SHELF, p.bands[3].type)
        assertNull(EqText.parse("zwykły tekst bez filtrów"))
    }

    @Test
    fun parsesGraphicEq() {
        val p = EqText.parse("GraphicEQ: 20 6; 1000 0; 20000 -6", 10)!!
        assertEquals(10, p.bands.size)
        assertTrue(p.bands.first().gain > 4f)
        assertEquals(0f, p.bands[5].gain, 0.01f)
        assertTrue(p.bands.last().gain < -4f)
    }

    @Test
    fun exportRoundTrips() {
        val bands = listOf(
            EqBand(60f, 4f, 0.9f, FilterType.LOW_SHELF),
            EqBand(1000f, -2.5f, 2f),
            EqBand(8000f, 3f, 1.41f),
        )
        Dsp.update { DspState(bands = bands, preamp = -1f, autoGain = false) }
        val p = EqText.parse(EqText.export(Dsp.value))!!
        assertEquals(-1f, p.preamp!!, 0.01f)
        assertEquals(bands, p.bands)
    }

    @Test
    fun importIgnoresFilePreampWithAutoGain() {
        Dsp.update { DspState(autoGain = true, preamp = 0f) }
        assertTrue(Dsp.importText("Preamp: -7 dB\nFilter 1: ON PK Fc 100 Hz Gain 5 dB Q 1"))
        assertEquals(0f, Dsp.value.preamp, 0f)
        assertEquals(1, Dsp.value.bands.size)
        assertEquals(Dsp.IMPORTED, Dsp.value.preset)
    }

    @Test
    fun undoRestoresPreviousCurve() {
        Dsp.update { DspState() }
        val before = Dsp.value.bands
        Dsp.applyPreset("Rock")
        assertTrue(Dsp.value.bands != before)
        assertTrue(Dsp.canUndo.value)
        Dsp.undo()
        assertEquals(before, Dsp.value.bands)
    }

    @Test
    fun bandCountChangeKeepsShape() {
        Dsp.update { DspState() }
        Dsp.applyPreset("Bas+")
        Dsp.setBandCount(31)
        assertEquals(31, Dsp.value.bands.size)
        val at60 = Dsp.eqDb(Dsp.value, 60f)
        val at4k = Dsp.eqDb(Dsp.value, 4000f)
        assertTrue("bas zostaje podbity: $at60", at60 > 3f)
        assertTrue("wysokie bez zmian: $at4k", abs(at4k) < 1f)
    }

    @Test
    fun replayGainParsing() {
        assertEquals(-6.53f, ReplayGain.parseDb("-6.53 dB")!!, 0.001f)
        assertEquals(2.1f, ReplayGain.parseDb("+2,1 dB")!!, 0.001f)
        assertNull(ReplayGain.parseDb("abc"))
    }
}
