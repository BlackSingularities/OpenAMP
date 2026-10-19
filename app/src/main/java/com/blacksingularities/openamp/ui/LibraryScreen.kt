package com.blacksingularities.openamp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.data.GroupSort
import com.blacksingularities.openamp.data.LibraryOps
import com.blacksingularities.openamp.data.LibraryTab
import com.blacksingularities.openamp.data.Playlist
import com.blacksingularities.openamp.data.SmartList
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.data.TrackSort
import com.blacksingularities.openamp.data.ViewMode
import com.blacksingularities.openamp.i18n.plural
import com.blacksingularities.openamp.i18n.tr

fun LibraryTab.label() = when (this) {
    LibraryTab.TRACKS -> tr("Utwory")
    LibraryTab.ALBUMS -> tr("Albumy")
    LibraryTab.ARTISTS -> tr("Wykonawcy")
    LibraryTab.GENRES -> tr("Gatunki")
    LibraryTab.FOLDERS -> tr("Foldery")
    LibraryTab.PLAYLISTS -> tr("Playlisty")
}

fun TrackSort.label() = when (this) {
    TrackSort.TITLE -> tr("Tytuł")
    TrackSort.ARTIST -> tr("Wykonawca")
    TrackSort.ALBUM -> tr("Album")
    TrackSort.YEAR -> tr("Rok")
    TrackSort.DURATION -> tr("Czas trwania")
    TrackSort.DATE_ADDED -> tr("Ostatnio dodane")
    TrackSort.PLAY_COUNT -> tr("Najczęściej odtwarzane")
    TrackSort.LAST_PLAYED -> tr("Ostatnio odtwarzane")
    TrackSort.SIZE -> tr("Rozmiar pliku")
    TrackSort.RANDOM -> tr("Losowo")
}

fun GroupSort.label() = when (this) {
    GroupSort.NAME -> tr("Nazwa")
    GroupSort.TRACK_COUNT -> tr("Liczba utworów")
    GroupSort.YEAR -> tr("Rok")
    GroupSort.PLAY_COUNT -> tr("Najczęściej odtwarzane")
    GroupSort.DATE_ADDED -> tr("Ostatnio dodane")
    GroupSort.RANDOM -> tr("Losowo")
}

fun ViewMode.label() = when (this) {
    ViewMode.LIST -> tr("Lista")
    ViewMode.COMPACT -> tr("Kompaktowa lista")
    ViewMode.GRID -> tr("Siatka")
    ViewMode.GRID_LARGE -> tr("Siatka duża")
}

fun SmartList.label() = when (this) {
    SmartList.FAVORITES -> tr("Ulubione")
    SmartList.MOST_PLAYED -> tr("Najczęściej odtwarzane")
    SmartList.RECENTLY_PLAYED -> tr("Ostatnio odtwarzane")
    SmartList.RECENTLY_ADDED -> tr("Ostatnio dodane")
    SmartList.NEVER_PLAYED -> tr("Nigdy nieodtwarzane")
}

fun SmartList.icon(): ImageVector = when (this) {
    SmartList.FAVORITES -> Icons.Rounded.Favorite
    SmartList.MOST_PLAYED -> Icons.Rounded.TrendingUp
    SmartList.RECENTLY_PLAYED -> Icons.Rounded.History
    SmartList.RECENTLY_ADDED -> Icons.Rounded.NewReleases
    SmartList.NEVER_PLAYED -> Icons.Rounded.VisibilityOff
}

