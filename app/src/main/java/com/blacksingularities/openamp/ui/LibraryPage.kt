package com.blacksingularities.openamp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.ai.CuratorStatus
import com.blacksingularities.openamp.data.LibraryOps
import com.blacksingularities.openamp.data.LibraryTab
import com.blacksingularities.openamp.data.PlayStat
import com.blacksingularities.openamp.data.SmartList
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.data.TrackSort
import com.blacksingularities.openamp.data.UserData
import com.blacksingularities.openamp.data.ViewMode
import com.blacksingularities.openamp.i18n.plural
import com.blacksingularities.openamp.i18n.tr
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.hypot

val LibraryTab.isTrackList: Boolean
    get() = this in setOf(LibraryTab.TRACKS, LibraryTab.RECENT_ADDED, LibraryTab.MOST_PLAYED, LibraryTab.RECENT_PLAYED, LibraryTab.FAVORITES)

fun LibraryTab.icon(): ImageVector = when (this) {
    LibraryTab.TRACKS -> Icons.Rounded.MusicNote
    LibraryTab.RECENT_ADDED -> Icons.Rounded.Add
    LibraryTab.MOST_PLAYED -> Icons.Rounded.TrendingUp
    LibraryTab.RECENT_PLAYED -> Icons.Rounded.History
    LibraryTab.FAVORITES -> Icons.Rounded.Favorite
    LibraryTab.HOME -> Icons.Rounded.AutoAwesome
    LibraryTab.CATEGORIES -> Icons.Rounded.Category
    LibraryTab.ALBUMS -> Icons.Rounded.Album
    LibraryTab.ARTISTS -> Icons.Rounded.Person
    LibraryTab.GENRES -> Icons.Rounded.Style
    LibraryTab.FOLDERS -> Icons.Rounded.Folder
    LibraryTab.PLAYLISTS -> Icons.AutoMirrored.Rounded.PlaylistPlay
}

/** Gęstość listy: 0 – sam tekst … 3 – duże okładki, 4–5 – siatka (3 i 2 kolumny). */
const val DENSITY_MAX = 5

/**
 * Szczypanie dwoma palcami zmienia skalę (gęstość) listy krok po kroku. Gest jednym palcem
 * zostaje dla przewijania – przechwytujemy tylko ruchy dwóch palców.
 */
fun Modifier.pinchSteps(onStep: (Int) -> Unit): Modifier = composed {
    val live = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    this
        .graphicsLayer { scaleX = live.value; scaleY = live.value }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var start = 0f
                var pinching = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        pinching = true
                        val d = hypot(pressed[0].position.x - pressed[1].position.x, pressed[0].position.y - pressed[1].position.y)
                        if (start == 0f) start = d
                        val ratio = d / start
                        scope.launch { live.snapTo(ratio.coerceIn(0.85f, 1.15f)) }
                        if (ratio > 1.22f) { step(+1); start = d; scope.launch { live.snapTo(0.9f); live.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f)) } }
                        if (ratio < 0.82f) { step(-1); start = d; scope.launch { live.snapTo(1.1f); live.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f)) } }
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (pinching) scope.launch { live.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f)) }
            }
        }
}

// ---------- Sekcje list (przyklejone nagłówki) ----------

private fun initial(s: String): String {
    val c = s.trim().firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return if (c.isDigit()) "#" else c.uppercaseChar().toString()
}

private fun dateBucket(ms: Long, now: Long = System.currentTimeMillis()): String {
    if (ms <= 0) return tr("Nigdy")
    val day = 86_400_000L
    val age = now - ms
    return when {
        age < day -> tr("Dzisiaj")
        age < 7 * day -> tr("Ten tydzień")
        age < 31 * day -> tr("Ten miesiąc")
        else -> {
            val c = Calendar.getInstance().apply { timeInMillis = ms }
            val y = c.get(Calendar.YEAR)
            if (y == Calendar.getInstance().get(Calendar.YEAR)) java.text.SimpleDateFormat("LLLL", java.util.Locale.getDefault()).format(java.util.Date(ms)).replaceFirstChar { it.uppercase() }
            else y.toString()
        }
    }
}

