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

/**
 * „Taśma” utworu: fala dźwiękowa całego pliku przesuwa się pod nieruchomą głowicą na środku.
 * Część odtworzona jest w kolorze akcentu, a przeciąganie w poziomie przewija utwór.
 */
@Composable
fun WaveformTape(
    waveform: FloatArray?,
    positionMs: Long,
    durationMs: Long,
    color: Color,
    live: LiveSpectrum?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragMs by remember { mutableStateOf<Long?>(null) }
    var accum by remember { mutableFloatStateOf(0f) }
    val currentPos by rememberUpdatedState(positionMs)
    val pos = dragMs ?: positionMs
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val stepDp = 4.dp
    Box(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(84.dp)
                .pointerInput(durationMs, waveform) {
                    val points = waveform?.size ?: 600
                    var startMs = 0L
                    detectHorizontalDragGestures(
                        onDragStart = { accum = 0f; startMs = currentPos; dragMs = currentPos },
                        onDragEnd = { dragMs?.let(onSeek); dragMs = null },
                        onDragCancel = { dragMs = null },
                    ) { change, dx ->
                        change.consume()
                        accum += dx
                        val msPerPx = durationMs.toFloat() / (points * stepDp.toPx())
                        dragMs = (startMs - accum * msPerPx).toLong().coerceIn(0, durationMs)
                    }
                }
                .pointerInput(durationMs) {
                    // Dotknięcie po lewej/prawej stronie głowicy: przeskok o 10 s.
                    detectTapGestures { o ->
                        val d = if (o.x < size.width / 2) -10_000 else 10_000
                        onSeek((currentPos + d).coerceIn(0, durationMs))
                    }
                },
        ) {
            @Suppress("UNUSED_VARIABLE") val t = live?.tick
            val w = waveform
            val step = stepDp.toPx()
            val bar = step * 0.6f
            val mid = size.height / 2
            val cx = size.width / 2
            val n = w?.size ?: 600
            val prog = if (durationMs > 0) pos.toFloat() / durationMs else 0f
            val head = prog * n
            val first = max(0, (head - cx / step).toInt() - 1)
            val last = minOf(n - 1, (head + cx / step).toInt() + 1)
            val boost = live?.level ?: 0f
            val played = Brush.verticalGradient(listOf(color.copy(alpha = 0.95f), color, color.copy(alpha = 0.6f)))
            for (i in first..last) {
                val x = cx + (i - head) * step
                val v = w?.get(i) ?: 0.15f
                val near = 1f + boost * 0.35f * max(0f, 1f - kotlin.math.abs(i - head) / 6f)
                val h = max(2.dp.toPx(), v * (size.height / 2 - 4.dp.toPx()) * near)
                val isPlayed = i < head
                // Wygaszanie przy krawędziach taśmy.
                val edge = (1f - kotlin.math.abs(x - cx) / cx).coerceIn(0f, 1f)
                val alpha = 0.25f + 0.75f * edge
                if (isPlayed) {
                    drawRoundRect(played, Offset(x - bar / 2, mid - h), Size(bar, h * 2), CornerRadius(bar / 2), alpha = alpha)
                } else {
                    drawRoundRect(dim, Offset(x - bar / 2, mid - h), Size(bar, h * 2), CornerRadius(bar / 2), alpha = alpha)
                }
            }
            // Głowica
            drawLine(Color.White, Offset(cx, 4.dp.toPx()), Offset(cx, size.height - 4.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.White, 4.dp.toPx(), Offset(cx, 4.dp.toPx()))
            drawCircle(Color.White, 4.dp.toPx(), Offset(cx, size.height - 4.dp.toPx()))
        }
        if (dragMs != null) {
            Text(
                formatTime(pos), style = MaterialTheme.typography.labelLarge, color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/**
 * Spektrogram przesuwający się w lewo: kolumny powstają przy głowicy (na środku), po prawej –
 * zarys nadchodzącej części utworu z fali dźwiękowej.
 */
@Composable
fun SpectrogramTape(
    waveform: FloatArray?,
    positionMs: Long,
    durationMs: Long,
    color: Color,
    live: LiveSpectrum,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    Canvas(
        modifier.fillMaxWidth().height(84.dp).pointerInput(durationMs) {
            detectTapGestures { o -> onSeek((o.x / size.width * durationMs).toLong()) }
        },
    ) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val cx = size.width / 2
        val colW = 3.dp.toPx()
        val hist = live.history
        val bands = Spectrum.BANDS / 2
        val cellH = size.height / bands
        var x = cx - colW
        for (k in hist.indices.reversed()) {
            if (x < -colW) break
            val col = hist[k]
            val fade = (x / cx).coerceIn(0f, 1f)
            for (b in 0 until bands) {
                val v = max(col[b * 2], col[b * 2 + 1])
                if (v < 0.08f) continue
                drawRect(color.copy(alpha = (v * v * fade).coerceIn(0f, 1f)), Offset(x, size.height - (b + 1) * cellH), Size(colW, cellH + 0.5f))
            }
            x -= colW
        }
        // Nadchodząca część: obrys fali dźwiękowej.
        val w = waveform
        if (w != null && durationMs > 0) {
            val head = positionMs.toFloat() / durationMs * w.size
            val step = 4.dp.toPx()
            var i = head.toInt() + 1
            while (true) {
                val px = cx + (i - head) * step
                if (px > size.width || i >= w.size) break
                val h = max(2f, w[i] * size.height * 0.45f)
                val edge = (1f - (px - cx) / cx).coerceIn(0f, 1f)
                drawRoundRect(dim, Offset(px - step * 0.3f, size.height / 2 - h), Size(step * 0.6f, h * 2), CornerRadius(step), alpha = edge)
                i++
            }
        }
        drawLine(Color.White, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        // Pasek postępu całego utworu u dołu.
        val p = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
        drawRoundRect(dim, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
        drawRoundRect(color, Offset(0f, size.height - 3.dp.toPx()), Size(size.width * p, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
    }
}

@Composable
fun SimpleSeek(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    Slider(
        value = drag ?: frac.coerceIn(0f, 1f),
        onValueChange = { drag = it },
        onValueChangeFinished = { drag?.let { onSeek((it * durationMs).toLong()) }; drag = null },
        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
        modifier = modifier,
    )
}
