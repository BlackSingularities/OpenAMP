package com.blacksingularities.openamp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HeadsetOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.BuildConfig
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.R
import com.blacksingularities.openamp.ai.ClaudeCurator
import com.blacksingularities.openamp.ai.CurationSource
import com.blacksingularities.openamp.ai.CuratorStatus
import com.blacksingularities.openamp.data.AodMode
import com.blacksingularities.openamp.data.CoverShape
import com.blacksingularities.openamp.data.LockBackground
import com.blacksingularities.openamp.data.NotifButton
import com.blacksingularities.openamp.data.NotifStyle
import com.blacksingularities.openamp.data.NpBackground
import com.blacksingularities.openamp.data.SeekStyle
import com.blacksingularities.openamp.data.ThemeMode
import com.blacksingularities.openamp.data.VisualizerStyle
import com.blacksingularities.openamp.i18n.AppLanguage
import com.blacksingularities.openamp.i18n.plural
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.lock.LockscreenActivity
import com.blacksingularities.openamp.lock.LockscreenLauncher
import com.blacksingularities.openamp.ui.theme.AccentChoices
import com.blacksingularities.openamp.update.UpdateState
import com.blacksingularities.openamp.widget.WidgetConfigActivity
import kotlin.math.roundToInt

private enum class SetTab { LOOK, PLAYER, SOUND, LIBRARY, AI, NOTIFY, OTHER }

private fun SetTab.label() = when (this) {
    SetTab.LOOK -> tr("Wygląd"); SetTab.PLAYER -> tr("Odtwarzacz"); SetTab.SOUND -> tr("Dźwięk")
    SetTab.LIBRARY -> tr("Biblioteka"); SetTab.AI -> "AI"; SetTab.NOTIFY -> tr("Blokada"); SetTab.OTHER -> tr("Inne")
}

private fun SetTab.icon(): ImageVector = when (this) {
    SetTab.LOOK -> Icons.Rounded.Palette; SetTab.PLAYER -> Icons.Rounded.PlayCircle; SetTab.SOUND -> Icons.Rounded.GraphicEq
    SetTab.LIBRARY -> Icons.Rounded.LibraryMusic; SetTab.AI -> Icons.Rounded.AutoAwesome; SetTab.NOTIFY -> Icons.Rounded.Notifications
    SetTab.OTHER -> Icons.Rounded.Info
}

@Composable
fun SettingsScreen(vm: MainViewModel, bottomPadding: Dp) {
    var tab by rememberSaveable { mutableStateOf(SetTab.LOOK) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), { vm.back() }, background = Color.Transparent, size = 40.dp)
            Text(tr("Ustawienia"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 4.dp))
        }
        TabChips(SetTab.entries, tab, { it.label() }, { it.icon() }, Modifier.padding(bottom = 6.dp)) { tab = it }
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "settab") { t ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 2.dp, bottom = bottomPadding + 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (t) {
                    SetTab.LOOK -> item { LookTab(vm) }
                    SetTab.PLAYER -> item { PlayerTab(vm) }
                    SetTab.SOUND -> item { SoundTab(vm) }
                    SetTab.LIBRARY -> item { LibraryTabSettings(vm) }
                    SetTab.AI -> item { AiTab(vm) }
                    SetTab.NOTIFY -> item { NotifyTab(vm) }
                    SetTab.OTHER -> item { OtherTab(vm) }
                }
            }
        }
    }
}

