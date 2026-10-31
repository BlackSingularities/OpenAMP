package com.blacksingularities.openamp.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.i18n.I18n
import com.blacksingularities.openamp.i18n.plural
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.playback.AudioFx
import com.blacksingularities.openamp.playback.Dsp
import com.blacksingularities.openamp.playback.DspPreset
import com.blacksingularities.openamp.playback.DspState
import com.blacksingularities.openamp.playback.EqBand
import com.blacksingularities.openamp.playback.EqText
import com.blacksingularities.openamp.playback.FilterType
import com.blacksingularities.openamp.playback.PresetGroup
import com.blacksingularities.openamp.playback.ReplayGainMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Zakładki: krzywa i fadery, biblioteka presetów, efekty barwy/przestrzeni/dynamiki, wyjście. */
private enum class EqTab { EQ, PRESETS, FX, OUTPUT }

private fun EqTab.label() = when (this) {
    EqTab.EQ -> tr("Korektor")
    EqTab.PRESETS -> tr("Presety")
    EqTab.FX -> tr("Efekty")
    EqTab.OUTPUT -> tr("Wyjście")
}

private fun EqTab.icon(): ImageVector = when (this) {
    EqTab.EQ -> Icons.Rounded.Equalizer
    EqTab.PRESETS -> Icons.Rounded.LibraryMusic
    EqTab.FX -> Icons.Rounded.Tune
    EqTab.OUTPUT -> Icons.AutoMirrored.Rounded.VolumeUp
}

private fun r2(v: Float) = (v * 2).roundToInt() / 2f
private fun pct(v: Float) = "${(v * 100).roundToInt()}%"

@Composable
fun EqualizerScreen(vm: MainViewModel, bottomPadding: Dp) {
    val s by Dsp.state.collectAsState()
    val canUndo by Dsp.canUndo.collectAsState()
    val ps by vm.player.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(EqTab.EQ) }
    var picked by remember { mutableIntStateOf(-1) }
    // Zaznaczenie znika samo, gdy pasmo przestało istnieć (mniej pasm, import, cofnięcie).
    val selected = picked.takeIf { it in s.bands.indices } ?: -1
    var held by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val clip = rememberClipboard(vm)
    if (saving) NameDialog(tr("Zapisz preset"), tr("Nazwa presetu"), s.preset.takeIf { n -> s.userPresets.any { it.name == n } } ?: "", { saving = false }) {
        Dsp.savePreset(it); saving = false; vm.message(tr("Zapisano preset {0}", it))
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Nagłówek: tytuł z presetem i wyjściem, cofanie, porównanie A/B, menu, włącznik.
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), { vm.back() }, background = Color.Transparent, size = 40.dp)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(tr("Korektor"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Flat.Fg, lineHeight = 22.sp)
                Text(
                    headerSubtitle(s, held), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (s.enabled && !held) MaterialTheme.colorScheme.primary else Flat.Faded,
                )
            }
            RoundButton(Icons.AutoMirrored.Rounded.Undo, tr("Cofnij"), { Dsp.undo() }, background = Color.Transparent, size = 40.dp, iconSize = 20.dp, enabled = canUndo)
            CompareButton(s.enabled) { held = it }
            Box {
                RoundButton(Icons.Rounded.MoreVert, tr("Więcej"), { menu = true }, background = Color.Transparent, size = 40.dp)
                CompactMenu(menu, { menu = false }) {
                    CMenuItem(tr("Zapisz jako preset"), Icons.Rounded.Save) { menu = false; saving = true }
                    CMenuItem(tr("Kopiuj jako tekst"), Icons.Rounded.ContentCopy) { menu = false; clip.copy(EqText.export(s)) }
                    CMenuItem(tr("Wklej ze schowka"), Icons.Rounded.ContentPaste) { menu = false; clip.import() }
                    CMenuDivider()
                    CMenuItem(tr("Resetuj wszystko"), Icons.Rounded.RestartAlt, danger = true) { menu = false; picked = -1; Dsp.reset() }
                }
            }
            Spacer(Modifier.width(4.dp))
            RoundButton(Icons.Rounded.PowerSettingsNew, if (s.enabled) tr("Wyłącz korektor") else tr("Włącz korektor"), { Dsp.update { it.copy(enabled = !it.enabled) } }, active = s.enabled, size = 40.dp, iconSize = 20.dp)
        }
        TabChips(EqTab.entries, tab, { it.label() }, { it.icon() }, Modifier.padding(top = 2.dp, bottom = 8.dp)) { tab = it }

        // Każda zakładka ma własną listę – po przełączeniu zaczyna się od góry.
        key(tab) { LazyColumn(
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 0.dp, bottom = bottomPadding + 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                EqTab.EQ -> eqTab(vm, s, ps.isPlaying, selected, { picked = it }) { tab = EqTab.PRESETS }
                EqTab.PRESETS -> presetsTab(vm, s, clip) { saving = true }
                EqTab.FX -> fxTab(s, ps.isPlaying)
                EqTab.OUTPUT -> outputTab(s, ps.isPlaying)
            }
        } }
    }
}

