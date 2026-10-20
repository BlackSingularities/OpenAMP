package com.blacksingularities.openamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Style
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import com.blacksingularities.openamp.playback.FilterType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.playback.AudioFx
import com.blacksingularities.openamp.playback.Dsp
import com.blacksingularities.openamp.playback.DspState
import com.blacksingularities.openamp.playback.EqBand
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private const val F_MIN = 20f
private const val F_MAX = 20000f
private const val DB_RANGE = 15f
private const val CURVE_N = 256

private fun xOf(f: Float, w: Float) = (ln(f / F_MIN) / ln(F_MAX / F_MIN)) * w
private fun fOf(x: Float, w: Float) = F_MIN * (F_MAX / F_MIN).pow((x / w).coerceIn(0f, 1f))

fun freqLabel(hz: Float): String = if (hz >= 1000) {
    val k = hz / 1000f
    if (k >= 10 || k == k.roundToInt().toFloat()) "${k.roundToInt()}k" else String.format(Locale.ROOT, "%.1fk", k)
} else "${hz.roundToInt()}"

private fun db(v: Float) = String.format(Locale.ROOT, "%+.1f", v)

@Composable
fun EqualizerScreen(vm: MainViewModel, bottomPadding: Dp) {
    val s by Dsp.state.collectAsState()
    val fx by AudioFx.state.collectAsState()
    val settings by vm.settings.collectAsState()
    val ps by vm.player.state.collectAsState()
    var selected by remember { mutableIntStateOf(-1) }
    var saving by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val live = rememberLiveSpectrum(ps.isPlaying)
    if (saving) NameDialog(tr("Zapisz preset"), tr("Nazwa presetu"), "", { saving = false }) { Dsp.savePreset(it); saving = false }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), { vm.back() }, background = Color.Transparent, size = 40.dp)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(tr("Korektor"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 22.sp)
                Text(if (s.enabled) tr(s.preset) else tr("Wyłączone"), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Box {
                RoundButton(Icons.Rounded.MoreVert, tr("Więcej"), { menu = true }, background = Color.Transparent, size = 40.dp)
                CompactMenu(menu, { menu = false }) {
                    CMenuItem(tr("Zapisz preset"), Icons.Rounded.Save) { menu = false; saving = true }
                    CMenuItem(tr("Automatyczny przedwzmacniacz"), Icons.Rounded.VolumeDown) { menu = false; Dsp.autoPreamp() }
                    CMenuItem(tr("Resetuj"), Icons.Rounded.RestartAlt) { menu = false; selected = -1; Dsp.reset() }
                }
            }
            Spacer(Modifier.width(4.dp))
            PowerButton(s.enabled, { Dsp.update { it.copy(enabled = !it.enabled) } })
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = bottomPadding + 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Wykres charakterystyki na tle widma na żywo
            item {
                Panel(padding = 10.dp) {
                    ResponseGraph(s, live, selected, { selected = it }, Modifier.fillMaxWidth().height(210.dp))
                    if (selected in s.bands.indices) BandEditor(s.bands[selected], selected, s.bands.size) { selected = -1 }
                }
            }
            // Fadery
            item {
                Panel(padding = 12.dp) {
                    Overline(tr("Pasma"), trailing = {
                        Text(tr("Przedwzmacniacz") + " " + db(s.preamp) + " dB", style = MaterialTheme.typography.labelMedium, fontFamily = MonoNumbers, color = MaterialTheme.colorScheme.primary)
                    })
                    val many = s.bands.size > 10
                    Row(if (many) Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()) {
                        s.bands.forEachIndexed { i, b ->
                            Column(if (many) Modifier.width(38.dp) else Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    db(b.gain).removeSuffix(".0"), fontFamily = MonoNumbers, style = MaterialTheme.typography.labelSmall,
                                    color = if (i == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Fader(
                                    b.gain, -Dsp.MAX_GAIN..Dsp.MAX_GAIN, { Dsp.setBand(i, b.copy(gain = (it * 2).roundToInt() / 2f)) },
                                    Modifier.fillMaxWidth().height(170.dp), selected = i == selected, enabled = s.enabled,
                                    onSelect = { selected = i },
                                )
                                Text(freqLabel(b.freq), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
            // Presety
            item {
                Panel(padding = 12.dp) {
                    Overline(tr("Presety"))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(Dsp.builtIn) { (name, gains) ->
                            PresetCard(tr(name), gains, s.preset == name) { selected = -1; Dsp.applyPreset(name) }
                        }
                    }
                    if (s.userPresets.isNotEmpty()) {
                        VSpace(12.dp)
                        Text(tr("Twoje presety"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        VSpace(6.dp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(s.userPresets) { p ->
                                Box {
                                    PresetCard(p.name, p.bands.map { it.gain }, s.preset == p.name) { selected = -1; Dsp.applyPreset(p.name) }
                                    Icon(
                                        Icons.Rounded.Close, tr("Usuń"),
                                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(RoundedCornerShape(50)).clickable { Dsp.deletePreset(p.name) },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    VSpace(6.dp)
                    SetSwitch(Icons.Rounded.Style, tr("Preset według gatunku"), null, settings.smartEq) { v -> vm.updateSettings { it.copy(smartEq = v) } }
                }
            }
            // Pasma, profile wyjścia, dynamika
            item {
                Panel(padding = 12.dp) {
                    ChoiceTiles(Dsp.BAND_COUNTS, s.bands.size, { tr("{0} pasm", it) }, title = tr("Liczba pasm"), titleIcon = Icons.Rounded.Tune) { selected = -1; Dsp.setBandCount(it) }
                    Gap(6.dp)
                    ChoiceTiles(OUTPUTS, s.profile, { outputLabel(it) }, { outputIcon(it) }, title = tr("Profil wyjścia")) { Dsp.switchProfile(it) }
                    Gap(8.dp)
                    TileGrid(listOf(
                        Tile(Icons.Rounded.DevicesOther, tr("Auto profil"), tr("Osobne ustawienia korektora dla słuchawek, Bluetooth, USB i głośnika – przełączane samoczynnie"), s.autoProfiles) { v -> Dsp.update { it.copy(autoProfiles = v) } },
                        Tile(Icons.Rounded.Compress, tr("Kompresor"), tr("Kompresor dynamiki – wyrównuje głośne i ciche fragmenty"), s.compressor) { v -> Dsp.update { it.copy(compressor = v) } },
                        Tile(Icons.Rounded.Headphones, tr("Crossfeed"), tr("Przesłuch słuchawkowy – mniej męczące stereo"), s.crossfeed > 0f) { v -> Dsp.update { it.copy(crossfeed = if (v) 0.5f else 0f) } },
                        Tile(Icons.Rounded.Shield, tr("Limiter"), tr("Limiter chroni przed przesterowaniem"), s.limiter) { v -> Dsp.update { it.copy(limiter = v) } },
                    ))
                    if (s.compressor || s.crossfeed > 0f) {
                        Gap(12.dp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            if (s.compressor) {
                                Knob(tr("Próg"), s.compThreshold, -40f..0f, { v -> Dsp.update { it.copy(compThreshold = v.roundToInt().toFloat()) } }, { "${it.roundToInt()} dB" }, neutral = 0f, default = -18f)
                                Knob(tr("Stosunek"), s.compRatio, 1f..10f, { v -> Dsp.update { it.copy(compRatio = (v * 2).roundToInt() / 2f) } }, { String.format(Locale.ROOT, "%.1f:1", it) }, neutral = 1f, default = 3f)
                            }
                            if (s.crossfeed > 0f) Knob(tr("Crossfeed"), s.crossfeed, 0f..1f, { v -> Dsp.update { it.copy(crossfeed = ((v * 20).roundToInt() / 20f).coerceAtLeast(0.05f)) } }, { "${(it * 100).roundToInt()}%" })
                        }
                    }
                }
            }
            // Barwa i przestrzeń
            item {
                Panel(padding = 14.dp) {
                    Overline(tr("Barwa i przestrzeń"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Knob(tr("Przedwzmacniacz"), s.preamp, -15f..15f, { v -> Dsp.update { it.copy(preamp = (v * 2).roundToInt() / 2f) } }, { db(it) + " dB" }, neutral = 0f, enabled = s.enabled)
                        Knob(tr("Basy"), s.bass, -12f..12f, { v -> Dsp.update { it.copy(bass = (v * 2).roundToInt() / 2f) } }, { db(it) + " dB" }, neutral = 0f, enabled = s.enabled)
                        Knob(tr("Soprany"), s.treble, -12f..12f, { v -> Dsp.update { it.copy(treble = (v * 2).roundToInt() / 2f) } }, { db(it) + " dB" }, neutral = 0f, enabled = s.enabled)
                    }
                    VSpace(10.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Knob(tr("Szerokość stereo"), s.width, 0f..2f, { v -> Dsp.update { it.copy(width = (v * 20).roundToInt() / 20f) } }, { "${(it * 100).roundToInt()}%" }, neutral = 1f, enabled = s.enabled && !s.mono)
                        Knob(tr("Balans"), s.balance, -1f..1f, { v -> Dsp.update { it.copy(balance = (v * 20).roundToInt() / 20f) } }, { balanceLabel(it) }, neutral = 0f, enabled = s.enabled)
                        Knob(tr("Przestrzenność"), fx.virtualizer.toFloat(), 0f..1000f, { AudioFx.setVirtualizer(it.roundToInt()) }, { "${(it / 10).roundToInt()}%" }, enabled = s.enabled && fx.virtualizerSupported)
                    }
                    VSpace(10.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Knob(tr("Głośność (wzmocnienie)"), fx.loudness.toFloat(), 0f..1500f, { AudioFx.setLoudness((it / 50).roundToInt() * 50) }, { "+" + String.format(Locale.ROOT, "%.1f", it / 100) + " dB" }, enabled = s.enabled && fx.loudnessSupported)
                    }
                    VSpace(12.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillToggle(tr("Mono"), s.mono, { Dsp.update { it.copy(mono = !it.mono) } })
                        PillToggle(tr("Zamiana kanałów"), s.swap, { Dsp.update { it.copy(swap = !it.swap) } })
                    }
                }
            }
        }
    }
}

fun balanceLabel(v: Float): String = when {
    v < -0.02f -> "L ${(-v * 100).roundToInt()}"
    v > 0.02f -> "P ${(v * 100).roundToInt()}".let { if (com.blacksingularities.openamp.i18n.I18n.english) it.replace("P", "R") else it }
    else -> "C"
}

/** Wykres: oś częstotliwości logarytmiczna, siatka dB, widmo na żywo, krzywe pasm i wypadkowa. */
@Composable
private fun ResponseGraph(s: DspState, live: LiveSpectrum, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val cur by rememberUpdatedState(s)
    var drag by remember { mutableIntStateOf(-1) }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = label)
    // Charakterystyka liczona raz na zmianę ustawień (256 punktów w skali logarytmicznej).
    val curve = remember(s) { FloatArray(CURVE_N) { Dsp.responseDb(s, F_MIN * (F_MAX / F_MIN).pow(it / (CURVE_N - 1f))) } }
    val bandCurve = remember(s, selected) {
        s.bands.getOrNull(selected)?.let { b -> FloatArray(CURVE_N) { Dsp.bandResponseDb(b, F_MIN * (F_MAX / F_MIN).pow(it / (CURVE_N - 1f))) } }
    }
    Canvas(
        modifier.pointerInput(Unit) {
            val pad = 18.dp.toPx()
            fun yToDb(y: Float) = (DB_RANGE - (y - pad) / (size.height - 2 * pad) * 2 * DB_RANGE).coerceIn(-DB_RANGE, DB_RANGE)
            detectDragGestures(
                onDragStart = { o ->
                    val w = size.width.toFloat()
                    val idx = cur.bands.indices.minByOrNull { kotlin.math.abs(xOf(cur.bands[it].freq, w) - o.x) } ?: -1
                    drag = idx; onSelect(idx)
                },
                onDragEnd = { drag = -1 },
                onDragCancel = { drag = -1 },
            ) { ch, _ ->
                ch.consume()
                val i = drag
                if (i < 0 || !cur.enabled) return@detectDragGestures
                val b = cur.bands[i]
                val w = size.width.toFloat()
                // Częstotliwość ograniczona sąsiednimi pasmami, żeby kolejność została zachowana.
                val lo = cur.bands.getOrNull(i - 1)?.freq?.times(1.12f) ?: F_MIN
                val hi = cur.bands.getOrNull(i + 1)?.freq?.div(1.12f) ?: F_MAX
                val f = fOf(ch.position.x, w).coerceIn(lo, hi)
                Dsp.setBand(i, b.copy(freq = f.roundToInt().toFloat(), gain = (yToDb(ch.position.y) * 2).roundToInt() / 2f))
            }
        },
    ) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val pad = 18.dp.toPx()
        val h = size.height - 2 * pad
        val w = size.width
        fun y(dbv: Float) = pad + (DB_RANGE - dbv.coerceIn(-DB_RANGE - 2, DB_RANGE + 2)) / (2 * DB_RANGE) * h
        // Widmo na żywo (log 40 Hz – 16 kHz, tak jak w procesorze widma)
        val n = live.bands.size
        val spec = Path().apply {
            moveTo(xOf(40f, w), size.height)
            for (i in 0 until n) {
                val f = 40f * (16000f / 40f).pow(i / (n - 1f))
                lineTo(xOf(f, w), size.height - live.bands[i] * size.height * 0.9f)
            }
            lineTo(xOf(16000f, w), size.height); close()
        }
        drawPath(spec, Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.02f))))
        // Siatka
        for (dbv in listOf(-12f, -6f, 0f, 6f, 12f)) {
            drawLine(if (dbv == 0f) grid.copy(alpha = 0.2f) else grid, Offset(0f, y(dbv)), Offset(w, y(dbv)), strokeWidth = 1.dp.toPx())
            drawText(textMeasurer, (if (dbv > 0) "+" else "") + dbv.roundToInt(), Offset(4.dp.toPx(), y(dbv) - 14.dp.toPx()), labelStyle)
        }
        for (f in listOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)) {
            drawLine(grid, Offset(xOf(f, w), pad), Offset(xOf(f, w), size.height - pad), strokeWidth = 1.dp.toPx())
            drawText(textMeasurer, freqLabel(f), Offset(xOf(f, w) + 3.dp.toPx(), size.height - pad + 2.dp.toPx()), labelStyle)
        }
        val alpha = if (s.enabled) 1f else 0.35f
        fun curvePath(c: FloatArray) = Path().apply {
            for (i in c.indices) {
                val px = w * i / (c.size - 1f)
                if (i == 0) moveTo(px, y(c[i])) else lineTo(px, y(c[i]))
            }
        }
        // Krzywa zaznaczonego pasma
        bandCurve?.let { drawPath(curvePath(it), accent.copy(alpha = 0.5f * alpha), style = Stroke(1.5.dp.toPx())) }
        // Wypadkowa
        val total = curvePath(curve)
        val fill = Path().apply { addPath(total); lineTo(w, y(0f)); lineTo(0f, y(0f)); close() }
        drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f * alpha), accent.copy(alpha = 0.0f))))
        drawPath(total, accent.copy(alpha = 0.35f * alpha), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        drawPath(total, Color.White.copy(alpha = 0.9f * alpha), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        // Uchwyty pasm
        s.bands.forEachIndexed { i, b ->
            val c = Offset(xOf(b.freq, w), y(b.gain + s.preamp))
            node(c, i == selected, accent, alpha)
        }
    }
}

private fun DrawScope.node(c: Offset, sel: Boolean, accent: Color, alpha: Float) {
    if (sel) drawCircle(accent.copy(alpha = 0.3f * alpha), 16.dp.toPx(), c)
    drawCircle(Color.Black.copy(alpha = 0.6f), 8.dp.toPx(), c)
    drawCircle(if (sel) accent else Color.White, 6.dp.toPx(), c, alpha = alpha)
}

@Composable
private fun BandEditor(b: EqBand, index: Int, n: Int, onClose: () -> Unit) {
    Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Pasmo {0}", index + 1), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${freqLabel(b.freq)} Hz  •  ${db(b.gain)} dB  •  Q ${String.format(Locale.ROOT, "%.2f", b.q)}", fontFamily = MonoNumbers, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, tr("Zamknij")) }
        }
        Text(tr("Częstotliwość"), style = MaterialTheme.typography.labelSmall)
        Slider(ln(b.freq / F_MIN) / ln(F_MAX / F_MIN), { Dsp.setBand(index, b.copy(freq = (F_MIN * (F_MAX / F_MIN).pow(it)).roundToInt().toFloat())) })
        Text(tr("Dobroć (szerokość pasma)"), style = MaterialTheme.typography.labelSmall)
        Slider(b.q, { Dsp.setBand(index, b.copy(q = (it * 20).roundToInt() / 20f)) }, valueRange = 0.3f..8f)
        Gap(4.dp)
        ChoiceTiles(FilterType.entries, b.type, { filterLabel(it) }, title = tr("Typ filtra"), titleIcon = Icons.Rounded.Tune) { Dsp.setBand(index, b.copy(type = it, q = if (it == FilterType.PEAK) b.q else 0.71f)) }
        Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillToggle(tr("Szerokie"), b.q < 1f, { Dsp.setBand(index, b.copy(q = 0.7f)) })
            PillToggle(tr("Standard"), b.q in 1f..2f, { Dsp.setBand(index, b.copy(q = 1.41f)) })
            PillToggle(tr("Wąskie"), b.q > 2f, { Dsp.setBand(index, b.copy(q = 4f)) })
            val iso = Dsp.freqsFor(n).getOrNull(index)
            if (iso != null) PillToggle("ISO", b.freq == iso, { Dsp.setBand(index, b.copy(freq = iso)) })
        }
    }
}