@Composable
private fun LookTab(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    var picker by remember { mutableStateOf(false) }
    if (picker) ColorPickerDialog(s.accent, { picker = false }) { c -> vm.updateSettings { it.copy(accent = c) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetCard(tr("Interfejs")) {
            SetSegment(Icons.Rounded.Language, tr("Język"), AppLanguage.entries, s.language, ::languageLabel, short = { when (it) { AppLanguage.SYSTEM -> "Auto"; AppLanguage.PL -> "PL"; AppLanguage.EN -> "EN" } }) { v -> vm.updateSettings { it.copy(language = v) } }
            SetDropdown(Icons.Rounded.DarkMode, tr("Motyw"), ThemeMode.entries, s.theme, ::themeLabel) { v -> vm.updateSettings { it.copy(theme = v) } }
            SetSwitch(Icons.Rounded.ViewAgenda, tr("Przyklejone nagłówki list"), tr("Litery, lata i grupy zostają u góry podczas przewijania"), s.stickyHeaders) { v -> vm.updateSettings { it.copy(stickyHeaders = v) } }
        }
        SetCard(tr("Kolory")) {
            SetSwitch(Icons.Rounded.Colorize, tr("Kolory z okładki"), tr("Akcent dopasowuje się do okładki odtwarzanego utworu"), s.artworkColors) { v -> vm.updateSettings { it.copy(artworkColors = v) } }
            val locked = s.artworkColors
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RowIcon(Icons.Rounded.Palette, enabled = !locked)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(tr("Kolor akcentu"), fontSize = 13.sp, color = if (locked) Compact.TextFaint else Color.White)
                    if (locked) Text(tr("Wyłącz kolory z okładki, aby wybrać"), fontSize = 10.sp, color = Compact.TextFaint)
                }
            }
            LazyRow(
                Modifier.alpha(if (locked) 0.3f else 1f).padding(bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(Brush.sweepGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f % 360f, 0.8f, 1f))) }))
                            .clickable(enabled = !locked) { picker = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Colorize, tr("Dowolny kolor"), tint = Color.Black, modifier = Modifier.size(15.dp)) }
                }
                val all = if (s.accent !in AccentChoices) listOf(s.accent) + AccentChoices else AccentChoices
                items(all) { c ->
                    val sel = c == s.accent
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(if (sel) Color.White else Color.White.copy(alpha = 0.1f))
                            .clickable(enabled = !locked) { vm.updateSettings { it.copy(accent = c) } }.padding(if (sel) 3.dp else 2.dp)
                            .clip(CircleShape).background(Color(c)),
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp)) }
                }
            }
        }
        SetCard(tr("Ekran odtwarzania")) {
            SetDropdown(Icons.Rounded.Image, tr("Tło"), NpBackground.entries, s.npBackground, ::bgLabel) { v -> vm.updateSettings { it.copy(npBackground = v) } }
            SetDropdown(Icons.Rounded.Crop, tr("Kształt okładki"), CoverShape.entries, s.coverShape, ::shapeLabel) { v -> vm.updateSettings { it.copy(coverShape = v) } }
            SetDropdown(Icons.Rounded.GraphicEq, tr("Wizualizacja"), VisualizerStyle.entries, s.visualizer, ::visLabel) { v -> vm.updateSettings { it.copy(visualizer = v) } }
            SetDropdown(Icons.Rounded.Waves, tr("Taśma utworu"), SeekStyle.entries, s.seekStyle, ::seekLabel, subtitle = tr("Przewijanie palcem w całym polu przycisków")) { v -> vm.updateSettings { it.copy(seekStyle = v) } }
        }
    }
}

@Composable
private fun PlayerTab(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetCard(tr("Ekran odtwarzania")) {
            SetSwitch(Icons.Rounded.ThumbUp, tr("Przyciski „lubię / nie lubię”"), null, s.showLikes) { v -> vm.updateSettings { it.copy(showLikes = v) } }
            SetSwitch(Icons.Rounded.Info, tr("Parametry pliku"), tr("Format, częstotliwość i bitrate na dole"), s.showTechInfo) { v -> vm.updateSettings { it.copy(showTechInfo = v) } }
            SetSwitch(Icons.Rounded.Swipe, tr("Gest przesunięcia okładki"), tr("W bok – następny lub poprzedni utwór"), s.coverGestures) { v -> vm.updateSettings { it.copy(coverGestures = v) } }
            SetSwitch(Icons.Rounded.VerticalAlignBottom, tr("Chowaj dolny pasek"), tr("Po kilku sekundach bez dotyku podczas grania"), s.autoHideBar) { v -> vm.updateSettings { it.copy(autoHideBar = v) } }
            SetSwitch(Icons.Rounded.Smartphone, tr("Nie wygaszaj ekranu"), null, s.keepScreenOn) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }
            SetSwitch(Icons.Rounded.BrightnessLow, tr("Tryb AMOLED"), tr("Po 20 s bez dotyku czarny ekran z zegarem, okładką i tekstem"), s.ambient) { v -> vm.updateSettings { it.copy(ambient = v) } }
        }
        SetCard(tr("Tekst utworu")) {
            SetSwitch(Icons.Rounded.Lyrics, tr("Pokazuj tekst od razu"), null, s.lyricsByDefault) { v -> vm.updateSettings { it.copy(lyricsByDefault = v) } }
            SetSwitch(Icons.Rounded.FormatAlignCenter, tr("Wyśrodkowany"), null, s.lyricsCenter) { v -> vm.updateSettings { it.copy(lyricsCenter = v) } }
            SetSwitch(Icons.Rounded.Public, tr("Szukaj w internecie"), tr("LRCLIB, gdy plik nie ma tekstu"), s.lyricsOnline) { v -> vm.updateSettings { it.copy(lyricsOnline = v) } }
            SetSlider(Icons.Rounded.TextFields, tr("Rozmiar tekstu"), "${s.lyricsSize} sp", s.lyricsSize.toFloat(), 14f..34f, 9) { v -> vm.updateSettings { it.copy(lyricsSize = v.roundToInt()) } }
        }
    }
}

