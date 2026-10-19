package com.blacksingularities.openamp.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.i18n.plural
import com.blacksingularities.openamp.i18n.tr
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.2f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.ROOT, "%d kB", bytes / 1024)
}

fun formatDate(ms: Long): String = if (ms <= 0) tr("nigdy") else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

fun tracksSummary(tracks: List<Track>): String =
    plural(tracks.size, "{0} utwór", "{0} utwory", "{0} utworów") + " • " + formatTime(tracks.sumOf { it.durationMs })

/** Animowane słupki przy odtwarzanym utworze. */
@Composable
fun PlayingIndicator(playing: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val t = rememberInfiniteTransition(label = "eq")
    val phases = List(3) { i ->
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420 + i * 130), RepeatMode.Reverse), label = "b$i")
    }
    Canvas(modifier.size(18.dp)) {
        val w = size.width / 5
        phases.forEachIndexed { i, p ->
            val h = size.height * (if (playing) p.value else 0.3f)
            drawRoundRect(color, Offset(i * w * 2, size.height - h), Size(w, h), CornerRadius(w / 2))
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    current: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    compact: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailingInfo: String? = null,
    showArt: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .background(if (current) accent.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading()
        else if (showArt) {
            Box {
                Artwork(track, Modifier.size(if (compact) 40.dp else 52.dp), size = 160, shape = RoundedCornerShape(if (compact) 8.dp else 12.dp))
                if (current) {
                    Box(
                        Modifier.matchParentSizeCompat(if (compact) 40 else 52).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { PlayingIndicator(playing, color = Color.White) }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                color = if (current) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (compact) track.artist else "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTime(track.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (trailingInfo != null) Text(trailingInfo, style = MaterialTheme.typography.labelSmall, color = accent)
        }
        IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, tr("Więcej"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun Modifier.matchParentSizeCompat(dp: Int) = this.size(dp.dp)

/** Kafelek siatki: okładka + dwa wiersze tekstu. */
@Composable
fun GridTile(
    title: String,
    subtitle: String,
    cover: Track?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPlay: (() -> Unit)? = null,
    circle: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Artwork(cover, Modifier.fillMaxSize(), size = 360, shape = if (circle) CircleShape else RoundedCornerShape(18.dp))
            if (onPlay != null) {
                Surface(
                    onClick = onPlay,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, tr("Odtwórz"), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = if (circle) Modifier.align(Alignment.CenterHorizontally) else Modifier,
        )
        Text(
            subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = if (circle) Modifier.align(Alignment.CenterHorizontally) else Modifier,
        )
    }
}

@Composable
fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

/** Menu utworu (długie przytrzymanie lub ⋮): kolejka, playlisty, ulubione, nawigacja, szczegóły. */
@Composable
fun TrackMenu(vm: MainViewModel, track: Track?, onDismiss: () -> Unit, extra: (@Composable (close: () -> Unit) -> Unit)? = null) {
    if (track == null) return
    val data by vm.userData.collectAsState()
    var addTo by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val context = LocalContext.current
    DropdownMenu(expanded = !addTo && !details, onDismissRequest = onDismiss) {
        Text(
            track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(220.dp).basicMarquee(),
        )
        HorizontalDivider()
        MenuItem(tr("Odtwórz następny"), Icons.Rounded.SkipNext) { vm.player.playNext(listOf(track)); vm.message(tr("Zagra jako następny")); onDismiss() }
        MenuItem(tr("Dodaj do kolejki"), Icons.AutoMirrored.Rounded.QueueMusic) { vm.player.enqueue(listOf(track)); vm.message(tr("Dodano do kolejki")); onDismiss() }
        MenuItem(tr("Dodaj do playlisty"), Icons.AutoMirrored.Rounded.PlaylistAdd) { addTo = true }
        val fav = track.id in data.favorites
        MenuItem(
            if (fav) tr("Usuń z ulubionych") else tr("Dodaj do ulubionych"),
            if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        ) { Graph.userData.toggleFavorite(track.id); onDismiss() }
        MenuItem(tr("Przejdź do albumu"), Icons.Rounded.Album) {
            vm.nowPlayingOpen = false; vm.open(Route.Detail(DetailKind.ALBUM, track.albumId.toString())); onDismiss()
        }
        MenuItem(tr("Przejdź do wykonawcy"), Icons.Rounded.Person) {
            vm.nowPlayingOpen = false; vm.open(Route.Detail(DetailKind.ARTIST, track.artist.lowercase())); onDismiss()
        }
        MenuItem(tr("Udostępnij"), Icons.Rounded.Share) {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType(track.mime.ifEmpty { "audio/*" })
                .putExtra(android.content.Intent.EXTRA_STREAM, track.uri)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(android.content.Intent.createChooser(send, track.title)) }
            onDismiss()
        }
        MenuItem(tr("Szczegóły"), Icons.Rounded.Info) { details = true }
        extra?.invoke(onDismiss)
    }
    if (addTo) AddToPlaylistDialog(vm, listOf(track)) { addTo = false; onDismiss() }
    if (details) TrackDetailsDialog(track) { details = false; onDismiss() }
}

@Composable
fun AddToPlaylistDialog(vm: MainViewModel, tracks: List<Track>, onDismiss: () -> Unit) {
    val data by vm.userData.collectAsState()
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(tr("Nowa playlista"), tr("Nazwa playlisty"), "", onDismiss) { name ->
            Graph.userData.createPlaylist(name, tracks.map { it.id })
            vm.message(tr("Dodano do playlisty {0}", name))
            onDismiss()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Dodaj do playlisty")) },
        text = {
            Column {
                TextButton(onClick = { creating = true }) { Text("+  " + tr("Nowa playlista")) }
                data.playlists.forEach { p ->
                    TextButton(onClick = {
                        Graph.userData.addToPlaylist(p.id, tracks.map { it.id })
                        vm.message(tr("Dodano do playlisty {0}", p.name))
                        onDismiss()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(p.name, Modifier.weight(1f))
                        Text(plural(p.trackIds.size, "{0} utwór", "{0} utwory", "{0} utworów"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Anuluj")) } },
    )
}

@Composable
fun NameDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(tr("Zapisz")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Anuluj")) } },
    )
}

@Composable
fun TrackDetailsDialog(track: Track, onDismiss: () -> Unit) {
    val stat = Graph.userData.value.stats[track.id]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(track.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine(tr("Wykonawca"), track.artist)
                DetailLine(tr("Album"), track.album)
                if (track.genre.isNotBlank()) DetailLine(tr("Gatunek"), track.genre)
                if (track.composer.isNotBlank()) DetailLine(tr("Kompozytor"), track.composer)
                if (track.year > 0) DetailLine(tr("Rok"), track.year.toString())
                if (track.trackNumber > 0) DetailLine(tr("Numer utworu"), track.trackNumber.toString())
                DetailLine(tr("Czas trwania"), formatTime(track.durationMs))
                DetailLine(tr("Format"), track.format)
                DetailLine(tr("Rozmiar"), formatSize(track.size))
                DetailLine(tr("Data dodania"), formatDate(track.dateAdded))
                DetailLine(tr("Odtworzenia"), (stat?.count ?: 0).toString())
                DetailLine(tr("Ostatnio"), formatDate(stat?.lastPlayed ?: 0))
                DetailLine(tr("Ścieżka"), track.path)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Zamknij")) } },
    )
}

@Composable
fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