@Composable
fun LibraryScreen(vm: MainViewModel, bottomPadding: androidx.compose.ui.unit.Dp) {
    val s by vm.settings.collectAsState()
    val all by vm.tracks.collectAsState()
    val data by vm.userData.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val tab = s.tab
    val tracks = remember(all, vm.query) { LibraryOps.search(all, vm.query) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Nagłówek
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.searchOpen) {
                TextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    placeholder = { Text(tr("Szukaj w bibliotece")) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { IconButton(onClick = { vm.searchOpen = false; vm.query = "" }) { Icon(Icons.Rounded.Close, tr("Zamknij")) } },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(tr("Biblioteka"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.searchOpen = true }) { Icon(Icons.Rounded.Search, tr("Szukaj")) }
            }
            SortMenu(vm)
            IconButton(onClick = { vm.open(Route.Equalizer) }) { Icon(Icons.Rounded.Tune, tr("Korektor")) }
            IconButton(onClick = { vm.open(Route.Settings) }) { Icon(Icons.Rounded.Settings, tr("Ustawienia")) }
        }
        PrimaryScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            edgePadding = 12.dp,
            containerColor = Color.Transparent,
            divider = {},
        ) {
            LibraryTab.entries.forEach { t ->
                Tab(
                    selected = t == tab,
                    onClick = { vm.updateSettings { it.copy(tab = t) } },
                    text = { Text(t.label(), fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal) },
                )
            }
        }
        AnimatedVisibility(scanning) { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        val pad = PaddingValues(bottom = bottomPadding + 16.dp)
        when (tab) {
            LibraryTab.TRACKS -> {
                val sorted = remember(tracks, s.trackSort, s.trackSortDesc, data.stats) {
                    LibraryOps.sortTracks(tracks, s.trackSort, s.trackSortDesc, data.stats)
                }
                TrackCollection(vm, sorted, s.trackView, pad, header = { PlayHeader(vm, sorted) }, sort = s.trackSort)
            }
            LibraryTab.ALBUMS -> {
                val albums = remember(tracks, s.groupSort, s.groupSortDesc, data.stats) {
                    LibraryOps.sortGroups(LibraryOps.albums(tracks), s.groupSort, s.groupSortDesc, data.stats, { it.title }, { it.tracks })
                }
                GroupCollection(
                    vm, albums, s.groupView, pad,
                    title = { it.title }, subtitle = { a -> a.artist + if (a.year > 0) " • ${a.year}" else "" },
                    cover = { it.cover }, tracksOf = { it.tracks },
                    open = { vm.open(Route.Detail(DetailKind.ALBUM, it.id.toString())) },
                )
            }
            LibraryTab.ARTISTS, LibraryTab.GENRES, LibraryTab.FOLDERS -> {
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
                    vm, groups, s.groupView, pad,
                    title = { it.name },
                    subtitle = { g ->
                        if (kind == DetailKind.ARTIST) {
                            val albums = g.tracks.map { it.albumId }.distinct().size
                            plural(albums, "{0} album", "{0} albumy", "{0} albumów") + " • " + plural(g.tracks.size, "{0} utwór", "{0} utwory", "{0} utworów")
                        } else plural(g.tracks.size, "{0} utwór", "{0} utwory", "{0} utworów")
                    },
                    cover = { it.cover }, tracksOf = { it.tracks },
                    circle = kind == DetailKind.ARTIST,
                    folderIcon = kind == DetailKind.FOLDER,
                    open = { vm.open(Route.Detail(kind, it.key)) },
                )
            }
            LibraryTab.PLAYLISTS -> PlaylistsTab(vm, tracks, pad)
        }
    }
}

@Composable
private fun SortMenu(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    var open by remember { mutableStateOf(false) }
    val tracksTab = s.tab == LibraryTab.TRACKS
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, tr("Sortuj")) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(tr("Sortowanie"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (tracksTab) {
                TrackSort.entries.forEach { o ->
                    CheckItem(o.label(), s.trackSort == o) { vm.updateSettings { it.copy(trackSort = o) } }
                }
                CheckItem(tr("Malejąco"), s.trackSortDesc) { vm.updateSettings { it.copy(trackSortDesc = !it.trackSortDesc) } }
            } else {
                GroupSort.entries.forEach { o ->
                    CheckItem(o.label(), s.groupSort == o) { vm.updateSettings { it.copy(groupSort = o) } }
                }
                CheckItem(tr("Malejąco"), s.groupSortDesc) { vm.updateSettings { it.copy(groupSortDesc = !it.groupSortDesc) } }
            }
            HorizontalDivider()
            Text(tr("Widok"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            ViewMode.entries.forEach { m ->
                val cur = if (tracksTab) s.trackView else s.groupView
                DropdownMenuItem(
                    text = { Text(m.label()) },
                    leadingIcon = { Icon(if (m == ViewMode.LIST || m == ViewMode.COMPACT) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, null) },
                    trailingIcon = { if (cur == m) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { vm.updateSettings { if (tracksTab) it.copy(trackView = m) else it.copy(groupView = m) } },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(tr("Odśwież bibliotekę")) }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { open = false; vm.rescan() })
        }
    }
}

@Composable
private fun CheckItem(text: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
        trailingIcon = { if (checked) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = onClick,
    )
}

@Composable
fun PlayHeader(vm: MainViewModel, tracks: List<Track>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tracksSummary(tracks), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = { vm.player.play(tracks, 0, shuffle = true) }, enabled = tracks.isNotEmpty()) {
            Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Wymieszaj"))
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = { vm.player.play(tracks, 0, shuffle = false) }, enabled = tracks.isNotEmpty()) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Odtwórz"))
        }
    }
}

