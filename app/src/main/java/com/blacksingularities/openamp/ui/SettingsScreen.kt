package com.blacksingularities.openamp.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import com.blacksingularities.openamp.ai.AiProvider
import com.blacksingularities.openamp.ai.CuratorStore
import com.blacksingularities.openamp.ai.CurationSource
import com.blacksingularities.openamp.ai.CuratorStatus
import com.blacksingularities.openamp.data.AodMode
import com.blacksingularities.openamp.data.CoverShape
import com.blacksingularities.openamp.data.LockBackground
import com.blacksingularities.openamp.data.NotifButton
import com.blacksingularities.openamp.data.NotifStyle
import com.blacksingularities.openamp.data.LiveColors
import com.blacksingularities.openamp.data.AppSettings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.BrightnessMedium
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Sekcje ustawień – wszystkie na jednej liście (jak w OpenPDF); chipy u góry przewijają do sekcji.
 * Każda sekcja to karty z podtytułem: najpierw wybory i suwaki, pod nimi kafelki-przełączniki.
 */
private enum class SetSection { GENERAL, LOOK, PLAYER, SOUND, LIBRARY, NOTIFY, AI }

private fun SetSection.label() = when (this) {
    SetSection.LOOK -> tr("Wygląd"); SetSection.PLAYER -> tr("Odtwarzacz"); SetSection.SOUND -> tr("Dźwięk")
    SetSection.LIBRARY -> tr("Biblioteka"); SetSection.NOTIFY -> tr("Powiadomienia"); SetSection.AI -> "AI"
    SetSection.GENERAL -> tr("Ogólne")
}

private fun SetSection.icon(): ImageVector = when (this) {
    SetSection.LOOK -> Icons.Rounded.Palette; SetSection.PLAYER -> Icons.Rounded.PlayCircle; SetSection.SOUND -> Icons.Rounded.GraphicEq
    SetSection.LIBRARY -> Icons.Rounded.LibraryMusic; SetSection.NOTIFY -> Icons.Rounded.Notifications; SetSection.AI -> Icons.Rounded.AutoAwesome
    SetSection.GENERAL -> Icons.Rounded.Settings
}

@Composable
fun SettingsScreen(vm: MainViewModel, bottomPadding: Dp) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Chip bieżącej sekcji podąża za przewijaniem.
    val current by remember { derivedStateOf { SetSection.entries[list.firstVisibleItemIndex.coerceIn(0, SetSection.entries.lastIndex)] } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), { vm.back() }, background = Color.Transparent, size = 40.dp)
            Text(tr("Ustawienia"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Flat.Fg, modifier = Modifier.padding(start = 4.dp))
        }
        TabChips(SetSection.entries, current, { it.label() }, { it.icon() }, Modifier.padding(bottom = 2.dp)) { s ->
            scope.launch { list.animateScrollToItem(s.ordinal) }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 0.dp, bottom = bottomPadding + 20.dp),
        ) {
            items(SetSection.entries, key = { it.name }) { s ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(s.icon(), s.label())
                    when (s) {
                        SetSection.LOOK -> LookSection(vm)
                        SetSection.PLAYER -> PlayerSection(vm)
                        SetSection.SOUND -> SoundSection(vm)
                        SetSection.LIBRARY -> LibrarySection(vm)
                        SetSection.NOTIFY -> NotifySection(vm)
                        SetSection.AI -> AiSection(vm)
                        SetSection.GENERAL -> GeneralSection(vm)
                    }
                }
            }
        }
    }
}

/** Kafelki wewnątrz karty – z marginesem, żeby nie dotykały jej krawędzi. */
@Composable
private fun CardTiles(tiles: List<Tile>) {
    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { TileGrid(tiles) }
}