/** Klucz sekcji dla danego sortowania; null – lista bez sekcji. */
fun sectionsFor(sort: TrackSort?, stats: Map<Long, PlayStat>): ((Track) -> String)? = when (sort) {
    TrackSort.TITLE -> { t -> initial(t.title) }
    TrackSort.ARTIST -> { t -> t.artist }
    TrackSort.ALBUM -> { t -> t.album }
    TrackSort.YEAR -> { t -> if (t.year > 0) t.year.toString() else tr("Bez roku") }
    TrackSort.DURATION -> { t ->
        when (t.durationMs / 60_000) { 0L, 1L -> tr("Do 2 min"); 2L -> "2–3 min"; 3L -> "3–4 min"; 4L -> "4–5 min"; in 5L..6L -> "5–7 min"; else -> tr("Ponad 7 min") }
    }
    TrackSort.DATE_ADDED -> { t -> dateBucket(t.dateAdded) }
    TrackSort.LAST_PLAYED -> { t -> dateBucket(stats[t.id]?.lastPlayed ?: 0) }
    TrackSort.PLAY_COUNT -> { t ->
        when (val c = stats[t.id]?.count ?: 0) { 0 -> tr("Niegrane"); in 1..4 -> "1–4×"; in 5..19 -> "5–19×"; in 20..49 -> "20–49×"; else -> if (c >= 50) "50×+" else "" }
    }
    TrackSort.SIZE -> { t -> val mb = t.size / (1L shl 20); when { mb < 5 -> "< 5 MB"; mb < 10 -> "5–10 MB"; mb < 30 -> "10–30 MB"; else -> "30 MB+" } }
    TrackSort.RANDOM, null -> null
}

/** Wiersz listy z sekcjami: nagłówek albo utwór (indeks w liście utworów). */
private sealed interface LRow {
    data class Header(val label: String, val count: Int) : LRow
    data class Item(val index: Int) : LRow
}

/** Wiersze listy; [keep] (wyszukiwanie) ukrywa niepasujące utwory, zachowując ich indeksy w pełnej liście. */
private fun buildRows(list: List<Track>, key: ((Track) -> String)?, keep: Set<Long>? = null): List<LRow> {
    val idx = if (keep == null) list.indices.toList() else list.indices.filter { list[it].id in keep }
    if (key == null || idx.size < 12) return idx.map { LRow.Item(it) }
    val out = ArrayList<LRow>(idx.size + 32)
    var last: String? = null
    var headerAt = -1
    var count = 0
    idx.forEach { i ->
        val t = list[i]
        val k = key(t)
        if (k != last) {
            if (headerAt >= 0) out[headerAt] = LRow.Header(last ?: "", count)
            headerAt = out.size; out += LRow.Header(k, 0); last = k; count = 0
        }
        out += LRow.Item(i); count++
    }
    if (headerAt >= 0) out[headerAt] = LRow.Header(last ?: "", count)
    return out
}

