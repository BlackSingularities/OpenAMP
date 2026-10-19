package com.blacksingularities.openamp.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.lyrics.Lyrics
import com.blacksingularities.openamp.lyrics.LyricsSource

fun LyricsSource.label() = when (this) {
    LyricsSource.SIDECAR -> tr("plik obok utworu")
    LyricsSource.TAGS -> tr("tagi pliku")
    LyricsSource.ONLINE -> "LRCLIB"
    LyricsSource.USER -> tr("wczytany plik")
}

/** Zanikanie u góry i dołu przewijanego tekstu. */
private fun Modifier.fadingEdges() = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
fun LyricsPanel(vm: MainViewModel, modifier: Modifier = Modifier) {
    val state by vm.lyrics.collectAsState()
    val pos by vm.player.position.collectAsState()
    val ps by vm.player.state.collectAsState()
    val offset by vm.lyricsOffset.collectAsState()
    val s by vm.settings.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importLyrics(it) } }

    Box(modifier) {
        when (val st = state) {
            LyricsUi.Loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(tr("Szukam tekstu…"), color = Color.White.copy(alpha = 0.7f))
            }
            LyricsUi.None -> Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(tr("Brak tekstu dla tego utworu"), style = MaterialTheme.typography.titleMedium, color = Color.White)
                OutlinedButton(onClick = { vm.searchLyricsOnline() }) { Text(tr("Szukaj tekstu w internecie")) }
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(tr("Wczytaj plik .lrc")) }
            }
            is LyricsUi.Loaded -> {
                val l = st.lyrics
                Column(Modifier.fillMaxSize()) {
                    if (l.synced) SyncedLyrics(l, pos + offset, ps.durationMs, s.lyricsSize, s.lyricsCenter, Modifier.weight(1f)) { vm.player.seekTo((it - offset).coerceAtLeast(0)) }
                    else PlainLyrics(l, pos, ps.durationMs, s.lyricsSize, s.lyricsCenter, Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (l.synced) tr("Tekst zsynchronizowany") else tr("Tekst statyczny")) + " • " + tr("Źródło: {0}", l.source.label()),
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f),
                        )
                        if (l.synced) {
                            TextButton(onClick = { vm.shiftLyrics(-500) }) { Text("−0.5s", color = Color.White) }
                            TextButton(onClick = { vm.shiftLyrics(0) }) {
                                Text(String.format(java.util.Locale.ROOT, "%+.1fs", offset / 1000f), color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { vm.shiftLyrics(500) }) { Text("+0.5s", color = Color.White) }
                        } else {
                            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(".lrc", color = Color.White) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(l: Lyrics, pos: Long, duration: Long, size: Int, center: Boolean, modifier: Modifier, onSeek: (Long) -> Unit) {
    val current = l.indexAt(pos)
    val list = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0) {
            val viewport = list.layoutInfo.viewportSize.height
            list.animateScrollToItem(current + 1, -viewport / 3)
        }
    }
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxWidth().fadingEdges(),
        contentPadding = PaddingValues(vertical = 160.dp, horizontal = 24.dp),
    ) {
        item { Spacer(Modifier.height(1.dp)) }
        itemsIndexed(l.lines) { i, line ->
            val active = i == current
            val passed = i < current
            val color by animateColorAsState(
                when {
                    active -> Color.White
                    passed -> Color.White.copy(alpha = 0.35f)
                    else -> Color.White.copy(alpha = 0.5f)
                },
                tween(300), label = "lc",
            )
            val sc by animateFloatAsState(if (active) 1.06f else 1f, tween(300), label = "ls")
            // Postęp w aktywnej linii (pasek pod tekstem jak w karaoke).
            val next = l.lines.getOrNull(i + 1)?.timeMs ?: duration
            Column(
                Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) }.padding(vertical = 10.dp).scale(sc),
            ) {
                Text(
                    line.text.ifBlank { "♪" },
                    color = color,
                    fontSize = size.sp,
                    lineHeight = (size * 1.3f).sp,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
                    textAlign = if (center) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (active && next > line.timeMs) {
                    val p = ((pos - line.timeMs).toFloat() / (next - line.timeMs)).coerceIn(0f, 1f)
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth(p).height(3.dp).drawWithContent {
                        drawRoundRect(Color.White.copy(alpha = 0.8f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                    })
                }
            }
        }
    }
}

/** Tekst bez znaczników czasu przewija się proporcjonalnie do postępu utworu (można też przewijać ręcznie). */
@Composable
private fun PlainLyrics(l: Lyrics, pos: Long, duration: Long, size: Int, center: Boolean, modifier: Modifier) {
    val scroll = rememberScrollState()
    val frac = if (duration > 0) (pos.toFloat() / duration) else 0f
    LaunchedEffect((frac * 200).toInt()) {
        if (!scroll.isScrollInProgress) {
            // Pierwsze 10% i ostatnie 10% utworu to zwykle wstęp i zakończenie.
            val t = ((frac - 0.1f) / 0.8f).coerceIn(0f, 1f)
            scroll.animateScrollTo((scroll.maxValue * t).toInt())
        }
    }
    val text = remember(l) { l.plain }
    Text(
        text,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = (size - 2).sp,
        lineHeight = (size * 1.45f).sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = modifier.fillMaxWidth().fadingEdges().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 120.dp),
    )
}
