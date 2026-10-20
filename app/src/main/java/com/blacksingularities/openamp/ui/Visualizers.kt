package com.blacksingularities.openamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.playback.Spectrum
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Płynne widmo do rysowania: klatki z procesora są opóźniane o czas bufora wyjściowego
 * (żeby obraz zgadzał się z dźwiękiem), a wartości wygładzane w pętli animacji.
 * Gdy muzyka stoi, słupki łagodnie opadają.
 */
class LiveSpectrum {
    val bands = FloatArray(Spectrum.BANDS)
    val wave = FloatArray(Spectrum.WAVE)
    var level = 0f
    var tick by mutableLongStateOf(0L)
    /** Historia dla spektrogramu (najnowsza na końcu). */
    val history = ArrayDeque<FloatArray>()
}

@Composable
fun rememberLiveSpectrum(playing: Boolean, keepHistory: Int = 0): LiveSpectrum {
    val live = remember { LiveSpectrum() }
    val queue = remember { ArrayDeque<Pair<Long, com.blacksingularities.openamp.playback.SpectrumFrame>>() }
    LaunchedEffect(Unit) {
        Spectrum.state.collect { f ->
            synchronized(queue) {
                queue.addLast(System.nanoTime() to f)
                while (queue.size > 64) queue.removeFirst()
            }
        }
    }
    LaunchedEffect(playing, keepHistory) {
        val latency = 160_000_000L
        var lastHistory = 0L
        while (true) {
            val now = withFrameNanos { it }
            val target = synchronized(queue) {
                var pick: com.blacksingularities.openamp.playback.SpectrumFrame? = null
                while (queue.isNotEmpty() && System.nanoTime() - queue.first().first >= latency) pick = queue.removeFirst().second
                pick
            }
            for (i in live.bands.indices) {
                val t = if (playing && target != null) target.bands[i] else if (playing) live.bands[i] else 0f
                live.bands[i] = if (t > live.bands[i]) live.bands[i] + (t - live.bands[i]) * 0.6f else live.bands[i] + (t - live.bands[i]) * 0.15f
            }
            if (target != null && playing) {
                target.wave.copyInto(live.wave)
                live.level = live.level * 0.6f + target.level * 0.4f
            } else if (!playing) {
                for (i in live.wave.indices) live.wave[i] *= 0.85f
                live.level *= 0.9f
            }
            if (keepHistory > 0 && playing && now - lastHistory > 33_000_000L) {
                lastHistory = now
                live.history.addLast(live.bands.copyOf())
                while (live.history.size > keepHistory) live.history.removeFirst()
            }
            live.tick = now
        }
    }
    return live
}

@Composable
fun SpectrumBars(live: LiveSpectrum, color: Color, modifier: Modifier, mirror: Boolean = true) {
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val n = live.bands.size
        val gap = size.width / n * 0.28f
        val w = size.width / n - gap
        val brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)))
        for (i in 0 until n) {
            val v = live.bands[i]
            val h = max(size.height * (if (mirror) 0.62f else 1f) * v, w * 0.6f)
            val x = i * (w + gap) + gap / 2
            val baseY = if (mirror) size.height * 0.62f else size.height
            drawRoundRect(brush, Offset(x, baseY - h), Size(w, h), CornerRadius(w / 2))
            if (mirror) {
                drawRoundRect(color.copy(alpha = 0.18f), Offset(x, baseY + 2.dp.toPx()), Size(w, h * 0.55f), CornerRadius(w / 2))
            }
        }
    }
}

@Composable
fun WaveScope(live: LiveSpectrum, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val n = live.wave.size
        val mid = size.height / 2
        val path = Path()
        for (i in 0 until n) {
            val x = size.width * i / (n - 1)
            val y = mid - live.wave[i] * size.height * 1.6f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color.copy(alpha = 0.25f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Promieniste słupki wokół okładki. */
@Composable
fun CircleSpectrum(live: LiveSpectrum, color: Color, modifier: Modifier, innerFraction: Float = 0.82f) {
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val c = center
        val r0 = size.minDimension / 2 * innerFraction
        val maxLen = size.minDimension / 2 - r0
        val n = live.bands.size
        val total = n * 2
        for (k in 0 until total) {
            // Symetrycznie: lewa połowa to lustro prawej.
            val i = if (k < n) k else total - 1 - k
            val v = live.bands[i]
            val a = (k.toFloat() / total) * 2 * PI - PI / 2
            val len = maxLen * (0.08f + v * 0.92f)
            val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
            drawLine(
                color.copy(alpha = 0.35f + v * 0.65f),
                Offset(c.x + ca * r0, c.y + sa * r0),
                Offset(c.x + ca * (r0 + len), c.y + sa * (r0 + len)),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
            )
        }
    }
}