private fun headerSubtitle(s: DspState, held: Boolean): String = when {
    held -> tr("Porównanie: bez efektów")
    !s.enabled -> tr("Wyłączony – dźwięk bez zmian")
    s.autoProfiles -> tr(s.preset) + " · " + outputLabel(s.profile)
    else -> tr(s.preset)
}

/** Porównanie A/B: dopóki przycisk jest wciśnięty, słychać dźwięk bez korektora i efektów. */
@Composable
private fun CompareButton(enabled: Boolean, onHeld: (Boolean) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    var held by remember { mutableStateOf(false) }
    // Ekran znika w trakcie przytrzymania – korektor nie może zostać ominięty na stałe.
    DisposableEffect(Unit) { onDispose { Dsp.setBypass(false) } }
    Box(
        Modifier.padding(horizontal = 2.dp).clip(Shapes.Pill)
            .background(if (held) accent else Flat.Block)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    held = true; onHeld(true); Dsp.setBypass(true)
                    try { waitForUpOrCancellation() } finally { held = false; onHeld(false); Dsp.setBypass(false) }
                }
            }
            .padding(horizontal = Shapes.PillH, vertical = Shapes.PillV),
    ) {
        Text(
            "A/B", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = MonoNumbers,
            color = when { held -> MaterialTheme.colorScheme.onPrimary; enabled -> Flat.Fg; else -> Flat.Faded },
        )
    }
}

// ---------- Zakładka: Korektor ----------

private fun androidx.compose.foundation.lazy.LazyListScope.eqTab(
    vm: MainViewModel,
    s: DspState,
    playing: Boolean,
    selected: Int,
    onSelect: (Int) -> Unit,
    openPresets: () -> Unit,
) {
    if (!s.enabled) item(key = "off") {
        Notice(Icons.Rounded.PowerSettingsNew, tr("Korektor jest wyłączony – dźwięk przechodzi bez zmian."), tr("Włącz")) { Dsp.update { it.copy(enabled = true) } }
    }
    item(key = "smart") {
        val settings by vm.settings.collectAsState()
        if (settings.smartEq && s.enabled) {
            Notice(Icons.Rounded.AutoAwesome, tr("Preset zmienia się z gatunkiem każdego utworu."), tr("Wyłącz")) { vm.updateSettings { it.copy(smartEq = false) } }
        }
    }
    item(key = "graph") { GraphPanel(s, playing, selected, onSelect) }
    item(key = "faders") { FadersPanel(s, selected, onSelect) }
    item(key = "quick") { QuickPresets(s, openPresets) { onSelect(-1) } }
}

/** Krótki komunikat w panelu: ikona, tekst i pigułka z akcją. */
@Composable
private fun Notice(icon: ImageVector, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clip(Shapes.Large).background(Flat.Faint).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 12.sp, lineHeight = 15.sp, color = Flat.Fg, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
        if (action != null) PillButton(action, onAction, compact = true)
    }
}