@Composable
private fun SoundTab(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetCard(tr("Odtwarzanie")) {
            SetSwitch(Icons.Rounded.Gradient, tr("Płynne wyciszanie"), tr("Przy pauzie i wznowieniu"), s.fade) { v -> vm.updateSettings { it.copy(fade = v) } }
            SetSwitch(Icons.Rounded.FastForward, tr("Pomijanie ciszy"), null, s.skipSilence) { v -> vm.updateSettings { it.copy(skipSilence = v) } }
            SetSwitch(Icons.Rounded.Shuffle, tr("Sprytne mieszanie"), tr("Bez tego samego wykonawcy pod rząd"), s.smartShuffle) { v -> vm.updateSettings { it.copy(smartShuffle = v) } }
            SetSwitch(Icons.Rounded.Restore, tr("Pamiętaj kolejkę i pozycję"), null, s.rememberQueue) { v -> vm.updateSettings { it.copy(rememberQueue = v) } }
            SetSlider(Icons.Rounded.Percent, tr("Licz odtworzenie po"), tr("{0}% utworu", s.countPlayPercent), s.countPlayPercent.toFloat(), 10f..100f, 8) { v ->
                vm.updateSettings { it.copy(countPlayPercent = (v / 10).roundToInt() * 10) }
            }
        }
        SetCard(tr("Słuchawki")) {
            SetSwitch(Icons.Rounded.HeadsetOff, tr("Pauza po odłączeniu"), null, s.pauseOnDisconnect) { v -> vm.updateSettings { it.copy(pauseOnDisconnect = v) } }
            SetSwitch(Icons.Rounded.Headphones, tr("Wznów po podłączeniu"), null, s.resumeOnHeadset) { v -> vm.updateSettings { it.copy(resumeOnHeadset = v) } }
        }
        SetCard(tr("Korektor")) {
            SetSwitch(Icons.Rounded.Tune, tr("Preset według gatunku"), null, s.smartEq) { v -> vm.updateSettings { it.copy(smartEq = v) } }
            SetAction(Icons.Rounded.Equalizer, tr("Otwórz korektor")) { vm.goTab(Route.Equalizer) }
        }
    }
}

