package com.blacksingularities.openamp.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.AodMode
import com.blacksingularities.openamp.data.LockBackground
import com.blacksingularities.openamp.data.RepeatMode
import com.blacksingularities.openamp.data.ThemeMode
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.playback.PlayerConnection
import com.blacksingularities.openamp.ui.Artwork
import com.blacksingularities.openamp.ui.RoundButton
import com.blacksingularities.openamp.ui.formatTime
import com.blacksingularities.openamp.ui.onAnyTouch
import com.blacksingularities.openamp.ui.rememberArtwork
import com.blacksingularities.openamp.ui.theme.OpenAmpTheme
import com.blacksingularities.openamp.ui.theme.vibrantColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Ekran blokady OpenAMP: pełny odtwarzacz nad blokadą (okładka, zegar, przewijanie, sterowanie)
 * i uproszczony tryb „always on” – po chwili bez dotyku czarny ekran z przygaszonym zegarem
 * i tytułem, przesuwany co pół minuty przeciw wypaleniu. Przesunięcie w górę wraca do blokady.
 */
class LockscreenActivity : ComponentActivity() {
    private lateinit var conn: PlayerConnection

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Telefon odblokowany innym sposobem (np. odciskiem palca) – ekran nie jest już potrzebny.
            if (intent.action == Intent.ACTION_USER_PRESENT) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        LockscreenLauncher.cancel(this)
        androidx.core.content.ContextCompat.registerReceiver(
            this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        conn = PlayerConnection(this).also { it.connect() }
        setContent {
            OpenAmpTheme(Graph.settings.value.accent, ThemeMode.BLACK) {
                LockScreen(conn, onUnlock = { finish() }, onAod = ::applyAod)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LockscreenLauncher.cancel(this)
    }

    /** Tryb AOD: ekran nie gaśnie, a jasność spada do minimum. */
    private fun applyAod(on: Boolean, dim: Boolean, keepOn: Boolean) {
        val lp = window.attributes
        lp.screenBrightness = if (on && dim) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        if (on && keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlockReceiver) }
        conn.release()
        super.onDestroy()
    }
}

private fun isCharging(context: Context): Boolean =
    context.getSystemService(BatteryManager::class.java)?.isCharging == true

@Composable
private fun LockScreen(conn: PlayerConnection, onUnlock: () -> Unit, onAod: (Boolean, Boolean, Boolean) -> Unit) {
    val context = LocalContext.current
    val s by Graph.settings.state.collectAsState()
    val ps by conn.state.collectAsState()
    val data by Graph.userData.state.collectAsState()
    val tracks by Graph.library.tracks.collectAsState()
    val track = remember(ps.trackId, tracks) { ps.trackId?.let { Graph.library.get(it) } }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var aod by remember { mutableStateOf(false) }

    // Przejście w tryb AOD po chwili bez dotyku (gdy włączony i – opcjonalnie – podczas ładowania).
    LaunchedEffect(s.aodMode, s.aodTimeoutSec, ps.isPlaying, lastTouch) {
        aod = false
        if (s.aodMode == AodMode.OFF || !ps.isPlaying) return@LaunchedEffect
        if (s.aodMode == AodMode.CHARGING && !isCharging(context)) return@LaunchedEffect
        delay(s.aodTimeoutSec * 1000L)
        aod = true
    }
    // Ekran trzymany włączony tylko podczas grania; po 2 min pauzy w AOD pozwalamy mu zgasnąć.
    var keepOn by remember { mutableStateOf(true) }
    LaunchedEffect(aod, ps.isPlaying) {
        keepOn = true
        if (aod && !ps.isPlaying) { delay(120_000); keepOn = false }
    }
    LaunchedEffect(aod, keepOn, s.aodDim) { onAod(aod, s.aodDim, keepOn) }
    // Nic nie gra (odtwarzacz zamknięty) – ekran blokady OpenAMP znika.
    LaunchedEffect(ps.connected, ps.queue.isEmpty()) {
        if (ps.connected && ps.queue.isEmpty()) { delay(1500); onUnlock() }
    }

    val density = LocalDensity.current
    var drag by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onAnyTouch { lastTouch = System.currentTimeMillis() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag < -with(density) { 140.dp.toPx() }) onUnlock(); drag = 0f },
                    onDragCancel = { drag = 0f },
                ) { _, dy -> drag += dy }
            },
    ) {
        AnimatedContent(aod, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(300)) }, label = "aod") { inAod ->
            if (inAod) AodView(conn, track, s.aodArt) { lastTouch = System.currentTimeMillis() }
            else FullView(conn, track, s, track?.id?.let { it in data.favorites } == true, drag)
        }
    }
}

