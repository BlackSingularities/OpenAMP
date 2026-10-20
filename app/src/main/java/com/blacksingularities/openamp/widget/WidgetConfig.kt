package com.blacksingularities.openamp.widget

import android.content.Context
import android.content.SharedPreferences

/** Rodzaje widżetów na pulpit. */
enum class WidgetKind { COVER, BAR, PLAYER, SHORTCUTS }

/** Tło widżetu. */
enum class WidgetBackground { ART_BLUR, ART_COLOR, DARK, AMOLED, LIGHT, TRANSPARENT, CUSTOM }

/** Kolor akcentu (przycisk odtwarzania, pasek postępu). */
enum class WidgetAccent { ART, APP, WHITE, CUSTOM }

enum class WidgetCoverShape { ROUNDED, SQUARE, CIRCLE }

/**
 * Styl paska postępu: [APP] – jak taśma na ekranie odtwarzania, fale rysowane z prawdziwej
 * fali dźwiękowej utworu (gdy jest już policzona), reszta to klasyczne paski.
 */
enum class WidgetProgressStyle { APP, THIN, LINE, BOLD, SEGMENTS, WAVE, MIRROR, CONTOUR, DOTS }

/** Położenie okładki względem tytułu i przycisków. */
enum class WidgetCoverPos { LEFT, RIGHT, TOP, BACKGROUND }

/** Czasy utworu: pod paskiem albo po jego bokach. */
enum class WidgetTimePos { BELOW, SIDES }

/** Przyciski odtwarzania pod taśmą postępu albo nad nią. */
enum class WidgetControlsPos { BELOW_BAR, ABOVE_BAR }

/** Co robi dotknięcie okładki / tytułu. */
enum class WidgetTap { OPEN_PLAYER, PLAY_PAUSE, NEXT, OPEN_QUEUE, OPEN_LIBRARY }

/** Skróty widżetu „Skróty”. */
enum class WidgetShortcut { RESUME, SHUFFLE_ALL, DAILY_MIX, FAVORITES, RECENT_ADDED, MOST_PLAYED, DISCOVER, FORGOTTEN, NEVER_PLAYED, SLEEP, SEARCH, EQUALIZER }

