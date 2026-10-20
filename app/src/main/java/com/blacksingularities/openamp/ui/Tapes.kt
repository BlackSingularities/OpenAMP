package com.blacksingularities.openamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.data.SeekStyle
import com.blacksingularities.openamp.playback.Spectrum
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Taśmy „przewijane” (przeciągnięcie przesuwa treść) – reszta działa jak klasyczny pasek. */
val SeekStyle.scrolls: Boolean get() = this == SeekStyle.TAPE || this == SeekStyle.SPECTROGRAM || this == SeekStyle.CASSETTE

/** Odstęp kolejnych punktów fali na taśmach przewijanych. */
val TapeStep = 4.dp

/**
 * Wizualizacja postępu utworu w wybranym stylu. Sama nie obsługuje dotyku – przewijaniem
 * zajmuje się całe pole wokół przycisków ([frac] to pokazywana pozycja, także w trakcie przeciągania).
 */
@Composable
fun SeekTape(
    style: SeekStyle,
    wave: FloatArray?,
    posMs: Long,
    frac: Float,
    live: LiveSpectrum,
    accent: Color,
    scrubbing: Boolean,
    modifier: Modifier,
) {
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val tick = live.tick
        val p = frac.coerceIn(0f, 1f)
        when (style) {
            SeekStyle.WAVEFORM -> bars(wave, p, live, accent)
            SeekStyle.MIRROR -> mirror(wave, p, live, accent)
            SeekStyle.LINE -> line(wave, p, live, accent)
            SeekStyle.DOTS -> dots(wave, p, live, accent)
            SeekStyle.SEGMENTS -> segments(wave, p, live, accent)
            SeekStyle.TAPE -> tape(wave, p, posMs, live, accent)
            SeekStyle.SPECTROGRAM -> spectrogram(wave, p, live, accent)
            SeekStyle.CASSETTE -> cassette(wave, p, posMs, live, accent)
        }
        if (scrubbing && !style.scrolls) {
            val x = p * size.width
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Wartość fali w przedziale [a, b) jako maksimum – z zastępczym kształtem, gdy fala się wczytuje. */
private fun sample(w: FloatArray?, i: Int, n: Int): Float {
    if (w == null || w.isEmpty()) return 0.18f + 0.08f * kotlin.math.sin(i * 0.7f).let { abs(it) }
    val a = i * w.size / n
    val b = max(a + 1, (i + 1) * w.size / n).coerceAtMost(w.size)
    var m = 0f
    for (k in a until b) m = max(m, w[k])
    return m
}

private fun played(accent: Color) = lerp(accent, Color.White, 0.25f)

private fun DrawScope.headGlow(x: Float, live: LiveSpectrum, accent: Color) {
    val r = 10.dp.toPx() + live.level * 14.dp.toPx()
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent), Offset(x, size.height / 2), r), r, Offset(x, size.height / 2))
}

/** Fala: zaokrąglone słupki, odtworzone w kolorze akcentu, przy głowicy pulsują z muzyką. */
private fun DrawScope.bars(w: FloatArray?, p: Float, live: LiveSpectrum, accent: Color) {
    val n = 60
    val slot = size.width / n
    val bw = slot * 0.62f
    val mid = size.height / 2
    val maxH = size.height / 2 - 6.dp.toPx()
    val top = played(accent)
    for (i in 0 until n) {
        val f = (i + 0.5f) / n
        val near = max(0f, 1f - abs(f - p) * n / 3f)
        val h = max(3.dp.toPx(), (sample(w, i, n) + live.level * 0.35f * near).coerceAtMost(1f) * maxH)
        val x = i * slot + (slot - bw) / 2
        if (f <= p) drawRoundRect(Brush.verticalGradient(listOf(top, accent, top), mid - h, mid + h), Offset(x, mid - h), Size(bw, h * 2), CornerRadius(bw / 2))
        else drawRoundRect(Color.White.copy(alpha = 0.26f), Offset(x, mid - h), Size(bw, h * 2), CornerRadius(bw / 2))
    }
    headGlow(p * size.width, live, accent)
}