@Composable
private fun Clock(big: Boolean, alpha: Float = 1f) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha)) {
        Text(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
            color = Color.White, fontSize = if (big) 76.sp else 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp,
        )
        Text(
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now).replaceFirstChar { it.uppercase() },
            color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp,
        )
    }
}

@Composable
private fun FullView(
    conn: PlayerConnection,
    track: com.blacksingularities.openamp.data.Track?,
    s: com.blacksingularities.openamp.data.AppSettings,
    favorite: Boolean,
    drag: Float,
) {
    val ps by conn.state.collectAsState()
    val pos by conn.position.collectAsState()
    val art = rememberArtwork(track, 1024)
    val artColor = remember(art) { art?.let { runCatching { vibrantColor(it) }.getOrNull() } }
    val accent = if (artColor != null) Color(artColor) else MaterialTheme.colorScheme.primary
    val image = remember(art) { art?.asImageBitmap() }
    val glass = accent.copy(alpha = 0.22f)
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        // Tło
        when (s.lockBackground) {
            LockBackground.BLUR -> {
                if (image != null) Image(
                    image, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(70.dp) else Modifier),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f)))))
            }
            LockBackground.ART_COLOR -> Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.7f), accent.copy(alpha = 0.25f).compositeOver(Color.Black), Color.Black))),
            )
            LockBackground.BLACK -> Box(
                Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), radius = 1400f)),
            )
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)
                .offset(y = with(density) { (drag / 3f).coerceAtMost(0f).toDp() }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            if (s.lockClock) Clock(big = true)
            Spacer(Modifier.weight(1f))
            Artwork(
                track, Modifier.fillMaxWidth(if (s.lockClock) 0.72f else 0.86f).aspectRatio(1f)
                    .shadow(40.dp, RoundedCornerShape(28.dp), ambientColor = accent, spotColor = accent),
                size = 1024, shape = RoundedCornerShape(28.dp), crossfade = true,
            )
            Spacer(Modifier.height(22.dp))
            Text(
                track?.title ?: tr("Nic nie jest odtwarzane"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Text(
                track?.artist ?: "", color = lerp(accent, Color.White, 0.45f), fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
            )
            if (!track?.album.isNullOrBlank()) Text(
                track?.album ?: "", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )

            if (s.lockSeek && ps.durationMs > 0) {
                var seeking by remember { mutableStateOf<Float?>(null) }
                val frac = seeking ?: (pos.toFloat() / ps.durationMs).coerceIn(0f, 1f)
                Slider(
                    value = frac,
                    onValueChange = { seeking = it },
                    onValueChangeFinished = { seeking?.let { conn.seekTo((it * ps.durationMs).toLong()) }; seeking = null },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = accent, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(formatTime((frac * ps.durationMs).toLong()), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(formatTime(ps.durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            } else Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (s.lockShuffleRepeat) RoundButton(Icons.Rounded.Shuffle, tr("Losowo"), { conn.toggleShuffle() }, active = ps.shuffle, background = glass)
                RoundButton(Icons.Rounded.SkipPrevious, tr("Poprzedni"), { conn.previous() }, size = 56.dp, iconSize = 30.dp, background = glass)
                RoundButton(
                    if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (ps.isPlaying) tr("Pauza") else tr("Odtwórz"),
                    { conn.toggle() }, size = 76.dp, iconSize = 40.dp, background = accent,
                    tint = if (accent.luminance() > 0.5f) Color.Black else Color.White,
                )
                RoundButton(Icons.Rounded.SkipNext, tr("Następny"), { conn.next() }, size = 56.dp, iconSize = 30.dp, background = glass)
                if (s.lockShuffleRepeat) RoundButton(
                    if (ps.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, tr("Powtarzanie"),
                    { conn.cycleRepeat() }, active = ps.repeat != RepeatMode.OFF, background = glass,
                )
                if (s.lockLike && track != null) RoundButton(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (favorite) tr("Usuń z ulubionych") else tr("Dodaj do ulubionych"),
                    { Graph.userData.toggleFavorite(track.id) }, background = glass,
                    tint = if (favorite) accent else Color.White,
                )
            }

            if (s.lockNext) {
                val next = ps.nextId?.let { Graph.library.get(it) }
                if (next != null) Text(
                    tr("Następnie: {0} — {1}", next.title, next.artist), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(26.dp))
            Text(tr("Przesuń w górę, aby odblokować"), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AodView(conn: PlayerConnection, track: com.blacksingularities.openamp.data.Track?, showArt: Boolean, onWake: () -> Unit) {
    val ps by conn.state.collectAsState()
    val pos by conn.position.collectAsState()
    val art = rememberArtwork(track, 300)
    val artColor = remember(art) { art?.let { runCatching { vibrantColor(it) }.getOrNull() } }
    val tint = artColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val next = ps.nextId?.let { Graph.library.get(it) }
    // Treść wędruje po ekranie przeciw wypaleniu – w granicach wyliczonych z jej rozmiaru.
    var target by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            target = androidx.compose.ui.geometry.Offset(Random.nextFloat() * 2 - 1, Random.nextFloat() * 2 - 1)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { onWake() } }) {
        var content by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val density = LocalDensity.current
        val rangeX = with(density) { ((constraints.maxWidth - content.width) / 2 - 16.dp.roundToPx()).coerceAtLeast(0).toDp() }
        val rangeY = with(density) { ((constraints.maxHeight - content.height) / 2 - 40.dp.roundToPx()).coerceAtLeast(0).toDp() }
        val ox by animateDpAsState(rangeX * target.x, tween(5000), label = "ox")
        val oy by animateDpAsState(rangeY * target.y, tween(5000), label = "oy")
        Column(
            Modifier.align(Alignment.Center).offset(ox, oy).widthIn(max = 300.dp).onSizeChanged { content = it },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Clock(big = false, alpha = 0.7f)
            if (showArt && track != null) {
                Box(Modifier.padding(top = 18.dp).size(150.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2
                        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.28f), Color.Transparent), center, r), r)
                        val st = 2.5.dp.toPx()
                        val inset = 10.dp.toPx()
                        val sz = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                        drawArc(Color.White.copy(alpha = 0.07f), -90f, 360f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(st))
                        val f = if (ps.durationMs > 0) (pos.toFloat() / ps.durationMs).coerceIn(0f, 1f) else 0f
                        drawArc(tint.copy(alpha = 0.75f), -90f, 360f * f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(st, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Artwork(track, Modifier.size(98.dp).alpha(0.6f), size = 300, shape = RoundedCornerShape(20.dp))
                }
            }
            Text(
                track?.title ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp),
            )
            Text(track?.artist ?: "", color = tint.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!showArt && ps.durationMs > 0) {
                val f = (pos.toFloat() / ps.durationMs).coerceIn(0f, 1f)
                Box(Modifier.padding(top = 12.dp).widthIn(max = 160.dp).fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0x22FFFFFF))) {
                    Box(Modifier.fillMaxWidth(f).height(2.dp).background(tint.copy(alpha = 0.6f)))
                }
            }
            if (next != null) Text(
                tr("Następnie: {0} — {1}", next.title, next.artist), color = Color.White.copy(alpha = 0.28f), fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp),
            )
            if (!ps.isPlaying) Text(tr("Wstrzymano"), color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