@Composable
private fun LookSection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    var picker by remember { mutableStateOf(false) }
    if (picker) ColorPickerDialog(s.accent, { picker = false }) { c -> vm.updateSettings { it.copy(accent = c) } }
    SetCard(tr("Interfejs")) {
        SetSegment(Icons.Rounded.Language, tr("Język"), AppLanguage.entries, s.language, ::languageLabel, short = { when (it) { AppLanguage.SYSTEM -> "Auto"; AppLanguage.PL -> "PL"; AppLanguage.EN -> "EN" } }) { v -> vm.updateSettings { it.copy(language = v) } }
        SetDropdown(Icons.Rounded.DarkMode, tr("Motyw"), ThemeMode.entries, s.theme, ::themeLabel) { v -> vm.updateSettings { it.copy(theme = v) } }
        CardTiles(
            listOf(
                Tile(Icons.Rounded.ViewAgenda, tr("Nagłówki list"), tr("Litery, lata i grupy zostają u góry podczas przewijania"), s.stickyHeaders) { v -> vm.updateSettings { it.copy(stickyHeaders = v) } },
            ),
        )
    }
    SetCard(tr("Kolor akcentu")) {
        CardTiles(
            listOf(
                Tile(Icons.Rounded.Colorize, tr("Z okładki"), tr("Akcent dopasowuje się do okładki odtwarzanego utworu"), s.artworkColors) { v -> vm.updateSettings { it.copy(artworkColors = v) } },
            ),
        )
        val locked = s.artworkColors
        if (locked) SetNote(tr("Wyłącz kolory z okładki, aby wybrać"))
        LazyRow(
            Modifier.alpha(if (locked) 0.3f else 1f).padding(top = 2.dp, bottom = 6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .background(Brush.sweepGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f % 360f, 0.8f, 1f))) }))
                        .clickable(enabled = !locked) { picker = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Colorize, tr("Dowolny kolor"), tint = Color.Black, modifier = Modifier.size(15.dp)) }
            }
            val all = if (s.accent !in AccentChoices) listOf(s.accent) + AccentChoices else AccentChoices
            items(all) { c ->
                val sel = c == s.accent
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(if (sel) Flat.Fg else Flat.Fg.copy(alpha = 0.1f))
                        .clickable(enabled = !locked) { vm.updateSettings { it.copy(accent = c) } }.padding(if (sel) 3.dp else 2.dp)
                        .clip(CircleShape).background(Color(c)),
                    contentAlignment = Alignment.Center,
                ) { if (sel) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp)) }
            }
        }
    }
}

