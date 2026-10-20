package com.blacksingularities.openamp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.data.PlayStat
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.i18n.plural
import com.blacksingularities.openamp.i18n.tr
import java.util.Calendar
import kotlin.math.roundToInt

private const val DAY = 86_400_000L

private fun hours(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    return when {
        h >= 100 -> "$h h"
        h > 0 -> "$h h $m min"
        else -> "$m min"
    }
}

private fun todayIndex(): Long = Math.floorDiv(System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()), DAY)

/** Dzień tygodnia dnia o numerze [day] (od 1970): 0 – poniedziałek … 6 – niedziela (1.01.1970 to czwartek). */
private fun weekday(day: Long): Int = Math.floorMod(day + 3, 7L).toInt()

private fun weekdayShort(i: Int) = listOf(tr("Pn"), tr("Wt"), tr("Śr"), tr("Cz"), tr("Pt"), tr("So"), tr("Nd"))[i]
private fun weekdayLong(i: Int) = listOf(tr("poniedziałki"), tr("wtorki"), tr("środy"), tr("czwartki"), tr("piątki"), tr("soboty"), tr("niedziele"))[i]
private fun monthShort(m: Int) = listOf(tr("sty"), tr("lut"), tr("mar"), tr("kwi"), tr("maj"), tr("cze"), tr("lip"), tr("sie"), tr("wrz"), tr("paź"), tr("lis"), tr("gru"))[m]

private fun dayLabel(day: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = day * DAY }
    return "${c.get(Calendar.DAY_OF_MONTH)} ${monthShort(c.get(Calendar.MONTH))}"
}

private fun partOfDay(h: Int) = when (h) {
    in 5..10 -> tr("rano")
    in 11..16 -> tr("w ciągu dnia")
    in 17..21 -> tr("wieczorem")
    else -> tr("nocą")
}

private enum class ChartRange { DAYS14, DAYS30, MONTHS12 }
private enum class TopBy { PLAYS, TIME }

/** Wartość do rankingów grup: przesłuchany czas, a dla starszych danych bez czasu – minuta za odtworzenie. */
private fun weightOf(s: PlayStat) = s.listenMs.coerceAtLeast(s.count * 60_000L)

