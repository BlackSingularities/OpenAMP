package com.blacksingularities.openamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.playback.AudioFx
import com.blacksingularities.openamp.playback.Dsp
import com.blacksingularities.openamp.playback.DspMeter
import com.blacksingularities.openamp.playback.DspState
import com.blacksingularities.openamp.playback.EqBand
import com.blacksingularities.openamp.playback.FilterType
import com.blacksingularities.openamp.playback.ReplayGainMode
import java.util.Locale
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private const val F_MIN = 20f
private const val F_MAX = 20000f
private const val CURVE_N = 256

private fun xOf(f: Float, w: Float) = (ln(f / F_MIN) / ln(F_MAX / F_MIN)) * w
private fun fOf(x: Float, w: Float) = F_MIN * (F_MAX / F_MIN).pow((x / w).coerceIn(0f, 1f))

fun freqLabel(hz: Float): String = if (hz >= 1000) {
    val k = hz / 1000f
    if (k >= 10 || k == k.roundToInt().toFloat()) "${k.roundToInt()}k" else String.format(Locale.ROOT, "%.1fk", k)
} else "${hz.roundToInt()}"

private fun db(v: Float) = String.format(Locale.ROOT, "%+.1f", v)

/** Zakładki korektora: krzywa i presety, efekty barwy i dynamiki, wyjście (poziomy, ReplayGain, profile). */
private enum class EqTab { EQ, FX, OUTPUT }

private fun EqTab.label() = when (this) {
    EqTab.EQ -> tr("Korektor")
    EqTab.FX -> tr("Efekty")
    EqTab.OUTPUT -> tr("Wyjście")
}

private fun EqTab.icon() = when (this) {
    EqTab.EQ -> Icons.Rounded.Equalizer
    EqTab.FX -> Icons.Rounded.Tune
    EqTab.OUTPUT -> Icons.Rounded.VolumeUp
}

