package com.blacksingularities.openamp.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.ThemeMode
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.ui.ChoiceTiles
import com.blacksingularities.openamp.ui.ColorPickerDialog
import com.blacksingularities.openamp.ui.Flat
import com.blacksingularities.openamp.ui.Overline
import com.blacksingularities.openamp.ui.Panel
import com.blacksingularities.openamp.ui.PillButton
import com.blacksingularities.openamp.ui.theme.AccentChoices
import com.blacksingularities.openamp.ui.theme.OpenAmpTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Konfiguracja widżetów: uruchamiana przez launcher przy dodawaniu (i ponownej konfiguracji)
 * widżetu albo z ustawień aplikacji – wtedy pokazuje listę widżetów na pulpicie i dodawanie nowych.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val launchedId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Wycofanie bez zapisu = launcher nie doda widżetu (gdy konfiguracja jest wymagana).
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, launchedId))
        val launchedKind = kindOf(this, launchedId)

        setContent {
            OpenAmpTheme(Graph.settings.value.accent, ThemeMode.BLACK) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    var editing by remember { mutableStateOf(launchedKind?.let { launchedId to it }) }
                    val current = editing
                    if (current != null) {
                        BackHandler(enabled = launchedKind == null) { editing = null }
                        WidgetEditor(
                            id = current.first,
                            kind = current.second,
                            onBack = { if (launchedKind == null) editing = null else finish() },
                            onSaved = {
                                if (launchedKind != null) {
                                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, launchedId))
                                    finish()
                                } else editing = null
                            },
                        )
                    } else {
                        WidgetOverview(onBack = { finish() }, onEdit = { id, kind -> editing = id to kind })
                    }
                }
            }
        }
    }

    companion object {
        fun kindOf(context: Context, id: Int): WidgetKind? {
            if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return null
            val cls = runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className }.getOrNull()
                ?: return null
            return WidgetKind.entries.firstOrNull { WidgetHub.providerClass(it).name == cls }
        }
    }
}

fun widgetKindLabel(k: WidgetKind) = when (k) {
    WidgetKind.COVER -> tr("Okładka")
    WidgetKind.BAR -> tr("Pasek")
    WidgetKind.PLAYER -> tr("Odtwarzacz")
    WidgetKind.SHORTCUTS -> tr("Skróty")
}

fun widgetKindDescription(k: WidgetKind) = when (k) {
    WidgetKind.COVER -> tr("Okładka na całą powierzchnię z tytułem i przyciskami (2×2)")
    WidgetKind.BAR -> tr("Zwarty pasek: okładka, tytuł, serduszko i sterowanie (4×1)")
    WidgetKind.PLAYER -> tr("Pełny odtwarzacz: postęp, czasy, losowanie, powtarzanie (4×2 i więcej)")
    WidgetKind.SHORTCUTS -> tr("Miksy, losuj wszystko, ulubione, wyłącznik czasowy i więcej (4×1 / 4×2)")
}

private fun bgLabel(b: WidgetBackground) = when (b) {
    WidgetBackground.ART_BLUR -> tr("Rozmyta okładka")
    WidgetBackground.ART_COLOR -> tr("Kolor okładki")
    WidgetBackground.DARK -> tr("Ciemne")
    WidgetBackground.AMOLED -> tr("Czarne")
    WidgetBackground.LIGHT -> tr("Jasne")
    WidgetBackground.TRANSPARENT -> tr("Przezroczyste")
    WidgetBackground.CUSTOM -> tr("Własny kolor")
}

private fun accentLabel(a: WidgetAccent) = when (a) {
    WidgetAccent.ART -> tr("Z okładki")
    WidgetAccent.APP -> tr("Jak w aplikacji")
    WidgetAccent.WHITE -> tr("Neutralny")
    WidgetAccent.CUSTOM -> tr("Własny kolor")
}

private fun shapeLabel(s: WidgetCoverShape) = when (s) {
    WidgetCoverShape.ROUNDED -> tr("Zaokrąglona")
    WidgetCoverShape.SQUARE -> tr("Kwadratowa")
    WidgetCoverShape.CIRCLE -> tr("Okrągła")
}

private fun progressLabel(p: WidgetProgressStyle) = when (p) {
    WidgetProgressStyle.LINE -> tr("Linia z uchwytem")
    WidgetProgressStyle.BOLD -> tr("Gruby pasek")
    WidgetProgressStyle.SEGMENTS -> tr("Segmenty")
}

private fun tapLabel(t: WidgetTap) = when (t) {
    WidgetTap.OPEN_PLAYER -> tr("Otwórz odtwarzacz")
    WidgetTap.PLAY_PAUSE -> tr("Odtwórz / pauza")
    WidgetTap.NEXT -> tr("Następny utwór")
    WidgetTap.OPEN_QUEUE -> tr("Kolejka")
    WidgetTap.OPEN_LIBRARY -> tr("Biblioteka")
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), tint = Color.White) }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
        if (action != null) { action(); Spacer(Modifier.width(8.dp)) }
    }
}

