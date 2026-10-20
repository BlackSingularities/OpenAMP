package com.blacksingularities.openamp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
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
import androidx.compose.runtime.LaunchedEffect
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
                Gap(8.dp)
                ActionRow(vm, ps, s.visualizer, { speedDialog = true })
                Gap(4.dp)
                SeekArea(vm, ps, pos, wave, live, s.seekStyle, accent)
            }
        }
        val techH = if (s.showTechInfo && track != null) 26.dp else 0.dp
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding + 2.dp)) { TechLine(track, info, s.showTechInfo) { details = true } }
        Column(Modifier.statusBarsPadding().padding(bottom = bottomPadding + techH).fillMaxSize()) {
            PlayerTopBar(vm, ps, onMenu = { menu = true })
            Box(Modifier.weight(1f).fillMaxWidth()) {
            if (wide) {
                Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    cover(Modifier.fillMaxHeight().aspectRatio(1f))
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f)) { controls() }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp)) {
                    cover(Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(1f, matchHeightConstraintsFirst = true).align(Alignment.CenterHorizontally))
                    Gap(12.dp)
                    controls()
                }
            }
            }
        }
        Box(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 40.dp)) { TrackMenu(vm, track.takeIf { menu }, { menu = false }) }
    }

    if (vm.queueOpen) QueueSheet(vm)
    if (vm.sleepDialog) SleepTimerDialog { vm.sleepDialog = false }
    if (speedDialog) SpeedDialog(vm) { speedDialog = false }
    if (details) track?.let { TrackDetailsDialog(vm, it) { details = false } }
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
    val ps by vm.player.state.collectAsState()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val liveNext = ps.nextId?.let { Graph.library.get(it) }
    val livePrev = ps.prevId?.let { Graph.library.get(it) }
    // Sąsiedzi „zamrożeni” na czas gestu, a po zwolnieniu – utwór docelowy pokazywany od razu
    // (zanim odtwarzacz zgłosi zmianę), żeby okładki nie przeskakiwały.
    var frozenNext by remember { mutableStateOf<Track?>(null) }
    var frozenPrev by remember { mutableStateOf<Track?>(null) }
    var pending by remember { mutableStateOf<Track?>(null) }
    LaunchedEffect(track?.id) { if (pending != null && track?.id == pending?.id) pending = null }
    val shown = pending ?: track
    val nextTrack = frozenNext ?: liveNext
    val prevTrack = frozenPrev ?: livePrev
    val hasNext by rememberUpdatedState(ps.nextId != null)
    val hasPrev by rememberUpdatedState(ps.prevId != null)
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
                LyricsCard(vm, track)
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                        .pointerInput(gestures) {
                            if (!gestures) return@pointerInput
                            // Gest jak w karuzeli: okładka idzie za palcem, obok wysuwa się sąsiednia.
                            // Decyzja: przesunięcie > 30% szerokości albo szybki rzut (> 1100 px/s).
                            // Na końcu kolejki – opór „gumki”, przy przekroczeniu progu – haptyka.
                            val tracker = VelocityTracker()
                            var crossed = false
                            detectHorizontalDragGestures(
                                onDragStart = { tracker.resetTracking(); crossed = false; frozenNext = liveNext; frozenPrev = livePrev },
                                onDragCancel = { scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.7f)) } },
                                onDragEnd = {
                                    val v = tracker.calculateVelocity().x
                                    val o = offset.value
                                    val goNext = hasNext && (o < -width * 0.3f || (v < -1100f && o < -width * 0.04f))
                                    val goPrev = hasPrev && (o > width * 0.3f || (v > 1100f && o > width * 0.04f))
                                    scope.launch {
                                        if (goNext || goPrev) {
                                            val target = if (goNext) nextTrack else prevTrack
                                            val speed = kotlin.math.abs(v).coerceIn(1200f, 6000f)
                                            val left = (width - kotlin.math.abs(o)) / speed * 1000f
                                            offset.animateTo(if (goNext) -width else width, tween(left.toInt().coerceIn(90, 260)))
                                            // Ta sama klatka: okładka docelowa staje na środku, dopiero potem zmiana utworu.
                                            pending = target
                                            offset.snapTo(0f)
                                            frozenNext = null; frozenPrev = null
                                            if (goNext) vm.player.next() else vm.player.previousTrack()
                                            launch { delay(1500); pending = null }
                                        } else {
                                            offset.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 420f))
                                            frozenNext = null; frozenPrev = null
                                        }
                                    }
                                },
                            ) { c, dx ->
                                c.consume()
                                tracker.addPosition(c.uptimeMillis, c.position)
                                val raw = offset.value + dx
                                val blocked = (raw < 0 && !hasNext) || (raw > 0 && !hasPrev)
                                val target = if (blocked) offset.value + dx * 0.25f else raw
                                scope.launch { offset.snapTo(target) }
                                val over = !blocked && kotlin.math.abs(target) > width * 0.3f
                                if (over != crossed) { crossed = over; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            }
                        }
                        ,
                    contentAlignment = Alignment.Center,
                ) {
                    if (vis == VisualizerStyle.CIRCLE) CircleSpectrum(live, accent, Modifier.fillMaxSize(), innerFraction = 0.80f)
                    val frac = { (kotlin.math.abs(offset.value) / width).coerceIn(0f, 1f) }
                    val fill = if (vis == VisualizerStyle.CIRCLE) 0.76f else 1f
                    // Okładki sąsiadów (podgląd podczas przesuwania)
                    if (offset.value < 0f && nextTrack != null) Artwork(
                        nextTrack,
                        Modifier.fillMaxSize(fill).graphicsLayer {
                            translationX = offset.value + width + 24.dp.toPx(); val sc = 0.9f + 0.1f * frac(); scaleX = sc; scaleY = sc; alpha = 0.4f + 0.6f * frac()
                        },
                        size = 1000, shape = shape,
                    )
                    if (offset.value > 0f && prevTrack != null) Artwork(
                        prevTrack,
                        Modifier.fillMaxSize(fill).graphicsLayer {
                            translationX = offset.value - width - 24.dp.toPx(); val sc = 0.9f + 0.1f * frac(); scaleX = sc; scaleY = sc; alpha = 0.4f + 0.6f * frac()
                        },
                        size = 1000, shape = shape,
                    )
                    val m = Modifier
                        .fillMaxSize(fill)
                        .graphicsLayer {
                            translationX = offset.value
                            rotationZ = offset.value / width * 4f
                            val sc = scale * (1f - 0.08f * frac()); scaleX = sc; scaleY = sc
                        }
                        .shadow(24.dp, shape)
                    if (shapeMode == CoverShape.VINYL) Vinyl(shown, playing, accent, m) else Artwork(shown, m, size = 1000, shape = shape, crossfade = !gestures)
                    if (vis == VisualizerStyle.BARS) SpectrumBars(live, Color.White.copy(alpha = 0.85f), Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.8f).height(70.dp).padding(bottom = 58.dp), mirror = false)
                    if (vis == VisualizerStyle.WAVE) WaveScope(live, Color.White, Modifier.align(Alignment.Center).fillMaxWidth(0.9f).height(90.dp))
                }
            }
        }
        if (!vm.showLyrics) {
            val fav = track != null && track.id in favorites
            val dis = track != null && track.id in disliked
            if (showLikes) Row(Modifier.align(Alignment.BottomStart).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                RoundButton(if (fav) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt, tr("Lubię"), { track?.let { Graph.userData.toggleFavorite(it.id) } }, background = Glassy, active = fav, size = 36.dp, iconSize = 18.dp)
                RoundButton(if (dis) Icons.Rounded.ThumbDown else Icons.Rounded.ThumbDownOffAlt, tr("Nie lubię – pomiń i nie proponuj"), {
                    track?.let {
                        val wasDisliked = it.id in disliked
                        Graph.userData.toggleDislike(it.id)
                        if (!wasDisliked) { vm.player.next(); vm.message(tr("Ten utwór nie trafi do miksów ani sprytnego mieszania")) }
                    }
                }, background = Glassy, active = dis, size = 36.dp, iconSize = 18.dp)
            }
            RoundButton(Icons.Rounded.Lyrics, tr("Tekst utworu"), { vm.showLyrics = true }, Modifier.align(Alignment.BottomEnd).padding(18.dp), background = Glassy, size = 38.dp, iconSize = 20.dp)
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
 * Pole przewijania: taśma utworu w wybranym stylu z nałożonymi przyciskami sterowania.
 * Przeciągnięcie w poziomie w dowolnym miejscu pola – także zaczęte na przycisku – przewija;
 * dotknięcie wolnego miejsca przeskakuje (na taśmach przesuwanych: ±10 s po danej stronie głowicy).
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
    var scrub by remember { mutableStateOf<Float?>(null) }
    val dur by rememberUpdatedState(ps.durationMs)
    val posNow by rememberUpdatedState(pos)
    val styleNow by rememberUpdatedState(style)
    val points by rememberUpdatedState(wave?.size ?: 600)
    val haptic = LocalHapticFeedback.current
    val frac = scrub ?: if (ps.durationMs > 0) (pos.toFloat() / ps.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        Modifier.fillMaxWidth().height(164.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        if (dur > 0) { scrub = posNow.toFloat() / dur; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    },
                    onDragEnd = { scrub?.let { vm.player.seekTo((it * dur).toLong()) }; scrub = null },
                    onDragCancel = { scrub = null },
                ) { c, dx ->
                    if (dur <= 0) return@detectHorizontalDragGestures
                    c.consume()
                    val df = if (styleNow.scrolls) -dx / (points * TapeStep.toPx()) else dx / size.width
                    scrub = ((scrub ?: 0f) + df).coerceIn(0f, 1f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    if (dur <= 0) return@detectTapGestures
                    if (styleNow.scrolls) vm.player.seekTo((posNow + if (o.x < size.width / 2) -10_000 else 10_000).coerceIn(0, dur))
                    else vm.player.seekTo((o.x / size.width * dur).toLong().coerceIn(0, dur))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SeekTape(style, wave, (frac * ps.durationMs).toLong(), frac, live, accent, scrub != null, Modifier.fillMaxWidth().height(if (style == SeekStyle.CASSETTE) 120.dp else 132.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundButton(Icons.Rounded.FastRewind, tr("−10 s"), { vm.player.seekTo((pos - 10_000).coerceAtLeast(0)) }, background = Color.Black.copy(alpha = 0.35f), tint = Color.White.copy(alpha = 0.6f), size = 46.dp, iconSize = 22.dp)
            RoundButton(Icons.Rounded.SkipPrevious, tr("Poprzedni"), { vm.player.previous() }, background = Color.Black, size = 58.dp, iconSize = 30.dp)
            RoundButton(if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (ps.isPlaying) tr("Pauza") else tr("Odtwórz"), { vm.player.toggle() }, background = Color.Black, size = 78.dp, iconSize = 42.dp)
            RoundButton(Icons.Rounded.SkipNext, tr("Następny"), { vm.player.next() }, background = Color.Black, size = 58.dp, iconSize = 30.dp)
            RoundButton(Icons.Rounded.FastForward, tr("+10 s"), { vm.player.seekTo((pos + 10_000).coerceAtMost(ps.durationMs)) }, background = Color.Black.copy(alpha = 0.35f), tint = Color.White.copy(alpha = 0.6f), size = 46.dp, iconSize = 22.dp)
        }
        val shown = (frac * ps.durationMs).toLong()
        Text(formatTime(shown), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).clip(RoundedCornerShape(50)).background(Color.Black).padding(horizontal = 8.dp, vertical = 3.dp))
        Text("−" + formatTime((ps.durationMs - shown).coerceAtLeast(0)), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(50)).background(Color.Black).padding(horizontal = 8.dp, vertical = 3.dp))
        androidx.compose.animation.AnimatedVisibility(scrub != null, Modifier.align(Alignment.TopCenter), enter = fadeIn(tween(120)), exit = fadeOut(tween(200))) {
            Text(
                formatTime(shown) + " / " + formatTime(ps.durationMs), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** Górny pasek, celowo stonowany: po lewej przejście do listy odtwarzania, po prawej „więcej”. */
@Composable
private fun PlayerTopBar(vm: MainViewModel, ps: com.blacksingularities.openamp.playback.PlayerUiState, onMenu: () -> Unit) {
    val dim = Color.White.copy(alpha = 0.62f)
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.reactive(tr("Lista")) { vm.goTab(Route.Queue) }.clip(RoundedCornerShape(50)).padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ChevronLeft, null, tint = dim, modifier = Modifier.size(20.dp))
            Text(tr("Lista"), color = dim, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (ps.queue.isNotEmpty()) Text("  ${ps.index + 1}/${ps.queue.size}", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        RoundButton(Icons.Rounded.MoreVert, tr("Więcej"), onMenu, background = Color.Transparent, tint = dim, size = 34.dp, iconSize = 18.dp)
    }
}

/** Tekst utworu na rozmytej okładce, z małą miniaturą i własnym paskiem narzędzi (bez nakładania się). */
@Composable
private fun LyricsCard(vm: MainViewModel, track: Track?) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(Color.Black)) {
        Artwork(
            track, Modifier.fillMaxSize().scale(1.2f).then(if (android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(36.dp) else Modifier).graphicsLayer { alpha = 0.75f },
            size = 300, shape = RoundedCornerShape(0.dp), crossfade = true,
        )
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.8f)))))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track, Modifier.size(38.dp), size = 160, shape = RoundedCornerShape(9.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(track?.title ?: "", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track?.artist ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                RoundButton(Icons.Rounded.Image, tr("Okładka"), { vm.showLyrics = false }, background = Glassy, size = 34.dp, iconSize = 18.dp)
            }
            LyricsPanel(vm, Modifier.weight(1f).fillMaxWidth())
        }
    }
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