@Composable
fun StatsScreen(vm: MainViewModel, bottomPadding: Dp) {
    val all by vm.tracks.collectAsState()
    val data by vm.userData.collectAsState()
    val byId = remember(all) { all.associateBy { it.id } }
    val today = todayIndex()

    val totalMs = remember(data) { data.stats.values.sumOf { it.listenMs } }
    val plays = remember(data) { data.stats.values.sumOf { it.count } }
    val played = remember(data, byId) { data.stats.count { it.value.count > 0 && it.key in byId } }
    val activeDays = remember(data) { data.daily.count { it.value > 0 } }
    val firstDay = remember(data) { data.daily.filter { it.value > 0 }.keys.minOrNull() }
    val week = remember(data, today) { (0..6).sumOf { data.daily[today - it] ?: 0L } }
    val prevWeek = remember(data, today) { (7..13).sumOf { data.daily[today - it] ?: 0L } }
    // Seria: kolejne dni ze słuchaniem kończące się dziś (albo wczoraj, gdy dziś jeszcze cisza).
    val streak = remember(data, today) {
        var d = if ((data.daily[today] ?: 0L) > 0) today else today - 1
        var n = 0
        while ((data.daily[d] ?: 0L) > 0) { n++; d-- }
        n
    }
    val bestStreak = remember(data) {
        var best = 0; var run = 0; var last = Long.MIN_VALUE
        for (d in data.daily.filter { it.value > 0 }.keys.sorted()) {
            run = if (d == last + 1) run + 1 else 1
            best = maxOf(best, run); last = d
        }
        best
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), { vm.back() }, background = Color.Transparent, size = 40.dp)
            Column(Modifier.padding(start = 4.dp)) {
                Text(tr("Statystyki"), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Flat.Fg)
                if (firstDay != null) Text(tr("Od {0} · {1}", dayLabel(firstDay), plural(activeDays, "{0} dzień słuchania", "{0} dni słuchania", "{0} dni słuchania")), fontSize = 11.sp, color = Compact.TextDim)
            }
        }
        if (plays == 0 && totalMs == 0L) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.BarChart, null, tint = Flat.Faded, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Jeszcze nic tu nie ma"), color = Flat.Fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(tr("Posłuchaj trochę muzyki – statystyki pojawią się same."), color = Compact.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- Najważniejsze liczby
            item { Hero(totalMs, plays, activeDays) }
            item {
                val trend = if (prevWeek > 0) ((week - prevWeek) * 100f / prevWeek).roundToInt() else null
                TileRows(
                    listOf(
                        StatCell(Icons.Rounded.Today, tr("Dziś"), hours(data.daily[today] ?: 0L)),
                        StatCell(
                            if (trend != null && trend < 0) Icons.AutoMirrored.Rounded.TrendingDown else Icons.AutoMirrored.Rounded.TrendingUp,
                            tr("Ten tydzień"), hours(week),
                            trend?.let { (if (it >= 0) "+" else "") + "$it% " + tr("vs poprzedni") },
                        ),
                        StatCell(Icons.Rounded.Schedule, tr("Średnio dziennie"), hours(if (activeDays > 0) totalMs / activeDays else 0L), tr("w dni ze słuchaniem")),
                        StatCell(Icons.Rounded.LocalFireDepartment, tr("Seria"), plural(streak, "{0} dzień", "{0} dni", "{0} dni"), tr("rekord: {0}", bestStreak)),
                        StatCell(Icons.Rounded.MusicNote, tr("Średni utwór"), hours(if (plays > 0) totalMs / plays else 0L), tr("na odtworzenie")),
                        StatCell(Icons.Rounded.LibraryMusic, tr("Odkryte"), if (all.isNotEmpty()) "${played * 100 / all.size}%" else "—", "$played / ${all.size}"),
                    ),
                )
            }

            // ---- Aktywność w czasie
            item { ActivityCard(data.daily, today) }
            item { WeekRhythm(data.daily) }
            item { DayRhythm(data.hourly) }

            // ---- Rankingi
            item { TopTracks(vm, data.stats, byId) }
            item { TopGroups(vm, tr("Wykonawcy"), Icons.Rounded.Person, data.stats, byId) { it.artist } }
            item { TopAlbums(vm, data.stats, byId) }
            item { TopGroups(vm, tr("Gatunki"), Icons.Rounded.Category, data.stats, byId) { it.genre } }
            item { DecadesCard(data.stats, byId) }
            item { FormatsCard(data.stats, byId) }

            // ---- Biblioteka i odkrycia
            item { LibraryCard(all, data.stats, data.favorites.size, data.disliked.size) }
            item { RecentCard(vm, data.stats, byId) }
            item { GemsCard(vm, data.stats, byId) }
        }
    }
}

// ---------------------------------------------------------------- elementy

@Composable
private fun Card(title: String, icon: ImageVector, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(Shapes.Large).background(Flat.Faint).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = Flat.Fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun Hero(totalMs: Long, plays: Int, days: Int) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth().clip(Shapes.Large)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.06f))))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(tr("Łącznie przesłuchane").uppercase(), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(hours(totalMs), color = Flat.Fg, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = MonoNumbers)
        val daysOfMusic = totalMs / DAY.toFloat()
        Text(
            listOfNotNull(
                plural(plays, "{0} odtworzenie", "{0} odtworzenia", "{0} odtworzeń"),
                if (daysOfMusic >= 1f) tr("{0} doby muzyki bez przerwy", String.format(java.util.Locale.ROOT, "%.1f", daysOfMusic).replace('.', ',')) else null,
                if (days > 0) plural(days, "{0} dzień", "{0} dni", "{0} dni") else null,
            ).joinToString("  ·  "),
            color = Compact.TextDim, fontSize = 12.sp,
        )
    }
}

private class StatCell(val icon: ImageVector, val label: String, val value: String, val note: String? = null)

