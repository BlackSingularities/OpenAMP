package com.blacksingularities.openamp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.blacksingularities.openamp.i18n.tr
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.lyrics.Lyrics
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Rejestruje każdy dotyk (bez przechwytywania) – do wykrywania bezczynności. */
fun Modifier.onAnyTouch(onTouch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onTouch()
    }
}

/**
 * Inteligentny tryb AMOLED: po [idleMs] bez dotyku podczas odtwarzania ekran gaśnie do czystej
 * czerni (piksele OLED wyłączone), zostaje tylko mała okładka, zegar, tytuł i bieżąca linia tekstu.
 * Całość co pół minuty przesuwa się o kilka–kilkanaście dp, żeby nie wypalać ekranu.
 */
@Composable
fun AmbientHost(vm: MainViewModel, enabled: Boolean, idleMs: Long = 20_000, content: @Composable () -> Unit) {
    val ps by vm.player.state.collectAsState()
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ambient by remember { mutableStateOf(false) }
    LaunchedEffect(enabled, ps.isPlaying, lastTouch) {
        ambient = false
        if (!enabled || !ps.isPlaying) return@LaunchedEffect
        delay(idleMs)
        ambient = true
    }
    Box(Modifier.fillMaxSize().onAnyTouch { lastTouch = System.currentTimeMillis() }) {
        content()
        AnimatedVisibility(ambient, enter = fadeIn(tween(900)), exit = fadeOut(tween(250))) {
            AmbientScreen(vm) { ambient = false; lastTouch = System.currentTimeMillis() }
        }
    }
}

@Composable
private fun AmbientScreen(vm: MainViewModel, onWake: () -> Unit) {
    val track by vm.currentTrack.collectAsState()
    val pos by vm.player.position.collectAsState()
    val ps by vm.player.state.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val offset by vm.lyricsOffset.collectAsState()
    val artAccent by vm.artAccent.collectAsState()
    val tint = artAccent?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    val line = (lyrics as? LyricsUi.Loaded)?.lyrics?.let { l: Lyrics -> l.lines.getOrNull(l.indexAt(pos + offset))?.text }
    val next = ps.nextId?.let { com.blacksingularities.openamp.Graph.library.get(it) }
    // Delikatne „oddychanie” poświaty wokół okładki.
    val breath by rememberInfiniteTransition(label = "amb").animateFloat(0.55f, 1f, infiniteRepeatable(tween(5200), RepeatMode.Reverse), label = "br")

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { onWake() } }) {
        // Przesuwanie przeciw wypaleniu: całość wędruje w granicach ekranu co 40 s.
        var contentSize by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current
        val maxX = with(density) { ((constraints.maxWidth - contentSize.width) / 2).coerceAtLeast(0).toDp() } - 12.dp
        val maxY = with(density) { ((constraints.maxHeight - contentSize.height) / 2).coerceAtLeast(0).toDp() } - 24.dp
        var target by remember { mutableStateOf(Offset(0f, 0f)) }
        LaunchedEffect(Unit) { while (true) { delay(40_000); target = Offset(Random.nextFloat() * 2 - 1, Random.nextFloat() * 2 - 1) } }
        val ox by animateDpAsState(maxX.coerceAtLeast(0.dp) * target.x, tween(6000), label = "ax")
        val oy by animateDpAsState(maxY.coerceAtLeast(0.dp) * target.y, tween(6000), label = "ay")

        Column(
            Modifier.align(Alignment.Center).offset(ox, oy).widthIn(max = 320.dp).onSizeChanged { contentSize = it },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Color.White.copy(alpha = 0.8f), fontSize = 60.sp, fontWeight = FontWeight.Thin, letterSpacing = (-1).sp)
            Text(
                SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now).replaceFirstChar { it.uppercase() },
                color = tint.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
            )
            Box(Modifier.padding(vertical = 20.dp).size(196.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.32f * breath), tint.copy(alpha = 0.08f * breath), Color.Transparent), center, r), r)
                    val stroke = 3.dp.toPx()
                    val inset = 16.dp.toPx()
                    val arc = Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(Color.White.copy(alpha = 0.08f), -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    val p = if (ps.durationMs > 0) (pos.toFloat() / ps.durationMs).coerceIn(0f, 1f) else 0f
                    drawArc(tint.copy(alpha = 0.85f), -90f, 360f * p, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Artwork(track, Modifier.size(128.dp).alpha(0.85f), size = 400, shape = RoundedCornerShape(22.dp))
            }
            Text(track?.title ?: "", color = Color.White.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track?.artist ?: "", color = tint.copy(alpha = 0.65f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AnimatedContent(line.orEmpty(), transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) }, label = "al") { l ->
                if (l.isNotBlank()) Text(
                    l, color = Color.White.copy(alpha = 0.72f), fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                ) else Spacer(Modifier.height(1.dp))
            }
            if (next != null) Text(
                tr("Następnie: {0} — {1}", next.title, next.artist), color = Color.White.copy(alpha = 0.32f), fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}
