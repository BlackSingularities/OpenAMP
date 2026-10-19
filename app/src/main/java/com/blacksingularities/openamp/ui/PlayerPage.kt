package com.blacksingularities.openamp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.CoverShape
import com.blacksingularities.openamp.data.RepeatMode
import com.blacksingularities.openamp.data.SeekStyle
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.data.VisualizerStyle
import com.blacksingularities.openamp.i18n.tr
import java.util.Locale
import kotlin.math.max

/** Ciemna, półprzezroczysta pigułka na tle okładki (napisy i przyciski ekranu odtwarzania). */
private val Glassy = Color(0x59000000)

@Composable
fun NowPlayingScreen(vm: MainViewModel, bottomPadding: Dp) {
    val track by vm.currentTrack.collectAsState()
    val ps by vm.player.state.collectAsState()
    val pos by vm.player.position.collectAsState()
    val s by vm.settings.collectAsState()
    val wave by vm.waveform.collectAsState()
    val info by vm.fileInfo.collectAsState()
    val data by vm.userData.collectAsState()
    val accent = MaterialTheme.colorScheme.primary
    val live = rememberLiveSpectrum(ps.isPlaying, keepHistory = if (s.seekStyle == SeekStyle.SPECTROGRAM) 200 else 0)
    var menu by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    val screenOn = s.keepScreenOn || (s.ambient && ps.isPlaying)
    androidx.compose.runtime.DisposableEffect(screenOn) {
        view.keepScreenOn = screenOn
        onDispose { view.keepScreenOn = false }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        NowPlayingBackground(track, s.npBackground, accent, live)
        val wide = maxWidth > maxHeight
        val cover: @Composable (Modifier) -> Unit = { m -> CoverBlock(vm, track, ps.isPlaying, live, s.visualizer, s.coverShape, s.coverGestures, s.showLikes, data.favorites, data.disliked, m) }
        val controls: @Composable () -> Unit = {
            Column {
                TitlePills(vm, track) { menu = true }
                Gap(10.dp)
                ActionRow(vm, ps, s.visualizer, { speedDialog = true })
                Gap(6.dp)
                SeekArea(vm, ps, pos, wave, live, s.seekStyle, accent)
            }
        }
        val techH = if (s.showTechInfo && track != null) 26.dp else 0.dp
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding + 2.dp)) { TechLine(track, info, s.showTechInfo) { details = true } }
        Box(Modifier.statusBarsPadding().padding(bottom = bottomPadding + techH).fillMaxSize()) {
            if (wide) {
                Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    cover(Modifier.fillMaxHeight().aspectRatio(1f))
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f)) { controls() }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)) {
                    cover(Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(1f, matchHeightConstraintsFirst = true).align(Alignment.CenterHorizontally))
                    Gap(14.dp)
                    controls()
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding()) { TrackMenu(vm, track.takeIf { menu }, { menu = false }) }
    }

    if (vm.queueOpen) QueueSheet(vm)
    if (vm.sleepDialog) SleepTimerDialog { vm.sleepDialog = false }
    if (speedDialog) SpeedDialog(vm) { speedDialog = false }
    if (details) track?.let { TrackDetailsDialog(it) { details = false } }
}