@Composable
private fun PlayerSection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    SetCard(tr("Okładka i tło")) {
        SetDropdown(Icons.Rounded.Image, tr("Tło"), NpBackground.entries, s.npBackground, ::bgLabel) { v -> vm.updateSettings { it.copy(npBackground = v) } }
        if (s.npBackground == NpBackground.LIVE) LiveBackgroundSettings(s, vm)
        SetDropdown(Icons.Rounded.Crop, tr("Kształt okładki"), CoverShape.entries, s.coverShape, ::shapeLabel) { v -> vm.updateSettings { it.copy(coverShape = v) } }
        val demo = rememberDemoSpectrum()
        val accent = MaterialTheme.colorScheme.primary
        SetDropdown(
            Icons.Rounded.GraphicEq, tr("Wizualizacja"), VisualizerByType, s.visualizer, ::visLabel,
            preview = { v, m -> VisualizerPreview(v, demo, accent, m) }, group = ::visGroup,
        ) { v -> vm.updateSettings { it.copy(visualizer = v) } }
        SetDropdown(
            Icons.Rounded.Waves, tr("Taśma utworu"), SeekStyle.entries, s.seekStyle, ::seekLabel, subtitle = tr("Przewijanie palcem w całym polu przycisków"),
            preview = { v, m -> SeekPreview(v, demo, accent, m) },
        ) { v -> vm.updateSettings { it.copy(seekStyle = v) } }
    }
    SetCard(tr("Tytuł i wykonawca")) {
        SetSegment(
            Icons.Rounded.FormatAlignCenter, tr("Wyrównanie"), listOf(false, true), s.titleCenter,
            { if (it) tr("Wyśrodkowane") else tr("Do lewej") },
            optionIcon = { if (it) Icons.Rounded.FormatAlignCenter else Icons.AutoMirrored.Rounded.FormatAlignLeft },
        ) { v -> vm.updateSettings { it.copy(titleCenter = v) } }
        SetSlider(Icons.Rounded.TextFields, tr("Rozmiar tytułu"), "${s.titleSize} sp", s.titleSize.toFloat(), 14f..30f, 15, subtitle = tr("Wykonawca zmienia się razem z tytułem")) { v ->
            vm.updateSettings { it.copy(titleSize = v.roundToInt()) }
        }
    }
    SetCard(tr("Elementy ekranu")) {
        CardTiles(
            listOf(
                Tile(Icons.Rounded.ThumbUp, tr("Lubię / nie lubię"), tr("Przyciski „lubię / nie lubię” na okładce"), s.showLikes) { v -> vm.updateSettings { it.copy(showLikes = v) } },
                Tile(Icons.Rounded.Info, tr("Parametry pliku"), tr("Format, częstotliwość i bitrate na dole"), s.showTechInfo) { v -> vm.updateSettings { it.copy(showTechInfo = v) } },
                Tile(Icons.Rounded.Swipe, tr("Gest okładki"), tr("Przesunięcie okładki w bok – następny lub poprzedni utwór"), s.coverGestures) { v -> vm.updateSettings { it.copy(coverGestures = v) } },
                Tile(Icons.Rounded.VerticalAlignBottom, tr("Chowaj pasek"), tr("Dolny pasek chowa się po kilku sekundach bez dotyku podczas grania"), s.autoHideBar) { v -> vm.updateSettings { it.copy(autoHideBar = v) } },
                Tile(Icons.Rounded.Smartphone, tr("Ekran włączony"), tr("Nie wygaszaj ekranu na ekranie odtwarzania"), s.keepScreenOn) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } },
                Tile(Icons.Rounded.BrightnessLow, tr("Tryb AMOLED"), tr("Po 20 s bez dotyku czarny ekran z zegarem, okładką i tekstem"), s.ambient) { v -> vm.updateSettings { it.copy(ambient = v) } },
            ),
        )
    }
    SetCard(tr("Tekst utworu")) {
        SetSlider(Icons.Rounded.TextFields, tr("Rozmiar tekstu"), "${s.lyricsSize} sp", s.lyricsSize.toFloat(), 14f..34f, 9) { v -> vm.updateSettings { it.copy(lyricsSize = v.roundToInt()) } }
        CardTiles(
            listOf(
                Tile(Icons.Rounded.Lyrics, tr("Od razu"), tr("Pokazuj tekst utworu od razu po otwarciu odtwarzacza"), s.lyricsByDefault) { v -> vm.updateSettings { it.copy(lyricsByDefault = v) } },
                Tile(Icons.Rounded.FormatAlignCenter, tr("Wyśrodkowany"), tr("Tekst utworu wyśrodkowany"), s.lyricsCenter) { v -> vm.updateSettings { it.copy(lyricsCenter = v) } },
                Tile(Icons.Rounded.Public, tr("Z internetu"), tr("Szukaj tekstu w LRCLIB, gdy plik go nie ma"), s.lyricsOnline) { v -> vm.updateSettings { it.copy(lyricsOnline = v) } },
            ),
        )
    }
}

@Composable
private fun SoundSection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    SetCard(tr("Odtwarzanie")) {
        SetSlider(Icons.Rounded.Percent, tr("Licz odtworzenie po"), tr("{0}% utworu", s.countPlayPercent), s.countPlayPercent.toFloat(), 10f..100f, 8) { v ->
            vm.updateSettings { it.copy(countPlayPercent = (v / 10).roundToInt() * 10) }
        }
        CardTiles(
            listOf(
                Tile(Icons.Rounded.Gradient, tr("Wyciszanie"), tr("Płynne wyciszanie przy pauzie i wznowieniu"), s.fade) { v -> vm.updateSettings { it.copy(fade = v) } },
                Tile(Icons.Rounded.FastForward, tr("Bez ciszy"), tr("Pomijanie ciszy w utworach"), s.skipSilence) { v -> vm.updateSettings { it.copy(skipSilence = v) } },
                Tile(Icons.Rounded.Shuffle, tr("Sprytne mieszanie"), tr("Bez tego samego wykonawcy pod rząd"), s.smartShuffle) { v -> vm.updateSettings { it.copy(smartShuffle = v) } },
                Tile(Icons.Rounded.Restore, tr("Pamiętaj kolejkę"), tr("Pamiętaj kolejkę i pozycję po zamknięciu"), s.rememberQueue) { v -> vm.updateSettings { it.copy(rememberQueue = v) } },
            ),
        )
    }
    SetCard(tr("Słuchawki i korektor")) {
        CardTiles(
            listOf(
                Tile(Icons.Rounded.HeadsetOff, tr("Pauza bez słuchawek"), tr("Pauza po odłączeniu słuchawek"), s.pauseOnDisconnect) { v -> vm.updateSettings { it.copy(pauseOnDisconnect = v) } },
                Tile(Icons.Rounded.Headphones, tr("Wznów ze słuchawkami"), tr("Wznów odtwarzanie po podłączeniu słuchawek"), s.resumeOnHeadset) { v -> vm.updateSettings { it.copy(resumeOnHeadset = v) } },
                Tile(Icons.Rounded.Tune, tr("Korektor gatunku"), tr("Preset korektora według gatunku utworu"), s.smartEq) { v -> vm.updateSettings { it.copy(smartEq = v) } },
            ),
        )
        SetAction(Icons.Rounded.Equalizer, tr("Otwórz korektor")) { vm.goTab(Route.Equalizer) }
    }
}