/** Kafelki po trzy w wierszu, w każdym wierszu tej samej wysokości. */
@Composable
private fun TileRows(cells: List<StatCell>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c ->
                    Column(Modifier.weight(1f).fillMaxHeight().clip(Shapes.Medium).background(Flat.Faint).padding(horizontal = 10.dp, vertical = 9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(c.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(c.label, color = Compact.TextDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(c.value, color = Flat.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                        if (c.note != null) Text(c.note, color = Compact.TextFaint, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Słupki z linią średniej; dotknięcie słupka pokazuje jego wartość. [labels] – podpis każdego
 * słupka (pokazywany dla zaznaczonego), [ticks] – podpisy osi pod wykresem.
 */
@Composable
private fun Bars(values: List<Long>, labels: List<String>, ticks: List<String>, height: Dp = 130.dp, highlight: Int = values.lastIndex, unit: (Long) -> String = ::hours) {
    val accent = MaterialTheme.colorScheme.primary
    val track = Flat.Fg.copy(alpha = 0.05f)
    val avgColor = Flat.Fg.copy(alpha = 0.35f)
    var selected by remember(values.size) { mutableIntStateOf(-1) }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(60_000L)
    val nonZero = values.filter { it > 0 }
    val avg = if (nonZero.isEmpty()) 0L else nonZero.sum() / nonZero.size
    val shown = if (selected in values.indices) selected else highlight
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(unit(values.getOrElse(shown) { 0L }), color = Flat.Fg, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = MonoNumbers)
        Spacer(Modifier.width(8.dp))
        Text(labels.getOrElse(shown) { "" }, color = Compact.TextDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp).weight(1f))
        if (avg > 0) Text(tr("średnio {0}", unit(avg)), color = Compact.TextFaint, fontSize = 10.sp, modifier = Modifier.padding(bottom = 3.dp))
    }
    Canvas(
        Modifier.fillMaxWidth().height(height).pointerInput(values.size) {
            detectTapGestures { o ->
                val i = (o.x / size.width * values.size).toInt().coerceIn(0, values.lastIndex)
                selected = if (selected == i) -1 else i
            }
        },
    ) {
        val n = values.size
        val gap = (size.width / n * 0.22f).coerceAtMost(6.dp.toPx())
        val w = (size.width - gap * (n - 1)) / n
        val r = CornerRadius(minOf(w / 2.5f, 6.dp.toPx()))
        values.forEachIndexed { i, ms ->
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), r)
            val h = size.height * ms / max
            if (h > 0) {
                val on = i == shown
                drawRoundRect(
                    Brush.verticalGradient(listOf(accent.copy(alpha = if (on) 1f else 0.75f), accent.copy(alpha = if (on) 0.55f else 0.3f)), startY = size.height - h, endY = size.height),
                    Offset(x, size.height - h), Size(w, h), r,
                )
            }
        }
        if (avg > 0) {
            val y = size.height - size.height * avg / max
            drawLine(avgColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
        ticks.forEachIndexed { i, t ->
            Text(
                t, color = Compact.TextFaint, fontSize = 9.sp, modifier = Modifier.weight(1f),
                textAlign = when (i) { 0 -> TextAlign.Start; ticks.lastIndex -> TextAlign.End; else -> TextAlign.Center },
            )
        }
    }
}

@Composable
private fun ActivityCard(daily: Map<Long, Long>, today: Long) {
    var range by remember { mutableStateOf(ChartRange.DAYS14) }
    Card(tr("Aktywność"), Icons.Rounded.CalendarMonth, trailing = {
        Segmented(ChartRange.entries, range, { when (it) { ChartRange.DAYS14 -> tr("14 dni"); ChartRange.DAYS30 -> tr("30 dni"); ChartRange.MONTHS12 -> tr("Rok") } }) { range = it }
    }) {
        when (range) {
            ChartRange.DAYS14, ChartRange.DAYS30 -> {
                val n = if (range == ChartRange.DAYS14) 14 else 30
                val days = (n - 1 downTo 0).map { today - it }
                Bars(
                    days.map { daily[it] ?: 0L },
                    days.map { if (it == today) tr("dziś") else "${weekdayShort(weekday(it))}, ${dayLabel(it)}" },
                    listOf(dayLabel(days.first()), dayLabel(days[days.size / 2]), tr("dziś")),
                )
            }
            ChartRange.MONTHS12 -> {
                val now = Calendar.getInstance()
                // Dwanaście ostatnich miesięcy: suma dni należących do każdego z nich.
                val months = (11 downTo 0).map { back -> Calendar.getInstance().apply { add(Calendar.MONTH, -back) }.let { it.get(Calendar.YEAR) to it.get(Calendar.MONTH) } }
                val sums = LongArray(12)
                val cal = Calendar.getInstance()
                daily.forEach { (d, ms) ->
                    cal.timeInMillis = d * DAY
                    val i = months.indexOf(cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH))
                    if (i >= 0) sums[i] += ms
                }
                Bars(
                    sums.toList(),
                    months.map { (y, m) -> "${monthShort(m)} $y" },
                    listOf(monthShort(months.first().second), monthShort(months[6].second), monthShort(now.get(Calendar.MONTH))),
                )
            }
        }
    }
}

@Composable
private fun WeekRhythm(daily: Map<Long, Long>) {
    val sums = LongArray(7)
    val counts = IntArray(7)
    daily.forEach { (d, ms) -> if (ms > 0) { sums[weekday(d)] += ms; counts[weekday(d)]++ } }
    if (sums.all { it == 0L }) return
    val avg = (0..6).map { if (counts[it] > 0) sums[it] / counts[it] else 0L }
    val best = avg.indices.maxBy { avg[it] }
    Card(tr("Rytm tygodnia"), Icons.Rounded.CalendarMonth) {
        Text(tr("Najwięcej słuchasz w {0}", weekdayLong(best)), color = Compact.TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        Bars(avg, (0..6).map { tr("średnio w {0}", weekdayLong(it)) }, (0..6).map { weekdayShort(it) }, height = 90.dp, highlight = best)
    }
}

@Composable
private fun DayRhythm(hourly: Map<Int, Long>) {
    Card(tr("Pora dnia"), Icons.Rounded.Schedule) {
        if (hourly.values.sum() == 0L) {
            Text(tr("Zbieram dane – rozkład godzin pojawi się po kilku odsłuchach."), color = Compact.TextDim, fontSize = 12.sp)
            return@Card
        }
        val values = (0..23).map { hourly[it] ?: 0L }
        val best = values.indices.maxBy { values[it] }
        Text(tr("Najczęściej słuchasz {0}, ok. {1}:00", partOfDay(best), best), color = Compact.TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        Bars(values, (0..23).map { "$it:00–${(it + 1) % 24}:00" }, listOf("0", "6", "12", "18", "23"), height = 80.dp, highlight = best)
    }
}

/** Wiersz rankingu: miejsce, miniatura, nazwa z podpisem i pasek udziału w tle. */
@Composable
private fun RankRow(rank: Int, art: Track?, title: String, subtitle: String?, value: String, share: Float, onClick: (() -> Unit)?) {
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().clip(Shapes.Medium).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        // Udział w tle wiersza – pasek na całą jego wysokość.
        Box(Modifier.matchParentSize()) { Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0.02f, 1f)).background(accent.copy(alpha = 0.10f), Shapes.Medium)) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", fontFamily = MonoNumbers, color = if (rank <= 3) accent else Compact.TextFaint, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(22.dp))
            if (art != null) {
                Artwork(art, Modifier.size(36.dp), size = 140, shape = Shapes.Small)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Flat.Fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, color = Compact.TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, color = Compact.TextDim, fontSize = 11.sp, fontFamily = MonoNumbers)
        }
    }
}

@Composable
private fun TopTracks(vm: MainViewModel, stats: Map<Long, PlayStat>, byId: Map<Long, Track>) {
    var by by remember { mutableStateOf(TopBy.PLAYS) }
    var more by remember { mutableStateOf(false) }
    val list = remember(stats, byId, by) {
        stats.entries.filter { it.value.count > 0 && it.key in byId }
            .sortedByDescending { if (by == TopBy.PLAYS) it.value.count.toLong() else it.value.listenMs }
            .take(25).map { byId.getValue(it.key) to it.value }
    }
    if (list.isEmpty()) return
    Card(tr("Najczęściej słuchane"), Icons.Rounded.MusicNote, trailing = {
        Segmented(TopBy.entries, by, { if (it == TopBy.PLAYS) tr("Odtworzenia") else tr("Czas") }) { by = it }
    }) {
        val top = if (by == TopBy.PLAYS) list.first().second.count.toFloat() else list.first().second.listenMs.toFloat().coerceAtLeast(1f)
        val shown = if (more) list else list.take(10)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            shown.forEachIndexed { i, (t, s) ->
                RankRow(
                    i + 1, t, t.title, t.artist,
                    if (by == TopBy.PLAYS) "${s.count}×" else hours(s.listenMs),
                    (if (by == TopBy.PLAYS) s.count.toFloat() else s.listenMs.toFloat()) / top,
                ) { vm.player.play(shown.map { it.first }, i) }
            }
        }
        if (list.size > 10) MoreButton(more) { more = !more }
    }
}

@Composable
private fun MoreButton(expanded: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        PillButton(if (expanded) tr("Pokaż mniej") else tr("Pokaż więcej"), onClick, compact = true)
    }
}

