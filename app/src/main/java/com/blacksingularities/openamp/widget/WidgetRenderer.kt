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
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.blacksingularities.openamp.MainActivity
import com.blacksingularities.openamp.R
import com.blacksingularities.openamp.data.RepeatMode
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.ui.formatTime
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
    )

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
            rv.setTextViewText(R.id.w_time_pos, formatTime(i.state.position()))
            rv.setTextViewText(R.id.w_time_dur, formatTime(i.state.durationMs))
        }
        return rv
    }

    private fun buildCover(context: Context, rv: RemoteViews, i: Input, pal: Palette) {
        val c = i.cfg
        val big = i.size.wDp >= 120 && i.size.hDp >= 120
        texts(rv, i, pal, titleSp = 15f, artistSp = 12f)
        rv.setViewVisibility(R.id.w_info, vis(big))
        rv.setViewVisibility(R.id.w_artist, vis(c.showArtist && i.state.trackId != null || i.state.trackId == null))
        val wide = i.size.wDp >= 140
        rv.setViewVisibility(R.id.w_prev, vis(wide))
        rv.setViewVisibility(R.id.w_next, vis(wide))
        controls(context, rv, i, pal)
        like(context, rv, i, pal, onArt = c.showCover && i.art != null)
        progress(context, rv, i, pal)
        rv.setOnClickPendingIntent(R.id.w_bg, tapIntent(context, i))
        rv.setOnClickPendingIntent(R.id.w_info, tapIntent(context, i))
    }

    private fun buildBar(context: Context, rv: RemoteViews, i: Input, pal: Palette) {
        val c = i.cfg
        texts(rv, i, pal, titleSp = 14f, artistSp = 12f)
        rv.setViewVisibility(R.id.w_artist, vis(c.showArtist || i.state.trackId == null))
        cover(context, rv, i, pal, 48)
        rv.setViewVisibility(R.id.w_prev, vis(i.size.wDp >= 230))
        controls(context, rv, i, pal)
        like(context, rv, i, pal)
        if (i.size.wDp < 270) rv.setViewVisibility(R.id.w_like, View.GONE)
        progress(context, rv, i, pal)
        rv.setOnClickPendingIntent(R.id.w_cover, tapIntent(context, i))
        rv.setOnClickPendingIntent(R.id.w_info, tapIntent(context, i))
        rv.setOnClickPendingIntent(R.id.w_bg, openApp(context, i.id, null, 20))
    }

    private fun buildPlayer(context: Context, rv: RemoteViews, i: Input, pal: Palette) {
        val c = i.cfg
        val s = i.state
        val h = i.size.hDp
        texts(rv, i, pal, titleSp = 17f, artistSp = 13f)
        rv.setViewVisibility(R.id.w_artist, vis(c.showArtist || s.trackId == null))
        val album = s.album?.takeIf { c.showAlbum && h >= 160 && it.isNotBlank() }
        rv.setViewVisibility(R.id.w_album, vis(album != null))
        if (album != null) {
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

        // Wiersz postępu i czasy – gdy jest miejsce.
        val showRow = (c.showProgress || c.showTime) && h >= 140
        rv.setViewVisibility(R.id.w_progress_row, vis(showRow))
        rv.setViewVisibility(R.id.w_progress, vis(c.showProgress))
        rv.setViewVisibility(R.id.w_times, vis(c.showTime))
        if (showRow) {
            progress(context, rv, i, pal)
            rv.setTextViewText(R.id.w_time_pos, formatTime(s.position()))
            rv.setTextViewText(R.id.w_time_dur, formatTime(s.durationMs))
            textStyle(rv, R.id.w_time_pos, 11f, pal.fg2)
            textStyle(rv, R.id.w_time_dur, 11f, pal.fg2)
        }

        // Okładka dopasowana do wysokości widżetu (Android 12+), na starszych stała.
        val reserved = 28 + 56 + (if (showRow) (if (c.showTime) 34 else 22) else 0)
        val coverDp = (h - reserved).coerceIn(48, 150).coerceAtMost((i.size.wDp * 0.42f).toInt())
        cover(context, rv, i, pal, coverDp)
        if (Build.VERSION.SDK_INT >= 31 && c.showCover) {
            rv.setViewLayoutWidth(R.id.w_cover, coverDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(R.id.w_cover, coverDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        }

        val extras = c.showShuffleRepeat && i.size.wDp >= 230
        rv.setViewVisibility(R.id.w_shuffle, vis(extras))
        rv.setViewVisibility(R.id.w_repeat, vis(extras))
        if (extras) {
            icon(rv, R.id.w_shuffle, R.drawable.ic_w_shuffle, if (s.shuffle) pal.accent else pal.fg3)
            icon(
                rv, R.id.w_repeat, if (s.repeat == RepeatMode.ONE) R.drawable.ic_w_repeat_one else R.drawable.ic_w_repeat,
                if (s.repeat != RepeatMode.OFF) pal.accent else pal.fg3,
            )
            rv.setOnClickPendingIntent(R.id.w_shuffle, action(context, i, WidgetActions.SHUFFLE, 5))
            rv.setOnClickPendingIntent(R.id.w_repeat, action(context, i, WidgetActions.REPEAT, 6))
            rv.setContentDescription(R.id.w_shuffle, tr("Losowo"))
            rv.setContentDescription(R.id.w_repeat, tr("Powtarzanie"))
        }
        controls(context, rv, i, pal)
        like(context, rv, i, pal)
        rv.setOnClickPendingIntent(R.id.w_cover, tapIntent(context, i))
        rv.setOnClickPendingIntent(R.id.w_info, tapIntent(context, i))
        rv.setOnClickPendingIntent(R.id.w_bg, openApp(context, i.id, null, 20))
    }

    private fun buildShortcuts(context: Context, rv: RemoteViews, i: Input, pal: Palette) {
        val c = i.cfg
        val header = i.size.hDp >= 130 && c.showCover
        rv.setViewVisibility(R.id.w_header, vis(header))
        if (header) {
            texts(rv, i, pal, titleSp = 14f, artistSp = 12f)
            cover(context, rv, i, pal, 40)
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

    private fun textStyle(rv: RemoteViews, id: Int, sp: Float, color: Int) {
        rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp)
        rv.setTextColor(id, color)
    }

    private fun texts(rv: RemoteViews, i: Input, pal: Palette, titleSp: Float, artistSp: Float) {
        val s = i.state
        val scale = i.cfg.textScale / 100f
        val title = s.title?.takeIf { it.isNotBlank() } ?: if (s.trackId == null) tr("Nic nie jest odtwarzane") else tr("Nieznany utwór")
        val artist = if (s.trackId == null) tr("Dotknij ▶, aby zagrać") else s.artist?.takeIf { it.isNotBlank() } ?: tr("Nieznany wykonawca")
        rv.setTextViewText(R.id.w_title, title)
        rv.setTextViewText(R.id.w_artist, artist)
        textStyle(rv, R.id.w_title, titleSp * scale, pal.fg)
        textStyle(rv, R.id.w_artist, artistSp * scale, pal.fg2)
    }

    private fun icon(rv: RemoteViews, id: Int, res: Int, color: Int) {
        rv.setImageViewResource(id, res)
        rv.setInt(id, "setColorFilter", color)
    }

    private fun controls(context: Context, rv: RemoteViews, i: Input, pal: Palette) {
        val playing = i.state.playing
        rv.setImageViewResource(R.id.w_play, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        if (i.cfg.filledPlay) {
            rv.setViewVisibility(R.id.w_play_bg, View.VISIBLE)
            rv.setInt(R.id.w_play_bg, "setColorFilter", pal.accent)
            rv.setInt(R.id.w_play, "setColorFilter", pal.onAccent)
        } else {
            rv.setViewVisibility(R.id.w_play_bg, View.GONE)
            rv.setInt(R.id.w_play, "setColorFilter", pal.fg)
        }
        rv.setContentDescription(R.id.w_play, if (playing) tr("Pauza") else tr("Odtwórz"))
        rv.setOnClickPendingIntent(R.id.w_play, action(context, i, WidgetActions.PLAY_PAUSE, 1))
        icon(rv, R.id.w_next, R.drawable.ic_w_next, pal.fg)
        rv.setContentDescription(R.id.w_next, tr("Następny"))
        rv.setOnClickPendingIntent(R.id.w_next, action(context, i, WidgetActions.NEXT, 2))
        if (i.kind != WidgetKind.SHORTCUTS) {
            icon(rv, R.id.w_prev, R.drawable.ic_w_prev, pal.fg)
            rv.setContentDescription(R.id.w_prev, tr("Poprzedni"))
            rv.setOnClickPendingIntent(R.id.w_prev, action(context, i, WidgetActions.PREV, 3))
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

    private fun cover(context: Context, rv: RemoteViews, i: Input, pal: Palette, dp: Int) {
        rv.setViewVisibility(R.id.w_cover, vis(i.cfg.showCover))
        if (!i.cfg.showCover) return
        val px = (dp * scaleOf(context)).roundToInt().coerceIn(48, 420)
        rv.setImageViewBitmap(R.id.w_cover, coverBitmap(context, i, pal, px))
    }

    private fun progress(context: Context, rv: RemoteViews, i: Input, pal: Palette) {
        val show = i.cfg.showProgress && (i.kind != WidgetKind.PLAYER || i.size.hDp >= 140)
        rv.setViewVisibility(R.id.w_progress, vis(show))
        if (!show) return
        val thin = i.kind != WidgetKind.PLAYER
        val inset = when (i.kind) { WidgetKind.PLAYER -> 28; WidgetKind.BAR -> 36; else -> 28 }
        rv.setImageViewBitmap(R.id.w_progress, progressBitmap(context, i, pal, (i.size.wDp - inset).coerceAtLeast(40), thin))
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
        val coverOnArt = i.kind == WidgetKind.COVER && c.showCover && i.art != null
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

    private fun scaleOf(context: Context) = min(context.resources.displayMetrics.density, 2.25f)

    private fun background(context: Context, i: Input): Bitmap {
        val c = i.cfg
        val sc = scaleOf(context)
        var w = (i.size.wDp * sc).roundToInt().coerceAtLeast(8)
        var h = (i.size.hDp * sc).roundToInt().coerceAtLeast(8)
        val limit = 900
        if (max(w, h) > limit) {
            val f = limit.toFloat() / max(w, h)
            w = (w * f).roundToInt().coerceAtLeast(8); h = (h * f).roundToInt().coerceAtLeast(8)
        }
        val radius = c.corner * sc * (w.toFloat() / (i.size.wDp * sc))
        val content = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        val art = i.art
        val coverOnArt = i.kind == WidgetKind.COVER && c.showCover && art != null
        when {
            coverOnArt -> {
                drawCrop(canvas, art!!, w, h)
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
            drawCrop(canvas, art, px, px)
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
            WidgetCoverShape.ROUNDED -> px * 0.16f
            WidgetCoverShape.SQUARE -> px * 0.04f
            WidgetCoverShape.CIRCLE -> px / 2f
        }
        return rounded(content, r, 255, circle = i.cfg.coverShape == WidgetCoverShape.CIRCLE)
    }

    private fun progressBitmap(context: Context, i: Input, pal: Palette, wDp: Int, thin: Boolean): Bitmap {
        val sc = scaleOf(context)
        val w = (wDp * sc).roundToInt().coerceIn(20, 1400)
        val h = ((if (thin) 4 else 12) * sc).roundToInt().coerceAtLeast(3)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val s = i.state
        val f = if (s.durationMs > 0) (s.position().toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(pal.fg, 0x40) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accent }
        val cy = h / 2f
        val style = if (thin) WidgetProgressStyle.LINE else i.cfg.progressStyle
        when (style) {
            WidgetProgressStyle.LINE, WidgetProgressStyle.BOLD -> {
                val th = when {
                    thin -> h.toFloat()
                    style == WidgetProgressStyle.BOLD -> 7 * sc
                    else -> 3.5f * sc
                }
                val knob = if (!thin && style == WidgetProgressStyle.LINE) 5.5f * sc else 0f
                val left = knob
                val right = w - knob
                val r = th / 2
                canvas.drawRoundRect(RectF(left, cy - r, right, cy + r), r, r, track)
                val x = left + (right - left) * f
                if (x > left) canvas.drawRoundRect(RectF(left, cy - r, max(x, left + th), cy + r), r, r, fill)
                if (knob > 0) canvas.drawCircle(x, cy, knob, fill)
            }
            WidgetProgressStyle.SEGMENTS -> {
                val step = 6 * sc
                val bw = 3.2f * sc
                val n = max(4, (w / step).toInt())
                val gap = (w - n * bw) / (n - 1).coerceAtLeast(1)
                val bh = 8 * sc
                for (k in 0 until n) {
                    val x0 = k * (bw + gap)
                    val on = (k + 0.5f) / n <= f
                    canvas.drawRoundRect(RectF(x0, cy - bh / 2, x0 + bw, cy + bh / 2), bw / 2, bw / 2, if (on) fill else track)
                }
            }
        }
        return bmp
    }

    /** Wypełnienie prostokąta obrazem z przycięciem do proporcji (jak centerCrop). */
    private fun drawCrop(canvas: Canvas, src: Bitmap, w: Int, h: Int) {
        val sr = src.width.toFloat() / src.height
        val dr = w.toFloat() / h
        val crop = if (sr > dr) {
            val cw = (src.height * dr).roundToInt()
            val x = (src.width - cw) / 2
            Rect(x, 0, x + cw, src.height)
        } else {
            val ch = (src.width / dr).roundToInt()
            val y = (src.height - ch) / 2
            Rect(0, y, src.width, y + ch)
        }
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
