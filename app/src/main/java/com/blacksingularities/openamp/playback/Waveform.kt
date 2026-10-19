package com.blacksingularities.openamp.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.LruCache
import com.blacksingularities.openamp.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Fala dźwiękowa utworu dla „taśmy” odtwarzania: plik jest dekodowany w tle, z każdego odcinka
 * zostaje wartość RMS. Wynik trafia do pamięci podręcznej na dysku (raz na utwór).
 */
object Waveform {
    const val POINTS = 600
    private val memory = LruCache<Long, FloatArray>(24)

    suspend fun load(context: Context, track: Track): FloatArray? = withContext(Dispatchers.IO) {
        memory.get(track.id)?.let { return@withContext it }
        val dir = File(context.cacheDir, "waveforms").apply { mkdirs() }
        val file = File(dir, "${track.id}_${track.size}.bin")
        if (file.exists()) {
            val b = file.readBytes()
            val w = FloatArray(b.size) { (b[it].toInt() and 0xFF) / 255f }
            memory.put(track.id, w)
            return@withContext w
        }
        val w = runCatching { decode(context, track) }.getOrNull() ?: return@withContext null
        memory.put(track.id, w)
        runCatching { file.writeBytes(ByteArray(w.size) { (w[it] * 255).toInt().coerceIn(0, 255).toByte() }) }
        w
    }

    private suspend fun decode(context: Context, track: Track): FloatArray? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, track.uri, null)
            val ti = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val fmt = ex.getTrackFormat(ti)
            ex.selectTrack(ti)
            val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else track.durationMs * 1000
            if (durUs <= 0) return null
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()
            val sum = DoubleArray(POINTS)
            val cnt = IntArray(POINTS)
            val peak = FloatArray(POINTS)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var channels = fmt.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var rate = fmt.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44100)
            var framesOut = 0L
            try {
                while (!outputDone) {
                    coroutineContext.ensureActive()
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(5000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val n = ex.readSampleData(buf, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 5000)
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        channels = codec.outputFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        rate = codec.outputFormat.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, rate)
                    } else if (outIdx >= 0) {
                        val out = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.LITTLE_ENDIAN)
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val shorts = out.asShortBuffer()
                        val frames = shorts.remaining() / channels
                        // Co czwarta ramka wystarcza do obwiedni i przyspiesza analizę.
                        var f = 0
                        while (f < frames) {
                            var v = 0f
                            for (c in 0 until channels) v += abs(shorts.get(f * channels + c).toInt()) / 32768f
                            v /= channels
                            val t = (framesOut + f) * 1_000_000L / rate
                            val bin = (t * POINTS / durUs).toInt().coerceIn(0, POINTS - 1)
                            sum[bin] += (v * v).toDouble()
                            cnt[bin]++
                            if (v > peak[bin]) peak[bin] = v
                            f += 4
                        }
                        framesOut += frames
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            val raw = FloatArray(POINTS) { i ->
                if (cnt[i] == 0) 0f else (sqrt(sum[i] / cnt[i]).toFloat() * 0.7f + peak[i] * 0.3f)
            }
            val mx = max(raw.maxOrNull() ?: 0f, 1e-4f)
            return FloatArray(POINTS) { (raw[it] / mx).coerceIn(0f, 1f) }
        } finally {
            ex.release()
        }
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, def: Int) = if (containsKey(key)) getInteger(key) else def
}