@Composable
private fun LibraryTabSettings(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    var filterOpen by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    if (filterOpen) LibraryFilterSheet(vm) { filterOpen = false }
    if (clearConfirm) AlertDialog(
        onDismissRequest = { clearConfirm = false },
        title = { Text(tr("Wyczyść statystyki odtworzeń")) },
        confirmButton = { TextButton(onClick = { Graph.userData.clearStats(); vm.message(tr("Statystyki wyczyszczone")); clearConfirm = false }) { Text(tr("Usuń")) } },
        dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text(tr("Anuluj")) } },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetCard(tr("Zawartość")) {
            SetAction(Icons.Rounded.FilterList, tr("Foldery i formaty"), tr("Co pokazywać w bibliotece")) { filterOpen = true }
            SetSlider(Icons.Rounded.Timer, tr("Minimalna długość utworu"), tr("{0} s", s.minDurationSec), s.minDurationSec.toFloat(), 0f..120f, 11) { v ->
                vm.updateSettings { it.copy(minDurationSec = (v / 10).roundToInt() * 10) }
            }
            SetSwitch(Icons.Rounded.VisibilityOff, tr("Ukryj utwory „nie lubię”"), null, s.hideDisliked) { v -> vm.updateSettings { it.copy(hideDisliked = v) } }
        }
        SetCard(tr("Dane")) {
            SetAction(Icons.Rounded.Refresh, tr("Odśwież bibliotekę"), tr("Usunięte pliki znikają też z playlist i miksów")) { vm.rescan() }
            SetAction(Icons.Rounded.Favorite, tr("Statystyki")) { vm.open(Route.Stats) }
            SetAction(Icons.Rounded.DeleteSweep, tr("Wyczyść statystyki odtworzeń"), danger = true) { clearConfirm = true }
        }
    }
}

@Composable
private fun AiTab(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val key by Graph.curator.apiKey.collectAsState()
    val cur by Graph.curator.state.collectAsState()
    val status by Graph.curator.status.collectAsState()
    val all by vm.tracks.collectAsState()
    var editKey by remember { mutableStateOf(false) }
    if (editKey) ApiKeyDialog(key, { editKey = false }) { Graph.curator.setApiKey(it); editKey = false }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetCard(tr("Analiza biblioteki")) {
            val st = status
            val info = when {
                st is CuratorStatus.Running -> st.stage
                cur.analyzedAt > 0 -> tr("Ostatnio: {0}", formatDate(cur.analyzedAt)) + " • " +
                    plural(cur.categories.size, "{0} kategoria", "{0} kategorie", "{0} kategorii") + " • " +
                    plural(cur.mixes.size, "{0} miks", "{0} miksy", "{0} miksów")
                else -> tr("Jeszcze nie analizowano")
            }
            SetAction(Icons.Rounded.AutoAwesome, tr("Analizuj teraz"), info, enabled = st !is CuratorStatus.Running) { vm.runAnalysis() }
            if (st is CuratorStatus.Running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 4.dp))
            if (st is CuratorStatus.Failed) SetNote(tr("Błąd: {0}", st.message))
            val change = remember(all, cur) { Graph.curator.changeCount(all) }
            if (cur.analyzedAt > 0 && change > 0) SetNote(tr("Od ostatniej analizy zmieniło się {0} utworów", change))
            SetSwitch(Icons.Rounded.Category, tr("Proponuj analizę"), tr("Gdy biblioteka zmieni się o co najmniej 5 utworów"), s.aiPrompt) { v -> vm.updateSettings { it.copy(aiPrompt = v) } }
            if (cur.source != CurationSource.NONE) SetAction(Icons.Rounded.Close, tr("Usuń wyniki analizy"), danger = true) { Graph.curator.clear() }
        }
        SetCard("Claude") {
            SetAction(
                Icons.Rounded.Key, tr("Klucz API Anthropic"),
                if (key.isBlank()) tr("Brak – analiza działa wtedy lokalnie, na telefonie") else "••••" + key.takeLast(4),
            ) { editKey = true }
            SetDropdown(Icons.Rounded.Psychology, tr("Model"), ClaudeCurator.MODELS, s.aiModel, ::modelLabel, enabled = key.isNotBlank()) { v -> vm.updateSettings { it.copy(aiModel = v) } }
            SetNote(tr("Z kluczem lista utworów (tytuł, wykonawca, album, gatunek, rok, liczba odtworzeń) trafia do Claude, który układa kategorie i miksy. Pliki muzyczne nie są wysyłane."))
        }
    }
}

fun modelLabel(m: String) = when (m) {
    "claude-opus-5-5" -> "Opus 5.5"
    "claude-sonnet-5-5" -> "Sonnet 5.5"
    "claude-haiku-5-5" -> "Haiku 5.5"
    else -> m
}