@Composable
private fun StickyLabel(label: String, count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f)).padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.reactive(tr("Przejdź do sekcji"), onClick = onClick).clip(Shapes.Pill).background(Flat.Block).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), color = Flat.Faded, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Okno szybkiego skoku: wszystkie sekcje listy jako kafelki. */
@Composable
private fun SectionJump(sections: List<Pair<String, Int>>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(Shapes.XLarge).background(Compact.Menu).padding(14.dp)) {
            Text(tr("Przejdź do"), color = Flat.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
            FlowRow(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sections.forEach { (label, row) ->
                    Text(
                        label, color = Flat.Fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.reactive(null) { onPick(row) }.clip(Shapes.Medium).background(Flat.Block)
                            .padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(vm: MainViewModel, bottomPadding: Dp) {
    val s by vm.settings.collectAsState()
    val all by vm.tracks.collectAsState()
    val data by vm.userData.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val tab = s.tab
    val pad = PaddingValues(bottom = bottomPadding + 12.dp)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (vm.libraryRoot) {
            CategoryList(vm, all, data, pad)
            return@Column
        }
        AnimatedVisibility(scanning) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 2.dp)) }
        val toRoot: @Composable () -> Unit = { LibraryButton { vm.closeSearch(); vm.libraryRoot = true } }

        if (tab.isTrackList) {
            val sortable = tab == LibraryTab.TRACKS || tab == LibraryTab.FAVORITES
            val list = remember(all, tab, s.trackSort, s.trackSortDesc, data) {
                val base = when (tab) {
                    LibraryTab.RECENT_ADDED -> LibraryOps.smart(all, SmartList.RECENTLY_ADDED, data)
                    LibraryTab.MOST_PLAYED -> LibraryOps.smart(all, SmartList.MOST_PLAYED, data)
                    LibraryTab.RECENT_PLAYED -> LibraryOps.smart(all, SmartList.RECENTLY_PLAYED, data)
                    LibraryTab.FAVORITES -> LibraryOps.smart(all, SmartList.FAVORITES, data)
                    else -> all
                }
                if (sortable) LibraryOps.sortTracks(base, s.trackSort, s.trackSortDesc, data.stats) else base
            }
            // Listy z własną kolejnością dostają sekcje dat zamiast sortowania.
            val sections = when (tab) {
                LibraryTab.RECENT_ADDED -> sectionsFor(TrackSort.DATE_ADDED, data.stats)
                LibraryTab.RECENT_PLAYED -> sectionsFor(TrackSort.LAST_PLAYED, data.stats)
                LibraryTab.MOST_PLAYED -> null
                else -> sectionsFor(s.trackSort, data.stats)
            }
            TrackListPage(
                vm, tab.label(), tab.icon(), list, s.trackDensity, pad,
                sort = if (sortable) ListSort.TRACKS else ListSort.NONE,
                sections = if (s.stickyHeaders) sections else null,
                headerTrailing = toRoot,
            )
        } else {
            val (sort, view) = when (tab) {
                LibraryTab.HOME -> ListSort.NONE to ListView.HOME
                LibraryTab.PLAYLISTS -> ListSort.NONE to ListView.NONE
                LibraryTab.CATEGORIES -> ListSort.NONE to ListView.GROUPS
                else -> ListSort.GROUPS to ListView.GROUPS
            }
            CategoryHeader(tab.label(), tab.icon(), null) { ListOptions(vm, sort, view); Spacer(Modifier.width(6.dp)); toRoot() }
            GroupTab(vm, tab, all, pad)
        }
    }
}

/** Przycisk powrotu do kategorii biblioteki – w prawym górnym rogu listy. */
@Composable
fun LibraryButton(onClick: () -> Unit) {
    PillButton(tr("Biblioteka"), onClick, icon = Icons.Rounded.NorthEast)
}

@Composable
fun SearchField(vm: MainViewModel, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier.clip(Shapes.Pill).background(Flat.Block).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Flat.Dim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
            if (vm.query.isEmpty()) Text(tr("Szukaj w bibliotece"), color = Flat.Faded, fontSize = 14.sp)
            BasicTextField(
                vm.query, { vm.query = it }, singleLine = true,
                textStyle = TextStyle(color = Flat.Fg, fontSize = 14.sp), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        RoundButton(Icons.Rounded.Close, tr("Zamknij"), { vm.closeSearch() }, background = Color.Transparent, size = 36.dp)
    }
}

/** Nagłówek listy: niewielka ikona, tytuł i licznik – bez zajmowania pół ekranu. */
@Composable
fun CategoryHeader(title: String, icon: ImageVector, count: String?, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Flat.Fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
            if (count != null) Text(count, color = Flat.Dim, fontSize = 11.sp, lineHeight = 13.sp)
        }
        trailing()
    }
}