@Composable
private fun TopGroups(vm: MainViewModel, title: String, icon: ImageVector, stats: Map<Long, PlayStat>, byId: Map<Long, Track>, key: (Track) -> String) {
    var more by remember { mutableStateOf(false) }
    val groups = remember(stats, byId) {
        stats.entries.mapNotNull { e -> byId[e.key]?.let { t -> Triple(key(t).trim(), t, e.value) } }
            .filter { it.first.isNotBlank() && !it.first.equals("<unknown>", true) }
            .groupBy { it.first }
            .map { (name, rows) ->
                Group(name, rows.sumOf { weightOf(it.third) }, rows.sumOf { it.third.count }, rows.maxBy { weightOf(it.third) }.second, rows.map { it.second })
            }
            .sortedByDescending { it.ms }
    }
    if (groups.isEmpty()) return
    Card(title, icon, trailing = { Text(plural(groups.size, "{0} pozycja", "{0} pozycje", "{0} pozycji"), color = Compact.TextFaint, fontSize = 10.sp) }) {
        val top = groups.first().ms.toFloat().coerceAtLeast(1f)
        val shown = if (more) groups.take(25) else groups.take(8)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            shown.forEachIndexed { i, g ->
                RankRow(i + 1, g.cover, g.name, plural(g.plays, "{0} odtworzenie", "{0} odtworzenia", "{0} odtworzeń"), hours(g.ms), g.ms / top) {
                    vm.player.play(g.tracks.sortedByDescending { stats[it.id]?.count ?: 0 }, 0)
                }
            }
        }
        if (groups.size > 8) MoreButton(more) { more = !more }
    }
}

