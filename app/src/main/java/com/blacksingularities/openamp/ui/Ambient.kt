package com.blacksingularities.openamp.ui

import androidx.compose.animation.AnimatedVisibility
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
    val lyrics by vm.lyrics.collectAsState()
    val offset by vm.lyricsOffset.collectAsState()
    var now by remember { mutableStateOf(Date()) }
    var dx by remember { mutableStateOf(0) }
    var dy by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            now = Date()
            if (tick++ % 30 == 0) { dx = Random.nextInt(-24, 25); dy = Random.nextInt(-60, 61) }
            delay(1000)
        }
    }
    val ox by animateDpAsState(dx.dp, tween(4000), label = "ax")
    val oy by animateDpAsState(dy.dp, tween(4000), label = "ay")
    val line = (lyrics as? LyricsUi.Loaded)?.lyrics?.let { l: Lyrics -> l.lines.getOrNull(l.indexAt(pos + offset))?.text }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { onWake() } }) {
        Column(Modifier.align(Alignment.Center).offset(ox, oy).widthIn(max = 320.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Color.White.copy(alpha = 0.75f), fontSize = 64.sp, fontWeight = FontWeight.Light)
            Artwork(track, Modifier.padding(vertical = 18.dp).size(150.dp), size = 400, shape = RoundedCornerShape(20.dp))
            Text(track?.title ?: "", color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track?.artist ?: "", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp, maxLines = 1)
            if (!line.isNullOrBlank()) Text(
                line, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