@Composable
private fun LibrarySection(vm: MainViewModel) {
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
    SetCard(tr("Zawartość")) {
        SetAction(Icons.Rounded.FilterList, tr("Foldery i formaty"), tr("Co pokazywać w bibliotece")) { filterOpen = true }
        SetSlider(Icons.Rounded.Timer, tr("Minimalna długość utworu"), tr("{0} s", s.minDurationSec), s.minDurationSec.toFloat(), 0f..120f, 11) { v ->
            vm.updateSettings { it.copy(minDurationSec = (v / 10).roundToInt() * 10) }
        }
        CardTiles(
            listOf(
                Tile(Icons.Rounded.VisibilityOff, tr("Ukryj „nie lubię”"), tr("Ukryj utwory oznaczone „nie lubię”"), s.hideDisliked) { v -> vm.updateSettings { it.copy(hideDisliked = v) } },
            ),
        )
    }
    SetCard(tr("Dane")) {
        SetAction(Icons.Rounded.Refresh, tr("Odśwież bibliotekę"), tr("Usunięte pliki znikają też z playlist i miksów")) { vm.rescan() }
        SetAction(Icons.Rounded.Favorite, tr("Statystyki")) { vm.open(Route.Stats) }
        SetAction(Icons.Rounded.DeleteSweep, tr("Wyczyść statystyki odtworzeń"), danger = true) { clearConfirm = true }
    }
}

