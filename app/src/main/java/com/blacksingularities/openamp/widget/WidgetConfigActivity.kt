package com.blacksingularities.openamp.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.rounded.AlignHorizontalRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Padding
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.ui.ColorPickerDialog
import com.blacksingularities.openamp.ui.Flat
import com.blacksingularities.openamp.ui.HintBubble
import com.blacksingularities.openamp.ui.HintState
import com.blacksingularities.openamp.ui.LocalHints
import com.blacksingularities.openamp.ui.ArtworkLoader
import com.blacksingularities.openamp.ui.PillButton
import com.blacksingularities.openamp.ui.RoundButton
import com.blacksingularities.openamp.ui.theme.vibrantColor
import androidx.compose.runtime.collectAsState
import com.blacksingularities.openamp.ui.SectionHeader
import com.blacksingularities.openamp.ui.SetAction
import com.blacksingularities.openamp.ui.SetCard
import com.blacksingularities.openamp.ui.Shapes
import com.blacksingularities.openamp.ui.SetDropdown
import com.blacksingularities.openamp.ui.SetSwitch
import com.blacksingularities.openamp.ui.TabChips
import com.blacksingularities.openamp.ui.WidgetBarPreview
import com.blacksingularities.openamp.ui.SetNote
import com.blacksingularities.openamp.ui.SetSegment
import com.blacksingularities.openamp.ui.SetSlider
import com.blacksingularities.openamp.ui.Tile
import com.blacksingularities.openamp.ui.TileGrid
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
            // Ten sam motyw i akcent co w aplikacji – edytor z launchera wygląda dokładnie jak w ustawieniach.
            val s by Graph.settings.state.collectAsState()
            val art by Graph.artAccent.collectAsState()
            LaunchedEffect(s.artworkColors) {
                if (s.artworkColors && Graph.artAccent.value == null) {
                    val ctx = this@WidgetConfigActivity
                    val track = WidgetHub.current(ctx).trackId?.let { Graph.library.get(it) }
                    if (track != null) withContext(Dispatchers.Default) {
                        ArtworkLoader.load(ctx, track, 900)?.let { vibrantColor(it) }
                    }?.let { Graph.artAccent.value = it }
                }
            }
            OpenAmpTheme(if (s.artworkColors) art ?: s.accent else s.accent, s.theme) {
                val hints = remember { HintState() }
                CompositionLocalProvider(LocalHints provides hints) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                        HintBubble(hints, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp))
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

