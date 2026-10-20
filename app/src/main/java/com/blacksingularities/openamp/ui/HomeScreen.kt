package com.blacksingularities.openamp.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.LibraryOps
import com.blacksingularities.openamp.data.SmartList
import com.blacksingularities.openamp.data.Smart
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.data.ViewMode
import com.blacksingularities.openamp.i18n.plural
import com.blacksingularities.openamp.i18n.tr

/** Miks gotowy do pokazania: lista utworów i opis. [key] – klucz trasy szczegółów. */
data class MixUi(val key: String, val title: String, val subtitle: String, val tracks: List<Track>, val ai: Boolean)

/** Miksy z analizy AI (najpierw) i miksy liczone z nawyków słuchania – wszystkie złożone z utworów. */
@Composable
fun rememberMixes(vm: MainViewModel): List<MixUi> {
    val all by vm.tracks.collectAsState()
    val data by vm.userData.collectAsState()
    val cur by Graph.curator.state.collectAsState()
    return remember(all, data.stats.size, data.favorites, cur) {
        val byId = all.associateBy { it.id }
        val ai = cur.mixes.map { m -> MixUi("ai:" + m.id, m.title, m.subtitle, m.trackIds.mapNotNull { byId[it] }, true) }.filter { it.tracks.size >= 3 }
        val local = Smart.mixes(all, data).map { MixUi(it.id, tr(it.title), tr(it.subtitle), it.tracks, false) }
        ai + local
    }
}

@Composable
fun HomeScreen(vm: MainViewModel, padding: PaddingValues) {
    val all by vm.tracks.collectAsState()
    val data by vm.userData.collectAsState()
    val s by vm.settings.collectAsState()
    val mixes = rememberMixes(vm)
    val recent = remember(all, data) { LibraryOps.smart(all, SmartList.RECENTLY_PLAYED, data).take(24) }
    val top = remember(all, data) { LibraryOps.smart(all, SmartList.MOST_PLAYED, data).take(24) }
    val newest = remember(all) { LibraryOps.smart(all, SmartList.RECENTLY_ADDED, com.blacksingularities.openamp.data.UserData()).take(24) }
    val random = remember(all) { all.shuffled().take(24) }
    val asList = s.homeView == ViewMode.LIST

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item { AiProposal(vm) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { PillButton(tr("Wymieszaj wszystko"), { vm.player.play(all, shuffle = true) }, icon = Icons.Rounded.Shuffle, compact = true) }
                item { PillButton(tr("Ulubione"), { vm.open(Route.Detail(DetailKind.SMART, SmartList.FAVORITES.name)) }, icon = Icons.Rounded.Favorite, compact = true) }
                item { PillButton(tr("Kategorie AI"), { vm.updateSettings { it.copy(tab = com.blacksingularities.openamp.data.LibraryTab.CATEGORIES) } }, icon = Icons.Rounded.AutoAwesome, compact = true) }
            }
        }
        if (mixes.isNotEmpty()) {
            item { HomeTitle(tr("Miksy"), plural(mixes.size, "{0} miks", "{0} miksy", "{0} miksów")) }
            if (asList) {
                items(mixes, key = { it.key }) { m -> MixRow(vm, m) }
            } else {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(mixes, key = { it.key }) { m -> MixCard(vm, m) }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) { item { HomeTitle(tr("Ostatnio odtwarzane")) }; item { TrackStrip(vm, recent) } }
        if (top.isNotEmpty()) { item { HomeTitle(tr("Najczęściej odtwarzane")) }; item { TrackStrip(vm, top) } }
        if (newest.isNotEmpty()) { item { HomeTitle(tr("Ostatnio dodane")) }; item { TrackStrip(vm, newest) } }
        if (random.size > 6) { item { HomeTitle(tr("Może coś z tego?")) }; item { TrackStrip(vm, random) } }
        if (all.isEmpty()) item { Empty(tr("Brak utworów")) }
    }
}

@Composable
private fun HomeTitle(text: String, count: String? = null) {
    Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Flat.Fg)
        if (count != null) Text(count, fontSize = 11.sp, color = Flat.Faded, modifier = Modifier.padding(start = 8.dp, bottom = 1.dp))
    }
}

/** Kolaż czterech okładek utworów miksu. */
@Composable
private fun Collage(tracks: List<Track>, modifier: Modifier) {
    val covers = remember(tracks) { tracks.distinctBy { it.albumId }.take(4).ifEmpty { tracks.take(1) } }
    Column(modifier) {
        Row(Modifier.weight(1f)) {
            Artwork(covers.getOrNull(0), Modifier.weight(1f).fillMaxSize(), size = 200, shape = RoundedCornerShape(0.dp))
            Artwork(covers.getOrNull(1) ?: covers.getOrNull(0), Modifier.weight(1f).fillMaxSize(), size = 200, shape = RoundedCornerShape(0.dp))
        }
        Row(Modifier.weight(1f)) {
            Artwork(covers.getOrNull(2) ?: covers.getOrNull(0), Modifier.weight(1f).fillMaxSize(), size = 200, shape = RoundedCornerShape(0.dp))
            Artwork(covers.getOrNull(3) ?: covers.getOrNull(1), Modifier.weight(1f).fillMaxSize(), size = 200, shape = RoundedCornerShape(0.dp))
        }
    }
}

@Composable
private fun MixCard(vm: MainViewModel, m: MixUi) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.width(142.dp).reactive(null) { vm.open(Route.Detail(DetailKind.MIX, m.key)) }) {
        Box(Modifier.size(142.dp).clip(Shapes.Large)) {
            Collage(m.tracks, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.82f)))))
            if (m.ai) Icon(Icons.Rounded.AutoAwesome, null, tint = Flat.Fg.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.TopStart).padding(9.dp).size(14.dp))
            Text(
                m.title, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.ExtraBold, color = Flat.Fg, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
            RoundButton(Icons.Rounded.PlayArrow, tr("Odtwórz"), { vm.player.play(m.tracks) }, Modifier.align(Alignment.TopEnd).padding(7.dp), active = true, size = 30.dp, iconSize = 18.dp)
        }
        Text(m.subtitle, fontSize = 11.sp, lineHeight = 13.sp, color = Flat.Dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun MixRow(vm: MainViewModel, m: MixUi) {
    Row(
        Modifier.fillMaxWidth().reactive(null) { vm.open(Route.Detail(DetailKind.MIX, m.key)) }.padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Collage(m.tracks, Modifier.size(50.dp).clip(Shapes.Medium))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.ai) { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)) }
                Text(m.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Flat.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(m.subtitle + " • " + plural(m.tracks.size, "{0} utwór", "{0} utwory", "{0} utworów"), fontSize = 11.sp, color = Flat.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundButton(Icons.Rounded.PlayArrow, tr("Odtwórz"), { vm.player.play(m.tracks) }, size = 34.dp, iconSize = 19.dp)
    }
}

@Composable
private fun TrackStrip(vm: MainViewModel, tracks: List<Track>) {
    val ps by vm.player.state.collectAsState()
    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(tracks, key = { i, t -> "${t.id}_$i" }) { i, t ->
            Column(Modifier.width(108.dp).reactive(t.title) { vm.player.play(tracks, i) }) {
                Box {
                    Artwork(t, Modifier.fillMaxWidth().aspectRatio(1f), size = 300, shape = Shapes.Medium)
                    if (ps.trackId == t.id) Box(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(5.dp)) {
                        PlayingIndicator(ps.isPlaying, color = Flat.Fg)
                    }
                }
                Text(t.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Flat.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                Text(t.artist, fontSize = 10.sp, color = Flat.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