@Composable
private fun GraphPanel(s: DspState, playing: Boolean, selected: Int, onSelect: (Int) -> Unit) {
    var range by rememberSaveable { mutableFloatStateOf(12f) }
    val live = rememberLiveSpectrum(playing)
    Panel(padding = 12.dp) {
        Overline(tr("Charakterystyka"), trailing = {
            Segmented(listOf(6f, 12f, 15f), range, { "±${it.roundToInt()}" }) { range = it }
        })
        EqGraph(s, live, selected, range, onSelect, Modifier.fillMaxWidth().height(232.dp).clip(Shapes.Medium).background(Flat.Faint))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            EngineStatus(playing, Modifier.weight(1f))
            val auto = remember(s) { Dsp.autoGainDb(s) }
            if (auto < -0.05f) Text(tr("zapas {0} dB", dbText(auto)), fontSize = 10.sp, color = Flat.Dim)
        }
        ClipWarning(s)
        val band = s.bands.getOrNull(selected)
        if (band != null) BandInspector(band, selected) { onSelect(-1) }
        else Text(
            tr("Przeciągnij punkt, aby zmienić częstotliwość i wzmocnienie. Dwa dotknięcia zerują pasmo."),
            fontSize = 10.sp, lineHeight = 13.sp, color = Flat.Faded, modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
    }
}

/** Przy wyłączonym zapasie: krzywa z przedwzmacniaczem wychodzi ponad 0 dB – grozi przester albo pompowanie limitera. */
@Composable
private fun ClipWarning(s: DspState) {
    if (s.autoGain || !s.enabled) return
    val over = remember(s) { Dsp.peakBoostDb(s) + s.preamp }
    if (over < 0.5f) return
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(Shapes.Medium).background(Flat.Faint).padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            tr("Szczyt {0} dB ponad pełną skalą – {1}", dbText(over), if (s.limiter) tr("limiter będzie ściszał") else tr("możliwy przester")),
            fontSize = 11.sp, lineHeight = 14.sp, color = Flat.Fg, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        PillButton(tr("Auto zapas"), { Dsp.update { it.copy(autoGain = true) } }, compact = true)
    }
}

