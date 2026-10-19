package com.blacksingularities.openamp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.LibraryOps
import com.blacksingularities.openamp.data.LibraryTab
import com.blacksingularities.openamp.data.SmartList
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.data.ViewMode
import com.blacksingularities.openamp.i18n.plural
import com.blacksingularities.openamp.i18n.tr
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
fun Modifier.pinchSteps(onStep: (Int) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var start = 0f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val d = hypot(pressed[0].position.x - pressed[1].position.x, pressed[0].position.y - pressed[1].position.y)
                if (start == 0f) start = d
                val ratio = d / start
                if (ratio > 1.3f) { onStep(+1); start = d }
                if (ratio < 0.77f) { onStep(-1); start = d }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
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
    val tracks = remember(all, vm.query) { LibraryOps.search(all, vm.query) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (vm.libraryRoot && !vm.searchOpen) {
            CategoryList(vm, all, data, pad)
            return@Column
        }
        // Pasek: powrót do kategorii albo wyszukiwanie
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.searchOpen) {
                SearchField(vm, Modifier.weight(1f))
            } else {
                PillButton(tr("Biblioteka"), { vm.libraryRoot = true }, icon = Icons.Rounded.NorthEast, background = Color.Transparent)
                Spacer(Modifier.weight(1f))
            }
        }
        AnimatedVisibility(scanning) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp)) }

        if (tab.isTrackList || vm.searchOpen) {
            val list = remember(tracks, tab, s.trackSort, s.trackSortDesc, data, vm.searchOpen) {
                val base = when {
                    vm.searchOpen -> tracks
                    tab == LibraryTab.RECENT_ADDED -> LibraryOps.smart(tracks, SmartList.RECENTLY_ADDED, data)
                    tab == LibraryTab.MOST_PLAYED -> LibraryOps.smart(tracks, SmartList.MOST_PLAYED, data)
                    tab == LibraryTab.RECENT_PLAYED -> LibraryOps.smart(tracks, SmartList.RECENTLY_PLAYED, data)
                    tab == LibraryTab.FAVORITES -> LibraryOps.smart(tracks, SmartList.FAVORITES, data)
                    else -> tracks
                }
                // Listy „inteligentne” mają swoją kolejność; własne sortowanie – w pozostałych.
                if (tab == LibraryTab.TRACKS || tab == LibraryTab.FAVORITES || vm.searchOpen) LibraryOps.sortTracks(base, s.trackSort, s.trackSortDesc, data.stats) else base
            }
            TrackListPage(vm, if (vm.searchOpen) tr("Szukaj") else tab.label(), if (vm.searchOpen) Icons.Rounded.Search else tab.icon(), list, s.trackDensity, pad)
        } else {
            CategoryHeader(tab.label(), tab.icon(), null) { SortMenu(vm) }
            GroupTab(vm, tab, tracks, pad)
        }
    }
}

@Composable
private fun SearchField(vm: MainViewModel, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Flat.Block).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Flat.Dim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (vm.query.isEmpty()) Text(tr("Szukaj w bibliotece"), color = Flat.Faded, fontSize = 15.sp)
            BasicTextField(
                vm.query, { vm.query = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RoundButton(Icons.Rounded.Close, tr("Zamknij"), { vm.searchOpen = false; vm.query = "" }, background = Color.Transparent, size = 40.dp)
    }
}

@Composable
private fun CategoryHeader(title: String, icon: ImageVector, count: String?, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (count != null) Text(count, color = Flat.Dim, fontSize = 12.sp)
        }
        trailing()
    }
}