private class Group(val name: String, val ms: Long, val plays: Int, val cover: Track, val tracks: List<Track>)

@Composable
private fun TopAlbums(vm: MainViewModel, stats: Map<Long, PlayStat>, byId: Map<Long, Track>) {
    val albums = remember(stats, byId) {
        stats.entries.mapNotNull { e -> byId[e.key]?.let { it to e.value } }
            .filter { it.first.album.isNotBlank() }
            .groupBy { it.first.albumId }
            .map { (_, rows) -> Group(rows.first().first.album, rows.sumOf { weightOf(it.second) }, rows.sumOf { it.second.count }, rows.first().first, rows.map { it.first }) }
            .sortedByDescending { it.ms }.take(12)
    }
    if (albums.isEmpty()) return
    Card(tr("Albumy"), Icons.Rounded.Album) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(albums) { a ->
                Column(Modifier.width(104.dp).clip(Shapes.Medium).clickable { vm.player.play(a.tracks.sortedBy { it.trackNumber }, 0) }) {
                    Artwork(a.cover, Modifier.size(104.dp), size = 300, shape = Shapes.Medium)
                    Text(a.name, color = Flat.Fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                    Text(a.cover.albumArtist.ifBlank { a.cover.artist }, color = Compact.TextDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(hours(a.ms), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontFamily = MonoNumbers)
                }
            }
        }
    }
}

