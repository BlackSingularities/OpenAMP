package com.blacksingularities.openamp.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.blacksingularities.openamp.data.ThemeMode

val AccentChoices = listOf(
    0xFFB45CFF, 0xFFFF5E7E, 0xFF4FD1FF, 0xFF3DDC97, 0xFFFFB547, 0xFFFF7A3D, 0xFF7C8CFF, 0xFFE0E0E0,
).map { it.toInt() }

/** Najbardziej „żywy” kolor okładki: histogram odcieni ważony nasyceniem i jasnością. */
fun vibrantColor(bmp: Bitmap): Int? {
    val small = Bitmap.createScaledBitmap(bmp, 48, 48, true)
    val buckets = FloatArray(36)
    val sat = FloatArray(36)
    val hsl = FloatArray(3)
    for (y in 0 until small.height) for (x in 0 until small.width) {
        ColorUtils.colorToHSL(small.getPixel(x, y), hsl)
        val s = hsl[1]; val l = hsl[2]
        if (l < 0.15f || l > 0.9f || s < 0.2f) continue
        val b = (hsl[0] / 10f).toInt().coerceIn(0, 35)
        val w = s * (1f - kotlin.math.abs(l - 0.55f))
        buckets[b] += w
        sat[b] += s * w
    }
    val best = buckets.indices.maxByOrNull { buckets[it] } ?: return null
    if (buckets[best] < 3f) return null
    val s = (sat[best] / buckets[best]).coerceIn(0.5f, 0.9f)
    return ColorUtils.HSLToColor(floatArrayOf(best * 10f + 5f, s, 0.62f))
}

private fun scheme(accent: Color, mode: ThemeMode, systemDark: Boolean): ColorScheme {
    val dark = when (mode) { ThemeMode.SYSTEM -> systemDark; ThemeMode.LIGHT -> false; else -> true }
    val black = mode == ThemeMode.BLACK
    return if (dark) {
        val bg = if (black) Color.Black else Color(0xFF0E0C13)
        val surface = if (black) Color(0xFF050505) else Color(0xFF15121C)
        darkColorScheme(
            primary = accent,
            onPrimary = if (accent.luminance() > 0.5f) Color.Black else Color.White,
            primaryContainer = accent.copy(alpha = 0.28f).compositeOver(surface),
            onPrimaryContainer = Color.White,
            secondary = accent.copy(alpha = 0.8f).compositeOver(Color.White),
            secondaryContainer = accent.copy(alpha = 0.18f).compositeOver(surface),
            onSecondaryContainer = Color.White,
            tertiary = accent,
            background = bg,
            surface = surface,
            surfaceVariant = Color(0xFF231F2C),
            surfaceContainer = accent.copy(alpha = 0.05f).compositeOver(Color(0xFF1A1721)),
            surfaceContainerHigh = accent.copy(alpha = 0.07f).compositeOver(Color(0xFF221E2A)),
            surfaceContainerHighest = accent.copy(alpha = 0.09f).compositeOver(Color(0xFF2A2633)),
            surfaceContainerLow = if (black) Color(0xFF0A0A0A) else Color(0xFF17141D),
            onBackground = Color(0xFFF1EEF6),
            onSurface = Color(0xFFF1EEF6),
            onSurfaceVariant = Color(0xFFB9B3C4),
            outline = Color(0xFF4A4554),
            outlineVariant = Color(0xFF332F3B),
        )
    } else {
        val deep = Color(ColorUtils.blendARGB(accent.toArgb(), 0xFF000000.toInt(), 0.25f))
        lightColorScheme(
            primary = deep,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.22f).compositeOver(Color.White),
            onPrimaryContainer = Color(0xFF1B1622),
            secondaryContainer = accent.copy(alpha = 0.14f).compositeOver(Color.White),
            tertiary = deep,
            background = Color(0xFFFBF9FD),
            surface = Color(0xFFFBF9FD),
            surfaceContainer = accent.copy(alpha = 0.05f).compositeOver(Color(0xFFF2EFF6)),
            surfaceContainerHigh = accent.copy(alpha = 0.07f).compositeOver(Color(0xFFECE8F1)),
            surfaceContainerHighest = accent.copy(alpha = 0.09f).compositeOver(Color(0xFFE5E1EB)),
            surfaceContainerLow = Color(0xFFF6F3F9),
        )
    }
}

private fun Color.luminance(): Float = ColorUtils.calculateLuminance(toArgb()).toFloat()

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun OpenAmpTheme(accent: Int, mode: ThemeMode, content: @Composable () -> Unit) {
    val animated by animateColorAsState(Color(accent), tween(700), label = "accent")
    MaterialTheme(
        colorScheme = scheme(animated, mode, isSystemInDarkTheme()),
        typography = AppTypography,
        content = content,
    )
}
