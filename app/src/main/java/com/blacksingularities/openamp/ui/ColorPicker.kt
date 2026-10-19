package com.blacksingularities.openamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.ui.theme.AccentChoices

/** Wybór dowolnego koloru: pole nasycenie/jasność, pasek odcienia, kod HEX i gotowe propozycje. */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)))
    var hex by remember(color) { mutableStateOf(String.format("%06X", color.toArgb() and 0xFFFFFF)) }

    fun setFrom(argb: Int) {
        val t = FloatArray(3); android.graphics.Color.colorToHSV(argb, t)
        h = t[0]; sat = t[1]; v = t[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Kolor akcentu")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Nasycenie (poziomo) × jasność (pionowo)
                Canvas(
                    Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            fun set(o: Offset) { sat = (o.x / size.width).coerceIn(0f, 1f); v = 1f - (o.y / size.height).coerceIn(0f, 1f) }
                            detectDragGestures(onDragStart = { set(it) }) { ch, _ -> ch.consume(); set(ch.position) }
                        }
                        .pointerInput(Unit) { detectTapGestures { o -> sat = (o.x / size.width).coerceIn(0f, 1f); v = 1f - (o.y / size.height).coerceIn(0f, 1f) } },
                ) {
                    val hue = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, 1f)))
                    drawRect(Brush.horizontalGradient(listOf(Color.White, hue)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val c = Offset(sat * size.width, (1 - v) * size.height)
                    drawCircle(Color.Black.copy(alpha = 0.5f), 13.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, 11.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                }
                // Odcień
                Canvas(
                    Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(onDragStart = { h = (it.x / size.width * 360f).coerceIn(0f, 359.9f) }) { ch, _ ->
                                ch.consume(); h = (ch.position.x / size.width * 360f).coerceIn(0f, 359.9f)
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures { h = (it.x / size.width * 360f).coerceIn(0f, 359.9f) } },
                ) {
                    drawRect(Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f % 360f, 1f, 1f))) }))
                    val x = h / 360f * size.width
                    drawRoundRect(Color.White, Offset(x - 4.dp.toPx(), 2.dp.toPx()), androidx.compose.ui.geometry.Size(8.dp.toPx(), size.height - 4.dp.toPx()), CornerRadius(4.dp.toPx()), style = Stroke(3.dp.toPx()))
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(color))
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        hex, { t ->
                            hex = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                            if (hex.length == 6) setFrom(0xFF000000.toInt() or hex.toInt(16))
                        },
                        prefix = { Text("#") }, singleLine = true, label = { Text("HEX") }, modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (AccentChoices + listOf(0xFFFF2D55, 0xFF00E5FF, 0xFFC6FF00, 0xFFFFD600, 0xFFFF6D00, 0xFF651FFF, 0xFF00C853, 0xFFFFFFFF).map { it.toInt() }).forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                .border(2.dp, if (c == color.toArgb()) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { setFrom(c) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color.toArgb()); onDismiss() }) { Text(tr("Gotowe")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Anuluj")) } },
    )
}