/** Okładka (albo tekst utworu) z przyciskami „lubię / nie lubię” i kolejką na dole. */
@Composable
private fun CoverBlock(
    vm: MainViewModel,
    track: Track?,
    playing: Boolean,
    live: LiveSpectrum,
    vis: VisualizerStyle,
    shapeMode: CoverShape,
    gestures: Boolean,
    showLikes: Boolean,
    favorites: Set<Long>,
    disliked: Set<Long>,
    modifier: Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    var swipe by remember { mutableFloatStateOf(0f) }
    val scale by animateFloatAsState(if (playing) 1f else 0.94f, spring(dampingRatio = 0.6f, stiffness = 240f), label = "cov")
    val round = vis == VisualizerStyle.CIRCLE || shapeMode == CoverShape.CIRCLE || shapeMode == CoverShape.VINYL
    val shape = when {
        round -> CircleShape
        shapeMode == CoverShape.SQUARE -> RoundedCornerShape(6.dp)
        else -> RoundedCornerShape(26.dp)
    }
    Box(modifier) {
        AnimatedContent(vm.showLyrics, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) }, label = "lyr") { lyrics ->
            if (lyrics) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = 0.55f))) {
                    LyricsPanel(vm, Modifier.fillMaxSize())
                    RoundButton(Icons.Rounded.Image, tr("Okładka"), { vm.showLyrics = false }, Modifier.align(Alignment.BottomEnd).padding(8.dp), background = Glassy, size = 38.dp, iconSize = 20.dp)
                }
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(gestures) {
                            if (!gestures) return@pointerInput
                            detectHorizontalDragGestures(onDragEnd = {
                                if (swipe < -120) vm.player.next() else if (swipe > 120) vm.player.previous()
                                swipe = 0f
                            }) { c, dx -> c.consume(); swipe += dx }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { vm.goTab(Route.Library) },
                                onDoubleTap = { o -> if (o.x < size.width / 2) vm.player.seekTo((vm.player.position.value - 10_000).coerceAtLeast(0)) else vm.player.seekTo(vm.player.position.value + 10_000) },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (vis == VisualizerStyle.CIRCLE) CircleSpectrum(live, accent, Modifier.fillMaxSize(), innerFraction = 0.80f)
                    val m = Modifier
                        .fillMaxSize(if (vis == VisualizerStyle.CIRCLE) 0.76f else 1f)
                        .graphicsLayer { translationX = swipe * 0.4f; rotationZ = swipe / 70f; scaleX = scale; scaleY = scale }
                        .shadow(24.dp, shape)
                    if (shapeMode == CoverShape.VINYL) Vinyl(track, playing, accent, m) else Artwork(track, m, size = 1000, shape = shape, crossfade = true)
                    if (vis == VisualizerStyle.BARS) SpectrumBars(live, Color.White.copy(alpha = 0.85f), Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.8f).height(70.dp).padding(bottom = 58.dp), mirror = false)
                    if (vis == VisualizerStyle.WAVE) WaveScope(live, Color.White, Modifier.align(Alignment.Center).fillMaxWidth(0.9f).height(90.dp))
                }
            }
        }
        if (!vm.showLyrics) {
            val fav = track != null && track.id in favorites
            val dis = track != null && track.id in disliked
            if (showLikes) Row(Modifier.align(Alignment.BottomStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                RoundButton(if (fav) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt, tr("Lubię"), { track?.let { Graph.userData.toggleFavorite(it.id) } }, background = Glassy, active = fav, size = 36.dp, iconSize = 18.dp)
                RoundButton(if (dis) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt, tr("Nie lubię – pomiń i nie proponuj"), {
                    track?.let {
                        val wasDisliked = it.id in disliked
                        Graph.userData.toggleDislike(it.id)
                        if (!wasDisliked) { vm.player.next(); vm.message(tr("Ten utwór nie trafi do miksów ani sprytnego mieszania")) }
                    }
                }, background = Glassy, active = dis, size = 36.dp, iconSize = 18.dp)
            }
            RoundButton(Icons.Rounded.Lyrics, tr("Tekst utworu"), { vm.showLyrics = true }, Modifier.align(Alignment.BottomEnd).padding(8.dp), background = Glassy, size = 38.dp, iconSize = 20.dp)
        }
    }
}

