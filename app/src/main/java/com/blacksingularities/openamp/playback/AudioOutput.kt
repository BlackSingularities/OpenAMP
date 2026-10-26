package com.blacksingularities.openamp.playback

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * Wyjście dźwięku OpenAMP:
 * - pliki 24/32-bit (FLAC, WAV, ALAC) dekodowane do float i przetwarzane przez [DspProcessor] bez obcinania
 *   do 16 bitów – wyjście float do AudioTrack ([OpenAmpAudioSink]),
 * - pozostałe pliki przez zwykły tor ExoPlayera z korektorem, pomijaniem ciszy i zmianą tempa,
 * - pomijanie ciszy tylko dla prawdziwej ciszy (poniżej −60 dBFS, co najmniej 1,5 s) i bez ściszania –
 *   domyślne ustawienia media3 brały za ciszę wszystko poniżej −30 dBFS po 100 ms i ściszały to do 10%.
 */
class OpenAmpRenderersFactory(context: Context, private val spectrum: SpectrumProcessor) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
        val silence = SilenceSkippingAudioProcessor(
            /* minimumSilenceDurationUs = */ 1_500_000,
            /* silenceRetentionRatio = */ 0.1f,
            /* maxSilenceToKeepDurationUs = */ 500_000,
            /* minVolumeToKeepPercentageWhenMuting = */ 100,
            /* silenceThresholdLevel = */ 32,
        )
        val inner = DefaultAudioSink.Builder(context)
            .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(arrayOf<AudioProcessor>(RateLimiter(), DspProcessor(), spectrum), silence, SonicAudioProcessor()))
            // Float trafia do AudioTrack bez zamiany na 16 bitów (tor hi-res poniżej).
            .setEnableFloatOutput(true)
            .build()
        return OpenAmpAudioSink(inner)
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        val start = out.size
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, audioSink, eventHandler, eventListener, out)
        for (i in start until out.size) {
            if (out[i].javaClass == MediaCodecAudioRenderer::class.java) {
                out[i] = HiResAudioRenderer(context, codecAdapterFactory, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, audioSink)
            }
        }
    }
}

/**
 * Pliki powyżej 192 kHz (np. FLAC 384 kHz) – AudioTrack na większości urządzeń ich nie otwiera,
 * więc są przepróbkowywane do 192 kHz. Dla niższych częstotliwości procesor jest nieaktywny.
 */
private class RateLimiter : AudioProcessor {
    private val sonic = SonicAudioProcessor()
    private var active = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        active = inputAudioFormat.sampleRate > MAX_RATE
        if (!active) return AudioProcessor.AudioFormat.NOT_SET
        sonic.setOutputSampleRateHz(MAX_RATE)
        return sonic.configure(inputAudioFormat)
    }

    override fun isActive() = active && sonic.isActive
    override fun queueInput(inputBuffer: ByteBuffer) = sonic.queueInput(inputBuffer)
    override fun queueEndOfStream() = sonic.queueEndOfStream()
    override fun getOutput(): ByteBuffer = sonic.output
    override fun isEnded() = sonic.isEnded
    override fun flush() = sonic.flush()
    override fun reset() { sonic.reset(); active = false }

    companion object { const val MAX_RATE = 192_000 }
}

/** Dekoder prosi o próbki float tylko dla źródeł o rozdzielczości ponad 16 bitów – reszta zostaje w 16 bitach. */
private class HiResAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    selector: MediaCodecSelector,
    fallback: Boolean,
    handler: Handler,
    listener: AudioRendererEventListener,
    sink: AudioSink,
) : MediaCodecAudioRenderer(context, codecAdapterFactory, selector, fallback, handler, listener, sink) {
    override fun getMediaFormat(format: Format, codecMimeType: String, codecMaxInputSize: Int, codecOperatingRate: Float): MediaFormat {
        val mf = super.getMediaFormat(format, codecMimeType, codecMaxInputSize, codecOperatingRate)
        if (!OpenAmpAudioSink.isHiRes(format.pcmEncoding)) mf.removeKey(MediaFormat.KEY_PCM_ENCODING)
        return mf
    }
}

/**
 * Nakładka na [DefaultAudioSink]. Dla wejścia hi-res (24/32-bit, float) sama przepuszcza dźwięk przez
 * [DspProcessor] i widmo, a do AudioTrack podaje float. Gdy włączona jest zmiana tempa albo pomijanie ciszy
 * (działają tylko w 16 bitach), zamienia hi-res na 16 bitów z ditheringiem i zostawia resztę zwykłemu torowi.
 * Przy każdej konfiguracji (nowy utwór) przekazuje jego ReplayGain do procesora.
 */
class OpenAmpAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    private enum class Mode { PASS, HIRES, TO16 }

    private val dsp = DspProcessor(floatOutput = true)
    private val spectrum = SpectrumProcessor()
    private var mode = Mode.PASS
    private var format: Format? = null
    private var bufferSize = 0
    private var channelMap: IntArray? = null
    private var params = PlaybackParameters.DEFAULT
    private var skipSilence = false
    private var reconfigure = false

    /** Przetworzony bufor czekający na przyjęcie przez właściwe wyjście. */
    private var pending: ByteBuffer? = null
    private var pendingTime = 0L
    private var pendingCount = 0
    private var conv: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var noise = 0x5DEECE66DL

    companion object {
        fun isHiRes(encoding: Int) = encoding == C.ENCODING_PCM_24BIT || encoding == C.ENCODING_PCM_32BIT || encoding == C.ENCODING_PCM_FLOAT
        private fun isHiRes(f: Format) = MimeTypes.AUDIO_RAW == f.sampleMimeType && isHiRes(f.pcmEncoding)
    }

    private fun wanted(f: Format) = when {
        !isHiRes(f) -> Mode.PASS
        // Powyżej 192 kHz hi-res idzie torem 16-bit, gdzie przepróbkowuje go RateLimiter.
        params == PlaybackParameters.DEFAULT && !skipSilence && f.sampleRate <= RateLimiter.MAX_RATE -> Mode.HIRES
        else -> Mode.TO16
    }

    override fun supportsFormat(format: Format) = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int =
        if (isHiRes(format)) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY else super.getFormatSupport(format)

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        // Utwór, który za chwilę trafi na wyjście – jego ReplayGain zacznie obowiązywać na granicy w procesorze.
        val (track, album) = ReplayGain.fromMetadata(inputFormat.metadata)
        Dsp.stageReplayGain(track, album)
        format = inputFormat
        bufferSize = specifiedBufferSize
        channelMap = outputChannels
        reconfigure = false
        applyMode()
    }

    private fun applyMode() {
        val f = format ?: return
        val next = wanted(f)
        if (next == Mode.HIRES) {
            val af = AudioProcessor.AudioFormat(f.sampleRate, f.channelCount, f.pcmEncoding)
            // Kolejny utwór hi-res: koniec strumienia + flush zachowują ciągłość (bez przerwy między utworami).
            if (mode == Mode.HIRES && dsp.isActive) dsp.queueEndOfStream()
            dsp.configure(af)
            dsp.flush()
            spectrum.configure(AudioProcessor.AudioFormat(f.sampleRate, f.channelCount, C.ENCODING_PCM_FLOAT))
            spectrum.flush()
        }
        mode = next
        val out = when (next) {
            Mode.PASS -> f
            Mode.HIRES -> f.buildUpon().setPcmEncoding(C.ENCODING_PCM_FLOAT).build()
            Mode.TO16 -> f.buildUpon().setPcmEncoding(C.ENCODING_PCM_16BIT).build()
        }
        super.configure(out, bufferSize, channelMap)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // Zmiana tempa / pomijania ciszy w trakcie utworu hi-res – przełączenie toru między buforami.
        if (reconfigure && pending == null) {
            reconfigure = false
            applyMode()
        }
        if (mode == Mode.PASS) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (pending == null) {
            pending = if (mode == Mode.HIRES) {
                dsp.queueInput(buffer)
                val processed = dsp.output
                spectrum.queueInput(processed)
                spectrum.output
            } else to16(buffer)
            pendingTime = presentationTimeUs
            pendingCount = encodedAccessUnitCount
        }
        val done = super.handleBuffer(pending!!, pendingTime, pendingCount)
        if (done) pending = null
        return done
    }

    /** Hi-res → 16 bitów z ditheringiem TPDF (dla toru ze zmianą tempa / pomijaniem ciszy). */
    private fun to16(src: ByteBuffer): ByteBuffer {
        val enc = format?.pcmEncoding ?: C.ENCODING_PCM_FLOAT
        val bytes = if (enc == C.ENCODING_PCM_24BIT) 3 else 4
        val samples = src.remaining() / bytes
        if (conv.capacity() < samples * 2) conv = ByteBuffer.allocateDirect(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        conv.clear()
        val s = src.order(ByteOrder.LITTLE_ENDIAN)
        repeat(samples) {
            val v = when (enc) {
                C.ENCODING_PCM_FLOAT -> s.float.toDouble()
                C.ENCODING_PCM_24BIT -> {
                    val b0 = s.get().toInt() and 0xFF
                    val b1 = s.get().toInt() and 0xFF
                    val b2 = s.get().toInt()
                    ((b2 shl 16) or (b1 shl 8) or b0) / 8388608.0
                }
                else -> s.int / 2147483648.0
            }
            conv.putShort((v * 32768 + rand() - rand()).roundToLong().coerceIn(-32768, 32767).toInt().toShort())
        }
        src.position(src.limit())
        conv.flip()
        return conv
    }

    private fun rand(): Double {
        var x = noise
        x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17)
        noise = x
        return (x ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        params = playbackParameters
        super.setPlaybackParameters(playbackParameters)
        format?.let { if (wanted(it) != mode) reconfigure = true }
    }

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        skipSilence = skipSilenceEnabled
        super.setSkipSilenceEnabled(skipSilenceEnabled)
        format?.let { if (wanted(it) != mode) reconfigure = true }
    }

    override fun flush() {
        super.flush()
        pending = null
        if (mode == Mode.HIRES) { dsp.flush(); spectrum.flush() }
    }

    override fun reset() {
        super.reset()
        pending = null
        dsp.reset()
        spectrum.reset()
        mode = Mode.PASS
        format = null
        reconfigure = false
    }
}