@Composable
fun EqualizerScreen(vm: MainViewModel, bottomPadding: Dp) {
    val s by Dsp.state.collectAsState()
    val fx by AudioFx.state.collectAsState()
    val settings by vm.settings.collectAsState()
    val ps by vm.player.state.collectAsState()
    var selected by remember { mutableIntStateOf(-1) }
    var saving by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(EqTab.EQ) }
    var range by remember { mutableFloatStateOf(15f) }
    val live = rememberLiveSpectrum(ps.isPlaying)
    if (saving) NameDialog(tr("Zapisz preset"), tr("Nazwa presetu"), if (s.userPresets.any { it.name == s.preset }) s.preset else "", { saving = false }) { Dsp.savePreset(it); saving = false }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), { vm.back() }, background = Color.Transparent, size = 40.dp)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(tr("Korektor"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Flat.Fg, lineHeight = 22.sp)
                Text(if (s.enabled) tr(s.preset) else tr("Wyłączone"), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            CompareButton(s.enabled)
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
        TabChips(EqTab.entries, tab, { it.label() }, { it.icon() }, Modifier.padding(top = 4.dp, bottom = 6.dp)) { tab = it }

        LazyColumn(
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = bottomPadding + 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                EqTab.EQ -> {
                    // Wykres charakterystyki na tle widma na żywo
                    item {
                        Panel(padding = 10.dp) {
                            Overline(tr("Charakterystyka"), trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (r in listOf(6f, 12f, 15f)) RangeChip("±${r.roundToInt()}", range == r) { range = r }
                                }
                            })
                            ResponseGraph(s, live, selected, range, { selected = it }, Modifier.fillMaxWidth().height(220.dp))
                            ClipWarning(s)
                            if (selected in s.bands.indices) BandEditor(s.bands[selected], selected, s.bands.size) { selected = -1 }
                        }
                    }
                    // Fadery: przedwzmacniacz osobno, potem pasma
                    item {
                        Panel(padding = 12.dp) {
                            Overline(tr("Pasma"), trailing = {
                                Text(tr("dotknij dwukrotnie, aby wyzerować"), fontSize = 10.sp, color = Flat.Faded)
                            })
                            val many = s.bands.size > 10
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                FaderColumn(tr("Pre"), s.preamp, true, s.enabled, Modifier.width(40.dp), onSelect = {}) { v -> Dsp.update { it.copy(preamp = v) } }
                                Box(Modifier.padding(horizontal = 4.dp, vertical = 24.dp).width(1.dp).height(150.dp).background(Flat.Hair))
                                Row(if (many) Modifier.weight(1f).horizontalScroll(rememberScrollState()) else Modifier.weight(1f)) {
                                    s.bands.forEachIndexed { i, b ->
                                        FaderColumn(
                                            freqLabel(b.freq), b.gain, i == selected, s.enabled,
                                            if (many) Modifier.width(38.dp) else Modifier.weight(1f), onSelect = { selected = i },
                                        ) { v -> Dsp.setBand(i, b.copy(gain = v)) }
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
                                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(Shapes.Pill).clickable { Dsp.deletePreset(p.name) },
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            VSpace(6.dp)
                            SetSwitch(Icons.Rounded.Style, tr("Preset według gatunku"), tr("Dobiera preset do gatunku każdego utworu"), settings.smartEq) { v -> vm.updateSettings { it.copy(smartEq = v) } }
                            Gap(4.dp)
                            ChoiceTiles(Dsp.BAND_COUNTS, s.bands.size, { tr("{0} pasm", it) }, title = tr("Liczba pasm"), titleIcon = Icons.Rounded.Tune) { selected = -1; Dsp.setBandCount(it) }
                        }
                    }
                }

                EqTab.FX -> {
                    item {
                        Panel(padding = 14.dp) {
                            Overline(tr("Barwa"))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Knob(tr("Basy"), s.bass, -12f..12f, { v -> Dsp.update { it.copy(bass = (v * 2).roundToInt() / 2f) } }, { db(it) + " dB" }, neutral = 0f, enabled = s.enabled)
                                Knob(tr("Soprany"), s.treble, -12f..12f, { v -> Dsp.update { it.copy(treble = (v * 2).roundToInt() / 2f) } }, { db(it) + " dB" }, neutral = 0f, enabled = s.enabled)
                                Knob(tr("Przedwzmacniacz"), s.preamp, -15f..15f, { v -> Dsp.update { it.copy(preamp = (v * 2).roundToInt() / 2f) } }, { db(it) + " dB" }, neutral = 0f, enabled = s.enabled)
                            }
                        }
                    }
                    item {
                        Panel(padding = 14.dp) {
                            Overline(tr("Przestrzeń"))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Knob(tr("Szerokość stereo"), s.width, 0f..2f, { v -> Dsp.update { it.copy(width = (v * 20).roundToInt() / 20f) } }, { "${(it * 100).roundToInt()}%" }, neutral = 1f, enabled = s.enabled && !s.mono)
                                Knob(tr("Balans"), s.balance, -1f..1f, { v -> Dsp.update { it.copy(balance = (v * 20).roundToInt() / 20f) } }, { balanceLabel(it) }, neutral = 0f, enabled = s.enabled)
                                Knob(tr("Crossfeed"), s.crossfeed, 0f..1f, { v -> Dsp.update { it.copy(crossfeed = (v * 20).roundToInt() / 20f) } }, { if (it <= 0f) tr("Wył.") else "${(it * 100).roundToInt()}%" }, enabled = s.enabled)
                            }
                            VSpace(10.dp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Knob(tr("Przestrzenność"), fx.virtualizer.toFloat(), 0f..1000f, { AudioFx.setVirtualizer(it.roundToInt()) }, { "${(it / 10).roundToInt()}%" }, enabled = s.enabled && fx.virtualizerSupported)
                            }
                            SetNote(tr("Crossfeed miesza kanały jak przy słuchaniu z głośników – stare nagrania stereo mniej męczą na słuchawkach."))
                            VSpace(8.dp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillToggle(tr("Mono"), s.mono, { Dsp.update { it.copy(mono = !it.mono) } })
                                PillToggle(tr("Zamiana kanałów"), s.swap, { Dsp.update { it.copy(swap = !it.swap) } })
                            }
                        }
                    }
                    item {
                        Panel(padding = 14.dp) {
                            Overline(tr("Dynamika"))
                            TileGrid(listOf(
                                Tile(Icons.Rounded.Compress, tr("Kompresor"), tr("Kompresor dynamiki z miękkim kolanem – wyrównuje głośne i ciche fragmenty"), s.compressor) { v -> Dsp.update { it.copy(compressor = v) } },
                                Tile(Icons.Rounded.Shield, tr("Limiter"), tr("Limiter z wyprzedzeniem – łagodnie ścisza szczyty, zanim przekroczą pełną skalę"), s.limiter) { v -> Dsp.update { it.copy(limiter = v) } },
                            ))
                            if (s.compressor) {
                                Gap(12.dp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Knob(tr("Próg"), s.compThreshold, -40f..0f, { v -> Dsp.update { it.copy(compThreshold = v.roundToInt().toFloat()) } }, { "${it.roundToInt()} dB" }, neutral = 0f, default = -18f)
                                    Knob(tr("Stosunek"), s.compRatio, 1f..10f, { v -> Dsp.update { it.copy(compRatio = (v * 2).roundToInt() / 2f) } }, { String.format(Locale.ROOT, "%.1f:1", it) }, neutral = 1f, default = 3f)
                                }
                            }
                            if (s.compressor || s.limiter) {
                                Gap(10.dp)
                                ReductionMeter(ps.isPlaying)
                            }
                        }
                    }
                }

                EqTab.OUTPUT -> {
                    item {
                        Panel(padding = 14.dp) {
                            Overline(tr("Poziom wyjścia"))
                            OutputMeter(ps.isPlaying)
                        }
                    }
                    item {
                        Panel(padding = 12.dp) {
                            Overline(tr("ReplayGain"))
                            SetSegment(
                                Icons.Rounded.GraphicEq, tr("Wyrównanie głośności"), ReplayGainMode.entries, s.replayGain,
                                { when (it) { ReplayGainMode.OFF -> tr("Wyłączone"); ReplayGainMode.TRACK -> tr("Według utworu"); ReplayGainMode.ALBUM -> tr("Według albumu") } },
                                short = { when (it) { ReplayGainMode.OFF -> tr("Wył."); ReplayGainMode.TRACK -> tr("Utwór"); ReplayGainMode.ALBUM -> tr("Album") } },
                            ) { v -> Dsp.update { it.copy(replayGain = v) } }
                            if (s.replayGain != ReplayGainMode.OFF) {
                                SetSlider(
                                    Icons.Rounded.VolumeUp, tr("Wzmocnienie ReplayGain"), db(s.rgPreamp) + " dB", s.rgPreamp, -12f..12f, 47,
                                    subtitle = tr("Dodawane do znacznika; limiter pilnuje przesteru"),
                                ) { v -> Dsp.update { it.copy(rgPreamp = (v * 2).roundToInt() / 2f) } }
                            }
                            val t = Dsp.rgTrack; val a = Dsp.rgAlbum
                            SetNote(
                                if (t == null && a == null) tr("Bieżący utwór nie ma znaczników ReplayGain – gra bez zmian.")
                                else tr("Znaczniki bieżącego utworu: utwór {0}, album {1}", t?.let { db(it) + " dB" } ?: "—", a?.let { db(it) + " dB" } ?: "—"),
                            )
                        }
                    }
                    item {
                        Panel(padding = 12.dp) {
                            Overline(tr("Profile wyjścia"))
                            ChoiceTiles(OUTPUTS, s.profile, { outputLabel(it) }, { outputIcon(it) }, title = tr("Profil wyjścia")) { Dsp.switchProfile(it) }
                            Gap(6.dp)
                            SetSwitch(Icons.Rounded.DevicesOther, tr("Auto profil"), tr("Osobne ustawienia korektora dla słuchawek, Bluetooth, USB i głośnika – przełączane samoczynnie"), s.autoProfiles) { v -> Dsp.update { it.copy(autoProfiles = v) } }
                        }
                    }
                    item {
                        Panel(padding = 14.dp) {
                            Overline(tr("Głośność"))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Knob(tr("Wzmocnienie systemowe"), fx.loudness.toFloat(), 0f..1500f, { AudioFx.setLoudness((it / 50).roundToInt() * 50) }, { "+" + String.format(Locale.ROOT, "%.1f", it / 100) + " dB" }, enabled = s.enabled && fx.loudnessSupported)
                            }
                            SetNote(tr("Efekt systemowy Androida – podbija głośność ponad maksimum telefonu; przy dużych wartościach może zniekształcać."))
                        }
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

/** Porównanie A/B: dopóki przycisk jest wciśnięty, słychać dźwięk bez korektora i efektów. */
@Composable
private fun CompareButton(enabled: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    var held by remember { mutableStateOf(false) }
    Box(
        Modifier.padding(end = 4.dp).clip(Shapes.Pill)
            .background(if (held) accent else Flat.Block)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    held = true; Dsp.setBypass(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        // Także gdy ekran znika w trakcie przytrzymania – korektor nie może zostać wyłączony.
                        held = false; Dsp.setBypass(false)
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            if (held) tr("Bez efektów") else "A/B", fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (held) MaterialTheme.colorScheme.onPrimary else if (enabled) Flat.Fg else Flat.Faded,
        )
    }
}

@Composable
private fun RangeChip(text: String, on: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = MonoNumbers,
        color = if (on) MaterialTheme.colorScheme.onPrimary else Flat.Dim,
        modifier = Modifier.clip(Shapes.Pill).background(if (on) accent else Flat.Block).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Fader pasma z wartością u góry i podpisem na dole. */
@Composable
private fun FaderColumn(label: String, value: Float, selected: Boolean, enabled: Boolean, modifier: Modifier, onSelect: () -> Unit, onChange: (Float) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            db(value).removeSuffix(".0"), fontFamily = MonoNumbers, style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else if (value != 0f) Flat.Fg else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Fader(
            value, -Dsp.MAX_GAIN..Dsp.MAX_GAIN, { onChange((it * 2).roundToInt() / 2f) },
            Modifier.fillMaxWidth().height(170.dp), selected = selected, enabled = enabled, onSelect = onSelect,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Ostrzeżenie o możliwym przesterowaniu: krzywa wychodzi ponad 0 dB – z szybkim wyrównaniem przedwzmacniacza. */
@Composable
private fun ClipWarning(s: DspState) {
    val peak = remember(s) { if (!s.enabled) 0f else (40..20000 step 40).maxOf { Dsp.responseDb(s, it.toFloat()) } }
    if (peak < 0.5f) return
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(Shapes.Medium).background(Color(0x33FFB020)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFFC14D), modifier = Modifier.size(16.dp))
        Text(
            tr("Podbicie do {0} dB – {1}", db(peak), if (s.limiter) tr("limiter ścisza szczyty") else tr("możliwy przester")),
            fontSize = 11.sp, color = Flat.Fg, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            tr("Wyrównaj"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(Shapes.Pill).clickable { Dsp.autoPreamp() }.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Odczyt procesora co klatkę: szczyty spadają płynnie (~20 dB/s), jak w miernikach studyjnych. */
@Composable
private fun rememberMeter(playing: Boolean): FloatArray {
    // [0] lewy dBFS, [1] prawy dBFS, [2] ściszenie dB; zmiana [3] wymusza przerysowanie.
    val v = remember { floatArrayOf(-60f, -60f, 0f, 0f) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else (now - last) / 1e9f
            last = now
            fun dbOf(x: Float) = if (playing && x > 1e-5f) 20 * log10(x) else -60f
            v[0] = max(dbOf(DspMeter.peakL), v[0] - 20f * dt)
            v[1] = max(dbOf(DspMeter.peakR), v[1] - 20f * dt)
            v[2] = if (playing) minOf(DspMeter.reductionDb, v[2] + 10f * dt).coerceAtMost(0f) else 0f
            tick++
        }
    }
    v[3] = tick.toFloat()
    return v
}

@Composable
private fun OutputMeter(playing: Boolean) {
    val m = rememberMeter(playing)
    MeterBar("L", m[0])
    Gap(6.dp)
    MeterBar(if (com.blacksingularities.openamp.i18n.I18n.english) "R" else "P", m[1])
    Gap(8.dp)
    val rate = DspMeter.sampleRate
    Text(
        if (rate > 0) tr(
            "{0} kHz · {1} · {2} · przetwarzanie 64-bit · wyjście {3}",
            String.format(Locale.ROOT, "%.1f", rate / 1000f), DspMeter.inputFormat, if (DspMeter.channels == 1) tr("mono") else tr("stereo"),
            if (DspMeter.floatOut) tr("float (hi-res)") else tr("16 bit z ditheringiem"),
        )
        else tr("Brak sygnału – zacznij odtwarzanie"),
        fontSize = 11.sp, color = Flat.Dim,
    )
    if (m[2] < -0.1f) {
        Gap(4.dp)
        Text(tr("Ściszenie przez dynamikę: {0} dB", String.format(Locale.ROOT, "%.1f", m[2])), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ReductionMeter(playing: Boolean) {
    val m = rememberMeter(playing)
    val accent = MaterialTheme.colorScheme.primary
    val hair = Flat.Hair
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Ściszenie"), fontSize = 11.sp, color = Flat.Dim, modifier = Modifier.width(72.dp))
        Canvas(Modifier.weight(1f).height(8.dp)) {
            drawRoundRect(hair, size = size, cornerRadius = CornerRadius(size.height / 2))
            // Skala 0…−12 dB od prawej – jak wskaźnik redukcji w kompresorach.
            val f = (-m[2] / 12f).coerceIn(0f, 1f)
            drawRoundRect(accent, Offset(size.width * (1 - f), 0f), Size(size.width * f, size.height), CornerRadius(size.height / 2))
        }
        Text(String.format(Locale.ROOT, "%.1f dB", m[2]), fontSize = 11.sp, fontFamily = MonoNumbers, color = Flat.Fg, modifier = Modifier.width(64.dp).padding(start = 8.dp))
    }
}

/** Pasek poziomu −60…0 dBFS: zielony, od −12 dB żółty, od −3 dB czerwony. */
@Composable
private fun MeterBar(label: String, dbfs: Float) {
    val hair = Flat.Hair
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Flat.Dim, modifier = Modifier.width(18.dp))
        Canvas(Modifier.weight(1f).height(10.dp)) {
            fun x(d: Float) = ((d + 60f) / 60f).coerceIn(0f, 1f) * size.width
            drawRoundRect(hair, size = size, cornerRadius = CornerRadius(3.dp.toPx()))
            clipRect(right = x(dbfs)) {
                drawRect(Brush.horizontalGradient(
                    0f to Color(0xFF3DDC84), 0.8f to Color(0xFF3DDC84), 0.8f to Color(0xFFFFC14D), 0.95f to Color(0xFFFFC14D), 0.95f to Color(0xFFFF5A5A),
                    startX = 0f, endX = size.width,
                ))
            }
            for (d in listOf(-48f, -36f, -24f, -12f, -6f, -3f)) drawLine(Color.Black.copy(alpha = 0.35f), Offset(x(d), 0f), Offset(x(d), size.height), 1.dp.toPx())
        }
        Text(if (dbfs <= -59.9f) "−∞" else String.format(Locale.ROOT, "%.1f", dbfs), fontSize = 11.sp, fontFamily = MonoNumbers, color = Flat.Fg, modifier = Modifier.width(52.dp).padding(start = 8.dp))
    }
}

/** Wykres: oś częstotliwości logarytmiczna, siatka dB, widmo na żywo, krzywe pasm i wypadkowa. */
@Composable
private fun ResponseGraph(s: DspState, live: LiveSpectrum, selected: Int, range: Float, onSelect: (Int) -> Unit, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val cur by rememberUpdatedState(s)
    val rng by rememberUpdatedState(range)
    var drag by remember { mutableIntStateOf(-1) }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = label)
    // Charakterystyka liczona raz na zmianę ustawień (256 punktów w skali logarytmicznej).
    val curve = remember(s) { FloatArray(CURVE_N) { Dsp.responseDb(s, F_MIN * (F_MAX / F_MIN).pow(it / (CURVE_N - 1f))) } }
    val bandCurve = remember(s, selected) {
        s.bands.getOrNull(selected)?.let { b -> FloatArray(CURVE_N) { Dsp.bandResponseDb(b, F_MIN * (F_MAX / F_MIN).pow(it / (CURVE_N - 1f))) } }
    }
    val fg = Flat.Fg
    val hot = Color(0xFFFF5A5A)
    Canvas(
        modifier
            .pointerInput(Unit) {
                val pad = 18.dp.toPx()
                fun nearest(x: Float): Int {
                    val w = size.width.toFloat()
                    return cur.bands.indices.minByOrNull { kotlin.math.abs(xOf(cur.bands[it].freq, w) - x) } ?: -1
                }
                fun yToDb(y: Float) = (rng - (y - pad) / (size.height - 2 * pad) * 2 * rng).coerceIn(-Dsp.MAX_GAIN, Dsp.MAX_GAIN)
                detectDragGestures(
                    onDragStart = { o -> val idx = nearest(o.x); drag = idx; onSelect(idx) },
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
            }
            // Dotknięcie wybiera najbliższe pasmo, podwójne – zeruje je.
            .pointerInput(Unit) {
                fun nearest(x: Float): Int {
                    val w = size.width.toFloat()
                    return cur.bands.indices.minByOrNull { kotlin.math.abs(xOf(cur.bands[it].freq, w) - x) } ?: -1
                }
                detectTapGestures(
                    onTap = { o -> onSelect(nearest(o.x)) },
                    onDoubleTap = { o ->
                        val i = nearest(o.x)
                        if (i >= 0 && cur.enabled) Dsp.setBand(i, cur.bands[i].copy(gain = 0f))
                    },
                )
            },
    ) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val pad = 18.dp.toPx()
        val h = size.height - 2 * pad
        val w = size.width
        fun y(dbv: Float) = pad + (range - dbv.coerceIn(-range - 2, range + 2)) / (2 * range) * h
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
        // Strefa ponad 0 dB – tam grozi przester (limiter ścisza).
        drawRect(hot.copy(alpha = 0.04f), Offset(0f, pad), Size(w, y(0f) - pad))
        // Siatka
        val step = if (range <= 6f) 3f else 6f
        var dbv = -range + (range % step)
        while (dbv <= range) {
            drawLine(if (dbv == 0f) grid.copy(alpha = 0.22f) else grid, Offset(0f, y(dbv)), Offset(w, y(dbv)), strokeWidth = 1.dp.toPx())
            drawText(textMeasurer, (if (dbv > 0) "+" else "") + dbv.roundToInt(), Offset(4.dp.toPx(), y(dbv) - 14.dp.toPx()), labelStyle)
            dbv += step
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
        bandCurve?.let {
            val p = curvePath(it)
            val area = Path().apply { addPath(p); lineTo(w, y(0f)); lineTo(0f, y(0f)); close() }
            drawPath(area, accent.copy(alpha = 0.12f * alpha))
            drawPath(p, accent.copy(alpha = 0.6f * alpha), style = Stroke(1.5.dp.toPx()))
        }
        // Wypadkowa: wypełnienie od zera, poświata i linia; nad 0 dB w czerwieni
        val total = curvePath(curve)
        val fill = Path().apply { addPath(total); lineTo(w, y(0f)); lineTo(0f, y(0f)); close() }
        drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.32f * alpha), accent.copy(alpha = 0.02f)), startY = pad, endY = size.height - pad))
        drawPath(total, accent.copy(alpha = 0.30f * alpha), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        drawPath(total, fg.copy(alpha = 0.92f * alpha), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        clipRect(bottom = y(0f)) { drawPath(total, hot.copy(alpha = 0.9f * alpha), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
        // Uchwyty pasm (na wysokości wzmocnienia pasma)
        s.bands.forEachIndexed { i, b ->
            val c = Offset(xOf(b.freq, w), y(b.gain + s.preamp))
            node(c, i == selected, accent, alpha, fg)
        }
        // Wartości zaznaczonego pasma przy uchwycie
        s.bands.getOrNull(selected)?.let { b ->
            val txt = "${freqLabel(b.freq)} Hz  ${db(b.gain)} dB"
            val m = textMeasurer.measure(txt, labelStyle.copy(color = fg, fontWeight = FontWeight.Bold))
            val cx = xOf(b.freq, w)
            val tx = (cx - m.size.width / 2f).coerceIn(4.dp.toPx(), w - m.size.width - 4.dp.toPx())
            val ty = (y(b.gain + s.preamp) - 34.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(tx - 6.dp.toPx(), ty - 2.dp.toPx()), Size(m.size.width + 12.dp.toPx(), m.size.height + 4.dp.toPx()), CornerRadius(8.dp.toPx()))
            drawText(m, topLeft = Offset(tx, ty))
        }
    }
}

private fun DrawScope.node(c: Offset, sel: Boolean, accent: Color, alpha: Float, fg: Color) {
    if (sel) drawCircle(accent.copy(alpha = 0.3f * alpha), 16.dp.toPx(), c)
    drawCircle(Color.Black.copy(alpha = 0.6f), 8.dp.toPx(), c)
    drawCircle(if (sel) accent else fg, 6.dp.toPx(), c, alpha = alpha)
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
    val shape = Shapes.Large
    Column(
        Modifier.width(96.dp).clip(shape)
            .background(if (selected) accent.copy(alpha = 0.22f) else Flat.Block)
            .clickable(onClick = onClick).padding(8.dp),
    ) {
        val fg = Flat.Fg
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
            drawLine(fg.copy(alpha = 0.1f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
            drawPath(p, if (selected) accent else fg.copy(alpha = 0.75f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        VSpace(6.dp)
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = if (selected) accent else Flat.Fg)
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
