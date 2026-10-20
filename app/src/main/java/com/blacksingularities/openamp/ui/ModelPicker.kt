package com.blacksingularities.openamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blacksingularities.openamp.ai.AiModelInfo
import com.blacksingularities.openamp.i18n.tr

private enum class ModelSort { NAME, PRICE, CONTEXT }

private fun sortLabel(s: ModelSort) = when (s) {
    ModelSort.NAME -> tr("Nazwa")
    ModelSort.PRICE -> tr("Cena")
    ModelSort.CONTEXT -> tr("Kontekst")
}

private fun price(v: Double?): String = when {
    v == null -> "?"
    v == 0.0 -> "0"
    v < 0.01 -> "<0,01"
    else -> "%.2f".format(v)
}

/**
 * Wybór modelu jak w OpenDesktop: wyszukiwarka, filtr darmowych i sortowanie po cenie
 * i kontekście – te dwa tylko wtedy, gdy API dostawcy je podaje (np. OpenRouter).
 */
@Composable
fun ModelPickerDialog(
    providerLabel: String,
    models: List<AiModelInfo>,
    current: String,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onPick: (AiModelInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(ModelSort.NAME) }
    LaunchedEffect(Unit) { if (models.isEmpty()) onRefresh() }
    val priced = remember(models) { models.any { it.hasPricing } }
    val withContext = remember(models) { models.any { it.context != null } }
    val filtered = remember(models, query, freeOnly, sort, priced) {
        val q = query.trim()
        models.filter { m -> (!freeOnly || !priced || m.isFree) && (q.isEmpty() || m.id.contains(q, true) || m.title.contains(q, true) || m.owner.contains(q, true)) }
            .let { list ->
                when (sort) {
                    ModelSort.NAME -> list
                    ModelSort.PRICE -> list.sortedBy { (it.promptPerMillion ?: 1e9) + (it.completionPerMillion ?: 1e9) }
                    ModelSort.CONTEXT -> list.sortedByDescending { it.context ?: 0 }
                }
            }
    }
    val accent = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 24.dp).fillMaxWidth().clip(Shapes.XLarge).background(Compact.Menu).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowIcon(Icons.Rounded.Psychology)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(tr("Model AI"), color = Flat.Fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(providerLabel, color = Compact.TextDim, fontSize = 11.sp)
                }
                RoundButton(Icons.Rounded.Refresh, tr("Odśwież"), { if (!loading) onRefresh() }, size = 36.dp, iconSize = 18.dp, enabled = !loading)
                Spacer(Modifier.width(6.dp))
                RoundButton(Icons.Rounded.Close, tr("Zamknij"), onDismiss, size = 36.dp, iconSize = 18.dp)
            }
            // Wyszukiwarka
            Row(
                Modifier.fillMaxWidth().clip(Shapes.Pill).background(Flat.Block).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Compact.TextDim, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(if (priced) tr("Szukaj (claude, gpt, llama, free…)") else tr("Szukaj modelu"), color = Compact.TextFaint, fontSize = 13.sp)
                    BasicTextField(
                        query, { query = it }, singleLine = true, cursorBrush = SolidColor(accent),
                        textStyle = TextStyle(color = Flat.Fg, fontSize = 13.sp), modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) Icon(Icons.Rounded.Close, tr("Wyczyść"), tint = Compact.TextDim, modifier = Modifier.size(17.dp).clickable { query = "" })
            }
            if (priced || withContext) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (priced) PillButton(tr("Darmowe"), { freeOnly = !freeOnly }, active = freeOnly, compact = true)
                    ModelSort.entries
                        .filter { (it != ModelSort.PRICE || priced) && (it != ModelSort.CONTEXT || withContext) }
                        .forEach { s -> PillButton(sortLabel(s), { sort = s }, active = sort == s, compact = true) }
                }
            }
            if (models.isNotEmpty()) Text(
                tr("{0} z {1} modeli", filtered.size, models.size) +
                    if (priced) " · " + tr("USD za 1 mln tokenów (wejście / wyjście)") else "",
                fontSize = 10.sp, color = Compact.TextFaint,
            )
            when {
                loading && models.isEmpty() -> Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(tr("Pobieram listę modeli…"), color = Compact.TextDim, fontSize = 12.sp)
                }
                error != null && models.isEmpty() -> Text(error, color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                models.isEmpty() -> Text(tr("Dostawca nie zwrócił żadnych modeli."), color = Compact.TextDim, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                filtered.isEmpty() -> Text(tr("Brak modeli pasujących do „{0}”.", query.trim()), color = Compact.TextDim, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                else -> LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.id }) { m -> ModelRow(m, m.id == current, priced) { onPick(m) } }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(m: AiModelInfo, selected: Boolean, priced: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clip(Shapes.Medium)
            .background(if (selected) accent.copy(alpha = 0.16f) else Flat.Fg.copy(alpha = 0.04f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.title, color = if (selected) accent else Flat.Fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.title != m.id) Text(m.id, fontSize = 10.sp, color = Compact.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildString {
                if (priced) {
                    if (m.isFree) append(tr("darmowy")) else append("$${price(m.promptPerMillion)} / $${price(m.completionPerMillion)}")
                }
                m.context?.let { if (isNotEmpty()) append(" · "); append(tr("{0}k kontekstu", it / 1000)) }
                if (isEmpty() && m.owner.isNotBlank()) append(m.owner)
            }
            if (meta.isNotEmpty()) Text(meta, fontSize = 10.sp, color = if (m.isFree) Color(0xFF7EE0C3) else Compact.TextDim)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(22.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}