/** Lista kategorii („Biblioteka”): płaskie wiersze z ikoną i liczbą. */
@Composable
private fun CategoryList(vm: MainViewModel, all: List<Track>, data: com.blacksingularities.openamp.data.UserData, pad: PaddingValues) {
    val counts = remember(all, data) {
        mapOf(
            LibraryTab.TRACKS to all.size,
            LibraryTab.RECENT_ADDED to minOf(100, all.size),
            LibraryTab.MOST_PLAYED to LibraryOps.smart(all, SmartList.MOST_PLAYED, data).size,
            LibraryTab.RECENT_PLAYED to LibraryOps.smart(all, SmartList.RECENTLY_PLAYED, data).size,
            LibraryTab.FAVORITES to all.count { it.id in data.favorites },
            LibraryTab.ALBUMS to all.map { it.albumId }.distinct().size,
            LibraryTab.ARTISTS to all.map { it.artist.lowercase() }.distinct().size,
            LibraryTab.GENRES to all.map { it.genre }.distinct().size,
            LibraryTab.FOLDERS to all.map { it.folder }.distinct().size,
            LibraryTab.PLAYLISTS to data.playlists.size,
        )
    }
    LazyColumn(contentPadding = pad) {
        item {
            Text(tr("Biblioteka"), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
        }
        items(LibraryTab.entries) { t ->
            Row(
                Modifier.fillMaxWidth().reactive(null) {
                    vm.updateSettings { it.copy(tab = t) }
                    vm.libraryRoot = false
                }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Flat.Block), contentAlignment = Alignment.Center) {
                    Icon(t.icon(), null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(t.label(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                counts[t]?.let { Text(it.toString(), color = Flat.Dim, fontSize = 13.sp) }
            }
        }
    }
}

/**
 * Strona listy utworów: nagłówek kategorii, rząd akcji (mieszaj, odtwórz, szukaj, wybierz, więcej),
 * lista o zmiennej gęstości (szczypanie) i tryb zaznaczania wielu utworów.
 */
@Composable
private fun TrackListPage(vm: MainViewModel, title: String, icon: ImageVector, list: List<Track>, density: Int, pad: PaddingValues) {
    val ps by vm.player.state.collectAsState()
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var menuFor by remember { mutableStateOf<Pair<Int, Track>?>(null) }
    var addTo by remember { mutableStateOf<List<Track>?>(null) }
    addTo?.let { AddToPlaylistDialog(vm, it) { addTo = null; selecting = false; selected.clear() } }
    val curDensity by rememberUpdatedState(density)
    val chosen = { list.filter { it.id in selected } }

    Column(Modifier.fillMaxSize()) {
        CategoryHeader(title, icon, tracksSummary(list))
        // Akcje
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!selecting) {
                item { PillButton(null, { vm.player.play(list, shuffle = true) }, icon = Icons.Rounded.Shuffle, label = tr("Wymieszaj")) }
                item { PillButton(null, { vm.player.play(list) }, icon = Icons.Rounded.PlayArrow, label = tr("Odtwórz")) }
                item { PillButton(null, { vm.searchOpen = true }, icon = Icons.Rounded.Search, label = tr("Szukaj"), active = vm.searchOpen) }
                item { PillButton(tr("Wybierz"), { selecting = true }) }
                item { SortMenu(vm) }
            } else {
                item { PillButton(tr("{0} zaznaczone", selected.size), { selecting = false; selected.clear() }, icon = Icons.Rounded.Close, active = true) }
                item { PillButton(null, { if (selected.size == list.size) selected.clear() else { selected.clear(); selected.addAll(list.map { it.id }) } }, icon = Icons.Rounded.SelectAll, label = tr("Zaznacz wszystko")) }
                item { PillButton(null, { vm.player.play(chosen()); selecting = false; selected.clear() }, icon = Icons.Rounded.PlayArrow, label = tr("Odtwórz")) }
                item { PillButton(null, { vm.player.playNext(chosen()); vm.message(tr("Zagra jako następny")); selecting = false; selected.clear() }, icon = Icons.Rounded.SkipNext, label = tr("Odtwórz następny")) }
                item { PillButton(null, { vm.player.enqueue(chosen()); vm.message(tr("Dodano do kolejki")); selecting = false; selected.clear() }, icon = Icons.AutoMirrored.Rounded.QueueMusic, label = tr("Dodaj do kolejki")) }
                item { PillButton(null, { addTo = chosen() }, icon = Icons.AutoMirrored.Rounded.PlaylistAdd, label = tr("Dodaj do playlisty")) }
                item { PillButton(null, { chosen().forEach { t -> if (t.id !in Graph.userData.value.favorites) Graph.userData.toggleFavorite(t.id) }; selecting = false; selected.clear() }, icon = Icons.Rounded.Favorite, label = tr("Dodaj do ulubionych")) }
            }
        }
        Gap(6.dp)
        if (list.isEmpty()) { Empty(if (vm.query.isNotBlank()) tr("Brak wyników") else tr("Brak utworów")); return@Column }

        val step: (Int) -> Unit = { d ->
            val n = (curDensity + d).coerceIn(0, DENSITY_MAX)
            if (n != curDensity) vm.updateSettings { it.copy(trackDensity = n) }
        }
        val onTap: (Int, Track) -> Unit = { i, t ->
            if (selecting) { if (t.id in selected) selected.remove(t.id) else selected.add(t.id) } else vm.player.play(list, i)
        }
        val onLong: (Int, Track) -> Unit = { i, t ->
            if (selecting) { if (t.id in selected) selected.remove(t.id) else selected.add(t.id) } else menuFor = i to t
        }
        if (density >= 4) {
            LazyVerticalGrid(
                GridCells.Fixed(if (density == 4) 3 else 2),
                Modifier.fillMaxSize().pinchSteps(step),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = pad.calculateBottomPadding()),
            ) {
                itemsIndexed(list, key = { i, t -> "${t.id}_$i" }) { i, t ->
                    Box {
                        Column(Modifier.padding(4.dp).combinedClickable(onClick = { onTap(i, t) }, onLongClick = { onLong(i, t) })) {
                            Box {
                                Artwork(t, Modifier.fillMaxWidth().aspectRatio(1f), size = 360, shape = RoundedCornerShape(16.dp))
                                if (selecting) SelectMark(t.id in selected, Modifier.align(Alignment.TopEnd).padding(6.dp))
                                if (ps.trackId == t.id) Box(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(6.dp)) { PlayingIndicator(ps.isPlaying, color = Color.White) }
                            }
                            Text(t.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            Text(t.artist, color = Flat.Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (menuFor?.first == i) TrackMenu(vm, t, { menuFor = null })
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Gap(8.dp) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().pinchSteps(step), contentPadding = pad) {
                listItemsIndexed(list, key = { i, t -> "${t.id}_$i" }) { i, t ->
                    Box {
                        DenseRow(t, density, ps.trackId == t.id, ps.isPlaying, selecting, t.id in selected, { onTap(i, t) }, { onLong(i, t) })
                        if (menuFor?.first == i) TrackMenu(vm, t, { menuFor = null })
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
        tint = if (on) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
        modifier = modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
    )
}

/** Wiersz utworu w jednej z czterech gęstości (jak w klasycznych odtwarzaczach). */
@Composable
private fun DenseRow(t: Track, density: Int, current: Boolean, playing: Boolean, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val titleColor = if (current) accent else Color.White
    val meta = "${formatTime(t.durationMs)} | ${t.format.lowercase()}"
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = when (density) { 0 -> 9.dp; 1 -> 6.dp; else -> 7.dp }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) { SelectMark(selected); Spacer(Modifier.width(10.dp)) }
        if (density == 0) {
            if (current) { PlayingIndicator(playing); Spacer(Modifier.width(8.dp)) }
            Text(t.title, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(meta, color = Flat.Faded, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            return@Row
        }
        val art = when (density) { 1 -> 44.dp; 2 -> 72.dp; else -> 104.dp }
        Box {
            Artwork(t, Modifier.size(art), size = if (density >= 3) 360 else 200, shape = RoundedCornerShape(if (density == 1) 10.dp else 16.dp))
            if (current) Box(Modifier.size(art).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                PlayingIndicator(playing, color = Color.White)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, color = titleColor, fontSize = if (density >= 2) 17.sp else 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.artist} - ${t.album}", color = Color.White.copy(alpha = 0.85f), fontSize = if (density >= 2) 13.sp else 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (density >= 2) Text(meta, color = Flat.Faded, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
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
        // Szczypanie „na zewnątrz” = większe elementy (siatka z mniejszą liczbą kolumn).
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