@Composable
private fun AiSection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val provider by Graph.curator.provider.collectAsState()
    val configs by Graph.curator.configs.collectAsState()
    @Suppress("UNUSED_VARIABLE") val tick by Graph.curator.enabledTick.collectAsState()
    val cur by Graph.curator.state.collectAsState()
    val status by Graph.curator.status.collectAsState()
    val all by vm.tracks.collectAsState()
    val scope = rememberCoroutineScope()
    val cfg = configs[provider] ?: CuratorStore.ProviderConfig()
    var editKey by remember { mutableStateOf(false) }
    var editUrl by remember { mutableStateOf(false) }
    var editModel by remember { mutableStateOf(false) }
    var pickModel by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var listError by remember(provider) { mutableStateOf<String?>(null) }
    if (editKey) TextDialog(tr("Klucz API – {0}", provider.label), cfg.key, "sk-…", secret = true, onDismiss = { editKey = false }) { v ->
        Graph.curator.updateConfig(provider) { it.copy(key = v) }; editKey = false
    }
    if (editUrl) TextDialog(tr("Adres serwera"), cfg.baseUrl, provider.baseUrl.ifBlank { "http://192.168.1.10:11434/v1" }, onDismiss = { editUrl = false }) { v ->
        Graph.curator.updateConfig(provider) { it.copy(baseUrl = v.ifBlank { provider.baseUrl }) }; editUrl = false
    }
    if (editModel) TextDialog(tr("Nazwa modelu"), cfg.model, "model-id", onDismiss = { editModel = false }) { v ->
        Graph.curator.updateConfig(provider) { it.copy(model = v) }; editModel = false
    }
    fun refresh() {
        loading = true
        scope.launch {
            listError = Graph.curator.refreshModels(provider)
            loading = false
        }
    }
    if (pickModel) ModelPickerDialog(
        providerLabel(provider), cfg.models, cfg.model, loading, listError,
        onRefresh = { refresh() },
        onPick = { m -> Graph.curator.updateConfig(provider) { it.copy(model = m.id) }; pickModel = false },
        onDismiss = { pickModel = false },
    )

    SetCard(tr("Dostawca")) {
        SetDropdown(Icons.Rounded.Hub, tr("Dostawca AI"), AiProvider.entries, provider, { providerLabel(it) }) { v -> Graph.curator.setProvider(v) }
        if (provider.needsKey) {
            SetAction(Icons.Rounded.Key, tr("Klucz API"), if (cfg.key.isBlank()) tr("Brak – bez klucza analiza działa lokalnie, na telefonie") else "••••" + cfg.key.takeLast(4)) { editKey = true }
        } else {
            CardTiles(
                listOf(
                    Tile(Icons.Rounded.Dns, tr("Używaj serwera"), tr("Analiza na tym serwerze (bez klucza) zamiast na telefonie"), Graph.curator.localEnabled(provider)) { v -> Graph.curator.setLocalEnabled(provider, v) },
                ),
            )
            SetAction(Icons.Rounded.Key, tr("Klucz API (opcjonalnie)"), if (cfg.key.isBlank()) tr("Brak") else "••••" + cfg.key.takeLast(4)) { editKey = true }
        }
        SetAction(Icons.Rounded.Link, tr("Adres serwera"), cfg.baseUrl.ifBlank { tr("Nie ustawiono") }) { editUrl = true }
    }
    SetCard(tr("Model")) {
        val chosen = cfg.models.firstOrNull { it.id == cfg.model }
        SetAction(
            Icons.Rounded.Psychology, tr("Model"),
            (chosen?.title ?: cfg.model.ifBlank { tr("Nie wybrano") }) +
                if (cfg.models.isNotEmpty()) " · " + plural(cfg.models.size, "{0} model", "{0} modele", "{0} modeli") else "",
            enabled = cfg.models.isNotEmpty() || !provider.needsKey || cfg.key.isNotBlank(),
        ) { pickModel = true }
        SetAction(Icons.Rounded.Edit, tr("Wpisz nazwę modelu"), cfg.model.ifBlank { tr("Nie wybrano") }) { editModel = true }
        SetAction(
            Icons.Rounded.Refresh, tr("Pobierz listę modeli"),
            when {
                loading -> tr("Pobieranie…")
                listError != null -> tr("Błąd: {0}", listError ?: "")
                else -> tr("Z API dostawcy")
            },
            enabled = !loading && (!provider.needsKey || cfg.key.isNotBlank()),
        ) { refresh() }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 4.dp))
    }
    SetCard(tr("Analiza biblioteki")) {
        val st = status
        val ready = Graph.curator.remoteReady(provider)
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
        SetNote(
            if (ready) tr("Analizuje: {0} · {1}", providerLabel(provider), cfg.model)
            else tr("Dostawca nie jest gotowy – analiza zostanie wykonana lokalnie, na telefonie."),
        )
        val change = remember(all, cur) { Graph.curator.changeCount(all) }
        if (cur.analyzedAt > 0 && change > 0) SetNote(tr("Od ostatniej analizy zmieniło się {0} utworów", change))
        CardTiles(
            listOf(
                Tile(Icons.Rounded.Category, tr("Proponuj analizę"), tr("Gdy biblioteka zmieni się o co najmniej 5 utworów"), s.aiPrompt) { v -> vm.updateSettings { it.copy(aiPrompt = v) } },
            ),
        )
        if (cur.source != CurationSource.NONE) SetAction(Icons.Rounded.Close, tr("Usuń wyniki analizy"), danger = true) { Graph.curator.clear() }
        SetNote(tr("Do dostawcy trafia tylko lista utworów (tytuł, wykonawca, album, gatunek, rok, liczba odtworzeń). Pliki muzyczne nie są wysyłane, a klucze zostają na tym telefonie."))
    }
}

private fun providerLabel(p: AiProvider) = if (p == AiProvider.CUSTOM) tr("Własny (zgodny z OpenAI)") else p.label

/** Okno z jednym polem tekstowym (klucz, adres, nazwa modelu). */
@Composable
private fun TextDialog(title: String, initial: String, hint: String, secret: Boolean = false, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                v, { v = it }, singleLine = true, label = { Text(hint) },
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(v.trim()) }) { Text(tr("Zapisz")) } },
        dismissButton = {
            Row {
                if (secret && initial.isNotBlank()) TextButton(onClick = { onSave("") }) { Text(tr("Usuń")) }
                TextButton(onClick = onDismiss) { Text(tr("Anuluj")) }
            }
        },
    )
}

