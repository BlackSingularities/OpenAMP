package com.blacksingularities.openamp.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.tanh

/**
 * Tor dźwięku OpenAMP w ExoPlayerze. Całe przetwarzanie w podwójnej precyzji:
 * - korektor i półki jako łańcuch filtrów dwukwadratowych; zmiana ustawień przenika się ze starym
 *   łańcuchem (30 ms), a wzmocnienia są wygładzane – ruszanie suwakami nie trzeszczy,
 * - ReplayGain, przedwzmacniacz, balans, szerokość stereo / mono, zamiana kanałów, crossfeed,
 * - kompresor z miękkim kolanem i limiter z wyprzedzeniem 1,5 ms (zamiast twardego ucinania szczytów),
 * - dithering TPDF przy powrocie do 16 bitów.
 * Przy neutralnych ustawieniach próbki przechodzą bez zmian (bit w bit).
 *
 * Wejście: PCM 16, 24, 32 bit albo float. [floatOutput] – wynik jako float (tor hi-res w [OpenAmpAudioSink]);
 * inaczej w formacie wejścia (16 bit z ditheringiem).
 * Na granicy utworów (koniec strumienia, potem flush) stan filtrów, limitera i linii opóźniającej zostaje –
 * odtwarzanie bez przerw jest ciągłe, a ReplayGain nowego utworu zaczyna działać dokładnie od jego pierwszej próbki.
 */
class DspProcessor(private val floatOutput: Boolean = false) : BaseAudioProcessor() {
    private var channels = 2
    private var rate = 44100
    private var encoding = C.ENCODING_PCM_16BIT
    /** Koniec strumienia przed flushem – granica utworów, nie przewinięcie. */
    private var boundary = false
    /** Zmieniła się częstotliwość albo liczba kanałów – stan trzeba wyzerować mimo granicy. */
    private var formatChanged = true
    private var seen = -1L
    private var snapshot = DspState()

    private var chain = Chain(DoubleArray(0), 2)
    /** Poprzedni łańcuch filtrów – przenikany z bieżącym przez [fadeLeft] ramek. */
    private var oldChain: Chain? = null
    private var fadeLen = 1
    private var fadeLeft = 0

    // Wartości docelowe i wygładzone (jednobiegunowo, ~10 ms).
    private var gainT = 1.0; private var gain = 1.0
    private var balLT = 1.0; private var balL = 1.0
    private var balRT = 1.0; private var balR = 1.0
    private var widthT = 1.0; private var width = 1.0
    private var xfeedT = 0.0; private var xfeed = 0.0
    private var smoothK = 0.0

    private var swap = false
    private var compOn = false
    private var compThr = -18.0
    private var compRatio = 3.0
    private var compMakeup = 0.0
    private var compEnv = 0.0
    private var compAtt = 0.0
    private var compRel = 0.0
    private var compGrDb = 0.0
    private var limOn = true

    // Crossfeed: dolnoprzepustowy ~700 Hz na sygnale z przeciwnego kanału.
    private var xfK = 0.0
    private var xfL = 0.0
    private var xfR = 0.0

    // Limiter z wyprzedzeniem: linia opóźniająca [lookahead] ramek.
    private var lookahead = 64
    private var delay = DoubleArray(0)
    private var delayPos = 0
    private var limTarget = 1.0
    private var limGain = 1.0
    private var limHold = 0
    private var limAtt = 0.0
    private var limRel = 0.0