@Composable
private fun TitlePills(vm: MainViewModel, track: Track?, onMenu: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            track?.title ?: tr("Nic nie jest odtwarzane"),
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.reactive(tr("Więcej"), onLongPress = onMenu) { onMenu() }.clip(RoundedCornerShape(50)).background(Glassy).padding(horizontal = 16.dp, vertical = 6.dp),
        )
        if (track != null) Text(
            "${track.artist} - ${track.album}",
            color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.reactive(tr("Przejdź do wykonawcy"), onLongPress = { vm.goTab(Route.Library); vm.open(Route.Detail(DetailKind.ALBUM, track.albumId.toString())) }) {
                vm.goTab(Route.Library); vm.open(Route.Detail(DetailKind.ARTIST, track.artist.lowercase()))
            }.clip(RoundedCornerShape(50)).background(Glassy).padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ActionRow(vm: MainViewModel, ps: com.blacksingularities.openamp.playback.PlayerUiState, vis: VisualizerStyle, onSpeed: () -> Unit) {
    val sleepAt by Graph.sleepAt.collectAsState()
    val sleepEnd by Graph.sleepEndOfTrack.collectAsState()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        val pill = Modifier
        WidePill(Icons.Rounded.GraphicEq, tr("Wizualizacja") + ": " + visLabel(vis), vis != VisualizerStyle.NONE, pill) {
            val next = VisualizerStyle.entries[(vis.ordinal + 1) % VisualizerStyle.entries.size]
            vm.updateSettings { it.copy(visualizer = next) }
            vm.message(tr("Wizualizacja") + ": " + visLabel(next))
        }
        WidePill(Icons.Rounded.Bedtime, tr("Wyłącznik czasowy"), sleepAt != null || sleepEnd, pill, onLong = onSpeed) { vm.sleepDialog = true }
        Text(
            if (ps.queue.isNotEmpty()) "${ps.index + 1} / ${ps.queue.size}" else "–",
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.reactive(tr("Kolejka")) { vm.queueOpen = true }.clip(RoundedCornerShape(50)).background(Glassy).padding(horizontal = 12.dp, vertical = 7.dp),
        )
        WidePill(if (ps.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, tr("Powtarzanie"), ps.repeat != RepeatMode.OFF, pill) { vm.player.cycleRepeat() }
        WidePill(Icons.Rounded.Shuffle, tr("Losowa kolejność"), ps.shuffle, pill) { vm.player.toggleShuffle() }
    }
    if (ps.speed != 1f || ps.pitch != 1f) {
        Row(Modifier.padding(top = 6.dp)) {
            PillButton(String.format(Locale.ROOT, "%.2fx", ps.speed), onSpeed, icon = Icons.Rounded.Speed, background = Glassy, label = tr("Szybkość i wysokość"))
        }
    }
}

@Composable
private fun WidePill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, modifier: Modifier, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier.size(48.dp).reactive(label, onLongPress = onLong, onClick = onClick).clip(CircleShape).background(if (active) accent else Glassy),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp)) }
}

/**
 * Pole przewijania: fala dźwiękowa całego utworu (lub taśma / spektrogram / pasek) z nałożonymi
 * przyciskami sterowania – jak w klasycznych odtwarzaczach. Przeciąganie w poziomie przewija.
 */
