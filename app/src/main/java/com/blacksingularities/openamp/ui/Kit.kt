package com.blacksingularities.openamp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Panel w stylu urządzenia: ciemna płyta z cienką obwódką w kolorze akcentu. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(c.surfaceContainer)
            .border(1.dp, Brush.verticalGradient(listOf(c.primary.copy(alpha = 0.28f), c.outlineVariant.copy(alpha = 0.25f))), RoundedCornerShape(26.dp))
            .padding(padding),
    ) { Column { content() } }
}

/** Nagłówek sekcji: małe wersaliki z rozstrzeleniem i kropką w kolorze akcentu. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

val MonoNumbers = FontFamily.Monospace

/**
 * Pokrętło: przeciąganie w pionie zmienia wartość, podwójne dotknięcie przywraca domyślną.
 * Łuk 270° z podświetleniem od wartości neutralnej.
 */
@Composable
fun Knob(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String,
    modifier: Modifier = Modifier,
    neutral: Float = range.start,
    default: Float = neutral,
    enabled: Boolean = true,
    size: Dp = 76.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val cur by rememberUpdatedState(value)
    val span = range.endInclusive - range.start
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(size)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { ch, dy ->
                        ch.consume()
                        onChange((cur - dy / (this.size.height * 2.2f) * span).coerceIn(range.start, range.endInclusive))
                    }
                }
                .pointerInput(enabled) { if (enabled) detectTapGestures(onDoubleTap = { onChange(default) }) },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSizeOf(size)) {
                val stroke = 5.dp.toPx()
                val inset = stroke / 2 + 2.dp.toPx()
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val tl = Offset(inset, inset)
                fun angle(v: Float) = 135f + (v - range.start) / span * 270f
                drawArc(track, 135f, 270f, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val a0 = angle(neutral); val a1 = angle(value)
                val alpha = if (enabled) 1f else 0.35f
                drawArc(accent.copy(alpha = alpha), minOf(a0, a1), kotlin.math.abs(a1 - a0).coerceAtLeast(0.5f), false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                // Gałka z wskaźnikiem
                val r = this.size.minDimension / 2 - stroke * 2.6f
                drawCircle(Brush.radialGradient(listOf(Color(0xFF2E2A36), Color(0xFF121016)), center, r), r, center)
                drawCircle(Color.White.copy(alpha = 0.06f), r, center, style = Stroke(1.dp.toPx()))
                val rad = Math.toRadians(a1.toDouble())
                val p1 = Offset(center.x + cos(rad).toFloat() * r * 0.45f, center.y + sin(rad).toFloat() * r * 0.45f)
                val p2 = Offset(center.x + cos(rad).toFloat() * r * 0.85f, center.y + sin(rad).toFloat() * r * 0.85f)
                drawLine(accent.copy(alpha = alpha), p1, p2, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Text(format(value), fontFamily = MonoNumbers, style = MaterialTheme.typography.labelMedium, color = if (value != neutral) accent else MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
    }
}

private fun Modifier.matchParentSizeOf(size: Dp) = this.size(size)

/**
 * Pionowy suwak (fader) z wypełnieniem od zera – jak w mikserze. Przeciąganie w dowolnym miejscu
 * ścieżki, podwójne dotknięcie zeruje.
 */
@Composable
fun Fader(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onSelect: () -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val tick = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val span = range.endInclusive - range.start
    val anim by animateFloatAsState(value, tween(120), label = "fader")
    Canvas(
        modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                fun v(y: Float) = (range.endInclusive - (y - 14.dp.toPx()) / (size.height - 28.dp.toPx()) * span).coerceIn(range.start, range.endInclusive)
                detectDragGestures(onDragStart = { o -> onSelect(); onChange(v(o.y)) }) { ch, _ -> ch.consume(); onChange(v(ch.position.y)) }
            }
            .pointerInput(enabled) { if (enabled) detectTapGestures(onTap = { onSelect() }, onDoubleTap = { onChange(0f) }) },
    ) {
        val pad = 14.dp.toPx()
        val h = size.height - pad * 2
        val cx = size.width / 2
        val w = 6.dp.toPx()
        fun y(v: Float) = pad + (range.endInclusive - v) / span * h
        // Podziałka
        for (i in 0..6) {
            val ty = pad + h * i / 6
            drawLine(tick, Offset(cx - 11.dp.toPx(), ty), Offset(cx - 7.dp.toPx(), ty), strokeWidth = 1.dp.toPx())
            drawLine(tick, Offset(cx + 7.dp.toPx(), ty), Offset(cx + 11.dp.toPx(), ty), strokeWidth = 1.dp.toPx())
        }
        drawRoundRect(track, Offset(cx - w / 2, pad), Size(w, h), CornerRadius(w))
        val y0 = y(0f.coerceIn(range.start, range.endInclusive)); val y1 = y(anim)
        val a = if (enabled) 1f else 0.35f
        drawRoundRect(
            Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.45f)), startY = minOf(y0, y1), endY = maxOf(y0, y1) + 1f),
            Offset(cx - w / 2, minOf(y0, y1)), Size(w, kotlin.math.abs(y1 - y0)), CornerRadius(w), alpha = a,
        )
        // Uchwyt
        val tw = 26.dp.toPx(); val th = 14.dp.toPx()
        if (selected) drawRoundRect(accent.copy(alpha = 0.25f), Offset(cx - tw / 2 - 4.dp.toPx(), y1 - th / 2 - 4.dp.toPx()), Size(tw + 8.dp.toPx(), th + 8.dp.toPx()), CornerRadius(th))
        drawRoundRect(Color(0xFFEDEAF2), Offset(cx - tw / 2, y1 - th / 2), Size(tw, th), CornerRadius(th / 2), alpha = if (enabled) 1f else 0.5f)
        drawLine(accent, Offset(cx - tw / 2 + 5.dp.toPx(), y1), Offset(cx + tw / 2 - 5.dp.toPx(), y1), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Duży okrągły włącznik z poświatą. */
@Composable
fun PowerButton(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val color by animateColorAsState(if (on) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), tween(250), label = "pw")
    Box(
        modifier.size(48.dp)
            .shadow(if (on) 14.dp else 0.dp, CircleShape, spotColor = accent, ambientColor = accent)
            .clip(CircleShape)
            .background(if (on) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.5.dp, color, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.PowerSettingsNew, null, tint = color) }
}

/** Przełącznik w kształcie pigułki (zamiast standardowego Switcha w panelach audio). */
@Composable
fun PillToggle(text: String, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (on) accent.copy(alpha = 0.2f) else Color.Transparent, label = "pt")
    val fg by animateColorAsState(if (on) accent else MaterialTheme.colorScheme.onSurfaceVariant, label = "pf")
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg)
            .border(1.dp, fg.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(if (on) accent else fg.copy(alpha = 0.4f), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
