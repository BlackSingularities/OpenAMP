package com.blacksingularities.openamp.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.blacksingularities.openamp.MainActivity
import com.blacksingularities.openamp.R
import com.blacksingularities.openamp.data.RepeatMode
import com.blacksingularities.openamp.data.SeekStyle
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.ui.formatTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Rysowanie widżetów jako RemoteViews. Tło, okładka i pasek postępu są gotowymi bitmapami
 * (zaokrąglenia, przezroczystość, rozmycie okładki, gradienty) – dzięki temu wyglądają tak samo
 * na każdym launcherze i każdej wersji Androida.
 */
object WidgetRenderer {
    data class Size(val wDp: Int, val hDp: Int)

    class Input(
        val kind: WidgetKind,
        val id: Int,
        val cfg: WidgetConfig,
        val state: WidgetState,
        val size: Size,
        val art: Bitmap?,
        val artColor: Int?,
        val favorite: Boolean,
        val sleepOn: Boolean,
        val appAccent: Int,
        /** Fala dźwiękowa bieżącego utworu (null – jeszcze nie policzona). */
        val wave: FloatArray? = null,
        /** Styl taśmy z ekranu odtwarzania – dla paska w stylu „jak w aplikacji”. */
        val appSeek: SeekStyle = SeekStyle.WAVEFORM,
    )

    /** Styl paska po rozwinięciu „jak w aplikacji” (taśmy przewijane rysujemy jako falę). */
    fun resolveStyle(style: WidgetProgressStyle, app: SeekStyle): WidgetProgressStyle =
        if (style != WidgetProgressStyle.APP) style else when (app) {
            SeekStyle.MIRROR -> WidgetProgressStyle.MIRROR
            SeekStyle.LINE -> WidgetProgressStyle.CONTOUR
            SeekStyle.DOTS -> WidgetProgressStyle.DOTS
            SeekStyle.SEGMENTS -> WidgetProgressStyle.SEGMENTS
            else -> WidgetProgressStyle.WAVE
        }

    fun usesWave(style: WidgetProgressStyle) = style == WidgetProgressStyle.WAVE || style == WidgetProgressStyle.MIRROR ||
        style == WidgetProgressStyle.CONTOUR || style == WidgetProgressStyle.DOTS || style == WidgetProgressStyle.SEGMENTS

    private class Palette(val fg: Int, val fg2: Int, val fg3: Int, val accent: Int, val onAccent: Int, val light: Boolean)

    fun defaultSize(kind: WidgetKind): Size = when (kind) {
        WidgetKind.COVER -> Size(160, 160)
        WidgetKind.BAR -> Size(320, 72)
        WidgetKind.PLAYER -> Size(320, 200)
        WidgetKind.SHORTCUTS -> Size(320, 130)
    }

    fun layoutOf(kind: WidgetKind): Int = when (kind) {
        WidgetKind.COVER -> R.layout.widget_cover
        WidgetKind.BAR -> R.layout.widget_bar
        WidgetKind.PLAYER -> R.layout.widget_player
        WidgetKind.SHORTCUTS -> R.layout.widget_shortcuts
    }

    // ---------------------------------------------------------------- budowanie

    fun build(context: Context, i: Input): RemoteViews {
        val rv = RemoteViews(context.packageName, layoutOf(i.kind))
        val pal = palette(i)
        rv.setImageViewBitmap(R.id.w_bg, background(context, i))
        when (i.kind) {
            WidgetKind.COVER -> buildCover(context, rv, i, pal)
            WidgetKind.BAR -> buildBar(context, rv, i, pal)
            WidgetKind.PLAYER -> buildPlayer(context, rv, i, pal)
            WidgetKind.SHORTCUTS -> buildShortcuts(context, rv, i, pal)
        }
        return rv
    }

    /** Tylko pasek postępu i czasy – do częstych, lekkich aktualizacji. */
    fun buildProgress(context: Context, i: Input): RemoteViews? {
        if (i.kind == WidgetKind.SHORTCUTS) return null
        val rv = RemoteViews(context.packageName, layoutOf(i.kind))
        val pal = palette(i)
        progress(context, rv, i, pal)
        if (i.kind == WidgetKind.PLAYER && i.cfg.showTime) {
            val sides = i.cfg.timePos == WidgetTimePos.SIDES
            val b = barOf(i)
            rv.setTextViewText(if (sides) b.sidePos else b.pos, formatTime(i.state.position()))
            rv.setTextViewText(if (sides) b.sideDur else b.dur, formatTime(i.state.durationMs))
        }
        return rv
    }

    private fun buildCover(context: Context, rv: RemoteViews, i: Input, pal: Palette) {
        val c = i.cfg
        val big = i.size.wDp >= 120 && i.size.hDp >= 120
        texts(rv, i, pal, titleSp = 15f, artistSp = 12f)
        rv.setViewVisibility(R.id.w_info, vis(big))
        rv.setViewVisibility(R.id.w_artist, vis(c.showArtist && i.state.trackId != null || i.state.trackId == null))
        val wide = i.size.wDp >= 140 && c.showPrevNext
        rv.setViewVisibility(R.id.w_prev, vis(wide))
        rv.setViewVisibility(R.id.w_next, vis(wide))
        val pad = px(context, c.padding)
        rv.setViewPadding(R.id.w_content, pad, 0, pad, max(px(context, 4), pad - px(context, 4)))
        controls(context, rv, i, pal)
        buttons(rv, i, play = 48, side = 40, sideWidth = null, extra = null, row = 48)
        like(context, rv, i, pal, onArt = coverAsBackground(i))
        progress(context, rv, i, pal)
        rv.setOnClickPendingIntent(R.id.w_bg, tapIntent(context, i))
        rv.setOnClickPendingIntent(R.id.w_info, tapIntent(context, i))
    }

