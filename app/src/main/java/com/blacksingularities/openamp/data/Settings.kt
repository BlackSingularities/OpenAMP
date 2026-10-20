package com.blacksingularities.openamp.data

import android.content.Context
import android.content.SharedPreferences
import com.blacksingularities.openamp.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT, BLACK }
enum class VisualizerStyle { BARS, WAVE, CIRCLE, PULSE, MIRROR, RING_WAVE, LINE, NONE }
enum class SeekStyle { WAVEFORM, MIRROR, LINE, DOTS, SEGMENTS, TAPE, SPECTROGRAM, CASSETTE }
enum class RepeatMode { OFF, ALL, ONE }
enum class NpBackground { BLUR, GRADIENT, AURORA, BLACK }
enum class CoverShape { ROUNDED, SQUARE, CIRCLE, VINYL }
/** Dodatkowe przyciski w powiadomieniu (obok poprzedni / odtwórz / następny). */
enum class NotifButton { NONE, LIKE, SHUFFLE, REPEAT, CLOSE }
enum class LockBackground { BLUR, ART_COLOR, BLACK }
/** Wygląd powiadomienia: własny układ (okładka po lewej) albo systemowy odtwarzacz. */
enum class NotifStyle { CUSTOM, SYSTEM }
/** Uproszczony ekran „always on” odtwarzacza na ekranie blokady. */
enum class AodMode { OFF, CHARGING, ALWAYS }

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: ThemeMode = ThemeMode.BLACK,
    val artworkColors: Boolean = false,
    val accent: Int = 0xFFFFFFFF.toInt(),
    val visualizer: VisualizerStyle = VisualizerStyle.BARS,
    val seekStyle: SeekStyle = SeekStyle.WAVEFORM,
    val blurBackground: Boolean = true,
    val smartEq: Boolean = false,
    val ambient: Boolean = true,
    val showLikes: Boolean = true,
    val smartShuffle: Boolean = true,
    val npBackground: NpBackground = NpBackground.BLUR,
    val coverShape: CoverShape = CoverShape.ROUNDED,
    val showTechInfo: Boolean = true,
    val keepScreenOn: Boolean = false,
    val lyricsCenter: Boolean = false,
    val coverGestures: Boolean = true,
    val accentFromArtStrength: Boolean = true,
    val lyricsSize: Int = 22,
    val lyricsOnline: Boolean = true,
    val lyricsByDefault: Boolean = false,
    val skipSilence: Boolean = false,
    val resumeOnHeadset: Boolean = false,
    val pauseOnDisconnect: Boolean = true,
    val rememberQueue: Boolean = true,
    val fade: Boolean = true,
    val countPlayPercent: Int = 50,
    val minDurationSec: Int = 30,
    val excludedFolders: String = "",
    val hiddenFormats: String = "",
    val hideDisliked: Boolean = false,
    val autoUpdate: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    // Biblioteka
    val tab: LibraryTab = LibraryTab.TRACKS,
    val trackDensity: Int = 2,
    val trackSort: TrackSort = TrackSort.TITLE,
    val trackSortDesc: Boolean = false,
    val groupSort: GroupSort = GroupSort.NAME,
    val groupSortDesc: Boolean = false,
    val trackView: ViewMode = ViewMode.LIST,
    val groupView: ViewMode = ViewMode.GRID,
    // Odtwarzanie
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    // Powiadomienie
    val notifButton1: NotifButton = NotifButton.LIKE,
    val notifButton2: NotifButton = NotifButton.CLOSE,
    /** Zamknięcie odtwarzacza (i powiadomienia) po tylu minutach pauzy; -1 – nigdy, 0 – od razu. */
    val autoCloseMin: Int = 5,
    /** Zwarte powiadomienie (niskie, także po rozwinięciu) i stały przycisk X. */
    val notifCompact: Boolean = true,
    val notifClose: Boolean = true,
    // Ekran blokady i AOD
    val lockScreen: Boolean = false,
    val lockBackground: LockBackground = LockBackground.BLUR,
    val lockClock: Boolean = true,
    val lockSeek: Boolean = true,
    val lockNext: Boolean = true,
    val lockLike: Boolean = true,
    val lockShuffleRepeat: Boolean = false,
    val aodMode: AodMode = AodMode.CHARGING,
    val aodTimeoutSec: Int = 15,
    val aodDim: Boolean = true,
    val aodArt: Boolean = true,
    val notifStyle: NotifStyle = NotifStyle.CUSTOM,
    /** Dolny pasek na ekranie odtwarzania chowa się po chwili bez dotyku. */
    val autoHideBar: Boolean = true,
    /** Nagłówki sekcji (litery, lata…) przyklejone u góry list. */
    val stickyHeaders: Boolean = true,
    /** Widok miksów: siatka kart albo lista. */
    val homeView: ViewMode = ViewMode.GRID,
    /** Tytuł i wykonawca na ekranie odtwarzania: wyśrodkowane i rozmiar tytułu w sp. */
    val titleCenter: Boolean = false,
    val titleSize: Int = 20,
    // AI
    val aiPrompt: Boolean = true,
    val aiModel: String = "claude-opus-5-5",
)