@Composable
private fun ApiKeyDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Klucz API Anthropic")) },
        text = {
            Column {
                OutlinedTextField(v, { v = it }, singleLine = true, label = { Text("sk-ant-…") }, visualTransformation = PasswordVisualTransformation())
                Text(tr("Klucz zostaje tylko na tym telefonie."), fontSize = 11.sp, color = Compact.TextDim, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v) }) { Text(tr("Zapisz")) } },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) TextButton(onClick = { onSave("") }) { Text(tr("Usuń")) }
                TextButton(onClick = onDismiss) { Text(tr("Anuluj")) }
            }
        },
    )
}

@Composable
private fun NotifyTab(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetCard(tr("Powiadomienie")) {
            SetSegment(Icons.Rounded.Notifications, tr("Wygląd"), NotifStyle.entries, s.notifStyle, ::notifStyleLabel, short = { if (it == NotifStyle.CUSTOM) tr("Własny") else tr("Systemowy") }) { v -> vm.updateSettings { it.copy(notifStyle = v) } }
            SetDropdown(Icons.Rounded.SkipNext, tr("Przycisk 1"), NotifButton.entries, s.notifButton1, ::notifLabel) { v -> vm.updateSettings { it.copy(notifButton1 = v) } }
            SetDropdown(Icons.Rounded.SkipNext, tr("Przycisk 2"), NotifButton.entries, s.notifButton2, ::notifLabel) { v -> vm.updateSettings { it.copy(notifButton2 = v) } }
            SetDropdown(Icons.Rounded.Timer, tr("Zamykaj po pauzie"), AutoCloseOptions, s.autoCloseMin, ::autoCloseLabel) { v -> vm.updateSettings { it.copy(autoCloseMin = v) } }
            SetNote(
                if (s.notifStyle == NotifStyle.CUSTOM) tr("Duża okładka po lewej, „wykonawca – tytuł” nad wyśrodkowanymi przyciskami. Od Androida 12 system zawsze dodaje mały nagłówek z ikoną aplikacji, a odtwarzacz w szybkich ustawieniach pojawia się tylko w stylu systemowym.")
                else tr("Systemowy odtwarzacz w panelu powiadomień i szybkich ustawieniach."),
            )
        }
        SetCard(tr("Ekran blokady")) {
            SetSwitch(Icons.Rounded.Lock, tr("Odtwarzacz na ekranie blokady"), tr("Po wygaszeniu ekranu podczas grania; przesunięcie w górę wraca do blokady"), s.lockScreen) { v -> vm.updateSettings { it.copy(lockScreen = v) } }
            if (s.lockScreen && !LockscreenLauncher.canShow(context)) {
                SetAction(Icons.Rounded.ScreenLockPortrait, tr("Zezwól na pełny ekran"), tr("Android wymaga zgody na wyświetlanie na pełnym ekranie."), danger = true) {
                    LockscreenLauncher.permissionIntent(context)?.let { runCatching { context.startActivity(it) } }
                }
            }
            val on = s.lockScreen
            SetDropdown(Icons.Rounded.BlurOn, tr("Tło"), LockBackground.entries, s.lockBackground, ::lockBgLabel, enabled = on) { v -> vm.updateSettings { it.copy(lockBackground = v) } }
            SetSwitch(Icons.Rounded.Schedule, tr("Zegar i data"), null, s.lockClock, enabled = on) { v -> vm.updateSettings { it.copy(lockClock = v) } }
            SetSwitch(Icons.Rounded.LinearScale, tr("Pasek przewijania"), null, s.lockSeek, enabled = on) { v -> vm.updateSettings { it.copy(lockSeek = v) } }
            SetSwitch(Icons.AutoMirrored.Rounded.QueueMusic, tr("Następny utwór"), null, s.lockNext, enabled = on) { v -> vm.updateSettings { it.copy(lockNext = v) } }
            SetSwitch(Icons.Rounded.Favorite, tr("Serduszko „lubię”"), null, s.lockLike, enabled = on) { v -> vm.updateSettings { it.copy(lockLike = v) } }
            SetSwitch(Icons.Rounded.Shuffle, tr("Losowanie i powtarzanie"), null, s.lockShuffleRepeat, enabled = on) { v -> vm.updateSettings { it.copy(lockShuffleRepeat = v) } }
            SetAction(Icons.Rounded.Visibility, tr("Podgląd"), enabled = on) { context.startActivity(Intent(context, LockscreenActivity::class.java)) }
        }
        SetCard("AOD") {
            val on = s.lockScreen
            SetDropdown(Icons.Rounded.Contrast, tr("Tryb AOD"), AodMode.entries, s.aodMode, ::aodLabel, enabled = on) { v -> vm.updateSettings { it.copy(aodMode = v) } }
            val aod = on && s.aodMode != AodMode.OFF
            SetSlider(Icons.Rounded.Timer, tr("AOD po"), tr("{0} s", s.aodTimeoutSec), s.aodTimeoutSec.toFloat(), 5f..60f, 10, enabled = aod) { v ->
                vm.updateSettings { it.copy(aodTimeoutSec = (v / 5).roundToInt() * 5) }
            }
            SetSwitch(Icons.Rounded.BrightnessLow, tr("Przyciemnij ekran"), null, s.aodDim, enabled = aod) { v -> vm.updateSettings { it.copy(aodDim = v) } }
            SetSwitch(Icons.Rounded.Image, tr("Okładka"), null, s.aodArt, enabled = aod) { v -> vm.updateSettings { it.copy(aodArt = v) } }
            SetNote(tr("Systemowego „always on” nie da się zmienić z aplikacji – AOD OpenAMP to przygaszony ekran nad blokadą, który nie gaśnie podczas grania."))
        }
    }
}