    private fun buildBar(context: Context, rv: RemoteViews, i: Input, pal: Palette) {
        val c = i.cfg
        texts(rv, i, pal, titleSp = 14f, artistSp = 12f)
        rv.setViewVisibility(R.id.w_artist, vis(c.showArtist || i.state.trackId == null))
        val pad = px(context, c.padding)
        rv.setViewPadding(R.id.w_content, pad, 0, max(0, pad - px(context, 2)), 0)
        val coverDp = (48 * c.coverSize / 100).coerceIn(24, (i.size.hDp - 8).coerceAtLeast(24))
        val pos = if (c.coverPos == WidgetCoverPos.TOP) WidgetCoverPos.LEFT else c.coverPos
        placeCover(context, rv, i, pal, coverDp, pos, listOf(WidgetCoverPos.LEFT to R.id.w_cover, WidgetCoverPos.RIGHT to R.id.w_cover_end))
        rv.setViewVisibility(R.id.w_prev, vis(i.size.wDp >= 230 && c.showPrevNext))
        rv.setViewVisibility(R.id.w_next, vis(c.showPrevNext))
        controls(context, rv, i, pal)
        buttons(rv, i, play = 44, side = 40, sideWidth = 40, extra = null, row = null)
        like(context, rv, i, pal, onArt = coverAsBackground(i))
        if (i.size.wDp < 270) rv.setViewVisibility(R.id.w_like, View.GONE)
        progress(context, rv, i, pal)
        rv.setOnClickPendingIntent(R.id.w_info, tapIntent(context, i))
        rv.setOnClickPendingIntent(R.id.w_bg, if (coverAsBackground(i)) tapIntent(context, i) else openApp(context, i.id, null, 20))
    }

    private fun buildPlayer(context: Context, rv: RemoteViews, i: Input, pal: Palette) {
        val c = i.cfg
        val s = i.state
        val h = i.size.hDp
        val inColumn = c.controlsPos == WidgetControlsPos.IN_COLUMN
        texts(rv, i, pal, titleSp = 17f, artistSp = 13f)
        rv.setViewVisibility(R.id.w_artist, vis(c.showArtist || s.trackId == null))
        val joined = c.artistAlbumLine && s.trackId != null
        val album = s.album?.takeIf { c.showAlbum && (joined || h >= 160) && it.isNotBlank() }
        // „Wykonawca – Album” w jednym wierszu zamiast osobnego wiersza albumu.
        if (joined && album != null) {
            rv.setTextViewText(R.id.w_artist, if (c.showArtist) "${s.artist?.takeIf { it.isNotBlank() } ?: tr("Nieznany wykonawca")} – $album" else album)
            rv.setViewVisibility(R.id.w_artist, View.VISIBLE)
        }
        rv.setViewVisibility(R.id.w_album, vis(album != null && !joined))
        if (album != null && !joined) {
            rv.setTextViewText(R.id.w_album, album)
            textStyle(rv, R.id.w_album, 12f * c.textScale / 100f, pal.fg3)
        }
        val meta = buildList {
            if (c.showQueuePos && s.count > 0 && s.index >= 0) add("${s.index + 1} / ${s.count}")
            if (i.sleepOn) add(tr("wyłącznik"))
        }.joinToString("  ·  ")
        rv.setViewVisibility(R.id.w_meta, vis(meta.isNotEmpty()))
        rv.setTextViewText(R.id.w_meta, meta)
        textStyle(rv, R.id.w_meta, 11f * c.textScale / 100f, pal.fg3)
        val pad = px(context, c.padding)
        rv.setViewPadding(R.id.w_content, pad, pad, pad, pad)

        // Wiersz postępu i czasy – gdy jest miejsce.
        val showRow = (c.showProgress || c.showTime) && h >= 110
        val sides = c.timePos == WidgetTimePos.SIDES
        val bar = barOf(i)
        rv.setViewVisibility(if (bar === barTop) R.id.w_progress_row_b else R.id.w_progress_row, View.GONE)
        rv.setViewVisibility(bar.row, vis(showRow))
        rv.setViewVisibility(bar.progress, vis(c.showProgress))
        rv.setViewVisibility(bar.times, vis(c.showTime && !sides))
        rv.setViewVisibility(bar.sidePos, vis(c.showTime && sides))
        rv.setViewVisibility(bar.sideDur, vis(c.showTime && sides))
        // Launcher przycina widżet do zaokrąglonych rogów – przy małym marginesie taśma i czasy
        // dostają własny odstęp od krawędzi, żeby cyfry nie znikały.
        val edgeX = max(0, barEdgeX - c.padding)
        val edgeB = if (bar === barBottom || inColumn) max(0, barEdgeBottom - c.padding) else 0
        rv.setViewPadding(bar.row, px(context, edgeX), 0, px(context, edgeX), px(context, edgeB))
        if (showRow) {
            progress(context, rv, i, pal)
            val posId = if (sides) bar.sidePos else bar.pos
            val durId = if (sides) bar.sideDur else bar.dur
            rv.setTextViewText(posId, formatTime(s.position()))
            rv.setTextViewText(durId, formatTime(s.durationMs))
            textStyle(rv, posId, 11f * c.textScale / 100f, pal.fg2)
            textStyle(rv, durId, 11f * c.textScale / 100f, pal.fg2)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val dip = TypedValue.COMPLEX_UNIT_DIP
            // Odstęp taśma–przyciski: gdy taśma jest nad przyciskami – margines przycisków, gdy pod – margines taśmy.
            val above = bar === barTop && !inColumn
            rv.setViewLayoutMargin(R.id.w_controls, RemoteViews.MARGIN_TOP, (if (above) c.progressGap else 0) + c.controlsTop.toFloat(), dip)
            rv.setViewLayoutMargin(R.id.w_controls, RemoteViews.MARGIN_BOTTOM, c.controlsBottom.toFloat(), dip)
            rv.setViewLayoutMargin(bar.row, RemoteViews.MARGIN_TOP, if (above || inColumn) (if (c.compact) 0f else c.barTop.toFloat()) else c.progressGap.toFloat(), dip)
            rv.setViewLayoutMargin(R.id.w_controls_col, RemoteViews.MARGIN_TOP, (if (c.compact) 0f else 6f) + c.controlsTop, dip)
            rv.setViewLayoutMargin(R.id.w_controls_col, RemoteViews.MARGIN_BOTTOM, c.controlsBottom.toFloat(), dip)
            rv.setViewLayoutMargin(R.id.w_cover_box, RemoteViews.MARGIN_END, if (c.compact) 8f else 12f, dip)
            rv.setViewLayoutMargin(R.id.w_cover_end_box, RemoteViews.MARGIN_START, if (c.compact) 6f else 8f, dip)
            rv.setViewLayoutMargin(R.id.w_cover_top, RemoteViews.MARGIN_BOTTOM, if (c.compact) 2f else 8f, dip)
        }

        // Okładka we własnym kontenerze: przy 100% ma wysokość całego wiersza – w kolumnowym układzie
        // przycisków to cały widżet poza taśmą postępu (Android 12+, na starszych stała).
        val barDp = if (!showRow) 0 else (if (c.compact) 0 else c.barTop) + max(if (c.showProgress) c.progressHeight else 0, if (c.showTime && sides) 16 else 0) +
            (if (c.showTime && !sides) 15 else 0) + edgeB
        val buttonsDp = if (inColumn) 0 else 48 * c.buttonScale / 100 + c.progressGap + c.controlsTop + c.controlsBottom
        val free = (h - c.padding * 2 - buttonsDp - barDp).coerceAtLeast(32)
        val coverDp = if (c.coverPos == WidgetCoverPos.TOP) {
            // Nad tytułem: zostaje miejsce na tytuł i wykonawcę.
            ((free - 52) * c.coverSize / 100).coerceIn(32, 400).coerceAtMost((i.size.wDp * 0.6f).toInt())
        } else {
            (free * c.coverSize / 100).coerceIn(32, free).coerceAtMost((i.size.wDp * 0.55f).toInt())
        }
        placeCover(
            context, rv, i, pal, coverDp, c.coverPos,
            listOf(WidgetCoverPos.LEFT to R.id.w_cover, WidgetCoverPos.RIGHT to R.id.w_cover_end, WidgetCoverPos.TOP to R.id.w_cover_top),
        )
        val coverShown = c.showCover && !coverAsBackground(i)
        rv.setViewVisibility(R.id.w_cover_box, vis(coverShown && c.coverPos == WidgetCoverPos.LEFT))
        rv.setViewVisibility(R.id.w_cover_end_box, vis(coverShown && c.coverPos == WidgetCoverPos.RIGHT))

        // Przyciski w jednym z dwóch kontenerów: wiersz na całą szerokość albo kolumna obok okładki.
        val ctl = if (inColumn) ctlColumn else ctlRow
        rv.setViewVisibility(R.id.w_controls, vis(!inColumn))
        rv.setViewVisibility(R.id.w_controls_col, vis(inColumn))
        val sideCover = coverShown && (c.coverPos == WidgetCoverPos.LEFT || c.coverPos == WidgetCoverPos.RIGHT)
        val columnDp = i.size.wDp - c.padding * 2 - (if (sideCover) coverDp + 12 else 0)
        val roomy = if (inColumn) columnDp >= 220 * c.buttonScale.coerceIn(60, 150) / 100 else i.size.wDp >= 230
        val showShuffle = c.showShuffle && roomy
        val showRepeat = c.showRepeat && roomy
        rv.setViewVisibility(ctl.shuffle, vis(showShuffle))
        rv.setViewVisibility(ctl.repeat, vis(showRepeat))
        rv.setViewVisibility(ctl.prev, vis(c.showPrevNext))
        rv.setViewVisibility(ctl.next, vis(c.showPrevNext))
        if (showShuffle) {
            icon(rv, ctl.shuffle, R.drawable.ic_w_shuffle, if (s.shuffle) pal.accent else pal.fg3)
            rv.setOnClickPendingIntent(ctl.shuffle, action(context, i, WidgetActions.SHUFFLE, 5))
            rv.setContentDescription(ctl.shuffle, tr("Losowo"))
        }
        if (showRepeat) {
            icon(
                rv, ctl.repeat, if (s.repeat == RepeatMode.ONE) R.drawable.ic_w_repeat_one else R.drawable.ic_w_repeat,
                if (s.repeat != RepeatMode.OFF) pal.accent else pal.fg3,
            )
            rv.setOnClickPendingIntent(ctl.repeat, action(context, i, WidgetActions.REPEAT, 6))
            rv.setContentDescription(ctl.repeat, tr("Powtarzanie"))
        }
        controls(context, rv, i, pal, ctl)
        if (inColumn) buttons(rv, i, play = 48, side = 44, sideWidth = 44, extra = 40, row = 48, ctl = ctl, extraWidth = 40)
        else buttons(rv, i, play = 48, side = 44, sideWidth = null, extra = 40, row = 48)
        like(context, rv, i, pal, onArt = coverAsBackground(i))
        rv.setOnClickPendingIntent(R.id.w_info, tapIntent(context, i))
        rv.setOnClickPendingIntent(R.id.w_bg, if (coverAsBackground(i)) tapIntent(context, i) else openApp(context, i.id, null, 20))
    }

