package com.blacksingularities.openamp.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.blacksingularities.openamp.data.LiveColors
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Gotowe palety tła „Żywe kolory” (cztery barwy każda). */
private val Presets: Map<LiveColors, List<Int>> = mapOf(
    LiveColors.SUNSET to listOf(0xFFFF5E62, 0xFFFF9966, 0xFFFFC371, 0xFFC33764),
    LiveColors.OCEAN to listOf(0xFF00C6FF, 0xFF0072FF, 0xFF00F5A0, 0xFF3A47D5),
    LiveColors.NEON to listOf(0xFFFF00CC, 0xFF3333FF, 0xFF00FFF0, 0xFF9D00FF),
    LiveColors.FOREST to listOf(0xFF2ECC71, 0xFFA8E063, 0xFF1D976C, 0xFF56AB2F),
    LiveColors.CANDY to listOf(0xFFFF9A9E, 0xFFFAD0C4, 0xFFA18CD1, 0xFFFBC2EB),
    LiveColors.MONO to listOf(0xFFE6E6E6, 0xFF9E9E9E, 0xFFBDBDBD, 0xFF757575),
).mapValues { (_, v) -> v.map { it.toInt() } }

fun liveColorsLabel(c: LiveColors) = when (c) {
    LiveColors.ARTWORK -> tr("Z okładki")
    LiveColors.ACCENT -> tr("Z koloru akcentu")
    LiveColors.SUNSET -> tr("Zachód słońca")
    LiveColors.OCEAN -> tr("Ocean")
    LiveColors.NEON -> tr("Neon")
    LiveColors.FOREST -> tr("Las")
    LiveColors.CANDY -> tr("Pastele")
    LiveColors.MONO -> tr("Monochromatyczne")
    LiveColors.CUSTOM -> tr("Własny kolor")
}

/** Cztery barwy wokół jednego koloru: sam kolor, sąsiednie odcienie i jaśniejszy kontrapunkt. */
fun harmonyOf(base: Int): List<Int> {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(base, it) }
    fun shift(dh: Float, s: Float = hsl[1], l: Float = hsl[2]) =
        ColorUtils.HSLToColor(floatArrayOf((hsl[0] + dh + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0.2f, 0.85f)))
    return listOf(shift(0f), shift(28f), shift(-32f, l = hsl[2] * 0.85f), shift(160f, s = hsl[1] * 0.7f, l = hsl[2] + 0.08f))
}

/**
 * Kilka wyraźnych barw okładki (nie tylko najżywsza): histogram odcieni ważony nasyceniem,
 * wybierane szczyty odległe od siebie o co najmniej 35°. Za mało barw – dopełnienie harmonią.
 */
fun artPalette(bmp: Bitmap, count: Int = 4): List<Int>? {
    val small = Bitmap.createScaledBitmap(bmp, 40, 40, true)
    val weight = FloatArray(36)
    val satSum = FloatArray(36)
    val lightSum = FloatArray(36)
    val hsl = FloatArray(3)
    for (y in 0 until small.height) for (x in 0 until small.width) {
        ColorUtils.colorToHSL(small.getPixel(x, y), hsl)
        val s = hsl[1]; val l = hsl[2]
        if (l < 0.1f || l > 0.93f || s < 0.12f) continue
        val b = (hsl[0] / 10f).toInt().coerceIn(0, 35)
        val w = s * (1f - abs(l - 0.55f))
        weight[b] += w; satSum[b] += s * w; lightSum[b] += l * w
    }
    val picked = mutableListOf<Int>()
    val order = weight.indices.sortedByDescending { weight[it] }
    for (b in order) {
        if (weight[b] < 1.5f || picked.size >= count) break
        if (picked.any { p -> min(abs(p - b), 36 - abs(p - b)) < 4 }) continue
        picked += b
    }
    if (picked.isEmpty()) return null
    val colors = picked.map { b ->
        val s = (satSum[b] / weight[b]).coerceIn(0.45f, 0.95f)
        val l = (lightSum[b] / weight[b]).coerceIn(0.42f, 0.66f)
        ColorUtils.HSLToColor(floatArrayOf(b * 10f + 5f, s, l))
    }
    return (colors + harmonyOf(colors.first()).drop(1)).take(count)
}

/**
 * Tło „Żywe kolory”: miękkie, nakładające się plamy światła płynące po krzywych Lissajous.
 * Bas powiększa i rozjaśnia plamy, średnica i góra pasma poruszają pozostałymi, uderzenie basu
 * daje krótki błysk, a ogólna głośność przyspiesza ruch. Bez muzyki tło dryfuje spokojnie.
 */
