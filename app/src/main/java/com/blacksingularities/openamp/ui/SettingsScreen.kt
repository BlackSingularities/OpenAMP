package com.blacksingularities.openamp.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.BuildConfig
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.R
import com.blacksingularities.openamp.data.CoverShape
import com.blacksingularities.openamp.data.NpBackground
import com.blacksingularities.openamp.data.SeekStyle
import com.blacksingularities.openamp.data.ThemeMode
import com.blacksingularities.openamp.data.VisualizerStyle
import com.blacksingularities.openamp.i18n.AppLanguage
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.ui.theme.AccentChoices
import com.blacksingularities.openamp.update.UpdateState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel, bottomPadding: Dp) {
    val s by vm.settings.collectAsState()
    var choice by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    if (picker) ColorPickerDialog(s.accent, { picker = false }) { c -> vm.updateSettings { it.copy(accent = c) } }
    choice?.invoke()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz")) }
            Text(tr("Ustawienia"), style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding + 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { UpdateCard(vm) }

            item {
                Section(tr("Wygląd")) {
                    ChoiceRow(tr("Język"), languageLabel(s.language)) {
                        choice = {
                            ChoiceDialog(tr("Język"), AppLanguage.entries, s.language, ::languageLabel, { choice = null }) { v -> vm.updateSettings { it.copy(language = v) } }
                        }
                    }
                    ChoiceRow(tr("Motyw"), themeLabel(s.theme)) {
                        choice = { ChoiceDialog(tr("Motyw"), ThemeMode.entries, s.theme, ::themeLabel, { choice = null }) { v -> vm.updateSettings { it.copy(theme = v) } } }
                    }
                    SwitchRow(tr("Kolory z okładki"), tr("Akcent interfejsu dopasowuje się do okładki odtwarzanego utworu"), s.artworkColors) { v -> vm.updateSettings { it.copy(artworkColors = v) } }
                    Text(tr("Kolor akcentu"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            // Dowolny kolor z próbnika
                            Box(
                                Modifier.size(40.dp).background(
                                    androidx.compose.ui.graphics.Brush.sweepGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f % 360f, 0.8f, 1f))) }),
                                    CircleShape,
                                ).clickable { picker = true },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Palette, tr("Dowolny kolor"), tint = Color.Black) }
                        }
                        if (s.accent !in AccentChoices) item {
                            Box(
                                Modifier.size(40.dp).background(Color(s.accent), CircleShape).border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Check, null, tint = Color.Black) }
                        }
                        items(AccentChoices) { c ->
                            val sel = c == s.accent
                            Box(
                                Modifier.size(40.dp).background(Color(c), CircleShape)
                                    .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { vm.updateSettings { it.copy(accent = c) } },
                                contentAlignment = Alignment.Center,
                            ) { if (sel) Icon(Icons.Rounded.Check, null, tint = Color.Black) }
                        }
                    }
                    if (s.artworkColors) Text(
                        tr("Gdy włączone są kolory z okładki, wybrany kolor jest używany dla utworów bez okładki."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            item {
                Section(tr("Inteligencja")) {
                    SwitchRow(tr("Sprytne mieszanie"), tr("Bez powtórek wykonawcy pod rząd, rzadziej słuchane i ulubione częściej"), s.smartShuffle) { v -> vm.updateSettings { it.copy(smartShuffle = v) } }
                    SwitchRow(tr("Inteligentny korektor"), tr("Preset korektora dobierany automatycznie do gatunku utworu"), s.smartEq) { v -> vm.updateSettings { it.copy(smartEq = v) } }
                    Text(tr("Miksy na ekranie Start powstają z Twojej historii słuchania – wszystko liczone na telefonie."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    OutlinedButton(onClick = { vm.open(Route.Stats) }, modifier = Modifier.padding(top = 8.dp)) { Text(tr("Statystyki")) }
                }
            }

            item {
                Section(tr("Ekran odtwarzania")) {
                    ChoiceRow(tr("Wizualizacja"), visLabel(s.visualizer)) {
                        choice = { ChoiceDialog(tr("Wizualizacja"), VisualizerStyle.entries, s.visualizer, ::visLabel, { choice = null }) { v -> vm.updateSettings { it.copy(visualizer = v) } } }
                    }
                    ChoiceRow(tr("Taśma utworu"), seekLabel(s.seekStyle)) {
                        choice = { ChoiceDialog(tr("Taśma utworu"), SeekStyle.entries, s.seekStyle, ::seekLabel, { choice = null }) { v -> vm.updateSettings { it.copy(seekStyle = v) } } }
                    }
                    ChoiceRow(tr("Tło"), bgLabel(s.npBackground)) {
                        choice = { ChoiceDialog(tr("Tło"), NpBackground.entries, s.npBackground, ::bgLabel, { choice = null }) { v -> vm.updateSettings { it.copy(npBackground = v) } } }
                    }
                    ChoiceRow(tr("Kształt okładki"), shapeLabel(s.coverShape)) {
                        choice = { ChoiceDialog(tr("Kształt okładki"), CoverShape.entries, s.coverShape, ::shapeLabel, { choice = null }) { v -> vm.updateSettings { it.copy(coverShape = v) } } }
                    }
                    SwitchRow(tr("Informacje techniczne pliku"), tr("Format, częstotliwość, bitrate pod tytułem"), s.showTechInfo) { v -> vm.updateSettings { it.copy(showTechInfo = v) } }
                    SwitchRow(tr("Gest przesunięcia okładki"), tr("Przesuń okładkę w bok, aby zmienić utwór"), s.coverGestures) { v -> vm.updateSettings { it.copy(coverGestures = v) } }
                    SwitchRow(tr("Nie wygaszaj ekranu"), tr("Podczas wyświetlania ekranu odtwarzania"), s.keepScreenOn) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }
                    SwitchRow(tr("Tekst wyśrodkowany"), null, s.lyricsCenter) { v -> vm.updateSettings { it.copy(lyricsCenter = v) } }
                    SwitchRow(tr("Pokazuj tekst domyślnie"), null, s.lyricsByDefault) { v -> vm.updateSettings { it.copy(lyricsByDefault = v) } }
                    SwitchRow(tr("Szukaj tekstów w internecie (LRCLIB)"), tr("Gdy plik nie ma tekstu, OpenAMP zapyta bazę LRCLIB i zapamięta wynik"), s.lyricsOnline) { v ->
                        vm.updateSettings { it.copy(lyricsOnline = v) }
                    }
                    SliderRow(tr("Rozmiar tekstu"), "${s.lyricsSize} sp", s.lyricsSize.toFloat(), 14f..34f, 9) { v -> vm.updateSettings { it.copy(lyricsSize = v.roundToInt()) } }
                }
            }

            item {
                Section(tr("Odtwarzanie")) {
                    SwitchRow(tr("Płynne przejście głośności"), null, s.fade) { v -> vm.updateSettings { it.copy(fade = v) } }
                    SwitchRow(tr("Pomijanie ciszy"), null, s.skipSilence) { v -> vm.updateSettings { it.copy(skipSilence = v) } }
                    SwitchRow(tr("Wstrzymaj po odłączeniu słuchawek"), null, s.pauseOnDisconnect) { v -> vm.updateSettings { it.copy(pauseOnDisconnect = v) } }
                    SwitchRow(tr("Wznawiaj po podłączeniu słuchawek"), null, s.resumeOnHeadset) { v -> vm.updateSettings { it.copy(resumeOnHeadset = v) } }
                    SwitchRow(tr("Pamiętaj kolejkę i pozycję"), null, s.rememberQueue) { v -> vm.updateSettings { it.copy(rememberQueue = v) } }
                    SliderRow(tr("Licz odtworzenie po"), tr("{0}% utworu", s.countPlayPercent), s.countPlayPercent.toFloat(), 10f..100f, 8) { v ->
                        vm.updateSettings { it.copy(countPlayPercent = (v / 10).roundToInt() * 10) }
                    }
                    OutlinedButton(onClick = { vm.nowPlayingOpen = false; vm.open(Route.Equalizer) }, modifier = Modifier.padding(top = 6.dp)) { Text(tr("Korektor")) }
                }
            }

            item {
                Section(tr("Biblioteka")) {
                    SliderRow(tr("Minimalna długość utworu"), tr("{0} s", s.minDurationSec), s.minDurationSec.toFloat(), 0f..120f, 11, tr("Krótsze pliki (dzwonki, nagrania) są pomijane")) { v ->
                        vm.updateSettings { it.copy(minDurationSec = (v / 10).roundToInt() * 10) }
                    }
                    var excluded by remember(s.excludedFolders) { mutableStateOf(s.excludedFolders) }
                    Text(tr("Pomijane foldery"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                    Text(tr("Ścieżki zawierające ten tekst nie trafią do biblioteki, oddzielone przecinkami"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        excluded, { excluded = it }, singleLine = true, placeholder = { Text("WhatsApp, Recordings") },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        trailingIcon = {
                            if (excluded != s.excludedFolders) IconButton(onClick = { vm.updateSettings { it.copy(excludedFolders = excluded) } }) { Icon(Icons.Rounded.Check, tr("Zapisz")) }
                        },
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.rescan() }) { Text(tr("Odśwież bibliotekę")) }
                        OutlinedButton(onClick = { clearConfirm = true }) { Text(tr("Wyczyść statystyki odtworzeń")) }
                    }
                }
            }

            item { About() }
        }
    }

    if (clearConfirm) AlertDialog(
        onDismissRequest = { clearConfirm = false },
        title = { Text(tr("Wyczyść statystyki odtworzeń")) },
        confirmButton = { TextButton(onClick = { Graph.userData.clearStats(); vm.message(tr("Statystyki wyczyszczone")); clearConfirm = false }) { Text(tr("Usuń")) } },
        dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text(tr("Anuluj")) } },
    )
}

