package com.blacksingularities.openamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blacksingularities.openamp.i18n.plural
import com.blacksingularities.openamp.i18n.tr

private fun hours(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    return if (h > 0) "$h h $m min" else "$m min"
}

@Composable
fun StatsScreen(vm: MainViewModel, bottomPadding: Dp) {
    val all by vm.tracks.collectAsState()
    val data by vm.userData.collectAsState()
    val byId = remember(all) { all.associateBy { it.id } }
    val totalMs = data.stats.values.sumOf { it.listenMs }
    val plays = data.stats.values.sumOf { it.count }
    val topTracks = remember(data, byId) {
        data.stats.entries.filter { it.value.count > 0 && byId[it.key] != null }.sortedByDescending { it.value.count }.take(10).map { byId[it.key]!! to it.value.count }
    }
    fun groupTop(key: (com.blacksingularities.openamp.data.Track) -> String) = data.stats.entries.mapNotNull { e -> byId[e.key]?.let { key(it) to e.value.listenMs.coerceAtLeast(e.value.count * 60_000L) } }
        .filter { it.first.isNotBlank() }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.take(8)
    val topArtists = remember(data, byId) { groupTop { it.artist } }
    val topGenres = remember(data, byId) { groupTop { it.genre } }
    val today = Math.floorDiv(System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()), 86_400_000L)
    val days = (13 downTo 0).map { today - it }.map { it to (data.daily[it] ?: 0L) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz")) }
            Text(tr("Statystyki"), style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPadding + 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(tr("Czas słuchania"), hours(totalMs), Modifier.weight(1f))
                    StatTile(tr("Odtworzenia"), plays.toString(), Modifier.weight(1f))
                    StatTile(tr("Utwory"), "${data.stats.count { it.value.count > 0 }}/${all.size}", Modifier.weight(1f))
                }
            }
            item {
                Panel {
                    Overline(tr("Ostatnie 14 dni"))
                    DaysChart(days)
                }
            }
            if (topTracks.isNotEmpty()) item {
                Panel {
                    Overline(tr("Najczęściej odtwarzane"))
                    topTracks.forEachIndexed { i, (t, c) ->
                        Row(Modifier.fillMaxWidth().clickable { vm.player.play(topTracks.map { it.first }, i) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", fontFamily = MonoNumbers, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
                            Artwork(t, Modifier.size(42.dp), size = 160, shape = Shapes.Small)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text(plural(c, "{0} odtworzenie", "{0} odtworzenia", "{0} odtworzeń"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (topArtists.isNotEmpty()) item { RankPanel(tr("Ulubieni wykonawcy"), topArtists.map { it.key to it.value }) }
            if (topGenres.isNotEmpty()) item { RankPanel(tr("Ulubione gatunki"), topGenres.map { it.key to it.value }) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Panel(modifier, padding = 14.dp) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DaysChart(days: List<Pair<Long, Long>>) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val max = (days.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(60_000L)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val n = days.size
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        days.forEachIndexed { i, (_, ms) ->
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 3))
            val h = size.height * ms / max
            if (h > 0) drawRoundRect(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.4f)), startY = size.height - h, endY = size.height), Offset(x, size.height - h), Size(w, h), CornerRadius(w / 3))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(tr("2 tyg. temu"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(tr("dziś") + " • " + hours(days.last().second), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RankPanel(title: String, rows: List<Pair<String, Long>>) {
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    Panel {
        Overline(title)
        rows.forEach { (name, v) ->
            Column(Modifier.padding(vertical = 5.dp)) {
                Row {
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(hours(v), style = MaterialTheme.typography.labelMedium, fontFamily = MonoNumbers, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))) {
                    Box(Modifier.fillMaxWidth(v.toFloat() / max).height(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}