@Composable
fun LiveBackground(
    track: Track?,
    colors: LiveColors,
    custom: Int,
    accent: Color,
    live: LiveSpectrum,
    motion: Int,
    react: Int,
    intensity: Int,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val context = LocalContext.current
    var artColors by remember { mutableStateOf<List<Int>?>(null) }
    LaunchedEffect(track?.id, colors) {
        if (colors != LiveColors.ARTWORK) return@LaunchedEffect
        artColors = track?.let { t ->
            withContext(Dispatchers.Default) { ArtworkLoader.load(context, t, 300)?.let { artPalette(it) } }
        }
    }
    val target: List<Int> = when (colors) {
        LiveColors.ARTWORK -> artColors ?: harmonyOf(accent.toArgb())
        LiveColors.ACCENT -> harmonyOf(accent.toArgb())
        LiveColors.CUSTOM -> harmonyOf(custom)
        else -> Presets.getValue(colors)
    }
    // Zmiana utworu albo palety – barwy przechodzą płynnie, bez skoku.
    val c0 by animateColorAsState(Color(target[0]), tween(1600), label = "lc0")
    val c1 by animateColorAsState(Color(target[1 % target.size]), tween(1600), label = "lc1")
    val c2 by animateColorAsState(Color(target[2 % target.size]), tween(1600), label = "lc2")
    val c3 by animateColorAsState(Color(target[3 % target.size]), tween(1600), label = "lc3")

    // Własny zegar animacji: tempo zależy od suwaka ruchu i od głośności muzyki.
    val clock = remember { FloatArray(3) } // [czas, ostatnia klatka (s), wygładzony poziom]
    val speed = 0.08f + motion.coerceIn(0, 100) / 100f * 0.5f
    val k = react.coerceIn(0, 100) / 100f
    val power = 0.35f + intensity.coerceIn(0, 100) / 100f * 0.65f

    Canvas(modifier) {
        val now = live.tick / 1e9f
        val dt = if (clock[1] == 0f) 0f else (now - clock[1]).coerceIn(0f, 0.1f)
        clock[1] = now
        clock[2] = clock[2] * 0.9f + live.level * 0.1f
        clock[0] += dt * speed * (1f + clock[2] * 1.6f * k)
        val t = clock[0]

        val n = live.bands.size
        fun band(from: Float, to: Float): Float {
            val a = (from * n).toInt().coerceIn(0, n - 1)
            val b = max(a + 1, (to * n).toInt()).coerceAtMost(n)
            var s = 0f
            for (i in a until b) s += live.bands[i]
            return s / (b - a)
        }
        val bass = band(0f, 0.12f) * k
        val lowMid = band(0.12f, 0.3f) * k
        val mid = band(0.3f, 0.55f) * k
        val high = band(0.55f, 1f) * k
        val beat = live.beat * k

        val w = size.width
        val h = size.height
        val r = size.maxDimension

        // Podkład: bardzo ciemna wersja pierwszej barwy – tło nie jest czarną dziurą, a tekst zostaje czytelny.
        drawRect(lerp(Color.Black, c0, 0.10f + 0.06f * power))

        fun blob(c: Color, x: Float, y: Float, rad: Float, a: Float) {
            val center = Offset(x, y)
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = a.coerceIn(0f, 1f)), c.copy(alpha = a * 0.35f), Color.Transparent), center, rad),
                rad, center, blendMode = BlendMode.Screen,
            )
        }
        // Barwy plam powoli przenikają się nawzajem.
        fun mix(a: Color, b: Color, phase: Float) = lerp(a, b, 0.5f + 0.5f * sin(t * 0.35f + phase))
        val a0 = 0.55f * power
        blob(mix(c0, c1, 0f), w * (0.30f + 0.22f * sin(t * 0.71f)), h * (0.22f + 0.12f * cos(t * 0.53f)), r * (0.42f + 0.30f * bass + 0.10f * beat), a0 + 0.30f * bass)
        blob(mix(c1, c2, 1.7f), w * (0.78f + 0.16f * cos(t * 0.61f + 1f)), h * (0.38f + 0.18f * sin(t * 0.47f + 2f)), r * (0.36f + 0.26f * lowMid), a0 * 0.9f + 0.25f * lowMid)
        blob(mix(c2, c3, 3.1f), w * (0.42f + 0.30f * sin(t * 0.43f + 4f)), h * (0.70f + 0.14f * cos(t * 0.67f + 1f)), r * (0.40f + 0.24f * mid), a0 * 0.85f + 0.22f * mid)
        blob(mix(c3, c0, 4.4f), w * (0.15f + 0.20f * cos(t * 0.83f + 3f)), h * (0.88f + 0.08f * sin(t * 0.91f)), r * (0.30f + 0.22f * high), a0 * 0.7f + 0.25f * high)
        blob(mix(c1, c3, 2.2f), w * (0.88f + 0.10f * sin(t * 0.37f + 5f)), h * (0.05f + 0.10f * cos(t * 0.29f + 2f)), r * (0.26f + 0.18f * high + 0.12f * beat), a0 * 0.6f + 0.2f * beat)

        // Błysk na uderzenie basu – krótki, delikatny, w barwie głównej.
        if (beat > 0.05f) drawRect(c0.copy(alpha = 0.10f * beat * power), blendMode = BlendMode.Screen)

        // Przyciemnienie u dołu i winieta: tytuł, taśma i przyciski zawsze czytelne.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.82f)), 0f, h))
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), Offset(w / 2, h * 0.4f), r * 0.85f))
    }
}
