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
fun NowPlayingScreen(vm: MainViewModel) {
    val track by vm.currentTrack.collectAsState()
    val ps by vm.player.state.collectAsState()
    val pos by vm.player.position.collectAsState()
    val s by vm.settings.collectAsState()
    val wave by vm.waveform.collectAsState()
    val info by vm.fileInfo.collectAsState()
    val data by vm.userData.collectAsState()
    val accent = MaterialTheme.colorScheme.primary
    val live = rememberLiveSpectrum(ps.isPlaying, keepHistory = if (s.seekStyle == SeekStyle.SPECTROGRAM) 200 else 0)
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    var menu by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragY.value.roundToInt()) }
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                // Przeciągnięcie w dół zamyka ekran odtwarzania.
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (dragY.value > size.height * 0.2f) {
                                vm.nowPlayingOpen = false
                                dragY.snapTo(0f)
                            } else dragY.animateTo(0f, spring())
                        }
                    },
                ) { change, dy ->
                    change.consume()
                    scope.launch { dragY.snapTo((dragY.value + dy).coerceAtLeast(0f)) }
                }
            },
    ) {
        val wide = maxWidth > maxHeight
        // Tło: rozmyta okładka + gradient w kolorze akcentu.
        if (s.blurBackground) {
            Artwork(
                track,
                Modifier.fillMaxSize().scale(1.3f).then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(60.dp) else Modifier).graphicsLayer { alpha = 0.55f },
                size = 300, shape = RoundedCornerShape(0.dp), crossfade = true,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f)),
                ),
            ),
        )

        if (track == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("Nic nie jest odtwarzane"), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(tr("Wybierz utwór z biblioteki"), color = Color.White.copy(alpha = 0.7f))
            }
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Pasek górny
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.nowPlayingOpen = false }) { Icon(Icons.Rounded.KeyboardArrowDown, tr("Zamknij"), tint = Color.White, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Teraz odtwarzane").uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 2)
                    Text(track?.album ?: "", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, tr("Więcej"), tint = Color.White) }
                    TrackMenu(vm, track.takeIf { menu }, { menu = false })
                }
            }

            if (wide) {
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        CoverOrLyrics(vm, track, ps.isPlaying, live, s.visualizer, accent)
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        PlayerControls(vm, track, ps, pos, wave, info, live, s.seekStyle, s.visualizer, accent, data.favorites, { sleepDialog = true }, { speedDialog = true }, { details = true })
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                    CoverOrLyrics(vm, track, ps.isPlaying, live, s.visualizer, accent)
                }
                PlayerControls(vm, track, ps, pos, wave, info, live, s.seekStyle, s.visualizer, accent, data.favorites, { sleepDialog = true }, { speedDialog = true }, { details = true })
            }
        }
    }

    if (vm.queueOpen) QueueSheet(vm)
    if (sleepDialog) SleepTimerDialog { sleepDialog = false }
    if (speedDialog) SpeedDialog(vm) { speedDialog = false }
    if (details) track?.let { TrackDetailsDialog(it) { details = false } }
}

