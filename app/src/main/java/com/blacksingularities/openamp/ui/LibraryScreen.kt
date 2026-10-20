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
import androidx.compose.material.icons.rounded.SwapVert
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.SkipNext
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
    LibraryTab.TRACKS -> tr("Wszystkie utwory")
    LibraryTab.RECENT_ADDED -> tr("Ostatnio dodane")
    LibraryTab.MOST_PLAYED -> tr("Najczęściej odtwarzane")
    LibraryTab.RECENT_PLAYED -> tr("Ostatnio odtwarzane")
    LibraryTab.FAVORITES -> tr("Ulubione")
    LibraryTab.HOME -> tr("Miksy dla Ciebie")
    LibraryTab.CATEGORIES -> tr("Kategorie AI")
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

/** Co pokazuje przycisk opcji listy: sortowanie utworów, grup, albo tylko widok. */
enum class ListSort { TRACKS, GROUPS, NONE }
enum class ListView { DENSITY, GROUPS, HOME, NONE }

private val densityLabels = listOf("Sam tekst", "Małe okładki", "Średnie okładki", "Duże okładki", "Siatka – 3 kolumny", "Siatka – 2 kolumny")

/**
 * Przycisk opcji listy: kompaktowe menu z sortowaniem (tylko tam, gdzie ma sens) i widokiem.
 * W miejscach z własną kolejnością (miksy, ostatnio dodane, kolejka…) zostaje tylko widok.
 */
@Composable
fun ListOptions(vm: MainViewModel, sort: ListSort, view: ListView) {
    if (sort == ListSort.NONE && view == ListView.NONE) return
    val s by vm.settings.collectAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        RoundButton(
            if (sort != ListSort.NONE) Icons.AutoMirrored.Rounded.Sort else Icons.AutoMirrored.Rounded.ViewList,
            if (sort != ListSort.NONE) tr("Sortuj") else tr("Widok"), { open = true }, size = 38.dp, iconSize = 19.dp,
        )
        CompactMenu(open, { open = false }) {
            when (sort) {
                ListSort.TRACKS -> {
                    CMenuLabel(tr("Sortowanie"))
                    TrackSort.entries.forEach { o -> CMenuItem(o.label(), checked = s.trackSort == o) { vm.updateSettings { it.copy(trackSort = o) } } }
                    CMenuItem(tr("Malejąco"), Icons.Rounded.SwapVert, checked = s.trackSortDesc) { vm.updateSettings { it.copy(trackSortDesc = !it.trackSortDesc) } }
                }
                ListSort.GROUPS -> {
                    CMenuLabel(tr("Sortowanie"))
                    GroupSort.entries.forEach { o -> CMenuItem(o.label(), checked = s.groupSort == o) { vm.updateSettings { it.copy(groupSort = o) } } }
                    CMenuItem(tr("Malejąco"), Icons.Rounded.SwapVert, checked = s.groupSortDesc) { vm.updateSettings { it.copy(groupSortDesc = !it.groupSortDesc) } }
                }
                ListSort.NONE -> Unit
            }
            if (sort != ListSort.NONE && view != ListView.NONE) CMenuDivider()
            when (view) {
                ListView.DENSITY -> {
                    CMenuLabel(tr("Widok"))
                    densityLabels.forEachIndexed { i, l -> CMenuItem(tr(l), checked = s.trackDensity == i) { vm.updateSettings { it.copy(trackDensity = i) } } }
                }
                ListView.GROUPS -> {
                    CMenuLabel(tr("Widok"))
                    ViewMode.entries.forEach { m -> CMenuItem(m.label(), if (m == ViewMode.LIST || m == ViewMode.COMPACT) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, checked = s.groupView == m) { vm.updateSettings { it.copy(groupView = m) } } }
                }
                ListView.HOME -> {
                    CMenuLabel(tr("Widok"))
                    CMenuItem(tr("Karty"), Icons.Rounded.GridView, checked = s.homeView != ViewMode.LIST) { vm.updateSettings { it.copy(homeView = ViewMode.GRID) } }
                    CMenuItem(tr("Lista"), Icons.AutoMirrored.Rounded.ViewList, checked = s.homeView == ViewMode.LIST) { vm.updateSettings { it.copy(homeView = ViewMode.LIST) } }
                }
                ListView.NONE -> Unit
            }
        }
    }
}

@Composable
fun PlayHeader(vm: MainViewModel, tracks: List<Track>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tracksSummary(tracks), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        PillButton(tr("Wymieszaj"), { if (tracks.isNotEmpty()) vm.player.play(tracks, 0, shuffle = true) }, icon = Icons.Rounded.Shuffle, compact = true)
        Spacer(Modifier.width(6.dp))
        PillButton(tr("Odtwórz"), { if (tracks.isNotEmpty()) vm.player.play(tracks, 0, shuffle = false) }, icon = Icons.AutoMirrored.Rounded.PlaylistPlay, active = true, compact = true)
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
        CompactMenu(menuFor == i, { menuFor = null }) {
            CMenuItem(tr("Odtwórz"), Icons.AutoMirrored.Rounded.PlaylistPlay) { vm.player.play(tracksOf(g)); menuFor = null }
            CMenuItem(tr("Wymieszaj"), Icons.Rounded.Shuffle) { vm.player.play(tracksOf(g), shuffle = true); menuFor = null }
            CMenuItem(tr("Odtwórz następny"), Icons.Rounded.SkipNext) { vm.player.playNext(tracksOf(g)); vm.message(tr("Zagra jako następny")); menuFor = null }
            CMenuItem(tr("Dodaj do kolejki"), Icons.AutoMirrored.Rounded.QueueMusic) { vm.player.enqueue(tracksOf(g)); vm.message(tr("Dodano do kolejki")); menuFor = null }
            CMenuItem(tr("Dodaj do playlisty"), Icons.AutoMirrored.Rounded.PlaylistAdd) { addTo = tracksOf(g); menuFor = null }
        }
    }
    if (mode == ViewMode.GRID || mode == ViewMode.GRID_LARGE) {
        LazyVerticalGrid(
            GridCells.Adaptive(if (mode == ViewMode.GRID) 120.dp else 180.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 2.dp, bottom = padding.calculateBottomPadding()),
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
                            .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (folderIcon) {
                            Box(Modifier.size(if (compact) 36.dp else 48.dp).clip(RoundedCornerShape(11.dp)).background(Flat.Block), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            Artwork(cover(g), Modifier.size(if (compact) 36.dp else 48.dp), size = 160, shape = if (circle) CircleShape else RoundedCornerShape(11.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title(g), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subtitle(g), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
fun PlaylistsTab(vm: MainViewModel, tracks: List<Track>, padding: PaddingValues) {
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
        text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PlaylistRow(title: String, subtitle: String, icon: ImageVector, cover: Track?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp)) {
            if (cover != null) Artwork(cover, Modifier.fillMaxSize(), size = 160, shape = RoundedCornerShape(12.dp))
            Box(
                Modifier.fillMaxSize().background(
                    if (cover != null) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.secondaryContainer,
                    RoundedCornerShape(12.dp),
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (cover != null) Color.White else MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