/** Ustawienia w SharedPreferences, wystawione jako StateFlow. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("openamp_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val value: AppSettings get() = _state.value

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_state.value)
        if (next == _state.value) return
        _state.value = next
        save(next)
    }

    private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, def: E): E =
        getString(key, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: def

    private fun load(): AppSettings {
        val d = AppSettings()
        return with(prefs) {
            AppSettings(
                language = enum("language", d.language),
                theme = enum("theme", d.theme),
                artworkColors = getBoolean("artworkColors", d.artworkColors),
                accent = getInt("accent", d.accent),
                visualizer = enum("visualizer", d.visualizer),
                seekStyle = enum("seekStyle", d.seekStyle),
                blurBackground = getBoolean("blurBackground", d.blurBackground),
                smartEq = getBoolean("smartEq", d.smartEq),
                ambient = getBoolean("ambient", d.ambient),
                showLikes = getBoolean("showLikes", d.showLikes),
                smartShuffle = getBoolean("smartShuffle", d.smartShuffle),
                npBackground = enum("npBackground", d.npBackground),
                coverShape = enum("coverShape", d.coverShape),
                showTechInfo = getBoolean("showTechInfo", d.showTechInfo),
                keepScreenOn = getBoolean("keepScreenOn", d.keepScreenOn),
                lyricsCenter = getBoolean("lyricsCenter", d.lyricsCenter),
                coverGestures = getBoolean("coverGestures", d.coverGestures),
                accentFromArtStrength = getBoolean("accentFromArtStrength", d.accentFromArtStrength),
                lyricsSize = getInt("lyricsSize", d.lyricsSize),
                lyricsOnline = getBoolean("lyricsOnline", d.lyricsOnline),
                lyricsByDefault = getBoolean("lyricsByDefault", d.lyricsByDefault),
                skipSilence = getBoolean("skipSilence", d.skipSilence),
                resumeOnHeadset = getBoolean("resumeOnHeadset", d.resumeOnHeadset),
                pauseOnDisconnect = getBoolean("pauseOnDisconnect", d.pauseOnDisconnect),
                rememberQueue = getBoolean("rememberQueue", d.rememberQueue),
                fade = getBoolean("fade", d.fade),
                countPlayPercent = getInt("countPlayPercent", d.countPlayPercent),
                minDurationSec = getInt("minDurationSec", d.minDurationSec),
                excludedFolders = getString("excludedFolders", d.excludedFolders) ?: "",
                hiddenFormats = getString("hiddenFormats", d.hiddenFormats) ?: "",
                hideDisliked = getBoolean("hideDisliked", d.hideDisliked),
                autoUpdate = getBoolean("autoUpdate", d.autoUpdate),
                lastUpdateCheck = getLong("lastUpdateCheck", d.lastUpdateCheck),
                tab = enum("tab", d.tab),
                trackDensity = getInt("trackDensity", d.trackDensity),
                trackSort = enum("trackSort", d.trackSort),
                trackSortDesc = getBoolean("trackSortDesc", d.trackSortDesc),
                groupSort = enum("groupSort", d.groupSort),
                groupSortDesc = getBoolean("groupSortDesc", d.groupSortDesc),
                trackView = enum("trackView", d.trackView),
                groupView = enum("groupView", d.groupView),
                shuffle = getBoolean("shuffle", d.shuffle),
                repeat = enum("repeat", d.repeat),
                speed = getFloat("speed", d.speed),
                pitch = getFloat("pitch", d.pitch),
                notifButton1 = enum("notifButton1", d.notifButton1),
                notifButton2 = enum("notifButton2", d.notifButton2),
                autoCloseMin = getInt("autoCloseMin", d.autoCloseMin),
                notifCompact = getBoolean("notifCompact", d.notifCompact),
                notifClose = getBoolean("notifClose", d.notifClose),
                lockScreen = getBoolean("lockScreen", d.lockScreen),
                lockBackground = enum("lockBackground", d.lockBackground),
                lockClock = getBoolean("lockClock", d.lockClock),
                lockSeek = getBoolean("lockSeek", d.lockSeek),
                lockNext = getBoolean("lockNext", d.lockNext),
                lockLike = getBoolean("lockLike", d.lockLike),
                lockShuffleRepeat = getBoolean("lockShuffleRepeat", d.lockShuffleRepeat),
                aodMode = enum("aodMode", d.aodMode),
                aodTimeoutSec = getInt("aodTimeoutSec", d.aodTimeoutSec),
                aodDim = getBoolean("aodDim", d.aodDim),
                aodArt = getBoolean("aodArt", d.aodArt),
                notifStyle = enum("notifStyle", d.notifStyle),
                autoHideBar = getBoolean("autoHideBar", d.autoHideBar),
                stickyHeaders = getBoolean("stickyHeaders", d.stickyHeaders),
                homeView = enum("homeView", d.homeView),
                titleCenter = getBoolean("titleCenter", d.titleCenter),
                titleSize = getInt("titleSize", d.titleSize),
                aiPrompt = getBoolean("aiPrompt", d.aiPrompt),
                aiModel = getString("aiModel", d.aiModel) ?: d.aiModel,
            )
        }
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString("language", s.language.name)
            .putString("theme", s.theme.name)
            .putBoolean("artworkColors", s.artworkColors)
            .putInt("accent", s.accent)
            .putString("visualizer", s.visualizer.name)
            .putString("seekStyle", s.seekStyle.name)
            .putBoolean("blurBackground", s.blurBackground)
            .putBoolean("smartEq", s.smartEq)
            .putBoolean("ambient", s.ambient)
            .putBoolean("showLikes", s.showLikes)
            .putBoolean("smartShuffle", s.smartShuffle)
            .putString("npBackground", s.npBackground.name)
            .putString("coverShape", s.coverShape.name)
            .putBoolean("showTechInfo", s.showTechInfo)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("lyricsCenter", s.lyricsCenter)
            .putBoolean("coverGestures", s.coverGestures)
            .putBoolean("accentFromArtStrength", s.accentFromArtStrength)
            .putInt("lyricsSize", s.lyricsSize)
            .putBoolean("lyricsOnline", s.lyricsOnline)
            .putBoolean("lyricsByDefault", s.lyricsByDefault)
            .putBoolean("skipSilence", s.skipSilence)
            .putBoolean("resumeOnHeadset", s.resumeOnHeadset)
            .putBoolean("pauseOnDisconnect", s.pauseOnDisconnect)
            .putBoolean("rememberQueue", s.rememberQueue)
            .putBoolean("fade", s.fade)
            .putInt("countPlayPercent", s.countPlayPercent)
            .putInt("minDurationSec", s.minDurationSec)
            .putString("excludedFolders", s.excludedFolders)
            .putString("hiddenFormats", s.hiddenFormats)
            .putBoolean("hideDisliked", s.hideDisliked)
            .putBoolean("autoUpdate", s.autoUpdate)
            .putLong("lastUpdateCheck", s.lastUpdateCheck)
            .putString("tab", s.tab.name)
            .putInt("trackDensity", s.trackDensity)
            .putString("trackSort", s.trackSort.name)
            .putBoolean("trackSortDesc", s.trackSortDesc)
            .putString("groupSort", s.groupSort.name)
            .putBoolean("groupSortDesc", s.groupSortDesc)
            .putString("trackView", s.trackView.name)
            .putString("groupView", s.groupView.name)
            .putBoolean("shuffle", s.shuffle)
            .putString("repeat", s.repeat.name)
            .putFloat("speed", s.speed)
            .putFloat("pitch", s.pitch)
            .putString("notifButton1", s.notifButton1.name)
            .putString("notifButton2", s.notifButton2.name)
            .putInt("autoCloseMin", s.autoCloseMin)
            .putBoolean("notifCompact", s.notifCompact)
            .putBoolean("notifClose", s.notifClose)
            .putBoolean("lockScreen", s.lockScreen)
            .putString("lockBackground", s.lockBackground.name)
            .putBoolean("lockClock", s.lockClock)
            .putBoolean("lockSeek", s.lockSeek)
            .putBoolean("lockNext", s.lockNext)
            .putBoolean("lockLike", s.lockLike)
            .putBoolean("lockShuffleRepeat", s.lockShuffleRepeat)
            .putString("aodMode", s.aodMode.name)
            .putInt("aodTimeoutSec", s.aodTimeoutSec)
            .putBoolean("aodDim", s.aodDim)
            .putBoolean("aodArt", s.aodArt)
            .putString("notifStyle", s.notifStyle.name)
            .putBoolean("autoHideBar", s.autoHideBar)
            .putBoolean("stickyHeaders", s.stickyHeaders)
            .putString("homeView", s.homeView.name)
            .putBoolean("titleCenter", s.titleCenter)
            .putInt("titleSize", s.titleSize)
            .putBoolean("aiPrompt", s.aiPrompt)
            .putString("aiModel", s.aiModel)
            .apply()
    }
}