@Composable
private fun CoverOrLyrics(
    vm: MainViewModel,
    track: Track?,
    playing: Boolean,
    live: LiveSpectrum,
    style: VisualizerStyle,
    accent: Color,
) {
    AnimatedContent(vm.showLyrics, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "lyr") { lyrics ->
        if (lyrics) {
            LyricsPanel(vm, Modifier.fillMaxSize())
        } else {
            val scale by animateFloatAsState(if (playing) 1f else 0.86f, spring(dampingRatio = 0.6f, stiffness = 200f), label = "cov")
            var swipe by remember { mutableStateOf(0f) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (swipe < -120) vm.player.next() else if (swipe > 120) vm.player.previous()
                                    swipe = 0f
                                },
                            ) { c, dx -> c.consume(); swipe += dx }
                        }
                        .clickable { vm.showLyrics = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (style == VisualizerStyle.CIRCLE) CircleSpectrum(live, accent, Modifier.fillMaxSize(), innerFraction = 0.80f)
                    val coverFrac = if (style == VisualizerStyle.CIRCLE) 0.74f else 0.94f
                    Artwork(
                        track,
                        Modifier
                            .fillMaxWidth(coverFrac)
                            .aspectRatio(1f)
                            .graphicsLayer { translationX = swipe * 0.4f; rotationZ = swipe / 60f }
                            .scale(scale)
                            .shadow(28.dp, if (style == VisualizerStyle.CIRCLE) CircleShape else RoundedCornerShape(28.dp), spotColor = accent),
                        size = 1000,
                        shape = if (style == VisualizerStyle.CIRCLE) CircleShape else RoundedCornerShape(28.dp),
                        crossfade = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    vm: MainViewModel,
    track: Track?,
    ps: com.blacksingularities.openamp.playback.PlayerUiState,
    pos: Long,
    wave: FloatArray?,
    info: FileInfo?,
    live: LiveSpectrum,
    seekStyle: SeekStyle,
    visualizer: VisualizerStyle,
    accent: Color,
    favorites: Set<Long>,
    onSleep: () -> Unit,
    onSpeed: () -> Unit,
    onInfo: () -> Unit,
) {
    val sleepAt by Graph.sleepAt.collectAsState()
    val sleepEnd by Graph.sleepEndOfTrack.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // Wizualizacja widma nad tytułem
        when (visualizer) {
            VisualizerStyle.BARS -> SpectrumBars(live, accent, Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp))
            VisualizerStyle.WAVE -> WaveScope(live, accent, Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp))
            else -> Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track?.title ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 1, modifier = Modifier.basicMarquee(),
                )
                Text(
                    track?.artist ?: "", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1, modifier = Modifier.basicMarquee().clickable(enabled = track != null) {
                        vm.nowPlayingOpen = false; vm.open(Route.Detail(DetailKind.ARTIST, track!!.artist.lowercase()))
                    },
                )
            }
            val fav = track != null && track.id in favorites
            IconButton(onClick = { track?.let { Graph.userData.toggleFavorite(it.id) } }) {
                Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, tr("Ulubione"), tint = if (fav) accent else Color.White, modifier = Modifier.size(28.dp))
            }
        }
        // Metadane techniczne
        if (track != null) {
            val parts = buildList {
                add(info?.format ?: track.format)
                info?.sampleRate?.takeIf { it > 0 }?.let { add(String.format(Locale.ROOT, "%.1f kHz", it / 1000f).replace(".0 ", " ")) }
                info?.bitDepth?.takeIf { it > 0 }?.let { add("$it-bit") }
                info?.bitrateKbps?.takeIf { it > 0 }?.let { add("$it kbps") }
                info?.channels?.takeIf { it > 0 }?.let { add(if (it == 1) tr("Mono") else if (it == 2) tr("Stereo") else "${it}ch") }
                if (track.year > 0) add(track.year.toString())
            }
            Row(Modifier.padding(top = 8.dp).clickable(onClick = onInfo), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                parts.take(5).forEach { p ->
                    Surface(shape = RoundedCornerShape(6.dp), color = Color.White.copy(alpha = 0.12f)) {
                        Text(p, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Taśma / pasek postępu
        when (seekStyle) {
            SeekStyle.WAVEFORM -> WaveformTape(wave, pos, ps.durationMs, accent, live, { vm.player.seekTo(it) })
            SeekStyle.SPECTROGRAM -> SpectrogramTape(wave, pos, ps.durationMs, accent, live, { vm.player.seekTo(it) })
            SeekStyle.SIMPLE -> SimpleSeek(pos, ps.durationMs, { vm.player.seekTo(it) })
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(pos), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.weight(1f))
            Text("-" + formatTime((ps.durationMs - pos).coerceAtLeast(0)), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.width(8.dp))
            Text(formatTime(ps.durationMs), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        }
        // Główne przyciski
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.player.toggleShuffle() }) {
                Icon(Icons.Rounded.Shuffle, tr("Losowa kolejność"), tint = if (ps.shuffle) accent else Color.White.copy(alpha = 0.7f))
            }
            IconButton(onClick = { vm.player.previous() }, modifier = Modifier.size(60.dp)) {
                Icon(Icons.Rounded.SkipPrevious, tr("Poprzedni"), tint = Color.White, modifier = Modifier.size(42.dp))
            }
            val pressScale by animateFloatAsState(if (ps.isPlaying) 1f else 1.06f, spring(), label = "pp")
            Surface(
                onClick = { vm.player.toggle() },
                shape = RoundedCornerShape(if (ps.isPlaying) 26.dp else 40.dp),
                color = accent,
                shadowElevation = 12.dp,
                modifier = Modifier.size(80.dp).scale(pressScale),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(ps.isPlaying, label = "pi") { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) tr("Pauza") else tr("Odtwórz"),
                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp),
                        )
                    }
                }
            }
            IconButton(onClick = { vm.player.next() }, modifier = Modifier.size(60.dp)) {
                Icon(Icons.Rounded.SkipNext, tr("Następny"), tint = Color.White, modifier = Modifier.size(42.dp))
            }
            IconButton(onClick = { vm.player.cycleRepeat() }) {
                Icon(
                    if (ps.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, tr("Powtarzanie"),
                    tint = if (ps.repeat != RepeatMode.OFF) accent else Color.White.copy(alpha = 0.7f),
                )
            }
        }
        // Przyciski dodatkowe
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            SmallAction(Icons.Rounded.Lyrics, tr("Tekst"), vm.showLyrics, accent) { vm.showLyrics = !vm.showLyrics }
            SmallAction(Icons.Rounded.Tune, tr("Korektor"), false, accent) { vm.nowPlayingOpen = false; vm.open(Route.Equalizer) }
            SmallAction(Icons.Rounded.Speed, if (ps.speed != 1f) String.format(Locale.ROOT, "%.2gx", ps.speed) else tr("Szybkość"), ps.speed != 1f || ps.pitch != 1f, accent, onSpeed)
            val sleeping = sleepAt != null || sleepEnd
            SmallAction(Icons.Rounded.Bedtime, sleepLabel(sleepAt, sleepEnd), sleeping, accent, onSleep)
            SmallAction(Icons.AutoMirrored.Rounded.QueueMusic, tr("Kolejka"), false, accent) { vm.queueOpen = true }
        }
    }
}

@Composable
private fun sleepLabel(at: Long?, endOfTrack: Boolean): String {
    if (endOfTrack) return tr("Do końca utworu")
    if (at == null) return tr("Wyłącznik czasowy")
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(at) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    return formatTime(at - now)
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp).width(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = if (active) accent else Color.White.copy(alpha = 0.8f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) accent else Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QueueSheet(vm: MainViewModel) {
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
private fun SleepTimerDialog(onDismiss: () -> Unit) {
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
private fun SpeedDialog(vm: MainViewModel, onDismiss: () -> Unit) {
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
            .padding(horizontal = 10.dp, vertical = 8.dp)
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 10.dp,
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