/** Propozycja analizy AI po zmianie biblioteki o co najmniej 5 utworów. */
@Composable
fun AiProposal(vm: MainViewModel, modifier: Modifier = Modifier) {
    val all by vm.tracks.collectAsState()
    val s by vm.settings.collectAsState()
    val cur by Graph.curator.state.collectAsState()
    val status by Graph.curator.status.collectAsState()
    val running = status is CuratorStatus.Running
    val show = remember(all, cur, status, s.aiPrompt) { s.aiPrompt && Graph.curator.shouldPropose(all) }
    AnimatedVisibility(show || running, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val change = remember(all, cur) { Graph.curator.changeCount(all) }
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp).fillMaxWidth().clip(Shapes.Large)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (running) (status as CuratorStatus.Running).stage
                    else if (cur.analyzedAt == 0L) tr("Uporządkować bibliotekę z AI?")
                    else tr("Biblioteka zmieniła się o {0} utworów", change),
                    color = Flat.Fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
            }
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            else {
                Text(tr("Nowe kategorie i miksy dopasowane do Twojej muzyki."), color = Flat.Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 26.dp, top = 2.dp))
                Row(Modifier.padding(start = 26.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillButton(tr("Analizuj"), { vm.runAnalysis() }, icon = Icons.Rounded.AutoAwesome, active = true)
                    PillButton(tr("Później"), { Graph.curator.dismiss(all) })
                }
            }
        }
    }
}

/** Lista kategorii („Biblioteka”): płaskie wiersze z ikoną i liczbą. */
@Composable
private fun CategoryList(vm: MainViewModel, all: List<Track>, data: UserData, pad: PaddingValues) {
    val cur by Graph.curator.state.collectAsState()
    val ps by vm.player.state.collectAsState()
    val counts = remember(all, data, cur) {
        mapOf(
            LibraryTab.TRACKS to all.size,
            LibraryTab.RECENT_ADDED to minOf(100, all.size),
            LibraryTab.MOST_PLAYED to LibraryOps.smart(all, SmartList.MOST_PLAYED, data).size,
            LibraryTab.RECENT_PLAYED to LibraryOps.smart(all, SmartList.RECENTLY_PLAYED, data).size,
            LibraryTab.FAVORITES to all.count { it.id in data.favorites },
            LibraryTab.CATEGORIES to cur.categories.size,
            LibraryTab.ALBUMS to all.map { it.albumId }.distinct().size,
            LibraryTab.ARTISTS to all.map { it.artist.lowercase() }.distinct().size,
            LibraryTab.GENRES to all.map { it.genre }.distinct().size,
            LibraryTab.FOLDERS to all.map { it.folder }.distinct().size,
            LibraryTab.PLAYLISTS to data.playlists.size,
        )
    }
    LazyColumn(contentPadding = pad) {
        item {
            Text(tr("Biblioteka"), color = Flat.Fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp))
        }
        item { AiProposal(vm) }
        item {
            CategoryRow(Icons.AutoMirrored.Rounded.QueueMusic, tr("Bieżąca lista odtwarzania"), ps.queue.size, highlight = true) { vm.goTab(Route.Queue) }
        }
        items(LibraryTab.entries) { t ->
            CategoryRow(t.icon(), t.label(), counts[t]) {
                vm.updateSettings { it.copy(tab = t) }
                vm.libraryRoot = false
            }
        }
    }
}

@Composable
private fun CategoryRow(icon: ImageVector, label: String, count: Int?, highlight: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().reactive(null, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (highlight) MaterialTheme.colorScheme.primary else Flat.Block),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (highlight) MaterialTheme.colorScheme.onPrimary else Flat.Fg, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(label, color = Flat.Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (count != null) Text(count.toString(), color = Flat.Dim, fontSize = 12.sp)
    }
}

/**
 * Strona listy utworów: nagłówek kategorii, rząd akcji (mieszaj, odtwórz, wybierz, opcje),
 * lista o zmiennej gęstości (szczypanie) z przyklejonymi nagłówkami sekcji i tryb zaznaczania.
 */