fun languageLabel(l: AppLanguage) = when (l) { AppLanguage.SYSTEM -> tr("Systemowy"); AppLanguage.PL -> "Polski"; AppLanguage.EN -> "English" }
fun themeLabel(t: ThemeMode) = when (t) { ThemeMode.SYSTEM -> tr("Systemowy"); ThemeMode.DARK -> tr("Ciemny"); ThemeMode.LIGHT -> tr("Jasny"); ThemeMode.BLACK -> tr("Czarny (AMOLED)") }
fun visLabel(v: VisualizerStyle) = when (v) { VisualizerStyle.BARS -> tr("Słupki widma"); VisualizerStyle.WAVE -> tr("Fala"); VisualizerStyle.CIRCLE -> tr("Okrąg"); VisualizerStyle.NONE -> tr("Brak") }
fun bgLabel(v: NpBackground) = when (v) { NpBackground.BLUR -> tr("Rozmyta okładka"); NpBackground.GRADIENT -> tr("Gradient akcentu"); NpBackground.AURORA -> tr("Zorza (reaguje na muzykę)"); NpBackground.BLACK -> tr("Czysta czerń") }
fun shapeLabel(v: CoverShape) = when (v) { CoverShape.ROUNDED -> tr("Zaokrąglona"); CoverShape.SQUARE -> tr("Kwadratowa"); CoverShape.CIRCLE -> tr("Okrągła"); CoverShape.VINYL -> tr("Płyta winylowa") }
fun seekLabel(v: SeekStyle) = when (v) { SeekStyle.WAVEFORM -> tr("Fala dźwiękowa"); SeekStyle.SPECTROGRAM -> tr("Spektrogram"); SeekStyle.SIMPLE -> tr("Prosty pasek") }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Panel {
        Overline(title)
        content()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onChange)
    }
}