@Composable
private fun NotifySection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val context = LocalContext.current
    SetCard(tr("Powiadomienie")) {
        SetSegment(Icons.Rounded.Notifications, tr("Wygląd"), NotifStyle.entries, s.notifStyle, ::notifStyleLabel, short = { if (it == NotifStyle.CUSTOM) tr("Własny") else tr("Systemowy") }) { v -> vm.updateSettings { it.copy(notifStyle = v) } }
        if (s.notifStyle == NotifStyle.CUSTOM) CardTiles(
            listOf(
                Tile(Icons.Rounded.Compress, tr("Zwarte"), tr("Niskie powiadomienie – także po rozwinięciu"), s.notifCompact) { v -> vm.updateSettings { it.copy(notifCompact = v) } },
                Tile(Icons.Rounded.Close, tr("Przycisk X"), tr("Przycisk zamykający cały odtwarzacz w powiadomieniu"), s.notifClose) { v -> vm.updateSettings { it.copy(notifClose = v) } },
            ),
        )
        SetDropdown(Icons.Rounded.SkipNext, tr("Przycisk 1"), NotifButton.entries, s.notifButton1, ::notifLabel) { v -> vm.updateSettings { it.copy(notifButton1 = v) } }
        SetDropdown(Icons.Rounded.SkipNext, tr("Przycisk 2"), NotifButton.entries, s.notifButton2, ::notifLabel) { v -> vm.updateSettings { it.copy(notifButton2 = v) } }
        SetDropdown(Icons.Rounded.Timer, tr("Zamykaj po pauzie"), AutoCloseOptions, s.autoCloseMin, ::autoCloseLabel) { v -> vm.updateSettings { it.copy(autoCloseMin = v) } }
        SetNote(
            if (s.notifStyle == NotifStyle.CUSTOM) tr("Okładka po lewej, „wykonawca – tytuł” i przyciski. Przyciski 1 i 2 są widoczne w rozwiniętym, niezwartym powiadomieniu. Od Androida 12 system dodaje mały nagłówek z ikoną aplikacji.")
            else tr("Systemowy odtwarzacz w panelu powiadomień i szybkich ustawieniach."),
        )
    }
    val on = s.lockScreen
    SetCard(tr("Ekran blokady")) {
        CardTiles(
            listOfNotNull(
                Tile(Icons.Rounded.Lock, tr("Na blokadzie"), tr("Odtwarzacz na ekranie blokady po wygaszeniu ekranu podczas grania; przesunięcie w górę wraca do blokady"), s.lockScreen) { v -> vm.updateSettings { it.copy(lockScreen = v) } },
                if (on) Tile(Icons.Rounded.Schedule, tr("Zegar i data"), tr("Zegar i data nad odtwarzaczem"), s.lockClock) { v -> vm.updateSettings { it.copy(lockClock = v) } } else null,
                if (on) Tile(Icons.Rounded.LinearScale, tr("Przewijanie"), tr("Pasek przewijania utworu"), s.lockSeek) { v -> vm.updateSettings { it.copy(lockSeek = v) } } else null,
                if (on) Tile(Icons.AutoMirrored.Rounded.QueueMusic, tr("Następny"), tr("Następny utwór w kolejce"), s.lockNext) { v -> vm.updateSettings { it.copy(lockNext = v) } } else null,
                if (on) Tile(Icons.Rounded.Favorite, tr("Serduszko"), tr("Serduszko „lubię”"), s.lockLike) { v -> vm.updateSettings { it.copy(lockLike = v) } } else null,
                if (on) Tile(Icons.Rounded.Shuffle, tr("Losuj i powtarzaj"), tr("Przyciski losowania i powtarzania"), s.lockShuffleRepeat) { v -> vm.updateSettings { it.copy(lockShuffleRepeat = v) } } else null,
            ),
        )
        if (on) {
            if (!LockscreenLauncher.canShow(context)) {
                SetAction(Icons.Rounded.ScreenLockPortrait, tr("Zezwól na pełny ekran"), tr("Android wymaga zgody na wyświetlanie na pełnym ekranie."), danger = true) {
                    LockscreenLauncher.permissionIntent(context)?.let { runCatching { context.startActivity(it) } }
                }
            }
            SetDropdown(Icons.Rounded.BlurOn, tr("Tło"), LockBackground.entries, s.lockBackground, ::lockBgLabel) { v -> vm.updateSettings { it.copy(lockBackground = v) } }
            SetAction(Icons.Rounded.Visibility, tr("Podgląd")) { context.startActivity(Intent(context, LockscreenActivity::class.java)) }
        } else {
            SetNote(tr("Włącz odtwarzacz na blokadzie, aby ustawić jego wygląd i tryb AOD."))
        }
    }
    if (on) SetCard("AOD") {
        SetDropdown(Icons.Rounded.Contrast, tr("Tryb AOD"), AodMode.entries, s.aodMode, ::aodLabel) { v -> vm.updateSettings { it.copy(aodMode = v) } }
        val aod = s.aodMode != AodMode.OFF
        SetSlider(Icons.Rounded.Timer, tr("AOD po"), tr("{0} s", s.aodTimeoutSec), s.aodTimeoutSec.toFloat(), 5f..60f, 10, enabled = aod) { v ->
            vm.updateSettings { it.copy(aodTimeoutSec = (v / 5).roundToInt() * 5) }
        }
        if (aod) CardTiles(
            listOf(
                Tile(Icons.Rounded.BrightnessLow, tr("Przyciemnij"), tr("Przyciemnij ekran w trybie AOD"), s.aodDim) { v -> vm.updateSettings { it.copy(aodDim = v) } },
                Tile(Icons.Rounded.Image, tr("Okładka"), tr("Okładka w trybie AOD"), s.aodArt) { v -> vm.updateSettings { it.copy(aodArt = v) } },
            ),
        )
        SetNote(tr("Systemowego „always on” nie da się zmienić z aplikacji – AOD OpenAMP to przygaszony ekran nad blokadą, który nie gaśnie podczas grania."))
    }
}