/** Karta presetu z miniaturą krzywej. */
@Composable
private fun PresetCard(name: String, gains: List<Float>, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(96.dp).clip(shape)
            .background(if (selected) accent.copy(alpha = 0.22f) else Flat.Block)
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val st = DspState(bands = gains.mapIndexed { i, g -> EqBand(Dsp.ISO.getOrElse(i) { 1000f }, g) })
            val p = Path()
            var x = 0f
            while (x <= size.width) {
                val d = Dsp.responseDb(st, fOf(x, size.width))
                val yy = size.height / 2 - d / 24f * size.height
                if (x == 0f) p.moveTo(x, yy) else p.lineTo(x, yy)
                x += 2f
            }
            drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
            drawPath(p, if (selected) accent else Color.White.copy(alpha = 0.75f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        VSpace(6.dp)
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = if (selected) accent else Color.White)
    }
}

private val OUTPUTS = listOf("SPEAKER", "HEADPHONES", "BLUETOOTH", "USB")

fun outputLabel(k: String) = when (k) {
    "HEADPHONES" -> tr("Słuchawki przewodowe")
    "BLUETOOTH" -> tr("Bluetooth")
    "USB" -> tr("USB / DAC")
    else -> tr("Głośnik")
}

private fun outputIcon(k: String) = when (k) {
    "HEADPHONES" -> Icons.Rounded.Headphones
    "BLUETOOTH" -> Icons.Rounded.Bluetooth
    "USB" -> Icons.Rounded.Usb
    else -> Icons.Rounded.Speaker
}

fun filterLabel(t: FilterType) = when (t) {
    FilterType.PEAK -> tr("Szczyt")
    FilterType.LOW_SHELF -> tr("Półka niska")
    FilterType.HIGH_SHELF -> tr("Półka wysoka")
    FilterType.LOW_PASS -> tr("Dolnoprzep.")
    FilterType.HIGH_PASS -> tr("Górnoprzep.")
    FilterType.NOTCH -> tr("Wycięcie")
}
