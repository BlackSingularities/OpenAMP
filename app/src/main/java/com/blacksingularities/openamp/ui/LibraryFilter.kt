package com.blacksingularities.openamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.i18n.tr
import kotlin.math.roundToInt

/**
 * Filtr biblioteki: które foldery i formaty pokazywać oraz minimalna długość utworu.
 * Zmiany są zapisywane po „Zastosuj” – biblioteka skanuje się wtedy ponownie.
 */
@Composable
fun LibraryFilterSheet(vm: MainViewModel, onClose: () -> Unit) {
    val s by vm.settings.collectAsState()
    val folders by Graph.library.allFolders.collectAsState()
    val formats by Graph.library.allFormats.collectAsState()
    val excluded = remember { mutableStateListOf<String>().apply { addAll(s.excludedFolders.split(',').map { it.trim() }.filter { it.isNotEmpty() }) } }
    val hidden = remember { mutableStateListOf<String>().apply { addAll(s.hiddenFormats.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }) } }
    var minSec by remember { mutableStateOf(s.minDurationSec.toFloat()) }
    val shown = folders.filter { (f, _) -> excluded.none { f.contains(it, ignoreCase = true) } }.sumOf { it.second }

    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.Black) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(Icons.Rounded.FilterList, tr("Co pokazywać w bibliotece"))
            Text(tr("Widocznych utworów: {0}", shown), color = Flat.Dim, fontSize = 13.sp)

            SectionHeader(null, tr("Formaty plików"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                formats.forEach { (fmt, n) ->
                    val on = fmt !in hidden
                    PillButton("${fmt.uppercase()}  $n", { if (on) hidden.add(fmt) else hidden.remove(fmt) }, active = on)
                }
            }

            SectionHeader(null, tr("Minimalna długość utworu"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(minSec, { minSec = ((it / 10).roundToInt() * 10).toFloat() }, valueRange = 0f..180f, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(tr("{0} s", minSec.roundToInt()), color = Color.White, fontFamily = MonoNumbers)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(null, tr("Foldery"), Modifier.weight(1f))
                PillButton(tr("Wszystkie"), { excluded.clear() })
                Spacer(Modifier.width(6.dp))
                PillButton(tr("Żadne"), { excluded.clear(); excluded.addAll(folders.map { it.first }) })
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                items(folders, key = { it.first }) { (path, n) ->
                    val on = excluded.none { path.contains(it, ignoreCase = true) }
                    Row(
                        Modifier.fillMaxWidth().reactive(path) {
                            if (on) excluded.add(path) else excluded.removeAll { path.contains(it, ignoreCase = true) }
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                            tint = if (on) MaterialTheme.colorScheme.primary else Flat.Faded, modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Flat.Block), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Folder, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(path.substringAfterLast('/').ifEmpty { "/" }, color = if (on) Color.White else Flat.Faded, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(path, color = Flat.Faded, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(n.toString(), color = Flat.Dim, fontSize = 12.sp)
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.End) {
                PillButton(tr("Anuluj"), onClose)
                Spacer(Modifier.width(8.dp))
                PillButton(tr("Zastosuj"), {
                    vm.updateSettings {
                        it.copy(
                            excludedFolders = excluded.distinct().joinToString(","),
                            hiddenFormats = hidden.distinct().joinToString(","),
                            minDurationSec = minSec.roundToInt(),
                        )
                    }
                    onClose()
                }, active = true)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