@Composable
private fun SeekArea(
    vm: MainViewModel,
    ps: com.blacksingularities.openamp.playback.PlayerUiState,
    pos: Long,
    wave: FloatArray?,
    live: LiveSpectrum,
    style: SeekStyle,
    accent: Color,
) {
    Box(Modifier.fillMaxWidth().heightIn(min = 150.dp).height(170.dp), contentAlignment = Alignment.Center) {
        when (style) {
            SeekStyle.WAVEFORM -> FullWaveform(wave, pos, ps.durationMs, accent, live) { vm.player.seekTo(it) }
            SeekStyle.TAPE -> WaveformTape(wave, pos, ps.durationMs, accent, live, { vm.player.seekTo(it) }, Modifier.fillMaxWidth())
            SeekStyle.SPECTROGRAM -> SpectrogramTape(wave, pos, ps.durationMs, accent, live, { vm.player.seekTo(it) })
            SeekStyle.SIMPLE -> SimpleSeek(pos, ps.durationMs, { vm.player.seekTo(it) }, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
        // Przyciski na fali
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundButton(Icons.Rounded.FastRewind, tr("−10 s"), { vm.player.seekTo((pos - 10_000).coerceAtLeast(0)) }, background = Color.Black.copy(alpha = 0.35f), tint = Color.White.copy(alpha = 0.6f), size = 50.dp)
            RoundButton(Icons.Rounded.SkipPrevious, tr("Poprzedni"), { vm.player.previous() }, background = Color.Black, size = 62.dp, iconSize = 32.dp)
            RoundButton(if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (ps.isPlaying) tr("Pauza") else tr("Odtwórz"), { vm.player.toggle() }, background = Color.Black, size = 84.dp, iconSize = 46.dp)
            RoundButton(Icons.Rounded.SkipNext, tr("Następny"), { vm.player.next() }, background = Color.Black, size = 62.dp, iconSize = 32.dp)
            RoundButton(Icons.Rounded.FastForward, tr("+10 s"), { vm.player.seekTo((pos + 10_000).coerceAtMost(ps.durationMs)) }, background = Color.Black.copy(alpha = 0.35f), tint = Color.White.copy(alpha = 0.6f), size = 50.dp)
        }
        Text(formatTime(pos), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).clip(RoundedCornerShape(50)).background(Color.Black).padding(horizontal = 8.dp, vertical = 3.dp))
        Text(formatTime(ps.durationMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(50)).background(Color.Black).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** Fala całego utworu: część odtworzona jasna, reszta przygaszona; dotyk/przeciągnięcie przewija. */
@Composable
private fun FullWaveform(wave: FloatArray?, pos: Long, duration: Long, accent: Color, live: LiveSpectrum, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur by rememberUpdatedState(duration)
    Canvas(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { onSeek((it * dur).toLong()) }; drag = null },
                    onDragCancel = { drag = null },
                ) { c, _ -> c.consume(); drag = (c.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        @Suppress("UNUSED_VARIABLE") val t = live.tick
        val bars = 64
        val gap = size.width / bars * 0.35f
        val w = size.width / bars - gap
        val p = drag ?: if (duration > 0) pos.toFloat() / duration else 0f
        val mid = size.height / 2
        val src = wave
        for (i in 0 until bars) {
            val v = if (src != null) {
                val a = i * src.size / bars
                val b = max(a + 1, (i + 1) * src.size / bars)
                var m = 0f
                for (k in a until b.coerceAtMost(src.size)) m = max(m, src[k])
                m
            } else 0.25f
            val frac = (i + 0.5f) / bars
            val boost = if (kotlin.math.abs(frac - p) < 0.03f) live.level * 0.3f else 0f
            val h = max(4.dp.toPx(), (v + boost).coerceAtMost(1f) * (size.height / 2 - 6.dp.toPx()))
            val played = frac <= p
            val color = if (played) Color.White else Color.White.copy(alpha = 0.38f)
            drawRoundRect(color, Offset(i * (w + gap) + gap / 2, mid - h), Size(w, h * 2), CornerRadius(w / 2))
        }
        if (drag != null) drawLine(accent, Offset(p * size.width, 0f), Offset(p * size.width, size.height), strokeWidth = 2.dp.toPx())
    }
    if (drag != null) Text(formatTime(((drag ?: 0f) * duration).toLong()), color = Color.Black, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
private fun TechLine(track: Track?, info: FileInfo?, show: Boolean, onClick: () -> Unit) {
    if (!show || track == null) return
    val parts = buildList {
        info?.sampleRate?.takeIf { it > 0 }?.let { add(String.format(Locale.ROOT, "%.1f kHz", it / 1000f).replace(".0 ", " ")) }
        info?.bitDepth?.takeIf { it > 0 }?.let { add("$it-bit") }
        info?.bitrateKbps?.takeIf { it > 0 }?.let { add("$it kb/s") }
        add(info?.format ?: track.format)
    }
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            parts.joinToString("  •  "), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.reactive(tr("Informacje o pliku"), onClick = onClick).clip(RoundedCornerShape(50)).background(Glassy).padding(horizontal = 12.dp, vertical = 4.dp).widthIn(max = 320.dp),
        )
    }
}
