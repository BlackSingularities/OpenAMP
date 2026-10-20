package com.blacksingularities.openamp.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.data.SeekStyle
import com.blacksingularities.openamp.data.VisualizerStyle
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.widget.WidgetProgressStyle
import com.blacksingularities.openamp.widget.WidgetRenderer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Podglądy stylów do list w ustawieniach: wizualizacje i taśmy rysowane przez te same funkcje
 * co ekran odtwarzania, tylko na sztucznym, łagodnie pulsującym widmie (bez odtwarzania muzyki).
 */
@Composable
fun rememberDemoSpectrum(history: Int = 48): LiveSpectrum {
    val live = remember { LiveSpectrum() }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var lastHistory = 0L
        while (true) {
            val now = withFrameNanos { it }
            val t = (now - start) / 1_000_000_000f
            val n = live.bands.size
            for (i in 0 until n) {
                val tilt = 1f - 0.55f * i / n
                live.bands[i] = (0.18f + 0.62f * abs(sin(t * 2.3f + i * 0.37f)) * tilt + 0.12f * abs(sin(t * 5.1f - i * 0.9f))).coerceIn(0f, 1f)
            }
            live.beat = max(0f, sin(t * 4.2f)).let { it * it * it }
            live.level = 0.45f + 0.35f * abs(sin(t * 1.7f))
            for (i in live.wave.indices) live.wave[i] = sin(i * 0.19f + t * 9f) * 0.55f * live.level + sin(i * 0.05f - t * 3f) * 0.2f
            if (now - lastHistory > 40_000_000L) {
                lastHistory = now
                live.history.addLast(live.bands.copyOf())
                while (live.history.size > history) live.history.removeFirst()
            }
            live.tick = now
        }
    }
    return live
}

/** Zastępcza fala utworu do podglądów taśm. */
val DemoWave: FloatArray = FloatArray(240) { i ->
    (0.25f + 0.55f * abs(sin(i * 0.11f)) * (0.6f + 0.4f * sin(i * 0.023f)) + 0.15f * abs(sin(i * 0.57f))).coerceIn(0.05f, 1f)
}

@Composable
fun VisualizerPreview(style: VisualizerStyle, live: LiveSpectrum, color: Color, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val round = Modifier.fillMaxHeight().aspectRatio(1f)
        when (style) {
            VisualizerStyle.BARS -> SpectrumBars(live, color, Modifier.fillMaxSize(), mirror = false)
            VisualizerStyle.WAVE -> WaveScope(live, color, Modifier.fillMaxSize())
            VisualizerStyle.CIRCLE -> CircleSpectrum(live, color, round, innerFraction = 0.55f)
            VisualizerStyle.PULSE -> PulseGlow(live, color, round)
            VisualizerStyle.MIRROR -> MirrorBars(live, color, Modifier.fillMaxSize())
            VisualizerStyle.RING_WAVE -> RingWave(live, color, round, innerFraction = 0.55f)
            VisualizerStyle.LINE -> SpectrumLine(live, color, Modifier.fillMaxSize())
            VisualizerStyle.NONE -> Text(tr("Brak"), color = Flat.Faded, fontSize = 11.sp)
        }
    }
}

@Composable
fun SeekPreview(style: SeekStyle, live: LiveSpectrum, accent: Color, modifier: Modifier) {
    SeekTape(style, DemoWave, posMs = (live.tick / 1_000_000L) % 240_000L, frac = 0.42f, live = live, accent = accent, scrubbing = false, modifier = modifier)
}

/** Podgląd stylu paska widżetu – ta sama bitmapa, którą rysuje widżet. */
@Composable
fun WidgetBarPreview(style: WidgetProgressStyle, appSeek: SeekStyle, accent: Int, modifier: Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = maxWidth.value.toInt().coerceAtLeast(40)
        val h = maxHeight.value.toInt().coerceIn(6, 22)
        val bmp: Bitmap = remember(style, appSeek, accent, w, h) { WidgetRenderer.progressPreview(context, style, appSeek, accent, w, h) }
        Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}
