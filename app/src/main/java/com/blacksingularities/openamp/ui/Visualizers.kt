package com.blacksingularities.openamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.offset
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
    /** Impuls basu 0..1 (skacze przy uderzeniu, potem opada). */
    var beat = 0f
    internal var gain = 0.3f
    internal var bassAvg = 0f
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
            // Automatyczne wzmocnienie: cicha muzyka też wypełnia skalę, a krzywa potęgowa
            // zwiększa kontrast, więc słupki wyraźnie skaczą zamiast stać na stałej wysokości.
            if (playing && target != null) {
                var peak = 0f
                for (v in target.bands) if (v > peak) peak = v
                live.gain = max(max(live.gain * 0.996f, peak), 0.25f)
            }
            for (i in live.bands.indices) {
                val raw = if (playing && target != null) target.bands[i] else if (playing) -1f else 0f
                if (raw < 0f) continue
                val norm = (raw / live.gain).coerceIn(0f, 1f)
                val t = norm * norm * (3f - 2f * norm) * 0.35f + norm * norm * 0.65f
                live.bands[i] = if (t > live.bands[i]) live.bands[i] + (t - live.bands[i]) * 0.85f else live.bands[i] + (t - live.bands[i]) * 0.28f
            }
            var bass = 0f
            for (i in 0 until 6) bass += live.bands[i]
            bass /= 6f
            live.bassAvg = live.bassAvg * 0.95f + bass * 0.05f
            val hit = ((bass - live.bassAvg * 1.08f) * 4f).coerceIn(0f, 1f)
            live.beat = if (hit > live.beat) hit else live.beat * 0.88f
            if (target != null && playing) {
                target.wave.copyInto(live.wave)
                live.level = live.level * 0.4f + (target.level * 1.6f).coerceAtMost(1f) * 0.6f
            } else if (!playing) {
                for (i in live.wave.indices) live.wave[i] *= 0.85f
                live.level *= 0.9f
                live.beat *= 0.85f
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
            val g = 1f / max(live.gain, 0.25f)
            val y = mid - (live.wave[i] * size.height * 1.6f * g).coerceIn(-mid, mid)
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
            val len = maxLen * (0.06f + v * 0.94f) * (0.85f + live.beat * 0.15f)
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


/** Pulsująca poświata za okładką i fala uderzeniowa przy mocnym basie. */
@Composable
fun PulseGlow(live: LiveSpectrum, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val r = size.minDimension / 2
        val e = live.beat * 0.7f + live.level * 0.3f
        val gr = r * (0.85f + e * 0.15f)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.25f + e * 0.55f), Color.Transparent), center, gr), gr, center)
        if (live.beat > 0.05f) drawCircle(
            color.copy(alpha = live.beat * 0.8f), r * (0.78f + (1f - live.beat) * 0.2f), center,
            style = Stroke((2f + live.beat * 6f).dp.toPx()),
        )
    }
}

/** Słupki rosnące symetrycznie od środka w obie strony (bas w środku). */
@Composable
fun MirrorBars(live: LiveSpectrum, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val n = Spectrum.BANDS / 2
        val slots = n * 2
        val step = size.width / slots
        val w = step * 0.62f
        val mid = size.height / 2
        for (k in 0 until slots) {
            val i = if (k < n) n - 1 - k else k - n
            val v = live.bands[i * 2]
            val h = max(size.height * v, w)
            val x = k * step + (step - w) / 2
            drawRoundRect(color.copy(alpha = 0.45f + v * 0.55f), Offset(x, mid - h / 2), Size(w, h), CornerRadius(w / 2))
        }
    }
}

/** Fala dźwięku zawinięta w okrąg wokół okładki. */
@Composable
fun RingWave(live: LiveSpectrum, color: Color, modifier: Modifier, innerFraction: Float = 0.84f) {
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val n = live.wave.size
        val r0 = size.minDimension / 2 * innerFraction
        val amp = size.minDimension / 2 - r0
        val g = 1f / max(live.gain, 0.25f)
        val path = Path()
        for (i in 0..n) {
            val idx = i % n
            // Wygasza końce fali, żeby okrąg domykał się bez skoku.
            val fade = sin(PI * idx / n).toFloat()
            val v = (live.wave[idx] * 3f * g * fade).coerceIn(-1f, 1f)
            val r = r0 + amp * (0.3f + v * 0.5f + live.beat * 0.2f)
            val a = 2 * PI * idx / n - PI / 2
            val x = center.x + cos(a).toFloat() * r
            val y = center.y + sin(a).toFloat() * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color.copy(alpha = 0.3f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, color, style = Stroke((2.5f + live.beat * 2f).dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Gładka, wypełniona krzywa widma u dołu okładki. */
@Composable
fun SpectrumLine(live: LiveSpectrum, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val n = live.bands.size
        val pts = List(n) { i -> Offset(size.width * i / (n - 1), size.height * (1f - live.bands[i] * 0.95f)) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until n) {
                val p = pts[i - 1]; val q = pts[i]
                val mx = (p.x + q.x) / 2
                cubicTo(mx, p.y, mx, q.y, q.x, q.y)
            }
        }
        val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = (0.45f + live.beat * 0.4f).coerceAtMost(1f)), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * Wizualizacja z lekkim cieniem: najpierw ten sam rysunek w półprzezroczystej czerni, lekko
 * przesunięty i rozmyty (Android 12+), potem właściwy – widać ją na białej i jasnej okładce.
 * [draw] dostaje kolor cienia (albo null dla właściwego rysunku) i modyfikator wypełniający pole.
 */
@Composable
fun Shadowed(modifier: Modifier, draw: @Composable (shadow: Color?, Modifier) -> Unit) {
    Box(modifier) {
        draw(
            Color.Black.copy(alpha = 0.55f),
            Modifier.matchParentSize().offset(y = 1.5.dp)
                .then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(4.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
        )
        draw(null, Modifier.matchParentSize())
    }
}