/** Edycja zaznaczonego pasma: typ filtra, częstotliwość, wzmocnienie i dobroć (−/+ z powtarzaniem). */
@Composable
private fun BandInspector(b: EqBand, index: Int, onClose: () -> Unit) {
    var typeMenu by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Pasmo {0}", index + 1).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Flat.Dim)
            Spacer(Modifier.width(8.dp))
            Box {
                PillButton(filterLabel(b.type), { typeMenu = true }, compact = true, icon = Icons.Rounded.Tune)
                CompactMenu(typeMenu, { typeMenu = false }) {
                    FilterType.entries.forEach { t ->
                        CMenuItem(filterLabel(t), checked = t == b.type) {
                            typeMenu = false
                            Dsp.checkpoint()
                            Dsp.setBand(index, b.copy(type = t, q = if (t == FilterType.PEAK) Dsp.qFor(10) else Dsp.SHELF_Q))
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (Dsp.hasGain(b.type) && abs(b.gain) > 0.01f) {
                PillButton(tr("Zeruj"), { Dsp.checkpoint(); Dsp.setBand(index, b.copy(gain = 0f)) }, compact = true)
                Spacer(Modifier.width(4.dp))
            }
            RoundButton(Icons.Rounded.Close, tr("Zamknij"), onClose, size = 30.dp, iconSize = 16.dp)
        }
        Gap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Częstotliwość co 1/24 oktawy, wzmocnienie co 0,5 dB, dobroć co ~8%.
            Stepper(tr("Częstotliwość"), freqText(b.freq), Modifier.weight(1.2f), onStart = { Dsp.checkpoint() },
                onMinus = { bandStep(index) { it.copy(freq = roundFreq(it.freq / 2f.pow(1 / 24f))) } },
                onPlus = { bandStep(index) { it.copy(freq = roundFreq(it.freq * 2f.pow(1 / 24f))) } })
            Stepper(tr("Wzmocnienie"), dbText(b.gain), Modifier.weight(1f), enabled = Dsp.hasGain(b.type), onStart = { Dsp.checkpoint() },
                onMinus = { bandStep(index) { it.copy(gain = r2(it.gain - 0.5f)) } },
                onPlus = { bandStep(index) { it.copy(gain = r2(it.gain + 0.5f)) } })
            Stepper(tr("Dobroć"), String.format(Locale.ROOT, "%.2f", b.q), Modifier.weight(1f), onStart = { Dsp.checkpoint() },
                onMinus = { bandStep(index) { it.copy(q = ((it.q / 1.08f) * 100).roundToInt() / 100f) } },
                onPlus = { bandStep(index) { it.copy(q = ((it.q * 1.08f) * 100).roundToInt() / 100f) } })
        }
    }
}

/** Zmiana pasma na podstawie jego bieżącej wartości (powtarzanie przy przytrzymaniu czyta świeży stan). */
private fun bandStep(i: Int, change: (EqBand) -> EqBand) {
    val b = Dsp.value.bands.getOrNull(i) ?: return
    Dsp.setBand(i, change(b))
}

private fun roundFreq(f: Float) = when {
    f >= 1000 -> (f / 10).roundToInt() * 10f
    f >= 100 -> f.roundToInt().toFloat()
    else -> (f * 10).roundToInt() / 10f
}

@Composable
private fun FadersPanel(s: DspState, selected: Int, onSelect: (Int) -> Unit) {
    Panel(padding = 12.dp) {
        Overline(tr("Pasma"), trailing = {
            PillButton(tr("Wyzeruj"), { onSelect(-1); Dsp.flatten() }, compact = true, icon = Icons.Rounded.VerticalAlignCenter)
        })
        val many = s.bands.size > 10
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            EqFader(
                tr("Pre"), s.preamp, -Dsp.MAX_PREAMP..Dsp.MAX_PREAMP, Modifier.width(FaderWidth + 4.dp), enabled = s.enabled,
                onStart = { Dsp.checkpoint() },
            ) { v -> Dsp.update { it.copy(preamp = r2(v)) } }
            Box(Modifier.padding(horizontal = 4.dp, vertical = 22.dp).width(1.dp).height(148.dp).background(Flat.Hair))
            Row(if (many) Modifier.weight(1f).horizontalScroll(rememberScrollState()) else Modifier.weight(1f)) {
                s.bands.forEachIndexed { i, b ->
                    EqFader(
                        freqLabel(b.freq), b.gain, -Dsp.MAX_GAIN..Dsp.MAX_GAIN,
                        if (many) Modifier.width(FaderWidth) else Modifier.weight(1f),
                        selected = i == selected, enabled = s.enabled && Dsp.hasGain(b.type),
                        onSelect = { onSelect(i) }, onStart = { Dsp.checkpoint() },
                    ) { v -> bandStep(i) { it.copy(gain = r2(v)) } }
                }
            }
        }
        Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Liczba pasm"), fontSize = 13.sp, color = Flat.Fg)
                Text(
                    if (s.bands.size in Dsp.BAND_COUNTS) tr("Kształt krzywej zostaje") else tr("Teraz: {0} (własny układ)", s.bands.size),
                    fontSize = 10.sp, lineHeight = 12.sp, color = Compact.TextDim,
                )
            }
            Segmented(Dsp.BAND_COUNTS, s.bands.size, { it.toString() }) { n -> onSelect(-1); Dsp.setBandCount(n) }
        }
    }
}

@Composable
private fun QuickPresets(s: DspState, openAll: () -> Unit, onApply: () -> Unit) {
    Panel(padding = 12.dp) {
        Overline(tr("Presety"), trailing = { PillButton(tr("Wszystkie"), openAll, compact = true) })
        // Własne najpierw; wbudowany o tej samej nazwie jest przez własny zasłonięty (jak w applyPreset).
        val names = remember(s.userPresets) { (s.userPresets.map { it.name } + Dsp.builtIn.map { it.name }).distinct() }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(names, key = { it }) { name ->
                val bands = remember(name, s.userPresets, s.bands.size) { Dsp.bandsOf(name, s) ?: emptyList() }
                PresetCard(tr(name), bands, s.preset == name, Modifier.width(96.dp)) { onApply(); Dsp.applyPreset(name) }
            }
        }
    }
}

/** Karta presetu: miniatura krzywej i nazwa; wybrany – blok akcentu. */
@Composable
private fun PresetCard(name: String, bands: List<EqBand>, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val on = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier.reactive(name, onClick = onClick).clip(Shapes.Medium)
            .background(if (selected) accent else Flat.Faint).padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        CurveThumb(bands, if (selected) on else Flat.Fg.copy(alpha = 0.8f), Modifier.fillMaxWidth().height(28.dp))
        Gap(6.dp)
        Text(
            name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) on else Flat.Fg,
        )
    }
}

// ---------- Zakładka: Presety ----------