@Composable
private fun OtherTab(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val st by vm.update.collectAsState()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetCard(tr("Aktualizacje")) {
            SetSwitch(Icons.Rounded.SystemUpdate, tr("Sprawdzaj automatycznie"), tr("Raz dziennie w tle i przy starcie"), s.autoUpdate) { v -> vm.updateSettings { it.copy(autoUpdate = v) } }
            Box(Modifier.padding(start = 48.dp, end = 12.dp, top = 2.dp, bottom = 6.dp)) { UpdateStatus(vm, st) }
            if (s.lastUpdateCheck > 0) SetNote(tr("Ostatnio sprawdzono: {0}", formatDate(s.lastUpdateCheck)))
        }
        SetCard(tr("Widżety na pulpicie")) {
            SetAction(Icons.Rounded.Widgets, tr("Widżety"), tr("Okładka, pasek, odtwarzacz i skróty – każdy z własnym wyglądem")) {
                context.startActivity(Intent(context, WidgetConfigActivity::class.java))
            }
        }
        SetCard(tr("O aplikacji")) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null,
                    Modifier.size(44.dp),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text("OpenAMP", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(tr("Wersja {0}", BuildConfig.VERSION_NAME), fontSize = 11.sp, color = Compact.TextDim)
                }
            }
            SetAction(Icons.Rounded.Code, tr("Kod źródłowy na GitHubie")) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.UPDATE_REPO}"))) }
            }
        }
    }
}

private val AutoCloseOptions = listOf(0, 1, 5, 15, 30, 60, -1)
fun autoCloseLabel(m: Int) = when (m) { -1 -> tr("Nigdy"); 0 -> tr("Od razu (10 s)"); else -> tr("Po {0} min", m) }
fun notifLabel(b: NotifButton) = when (b) {
    NotifButton.NONE -> tr("Brak"); NotifButton.LIKE -> tr("Lubię"); NotifButton.SHUFFLE -> tr("Losowo")
    NotifButton.REPEAT -> tr("Powtarzanie"); NotifButton.CLOSE -> tr("Zamknij odtwarzacz")
}
fun notifStyleLabel(n: NotifStyle) = when (n) { NotifStyle.CUSTOM -> tr("Własny: okładka po lewej"); NotifStyle.SYSTEM -> tr("Systemowy odtwarzacz") }
fun lockBgLabel(b: LockBackground) = when (b) { LockBackground.BLUR -> tr("Rozmyta okładka"); LockBackground.ART_COLOR -> tr("Kolor okładki"); LockBackground.BLACK -> tr("Czysta czerń") }
fun aodLabel(m: AodMode) = when (m) { AodMode.OFF -> tr("Wyłączony"); AodMode.CHARGING -> tr("Podczas ładowania"); AodMode.ALWAYS -> tr("Zawsze podczas grania") }