/** Lista albo siatka utworów z menu kontekstowym. */
@Composable
fun TrackCollection(
    vm: MainViewModel,
    tracks: List<Track>,
    mode: ViewMode,
    padding: PaddingValues,
    header: @Composable () -> Unit = {},
    sort: TrackSort? = null,
    showArt: Boolean = true,
    numbered: Boolean = false,
    menuExtra: ((Int) -> (@Composable (close: () -> Unit) -> Unit))? = null,
) {
    val ps by vm.player.state.collectAsState()
    val data by vm.userData.collectAsState()
    var menuFor by remember { mutableStateOf<Pair<Int, Track>?>(null) }
    if (tracks.isEmpty()) {
        Column { header(); Empty(if (vm.query.isNotBlank()) tr("Brak wyników") else tr("Brak utworów")) }
        return
    }
    fun info(t: Track): String? = when (sort) {
        TrackSort.PLAY_COUNT -> data.stats[t.id]?.count?.let { plural(it, "{0} odtworzenie", "{0} odtworzenia", "{0} odtworzeń") }
        TrackSort.LAST_PLAYED -> data.stats[t.id]?.lastPlayed?.let { formatDate(it) }
        TrackSort.YEAR -> t.year.takeIf { it > 0 }?.toString()
        TrackSort.SIZE -> formatSize(t.size)
        else -> null
    }
    if (mode == ViewMode.GRID || mode == ViewMode.GRID_LARGE) {
        LazyVerticalGrid(
            GridCells.Adaptive(if (mode == ViewMode.GRID) 150.dp else 220.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = padding.calculateBottomPadding()),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
            itemsIndexed(tracks, key = { i, t -> "${t.id}_$i" }) { i, t ->
                Box {
                    GridTile(t.title, t.artist, t, onClick = { vm.player.play(tracks, i) }, onLongClick = { menuFor = i to t })
                    if (menuFor?.first == i) TrackMenu(vm, t, { menuFor = null }, menuExtra?.invoke(i))
                }
            }
        }
    } else {
        LazyColumn(contentPadding = padding) {
            item { header() }
            itemsIndexed(tracks, key = { i, t -> "${t.id}_$i" }) { i, t ->
                Box {
                    TrackRow(
                        t, current = ps.trackId == t.id, playing = ps.isPlaying,
                        onClick = { vm.player.play(tracks, i) },
                        onMenu = { menuFor = i to t },
                        compact = mode == ViewMode.COMPACT,
                        trailingInfo = info(t),
                        showArt = showArt,
                        leading = if (numbered) ({
                            Text(
                                if (t.trackNumber > 0) t.trackNumber.toString() else "–",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (ps.trackId == t.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(32.dp),
                            )
                        }) else null,
                    )
                    if (menuFor?.first == i) TrackMenu(vm, t, { menuFor = null }, menuExtra?.invoke(i))
                }
            }
        }
    }
}

@Composable
fun <T> GroupCollection(
    vm: MainViewModel,
    groups: List<T>,
    mode: ViewMode,
    padding: PaddingValues,
    title: (T) -> String,
    subtitle: (T) -> String,
    cover: (T) -> Track?,
    tracksOf: (T) -> List<Track>,
    open: (T) -> Unit,
    circle: Boolean = false,
    folderIcon: Boolean = false,
) {
    if (groups.isEmpty()) { Empty(if (vm.query.isNotBlank()) tr("Brak wyników") else tr("Brak utworów")); return }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var addTo by remember { mutableStateOf<List<Track>?>(null) }
    addTo?.let { AddToPlaylistDialog(vm, it) { addTo = null } }
    val menu: @Composable (Int, T) -> Unit = { i, g ->
        DropdownMenu(expanded = menuFor == i, onDismissRequest = { menuFor = null }) {
            MenuItem(tr("Odtwórz"), Icons.AutoMirrored.Rounded.PlaylistPlay) { vm.player.play(tracksOf(g)); menuFor = null }
            MenuItem(tr("Wymieszaj"), Icons.Rounded.Shuffle) { vm.player.play(tracksOf(g), shuffle = true); menuFor = null }
            MenuItem(tr("Odtwórz następny"), Icons.Rounded.Add) { vm.player.playNext(tracksOf(g)); vm.message(tr("Zagra jako następny")); menuFor = null }
            MenuItem(tr("Dodaj do kolejki"), Icons.Rounded.Add) { vm.player.enqueue(tracksOf(g)); vm.message(tr("Dodano do kolejki")); menuFor = null }
            MenuItem(tr("Dodaj do playlisty"), Icons.Rounded.Add) { addTo = tracksOf(g); menuFor = null }
        }
    }
    if (mode == ViewMode.GRID || mode == ViewMode.GRID_LARGE) {
        LazyVerticalGrid(
            GridCells.Adaptive(if (mode == ViewMode.GRID) 150.dp else 220.dp),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = padding.calculateBottomPadding()),
        ) {
            itemsIndexed(groups) { i, g ->
                Box {
                    GridTile(title(g), subtitle(g), cover(g), onClick = { open(g) }, onLongClick = { menuFor = i }, onPlay = { vm.player.play(tracksOf(g)) }, circle = circle)
                    menu(i, g)
                }
            }
        }
    } else {
        val compact = mode == ViewMode.COMPACT
        LazyColumn(contentPadding = padding) {
            itemsIndexed(groups) { i, g ->
                Box {
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { open(g) }, onLongClick = { menuFor = i })
                            .padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (folderIcon) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(if (compact) 40.dp else 56.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary) }
                            }
                        } else {
                            Artwork(cover(g), Modifier.size(if (compact) 40.dp else 56.dp), size = 160, shape = if (circle) CircleShape else RoundedCornerShape(12.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title(g), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subtitle(g), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    menu(i, g)
                }
            }
        }
    }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaylistsTab(vm: MainViewModel, tracks: List<Track>, padding: PaddingValues) {
    val data by vm.userData.collectAsState()
    var creating by remember { mutableStateOf(false) }
    if (creating) NameDialog(tr("Nowa playlista"), tr("Nazwa playlisty"), "", { creating = false }) {
        com.blacksingularities.openamp.Graph.userData.createPlaylist(it); creating = false
    }
    LazyColumn(contentPadding = padding) {
        item { SectionTitle(tr("Inteligentne playlisty")) }
        items(SmartList.entries) { kind ->
            val list = remember(tracks, data) { LibraryOps.smart(tracks, kind, data) }
            PlaylistRow(kind.label(), plural(list.size, "{0} utwór", "{0} utwory", "{0} utworów"), kind.icon(), list.firstOrNull()) {
                vm.open(Route.Detail(DetailKind.SMART, kind.name))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(tr("Twoje playlisty"), Modifier.weight(1f))
                IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, tr("Nowa playlista"), tint = MaterialTheme.colorScheme.primary) }
            }
        }
        items(data.playlists, key = { it.id }) { p: Playlist ->
            val first = p.trackIds.firstNotNullOfOrNull { id -> tracks.firstOrNull { it.id == id } }
            PlaylistRow(p.name, plural(p.trackIds.size, "{0} utwór", "{0} utwory", "{0} utworów"), Icons.AutoMirrored.Rounded.PlaylistPlay, first) {
                vm.open(Route.Detail(DetailKind.PLAYLIST, p.id))
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun PlaylistRow(title: String, subtitle: String, icon: ImageVector, cover: Track?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp)) {
            if (cover != null) Artwork(cover, Modifier.fillMaxSize(), size = 160, shape = RoundedCornerShape(12.dp))
            Box(
                Modifier.fillMaxSize().background(
                    if (cover != null) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(12.dp),
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (cover != null) Color.White else MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
