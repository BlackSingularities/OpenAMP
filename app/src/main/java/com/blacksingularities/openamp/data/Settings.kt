package com.blacksingularities.openamp.data

import android.content.Context
import android.content.SharedPreferences
import com.blacksingularities.openamp.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT, BLACK }
enum class VisualizerStyle { BARS, WAVE, CIRCLE, NONE }
enum class SeekStyle { WAVEFORM, SPECTROGRAM, SIMPLE }
enum class RepeatMode { OFF, ALL, ONE }

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: ThemeMode = ThemeMode.DARK,
    val artworkColors: Boolean = true,
    val accent: Int = 0xFFB45CFF.toInt(),
    val visualizer: VisualizerStyle = VisualizerStyle.BARS,
    val seekStyle: SeekStyle = SeekStyle.WAVEFORM,
    val blurBackground: Boolean = true,
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
    val autoUpdate: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    // Biblioteka
    val tab: LibraryTab = LibraryTab.TRACKS,
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
                autoUpdate = getBoolean("autoUpdate", d.autoUpdate),
                lastUpdateCheck = getLong("lastUpdateCheck", d.lastUpdateCheck),
                tab = enum("tab", d.tab),
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
            .putBoolean("autoUpdate", s.autoUpdate)
            .putLong("lastUpdateCheck", s.lastUpdateCheck)
            .putString("tab", s.tab.name)
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
            .apply()
    }
}