@Composable
private fun ChoiceRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SliderRow(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int, subtitle: String? = null, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(current, onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().selectable(o == selected, role = Role.RadioButton) { onPick(o); onDismiss() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(o == selected, null)
                        Spacer(Modifier.width(12.dp))
                        Text(label(o), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Zamknij")) } },
    )
}

@Composable
fun UpdateCard(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val st by vm.update.collectAsState()
    Section(tr("Aktualizacje")) {
        SwitchRow(tr("Sprawdzaj aktualizacje automatycznie"), tr("Raz dziennie w tle i przy starcie; powiadomienie o nowej wersji"), s.autoUpdate) { v ->
            vm.updateSettings { it.copy(autoUpdate = v) }
        }
        Text(tr("Wersja {0}", BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
        if (s.lastUpdateCheck > 0) Text(tr("Ostatnio sprawdzono: {0}", formatDate(s.lastUpdateCheck)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        UpdateStatus(vm, st)
    }
}

@Composable
fun UpdateStatus(vm: MainViewModel, st: UpdateState) {
    when (st) {
        UpdateState.Idle -> OutlinedButton(onClick = { vm.checkUpdate() }) { Icon(Icons.Rounded.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text(tr("Sprawdź teraz")) }
        UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(Modifier.weight(1f)); Spacer(Modifier.width(12.dp)); Text(tr("Sprawdzanie…"))
        }
        UpdateState.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(tr("Masz najnowszą wersję"), modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.checkUpdate() }) { Text(tr("Sprawdź teraz")) }
        }
        is UpdateState.Available -> Column {
            Text(tr("Dostępna wersja {0}", st.info.version), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (st.info.notes.isNotBlank()) Text(st.info.notes.take(800), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
            Button(onClick = { vm.downloadAndInstall() }) { Text(tr("Pobierz i zainstaluj")) }
        }
        is UpdateState.Downloading -> Column {
            Text(tr("Pobieranie… {0}%", (st.progress * 100).roundToInt()))
            LinearProgressIndicator({ st.progress }, Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        is UpdateState.Ready -> Button(onClick = { vm.downloadAndInstall() }) { Text(tr("Zainstaluj")) }
        is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(Modifier.weight(1f)); Spacer(Modifier.width(12.dp)); Text(tr("Instalowanie…"))
        }
        is UpdateState.Failed -> Column {
            Text(tr("Błąd aktualizacji: {0}", st.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { vm.checkUpdate() }, modifier = Modifier.padding(top = 6.dp)) { Text(tr("Sprawdź teraz")) }
        }
    }
}

@Composable
private fun About() {
    val context = LocalContext.current
    Section(tr("O aplikacji")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null,
                Modifier.size(64.dp).background(Color(0xFF1C1030), RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("OpenAMP", style = MaterialTheme.typography.titleLarge)
                Text(tr("Odtwarzacz muzyki o otwartym kodzie źródłowym"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tr("Wersja {0}", BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        TextButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.UPDATE_REPO}"))) }
        }) { Icon(Icons.Rounded.Code, null); Spacer(Modifier.width(8.dp)); Text(tr("Kod źródłowy na GitHubie")) }
    }
}