@Composable
private fun GeneralSection(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val st by vm.update.collectAsState()
    val context = LocalContext.current
    SetCard(tr("Widżety na pulpicie")) {
        SetAction(Icons.Rounded.Widgets, tr("Widżety"), tr("Okładka, pasek, odtwarzacz i skróty – każdy z własnym wyglądem")) {
            context.startActivity(Intent(context, WidgetConfigActivity::class.java))
        }
    }
    SetCard(tr("Aktualizacje")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Rounded.SystemUpdate)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("OpenAMP ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Flat.Fg)
                if (s.lastUpdateCheck > 0) Text(tr("Ostatnio sprawdzono: {0}", formatDate(s.lastUpdateCheck)), fontSize = 10.sp, color = Compact.TextDim)
            }
        }
        Box(Modifier.padding(start = 48.dp, end = 12.dp, top = 2.dp, bottom = 6.dp)) { UpdateStatus(vm, st) }
        CardTiles(
            listOf(
                Tile(Icons.Rounded.SystemUpdate, tr("Automatycznie"), tr("Sprawdzaj automatycznie – raz dziennie w tle i przy starcie"), s.autoUpdate) { v -> vm.updateSettings { it.copy(autoUpdate = v) } },
            ),
        )
    }
    About()
}

/** Stopka jak w OpenPDF: ikona, nazwa, wersja i odnośniki. */
@Composable
private fun About() {
    val context = LocalContext.current
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null,
            Modifier.size(44.dp),
        )
        Text(tr("OpenAMP – odtwarzacz muzyki"), fontSize = 13.sp, color = Flat.Fg, modifier = Modifier.padding(top = 6.dp))
        Text(tr("Wersja {0}", BuildConfig.VERSION_NAME), fontSize = 11.sp, color = Compact.TextDim)
        Text(
            "github.com/${BuildConfig.UPDATE_REPO}", fontSize = 12.sp, color = Flat.Fg, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp).clip(Shapes.Small).clickable { open("https://github.com/${BuildConfig.UPDATE_REPO}") }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            tr("Pomysł i wykonanie: redzikowski.com"), fontSize = 12.sp, color = Compact.TextDim,
            modifier = Modifier.clip(Shapes.Small).clickable { open("https://redzikowski.com") }.padding(horizontal = 8.dp, vertical = 4.dp),
        )
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
/** Wizualizacje pogrupowane według typu: słupki, fale, okrągłe, na końcu „brak”. */
private val VisualizerByType = listOf(
    VisualizerStyle.BARS, VisualizerStyle.MIRROR,
    VisualizerStyle.WAVE, VisualizerStyle.LINE,
    VisualizerStyle.CIRCLE, VisualizerStyle.RING_WAVE, VisualizerStyle.PULSE,
    VisualizerStyle.NONE,
)