/** Ustawienia pojedynczego widżetu (każda instancja na pulpicie ma własne). */
data class WidgetConfig(
    val background: WidgetBackground = WidgetBackground.ART_BLUR,
    val customColor: Int = 0xFF1C1C1C.toInt(),
    val opacity: Int = 100,
    val corner: Int = 24,
    val accent: WidgetAccent = WidgetAccent.ART,
    val accentColor: Int = 0xFFFFFFFF.toInt(),
    val coverShape: WidgetCoverShape = WidgetCoverShape.ROUNDED,
    val showCover: Boolean = true,
    val showArtist: Boolean = true,
    val showAlbum: Boolean = true,
    val showProgress: Boolean = true,
    val progressStyle: WidgetProgressStyle = WidgetProgressStyle.LINE,
    val showTime: Boolean = true,
    val showShuffle: Boolean = true,
    val showRepeat: Boolean = true,
    val showPrevNext: Boolean = true,
    val coverPos: WidgetCoverPos = WidgetCoverPos.LEFT,
    /** Rozmiar okładki w % domyślnego (w widżecie „Okładka” – powiększenie kadru). */
    val coverSize: Int = 100,
    /** Kadrowanie okładki w pionie: 0 – góra, 50 – środek, 100 – dół. */
    val coverFocus: Int = 50,
    /** Wysokość paska postępu w dp (Android 12+; starsze – stała z układu). */
    val progressHeight: Int = 14,
    /** Odstęp między paskiem postępu a przyciskami w dp. */
    val progressGap: Int = 0,
    val timePos: WidgetTimePos = WidgetTimePos.BELOW,
    /** Wielkość przycisków w % (Android 12+). */
    val buttonScale: Int = 100,
    /** Margines wewnętrzny widżetu w dp. */
    val padding: Int = 10,
    val titleLines: Int = 2,
    val boldTitle: Boolean = true,
    val controlsPos: WidgetControlsPos = WidgetControlsPos.BELOW_BAR,
    /** Zwarty układ: bez odstępów między okładką, tekstem, taśmą i przyciskami. */
    val compact: Boolean = false,
    val showLike: Boolean = true,
    val showQueuePos: Boolean = true,
    val filledPlay: Boolean = true,
    val textScale: Int = 100,
    val tap: WidgetTap = WidgetTap.OPEN_PLAYER,
    val shortcuts: List<WidgetShortcut> = listOf(WidgetShortcut.RESUME, WidgetShortcut.SHUFFLE_ALL, WidgetShortcut.DAILY_MIX, WidgetShortcut.FAVORITES),
    val shortcutLabels: Boolean = true,
) {
    companion object {
        /** Rozsądne ustawienia startowe dla danego rodzaju. */
        fun defaults(kind: WidgetKind): WidgetConfig = when (kind) {
            WidgetKind.COVER -> WidgetConfig(
                corner = 28, showAlbum = false, showShuffle = false, showRepeat = false, showLike = false, showQueuePos = false,
                coverPos = WidgetCoverPos.BACKGROUND, progressStyle = WidgetProgressStyle.THIN, progressHeight = 4, titleLines = 1,
            )
            WidgetKind.BAR -> WidgetConfig(
                corner = 40, showAlbum = false, showProgress = false, showShuffle = false, showRepeat = false, showQueuePos = false,
                background = WidgetBackground.ART_COLOR, progressStyle = WidgetProgressStyle.THIN, progressHeight = 4, padding = 8, titleLines = 1,
            )
            WidgetKind.PLAYER -> WidgetConfig()
            WidgetKind.SHORTCUTS -> WidgetConfig(background = WidgetBackground.DARK, accent = WidgetAccent.APP, corner = 28, padding = 10, titleLines = 1)
        }

        const val MAX_SHORTCUTS = 6
    }
}