    /** Czy tor jest neutralny – wtedy bez ditheringu, żeby próbki przeszły bez zmian. */
    private var neutral = true
    private var noise = 0x2545F491L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val enc = inputAudioFormat.encoding
        // Nieobsługiwany format – przepuszczamy bez obróbki zamiast blokować odtwarzanie.
        val supported = enc == C.ENCODING_PCM_16BIT || enc == C.ENCODING_PCM_FLOAT ||
            (floatOutput && (enc == C.ENCODING_PCM_24BIT || enc == C.ENCODING_PCM_32BIT))
        if (!supported) return AudioProcessor.AudioFormat.NOT_SET
        if (inputAudioFormat.channelCount != channels || inputAudioFormat.sampleRate != rate) formatChanged = true
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        encoding = enc
        val r = rate.toDouble()
        smoothK = 1 - exp(-1 / (r * 0.010))
        compAtt = 1 - exp(-1 / (r * 0.008))
        compRel = 1 - exp(-1 / (r * 0.150))
        xfK = 1 - exp(-2 * PI * 700 / r)
        lookahead = (r * 0.0015).toInt().coerceAtLeast(1)
        limAtt = 1 - exp(-1 / (lookahead * 0.3))
        limRel = 1 - exp(-1 / (r * 0.120))
        fadeLen = (r * 0.030).toInt().coerceAtLeast(1)
        DspMeter.sampleRate = rate
        DspMeter.channels = channels
        DspMeter.inputFormat = when (enc) {
            C.ENCODING_PCM_16BIT -> "16 bit"
            C.ENCODING_PCM_24BIT -> "24 bit"
            C.ENCODING_PCM_32BIT -> "32 bit"
            else -> "32 bit float"
        }
        DspMeter.floatOut = floatOutput || enc == C.ENCODING_PCM_FLOAT
        seen = -1
        return if (floatOutput) AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_FLOAT) else inputAudioFormat
    }

    /** Koniec strumienia: nic nie wypuszczamy – ogon z linii opóźniającej zagra na początku następnego utworu. */
    override fun onQueueEndOfStream() {
        boundary = true
    }

    override fun onFlush() {
        val continuous = boundary && !formatChanged
        boundary = false
        formatChanged = false
        // ReplayGain nowego utworu – przygotowany przy konfiguracji wyjścia, obowiązuje od tej chwili.
        Dsp.commitReplayGain()
        if (continuous) {
            // Granica utworów bez przerwy: filtry, limiter i opóźnienie grają dalej, wzmocnienia przechodzą płynnie.
            refresh()
            return
        }
        chain = Chain(chain.co, channels)
        oldChain = null
        fadeLeft = 0
        delay = DoubleArray(lookahead * channels)
        delayPos = 0
        limTarget = 1.0; limGain = 1.0; limHold = 0
        compEnv = 0.0; compGrDb = 0.0
        xfL = 0.0; xfR = 0.0
        // Po przewinięciu wartości od razu docelowe – bez narastania od poprzednich.
        refresh()
        gain = gainT; balL = balLT; balR = balRT; width = widthT; xfeed = xfeedT
    }

    override fun onReset() {
        seen = -1
        boundary = false
        formatChanged = true
    }

    private fun refresh() {
        val v = Dsp.version
        if (v == seen) return
        seen = v
        val s = Dsp.value
        snapshot = s
        val on = s.enabled && !Dsp.bypass
        val r = rate.toFloat()

        val list = ArrayList<Biquad>()
        if (on) {
            s.bands.filter { Dsp.isActive(it) }.forEach { list += Dsp.filterFor(it, r) }
            if (abs(s.bass) > 0.05f) list += Biquad.lowShelf(Dsp.BASS_FREQ, s.bass, r)
            if (abs(s.treble) > 0.05f) list += Biquad.highShelf(Dsp.TREBLE_FREQ, s.treble, r)
        }
        val co = DoubleArray(list.size * 5)
        list.forEachIndexed { i, b -> co[i * 5] = b.b0; co[i * 5 + 1] = b.b1; co[i * 5 + 2] = b.b2; co[i * 5 + 3] = b.a1; co[i * 5 + 4] = b.a2 }
        if (!co.contentEquals(chain.co)) {
            val next = Chain(co, channels)
            // Ten sam kształt łańcucha – stan filtrów przejmujemy, przenikanie usuwa resztę stanu nieustalonego.
            if (next.n == chain.n) for (c in 0 until channels) chain.z[c].copyInto(next.z[c])
            oldChain = if (chain.n == 0 && next.n == 0) null else chain
            fadeLeft = if (oldChain != null) fadeLen else 0
            chain = next
        }

        val rg = Dsp.replayGainDb(s)
        gainT = 10.0.pow(((if (on) s.preamp else 0f) + rg) / 20.0)
        val bal = if (on) s.balance.toDouble() else 0.0
        balLT = if (bal > 0) 1 - bal else 1.0
        balRT = if (bal < 0) 1 + bal else 1.0
        widthT = if (!on) 1.0 else if (s.mono) 0.0 else s.width.toDouble()
        xfeedT = if (on) s.crossfeed * 0.45 else 0.0
        swap = on && s.swap
        compOn = on && s.compressor
        compThr = s.compThreshold.toDouble()
        compRatio = s.compRatio.toDouble().coerceAtLeast(1.0)
        // Automatyczne wyrównanie: połowa ściszenia, jakie kompresor daje na progu 0 dB.
        compMakeup = -compThr * (1 - 1 / compRatio) * 0.5
        // Limiter pilnuje też wzmocnienia ReplayGain, gdy korektor jest wyłączony.
        limOn = s.limiter && (on || rg > 0f)
    }

    private fun approach(cur: Double, target: Double): Double {
        val d = target - cur
        return if (abs(d) < 1e-7) target else cur + d * smoothK
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        refresh()
        val src = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val float = encoding == C.ENCODING_PCM_FLOAT
        val bytes = when (encoding) { C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_24BIT -> 3; else -> 4 }
        val frames = remaining / (bytes * channels)
        val outFloat = floatOutput || float
        val out = replaceOutputBuffer(frames * channels * (if (outFloat) 4 else 2)).order(ByteOrder.LITTLE_ENDIAN)
        if (delay.size != lookahead * channels) { delay = DoubleArray(lookahead * channels); delayPos = 0 }
        val frame = DoubleArray(channels)
        val stereo = channels == 2
        var peakL = 0.0
        var peakR = 0.0
        var minGain = 1.0

        for (n in 0 until frames) {
            for (c in 0 until channels) frame[c] = when (encoding) {
                C.ENCODING_PCM_16BIT -> src.short / 32768.0
                C.ENCODING_PCM_FLOAT -> src.float.toDouble()
                C.ENCODING_PCM_24BIT -> {
                    val b0 = src.get().toInt() and 0xFF
                    val b1 = src.get().toInt() and 0xFF
                    val b2 = src.get().toInt()
                    ((b2 shl 16) or (b1 shl 8) or b0) / 8388608.0
                }
                else -> src.int / 2147483648.0
            }

            gain = approach(gain, gainT); balL = approach(balL, balLT); balR = approach(balR, balRT)
            width = approach(width, widthT); xfeed = approach(xfeed, xfeedT)

            // Filtry; w trakcie zmiany ustawień – przenikanie starego łańcucha w nowy.
            val old = oldChain
            if (old != null && fadeLeft > 0) {
                val t = 1.0 - fadeLeft.toDouble() / fadeLen
                for (c in 0 until channels) {
                    val x = frame[c] * gain
                    frame[c] = old.process(c, x) * (1 - t) + chain.process(c, x) * t
                }
                if (--fadeLeft == 0) oldChain = null
            } else {
                if (gain != 1.0) for (c in 0 until channels) frame[c] *= gain
                if (chain.n > 0) for (c in 0 until channels) frame[c] = chain.process(c, frame[c])
            }

            if (stereo) {
                var l = frame[0]; var r = frame[1]
                if (swap) { val t = l; l = r; r = t }
                if (width != 1.0) {
                    val m = (l + r) * 0.5
                    val sd = (l - r) * 0.5 * width
                    l = m + sd; r = m - sd
                }
                if (xfeed > 1e-6) {
                    xfL += (l - xfL) * xfK; xfR += (r - xfR) * xfK
                    val nl = (l + xfR * xfeed) / (1 + xfeed); val nr = (r + xfL * xfeed) / (1 + xfeed)
                    l = nl; r = nr
                }
                if (balL != 1.0) l *= balL
                if (balR != 1.0) r *= balR
                frame[0] = l; frame[1] = r
            }

            if (compOn) {
                var peak = 0.0
                for (c in 0 until channels) peak = max(peak, abs(frame[c]))
                compEnv += (peak - compEnv) * (if (peak > compEnv) compAtt else compRel)
                val over = 20 * log10(compEnv.coerceAtLeast(1e-6)) - compThr
                // Miękkie kolano 6 dB: kompresja narasta łagodnie zamiast włączać się skokowo.
                val knee = 6.0
                val slope = 1 - 1 / compRatio
                compGrDb = when {
                    over <= -knee / 2 -> 0.0
                    over >= knee / 2 -> over * slope
                    else -> slope * (over + knee / 2) * (over + knee / 2) / (2 * knee)
                }
                val g = 10.0.pow((compMakeup - compGrDb) / 20)
                for (c in 0 until channels) frame[c] *= g
            } else compGrDb = 0.0

            // Limiter: decyzja na podstawie próbki wchodzącej, wzmocnienie stosowane do opóźnionej.
            val base = delayPos * channels
            if (limOn) {
                var peak = 0.0
                for (c in 0 until channels) peak = max(peak, abs(frame[c]))
                val ceiling = 0.966 // −0,3 dBFS
                val need = if (peak > ceiling) ceiling / peak else 1.0
                if (need <= limTarget) { limTarget = need; limHold = lookahead }
                else if (limHold > 0) limHold--
                else limTarget = 1.0
                limGain += (limTarget - limGain) * (if (limTarget < limGain) limAtt else limRel)
                if (abs(1.0 - limGain) < 1e-7) limGain = 1.0
                if (limGain < minGain) minGain = limGain
            } else {
                limTarget = 1.0; limHold = 0
                limGain += (1.0 - limGain) * limRel
                if (abs(1.0 - limGain) < 1e-7) limGain = 1.0
            }
            for (c in 0 until channels) {
                val delayed = delay[base + c]
                delay[base + c] = frame[c]
                frame[c] = if (limGain != 1.0) delayed * limGain else delayed
            }
            delayPos = (delayPos + 1) % lookahead

            if (stereo) { peakL = max(peakL, abs(frame[0])); peakR = max(peakR, abs(frame[1])) }
            else if (channels > 0) { peakL = max(peakL, abs(frame[0])); peakR = peakL }

            for (c in 0 until channels) {
                var v = frame[c]
                // Zabezpieczenie: szczyty powyżej 0,97 pełnej skali (ponad próg limitera), zaokrąglane
                // miękko (ciągle, bez załamania); przy neutralnym torze próbki zostają nietknięte.
                if (!neutral && abs(v) > 0.97) v = Math.copySign(0.97 + 0.03 * tanh((abs(v) - 0.97) / 0.03), v)
                if (outFloat) out.putFloat(v.toFloat())
                else {
                    // Dithering TPDF (±1 LSB) – zamiast zniekształceń kwantyzacji cichy, równy szum.
                    val d = if (neutral) 0.0 else rand() - rand()
                    out.putShort((v * 32768 + d).roundToLong().coerceIn(-32768, 32767).toInt().toShort())
                }
            }
        }

        neutral = chain.n == 0 && oldChain == null && gain == 1.0 && width == 1.0 && xfeed <= 1e-6 && !swap &&
            balL == 1.0 && balR == 1.0 && !compOn && limGain == 1.0
        DspMeter.peakL = peakL.toFloat()
        DspMeter.peakR = peakR.toFloat()
        DspMeter.reductionDb = (20 * log10(minGain.coerceAtLeast(1e-6)) - compGrDb).toFloat().coerceAtMost(0f)
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    /** Szybki generator szumu (xorshift) w przedziale [0, 1). */
    private fun rand(): Double {
        var x = noise
        x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17)
        noise = x
        return (x ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    /** Łańcuch filtrów: współczynniki [b0, b1, b2, a1, a2] po kolei i stan (postać transponowana II) dla każdego kanału. */
    private class Chain(val co: DoubleArray, channels: Int) {
        val n = co.size / 5
        val z = Array(channels) { DoubleArray(n * 2) }

        fun process(c: Int, input: Double): Double {
            var x = input
            val s = z[c]
            var k = 0
            var j = 0
            while (k < co.size) {
                val y = co[k] * x + s[j]
                s[j] = co[k + 1] * x - co[k + 3] * y + s[j + 1]
                s[j + 1] = co[k + 2] * x - co[k + 4] * y
                x = y
                k += 5; j += 2
            }
            return x
        }
    }
}
