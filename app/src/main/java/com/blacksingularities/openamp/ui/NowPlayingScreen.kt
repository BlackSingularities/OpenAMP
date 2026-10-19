package com.blacksingularities.openamp.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.CoverShape
import com.blacksingularities.openamp.data.RepeatMode
import com.blacksingularities.openamp.data.SeekStyle
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.data.VisualizerStyle
import com.blacksingularities.openamp.i18n.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun QueueSheet(vm: MainViewModel) {
    val ps by vm.player.state.collectAsState()
    val all by vm.tracks.collectAsState()
    val byId = remember(all) { all.associateBy { it.id } }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { if (ps.index > 0) list.scrollToItem((ps.index - 1).coerceAtLeast(0)) }
    ModalBottomSheet(onDismissRequest = { vm.queueOpen = false }, sheetState = sheet) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Kolejka"), style = MaterialTheme.typography.titleLarge)
                val remaining = ps.queue.drop(ps.index + 1).sumOf { byId[it]?.durationMs ?: 0L }
                Text("${ps.index + 1} / ${ps.queue.size} • ${formatTime(remaining)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.player.clearQueue(); vm.queueOpen = false }) { Icon(Icons.Rounded.DeleteSweep, tr("Wyczyść kolejkę")) }
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().height(520.dp)) {
            itemsIndexed(ps.queue) { i, id ->
                val t = byId[id] ?: return@itemsIndexed
                val current = i == ps.index
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { vm.player.skipTo(i) }
                        .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Artwork(t, Modifier.size(44.dp), size = 160, shape = RoundedCornerShape(10.dp))
                        if (current) Box(Modifier.size(44.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            PlayingIndicator(ps.isPlaying, color = Color.White)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { if (i > 0) vm.player.move(i, i - 1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, null) }
                    IconButton(onClick = { if (i < ps.queue.size - 1) vm.player.move(i, i + 1) }, enabled = i < ps.queue.size - 1) { Icon(Icons.Rounded.KeyboardArrowDown, null) }
                    IconButton(onClick = { vm.player.removeAt(i) }) { Icon(Icons.Rounded.Close, tr("Usuń z kolejki")) }
                }
            }
        }
    }
}

@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    val at by Graph.sleepAt.collectAsState()
    val end by Graph.sleepEndOfTrack.collectAsState()
    var minutes by remember { mutableStateOf(30f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Wyłącznik czasowy")) },
        text = {
            Column {
                Text(tr("Muzyka zatrzyma się za {0}", tr("{0} min", minutes.roundToInt())), style = MaterialTheme.typography.titleMedium)
                Slider(minutes, { minutes = it }, valueRange = 5f..180f, steps = 34)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { m ->
                        FilterChip(selected = abs(minutes - m) < 1, onClick = { minutes = m.toFloat() }, label = { Text("$m") })
                    }
                }
                TextButton(onClick = { Graph.setSleepEndOfTrack(); onDismiss() }) { Text(tr("Do końca utworu")) }
                if (at != null || end) TextButton(onClick = { Graph.clearSleep(); onDismiss() }) { Text(tr("Wyłącz wyłącznik")) }
            }
        },
        confirmButton = { TextButton(onClick = { Graph.setSleep(minutes.roundToInt()); onDismiss() }) { Text(tr("Gotowe")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Anuluj")) } },
    )
}

@Composable
fun SpeedDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val ps by vm.player.state.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Szybkość i wysokość")) },
        text = {
            Column {
                Text(tr("Szybkość") + String.format(Locale.ROOT, ": %.2fx", ps.speed))
                Slider(ps.speed, { vm.player.setSpeedPitch((it * 20).roundToInt() / 20f, ps.pitch) }, valueRange = 0.5f..2f)
                Text(tr("Wysokość dźwięku") + String.format(Locale.ROOT, ": %.2fx", ps.pitch))
                Slider(ps.pitch, { vm.player.setSpeedPitch(ps.speed, (it * 20).roundToInt() / 20f) }, valueRange = 0.5f..2f)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Gotowe")) } },
        dismissButton = { TextButton(onClick = { vm.player.setSpeedPitch(1f, 1f) }) { Text(tr("Resetuj")) } },
    )
}

/** Mini odtwarzacz nad dolną krawędzią biblioteki. */
@Composable
fun MiniPlayer(vm: MainViewModel, modifier: Modifier = Modifier) {
    val track by vm.currentTrack.collectAsState()
    val ps by vm.player.state.collectAsState()
    val pos by vm.player.position.collectAsState()
    val t = track ?: return
    var swipe by remember { mutableStateOf(0f) }
    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = {
                    if (swipe < -100) vm.player.next() else if (swipe > 100) vm.player.previous()
                    swipe = 0f
                }) { c, dx -> c.consume(); swipe += dx }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { c, dy -> if (dy < -12) { c.consume(); vm.nowPlayingOpen = true } }
            },
        onClick = { vm.nowPlayingOpen = true },
        shape = RoundedCornerShape(22.dp),
        color = Flat.Block,
        shadowElevation = 0.dp,
    ) {
        Column {
            Row(Modifier.padding(8.dp).graphicsLayer { translationX = swipe * 0.3f }, verticalAlignment = Alignment.CenterVertically) {
                Artwork(t, Modifier.size(48.dp), size = 160, shape = RoundedCornerShape(12.dp), crossfade = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.player.toggle() }) {
                    Icon(if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { vm.player.next() }) { Icon(Icons.Rounded.SkipNext, tr("Następny")) }
            }
            val p = if (ps.durationMs > 0) pos.toFloat() / ps.durationMs else 0f
            Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(3.dp).background(MaterialTheme.colorScheme.primary))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
