package com.blacksingularities.openamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.playback.AudioFx
import com.blacksingularities.openamp.playback.EqState
import java.util.Locale
import kotlin.math.roundToInt

private fun freqLabel(hz: Int) = if (hz >= 1000) String.format(Locale.ROOT, if (hz % 1000 == 0) "%.0fk" else "%.1fk", hz / 1000f) else "$hz"

@Composable
fun EqualizerScreen(vm: MainViewModel, bottomPadding: Dp) {
    val eq by AudioFx.state.collectAsState()
    var saving by remember { mutableStateOf(false) }
    if (saving) NameDialog(tr("Zapisz preset"), tr("Nazwa presetu"), "", { saving = false }) { AudioFx.saveUserPreset(it); saving = false }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz")) }
            Text(tr("Korektor"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { AudioFx.reset() }) { Icon(Icons.Rounded.RestartAlt, tr("Resetuj")) }
            IconButton(onClick = { saving = true }, enabled = eq.available) { Icon(Icons.Rounded.Save, tr("Zapisz preset")) }
            Switch(eq.enabled, { AudioFx.setEnabled(it) }, modifier = Modifier.padding(end = 12.dp))
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding + 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!eq.available) {
                item {
                    Text(
                        tr("Ten telefon nie udostępnia korektora systemowego.") + "\n" + tr("Efekty działają po rozpoczęciu odtwarzania."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                EqCard {
                    Text(tr("Preset"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AudioFx.builtInNames.forEach { n ->
                            FilterChip(selected = eq.preset == n, onClick = { AudioFx.applyPreset(n) }, label = { Text(tr(n)) }, enabled = eq.available)
                        }
                        if (eq.preset == "Własny") FilterChip(selected = true, onClick = {}, label = { Text(tr("Własny")) })
                    }
                    if (eq.userPresets.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(tr("Twoje presety"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            eq.userPresets.forEach { p ->
                                FilterChip(
                                    selected = eq.preset == p.name,
                                    onClick = { AudioFx.applyPreset(p.name) },
                                    label = { Text(p.name) },
                                    trailingIcon = {
                                        Icon(Icons.Rounded.Close, tr("Usuń"), Modifier.pointerInput(p.name) { detectTapGestures { AudioFx.deleteUserPreset(p.name) } })
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                EqCard {
                    Text(tr("Korektor graficzny"), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    EqCurve(eq, Modifier.fillMaxWidth().height(260.dp))
                }
            }
            item {
                EqCard {
                    EffectSlider(tr("Wzmocnienie basów"), eq.bass, 1000, eq.bassSupported && eq.available) { AudioFx.setBass(it) }
                    EffectSlider(tr("Przestrzenność"), eq.virtualizer, 1000, eq.virtualizerSupported && eq.available) { AudioFx.setVirtualizer(it) }
                    EffectSlider(tr("Głośność (wzmocnienie)"), eq.loudness, 1500, eq.available, unit = "dB", scale = 100f) { AudioFx.setLoudness(it) }
                }
            }
        }
    }
}

@Composable
private fun EqCard(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun EffectSlider(label: String, value: Int, max: Int, enabled: Boolean, unit: String = "%", scale: Float = max / 100f, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            if (unit == "%") "${(value / scale).roundToInt()}%" else String.format(Locale.ROOT, "+%.1f dB", value / scale),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(value.toFloat(), { onChange(it.roundToInt()) }, valueRange = 0f..max.toFloat(), enabled = enabled)
}

/**
 * Korektor jako krzywa: każde pasmo to uchwyt, który przeciąga się w pionie; między uchwytami
 * gładka krzywa z wypełnieniem.
 */
@Composable
private fun EqCurve(eq: EqState, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    val current by rememberUpdatedState(eq)
    var active by remember { mutableStateOf(-1) }
    val n = eq.levels.size
    if (n == 0) { Text("—"); return }
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            eq.levels.forEachIndexed { i, l ->
                Text(
                    String.format(Locale.ROOT, "%+.1f", l / 100f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == active) accent else text,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Canvas(
            Modifier.weight(1f).fillMaxWidth().pointerInput(n) {
                fun bandAt(x: Float) = ((x / size.width) * n).toInt().coerceIn(0, n - 1)
                fun levelAt(y: Float): Int {
                    val s = current
                    val pad = 14.dp.toPx()
                    val f = 1f - ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                    return ((s.minLevel + f * (s.maxLevel - s.minLevel)) / 50f).roundToInt() * 50
                }
                detectDragGestures(
                    onDragStart = { o -> active = bandAt(o.x); AudioFx.setBand(active, levelAt(o.y)) },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 },
                ) { change, _ ->
                    change.consume()
                    if (active >= 0) AudioFx.setBand(active, levelAt(change.position.y))
                }
            },
        ) {
            val pad = 14.dp.toPx()
            val h = size.height - 2 * pad
            val colW = size.width / n
            fun y(level: Int) = pad + h * (1f - (level - eq.minLevel).toFloat() / (eq.maxLevel - eq.minLevel))
            // Siatka
            for (db in listOf(eq.maxLevel, eq.maxLevel / 2, 0, eq.minLevel / 2, eq.minLevel)) {
                drawLine(grid, Offset(0f, y(db)), Offset(size.width, y(db)), strokeWidth = if (db == 0) 2f else 1f)
            }
            val pts = eq.levels.mapIndexed { i, l -> Offset(colW * (i + 0.5f), y(l)) }
            for (p in pts) drawLine(grid, Offset(p.x, pad), Offset(p.x, size.height - pad), strokeWidth = 1f)
            // Gładka krzywa (Catmull-Rom → Bézier)
            val path = Path().apply {
                moveTo(0f, pts.first().y)
                lineTo(pts.first().x, pts.first().y)
                for (i in 0 until pts.size - 1) {
                    val p0 = pts.getOrElse(i - 1) { pts[i] }
                    val p1 = pts[i]
                    val p2 = pts[i + 1]
                    val p3 = pts.getOrElse(i + 2) { p2 }
                    cubicTo(
                        p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
                        p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
                        p2.x, p2.y,
                    )
                }
                lineTo(size.width, pts.last().y)
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(size.width, y(0)); lineTo(0f, y(0)); close()
            }
            val a = if (eq.enabled) 1f else 0.4f
            drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f * a), accent.copy(alpha = 0.02f))))
            drawPath(path, accent.copy(alpha = a), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            pts.forEachIndexed { i, p ->
                drawCircle(Color.White, if (i == active) 11.dp.toPx() else 8.dp.toPx(), p)
                drawCircle(accent, if (i == active) 7.dp.toPx() else 5.dp.toPx(), p)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            eq.freqs.forEach { f ->
                Text(
                    freqLabel(f), style = MaterialTheme.typography.labelSmall, color = text,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
