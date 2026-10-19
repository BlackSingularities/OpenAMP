package com.blacksingularities.openamp.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.LibraryOps
import com.blacksingularities.openamp.data.SmartList
import com.blacksingularities.openamp.data.ViewMode
import com.blacksingularities.openamp.i18n.plural
import com.blacksingularities.openamp.i18n.tr

@Composable
fun DetailScreen(vm: MainViewModel, route: Route.Detail, bottomPadding: Dp) {
    val all by vm.tracks.collectAsState()
    val data by vm.userData.collectAsState()
    val s by vm.settings.collectAsState()

    val (title, subtitle, tracks) = remember(route, all, data) {
        when (route.kind) {
            DetailKind.ALBUM -> {
                val a = LibraryOps.albums(all.filter { it.albumId.toString() == route.key }).firstOrNull()
                Triple(a?.title ?: "", listOfNotNull(a?.artist, a?.year?.takeIf { it > 0 }?.toString()).joinToString(" • "), a?.tracks ?: emptyList())
            }
            DetailKind.ARTIST -> {
                val g = LibraryOps.artists(all).firstOrNull { it.key == route.key }
                Triple(g?.name ?: "", tr("Wykonawca"), g?.tracks ?: emptyList())
            }
            DetailKind.GENRE -> {
                val g = LibraryOps.genres(all).firstOrNull { it.key == route.key }
                Triple(g?.name ?: "", tr("Gatunek"), g?.tracks ?: emptyList())
            }
            DetailKind.FOLDER -> {
                val g = LibraryOps.folders(all).firstOrNull { it.key == route.key }
                Triple(g?.name ?: "", route.key, g?.tracks ?: emptyList())
            }
            DetailKind.PLAYLIST -> {
                val p = data.playlists.firstOrNull { it.id == route.key }
                val byId = all.associateBy { it.id }
                Triple(p?.name ?: "", tr("Playlisty"), p?.trackIds?.mapNotNull { byId[it] } ?: emptyList())
            }
            DetailKind.MIX -> {
                val m = com.blacksingularities.openamp.data.Smart.mixes(all, data).firstOrNull { it.id == route.key }
                Triple(m?.let { tr(it.title) } ?: "", m?.let { tr(it.subtitle) } ?: "", m?.tracks ?: emptyList())
            }
            DetailKind.SMART -> {
                val k = SmartList.valueOf(route.key)
                Triple(k.label(), tr("Inteligentne playlisty"), LibraryOps.smart(all, k, data))
            }
        }
    }
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    if (renaming) NameDialog(tr("Zmień nazwę"), tr("Nazwa playlisty"), title, { renaming = false }) {
        Graph.userData.renamePlaylist(route.key, it); renaming = false
    }

    val header: @Composable () -> Unit = {
        Column {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                val cover = tracks.firstOrNull()
                Artwork(cover, Modifier.fillMaxSize().blur(40.dp), size = 360, shape = RoundedCornerShape(0.dp))
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), MaterialTheme.colorScheme.background)),
                    ),
                )
                Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.Bottom) {
                    Artwork(cover, Modifier.size(150.dp), size = 500, shape = RoundedCornerShape(20.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.statusBarsPadding().padding(4.dp).fillMaxWidth()) {
                    IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), tint = Color.White) }
                    Spacer(Modifier.weight(1f))
                    if (route.kind == DetailKind.PLAYLIST) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, tr("Więcej"), tint = Color.White) }
                        DropdownMenu(menu, { menu = false }) {
                            MenuItem(tr("Zmień nazwę"), Icons.Rounded.Edit) { menu = false; renaming = true }
                            MenuItem(tr("Usuń playlistę"), Icons.Rounded.Delete) {
                                menu = false; Graph.userData.deletePlaylist(route.key); vm.back()
                            }
                        }
                    }
                }
            }
            if (route.kind == DetailKind.ARTIST) {
                val albums = remember(tracks) { LibraryOps.albums(tracks).sortedByDescending { it.year } }
                if (albums.size > 1) {
                    SectionTitle(plural(albums.size, "{0} album", "{0} albumy", "{0} albumów"))
                    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                        items(albums) { a ->
                            Box(Modifier.width(150.dp)) {
                                GridTile(a.title, a.year.takeIf { it > 0 }?.toString() ?: "", a.cover, onClick = {
                                    vm.open(Route.Detail(DetailKind.ALBUM, a.id.toString()))
                                })
                            }
                        }
                    }
                }
            }
            PlayHeader(vm, tracks)
        }
    }

    TrackCollection(
        vm, tracks,
        mode = if (route.kind == DetailKind.ALBUM) ViewMode.LIST else s.trackView.let { if (it == ViewMode.GRID || it == ViewMode.GRID_LARGE) ViewMode.LIST else it },
        padding = PaddingValues(bottom = bottomPadding + 16.dp),
        header = header,
        showArt = route.kind != DetailKind.ALBUM,
        numbered = route.kind == DetailKind.ALBUM,
        menuExtra = if (route.kind == DetailKind.PLAYLIST) { index ->
            { close -> MenuItem(tr("Usuń z playlisty"), Icons.Rounded.RemoveCircleOutline) { Graph.userData.removeFromPlaylist(route.key, index); close() } }
        } else null,
    )
}
