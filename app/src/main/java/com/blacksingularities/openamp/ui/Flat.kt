package com.blacksingularities.openamp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Płaski, czarno-biały język interfejsu (jak w OpenPDF): czarne tło, przyciski-koła i pigułki
 * w kolorze #1C1C1C, aktywny element to biały (akcentowy) blok z czarną ikoną. Bez gradientów
 * i obwódek; reakcja na dotyk – sprężyste wciśnięcie i haptyka, a po przytrzymaniu dymek z nazwą.
 */
object Flat {
    val Block = Color(0xFF1C1C1C)
    val Faint = Color(0x0FFFFFFF)
    val Dim = Color(0xB3FFFFFF)
    val Faded = Color(0x66FFFFFF)
    val Hair = Color(0x1FFFFFFF)
}

@Stable
class HintState {
    var text by mutableStateOf<String?>(null)
        private set
    var token by mutableIntStateOf(0)
        private set

    fun show(t: String) { text = t; token++ }
    fun hide() { text = null }
}

val LocalHints = staticCompositionLocalOf<HintState?> { null }

/** Dymek z nazwą elementu (u góry ekranu), znika sam po chwili. */
@Composable
fun HintBubble(state: HintState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.token) {
        if (state.text != null) { delay(1400); state.hide() }
    }
    AnimatedVisibility(state.text != null, modifier, enter = fadeIn(tween(120)), exit = fadeOut(tween(200))) {
        Text(
            state.text ?: "", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/**
 * Dotyk jak w OpenPDF: sprężyste wciśnięcie (skala 0,86), haptyka przy dotknięciu,
 * przytrzymanie pokazuje dymek z [label] albo wykonuje [onLongPress].
 */
fun Modifier.reactive(label: String?, enabled: Boolean = true, onLongPress: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val hints = LocalHints.current
    var pressed by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongPress)
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .semantics { if (label != null) contentDescription = label }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val l = long
                    if (l != null) l() else label?.let { hints?.show(it) }
                },
                onTap = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    click()
                },
            )
        }
}

/** Okrągły przycisk z ikoną; [active] – biały blok z czarną ikoną. */
@Composable
fun RoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    background: Color = Flat.Block,
    tint: Color = Color.White,
    enabled: Boolean = true,
    onLongPress: (() -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (active) accent else background, tween(160), label = "rb")
    Box(
        modifier.size(size).reactive(label, enabled, onLongPress, onClick).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) MaterialTheme.colorScheme.onPrimary else tint.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(iconSize))
    }
}

/** Pigułka: ikona i/lub tekst. */
@Composable
fun PillButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
    label: String? = text,
    background: Color = Flat.Block,
    onLongPress: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (active) accent else background, tween(160), label = "pb")
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        modifier.reactive(label, onLongPress = onLongPress, onClick = onClick).clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = if (text == null) (if (compact) 10.dp else 11.dp) else (if (compact) 11.dp else 13.dp), vertical = if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(if (compact) 16.dp else 18.dp))
        if (icon != null && text != null) Spacer(Modifier.width(5.dp))
        if (text != null) Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 12.sp else 13.sp, maxLines = 1)
    }
}

/** Nagłówek sekcji: ikona, pogrubiony tytuł i cienka linia do końca wiersza. */
@Composable
fun SectionHeader(icon: ImageVector?, title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Flat.Hair))
    }
}

/** Kafelek-przełącznik (4 w rzędzie): ikona + krótki podpis, pełny opis po przytrzymaniu. */
class Tile(
    val icon: ImageVector,
    val short: String,
    val full: String,
    val value: Boolean,
    val onChange: (Boolean) -> Unit,
)

@Composable
fun TileGrid(tiles: List<Tile>, columns: Int = 4) {
    val accent = MaterialTheme.colorScheme.primary
    val on = MaterialTheme.colorScheme.onPrimary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { t ->
                    Column(
                        Modifier.weight(1f)
                            .reactive(t.full) { t.onChange(!t.value) }
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (t.value) accent else Flat.Faint)
                            .padding(vertical = 9.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(t.icon, null, tint = if (t.value) on else Flat.Dim, modifier = Modifier.size(20.dp))
                        Text(
                            t.short, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, textAlign = TextAlign.Center,
                            color = if (t.value) on else Flat.Dim,
                            fontWeight = if (t.value) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Kafelki wyboru jednej opcji (segment): aktywny – blok akcentu. */
@Composable
fun <T> ChoiceTiles(options: List<T>, selected: T, label: (T) -> String, icon: ((T) -> ImageVector)? = null, onPick: (T) -> Unit) {
    TileGridChoice(options, selected, label, icon, onPick)
}

@Composable
private fun <T> TileGridChoice(options: List<T>, selected: T, label: (T) -> String, icon: ((T) -> ImageVector)?, onPick: (T) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val on = MaterialTheme.colorScheme.onPrimary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { o ->
                    val sel = o == selected
                    Column(
                        Modifier.weight(1f).reactive(label(o)) { onPick(o) }.clip(RoundedCornerShape(14.dp))
                            .background(if (sel) accent else Flat.Faint).padding(vertical = 9.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (icon != null) Icon(icon(o), null, tint = if (sel) on else Flat.Dim, modifier = Modifier.size(20.dp))
                        Text(
                            label(o), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, textAlign = TextAlign.Center,
                            color = if (sel) on else Flat.Dim, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(top = if (icon != null) 3.dp else 4.dp, bottom = if (icon != null) 0.dp else 4.dp),
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun Hair(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Flat.Hair))

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))