@Composable
private fun DecadesCard(stats: Map<Long, PlayStat>, byId: Map<Long, Track>) {
    val decades = remember(stats, byId) {
        stats.entries.mapNotNull { e -> byId[e.key]?.takeIf { it.year in 1900..2100 }?.let { (it.year / 10 * 10) to weightOf(e.value) } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.toSortedMap()
    }
    if (decades.size < 2) return
    val best = decades.maxBy { it.value }.key
    Card(tr("Dekady"), Icons.Rounded.History) {
        Text(tr("Najczęściej wracasz do lat {0}.", "${best % 100}".padStart(2, '0')), color = Compact.TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        val keys = decades.keys.toList()
        Bars(decades.values.toList(), keys.map { tr("lata {0}", it) }, keys.map { "'" + "${it % 100}".padStart(2, '0') }, height = 80.dp, highlight = keys.indexOf(best))
    }
}

@Composable
private fun FormatsCard(stats: Map<Long, PlayStat>, byId: Map<Long, Track>) {
    val formats = remember(stats, byId) {
        stats.entries.mapNotNull { e -> byId[e.key]?.let { it.format to weightOf(e.value) } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }
    }
    if (formats.isEmpty()) return
    val total = formats.sumOf { it.value }.toFloat().coerceAtLeast(1f)
    val accent = MaterialTheme.colorScheme.primary
    val shades = listOf(1f, 0.7f, 0.48f, 0.32f, 0.2f)
    Card(tr("Formaty plików"), Icons.Rounded.LibraryMusic) {
        // Jeden pasek podzielony na udziały, pod nim legenda.
        Row(Modifier.fillMaxWidth().height(12.dp).clip(Shapes.Pill)) {
            formats.take(5).forEachIndexed { i, f -> Box(Modifier.weight((f.value / total).coerceAtLeast(0.01f)).fillMaxHeight().background(accent.copy(alpha = shades[i]))) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            formats.take(5).forEachIndexed { i, f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(Shapes.Pill).background(accent.copy(alpha = shades[i])))
                    Spacer(Modifier.width(4.dp))
                    Text("${f.key} ${(f.value * 100 / total).roundToInt()}%", color = Compact.TextDim, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(all: List<Track>, stats: Map<Long, PlayStat>, favorites: Int, disliked: Int) {
    val ids = remember(all) { all.mapTo(HashSet()) { it.id } }
    val played = stats.count { it.value.count > 0 && it.key in ids }
    val never = all.size - played
    val libMs = remember(all) { all.sumOf { it.durationMs } }
    val share = if (all.isEmpty()) 0f else played.toFloat() / all.size
    val accent = MaterialTheme.colorScheme.primary
    val ring = Flat.Fg.copy(alpha = 0.08f)
    Card(tr("Twoja biblioteka"), Icons.Rounded.LibraryMusic) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val st = 9.dp.toPx()
                    drawArc(ring, 0f, 360f, false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st), style = Stroke(st))
                    drawArc(accent, -90f, 360f * share, false, Offset(st / 2, st / 2), Size(size.width - st, size.height - st), style = Stroke(st, cap = StrokeCap.Round))
                }
                Text("${(share * 100).roundToInt()}%", color = Flat.Fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                LibLine(tr("Utwory"), "${all.size}")
                LibLine(tr("Długość biblioteki"), hours(libMs))
                LibLine(tr("Odsłuchane"), "$played")
                LibLine(tr("Nigdy nie grane"), "$never")
                LibLine(tr("Ulubione"), "$favorites")
                if (disliked > 0) LibLine(tr("Nie lubię"), "$disliked")
            }
        }
    }
}

@Composable
private fun LibLine(label: String, value: String) {
    Row(Modifier.width(200.dp)) {
        Text(label, color = Compact.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = Flat.Fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = MonoNumbers)
    }
}

@Composable
private fun RecentCard(vm: MainViewModel, stats: Map<Long, PlayStat>, byId: Map<Long, Track>) {
    val recent = remember(stats, byId) { stats.entries.filter { it.value.lastPlayed > 0 && it.key in byId }.sortedByDescending { it.value.lastPlayed }.take(15).map { byId.getValue(it.key) } }
    if (recent.isEmpty()) return
    Card(tr("Ostatnio słuchane"), Icons.Rounded.History) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recent.size) { i ->
                val t = recent[i]
                Column(Modifier.width(72.dp).clip(Shapes.Small).clickable { vm.player.play(recent, i) }) {
                    Artwork(t, Modifier.size(72.dp), size = 200, shape = Shapes.Small)
                    Text(t.title, color = Flat.Fg, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

/** Zapomniane perełki: często grane kiedyś, ale nie od dwóch miesięcy. */
@Composable
private fun GemsCard(vm: MainViewModel, stats: Map<Long, PlayStat>, byId: Map<Long, Track>) {
    val now = System.currentTimeMillis()
    val gems = remember(stats, byId) {
        stats.entries.filter { it.value.count >= 3 && it.value.lastPlayed in 1 until now - 60 * DAY && it.key in byId }
            .sortedByDescending { it.value.count }.take(6).map { byId.getValue(it.key) to it.value }
    }
    if (gems.isEmpty()) return
    Card(tr("Zapomniane perełki"), Icons.Rounded.Diamond, trailing = {
        PillButton(tr("Zagraj"), { vm.player.play(gems.map { it.first }, 0) }, compact = true)
    }) {
        Text(tr("Kiedyś grane często, ostatnio ponad dwa miesiące temu."), color = Compact.TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        val top = gems.first().second.count.toFloat()
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            gems.forEachIndexed { i, (t, s) ->
                RankRow(i + 1, t, t.title, tr("{0} · ostatnio {1}", t.artist, dayLabel(Math.floorDiv(s.lastPlayed, DAY))), "${s.count}×", s.count / top) {
                    vm.player.play(gems.map { it.first }, i)
                }
            }
        }
    }
}
