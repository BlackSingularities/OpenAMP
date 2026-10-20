package com.blacksingularities.openamp.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.data.AppSettings
import com.blacksingularities.openamp.data.NpBackground
import com.blacksingularities.openamp.data.Track
import kotlin.math.cos
import kotlin.math.sin

/** Tło ekranu odtwarzania w wybranym stylu. */
@Composable
fun NowPlayingBackground(track: Track?, mode: NpBackground, accent: Color, live: LiveSpectrum, s: AppSettings) {
    when (mode) {
        NpBackground.LIVE -> LiveBackground(track, s.liveColors, s.liveCustom, accent, live, s.liveMotion, s.liveReact, s.liveIntensity)
        NpBackground.BLACK -> Box(Modifier.fillMaxSize().background(Color.Black))
        NpBackground.BLUR -> {
            Box(Modifier.fillMaxSize().background(Color.Black))
            Artwork(
                track,
                Modifier.fillMaxSize().scale(1.3f).then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(60.dp) else Modifier).graphicsLayer { alpha = 0.55f },
                size = 300, shape = RoundedCornerShape(0.dp), crossfade = true,
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f)))))
        }
        NpBackground.GRADIENT -> Box(
            Modifier.fillMaxSize().background(Color.Black).background(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.08f), Color.Transparent), center = Offset(0f, 0f), radius = 1800f),
            ),
        )
        NpBackground.AURORA -> Aurora(accent, live)
    }
}

/** Zorza: miękkie plamy koloru dryfujące w rytm muzyki (poziom i pasma widma). */
@Composable
private fun Aurora(accent: Color, live: LiveSpectrum) {
    val t = rememberInfiniteTransition(label = "aurora")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "ph")
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(accentArgb(accent), it) }
    val c2 = Color(android.graphics.Color.HSVToColor(floatArrayOf((hsv[0] + 40) % 360, hsv[1], hsv[2])))
    val c3 = Color(android.graphics.Color.HSVToColor(floatArrayOf((hsv[0] + 320) % 360, hsv[1], hsv[2] * 0.9f)))
    Canvas(Modifier.fillMaxSize().background(Color.Black)) {
        @Suppress("UNUSED_VARIABLE") val tick = live.tick
        val bass = (live.bands.take(6).average().toFloat())
        val mid = (live.bands.drop(16).take(12).average().toFloat())
        val r = size.maxDimension
        fun blob(c: Color, cx: Float, cy: Float, rad: Float, a: Float) =
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(cx, cy), rad), rad, Offset(cx, cy))
        blob(accent, size.width * (0.3f + 0.2f * cos(phase)), size.height * (0.25f + 0.1f * sin(phase * 2)), r * (0.45f + bass * 0.25f), 0.55f)
        blob(c2, size.width * (0.75f + 0.15f * sin(phase)), size.height * (0.45f + 0.12f * cos(phase)), r * (0.38f + mid * 0.25f), 0.45f)
        blob(c3, size.width * (0.4f + 0.25f * sin(phase * 0.7f)), size.height * (0.85f + 0.08f * cos(phase * 1.3f)), r * 0.42f, 0.35f)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
    }
}

private fun accentArgb(c: Color): Int = android.graphics.Color.argb((c.alpha * 255).toInt(), (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

/** Płyta winylowa: okładka jako etykieta, rowki, obrót podczas odtwarzania. */
@Composable
fun Vinyl(track: Track?, playing: Boolean, accent: Color, modifier: Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        var last = 0L
        while (playing) {
            withFrameNanos { now ->
                if (last != 0L) angle = (angle + (now - last) / 1e9f * 120f) % 360f // 20 obr./min
                last = now
            }
        }
    }
    Box(modifier.clip(CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().rotate(angle)) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(Color(0xFF1A1A1C), Color(0xFF050505)), center, r), r)
            var rr = r * 0.97f
            var i = 0
            while (rr > r * 0.38f) {
                drawCircle(Color.White.copy(alpha = if (i % 7 == 0) 0.07f else 0.025f), rr, center, style = Stroke(1f))
                rr -= r * 0.012f; i++
            }
            // Odblask światła
            drawArc(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent), center),
                0f, 360f, true)
            drawCircle(accent.copy(alpha = 0.8f), r * 0.375f, center, style = Stroke(2.dp.toPx()))
        }
        Artwork(track, Modifier.fillMaxWidth(0.72f).aspectRatio(1f).rotate(angle), size = 600, shape = CircleShape)
        Box(Modifier.fillMaxWidth(0.045f).aspectRatio(1f).background(Color.Black, CircleShape))
    }
}