fun languageLabel(l: AppLanguage) = when (l) { AppLanguage.SYSTEM -> tr("Systemowy"); AppLanguage.PL -> "Polski"; AppLanguage.EN -> "English" }
fun themeLabel(t: ThemeMode) = when (t) { ThemeMode.SYSTEM -> tr("Systemowy"); ThemeMode.DARK -> tr("Ciemny"); ThemeMode.LIGHT -> tr("Jasny"); ThemeMode.BLACK -> tr("Czarny (AMOLED)") }
fun visLabel(v: VisualizerStyle) = when (v) { VisualizerStyle.BARS -> tr("Słupki widma"); VisualizerStyle.WAVE -> tr("Fala"); VisualizerStyle.CIRCLE -> tr("Okrąg"); VisualizerStyle.PULSE -> tr("Puls"); VisualizerStyle.MIRROR -> tr("Lustrzane słupki"); VisualizerStyle.RING_WAVE -> tr("Fala w okręgu"); VisualizerStyle.LINE -> tr("Krzywa widma"); VisualizerStyle.NONE -> tr("Brak") }
fun bgLabel(v: NpBackground) = when (v) { NpBackground.BLUR -> tr("Rozmyta okładka"); NpBackground.GRADIENT -> tr("Gradient akcentu"); NpBackground.AURORA -> tr("Zorza (reaguje na muzykę)"); NpBackground.BLACK -> tr("Czysta czerń") }
fun shapeLabel(v: CoverShape) = when (v) { CoverShape.ROUNDED -> tr("Zaokrąglona"); CoverShape.SQUARE -> tr("Kwadratowa"); CoverShape.CIRCLE -> tr("Okrągła"); CoverShape.VINYL -> tr("Płyta winylowa") }
fun seekLabel(v: SeekStyle) = when (v) {
    SeekStyle.WAVEFORM -> tr("Fala dźwiękowa")
    SeekStyle.MIRROR -> tr("Lustrzana fala")
    SeekStyle.LINE -> tr("Linia konturu")
    SeekStyle.DOTS -> tr("Matryca kropek")
    SeekStyle.SEGMENTS -> tr("Segmenty LED")
    SeekStyle.TAPE -> tr("Taśma przesuwana")
    SeekStyle.SPECTROGRAM -> tr("Spektrogram")
    SeekStyle.CASSETTE -> tr("Kaseta")
}

@Composable
fun UpdateStatus(vm: MainViewModel, st: UpdateState) {
    when (st) {
        UpdateState.Idle -> PillButton(tr("Sprawdź teraz"), { vm.checkUpdate() }, icon = Icons.Rounded.SystemUpdate)
        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(Modifier.weight(1f)); Spacer(Modifier.width(12.dp)); Text(tr("Sprawdzanie…"), fontSize = 12.sp)
        }
        UpdateState.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr("Masz najnowszą wersję"), fontSize = 12.sp, modifier = Modifier.weight(1f))
            PillButton(tr("Sprawdź teraz"), { vm.checkUpdate() })
        }
        is UpdateState.Available -> Column {
            Text(tr("Dostępna wersja {0}", st.info.version), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (st.info.notes.isNotBlank()) Text(st.info.notes.take(800), fontSize = 11.sp, color = Compact.TextDim, modifier = Modifier.padding(vertical = 6.dp))
            PillButton(tr("Pobierz i zainstaluj"), { vm.downloadAndInstall() }, active = true)
        }
        is UpdateState.Downloading -> Column {
            Text(tr("Pobieranie… {0}%", (st.progress * 100).roundToInt()), fontSize = 12.sp)
            LinearProgressIndicator({ st.progress }, Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        is UpdateState.Ready -> PillButton(tr("Zainstaluj"), { vm.downloadAndInstall() }, active = true)
        is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(Modifier.weight(1f)); Spacer(Modifier.width(12.dp)); Text(tr("Instalowanie…"), fontSize = 12.sp)
        }
        is UpdateState.Failed -> Column {
            Text(tr("Błąd aktualizacji: {0}", st.message), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
            PillButton(tr("Sprawdź teraz"), { vm.checkUpdate() }, Modifier.padding(top = 6.dp))
        }
    }
}