private fun androidx.compose.foundation.lazy.LazyListScope.presetsTab(vm: MainViewModel, s: DspState, clip: EqClipboard, onSave: () -> Unit) {
    item(key = "mine") { UserPresets(s, onSave) }
    PresetGroup.entries.forEach { g ->
        item(key = "g_" + g.name) {
            SetCard(groupLabel(g)) {
                val list = Dsp.builtIn.filter { it.group == g }
                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { p ->
                                val bands = remember(p.name, s.bands.size) { Dsp.bandsOf(p.name, s) ?: emptyList() }
                                PresetCard(tr(p.name), bands, s.preset == p.name, Modifier.weight(1f)) { Dsp.applyPreset(p.name) }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
    item(key = "exchange") {
        SetCard(tr("Wymiana")) {
            SetAction(Icons.Rounded.ContentPaste, tr("Wklej ze schowka"), tr("Equalizer APO i AutoEQ: ParametricEQ albo GraphicEQ")) { clip.import() }
            SetAction(Icons.Rounded.ContentCopy, tr("Kopiuj jako tekst"), tr("Ten sam format – do Equalizer APO, Wavelet, PC")) { clip.copy(EqText.export(s)) }
            SetNote(tr("Gotowe korekcje dla tysięcy modeli słuchawek znajdziesz w projekcie AutoEQ – skopiuj zawartość pliku ParametricEQ.txt i wklej tutaj."))
        }
    }
    item(key = "auto") {
        val settings by vm.settings.collectAsState()
        SetCard(tr("Automatyka")) {
            SetSwitch(Icons.Rounded.AutoAwesome, tr("Preset według gatunku"), tr("Przy każdym utworze dobiera preset do jego gatunku; ręczne zmiany obowiązują do następnego utworu"), settings.smartEq) { v ->
                vm.updateSettings { it.copy(smartEq = v) }
            }
        }
    }
}

private fun groupLabel(g: PresetGroup) = when (g) {
    PresetGroup.TONE -> tr("Barwa")
    PresetGroup.GENRE -> tr("Gatunki")
    PresetGroup.DEVICE -> tr("Urządzenia i mowa")
}

@Composable
private fun UserPresets(s: DspState, onSave: () -> Unit) {
    var renaming by remember { mutableStateOf<String?>(null) }
    renaming?.let { old ->
        NameDialog(tr("Zmień nazwę"), tr("Nazwa presetu"), old, { renaming = null }) { Dsp.renamePreset(old, it); renaming = null }
    }
    SetCard(tr("Twoje presety")) {
        SetAction(Icons.Rounded.Save, tr("Zapisz bieżące ustawienia"), tr("Pasma i przedwzmacniacz jako nowy preset")) { onSave() }
        if (s.userPresets.isEmpty()) SetNote(tr("Nie masz jeszcze własnych presetów."))
        s.userPresets.forEach { p -> UserPresetRow(p, s.preset == p.name, { renaming = p.name }) }
    }
}

@Composable
private fun UserPresetRow(p: DspPreset, selected: Boolean, onRename: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(Shapes.Medium)
            .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .reactive(p.name) { Dsp.applyPreset(p.name) }.padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 58.dp, height = 32.dp).clip(Shapes.Small).background(Flat.Faint).padding(horizontal = 5.dp, vertical = 4.dp)) {
            CurveThumb(p.bands, if (selected) accent else Flat.Fg.copy(alpha = 0.8f), Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(p.name, fontSize = 13.sp, color = if (selected) accent else Flat.Fg, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                plural(p.bands.size, "{0} pasmo", "{0} pasma", "{0} pasm") + if (abs(p.preamp) > 0.05f) " · pre ${dbText(p.preamp)} dB" else "",
                fontSize = 10.sp, color = Compact.TextDim,
            )
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(16.dp))
        Box {
            RoundButton(Icons.Rounded.MoreVert, tr("Więcej"), { menu = true }, background = Color.Transparent, size = 36.dp, iconSize = 18.dp)
            CompactMenu(menu, { menu = false }) {
                CMenuItem(tr("Nadpisz bieżącymi"), Icons.Rounded.Save) { menu = false; Dsp.savePreset(p.name) }
                CMenuItem(tr("Zmień nazwę"), Icons.Rounded.DriveFileRenameOutline) { menu = false; onRename() }
                CMenuDivider()
                CMenuItem(tr("Usuń"), Icons.Rounded.Delete, danger = true) { menu = false; Dsp.deletePreset(p.name) }
            }
        }
    }
}

// ---------- Zakładka: Efekty ----------

private fun androidx.compose.foundation.lazy.LazyListScope.fxTab(s: DspState, playing: Boolean) {
    item(key = "tone") {
        SetCard(tr("Barwa")) {
            ValueSlider(tr("Basy"), s.bass, -12f..12f, { dbText(it) + " dB" }, neutral = 0f, step = 0.5f, enabled = s.enabled,
                subtitle = tr("Półka poniżej {0} Hz", Dsp.BASS_FREQ.roundToInt()), onStart = { Dsp.checkpoint() }) { v -> Dsp.update { it.copy(bass = v) } }
            ValueSlider(tr("Soprany"), s.treble, -12f..12f, { dbText(it) + " dB" }, neutral = 0f, step = 0.5f, enabled = s.enabled,
                subtitle = tr("Półka powyżej {0} kHz", (Dsp.TREBLE_FREQ / 1000).roundToInt()), onStart = { Dsp.checkpoint() }) { v -> Dsp.update { it.copy(treble = v) } }
            Gap(4.dp)
        }
    }
    item(key = "space") {
        val fx by AudioFx.state.collectAsState()
        SetCard(tr("Przestrzeń")) {
            ValueSlider(tr("Szerokość stereo"), s.width, 0f..2f, ::pct, neutral = 1f, step = 0.05f, enabled = s.enabled && !s.mono,
                onStart = { Dsp.checkpoint() }) { v -> Dsp.update { it.copy(width = v) } }
            ValueSlider(tr("Balans"), s.balance, -1f..1f, ::balanceLabel, neutral = 0f, step = 0.05f, enabled = s.enabled,
                onStart = { Dsp.checkpoint() }) { v -> Dsp.update { it.copy(balance = v) } }
            ValueSlider(tr("Crossfeed"), s.crossfeed, 0f..1f, { if (it <= 0f) tr("Wył.") else pct(it) }, step = 0.05f, enabled = s.enabled,
                subtitle = tr("Miesza kanały jak głośniki w pokoju – mniej męczy na słuchawkach"), onStart = { Dsp.checkpoint() }) { v -> Dsp.update { it.copy(crossfeed = v) } }
            if (fx.virtualizerSupported) {
                ValueSlider(tr("Przestrzenność"), fx.virtualizer.toFloat(), 0f..1000f, { if (it <= 0f) tr("Wył.") else "${(it / 10).roundToInt()}%" }, step = 50f,
                    enabled = s.enabled, subtitle = tr("Efekt systemowy Androida, najlepiej na słuchawkach")) { AudioFx.setVirtualizer(it.roundToInt()) }
            }
            Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                TileGrid(listOf(
                    Tile(Icons.Rounded.MergeType, tr("Mono"), tr("Oba kanały zmiksowane w jeden – np. przy jednej słuchawce"), s.mono) { v -> Dsp.checkpoint(); Dsp.update { it.copy(mono = v) } },
                    Tile(Icons.Rounded.SwapHoriz, tr("Zamiana kanałów"), tr("Lewy kanał gra po prawej i odwrotnie"), s.swap) { v -> Dsp.checkpoint(); Dsp.update { it.copy(swap = v) } },
                ))
            }
        }
    }
    item(key = "dyn") {
        SetCard(tr("Dynamika")) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                TileGrid(listOf(
                    Tile(Icons.Rounded.Compress, tr("Kompresor"), tr("Kompresor dynamiki z miękkim kolanem – wyrównuje głośne i ciche fragmenty"), s.compressor) { v -> Dsp.checkpoint(); Dsp.update { it.copy(compressor = v) } },
                    Tile(Icons.Rounded.Shield, tr("Limiter"), tr("Limiter z wyprzedzeniem – łagodnie ścisza szczyty, zanim przekroczą pełną skalę"), s.limiter) { v -> Dsp.checkpoint(); Dsp.update { it.copy(limiter = v) } },
                ))
            }
            if (s.compressor) {
                ValueSlider(tr("Próg"), s.compThreshold, -40f..0f, { "${it.roundToInt()} dB".replace("-", "−") }, neutral = 0f, default = -18f, step = 1f,
                    enabled = s.enabled, onStart = { Dsp.checkpoint() }) { v -> Dsp.update { it.copy(compThreshold = v) } }
                ValueSlider(tr("Stosunek"), s.compRatio, 1f..10f, { String.format(Locale.ROOT, "%.1f:1", it) }, neutral = 1f, default = 3f, step = 0.5f,
                    enabled = s.enabled, onStart = { Dsp.checkpoint() }) { v -> Dsp.update { it.copy(compRatio = v) } }
            }
            if (s.enabled && (s.compressor || s.limiter)) {
                val m = rememberMeter(playing)
                Gap(6.dp)
                ReductionBar(m[2])
                Gap(8.dp)
            }
        }
    }
}