    private fun buildShortcuts(context: Context, rv: RemoteViews, i: Input, pal: Palette) {
        val c = i.cfg
        val pad = px(context, c.padding)
        rv.setViewPadding(R.id.w_content, pad, pad, pad, pad)
        val header = i.size.hDp >= 130 && c.showCover
        rv.setViewVisibility(R.id.w_header, vis(header))
        if (header) {
            texts(rv, i, pal, titleSp = 14f, artistSp = 12f)
            val coverDp = (40 * c.coverSize / 100).coerceIn(24, 48)
            cover(context, rv, i, pal, coverDp)
            if (Build.VERSION.SDK_INT >= 31) {
                rv.setViewLayoutWidth(R.id.w_cover, coverDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                rv.setViewLayoutHeight(R.id.w_cover, coverDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            }
            controls(context, rv, i, pal)
            rv.setOnClickPendingIntent(R.id.w_cover, tapIntent(context, i))
            rv.setOnClickPendingIntent(R.id.w_info, tapIntent(context, i))
        }
        val slots = listOf(
            Slot(R.id.w_sc0, R.id.w_sc0_bg, R.id.w_sc0_icon, R.id.w_sc0_label),
            Slot(R.id.w_sc1, R.id.w_sc1_bg, R.id.w_sc1_icon, R.id.w_sc1_label),
            Slot(R.id.w_sc2, R.id.w_sc2_bg, R.id.w_sc2_icon, R.id.w_sc2_label),
            Slot(R.id.w_sc3, R.id.w_sc3_bg, R.id.w_sc3_icon, R.id.w_sc3_label),
            Slot(R.id.w_sc4, R.id.w_sc4_bg, R.id.w_sc4_icon, R.id.w_sc4_label),
            Slot(R.id.w_sc5, R.id.w_sc5_bg, R.id.w_sc5_icon, R.id.w_sc5_label),
        )
        val fit = max(1, i.size.wDp / 58)
        val list = c.shortcuts.take(min(fit, WidgetConfig.MAX_SHORTCUTS)).ifEmpty { listOf(WidgetShortcut.RESUME) }
        val labels = c.shortcutLabels && i.size.hDp >= 76
        slots.forEachIndexed { n, slot ->
            val sc = list.getOrNull(n)
            rv.setViewVisibility(slot.root, vis(sc != null))
            if (sc == null) return@forEachIndexed
            val active = sc == WidgetShortcut.SLEEP && i.sleepOn ||
                sc == WidgetShortcut.RESUME && i.state.playing
            val iconRes = if (sc == WidgetShortcut.RESUME && i.state.playing) R.drawable.ic_w_pause else shortcutIcon(sc)
            rv.setImageViewResource(slot.icon, iconRes)
            rv.setInt(slot.icon, "setColorFilter", if (active) pal.onAccent else pal.fg)
            rv.setInt(slot.bg, "setColorFilter", if (active) pal.accent else ColorUtils.setAlphaComponent(pal.fg, 0x1F))
            rv.setViewVisibility(slot.label, vis(labels))
            rv.setTextViewText(slot.label, shortcutLabel(sc))
            textStyle(rv, slot.label, 11f * c.textScale / 100f, pal.fg2)
            rv.setContentDescription(slot.root, shortcutLabel(sc))
            val pi = when (sc) {
                WidgetShortcut.SEARCH -> openApp(context, i.id, "search", 40 + n)
                WidgetShortcut.EQUALIZER -> openApp(context, i.id, "equalizer", 40 + n)
                WidgetShortcut.RESUME -> action(context, i, WidgetActions.PLAY_PAUSE, 40 + n)
                else -> action(context, i, WidgetActions.SHORTCUT, 40 + n, sc.name)
            }
            rv.setOnClickPendingIntent(slot.root, pi)
        }
        rv.setOnClickPendingIntent(R.id.w_bg, openApp(context, i.id, null, 20))
    }

    private class Slot(val root: Int, val bg: Int, val icon: Int, val label: Int)

    fun shortcutLabel(s: WidgetShortcut): String = when (s) {
        WidgetShortcut.RESUME -> tr("Graj")
        WidgetShortcut.SHUFFLE_ALL -> tr("Losuj")
        WidgetShortcut.DAILY_MIX -> tr("Mix dnia")
        WidgetShortcut.FAVORITES -> tr("Ulubione")
        WidgetShortcut.RECENT_ADDED -> tr("Nowe")
        WidgetShortcut.MOST_PLAYED -> tr("Najczęstsze")
        WidgetShortcut.DISCOVER -> tr("Odkrywaj")
        WidgetShortcut.FORGOTTEN -> tr("Perełki")
        WidgetShortcut.NEVER_PLAYED -> tr("Niegrane")
        WidgetShortcut.SLEEP -> tr("Wyłącznik")
        WidgetShortcut.SEARCH -> tr("Szukaj")
        WidgetShortcut.EQUALIZER -> tr("Korektor")
    }

    fun shortcutIcon(s: WidgetShortcut): Int = when (s) {
        WidgetShortcut.RESUME -> R.drawable.ic_w_play
        WidgetShortcut.SHUFFLE_ALL -> R.drawable.ic_w_shuffle
        WidgetShortcut.DAILY_MIX -> R.drawable.ic_w_mix
        WidgetShortcut.FAVORITES -> R.drawable.ic_w_heart
        WidgetShortcut.RECENT_ADDED -> R.drawable.ic_w_added
        WidgetShortcut.MOST_PLAYED -> R.drawable.ic_w_trending
        WidgetShortcut.DISCOVER -> R.drawable.ic_w_explore
        WidgetShortcut.FORGOTTEN -> R.drawable.ic_w_history
        WidgetShortcut.NEVER_PLAYED -> R.drawable.ic_w_star
        WidgetShortcut.SLEEP -> R.drawable.ic_w_sleep
        WidgetShortcut.SEARCH -> R.drawable.ic_w_search
        WidgetShortcut.EQUALIZER -> R.drawable.ic_w_tune
    }

    // ---------------------------------------------------------------- wspólne elementy

    private fun vis(b: Boolean) = if (b) View.VISIBLE else View.GONE

    /** Widoki jednego wiersza postępu (Odtwarzacz ma dwa: nad przyciskami i pod nimi). */
    private class Bar(val row: Int, val progress: Int, val sidePos: Int, val sideDur: Int, val times: Int, val pos: Int, val dur: Int)

    /** Najmniejszy odstęp taśmy postępu od bocznych i dolnej krawędzi widżetu (dp). */
    private const val barEdgeX = 12
    private const val barEdgeBottom = 6

    private val barTop = Bar(R.id.w_progress_row, R.id.w_progress, R.id.w_time_side_pos, R.id.w_time_side_dur, R.id.w_times, R.id.w_time_pos, R.id.w_time_dur)
    private val barBottom = Bar(R.id.w_progress_row_b, R.id.w_progress_b, R.id.w_time_side_pos_b, R.id.w_time_side_dur_b, R.id.w_times_b, R.id.w_time_pos_b, R.id.w_time_dur_b)

    /** Wiersz postępu użyty przez widżet; dla innych rodzajów – zwykły w_progress. */
    private fun barOf(i: Input) = if (i.kind == WidgetKind.PLAYER && i.cfg.controlsPos == WidgetControlsPos.ABOVE_BAR) barBottom else barTop

    private fun textStyle(rv: RemoteViews, id: Int, sp: Float, color: Int) {
        rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp)
        rv.setTextColor(id, color)
    }

    private fun texts(rv: RemoteViews, i: Input, pal: Palette, titleSp: Float, artistSp: Float) {
        val s = i.state
        val scale = i.cfg.textScale / 100f
        val title = s.title?.takeIf { it.isNotBlank() } ?: if (s.trackId == null) tr("Nic nie jest odtwarzane") else tr("Nieznany utwór")
        val artist = if (s.trackId == null) tr("Dotknij ▶, aby zagrać") else s.artist?.takeIf { it.isNotBlank() } ?: tr("Nieznany wykonawca")
        rv.setTextViewText(
            R.id.w_title,
            if (i.cfg.boldTitle) SpannableString(title).apply { setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) } else title,
        )
        rv.setInt(R.id.w_title, "setMaxLines", i.cfg.titleLines.coerceIn(1, 3))
        rv.setTextViewText(R.id.w_artist, artist)
        textStyle(rv, R.id.w_title, titleSp * scale, pal.fg)
        textStyle(rv, R.id.w_artist, artistSp * scale, pal.fg2)
        // Wyrównanie wszystkich wierszy tekstu (album i znaczniki są tylko w Odtwarzaczu – tam ustawiane osobno).
        val g = if (i.cfg.centerText) android.view.Gravity.CENTER_HORIZONTAL else android.view.Gravity.START
        rv.setInt(R.id.w_title, "setGravity", g)
        rv.setInt(R.id.w_artist, "setGravity", g)
        if (i.kind == WidgetKind.PLAYER) {
            rv.setInt(R.id.w_album, "setGravity", g)
            rv.setInt(R.id.w_meta, "setGravity", g)
        }
    }

