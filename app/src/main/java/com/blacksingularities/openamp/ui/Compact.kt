package com.blacksingularities.openamp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Kompaktowe elementy ustawień i menu (jak w OpenPDF): ikona w kółku 30 dp, tytuł 13 sp,
 * opis 10 sp, przełączniki pomniejszone, wybór jednej opcji jako segment w tym samym wierszu.
 */
object Compact {
    val Menu = Color(0xFF171717)
    val TextDim = Color(0x99FFFFFF)
    val TextFaint = Color(0x55FFFFFF)
}

@Composable
fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, enabled: Boolean = true) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = if (enabled) 0.15f else 0.06f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) tint else Compact.TextFaint, modifier = Modifier.size(17.dp)) }
}

@Composable
private fun RowTexts(title: String, subtitle: String?, enabled: Boolean, modifier: Modifier) {
    Column(modifier) {
        Text(title, fontSize = 13.sp, color = if (enabled) Color.White else Compact.TextFaint, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
        if (subtitle != null) Text(subtitle, fontSize = 10.sp, color = if (enabled) Compact.TextDim else Compact.TextFaint, lineHeight = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SetSwitch(icon: ImageVector, title: String, subtitle: String?, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled) { onChange(!value) }.padding(horizontal = 8.dp, vertical = 2.dp).heightIn(min = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, enabled = enabled)
        RowTexts(title, subtitle, enabled, Modifier.weight(1f).padding(horizontal = 10.dp))
        Switch(
            value, onChange, enabled = enabled, modifier = Modifier.scale(0.78f),
            colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = MaterialTheme.colorScheme.onPrimary, uncheckedTrackColor = Color(0xFF222222)),
        )
    }
}

/** Wybór jednej z kilku opcji w jednym wierszu: segment po prawej. */
@Composable
fun <T> SetSegment(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    short: (T) -> String = label,
    optionIcon: ((T) -> ImageVector)? = null,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon, enabled = enabled)
        RowTexts(title, label(selected), enabled, Modifier.weight(1f).padding(horizontal = 10.dp))
        Segmented(options, selected, short, optionIcon, enabled, onSelect)
    }
}

@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, icon: ((T) -> ImageVector)? = null, enabled: Boolean = true, onSelect: (T) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val on = MaterialTheme.colorScheme.onPrimary
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.06f)).padding(2.dp).alpha(if (enabled) 1f else 0.4f)) {
        options.forEach { o ->
            val sel = o == selected
            val bg by animateColorAsState(if (sel) accent else Color.Transparent, tween(160), label = "seg")
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(bg)
                    .selectable(sel, enabled = enabled, role = Role.RadioButton) { onSelect(o) }
                    .padding(horizontal = if (icon != null) 9.dp else 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) Icon(icon(o), label(o), tint = if (sel) on else Compact.TextDim, modifier = Modifier.size(16.dp))
                else Text(label(o), fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, color = if (sel) on else Compact.TextDim, maxLines = 1)
            }
        }
    }
}

/** Wybór z dłuższej listy: wartość po prawej, dotknięcie otwiera kompaktowe menu. */
@Composable
fun <T> SetDropdown(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled) { open = true }.padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(icon, enabled = enabled)
            RowTexts(title, subtitle, enabled, Modifier.weight(1f).padding(horizontal = 10.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.06f)).padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp).widthIn(max = 170.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label(selected), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) MaterialTheme.colorScheme.primary else Compact.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Rounded.UnfoldMore, null, tint = Compact.TextDim, modifier = Modifier.size(14.dp))
            }
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            CompactMenu(open, { open = false }) {
                options.forEach { o -> CMenuItem(label(o), checked = o == selected) { onSelect(o); open = false } }
            }
        }
    }
}

@Composable
fun SetSlider(
    icon: ImageVector,
    title: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    subtitle: String? = null,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(Modifier.heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon, enabled = enabled)
            RowTexts(title, subtitle, enabled, Modifier.weight(1f).padding(horizontal = 10.dp))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (enabled) MaterialTheme.colorScheme.primary else Compact.TextFaint, fontFamily = MonoNumbers)
        }
        Slider(
            current, onChange, valueRange = range, steps = steps, enabled = enabled,
            modifier = Modifier.padding(start = 34.dp).height(28.dp),
            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.1f)),
        )
    }
}

@Composable
fun SetAction(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (danger) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, tint, enabled)
        RowTexts(title, subtitle, enabled, Modifier.weight(1f).padding(horizontal = 10.dp))
        trailing?.invoke(this)
    }
}

@Composable
fun InfoRow(icon: ImageVector, title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, fontSize = 10.sp, color = Compact.TextDim, lineHeight = 12.sp)
            Text(value.ifBlank { "—" }, fontSize = 13.sp, color = Color.White, lineHeight = 16.sp)
        }
    }
}

/** Karta grupy ustawień: tytuł sekcji i zwarte wiersze. */
@Composable
fun SetCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x0FFFFFFF)).padding(vertical = 6.dp, horizontal = 2.dp)) {
        if (title != null) Text(
            title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        )
        content()
    }
}

@Composable
fun SetNote(text: String) {
    Text(text, fontSize = 10.sp, lineHeight = 13.sp, color = Compact.TextDim, modifier = Modifier.padding(start = 48.dp, end = 12.dp, bottom = 4.dp))
}

/** Kompaktowe menu rozwijane: ciemna płyta, wiersze 36 dp, tekst 13 sp. */
@Composable
fun CompactMenu(expanded: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss,
        modifier = modifier.widthIn(min = 168.dp, max = 260.dp),
        shape = RoundedCornerShape(16.dp), containerColor = Compact.Menu, tonalElevation = 0.dp, shadowElevation = 10.dp,
    ) { content() }
}

@Composable
fun CMenuItem(text: String, icon: ImageVector? = null, checked: Boolean = false, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val color = when {
        !enabled -> Compact.TextFaint
        danger -> Color(0xFFFF6B6B)
        checked -> accent
        else -> Color.White
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp).height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, fontSize = 13.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
        if (checked) Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(15.dp))
    }
}

@Composable
fun CMenuLabel(text: String) {
    Text(text.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Compact.TextFaint, modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 2.dp))
}

@Composable
fun CMenuDivider() = Box(Modifier.padding(vertical = 3.dp, horizontal = 10.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))

/** Rząd zakładek-chipów (przewijany w poziomie). */
@Composable
fun <T> TabChips(tabs: List<T>, selected: T, label: (T) -> String, icon: (T) -> ImageVector, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val on = MaterialTheme.colorScheme.onPrimary
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tabs.forEach { t ->
            val sel = t == selected
            val bg by animateColorAsState(if (sel) accent else Flat.Block, tween(160), label = "tab")
            Row(
                Modifier.reactive(label(t)) { onSelect(t) }.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon(t), null, tint = if (sel) on else Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(label(t), color = if (sel) on else Color.White, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

@Composable
fun SmallGap(h: Dp = 8.dp) = Spacer(Modifier.height(h))