fun balanceLabel(v: Float): String = when {
    v < -0.02f -> "L ${(-v * 100).roundToInt()}"
    v > 0.02f -> (if (I18n.english) "R " else "P ") + (v * 100).roundToInt()
    else -> tr("środek")
}

// ---------- Zakładka: Wyjście ----------

private fun androidx.compose.foundation.lazy.LazyListScope.outputTab(s: DspState, playing: Boolean) {
    item(key = "signal") {
        SetCard(tr("Sygnał")) {
            val m = rememberMeter(playing)
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelMeter("L", m[0])
                LevelMeter(if (I18n.english) "R" else "P", m[1])
                Gap(2.dp)
                EngineStatus(playing)
                if (m[2] < -0.1f) Text(tr("Ściszenie przez dynamikę: {0} dB", String.format(Locale.ROOT, "%.1f", m[2]).replace("-", "−")), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    item(key = "gain") {
        SetCard(tr("Poziom")) {
            val auto = remember(s) { Dsp.autoGainDb(s) }
            SetSwitch(
                Icons.Rounded.VerticalAlignCenter, tr("Automatyczny zapas"),
                if (s.autoGain) tr("Teraz {0} dB – podbicia nie wpadają w limiter", dbText(auto)) else tr("Wyłączony – podbicia mogą przesterować albo pompować"),
                s.autoGain,
            ) { v -> Dsp.checkpoint(); Dsp.update { it.copy(autoGain = v) } }
            ValueSlider(tr("Przedwzmacniacz"), s.preamp, -Dsp.MAX_PREAMP..Dsp.MAX_PREAMP, { dbText(it) + " dB" }, neutral = 0f, step = 0.5f, enabled = s.enabled,
                subtitle = if (s.autoGain) tr("Dodawany do automatycznego zapasu") else null, onStart = { Dsp.checkpoint() }) { v -> Dsp.update { it.copy(preamp = v) } }
            Gap(4.dp)
        }
    }
    item(key = "rg") {
        SetCard(tr("ReplayGain")) {
            SetSegment(
                Icons.Rounded.GraphicEq, tr("Wyrównanie głośności"), ReplayGainMode.entries, s.replayGain,
                { when (it) { ReplayGainMode.OFF -> tr("Wyłączone"); ReplayGainMode.TRACK -> tr("Według utworu"); ReplayGainMode.ALBUM -> tr("Według albumu") } },
                short = { when (it) { ReplayGainMode.OFF -> tr("Wył."); ReplayGainMode.TRACK -> tr("Utwór"); ReplayGainMode.ALBUM -> tr("Album") } },
            ) { v -> Dsp.update { it.copy(replayGain = v) } }
            if (s.replayGain != ReplayGainMode.OFF) {
                ValueSlider(tr("Wzmocnienie ReplayGain"), s.rgPreamp, -12f..12f, { dbText(it) + " dB" }, neutral = 0f, step = 0.5f,
                    subtitle = tr("Dodawane do znacznika; limiter pilnuje przesteru")) { v -> Dsp.update { it.copy(rgPreamp = v) } }
            }
            val t = Dsp.rgTrack; val a = Dsp.rgAlbum
            SetNote(
                if (t == null && a == null) tr("Bieżący utwór nie ma znaczników ReplayGain – gra bez zmian.")
                else tr("Znaczniki bieżącego utworu: utwór {0}, album {1}", t?.let { dbText(it) + " dB" } ?: "—", a?.let { dbText(it) + " dB" } ?: "—"),
            )
        }
    }
    item(key = "profiles") {
        var copied by remember { mutableStateOf(false) }
        SetCard(tr("Profile wyjścia")) {
            SetSwitch(Icons.Rounded.DevicesOther, tr("Osobno dla każdego wyjścia"), tr("Słuchawki, Bluetooth, USB i głośnik mają własne ustawienia, przełączane samoczynnie"), s.autoProfiles) { v ->
                Dsp.setAutoProfiles(v)
            }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Dsp.OUTPUTS.forEach { k -> OutputTile(k, k == s.profile, s.autoProfiles && (k == s.profile || Dsp.hasProfile(k)), Modifier.weight(1f)) }
            }
            if (s.autoProfiles) {
                SetAction(Icons.Rounded.ContentCopy, if (copied) tr("Skopiowano do wszystkich wyjść") else tr("Użyj bieżących ustawień wszędzie"), tr("Nadpisuje profile pozostałych wyjść")) {
                    Dsp.copyToAllProfiles(); copied = true
                }
            }
            SetNote(
                if (s.autoProfiles) tr("Gra teraz: {0}. Zmiany zapisują się w profilu tego wyjścia.", outputLabel(s.profile))
                else tr("Gra teraz: {0}. Wszystkie wyjścia używają tych samych ustawień.", outputLabel(s.profile)),
            )
        }
    }
    item(key = "loud") {
        val fx by AudioFx.state.collectAsState()
        if (fx.loudnessSupported) SetCard(tr("Głośność systemowa")) {
            ValueSlider(tr("Wzmocnienie systemowe"), fx.loudness.toFloat(), 0f..1500f, { if (it <= 0f) tr("Wył.") else "+" + String.format(Locale.ROOT, "%.1f", it / 100) + " dB" }, step = 50f,
                enabled = s.enabled, subtitle = tr("Efekt Androida ponad maksimum telefonu; przy dużych wartościach zniekształca")) { AudioFx.setLoudness(it.roundToInt()) }
            Gap(4.dp)
        }
    }
}

/** Kafelek wyjścia: bieżące – blok akcentu; kropka – wyjście ma zapisany własny profil. */
@Composable
private fun OutputTile(kind: String, current: Boolean, hasOwn: Boolean, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val on = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier.clip(Shapes.Medium).background(if (current) accent else Flat.Faint).padding(vertical = 9.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(outputIcon(kind), null, tint = if (current) on else Flat.Dim, modifier = Modifier.size(20.dp))
            if (hasOwn && !current) Box(Modifier.align(Alignment.TopEnd).size(6.dp).clip(CircleShape).background(accent))
        }
        Text(
            outputShort(kind), fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp).alpha(if (current) 1f else 0.85f),
            color = if (current) on else Flat.Dim, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

fun outputLabel(k: String) = when (k) {
    "HEADPHONES" -> tr("Słuchawki przewodowe")
    "BLUETOOTH" -> tr("Bluetooth")
    "USB" -> tr("USB / DAC")
    else -> tr("Głośnik")
}

private fun outputShort(k: String) = when (k) {
    "HEADPHONES" -> tr("Słuchawki")
    "BLUETOOTH" -> "Bluetooth"
    "USB" -> "USB"
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
    FilterType.LOW_PASS -> tr("Dolnoprzepustowy")
    FilterType.HIGH_PASS -> tr("Górnoprzepustowy")
    FilterType.NOTCH -> tr("Wycięcie")
}

// ---------- Schowek ----------

/** Kopiowanie i wklejanie ustawień jako tekstu (format Equalizer APO). */
class EqClipboard(private val cm: ClipboardManager?, private val vm: MainViewModel) {
    fun copy(text: String) {
        cm?.setPrimaryClip(ClipData.newPlainText("OpenAMP EQ", text))
        vm.message(tr("Skopiowano ustawienia korektora"))
    }

    fun import() {
        val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
        vm.message(
            when {
                text.isBlank() -> tr("Schowek jest pusty")
                Dsp.importText(text) -> tr("Wczytano {0}", plural(Dsp.value.bands.size, "{0} pasmo", "{0} pasma", "{0} pasm"))
                else -> tr("W schowku nie ma ustawień korektora")
            },
        )
    }
}

@Composable
private fun rememberClipboard(vm: MainViewModel): EqClipboard {
    val context = LocalContext.current
    return remember(vm) { EqClipboard(context.getSystemService(ClipboardManager::class.java), vm) }
}