private fun widgetKindIcon(k: WidgetKind): ImageVector = when (k) {
    WidgetKind.COVER -> Icons.Rounded.Image
    WidgetKind.BAR -> Icons.Rounded.LinearScale
    WidgetKind.PLAYER -> Icons.Rounded.PlayCircle
    WidgetKind.SHORTCUTS -> Icons.Rounded.Widgets
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

private fun posLabel(p: WidgetCoverPos) = when (p) {
    WidgetCoverPos.LEFT -> tr("Po lewej")
    WidgetCoverPos.RIGHT -> tr("Po prawej")
    WidgetCoverPos.TOP -> tr("U góry")
    WidgetCoverPos.BACKGROUND -> tr("Jako tło")
}

private fun posIcon(p: WidgetCoverPos): ImageVector = when (p) {
    WidgetCoverPos.LEFT -> Icons.Rounded.AlignHorizontalLeft
    WidgetCoverPos.RIGHT -> Icons.Rounded.AlignHorizontalRight
    WidgetCoverPos.TOP -> Icons.Rounded.VerticalAlignTop
    WidgetCoverPos.BACKGROUND -> Icons.Rounded.Wallpaper
}

private fun progressLabel(p: WidgetProgressStyle) = when (p) {
    WidgetProgressStyle.APP -> tr("Jak w aplikacji")
    WidgetProgressStyle.THIN -> tr("Cienka linia")
    WidgetProgressStyle.LINE -> tr("Linia z uchwytem")
    WidgetProgressStyle.BOLD -> tr("Gruby pasek")
    WidgetProgressStyle.SEGMENTS -> tr("Segmenty LED")
    WidgetProgressStyle.WAVE -> tr("Fala dźwiękowa")
    WidgetProgressStyle.MIRROR -> tr("Lustrzana fala")
    WidgetProgressStyle.CONTOUR -> tr("Linia konturu")
    WidgetProgressStyle.DOTS -> tr("Matryca kropek")
}

private fun progressIcon(p: WidgetProgressStyle): ImageVector = when (p) {
    WidgetProgressStyle.APP -> Icons.Rounded.PlayCircle
    WidgetProgressStyle.THIN -> Icons.Rounded.Notes
    WidgetProgressStyle.LINE -> Icons.Rounded.LinearScale
    WidgetProgressStyle.BOLD -> Icons.Rounded.SpaceBar
    WidgetProgressStyle.SEGMENTS -> Icons.Rounded.MoreHoriz
    WidgetProgressStyle.WAVE -> Icons.Rounded.GraphicEq
    WidgetProgressStyle.MIRROR -> Icons.Rounded.Waves
    WidgetProgressStyle.CONTOUR -> Icons.Rounded.ShowChart
    WidgetProgressStyle.DOTS -> Icons.Rounded.MoreHoriz
}

private fun tapLabel(t: WidgetTap) = when (t) {
    WidgetTap.OPEN_PLAYER -> tr("Otwórz odtwarzacz")
    WidgetTap.PLAY_PAUSE -> tr("Odtwórz / pauza")
    WidgetTap.NEXT -> tr("Następny utwór")
    WidgetTap.OPEN_QUEUE -> tr("Kolejka")
    WidgetTap.OPEN_LIBRARY -> tr("Biblioteka")
}

/** Rozmiary ustawiane w dp działają dopiero od Androida 12 – na starszych zostają z układu. */
private val sizesSupported = Build.VERSION.SDK_INT >= 31

@Composable
private fun TopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Nagłówek jak w ustawieniach aplikacji.
        RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), onBack, background = Color.Transparent, size = 40.dp)
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Flat.Fg, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1)
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
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item { SectionHeader(Icons.Rounded.Widgets, tr("Twoje widżety")) }
            item {
                SetCard(null) {
                    if (placed.isEmpty()) SetNote(tr("Nie masz jeszcze widżetów OpenAMP na pulpicie."))
                    placed.forEachIndexed { n, (id, kind) ->
                        SetAction(widgetKindIcon(kind), widgetKindLabel(kind), tr("Widżet nr {0}", n + 1), trailing = {
                            Icon(Icons.Rounded.Edit, tr("Edytuj"), tint = Flat.Dim, modifier = Modifier.size(18.dp))
                        }) { onEdit(id, kind) }
                    }
                }
            }
            item { SectionHeader(Icons.Rounded.Add, tr("Dodaj na pulpit")) }
            item {
                SetCard(null) {
                    if (!canPin) SetNote(tr("Twój launcher nie pozwala dodawać widżetów z aplikacji. Przytrzymaj puste miejsce na pulpicie → Widżety → OpenAMP."))
                    WidgetKind.entries.forEach { kind ->
                        SetAction(widgetKindIcon(kind), widgetKindLabel(kind), widgetKindDescription(kind), enabled = canPin, trailing = {
                            if (canPin) Icon(Icons.Rounded.Add, tr("Dodaj"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }) {
                            runCatching { mgr.requestPinAppWidget(ComponentName(context, WidgetHub.providerClass(kind)), null, null) }
                        }
                    }
                    SetNote(tr("Każdy widżet ma własne ustawienia. Na Androidzie 12+ zmienisz je też, przytrzymując widżet na pulpicie."))
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

    fun set(t: (WidgetConfig) -> WidgetConfig) { cfg = t(cfg) }

    when (picker) {
        "bg" -> ColorPickerDialog(cfg.customColor, { picker = null }) { c -> set { it.copy(customColor = c, background = WidgetBackground.CUSTOM) } }
        "accent" -> ColorPickerDialog(cfg.accentColor, { picker = null }) { c -> set { it.copy(accentColor = c, accent = WidgetAccent.CUSTOM) } }
    }

    val player = kind == WidgetKind.PLAYER
    val hasTrack = kind != WidgetKind.SHORTCUTS
    val bgCover = cfg.showCover && (kind == WidgetKind.COVER || (kind != WidgetKind.SHORTCUTS && cfg.coverPos == WidgetCoverPos.BACKGROUND))

    Column(Modifier.fillMaxSize()) {
        TopBar(tr("Widżet: {0}", widgetKindLabel(kind)), onBack) {
            PillButton(tr("Zapisz"), { save() }, icon = Icons.Rounded.Check, active = true)
        }

        // Podgląd na tle „tapety” – widać przezroczystość i zaokrąglenia.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val maxW = maxWidth
            // Widżet układany w prawdziwym rozmiarze z pulpitu i tylko pomniejszany – inaczej
            // wymiary w dp (przyciski, tekst, taśma) dawałyby inny układ niż na ekranie głównym.
            val k = minOf(1f, (maxW - 24.dp).value / size.wDp, 260f / size.hDp)
            val w = (size.wDp * k).dp
            val h = (size.hDp * k).dp
            val wall = if (lightWallpaper) listOf(Color(0xFFE9E4FF), Color(0xFFFFE3D3)) else listOf(Color(0xFF241B3A), Color(0xFF0B2A33))
            Box(
                Modifier.fillMaxWidth().clip(Shapes.XLarge).background(Brush.linearGradient(wall))
                    .clickable { lightWallpaper = !lightWallpaper }.padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                val rv = preview
                Box(Modifier.width(w).height(h), contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { FrameLayout(it) },
                        update = { fl ->
                            fl.removeAllViews()
                            if (rv != null) runCatching { fl.addView(rv.apply(fl.context, fl)) }
                        },
                        modifier = Modifier.requiredSize(size.wDp.dp, size.hDp.dp).graphicsLayer { scaleX = k; scaleY = k },
                    )
                }
            }
        }
        Text(
            tr("Dotknij tła podglądu, aby zmienić tapetę · przytrzymaj kafelek, aby zobaczyć opis"),
            color = Flat.Faded, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp, start = 16.dp, end = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        // Zakładki zamiast jednej długiej listy – podgląd zostaje na górze, ustawienia mieszczą się na ekranie.
        val tabs = remember(kind) { EditorTab.entries.filter { it.applies(kind) } }
        var tab by remember { mutableStateOf(tabs.first()) }
        TabChips(tabs, tab, { it.label() }, { it.icon() }, Modifier.padding(top = 4.dp, bottom = 6.dp)) { tab = it }

        LazyColumn(
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f).navigationBarsPadding(),
        ) {
            when (tab) {
                EditorTab.LAYOUT -> {
                    item {
                        SetCard(null) {
                            if (player || kind == WidgetKind.BAR) {
                                val options = if (player) WidgetCoverPos.entries else WidgetCoverPos.entries - WidgetCoverPos.TOP
                                SetDropdown(
                                    Icons.Rounded.Image, tr("Okładka"), listOf<WidgetCoverPos?>(null) + options, cfg.coverPos.takeIf { cfg.showCover },
                                    { it?.let(::posLabel) ?: tr("Ukryta") },
                                ) { v -> set { if (v == null) it.copy(showCover = false) else it.copy(showCover = true, coverPos = v) } }
                            } else {
                                SetSwitch(
                                    Icons.Rounded.Image, if (kind == WidgetKind.SHORTCUTS) tr("Pasek utworu") else tr("Okładka"),
                                    when (kind) {
                                        WidgetKind.SHORTCUTS -> tr("Okładka, tytuł i sterowanie nad skrótami (gdy widżet jest wysoki)")
                                        else -> tr("Okładka wypełnia cały widżet; wyłączona – tło z ustawień")
                                    },
                                    cfg.showCover,
                                ) { v -> set { it.copy(showCover = v) } }
                            }
                            if (player) SetSegment(
                                Icons.Rounded.ViewColumn, tr("Przyciski"), WidgetControlsPos.entries, cfg.controlsPos, ::controlsPosLabel,
                                optionIcon = ::controlsPosIcon,
                            ) { v -> set { it.copy(controlsPos = v) } }
                            if (hasTrack) SetDropdown(Icons.Rounded.TouchApp, tr("Dotknięcie okładki i tytułu"), WidgetTap.entries, cfg.tap, ::tapLabel) { v -> set { it.copy(tap = v) } }
                        }
                    }
                    if (player) item { SetNote(controlsPosHint(cfg.controlsPos)) }
                    if (hasTrack) item {
                        SetCard(null) {
                            SetSwitch(Icons.Rounded.Compress, tr("Zwarty układ"), tr("Bez odstępów między okładką, tekstem, taśmą i przyciskami (Android 12+)"), cfg.compact) { v -> set { it.copy(compact = v) } }
                            SetSlider(Icons.Rounded.Padding, tr("Margines wewnętrzny"), "${cfg.padding} dp", cfg.padding.toFloat(), 0f..24f, 23) { v -> set { it.copy(padding = v.roundToInt()) } }
                        }
                    }
                }

                EditorTab.COVER -> {
                    if (!cfg.showCover) item { SetCard(null) { SetNote(tr("Okładka jest ukryta – włącz ją w zakładce „Układ”.")) } }
                    else item {
                        SetCard(null) {
                            if (kind == WidgetKind.COVER) {
                                SetSlider(Icons.Rounded.ZoomIn, tr("Powiększenie kadru"), "${cfg.coverSize.coerceAtLeast(100)}%", cfg.coverSize.coerceAtLeast(100).toFloat(), 100f..200f, 19) { v ->
                                    set { it.copy(coverSize = (v / 5).roundToInt() * 5) }
                                }
                            } else if (!bgCover) {
                                SetSlider(
                                    Icons.Rounded.ZoomIn, tr("Rozmiar okładki"), "${cfg.coverSize.coerceAtMost(100)}%", cfg.coverSize.coerceIn(30, 100).toFloat(), 30f..100f, 13,
                                    subtitle = if (!sizesSupported) tr("Wymaga Androida 12 lub nowszego") else if (player) tr("100% – cała wysokość wiersza okładki") else null,
                                    enabled = sizesSupported,
                                ) { v -> set { it.copy(coverSize = (v / 5).roundToInt() * 5) } }
                            }
                            SetSlider(Icons.Rounded.Crop, tr("Kadr w pionie"), focusLabel(cfg.coverFocus), cfg.coverFocus.toFloat(), 0f..100f, 9, subtitle = tr("Która część okładki zostaje przy przycięciu")) { v ->
                                set { it.copy(coverFocus = (v / 10).roundToInt() * 10) }
                            }
                            if (kind != WidgetKind.COVER && !bgCover) {
                                SetSegment(Icons.Rounded.RoundedCorner, tr("Kształt okładki"), WidgetCoverShape.entries, cfg.coverShape, ::shapeLabel, short = {
                                    when (it) { WidgetCoverShape.ROUNDED -> tr("Zaokr."); WidgetCoverShape.SQUARE -> tr("Kwadrat"); WidgetCoverShape.CIRCLE -> tr("Koło") }
                                }) { v -> set { it.copy(coverShape = v) } }
                                if (cfg.coverShape == WidgetCoverShape.ROUNDED) SetSlider(
                                    Icons.Rounded.RoundedCorner, tr("Zaokrąglenie okładki"), "${cfg.coverRadius}%", cfg.coverRadius.toFloat(), 0f..50f, 9,
                                    subtitle = tr("0% – ostre rogi, 50% – koło"),
                                ) { v -> set { it.copy(coverRadius = (v / 5).roundToInt() * 5) } }
                            }
                        }
                    }
                }

                EditorTab.BAR -> {
                    item {
                        SetCard(null) {
                            SetSwitch(Icons.Rounded.LinearScale, tr("Pasek postępu"), tr("Odświeżany co 2 s podczas grania, gdy ekran jest włączony"), cfg.showProgress) { v -> set { it.copy(showProgress = v) } }
                            if (cfg.showProgress) {
                                val seek = Graph.settings.value.seekStyle
                                val accent = MaterialTheme.colorScheme.primary.toArgb()
                                SetDropdown(
                                    Icons.Rounded.Waves, tr("Styl paska"), WidgetProgressStyle.entries, cfg.progressStyle, ::progressLabel,
                                    preview = { v, m -> WidgetBarPreview(v, seek, accent, m) },
                                ) { v -> set { it.copy(progressStyle = v, progressHeight = suggestedHeight(kind, v, it.progressHeight)) } }
                                SetSlider(
                                    Icons.Rounded.Height, tr("Wysokość paska"), "${cfg.progressHeight} dp", cfg.progressHeight.toFloat(), 2f..40f, 18,
                                    subtitle = if (sizesSupported) tr("Fale wyglądają najlepiej od 16 dp") else tr("Wymaga Androida 12 lub nowszego"), enabled = sizesSupported,
                                ) { v -> set { it.copy(progressHeight = ((v / 2).roundToInt() * 2).coerceAtLeast(2)) } }
                                if (WidgetRenderer.usesWave(WidgetRenderer.resolveStyle(cfg.progressStyle, seek))) {
                                    SetNote(tr("Fala dźwiękowa utworu jest liczona raz, w tle – do tego czasu widać zastępczy kształt."))
                                }
                            }
                        }
                    }
                    if (player) item {
                        SetCard(null) {
                            SetSegment(
                                Icons.Rounded.Schedule, tr("Czas utworu"), listOf<WidgetTimePos?>(null, WidgetTimePos.BELOW, WidgetTimePos.SIDES), cfg.timePos.takeIf { cfg.showTime },
                                { when (it) { null -> tr("Ukryty"); WidgetTimePos.BELOW -> tr("Pod paskiem"); WidgetTimePos.SIDES -> tr("Po bokach paska") } },
                                short = { when (it) { null -> tr("Brak"); WidgetTimePos.BELOW -> tr("Pod"); WidgetTimePos.SIDES -> tr("Boki") } },
                            ) { v -> set { if (v == null) it.copy(showTime = false) else it.copy(showTime = true, timePos = v) } }
                            if (cfg.controlsPos != WidgetControlsPos.ABOVE_BAR) SetSlider(
                                Icons.Rounded.VerticalAlignTop, tr("Odstęp nad paskiem"), "${cfg.barTop} dp", cfg.barTop.toFloat(), -24f..24f, 47,
                                subtitle = if (sizesSupported) tr("Między okładką z tytułem a paskiem postępu; ujemny nasuwa pasek wyżej") else tr("Wymaga Androida 12 lub nowszego"), enabled = sizesSupported,
                            ) { v -> set { it.copy(barTop = v.roundToInt()) } }
                            if (cfg.controlsPos != WidgetControlsPos.IN_COLUMN) SetSlider(
                                Icons.Rounded.SpaceBar, tr("Odstęp od przycisków"), "${cfg.progressGap} dp", cfg.progressGap.toFloat(), -16f..16f, 31,
                                subtitle = if (sizesSupported) null else tr("Wymaga Androida 12 lub nowszego"), enabled = sizesSupported,
                            ) { v -> set { it.copy(progressGap = v.roundToInt()) } }
                        }
                    }
                }

                EditorTab.BUTTONS -> {
                    item {
                        SetCard(null) {
                            CardTiles(
                                listOfNotNull(
                                    Tile(Icons.Rounded.SkipNext, tr("Poprz. / nast."), tr("Przyciski poprzedniego i następnego utworu"), cfg.showPrevNext) { v -> set { it.copy(showPrevNext = v) } },
                                    if (player) Tile(Icons.Rounded.Shuffle, tr("Losowanie"), tr("Przycisk losowej kolejności (gdy widżet jest dość szeroki)"), cfg.showShuffle) { v -> set { it.copy(showShuffle = v) } } else null,
                                    if (player) Tile(Icons.Rounded.Repeat, tr("Powtarzanie"), tr("Przycisk powtarzania: wszystko / jeden utwór (gdy widżet jest dość szeroki)"), cfg.showRepeat) { v -> set { it.copy(showRepeat = v) } } else null,
                                    Tile(Icons.Rounded.Favorite, tr("Serduszko"), tr("Dodaje bieżący utwór do ulubionych"), cfg.showLike) { v -> set { it.copy(showLike = v) } },
                                ),
                            )
                            SetDropdown(Icons.Rounded.PlayCircle, tr("Styl przycisków"), WidgetButtonStyle.entries, cfg.buttonStyle, ::buttonStyleLabel) { v -> set { it.copy(buttonStyle = v) } }
                            if (player) {
                                SetSlider(
                                    Icons.Rounded.VerticalAlignTop, tr("Odstęp nad przyciskami"), "${cfg.controlsTop} dp", cfg.controlsTop.toFloat(), 0f..24f, 23,
                                    subtitle = if (sizesSupported) null else tr("Wymaga Androida 12 lub nowszego"), enabled = sizesSupported,
                                ) { v -> set { it.copy(controlsTop = v.roundToInt()) } }
                                SetSlider(
                                    Icons.Rounded.VerticalAlignBottom, tr("Odstęp pod przyciskami"), "${cfg.controlsBottom} dp", cfg.controlsBottom.toFloat(), 0f..24f, 23,
                                    subtitle = if (sizesSupported) null else tr("Wymaga Androida 12 lub nowszego"), enabled = sizesSupported,
                                ) { v -> set { it.copy(controlsBottom = v.roundToInt()) } }
                            }
                            SetSlider(
                                Icons.Rounded.ZoomIn, tr("Wielkość przycisków"), "${cfg.buttonScale}%", cfg.buttonScale.toFloat(), 60f..140f, 15,
                                subtitle = if (sizesSupported) null else tr("Wymaga Androida 12 lub nowszego"), enabled = sizesSupported,
                            ) { v -> set { it.copy(buttonScale = (v / 5).roundToInt() * 5) } }
                        }
                    }
                }

                EditorTab.TEXT -> {
                    item {
                        SetCard(null) {
                            if (hasTrack) CardTiles(
                                listOfNotNull(
                                    Tile(Icons.Rounded.Person, tr("Wykonawca"), tr("Wykonawca pod tytułem"), cfg.showArtist) { v -> set { it.copy(showArtist = v) } },
                                    if (player) Tile(Icons.Rounded.Album, tr("Album"), tr("Nazwa albumu"), cfg.showAlbum) { v -> set { it.copy(showAlbum = v) } } else null,
                                    if (player && cfg.showAlbum) Tile(Icons.Rounded.Notes, tr("Wyk. – album"), tr("Wykonawca i album w jednym wierszu: „Wykonawca – Album”"), cfg.artistAlbumLine) { v -> set { it.copy(artistAlbumLine = v) } } else null,
                                    if (player) Tile(Icons.Rounded.FormatListNumbered, tr("Kolejka"), tr("Pozycja w kolejce, np. 12 / 240, i znacznik wyłącznika czasowego"), cfg.showQueuePos) { v -> set { it.copy(showQueuePos = v) } } else null,
                                    Tile(Icons.Rounded.FormatBold, tr("Pogrubiony"), tr("Pogrubiony tytuł utworu"), cfg.boldTitle) { v -> set { it.copy(boldTitle = v) } },
                                    Tile(Icons.Rounded.FormatAlignCenter, tr("Wyśrodkuj"), tr("Tytuł, wykonawca i album wyśrodkowane"), cfg.centerText) { v -> set { it.copy(centerText = v) } },
                                ),
                            ) else CardTiles(
                                listOf(
                                    Tile(Icons.Rounded.Label, tr("Podpisy"), tr("Podpisy pod ikonami skrótów"), cfg.shortcutLabels) { v -> set { it.copy(shortcutLabels = v) } },
                                    Tile(Icons.Rounded.FormatBold, tr("Pogrubiony"), tr("Pogrubiony tytuł utworu"), cfg.boldTitle) { v -> set { it.copy(boldTitle = v) } },
                                    Tile(Icons.Rounded.FormatAlignCenter, tr("Wyśrodkuj"), tr("Tytuł i wykonawca wyśrodkowane"), cfg.centerText) { v -> set { it.copy(centerText = v) } },
                                ),
                            )
                            SetSlider(Icons.Rounded.FormatSize, tr("Rozmiar tekstu"), "${cfg.textScale}%", cfg.textScale.toFloat(), 80f..130f, 9) { v -> set { it.copy(textScale = (v / 5).roundToInt() * 5) } }
                            if (hasTrack) SetSegment(Icons.Rounded.Notes, tr("Wiersze tytułu"), listOf(1, 2, 3), cfg.titleLines, { tr("Maks. {0}", it) }, short = { "$it" }) { v -> set { it.copy(titleLines = v) } }
                        }
                    }
                }

                EditorTab.SHORTCUTS -> {
                    item { SectionHeader(Icons.Rounded.Widgets, tr("Skróty ({0}/{1})", cfg.shortcuts.size, WidgetConfig.MAX_SHORTCUTS)) }
                    item { ShortcutsEditor(cfg) { cfg = it } }
                }

                EditorTab.LOOK -> {
                    item {
                        SetCard(null) {
                            SetDropdown(Icons.Rounded.Wallpaper, tr("Tło"), WidgetBackground.entries, cfg.background, ::bgLabel) { v -> set { it.copy(background = v) } }
                            if (cfg.background == WidgetBackground.CUSTOM) Box(Modifier.padding(start = 40.dp)) {
                                ColorRow(cfg.customColor, AccentChoices + listOf(0xFF1C1C1C.toInt(), 0xFF0D2B45.toInt(), 0xFF3A1030.toInt())) {
                                    if (it == null) picker = "bg" else set { c -> c.copy(customColor = it) }
                                }
                            }
                            if (bgCover) SetNote(tr("Tło przykrywa okładka – poniższe ustawienia działają, gdy utwór nie ma okładki."))
                            SetSlider(Icons.Rounded.Opacity, tr("Krycie tła"), "${cfg.opacity}%", cfg.opacity.toFloat(), 0f..100f, 19) { v -> set { it.copy(opacity = (v / 5).roundToInt() * 5) } }
                            SetSlider(Icons.Rounded.RoundedCorner, tr("Zaokrąglenie rogów"), "${cfg.corner} dp", cfg.corner.toFloat(), 0f..48f, 11) { v -> set { it.copy(corner = (v / 4).roundToInt() * 4) } }
                        }
                    }
                    item {
                        SetCard(null) {
                            SetDropdown(Icons.Rounded.Palette, tr("Kolor akcentu"), WidgetAccent.entries, cfg.accent, ::accentLabel) { v -> set { it.copy(accent = v) } }
                            if (cfg.accent == WidgetAccent.CUSTOM) Box(Modifier.padding(start = 40.dp)) {
                                ColorRow(cfg.accentColor, AccentChoices + listOf(0xFFFFFFFF.toInt())) {
                                    if (it == null) picker = "accent" else set { c -> c.copy(accentColor = it) }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(tr("Przywróć domyślne"), { cfg = WidgetConfig.defaults(kind) }, icon = Icons.Rounded.Refresh, compact = true)
                    PillButton(tr("Zapisz"), { save() }, icon = Icons.Rounded.Check, active = true, compact = true)
                }
            }
        }
    }
}

private fun controlsPosLabel(p: WidgetControlsPos) = when (p) {
    WidgetControlsPos.BELOW_BAR -> tr("Przyciski pod taśmą")
    WidgetControlsPos.ABOVE_BAR -> tr("Przyciski nad taśmą")
    WidgetControlsPos.IN_COLUMN -> tr("Przyciski obok okładki")
}

private fun controlsPosIcon(p: WidgetControlsPos): ImageVector = when (p) {
    WidgetControlsPos.BELOW_BAR -> Icons.Rounded.VerticalAlignBottom
    WidgetControlsPos.ABOVE_BAR -> Icons.Rounded.VerticalAlignTop
    WidgetControlsPos.IN_COLUMN -> Icons.Rounded.ViewColumn
}

private fun buttonStyleLabel(s: WidgetButtonStyle) = when (s) {
    WidgetButtonStyle.CIRCLE -> tr("Koło pod ▶")
    WidgetButtonStyle.SQUARE -> tr("Zaokrąglony kwadrat pod ▶")
    WidgetButtonStyle.RING -> tr("Obwódka wokół ▶")
    WidgetButtonStyle.PLAIN -> tr("Same ikony")
    WidgetButtonStyle.BUBBLES -> tr("Wszystkie w kołach")
}

private fun controlsPosHint(p: WidgetControlsPos) = when (p) {
    WidgetControlsPos.BELOW_BAR -> tr("Okładka i tytuł u góry, pod nimi taśma, na samym dole przyciski.")
    WidgetControlsPos.ABOVE_BAR -> tr("Przyciski w osobnym wierszu pod okładką i tytułem, taśma na dole.")
    WidgetControlsPos.IN_COLUMN -> tr("Przyciski wyśrodkowane pod tytułem, obok okładki – okładka może sięgać aż do taśmy na dole.")
}

/** Zakładki edytora widżetu. */
private enum class EditorTab {
    LAYOUT, COVER, BAR, BUTTONS, TEXT, SHORTCUTS, LOOK;

    fun applies(kind: WidgetKind) = when (this) {
        COVER -> true
        BAR, BUTTONS -> kind != WidgetKind.SHORTCUTS
        SHORTCUTS -> kind == WidgetKind.SHORTCUTS
        else -> true
    }

    fun label() = when (this) {
        LAYOUT -> tr("Układ")
        COVER -> tr("Okładka")
        BAR -> tr("Pasek")
        BUTTONS -> tr("Przyciski")
        TEXT -> tr("Tekst")
        SHORTCUTS -> tr("Skróty")
        LOOK -> tr("Wygląd")
    }

    fun icon(): ImageVector = when (this) {
        LAYOUT -> Icons.Rounded.Dashboard
        COVER -> Icons.Rounded.Image
        BAR -> Icons.Rounded.LinearScale
        BUTTONS -> Icons.Rounded.PlayCircle
        TEXT -> Icons.Rounded.TextFields
        SHORTCUTS -> Icons.Rounded.Widgets
        LOOK -> Icons.Rounded.Palette
    }
}

/** Kafelki wewnątrz karty – z marginesem, żeby nie dotykały jej krawędzi. */
@Composable
private fun CardTiles(tiles: List<Tile>) {
    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) { TileGrid(tiles) }
}

private fun focusLabel(v: Int) = when {
    v <= 10 -> tr("Góra")
    v >= 90 -> tr("Dół")
    v in 40..60 -> tr("Środek")
    else -> "$v%"
}

/** Po zmianie stylu: fale potrzebują wyższego paska, cienka linia – niskiego. */
private fun suggestedHeight(kind: WidgetKind, style: WidgetProgressStyle, current: Int): Int {
    val resolved = WidgetRenderer.resolveStyle(style, Graph.settings.value.seekStyle)
    return when {
        WidgetRenderer.usesWave(resolved) && resolved != WidgetProgressStyle.SEGMENTS -> current.coerceAtLeast(if (kind == WidgetKind.PLAYER) 20 else 12)
        resolved == WidgetProgressStyle.THIN -> current.coerceAtMost(6)
        else -> current
    }
}

@Composable
private fun ShortcutsEditor(cfg: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    SetCard(null) {
        cfg.shortcuts.forEachIndexed { n, sc ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).background(Flat.Block, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${n + 1}", color = Flat.Fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(WidgetRenderer.shortcutLabel(sc), color = Flat.Fg, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                IconButton(onClick = {
                    if (n > 0) onChange(cfg.copy(shortcuts = cfg.shortcuts.toMutableList().apply { add(n - 1, removeAt(n)) }))
                }, enabled = n > 0) { Icon(Icons.Rounded.KeyboardArrowUp, tr("W górę"), tint = if (n > 0) Flat.Fg else Flat.Faded) }
                IconButton(onClick = {
                    if (n < cfg.shortcuts.lastIndex) onChange(cfg.copy(shortcuts = cfg.shortcuts.toMutableList().apply { add(n + 1, removeAt(n)) }))
                }, enabled = n < cfg.shortcuts.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, tr("W dół"), tint = if (n < cfg.shortcuts.lastIndex) Flat.Fg else Flat.Faded) }
                IconButton(onClick = { onChange(cfg.copy(shortcuts = cfg.shortcuts - sc)) }, enabled = cfg.shortcuts.size > 1) {
                    Icon(Icons.Rounded.Close, tr("Usuń"), tint = if (cfg.shortcuts.size > 1) Flat.Fg else Flat.Faded)
                }
            }
        }
        val rest = WidgetShortcut.entries.filter { it !in cfg.shortcuts }
        if (rest.isNotEmpty() && cfg.shortcuts.size < WidgetConfig.MAX_SHORTCUTS) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
            ) {
                items(rest) { sc ->
                    PillButton(WidgetRenderer.shortcutLabel(sc), { onChange(cfg.copy(shortcuts = cfg.shortcuts + sc)) }, icon = Icons.Rounded.Add, compact = true)
                }
            }
        }
        SetNote(tr("Na wąskim widżecie mieszczą się tylko pierwsze skróty – kolejność ma znaczenie."))
    }
}

@Composable
private fun ColorRow(selected: Int, options: List<Int>, onPick: (Int?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        item {
            Box(
                Modifier.size(34.dp).background(
                    Brush.sweepGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f % 360f, 0.8f, 1f))) }),
                    CircleShape,
                ).clickable { onPick(null) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Palette, tr("Dowolny kolor"), tint = Color.Black, modifier = Modifier.size(18.dp)) }
        }
        val all = if (selected in options) options else listOf(selected) + options
        items(all) { c ->
            val sel = c == selected
            Box(
                Modifier.size(34.dp).background(Color(c), CircleShape)
                    .then(if (sel) Modifier.border(3.dp, Flat.Fg, CircleShape) else Modifier.border(1.dp, Flat.Hair, CircleShape))
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (sel) Icon(
                    Icons.Rounded.Check, null, modifier = Modifier.size(16.dp),
                    tint = if (androidx.core.graphics.ColorUtils.calculateLuminance(c) > 0.5) Color.Black else Flat.Fg,
                )
            }
        }
    }
}