// ---------------------------------------------------------------- lista widżetów

@Composable
private fun WidgetOverview(onBack: () -> Unit, onEdit: (Int, WidgetKind) -> Unit) {
    val context = LocalContext.current
    val mgr = remember { AppWidgetManager.getInstance(context) }
    val placed = remember { WidgetKind.entries.flatMap { k -> WidgetHub.ids(context, k).map { it to k } } }
    val canPin = remember { runCatching { mgr.isRequestPinAppWidgetSupported }.getOrDefault(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("Widżety na pulpicie"), onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item {
                Panel {
                    Overline(tr("Twoje widżety"))
                    if (placed.isEmpty()) {
                        Text(tr("Nie masz jeszcze widżetów OpenAMP na pulpicie."), color = Flat.Dim, fontSize = 14.sp)
                    }
                    placed.forEachIndexed { n, (id, kind) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onEdit(id, kind) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Widgets, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(widgetKindLabel(kind), color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(tr("Widżet nr {0}", n + 1), color = Flat.Faded, fontSize = 12.sp)
                            }
                            Icon(Icons.Rounded.Edit, tr("Edytuj"), tint = Flat.Dim)
                        }
                    }
                }
            }
            item {
                Panel {
                    Overline(tr("Dodaj na pulpit"))
                    if (!canPin) Text(
                        tr("Twój launcher nie pozwala dodawać widżetów z aplikacji. Przytrzymaj puste miejsce na pulpicie → Widżety → OpenAMP."),
                        color = Flat.Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp),
                    )
                    WidgetKind.entries.forEach { kind ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(widgetKindLabel(kind), color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(widgetKindDescription(kind), color = Flat.Faded, fontSize = 12.sp)
                            }
                            if (canPin) PillButton(tr("Dodaj"), {
                                runCatching { mgr.requestPinAppWidget(ComponentName(context, WidgetHub.providerClass(kind)), null, null) }
                            }, Modifier.padding(start = 10.dp), icon = Icons.Rounded.Add, active = true)
                        }
                    }
                    Text(
                        tr("Każdy widżet ma własne ustawienia. Na Androidzie 12+ zmienisz je też, przytrzymując widżet na pulpicie."),
                        color = Flat.Faded, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- edytor

@Composable
private fun WidgetEditor(id: Int, kind: WidgetKind, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(WidgetConfigStore.load(context, id, kind)) }
    var preview by remember { mutableStateOf<RemoteViews?>(null) }
    var lightWallpaper by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<String?>(null) }
    val size = remember {
        val o = runCatching { AppWidgetManager.getInstance(context).getAppWidgetOptions(id) }.getOrNull()
        val d = WidgetRenderer.defaultSize(kind)
        WidgetRenderer.Size(
            o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 } ?: d.wDp,
            o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)?.takeIf { it > 0 } ?: d.hDp,
        )
    }

    LaunchedEffect(cfg) {
        preview = withContext(Dispatchers.Default) {
            runCatching { WidgetRenderer.build(context, WidgetHub.input(context, kind, id, cfg, size)) }.getOrNull()
        }
    }

    fun save() {
        WidgetConfigStore.save(context, id, cfg)
        val app = context.applicationContext
        Graph.scope.launch { WidgetHub.render(app, kind, intArrayOf(id)) }
        onSaved()
    }

    when (picker) {
        "bg" -> ColorPickerDialog(cfg.customColor, { picker = null }) { c -> cfg = cfg.copy(customColor = c, background = WidgetBackground.CUSTOM) }
        "accent" -> ColorPickerDialog(cfg.accentColor, { picker = null }) { c -> cfg = cfg.copy(accentColor = c, accent = WidgetAccent.CUSTOM) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("Widżet: {0}", widgetKindLabel(kind)), onBack) {
            PillButton(tr("Zapisz"), { save() }, icon = Icons.Rounded.Check, active = true)
        }

        // Podgląd na tle „tapety” – widać przezroczystość i zaokrąglenia.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val maxW = maxWidth
            val w = minOf(size.wDp.dp, maxW - 24.dp)
            val h = (size.hDp.toFloat() * (w.value / size.wDp)).dp.coerceAtMost(260.dp)
            val wall = if (lightWallpaper) listOf(Color(0xFFE9E4FF), Color(0xFFFFE3D3)) else listOf(Color(0xFF241B3A), Color(0xFF0B2A33))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(wall))
                    .clickable { lightWallpaper = !lightWallpaper }.padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                val rv = preview
                AndroidView(
                    factory = { FrameLayout(it) },
                    update = { fl ->
                        fl.removeAllViews()
                        if (rv != null) runCatching { fl.addView(rv.apply(fl.context, fl)) }
                    },
                    modifier = Modifier.width(w).height(h),
                )
            }
        }
        Text(
            tr("Dotknij tła podglądu, aby zmienić tapetę"),
            color = Flat.Faded, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f).navigationBarsPadding(),
        ) {
            item {
                Panel {
                    Overline(tr("Tło"))
                    ChoiceTiles(WidgetBackground.entries, cfg.background, ::bgLabel) { cfg = cfg.copy(background = it) }
                    if (cfg.background == WidgetBackground.CUSTOM) {
                        ColorRow(cfg.customColor, AccentChoices + listOf(0xFF1C1C1C.toInt(), 0xFF0D2B45.toInt(), 0xFF3A1030.toInt())) {
                            if (it == null) { picker = "bg" } else { cfg = cfg.copy(customColor = it) }
                        }
                    }
                    CfgSlider(tr("Krycie tła"), "${cfg.opacity}%", cfg.opacity.toFloat(), 0f..100f, 19) { cfg = cfg.copy(opacity = (it / 5).roundToInt() * 5) }
                    CfgSlider(tr("Zaokrąglenie rogów"), "${cfg.corner} dp", cfg.corner.toFloat(), 0f..48f, 11) { cfg = cfg.copy(corner = (it / 4).roundToInt() * 4) }
                }
            }
            item {
                Panel {
                    Overline(tr("Kolor akcentu"))
                    ChoiceTiles(WidgetAccent.entries, cfg.accent, ::accentLabel) { cfg = cfg.copy(accent = it) }
                    if (cfg.accent == WidgetAccent.CUSTOM) {
                        ColorRow(cfg.accentColor, AccentChoices + listOf(0xFFFFFFFF.toInt())) {
                            if (it == null) { picker = "accent" } else { cfg = cfg.copy(accentColor = it) }
                        }
                    }
                    CfgSwitch(tr("Wypełniony przycisk odtwarzania"), tr("Koło w kolorze akcentu pod ikoną ▶"), cfg.filledPlay) { cfg = cfg.copy(filledPlay = it) }
                }
            }
            item {
                Panel {
                    Overline(if (kind == WidgetKind.SHORTCUTS) tr("Bieżący utwór") else tr("Okładka"))
                    CfgSwitch(
                        if (kind == WidgetKind.SHORTCUTS) tr("Pasek bieżącego utworu") else tr("Pokazuj okładkę"),
                        when (kind) {
                            WidgetKind.SHORTCUTS -> tr("Okładka, tytuł i sterowanie nad skrótami (gdy widżet jest wysoki)")
                            WidgetKind.COVER -> tr("Okładka wypełnia cały widżet; wyłączona – tło z ustawień powyżej")
                            else -> null
                        },
                        cfg.showCover,
                    ) { cfg = cfg.copy(showCover = it) }
                    if (kind != WidgetKind.COVER && cfg.showCover) {
                        Text(tr("Kształt okładki"), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                        ChoiceTiles(WidgetCoverShape.entries, cfg.coverShape, ::shapeLabel) { cfg = cfg.copy(coverShape = it) }
                    }
                }
            }
            if (kind != WidgetKind.SHORTCUTS) item {
                Panel {
                    Overline(tr("Elementy"))
                    CfgSwitch(tr("Wykonawca"), null, cfg.showArtist) { cfg = cfg.copy(showArtist = it) }
                    if (kind == WidgetKind.PLAYER) {
                        CfgSwitch(tr("Album"), tr("Gdy widżet jest dość wysoki"), cfg.showAlbum) { cfg = cfg.copy(showAlbum = it) }
                        CfgSwitch(tr("Pozycja w kolejce"), tr("Np. 12 / 240 i znacznik wyłącznika czasowego"), cfg.showQueuePos) { cfg = cfg.copy(showQueuePos = it) }
                        CfgSwitch(tr("Losowanie i powtarzanie"), null, cfg.showShuffleRepeat) { cfg = cfg.copy(showShuffleRepeat = it) }
                    }
                    CfgSwitch(tr("Serduszko „lubię”"), tr("Dodaje bieżący utwór do ulubionych"), cfg.showLike) { cfg = cfg.copy(showLike = it) }
                    CfgSwitch(tr("Pasek postępu"), tr("Odświeżany co 2 s podczas grania, gdy ekran jest włączony"), cfg.showProgress) { cfg = cfg.copy(showProgress = it) }
                    if (kind == WidgetKind.PLAYER) {
                        if (cfg.showProgress) {
                            Text(tr("Styl paska"), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                            ChoiceTiles(WidgetProgressStyle.entries, cfg.progressStyle, ::progressLabel) { cfg = cfg.copy(progressStyle = it) }
                        }
                        CfgSwitch(tr("Czas utworu"), null, cfg.showTime) { cfg = cfg.copy(showTime = it) }
                    }
                }
            }
            if (kind == WidgetKind.SHORTCUTS) item {
                ShortcutsEditor(cfg) { cfg = it }
            }
            item {
                Panel {
                    Overline(tr("Tekst i dotyk"))
                    CfgSlider(tr("Rozmiar tekstu"), "${cfg.textScale}%", cfg.textScale.toFloat(), 80f..130f, 9) { cfg = cfg.copy(textScale = (it / 5).roundToInt() * 5) }
                    Text(
                        if (kind == WidgetKind.COVER) tr("Dotknięcie okładki") else tr("Dotknięcie okładki i tytułu"),
                        color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                    )
                    ChoiceTiles(WidgetTap.entries, cfg.tap, ::tapLabel) { cfg = cfg.copy(tap = it) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(tr("Przywróć domyślne"), { cfg = WidgetConfig.defaults(kind) }, icon = Icons.Rounded.Refresh)
                    PillButton(tr("Zapisz"), { save() }, icon = Icons.Rounded.Check, active = true)
                }
            }
        }
    }
}

@Composable
private fun ShortcutsEditor(cfg: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    Panel {
        Overline(tr("Skróty ({0}/{1})", cfg.shortcuts.size, WidgetConfig.MAX_SHORTCUTS))
        CfgSwitch(tr("Podpisy pod ikonami"), null, cfg.shortcutLabels) { onChange(cfg.copy(shortcutLabels = it)) }
        cfg.shortcuts.forEachIndexed { n, sc ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).background(Flat.Block, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${n + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(WidgetRenderer.shortcutLabel(sc), color = Color.White, modifier = Modifier.weight(1f).padding(start = 12.dp))
                IconButton(onClick = {
                    if (n > 0) onChange(cfg.copy(shortcuts = cfg.shortcuts.toMutableList().apply { add(n - 1, removeAt(n)) }))
                }, enabled = n > 0) { Icon(Icons.Rounded.KeyboardArrowUp, tr("W górę"), tint = if (n > 0) Color.White else Flat.Faded) }
                IconButton(onClick = {
                    if (n < cfg.shortcuts.lastIndex) onChange(cfg.copy(shortcuts = cfg.shortcuts.toMutableList().apply { add(n + 1, removeAt(n)) }))
                }, enabled = n < cfg.shortcuts.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, tr("W dół"), tint = if (n < cfg.shortcuts.lastIndex) Color.White else Flat.Faded) }
                IconButton(onClick = { onChange(cfg.copy(shortcuts = cfg.shortcuts - sc)) }, enabled = cfg.shortcuts.size > 1) {
                    Icon(Icons.Rounded.Close, tr("Usuń"), tint = if (cfg.shortcuts.size > 1) Color.White else Flat.Faded)
                }
            }
        }
        val rest = WidgetShortcut.entries.filter { it !in cfg.shortcuts }
        if (rest.isNotEmpty() && cfg.shortcuts.size < WidgetConfig.MAX_SHORTCUTS) {
            Text(tr("Dodaj skrót"), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rest) { sc ->
                    PillButton(WidgetRenderer.shortcutLabel(sc), { onChange(cfg.copy(shortcuts = cfg.shortcuts + sc)) }, icon = Icons.Rounded.Add)
                }
            }
        }
        Text(
            tr("Na wąskim widżecie mieszczą się tylko pierwsze skróty – kolejność ma znaczenie."),
            color = Flat.Faded, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun ColorRow(selected: Int, options: List<Int>, onPick: (Int?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
        item {
            Box(
                Modifier.size(38.dp).background(
                    Brush.sweepGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f % 360f, 0.8f, 1f))) }),
                    CircleShape,
                ).clickable { onPick(null) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Palette, tr("Dowolny kolor"), tint = Color.Black, modifier = Modifier.size(20.dp)) }
        }
        val all = if (selected in options) options else listOf(selected) + options
        items(all) { c ->
            val sel = c == selected
            Box(
                Modifier.size(38.dp).background(Color(c), CircleShape)
                    .then(if (sel) Modifier.border(3.dp, Color.White, CircleShape) else Modifier.border(1.dp, Flat.Hair, CircleShape))
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (sel) Icon(
                    Icons.Rounded.Check, null, modifier = Modifier.size(18.dp),
                    tint = if (androidx.core.graphics.ColorUtils.calculateLuminance(c) > 0.5) Color.Black else Color.White,
                )
            }
        }
    }
}

@Composable
private fun CfgSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = Flat.Faded, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CfgSlider(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Slider(value = current, onValueChange = onChange, valueRange = range, steps = steps)
    }
}
