package com.blacksingularities.openamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.data.LibraryOps
import com.blacksingularities.openamp.data.Mix
import com.blacksingularities.openamp.data.SmartList
import com.blacksingularities.openamp.data.Smart
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.i18n.tr
import java.util.Calendar

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> tr("Dzień dobry")
    in 12..17 -> tr("Miłego popołudnia")
    in 18..22 -> tr("Dobry wieczór")
    else -> tr("Nocne granie")
}

@Composable
fun HomeScreen(vm: MainViewModel, padding: PaddingValues) {
    val all by vm.tracks.collectAsState()
    val data by vm.userData.collectAsState()
    val mixes = remember(all, data.stats.size, data.favorites) { Smart.mixes(all, data) }
    val recent = remember(all, data) { LibraryOps.smart(all, SmartList.RECENTLY_PLAYED, data).take(20) }
    val top = remember(all, data) { LibraryOps.smart(all, SmartList.MOST_PLAYED, data).take(20) }
    val newAlbums = remember(all) { LibraryOps.albums(all).sortedByDescending { a -> a.tracks.maxOf { it.dateAdded } }.take(16) }
    val randomAlbums = remember(all) { LibraryOps.albums(all).shuffled().take(16) }
    val totalMs = remember(data.stats) { data.stats.values.sumOf { it.listenMs } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(greeting(), style = MaterialTheme.typography.displaySmall)
                Text(
                    tracksSummary(all) + if (totalMs > 0) " • " + tr("słuchane {0}", formatTime(totalMs)) else "",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { QuickAction(Icons.Rounded.Shuffle, tr("Wymieszaj wszystko")) { vm.player.play(all, shuffle = true) } }
                item {
                    QuickAction(Icons.Rounded.Favorite, tr("Ulubione")) { vm.open(Route.Detail(DetailKind.SMART, SmartList.FAVORITES.name)) }
                }
                item { QuickAction(Icons.Rounded.BarChart, tr("Statystyki")) { vm.open(Route.Stats) } }
            }
        }
        if (mixes.isNotEmpty()) {
            item { HomeTitle(tr("Miksy dla Ciebie"), Icons.Rounded.AutoAwesome) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mixes, key = { it.id }) { m -> MixCard(vm, m) }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { HomeTitle(tr("Ostatnio odtwarzane")) }
            item { TrackRowStrip(vm, recent) }
        }
        if (top.isNotEmpty()) {
            item { HomeTitle(tr("Najczęściej odtwarzane")) }
            item { TrackRowStrip(vm, top) }
        }
        if (newAlbums.isNotEmpty()) {
            item { HomeTitle(tr("Ostatnio dodane")) }
            item { AlbumStrip(vm, newAlbums) }
        }
        if (randomAlbums.size > 4) {
            item { HomeTitle(tr("Może coś z tego?")) }
            item { AlbumStrip(vm, randomAlbums) }
        }
        if (all.isEmpty()) item { Empty(tr("Brak utworów")) }
    }
}

@Composable
private fun HomeTitle(text: String, icon: ImageVector? = null) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Karta miksu: kolaż czterech okładek z gradientem i tytułem. */
@Composable
private fun MixCard(vm: MainViewModel, m: Mix) {
    val covers = remember(m) { m.tracks.distinctBy { it.albumId }.take(4) }
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.width(170.dp).clickable { vm.open(Route.Detail(DetailKind.MIX, m.id)) }) {
        Box(Modifier.size(170.dp).clip(RoundedCornerShape(22.dp))) {
            Column {
                Row(Modifier.weight(1f)) {
                    Artwork(covers.getOrNull(0), Modifier.weight(1f).fillMaxSize(), size = 200, shape = RoundedCornerShape(0.dp))
                    Artwork(covers.getOrNull(1) ?: covers.getOrNull(0), Modifier.weight(1f).fillMaxSize(), size = 200, shape = RoundedCornerShape(0.dp))
                }
                Row(Modifier.weight(1f)) {
                    Artwork(covers.getOrNull(2) ?: covers.getOrNull(0), Modifier.weight(1f).fillMaxSize(), size = 200, shape = RoundedCornerShape(0.dp))
                    Artwork(covers.getOrNull(3) ?: covers.getOrNull(1), Modifier.weight(1f).fillMaxSize(), size = 200, shape = RoundedCornerShape(0.dp))
                }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.85f)))))
            Text(
                tr(m.title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
            Surface(
                onClick = { vm.player.play(m.tracks) }, shape = CircleShape, color = accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, tr("Odtwórz"), tint = MaterialTheme.colorScheme.onPrimary) } }
        }
        Spacer(Modifier.height(6.dp))
        Text(tr(m.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrackRowStrip(vm: MainViewModel, tracks: List<Track>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(tracks.size) { i ->
            val t = tracks[i]
            Column(Modifier.width(132.dp).clickable { vm.player.play(tracks, i) }) {
                Artwork(t, Modifier.fillMaxWidth().aspectRatio(1f), size = 300, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(6.dp))
                Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AlbumStrip(vm: MainViewModel, albums: List<com.blacksingularities.openamp.data.Album>) {
    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
        items(albums, key = { it.id }) { a ->
            Box(Modifier.width(150.dp)) {
                GridTile(a.title, a.artist, a.cover, onClick = { vm.open(Route.Detail(DetailKind.ALBUM, a.id.toString())) }, onPlay = { vm.player.play(a.tracks) })
            }
        }
    }
}