/** Zapis ustawień widżetów: osobne klucze dla każdego identyfikatora widżetu. */
object WidgetConfigStore {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("openamp_widgets", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, def: E): E =
        getString(key, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: def

    fun has(context: Context, id: Int): Boolean = prefs(context).contains("w${id}_bg")

    fun load(context: Context, id: Int, kind: WidgetKind): WidgetConfig {
        val d = WidgetConfig.defaults(kind)
        val p = prefs(context)
        if (!p.contains("w${id}_bg")) return d
        fun k(n: String) = "w${id}_$n"
        return with(p) {
            WidgetConfig(
                background = enum(k("bg"), d.background),
                customColor = getInt(k("customColor"), d.customColor),
                opacity = getInt(k("opacity"), d.opacity),
                corner = getInt(k("corner"), d.corner),
                accent = enum(k("accent"), d.accent),
                accentColor = getInt(k("accentColor"), d.accentColor),
                coverShape = enum(k("coverShape"), d.coverShape),
                showCover = getBoolean(k("showCover"), d.showCover),
                showArtist = getBoolean(k("showArtist"), d.showArtist),
                showAlbum = getBoolean(k("showAlbum"), d.showAlbum),
                showProgress = getBoolean(k("showProgress"), d.showProgress),
                // Starsze wersje zawsze rysowały cienką linię na okładce i pasku – bez nadpisywania jej teraz.
                progressStyle = if (kind != WidgetKind.PLAYER && !contains(k("progressHeight"))) d.progressStyle else enum(k("progressStyle"), d.progressStyle),
                showTime = getBoolean(k("showTime"), d.showTime),
                // Dawny wspólny przełącznik losowania i powtarzania – wartość startowa dla obu nowych.
                showShuffle = getBoolean(k("showShuffle"), getBoolean(k("showShuffleRepeat"), d.showShuffle)),
                showRepeat = getBoolean(k("showRepeat"), getBoolean(k("showShuffleRepeat"), d.showRepeat)),
                showPrevNext = getBoolean(k("showPrevNext"), d.showPrevNext),
                coverPos = enum(k("coverPos"), d.coverPos),
                coverSize = getInt(k("coverSize"), d.coverSize),
                coverFocus = getInt(k("coverFocus"), d.coverFocus),
                progressHeight = getInt(k("progressHeight"), d.progressHeight),
                progressGap = getInt(k("progressGap"), d.progressGap),
                timePos = enum(k("timePos"), d.timePos),
                buttonScale = getInt(k("buttonScale"), d.buttonScale),
                padding = getInt(k("padding"), d.padding),
                titleLines = getInt(k("titleLines"), d.titleLines),
                boldTitle = getBoolean(k("boldTitle"), d.boldTitle),
                controlsPos = enum(k("controlsPos"), d.controlsPos),
                compact = getBoolean(k("compact"), d.compact),
                showLike = getBoolean(k("showLike"), d.showLike),
                showQueuePos = getBoolean(k("showQueuePos"), d.showQueuePos),
                filledPlay = getBoolean(k("filledPlay"), d.filledPlay),
                textScale = getInt(k("textScale"), d.textScale),
                tap = enum(k("tap"), d.tap),
                shortcuts = getString(k("shortcuts"), null)?.split(',')
                    ?.mapNotNull { n -> WidgetShortcut.entries.firstOrNull { it.name == n } }
                    ?: d.shortcuts,
                shortcutLabels = getBoolean(k("shortcutLabels"), d.shortcutLabels),
            )
        }
    }

    fun save(context: Context, id: Int, c: WidgetConfig) {
        fun k(n: String) = "w${id}_$n"
        prefs(context).edit()
            .putString(k("bg"), c.background.name)
            .putInt(k("customColor"), c.customColor)
            .putInt(k("opacity"), c.opacity)
            .putInt(k("corner"), c.corner)
            .putString(k("accent"), c.accent.name)
            .putInt(k("accentColor"), c.accentColor)
            .putString(k("coverShape"), c.coverShape.name)
            .putBoolean(k("showCover"), c.showCover)
            .putBoolean(k("showArtist"), c.showArtist)
            .putBoolean(k("showAlbum"), c.showAlbum)
            .putBoolean(k("showProgress"), c.showProgress)
            .putString(k("progressStyle"), c.progressStyle.name)
            .putBoolean(k("showTime"), c.showTime)
            .putBoolean(k("showShuffle"), c.showShuffle)
            .putBoolean(k("showRepeat"), c.showRepeat)
            .putBoolean(k("showPrevNext"), c.showPrevNext)
            .putString(k("coverPos"), c.coverPos.name)
            .putInt(k("coverSize"), c.coverSize)
            .putInt(k("coverFocus"), c.coverFocus)
            .putInt(k("progressHeight"), c.progressHeight)
            .putInt(k("progressGap"), c.progressGap)
            .putString(k("timePos"), c.timePos.name)
            .putInt(k("buttonScale"), c.buttonScale)
            .putInt(k("padding"), c.padding)
            .putInt(k("titleLines"), c.titleLines)
            .putBoolean(k("boldTitle"), c.boldTitle)
            .putString(k("controlsPos"), c.controlsPos.name)
            .putBoolean(k("compact"), c.compact)
            .putBoolean(k("showLike"), c.showLike)
            .putBoolean(k("showQueuePos"), c.showQueuePos)
            .putBoolean(k("filledPlay"), c.filledPlay)
            .putInt(k("textScale"), c.textScale)
            .putString(k("tap"), c.tap.name)
            .putString(k("shortcuts"), c.shortcuts.joinToString(",") { it.name })
            .putBoolean(k("shortcutLabels"), c.shortcutLabels)
            .apply()
    }

    fun delete(context: Context, id: Int) {
        val p = prefs(context)
        val e = p.edit()
        p.all.keys.filter { it.startsWith("w${id}_") }.forEach { e.remove(it) }
        e.apply()
    }
}