private fun envelope(w: FloatArray?, n: Int): FloatArray = FloatArray(n) { sample(w, it, n) }.let { e ->
    // Wygładzenie (średnia z sąsiadów) – miękki kształt zamiast schodków.
    FloatArray(n) { i -> (e[max(0, i - 1)] + e[i] * 2 + e[min(n - 1, i + 1)]) / 4f }
}

/** Lustrzana fala: wypełniony obrys u góry i jego odbicie u dołu. */
private fun DrawScope.mirror(w: FloatArray?, p: Float, live: LiveSpectrum, accent: Color) {
    val n = 120
    val e = envelope(w, n)
    val mid = size.height * 0.56f
    val up = mid - 6.dp.toPx()
    val down = size.height - mid - 4.dp.toPx()
    val path = Path().apply {
        moveTo(0f, mid)
        for (i in 0 until n) lineTo(size.width * i / (n - 1), mid - max(1.5f, e[i] * up))
        for (i in n - 1 downTo 0) lineTo(size.width * i / (n - 1), mid + max(1.5f, e[i] * down * 0.7f))
        close()
    }
    val x = p * size.width
    drawPath(path, Color.White.copy(alpha = 0.16f))
    clipRect(right = x) {
        drawPath(path, Brush.verticalGradient(listOf(played(accent), accent, accent.copy(alpha = 0.35f)), 0f, size.height))
    }
    drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1f)
    headGlow(x, live, accent)
    drawLine(Color.White, Offset(x, mid - up * 0.9f), Offset(x, mid + down * 0.7f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
}

/** Linia konturu: jeden gładki przebieg głośności, odtworzona część świeci. */
private fun DrawScope.line(w: FloatArray?, p: Float, live: LiveSpectrum, accent: Color) {
    val n = 90
    val e = envelope(w, n)
    val mid = size.height / 2
    val amp = size.height / 2 - 10.dp.toPx()
    val path = Path()
    var px = 0f
    var py = mid
    for (i in 0 until n) {
        val x = size.width * i / (n - 1)
        // Na przemian w górę i w dół – kontur przypomina oscylogram.
        val y = mid + (if (i % 2 == 0) -1 else 1) * e[i] * amp
        if (i == 0) path.moveTo(x, y) else path.cubicTo((px + x) / 2, py, (px + x) / 2, y, x, y)
        px = x; py = y
    }
    val x = p * size.width
    drawPath(path, Color.White.copy(alpha = 0.28f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    clipRect(right = x) {
        drawPath(path, accent.copy(alpha = 0.28f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, played(accent), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
    }
    headGlow(x, live, accent)
    drawCircle(Color.White, 4.dp.toPx() + live.level * 3.dp.toPx(), Offset(x, mid))
}

/** Matryca kropek: kolumny diod zapalanych głośnością. */
private fun DrawScope.dots(w: FloatArray?, p: Float, live: LiveSpectrum, accent: Color) {
    val cols = 44
    val rows = 11
    val cw = size.width / cols
    val rh = size.height / rows
    val r = min(cw, rh) * 0.3f
    val head = (p * cols).toInt()
    for (c in 0 until cols) {
        val f = (c + 0.5f) / cols
        val boost = if (abs(c - head) <= 1) live.level * 0.4f else 0f
        val lit = ((sample(w, c, cols) + boost).coerceAtMost(1f) * rows / 2f).toInt().coerceAtLeast(0)
        for (rr in 0 until rows) {
            val d = abs(rr - rows / 2)
            val on = d <= lit
            val color = when {
                !on -> Color.White.copy(alpha = 0.07f)
                c == head -> Color.White
                f <= p -> lerp(accent, Color.White, d / rows.toFloat())
                else -> Color.White.copy(alpha = 0.32f)
            }
            drawCircle(color, r, Offset(c * cw + cw / 2, rr * rh + rh / 2))
        }
    }
}

/** Segmenty LED: bloki w rzędzie, odtworzone świecą, a pod spodem pasek widma na żywo. */
private fun DrawScope.segments(w: FloatArray?, p: Float, live: LiveSpectrum, accent: Color) {
    val n = 36
    val slot = size.width / n
    val bw = slot * 0.78f
    val base = size.height * 0.72f
    val maxH = base - 8.dp.toPx()
    val head = (p * n).toInt()
    for (i in 0 until n) {
        val h = max(6.dp.toPx(), sample(w, i, n) * maxH)
        val x = i * slot + (slot - bw) / 2
        val color = when {
            i == head -> Color.White
            i < head -> lerp(accent, Color.White, 0.15f)
            else -> Color.White.copy(alpha = 0.14f)
        }
        // Blok złożony z poziomych pasków jak w starym wyświetlaczu.
        var y = base
        val cell = 4.dp.toPx()
        while (y > base - h) {
            drawRoundRect(color, Offset(x, y - cell + 1.dp.toPx()), Size(bw, cell - 1.5.dp.toPx()), CornerRadius(1.5.dp.toPx()))
            y -= cell
        }
    }
    // Widmo na żywo (VU) pod segmentami.
    val bands = live.bands
    val bwid = size.width / bands.size
    for (i in bands.indices) {
        val hh = 2.dp.toPx() + bands[i] * (size.height - base - 6.dp.toPx())
        drawRoundRect(accent.copy(alpha = 0.25f + bands[i] * 0.6f), Offset(i * bwid + 1f, size.height - hh), Size(bwid - 2f, hh), CornerRadius(1.dp.toPx()))
    }
}

/** Taśma przesuwana: fala płynie pod nieruchomą głowicą, z podziałką co 30 s i odbiciem. */
private fun DrawScope.tape(w: FloatArray?, p: Float, posMs: Long, live: LiveSpectrum, accent: Color) {
    val n = w?.size ?: 600
    val step = TapeStep.toPx()
    val bar = step * 0.6f
    val mid = size.height * 0.45f
    val amp = mid - 6.dp.toPx()
    val cx = size.width / 2
    val head = p * n
    val first = max(0, (head - cx / step).toInt() - 1)
    val last = min(n - 1, (head + cx / step).toInt() + 1)
    val top = played(accent)
    for (i in first..last) {
        val x = cx + (i - head) * step
        val v = w?.getOrNull(i) ?: 0.15f
        val near = 1f + live.level * 0.4f * max(0f, 1f - abs(i - head) / 6f)
        val h = max(2.dp.toPx(), v * amp * near)
        val edge = (1f - abs(x - cx) / cx).coerceIn(0f, 1f)
        val a = 0.2f + 0.8f * edge
        val c = if (i < head) top else Color.White.copy(alpha = 0.3f)
        drawRoundRect(c, Offset(x - bar / 2, mid - h), Size(bar, h), CornerRadius(bar / 2), alpha = a)
        // Odbicie poniżej osi
        drawRoundRect(c, Offset(x - bar / 2, mid + 2.dp.toPx()), Size(bar, h * 0.45f), CornerRadius(bar / 2), alpha = a * 0.28f)
    }
    // Podziałka czasu (co 30 s, dłuższa kreska co minutę) przesuwa się razem z taśmą.
    val dur = if (p > 0f) posMs / p else 0f
    if (dur > 0f) {
        val pxPerMs = n * step / dur
        val startMs = ((posMs - cx / pxPerMs) / 30_000f).toInt().coerceAtLeast(0) * 30_000L
        var t = startMs
        while (true) {
            val x = cx + (t - posMs) * pxPerMs
            if (x > size.width) break
            if (x >= 0) {
                val major = t % 60_000L == 0L
                val len = if (major) 8.dp.toPx() else 4.dp.toPx()
                drawLine(Color.White.copy(alpha = if (major) 0.35f else 0.18f), Offset(x, size.height - len), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            t += 30_000L
        }
    }
    drawLine(Color.White, Offset(cx, 2.dp.toPx()), Offset(cx, size.height - 2.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(accent, 5.dp.toPx(), Offset(cx, 4.dp.toPx()))
}

/** Spektrogram: kolumny widma powstają przy głowicy, kolor od akcentu do bieli wg siły. */
private fun DrawScope.spectrogram(w: FloatArray?, p: Float, live: LiveSpectrum, accent: Color) {
    val cx = size.width / 2
    val colW = 3.dp.toPx()
    val bands = Spectrum.BANDS / 2
    val cellH = size.height / bands
    var x = cx - colW
    val hist = live.history
    for (k in hist.indices.reversed()) {
        if (x < -colW) break
        val col = hist[k]
        val fade = (x / cx).coerceIn(0f, 1f)
        for (b in 0 until bands) {
            val v = max(col[b * 2], col[b * 2 + 1])
            if (v < 0.07f) continue
            val c = lerp(accent, Color.White, (v * v).coerceIn(0f, 1f))
            drawRect(c.copy(alpha = (v * fade).coerceIn(0f, 1f)), Offset(x, size.height - (b + 1) * cellH), Size(colW, cellH + 0.5f))
        }
        x -= colW
    }
    if (w != null) {
        val head = p * w.size
        val step = TapeStep.toPx()
        var i = head.toInt() + 1
        while (true) {
            val px = cx + (i - head) * step
            if (px > size.width || i >= w.size) break
            val h = max(2f, w[i] * size.height * 0.42f)
            val edge = (1f - (px - cx) / cx).coerceIn(0f, 1f)
            drawRoundRect(Color.White.copy(alpha = 0.2f), Offset(px - step * 0.3f, size.height / 2 - h), Size(step * 0.6f, h * 2), CornerRadius(step), alpha = edge)
            i++
        }
    }
    drawLine(Color.White, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
    drawRoundRect(accent, Offset(0f, size.height - 3.dp.toPx()), Size(size.width * p, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
}

/** Kaseta: dwie szpule – lewa maleje, prawa rośnie z postępem – i okienko z falą między nimi. */
private fun DrawScope.cassette(w: FloatArray?, p: Float, posMs: Long, live: LiveSpectrum, accent: Color) {
    val cy = size.height / 2
    val rMax = size.height * 0.46f
    val rHub = rMax * 0.32f
    val lx = rMax + 4.dp.toPx()
    val rx = size.width - rMax - 4.dp.toPx()
    val rl = rHub + (rMax - rHub) * kotlin.math.sqrt(1f - p)
    val rr = rHub + (rMax - rHub) * kotlin.math.sqrt(p)
    val angle = (posMs / 1000f * 140f) % 360f
    // Taśma między szpulami
    drawLine(Color(0xFF4A3B2A), Offset(lx, cy + rl), Offset(rx, cy + rr), strokeWidth = 2.dp.toPx())
    fun reel(cx: Float, r: Float, a: Float) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF3A2C1E), Color(0xFF1C140C)), Offset(cx, cy), r), r, Offset(cx, cy))
        var rr2 = r - 2.dp.toPx()
        while (rr2 > rHub + 2.dp.toPx()) { drawCircle(Color.White.copy(alpha = 0.035f), rr2, Offset(cx, cy), style = Stroke(1f)); rr2 -= 3.dp.toPx() }
        drawCircle(Color(0xFFE8E4DC), rHub, Offset(cx, cy))
        rotate(a, Offset(cx, cy)) {
            for (k in 0 until 6) {
                rotate(k * 60f, Offset(cx, cy)) {
                    drawRoundRect(Color(0xFF222222), Offset(cx - 1.5.dp.toPx(), cy - rHub * 0.85f), Size(3.dp.toPx(), rHub * 0.35f), CornerRadius(1.dp.toPx()))
                }
            }
        }
        drawCircle(accent, rHub * 0.28f, Offset(cx, cy))
    }
    reel(lx, rl, angle)
    reel(rx, rr, angle)
    // Okienko z falą wokół bieżącej pozycji
    val wl = lx + rMax + 6.dp.toPx()
    val wr = rx - rMax - 6.dp.toPx()
    if (wr > wl) {
        drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(wl, cy - size.height * 0.2f), Size(wr - wl, size.height * 0.4f), CornerRadius(6.dp.toPx()))
        val n = 24
        val slot = (wr - wl) / n
        for (i in 0 until n) {
            val f = (p - 0.5f / 10f + i / (n * 10f))
            val idx = ((f.coerceIn(0f, 1f)) * 200).toInt()
            val v = sample(w, idx, 200)
            val h = max(1.5.dp.toPx(), v * size.height * 0.17f * (1f + live.level * 0.3f))
            drawRoundRect(if (i < n / 2) played(accent) else Color.White.copy(alpha = 0.35f), Offset(wl + i * slot + slot * 0.2f, cy - h), Size(slot * 0.6f, h * 2), CornerRadius(slot))
        }
    }
}