@Composable
fun TrackListPage(
    vm: MainViewModel,
    title: String,
    icon: ImageVector,
    list: List<Track>,
    density: Int,
    pad: PaddingValues,
    onPlay: ((Int) -> Unit)? = null,
    currentIndex: Int? = null,
    sort: ListSort = ListSort.NONE,
    sections: ((Track) -> String)? = null,
    headerTrailing: @Composable () -> Unit = {},
) {
    val ps by vm.player.state.collectAsState()
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var menuFor by remember { mutableStateOf<Pair<Int, Track>?>(null) }
    var addTo by remember { mutableStateOf<List<Track>?>(null) }
    var jump by remember { mutableStateOf(false) }
    addTo?.let { AddToPlaylistDialog(vm, it) { addTo = null; selecting = false; selected.clear() } }
    val curDensity by rememberUpdatedState(density)
    val chosen = { list.filter { it.id in selected } }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        CategoryHeader(title, icon, tracksSummary(list), headerTrailing)
        LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!selecting) {
                item { PillButton(tr("Wymieszaj"), { vm.player.play(list, shuffle = true) }, icon = Icons.Rounded.Shuffle, compact = true) }
                item { PillButton(tr("Odtwórz"), { vm.player.play(list) }, icon = Icons.Rounded.PlayArrow, compact = true) }
                item { PillButton(tr("Wybierz"), { selecting = true }, compact = true) }
                item { ListOptions(vm, sort, ListView.DENSITY) }
            } else {
                item { PillButton(tr("{0} zaznaczone", selected.size), { selecting = false; selected.clear() }, icon = Icons.Rounded.Close, active = true, compact = true) }
                item { PillButton(null, { if (selected.size == list.size) selected.clear() else { selected.clear(); selected.addAll(list.map { it.id }) } }, icon = Icons.Rounded.SelectAll, label = tr("Zaznacz wszystko"), compact = true) }
                item { PillButton(null, { vm.player.play(chosen()); selecting = false; selected.clear() }, icon = Icons.Rounded.PlayArrow, label = tr("Odtwórz"), compact = true) }
                item { PillButton(null, { vm.player.playNext(chosen()); vm.message(tr("Zagra jako następny")); selecting = false; selected.clear() }, icon = Icons.Rounded.SkipNext, label = tr("Odtwórz następny"), compact = true) }
                item { PillButton(null, { vm.player.enqueue(chosen()); vm.message(tr("Dodano do kolejki")); selecting = false; selected.clear() }, icon = Icons.AutoMirrored.Rounded.QueueMusic, label = tr("Dodaj do kolejki"), compact = true) }
                item { PillButton(null, { addTo = chosen() }, icon = Icons.AutoMirrored.Rounded.PlaylistAdd, label = tr("Dodaj do playlisty"), compact = true) }
                item { PillButton(null, { chosen().forEach { t -> if (t.id !in Graph.userData.value.favorites) Graph.userData.toggleFavorite(t.id) }; selecting = false; selected.clear() }, icon = Icons.Rounded.Favorite, label = tr("Dodaj do ulubionych"), compact = true) }
                item { PillButton(null, { vm.deleteRequest = chosen(); selecting = false; selected.clear() }, icon = Icons.Rounded.Delete, label = tr("Usuń z urządzenia"), compact = true) }
            }
        }
        // Wyszukiwanie filtruje tę samą listę w miejscu – odtwarzanie dalej idzie po pełnej liście.
        if (vm.searchOpen) SearchField(vm, Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp))
        Gap(4.dp)
        val keep = remember(list, vm.query) { if (vm.query.isBlank()) null else LibraryOps.search(list, vm.query).mapTo(HashSet()) { it.id } }
        if (list.isEmpty() || keep?.isEmpty() == true) { Empty(if (keep != null) tr("Brak wyników") else tr("Brak utworów")); return@Column }

        val step: (Int) -> Unit = { d ->
            val n = (curDensity + d).coerceIn(0, DENSITY_MAX)
            if (n != curDensity) vm.updateSettings { it.copy(trackDensity = n) }
        }
        val onTap: (Int, Track) -> Unit = { i, t ->
            if (selecting) { if (t.id in selected) selected.remove(t.id) else selected.add(t.id) } else if (onPlay != null) onPlay(i) else vm.player.play(list, i)
        }
        val onLong: (Int, Track) -> Unit = { i, t ->
            if (selecting) { if (t.id in selected) selected.remove(t.id) else selected.add(t.id) } else menuFor = i to t
        }
        val rows = remember(list, sections, keep) { buildRows(list, sections, keep) }
        val headers = remember(rows) { rows.mapIndexedNotNull { r, row -> (row as? LRow.Header)?.let { it.label to r } } }

        if (density >= 4) {
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            if (jump) SectionJump(headers, { r -> jump = false; scope.launch { gridState.animateScrollToItem(r) } }, { jump = false })
            LazyVerticalGrid(
                GridCells.Fixed(if (density == 4) 3 else 2),
                Modifier.fillMaxSize().pinchSteps(step),
                state = gridState,
                contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = pad.calculateBottomPadding()),
            ) {
                rows.forEachIndexed { r, row ->
                    when (row) {
                        is LRow.Header -> item(key = "h_$r", span = { GridItemSpan(maxLineSpan) }) { StickyLabel(row.label, row.count) { jump = true } }
                        is LRow.Item -> {
                            val i = row.index
                            val t = list[i]
                            item(key = "${t.id}_$i") {
                                Box(Modifier.animateItem()) {
                                    Column(Modifier.padding(4.dp).combinedClickable(onClick = { onTap(i, t) }, onLongClick = { onLong(i, t) })) {
                                        Box {
                                            Artwork(t, Modifier.fillMaxWidth().aspectRatio(1f), size = 360, shape = Shapes.Medium)
                                            if (selecting) SelectMark(t.id in selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
                                            if (ps.trackId == t.id) Box(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(6.dp)) { PlayingIndicator(ps.isPlaying, color = Flat.Fg) }
                                        }
                                        Text(t.title, color = if (ps.trackId == t.id) MaterialTheme.colorScheme.primary else Flat.Fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                        Text(t.artist, color = Flat.Dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (menuFor?.first == i) TrackMenu(vm, t, { menuFor = null })
                                }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Gap(8.dp) }
            }
        } else {
            val listState = rememberLazyListState()
            if (jump) SectionJump(headers, { r -> jump = false; scope.launch { listState.animateScrollToItem(r) } }, { jump = false })
            LaunchedEffect(currentIndex != null) {
                if (currentIndex != null && currentIndex > 2) listState.scrollToItem(currentIndex - 2)
            }
            LazyColumn(Modifier.fillMaxSize().pinchSteps(step), state = listState, contentPadding = pad) {
                rows.forEachIndexed { r, row ->
                    when (row) {
                        is LRow.Header -> stickyHeader(key = "h_$r") { StickyLabel(row.label, row.count) { jump = true } }
                        is LRow.Item -> {
                            val i = row.index
                            val t = list[i]
                            item(key = "${t.id}_$i") {
                                Box(Modifier.animateItem()) {
                                    val isCurrent = if (currentIndex != null) i == currentIndex else ps.trackId == t.id
                                    DenseRow(t, density, isCurrent, ps.isPlaying, selecting, t.id in selected, { onTap(i, t) }, { onLong(i, t) })
                                    if (menuFor?.first == i) TrackMenu(vm, t, { menuFor = null })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectMark(on: Boolean, modifier: Modifier = Modifier) {
    Icon(
        if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
        tint = if (on) MaterialTheme.colorScheme.primary else Flat.Fg.copy(alpha = 0.6f),
        modifier = modifier.size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
    )
}

/** Wiersz utworu w jednej z czterech gęstości (jak w klasycznych odtwarzaczach). */
@Composable
private fun DenseRow(t: Track, density: Int, current: Boolean, playing: Boolean, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val titleColor = if (current) accent else Flat.Fg
    val meta = "${formatTime(t.durationMs)} · ${t.format.lowercase()}"
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .background(if (selected) accent.copy(alpha = 0.16f) else if (current) accent.copy(alpha = 0.07f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = when (density) { 0 -> 7.dp; 1 -> 4.dp; else -> 5.dp }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) { SelectMark(selected); Spacer(Modifier.width(10.dp)) }
        if (density == 0) {
            if (current) { PlayingIndicator(playing); Spacer(Modifier.width(8.dp)) }
            Text(t.title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(t.artist, color = Flat.Faded, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.6f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(formatTime(t.durationMs), color = Flat.Faded, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            return@Row
        }
        val art = when (density) { 1 -> 40.dp; 2 -> 58.dp; else -> 88.dp }
        Box {
            Artwork(t, Modifier.size(art), size = if (density >= 3) 360 else 200, shape = RoundedCornerShape(if (density == 1) 9.dp else 13.dp))
            if (current) Box(Modifier.size(art).clip(Shapes.Medium).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                PlayingIndicator(playing, color = Flat.Fg)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = titleColor, fontSize = if (density >= 2) 15.sp else 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            Text(if (density == 1) t.artist else "${t.artist} · ${t.album}", color = Flat.Fg.copy(alpha = 0.78f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
            if (density >= 2) Text(meta, color = Flat.Faded, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        }
        if (density == 1) Text(formatTime(t.durationMs), color = Flat.Faded, fontSize = 11.sp)
    }
}

/** Zakładki grupujące (albumy, wykonawcy, …) – także ze skalą gestem szczypania. */
@Composable
private fun GroupTab(vm: MainViewModel, tab: LibraryTab, tracks: List<Track>, pad: PaddingValues) {
    val s by vm.settings.collectAsState()
    val data by vm.userData.collectAsState()
    val views = listOf(ViewMode.COMPACT, ViewMode.LIST, ViewMode.GRID, ViewMode.GRID_LARGE)
    val cur by rememberUpdatedState(s.groupView)
    val step: (Int) -> Unit = { d ->
        val i = views.indexOf(cur).coerceAtLeast(0)
        val n = (i + d).coerceIn(0, views.lastIndex)
        if (n != i) vm.updateSettings { it.copy(groupView = views[n]) }
    }
    Box(Modifier.fillMaxSize().pinchSteps(step)) {
        when (tab) {
            LibraryTab.ALBUMS -> {
                val albums = remember(tracks, s.groupSort, s.groupSortDesc, data.stats) {
                    LibraryOps.sortGroups(LibraryOps.albums(tracks), s.groupSort, s.groupSortDesc, data.stats, { it.title }, { it.tracks })
                }
                GroupCollection(
                    vm, albums, s.groupView, pad, title = { it.title }, subtitle = { a -> a.artist + if (a.year > 0) " • ${a.year}" else "" },
                    cover = { it.cover }, tracksOf = { it.tracks }, open = { vm.open(Route.Detail(DetailKind.ALBUM, it.id.toString())) },
                )
            }
            LibraryTab.PLAYLISTS -> PlaylistsTab(vm, tracks, pad)
            LibraryTab.HOME -> HomeScreen(vm, pad)
            LibraryTab.CATEGORIES -> CategoriesTab(vm, tracks, pad)
            else -> {
                val kind = when (tab) { LibraryTab.ARTISTS -> DetailKind.ARTIST; LibraryTab.GENRES -> DetailKind.GENRE; else -> DetailKind.FOLDER }
                val groups = remember(tracks, tab, s.groupSort, s.groupSortDesc, data.stats) {
                    val g = when (tab) {
                        LibraryTab.ARTISTS -> LibraryOps.artists(tracks)
                        LibraryTab.GENRES -> LibraryOps.genres(tracks)
                        else -> LibraryOps.folders(tracks)
                    }
                    LibraryOps.sortGroups(g, s.groupSort, s.groupSortDesc, data.stats, { it.name }, { it.tracks })
                }
                GroupCollection(
                    vm, groups, s.groupView, pad, title = { it.name },
                    subtitle = { g -> plural(g.tracks.size, "{0} utwór", "{0} utwory", "{0} utworów") },
                    cover = { it.cover }, tracksOf = { it.tracks },
                    circle = kind == DetailKind.ARTIST, folderIcon = kind == DetailKind.FOLDER,
                    open = { vm.open(Route.Detail(kind, it.key)) },
                )
            }
        }
    }
}

/** Kategorie ułożone przez analizę AI (lub lokalną). */
@Composable
private fun CategoriesTab(vm: MainViewModel, tracks: List<Track>, pad: PaddingValues) {
    val s by vm.settings.collectAsState()
    val cur by Graph.curator.state.collectAsState()
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val cats = remember(cur, byId) { cur.categories.map { c -> c to c.trackIds.mapNotNull { byId[it] } }.filter { it.second.isNotEmpty() } }
    Column {
        AiProposal(vm)
        if (cats.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Category, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Gap(10.dp)
                Text(tr("Brak kategorii"), color = Flat.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(tr("Analiza podzieli utwory na nastroje, style, epoki i okazje."), color = Flat.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                PillButton(tr("Analizuj"), { vm.runAnalysis() }, icon = Icons.Rounded.AutoAwesome, active = true)
            }
            return@Column
        }
        GroupCollection(
            vm, cats, s.groupView, pad, title = { it.first.name },
            subtitle = { (c, l) -> listOf(c.description, plural(l.size, "{0} utwór", "{0} utwory", "{0} utworów")).filter { it.isNotBlank() }.joinToString(" • ") },
            cover = { it.second.firstOrNull() }, tracksOf = { it.second },
            open = { vm.open(Route.Detail(DetailKind.CATEGORY, it.first.id)) },
        )
    }
}

/**
 * Bieżąca lista odtwarzania w faktycznej kolejności grania (przy losowaniu – wylosowanej),
 * przewinięta do grającego utworu. Dotknięcie przeskakuje do utworu bez zmiany kolejki.
 * Przycisk w prawym górnym rogu (w tym samym miejscu co na ekranie odtwarzania) prowadzi dalej – do biblioteki.
 */
@Composable
fun QueuePage(vm: MainViewModel, bottomPadding: Dp) {
    val ps by vm.player.state.collectAsState()
    val all by vm.tracks.collectAsState()
    val s by vm.settings.collectAsState()
    val byId = remember(all) { all.associateBy { it.id } }
    val order = ps.order.ifEmpty { ps.queue.indices.toList() }
    val pairs = remember(order, ps.queue, byId) { order.mapNotNull { qi -> ps.queue.getOrNull(qi)?.let { byId[it] }?.let { qi to it } } }
    val list = pairs.map { it.second }
    val current = pairs.indexOfFirst { it.first == ps.index }.takeIf { it >= 0 }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TrackListPage(
            vm, tr("Bieżąca lista odtwarzania"), Icons.AutoMirrored.Rounded.QueueMusic, list, s.trackDensity,
            PaddingValues(bottom = bottomPadding + 12.dp),
            onPlay = { i -> pairs.getOrNull(i)?.let { vm.player.skipTo(it.first) } },
            currentIndex = current,
            headerTrailing = {
                if (ps.shuffle) { RoundButton(Icons.Rounded.Shuffle, tr("Losowo"), { vm.player.toggleShuffle() }, active = true, size = 38.dp, iconSize = 18.dp); Spacer(Modifier.width(6.dp)) }
                LibraryButton { vm.goTab(Route.Library); vm.libraryRoot = true }
            },
        )
    }
}