private fun visGroup(v: VisualizerStyle) = when (v) {
    VisualizerStyle.BARS, VisualizerStyle.MIRROR -> tr("Słupki")
    VisualizerStyle.WAVE, VisualizerStyle.LINE -> tr("Fale i linie")
    VisualizerStyle.CIRCLE, VisualizerStyle.RING_WAVE, VisualizerStyle.PULSE -> tr("Wokół okładki")
    VisualizerStyle.NONE -> tr("Bez wizualizacji")
}

fun visLabel(v: VisualizerStyle) = when (v) { VisualizerStyle.BARS -> tr("Słupki widma"); VisualizerStyle.WAVE -> tr("Fala"); VisualizerStyle.CIRCLE -> tr("Okrąg"); VisualizerStyle.PULSE -> tr("Puls"); VisualizerStyle.MIRROR -> tr("Lustrzane słupki"); VisualizerStyle.RING_WAVE -> tr("Fala w okręgu"); VisualizerStyle.LINE -> tr("Krzywa widma"); VisualizerStyle.NONE -> tr("Brak") }
fun bgLabel(v: NpBackground) = when (v) { NpBackground.BLUR -> tr("Rozmyta okładka"); NpBackground.GRADIENT -> tr("Gradient akcentu"); NpBackground.AURORA -> tr("Zorza (reaguje na muzykę)"); NpBackground.LIVE -> tr("Żywe kolory (animowane)"); NpBackground.BLACK -> tr("Czysta czerń") }
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

/** Ustawienia tła „Żywe kolory”: paleta (z animowanym podglądem), własny kolor, ruch, reakcja i jasność. */
@Composable
private fun LiveBackgroundSettings(s: AppSettings, vm: MainViewModel) {
    val demo = rememberDemoSpectrum()
    val accent = MaterialTheme.colorScheme.primary
    var picker by remember { mutableStateOf(false) }
    if (picker) ColorPickerDialog(s.liveCustom, { picker = false }) { c -> vm.updateSettings { it.copy(liveCustom = c, liveColors = LiveColors.CUSTOM) } }
    SetDropdown(
        Icons.Rounded.Palette, tr("Kolory tła"), LiveColors.entries, s.liveColors, ::liveColorsLabel,
        preview = { v, m -> Box(m.clip(Shapes.Small)) { LiveBackground(null, v, s.liveCustom, accent, demo, s.liveMotion, s.liveReact, s.liveIntensity, Modifier.fillMaxSize()) } },
    ) { v -> if (v == LiveColors.CUSTOM) picker = true else vm.updateSettings { it.copy(liveColors = v) } }
    if (s.liveColors == LiveColors.CUSTOM) SetAction(Icons.Rounded.Colorize, tr("Własny kolor"), tr("Tło buduje harmonię barw wokół tego koloru"), trailing = {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color(s.liveCustom)))
    }) { picker = true }
    SetSlider(Icons.Rounded.Speed, tr("Ruch"), "${s.liveMotion}%", s.liveMotion.toFloat(), 0f..100f, 19, subtitle = tr("Jak szybko płyną plamy koloru")) { v -> vm.updateSettings { it.copy(liveMotion = (v / 5).roundToInt() * 5) } }
    SetSlider(Icons.Rounded.GraphicEq, tr("Reakcja na muzykę"), "${s.liveReact}%", s.liveReact.toFloat(), 0f..100f, 19, subtitle = tr("Bas powiększa plamy, uderzenia dają błysk; 0% – tylko spokojny ruch")) { v -> vm.updateSettings { it.copy(liveReact = (v / 5).roundToInt() * 5) } }
    SetSlider(Icons.Rounded.BrightnessMedium, tr("Intensywność"), "${s.liveIntensity}%", s.liveIntensity.toFloat(), 0f..100f, 19, subtitle = tr("Niżej – spokojniejsze, ciemniejsze tło")) { v -> vm.updateSettings { it.copy(liveIntensity = (v / 5).roundToInt() * 5) } }
}