    private fun icon(rv: RemoteViews, id: Int, res: Int, color: Int) {
        rv.setImageViewResource(id, res)
        rv.setInt(id, "setColorFilter", color)
    }

    /** Identyfikatory jednego zestawu przycisków (Odtwarzacz ma dwa: wiersz i kolumnę obok okładki). */
    private class Ctl(val row: Int, val shuffle: Int, val prev: Int, val playFrame: Int, val playBg: Int, val play: Int, val next: Int, val repeat: Int)

    private val ctlRow = Ctl(R.id.w_controls, R.id.w_shuffle, R.id.w_prev, R.id.w_play_frame, R.id.w_play_bg, R.id.w_play, R.id.w_next, R.id.w_repeat)
    private val ctlColumn = Ctl(R.id.w_controls_col, R.id.w_shuffle_c, R.id.w_prev_c, R.id.w_play_frame_c, R.id.w_play_bg_c, R.id.w_play_c, R.id.w_next_c, R.id.w_repeat_c)

    private fun controls(context: Context, rv: RemoteViews, i: Input, pal: Palette, ctl: Ctl = ctlRow) {
        val playing = i.state.playing
        rv.setImageViewResource(ctl.play, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        val style = i.cfg.buttonStyle
        when (style) {
            WidgetButtonStyle.CIRCLE, WidgetButtonStyle.SQUARE, WidgetButtonStyle.BUBBLES -> {
                rv.setViewVisibility(ctl.playBg, View.VISIBLE)
                rv.setImageViewResource(ctl.playBg, if (style == WidgetButtonStyle.SQUARE) R.drawable.widget_rsquare else R.drawable.widget_circle)
                rv.setInt(ctl.playBg, "setColorFilter", pal.accent)
                rv.setInt(ctl.play, "setColorFilter", pal.onAccent)
            }
            WidgetButtonStyle.RING -> {
                rv.setViewVisibility(ctl.playBg, View.VISIBLE)
                rv.setImageViewResource(ctl.playBg, R.drawable.widget_ring)
                rv.setInt(ctl.playBg, "setColorFilter", pal.accent)
                rv.setInt(ctl.play, "setColorFilter", pal.accent)
            }
            WidgetButtonStyle.PLAIN -> {
                rv.setViewVisibility(ctl.playBg, View.GONE)
                rv.setInt(ctl.play, "setColorFilter", pal.fg)
            }
        }
        // „Wszystkie w kołach”: delikatne koła pod poprzednim i następnym (i losowaniem/powtarzaniem w Odtwarzaczu).
        val soft = if (style == WidgetButtonStyle.BUBBLES) (if (pal.light) R.drawable.widget_soft_circle_dark else R.drawable.widget_soft_circle) else 0
        val others = if (i.kind == WidgetKind.PLAYER) intArrayOf(ctl.prev, ctl.next, ctl.shuffle, ctl.repeat) else if (i.kind == WidgetKind.SHORTCUTS) intArrayOf() else intArrayOf(ctl.prev, ctl.next)
        // Pozostałe style zostawiają tło z układu (efekt dotknięcia).
        if (soft != 0) for (id in others) rv.setInt(id, "setBackgroundResource", soft)
        rv.setContentDescription(ctl.play, if (playing) tr("Pauza") else tr("Odtwórz"))
        rv.setOnClickPendingIntent(ctl.play, action(context, i, WidgetActions.PLAY_PAUSE, 1))
        icon(rv, ctl.next, R.drawable.ic_w_next, pal.fg)
        rv.setContentDescription(ctl.next, tr("Następny"))
        rv.setOnClickPendingIntent(ctl.next, action(context, i, WidgetActions.NEXT, 2))
        if (i.kind != WidgetKind.SHORTCUTS) {
            icon(rv, ctl.prev, R.drawable.ic_w_prev, pal.fg)
            rv.setContentDescription(ctl.prev, tr("Poprzedni"))
            rv.setOnClickPendingIntent(ctl.prev, action(context, i, WidgetActions.PREV, 3))
        }
    }

    private fun like(context: Context, rv: RemoteViews, i: Input, pal: Palette, onArt: Boolean = false) {
        val show = i.cfg.showLike && i.state.trackId != null
        rv.setViewVisibility(R.id.w_like, vis(show))
        if (!show) return
        val color = if (i.favorite) (if (onArt) Color.WHITE else pal.accent) else if (onArt) Color.WHITE else pal.fg3
        icon(rv, R.id.w_like, if (i.favorite) R.drawable.ic_w_heart else R.drawable.ic_w_heart_outline, color)
        rv.setContentDescription(R.id.w_like, if (i.favorite) tr("Usuń z ulubionych") else tr("Dodaj do ulubionych"))
        rv.setOnClickPendingIntent(R.id.w_like, action(context, i, WidgetActions.LIKE, 4))
    }

    private fun px(context: Context, dp: Int) = (dp * context.resources.displayMetrics.density).roundToInt()

    /** Okładka jako tło całego widżetu (widżet „Okładka” albo położenie „tło”). */
    private fun coverAsBackground(i: Input): Boolean = i.cfg.showCover && i.art != null && when (i.kind) {
        WidgetKind.COVER -> true
        WidgetKind.PLAYER, WidgetKind.BAR -> i.cfg.coverPos == WidgetCoverPos.BACKGROUND
        WidgetKind.SHORTCUTS -> false
    }

    /**
     * Okładka w wybranym miejscu: [slots] to możliwe położenia z identyfikatorami widoków
     * (pozostałe są chowane). Rozmiar w dp ustawiany na Androidzie 12+.
     */
    private fun placeCover(
        context: Context, rv: RemoteViews, i: Input, pal: Palette, dp: Int,
        pos: WidgetCoverPos, slots: List<Pair<WidgetCoverPos, Int>>,
    ) {
        val target = slots.firstOrNull { it.first == pos }?.second
        for ((_, id) in slots) if (id != target) rv.setViewVisibility(id, View.GONE)
        if (target == null) return
        cover(context, rv, i, pal, dp, target)
        if (Build.VERSION.SDK_INT >= 31) {
            rv.setViewLayoutWidth(target, dp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(target, dp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }
        rv.setOnClickPendingIntent(target, tapIntent(context, i))
    }

    /**
     * Wielkość przycisków (Android 12+): [play] – koło odtwarzania, [side] – poprzedni/następny,
     * [extra] – losowanie/powtarzanie, [row] – wysokość wiersza; wartości w dp przy 100%.
     */
    private fun buttons(
        rv: RemoteViews, i: Input, play: Int, side: Int, sideWidth: Int?, extra: Int?, row: Int?,
        ctl: Ctl = ctlRow, extraWidth: Int? = null,
    ) {
        if (Build.VERSION.SDK_INT < 31) return
        val k = i.cfg.buttonScale.coerceIn(60, 150) / 100f
        val unit = TypedValue.COMPLEX_UNIT_DIP
        rv.setViewLayoutWidth(ctl.playFrame, play * k, unit)
        rv.setViewLayoutHeight(ctl.playFrame, play * k, unit)
        for (id in intArrayOf(ctl.prev, ctl.next)) {
            rv.setViewLayoutHeight(id, side * k, unit)
            if (sideWidth != null) rv.setViewLayoutWidth(id, sideWidth * k, unit)
        }
        if (extra != null) for (id in intArrayOf(ctl.shuffle, ctl.repeat)) {
            rv.setViewLayoutHeight(id, extra * k, unit)
            if (extraWidth != null) rv.setViewLayoutWidth(id, extraWidth * k, unit)
        }
        if (row != null) rv.setViewLayoutHeight(ctl.row, row * k, unit)
    }

    private fun cover(context: Context, rv: RemoteViews, i: Input, pal: Palette, dp: Int, id: Int = R.id.w_cover) {
        // Okładka jako tło – osobny obrazek byłby zdublowany.
        val show = i.cfg.showCover && !coverAsBackground(i)
        rv.setViewVisibility(id, vis(show))
        if (!show) return
        val px = (dp * scaleOf(context)).roundToInt().coerceIn(48, 420)
        rv.setImageViewBitmap(id, coverBitmap(context, i, pal, px))
    }

    private fun progress(context: Context, rv: RemoteViews, i: Input, pal: Palette) {
        val show = i.cfg.showProgress && (i.kind != WidgetKind.PLAYER || i.size.hDp >= 110)
        rv.setViewVisibility(barOf(i).progress, vis(show))
        if (!show) return
        val c = i.cfg
        val inset = when (i.kind) {
            WidgetKind.PLAYER -> max(c.padding, barEdgeX) * 2 + (if (c.showTime && c.timePos == WidgetTimePos.SIDES) 96 else 0)
            WidgetKind.BAR -> 36
            else -> c.padding * 2
        }
        val hDp = c.progressHeight.coerceIn(2, 48)
        val id = barOf(i).progress
        if (Build.VERSION.SDK_INT >= 31) rv.setViewLayoutHeight(id, hDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        val s = i.state
        val f = if (s.durationMs > 0) (s.position().toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
        rv.setImageViewBitmap(id, progressBitmap(context, resolveStyle(c.progressStyle, i.appSeek), f, i.wave, pal, (i.size.wDp - inset).coerceAtLeast(40), hDp))
    }

    // ---------------------------------------------------------------- akcje

    private fun action(context: Context, i: Input, action: String, n: Int, extra: String? = null): PendingIntent {
        val intent = Intent(context, WidgetHub.providerClass(i.kind))
            .setAction(action)
            .setData(Uri.parse("openamp://widget/${i.id}/$n/${extra ?: ""}"))
        if (extra != null) intent.putExtra(WidgetActions.EXTRA_SHORTCUT, extra)
        return PendingIntent.getBroadcast(context, i.id * 64 + n, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Otwarcie aplikacji: [route] == null – ekran odtwarzania. */
    fun openApp(context: Context, id: Int, route: String?, n: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setData(Uri.parse("openamp://widget/$id/open/${route ?: "player"}"))
        if (route == null) intent.putExtra(MainActivity.EXTRA_OPEN_PLAYER, true) else intent.putExtra(MainActivity.EXTRA_ROUTE, route)
        return PendingIntent.getActivity(context, id * 64 + n, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun tapIntent(context: Context, i: Input): PendingIntent = when (i.cfg.tap) {
        WidgetTap.OPEN_PLAYER -> openApp(context, i.id, null, 10)
        WidgetTap.PLAY_PAUSE -> action(context, i, WidgetActions.PLAY_PAUSE, 11)
        WidgetTap.NEXT -> action(context, i, WidgetActions.NEXT, 12)
        WidgetTap.OPEN_QUEUE -> openApp(context, i.id, "queue", 13)
        WidgetTap.OPEN_LIBRARY -> openApp(context, i.id, "library", 14)
    }

    // ---------------------------------------------------------------- kolory

    private fun palette(i: Input): Palette {
        val c = i.cfg
        val coverOnArt = coverAsBackground(i)
        val light = !coverOnArt && c.opacity >= 45 && when (c.background) {
            WidgetBackground.LIGHT -> true
            WidgetBackground.CUSTOM -> ColorUtils.calculateLuminance(c.customColor) > 0.55
            else -> false
        }
        val fg = if (light) 0xFF121212.toInt() else Color.WHITE
        var accent = when (c.accent) {
            WidgetAccent.ART -> i.artColor ?: i.appAccent
            WidgetAccent.APP -> i.appAccent
            WidgetAccent.WHITE -> fg
            WidgetAccent.CUSTOM -> c.accentColor
        }
        // Na jasnym tle zbyt jasny akcent byłby niewidoczny – przyciemniamy.
        if (light && ColorUtils.calculateLuminance(accent) > 0.6) accent = ColorUtils.blendARGB(accent, Color.BLACK, 0.45f)
        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.5) 0xFF111111.toInt() else Color.WHITE
        return Palette(
            fg = fg,
            fg2 = ColorUtils.setAlphaComponent(fg, 0xB8),
            fg3 = ColorUtils.setAlphaComponent(fg, 0x85),
            accent = accent,
            onAccent = onAccent,
            light = light,
        )
    }

    // ---------------------------------------------------------------- bitmapy

    /** Największy bok tła (px); hub obniża go, gdy launcher odrzuci zbyt dużą aktualizację. */
    @Volatile var bgLimit = 900

    private fun scaleOf(context: Context) = min(context.resources.displayMetrics.density, 2.25f)

    private fun background(context: Context, i: Input): Bitmap {
        val c = i.cfg
        val sc = scaleOf(context)
        var w = (i.size.wDp * sc).roundToInt().coerceAtLeast(8)
        var h = (i.size.hDp * sc).roundToInt().coerceAtLeast(8)
        val limit = bgLimit
        if (max(w, h) > limit) {
            val f = limit.toFloat() / max(w, h)
            w = (w * f).roundToInt().coerceAtLeast(8); h = (h * f).roundToInt().coerceAtLeast(8)
        }
        val radius = c.corner * sc * (w.toFloat() / (i.size.wDp * sc))
        val content = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        val art = i.art
        val coverOnArt = coverAsBackground(i)
        when {
            coverOnArt && i.kind != WidgetKind.COVER -> {
                // Okładka pod całym odtwarzaczem: równe przyciemnienie, bo tekst i przyciski są wszędzie.
                drawCrop(canvas, art!!, w, h, 1f, c.coverFocus / 100f)
                canvas.drawColor(0x8C000000.toInt())
            }
            coverOnArt -> {
                drawCrop(canvas, art!!, w, h, c.coverSize.coerceIn(100, 200) / 100f, c.coverFocus / 100f)
                // Przyciemnienie u dołu pod tekst i przyciski, lekko u góry pod serduszko.
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.shader = LinearGradient(0f, h * 0.30f, 0f, h.toFloat(), 0x00000000, 0xD9000000.toInt(), Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
                p.shader = LinearGradient(0f, 0f, 0f, h * 0.28f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h * 0.28f, p)
            }
            c.background == WidgetBackground.ART_BLUR && art != null -> {
                drawBlur(canvas, art, w, h)
                canvas.drawColor(0x80000000.toInt())
            }
            c.background == WidgetBackground.ART_COLOR || c.background == WidgetBackground.ART_BLUR -> {
                val base = i.artColor ?: if (c.background == WidgetBackground.ART_COLOR) i.appAccent else 0xFF1C1C1C.toInt()
                val a = ColorUtils.blendARGB(base, Color.BLACK, 0.50f)
                val b = ColorUtils.blendARGB(base, Color.BLACK, 0.78f)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a, b, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
            c.background == WidgetBackground.DARK -> canvas.drawColor(0xFF1A1A1A.toInt())
            c.background == WidgetBackground.AMOLED -> canvas.drawColor(Color.BLACK)
            c.background == WidgetBackground.LIGHT -> canvas.drawColor(0xFFF3F3F3.toInt())
            c.background == WidgetBackground.CUSTOM -> canvas.drawColor(c.customColor or 0xFF000000.toInt())
            else -> Unit // przezroczyste
        }
        val alpha = if (c.background == WidgetBackground.TRANSPARENT && !coverOnArt) 0 else (c.opacity.coerceIn(0, 100) * 255 / 100)
        return rounded(content, radius, alpha, circle = false)
    }

    private fun coverBitmap(context: Context, i: Input, pal: Palette, px: Int): Bitmap {
        val content = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        val art = i.art
        if (art != null) {
            drawCrop(canvas, art, px, px, 1f, i.cfg.coverFocus / 100f)
        } else {
            // Zastępcza okładka: delikatny blok w kolorze akcentu z nutą.
            canvas.drawColor(ColorUtils.blendARGB(pal.accent, if (pal.light) Color.WHITE else Color.BLACK, 0.72f))
            ContextCompat.getDrawable(context, R.drawable.ic_w_note)?.mutate()?.let { d ->
                val s = (px * 0.42f).roundToInt()
                val o = (px - s) / 2
                d.setBounds(o, o, o + s, o + s)
                d.setTint(ColorUtils.setAlphaComponent(pal.fg, 0xB0))
                d.draw(canvas)
            }
        }
        val r = when (i.cfg.coverShape) {
            WidgetCoverShape.ROUNDED -> px * i.cfg.coverRadius.coerceIn(0, 50) / 100f
            WidgetCoverShape.SQUARE -> px * 0.04f
            WidgetCoverShape.CIRCLE -> px / 2f
        }
        return rounded(content, r, 255, circle = i.cfg.coverShape == WidgetCoverShape.CIRCLE)
    }

    /** Wartość fali w odcinku [i] z [n] (maksimum) – z zastępczym kształtem, gdy fali jeszcze nie ma. */
    private fun sample(w: FloatArray?, i: Int, n: Int): Float {
        if (w == null || w.isEmpty()) return 0.22f + 0.1f * abs(kotlin.math.sin(i * 0.7f))
        val a = i * w.size / n
        val b = max(a + 1, (i + 1) * w.size / n).coerceAtMost(w.size)
        var m = 0f
        for (k in a until b) m = max(m, w[k])
        return m
    }

    /** Wygładzony przebieg głośności (jak lustrzana fala i kontur w aplikacji). */
    private fun envelope(w: FloatArray?, n: Int): FloatArray {
        val e = FloatArray(n) { sample(w, it, n) }
        return FloatArray(n) { k -> (e[max(0, k - 1)] + e[k] * 2 + e[min(n - 1, k + 1)]) / 4f }
    }

    /** Pasek postępu [wDp]×[hDp] w stylu z ustawień – klasyczne paski albo fala utworu jak na ekranie odtwarzania. */
    /** Podgląd stylu paska do ustawień: ciemne tło, zastępcza fala, postęp [f]. */
    fun progressPreview(context: Context, style: WidgetProgressStyle, appSeek: SeekStyle, accent: Int, wDp: Int, hDp: Int, f: Float = 0.42f): Bitmap {
        val pal = Palette(Color.WHITE, 0xB8FFFFFF.toInt(), 0x85FFFFFF.toInt(), accent, Color.BLACK, light = false)
        return progressBitmap(context, resolveStyle(style, appSeek), f, null, pal, wDp, hDp)
    }

    private fun progressBitmap(context: Context, style: WidgetProgressStyle, f: Float, wave: FloatArray?, pal: Palette, wDp: Int, hDp: Int): Bitmap {
        val sc = scaleOf(context)
        val w = (wDp * sc).roundToInt().coerceIn(20, 1400)
        val h = (hDp * sc).roundToInt().coerceAtLeast(3)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(pal.fg, 0x40) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accent }
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (pal.light) pal.accent else Color.WHITE }
        val cy = h / 2f
        val x = w * f
        when (style) {
            WidgetProgressStyle.THIN, WidgetProgressStyle.LINE, WidgetProgressStyle.BOLD, WidgetProgressStyle.APP -> {
                val th = when (style) {
                    WidgetProgressStyle.THIN -> h.toFloat()
                    WidgetProgressStyle.BOLD -> min(h.toFloat(), max(6 * sc, h * 0.6f))
                    else -> min(h.toFloat(), 3.5f * sc)
                }
                val knob = if (style == WidgetProgressStyle.LINE) min(5.5f * sc, h / 2f) else 0f
                val left = knob
                val right = w - knob
                val r = th / 2
                canvas.drawRoundRect(RectF(left, cy - r, right, cy + r), r, r, track)
                val px = left + (right - left) * f
                if (px > left) canvas.drawRoundRect(RectF(left, cy - r, max(px, left + th), cy + r), r, r, fill)
                if (knob > 0) canvas.drawCircle(px, cy, knob, fill)
            }
            WidgetProgressStyle.SEGMENTS -> {
                val step = 6 * sc
                val bw = 3.2f * sc
                val n = max(4, (w / step).toInt())
                val gap = (w - n * bw) / (n - 1).coerceAtLeast(1)
                for (k in 0 until n) {
                    val x0 = k * (bw + gap)
                    val on = (k + 0.5f) / n <= f
                    val bh = if (wave != null) max(3 * sc, sample(wave, k, n) * h) else h * 0.65f
                    canvas.drawRoundRect(RectF(x0, cy - bh / 2, x0 + bw, cy + bh / 2), bw / 2, bw / 2, if (on) fill else track)
                }
            }
            WidgetProgressStyle.WAVE -> {
                val n = max(8, (w / (4.5f * sc)).toInt())
                val slot = w.toFloat() / n
                val bw = slot * 0.62f
                for (k in 0 until n) {
                    val bh = max(2 * sc, sample(wave, k, n) * h)
                    val x0 = k * slot + (slot - bw) / 2
                    val on = (k + 0.5f) / n <= f
                    canvas.drawRoundRect(RectF(x0, cy - bh / 2, x0 + bw, cy + bh / 2), bw / 2, bw / 2, if (on) fill else track)
                }
            }
            WidgetProgressStyle.MIRROR -> {
                val n = 90
                val e = envelope(wave, n)
                val mid = h * 0.58f
                val up = mid - sc
                val down = (h - mid) - sc
                val path = Path().apply {
                    moveTo(0f, mid)
                    for (k in 0 until n) lineTo(w * k / (n - 1f), mid - max(sc * 0.8f, e[k] * up))
                    for (k in n - 1 downTo 0) lineTo(w * k / (n - 1f), mid + max(sc * 0.8f, e[k] * down * 0.75f))
                    close()
                }
                canvas.drawPath(path, track)
                canvas.save()
                canvas.clipRect(0f, 0f, x, h.toFloat())
                canvas.drawPath(path, fill)
                canvas.restore()
                canvas.drawRect(x - sc, 0f, x + sc, h.toFloat(), head)
            }
            WidgetProgressStyle.CONTOUR -> {
                val n = 60
                val e = envelope(wave, n)
                val amp = h / 2f - 2 * sc
                val path = Path()
                var px = 0f
                var py = cy
                for (k in 0 until n) {
                    val xx = w * k / (n - 1f)
                    // Na przemian w górę i w dół – kontur przypomina oscylogram.
                    val yy = cy + (if (k % 2 == 0) -1 else 1) * e[k] * amp
                    if (k == 0) path.moveTo(xx, yy) else path.cubicTo((px + xx) / 2, py, (px + xx) / 2, yy, xx, yy)
                    px = xx; py = yy
                }
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = 1.4f * sc
                    color = ColorUtils.setAlphaComponent(pal.fg, 0x55)
                }
                canvas.drawPath(path, stroke)
                canvas.save()
                canvas.clipRect(0f, 0f, x, h.toFloat())
                stroke.strokeWidth = 2.2f * sc
                stroke.color = pal.accent
                canvas.drawPath(path, stroke)
                canvas.restore()
                canvas.drawCircle(x, cy, min(3.5f * sc, h / 2f), head)
            }
            WidgetProgressStyle.DOTS -> {
                var rows = max(3, (h / (3.4f * sc)).toInt())
                if (rows % 2 == 0) rows--
                val rh = h.toFloat() / rows
                val cols = max(8, (w / rh).toInt())
                val cw = w.toFloat() / cols
                val r = min(cw, rh) * 0.32f
                val headCol = (f * cols).toInt()
                val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(pal.fg, 0x14) }
                val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(pal.fg, 0x55) }
                for (col in 0 until cols) {
                    val lit = (sample(wave, col, cols) * rows / 2f).roundToInt()
                    for (row in 0 until rows) {
                        val d = abs(row - rows / 2)
                        val paint = when {
                            d > lit -> off
                            col == headCol -> head
                            (col + 0.5f) / cols <= f -> fill
                            else -> dim
                        }
                        canvas.drawCircle(col * cw + cw / 2, row * rh + rh / 2, r, paint)
                    }
                }
            }
        }
        return bmp
    }

    /** Wypełnienie prostokąta obrazem z przycięciem do proporcji (jak centerCrop). */
    private fun drawCrop(canvas: Canvas, src: Bitmap, w: Int, h: Int, zoom: Float = 1f, focusY: Float = 0.5f) {
        val sr = src.width.toFloat() / src.height
        val dr = w.toFloat() / h
        // Kadr o proporcjach celu, pomniejszony o [zoom]; w pionie przesunięty według [focusY].
        val z = zoom.coerceAtLeast(1f)
        val cw = (if (sr > dr) src.height * dr else src.width.toFloat()) / z
        val ch = (if (sr > dr) src.height.toFloat() else src.width / dr) / z
        val x = (src.width - cw) / 2f
        val y = (src.height - ch) * focusY.coerceIn(0f, 1f)
        val crop = Rect(x.roundToInt(), y.roundToInt(), (x + cw).roundToInt().coerceAtMost(src.width), (y + ch).roundToInt().coerceAtMost(src.height))
        canvas.drawBitmap(src, crop, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    /** Tanie i ładne rozmycie: zmniejszenie do kilkunastu pikseli i wygładzone powiększenie. */
    private fun drawBlur(canvas: Canvas, src: Bitmap, w: Int, h: Int) {
        val tw = 20
        val th = max(4, (20f * h / w).roundToInt())
        val tiny = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        drawCrop(Canvas(tiny), src, tw, th)
        val mid = Bitmap.createScaledBitmap(tiny, tw * 3, th * 3, true)
        canvas.drawBitmap(mid, Rect(0, 0, mid.width, mid.height), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        // Lekkie nasycenie barw i rozjaśnienie ciemnych okładek, żeby tło nie było szare.
        canvas.drawColor(0x14FFFFFF)
    }

    /** Zaokrąglenie (albo koło) z wygładzonymi krawędziami i zadaną przezroczystością. */
    private fun rounded(content: Bitmap, radius: Float, alpha: Int, circle: Boolean): Bitmap {
        val out = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888)
        if (alpha <= 0) return out
        val canvas = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            this.alpha = alpha
        }
        val rect = RectF(0f, 0f, content.width.toFloat(), content.height.toFloat())
        if (circle) canvas.drawOval(rect, p) else canvas.drawRoundRect(rect, radius, radius, p)
        return out
    }
}
