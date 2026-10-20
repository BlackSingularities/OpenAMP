package com.blacksingularities.openamp.ui

import android.app.Application
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.AppSettings
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.data.UserData
import com.blacksingularities.openamp.i18n.I18n
import com.blacksingularities.openamp.lyrics.Lyrics
import com.blacksingularities.openamp.lyrics.LyricsRepository
import com.blacksingularities.openamp.playback.PlayerConnection
import com.blacksingularities.openamp.playback.Waveform
import com.blacksingularities.openamp.ui.theme.vibrantColor
import com.blacksingularities.openamp.update.UPDATE_API
import com.blacksingularities.openamp.update.UpdateManager
import com.blacksingularities.openamp.update.UpdateState
import com.blacksingularities.openamp.update.UpdateWorker
import com.blacksingularities.openamp.update.isNewer
import com.blacksingularities.openamp.update.parseRelease
import com.blacksingularities.openamp.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DetailKind { ALBUM, ARTIST, GENRE, FOLDER, PLAYLIST, SMART, MIX, CATEGORY }

sealed interface Route {
    data object Player : Route
    data object Library : Route
    data class Detail(val kind: DetailKind, val key: String) : Route
    data object Settings : Route
    data object Equalizer : Route
    data object Stats : Route
    data object Queue : Route
    data object Search : Route
}

sealed interface LyricsUi {
    data object Loading : LyricsUi
    data object None : LyricsUi
    data class Loaded(val lyrics: Lyrics) : LyricsUi
}

data class FileInfo(
    val format: String,
    val bitrateKbps: Int,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val player = PlayerConnection(app).also { it.connect() }
    val settings: StateFlow<AppSettings> = Graph.settings.state
    val tracks: StateFlow<List<Track>> = combine(Graph.library.tracks, Graph.settings.state.map { it.hideDisliked }.distinctUntilChanged(), Graph.userData.state.map { it.disliked }.distinctUntilChanged()) { list, hide, dis ->
        if (hide && dis.isNotEmpty()) list.filter { it.id !in dis } else list
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Graph.library.tracks.value)
    val scanning: StateFlow<Boolean> = Graph.library.scanning
    val userData: StateFlow<UserData> = Graph.userData.state

    val stack = mutableStateListOf<Route>(Route.Player)
    /** Odtwarzacz to strona główna (korzeń stosu); „zamknięcie” go przechodzi do biblioteki. */
    var nowPlayingOpen: Boolean
        get() = route == Route.Player
        set(v) {
            if (v) { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
            else if (route == Route.Player) open(Route.Library)
        }
    var menuOpen by mutableStateOf(false)
    /** Biblioteka pokazuje listę kategorii zamiast utworów. */
    var libraryRoot by mutableStateOf(false)
    var sleepDialog by mutableStateOf(false)

    /** Dolny pasek: przejście do sekcji bez rozbudowy stosu. */
    fun goTab(r: Route) {
        if (route == Route.Search && r != Route.Search) query = ""
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (r != Route.Player) stack.add(r)
    }
    var queueOpen by mutableStateOf(false)
    /** Pliki do usunięcia z urządzenia (potwierdzenie i zgoda systemu w [DeleteHost]). */
    var deleteRequest by mutableStateOf<List<Track>?>(null)
    var showLyrics by mutableStateOf(Graph.settings.value.lyricsByDefault)
    var query by mutableStateOf("")

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    val currentTrack: StateFlow<Track?> = combine(player.state.map { it.trackId }.distinctUntilChanged(), tracks) { id, list ->
        id?.let { i -> Graph.library.get(i) ?: list.firstOrNull { it.id == i } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _lyrics = MutableStateFlow<LyricsUi>(LyricsUi.None)
    val lyrics: StateFlow<LyricsUi> = _lyrics.asStateFlow()
    private val _waveform = MutableStateFlow<FloatArray?>(null)
    val waveform: StateFlow<FloatArray?> = _waveform.asStateFlow()
    private val _artAccent = MutableStateFlow<Int?>(null)
    val artAccent: StateFlow<Int?> = _artAccent.asStateFlow()
    private val _fileInfo = MutableStateFlow<FileInfo?>(null)
    val fileInfo: StateFlow<FileInfo?> = _fileInfo.asStateFlow()
    private val _lyricsOffset = MutableStateFlow(0L)
    val lyricsOffset: StateFlow<Long> = _lyricsOffset.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()
    var updateDialog by mutableStateOf(false)

    private var trackJobs: Job? = null
    private val offsets = app.getSharedPreferences("openamp_lyrics_offset", 0)

    init {
        viewModelScope.launch {
            currentTrack.collect { t -> onTrackChanged(t) }
        }
        val s = settings.value
        if (s.autoUpdate && System.currentTimeMillis() - s.lastUpdateCheck > 6 * 3600_000L) checkUpdate(silent = true)
    }

    private fun onTrackChanged(t: Track?) {
        trackJobs?.cancel()
        _waveform.value = null
        _fileInfo.value = null
        if (t == null) {
            _lyrics.value = LyricsUi.None
            return
        }
        _lyricsOffset.value = offsets.getLong(t.id.toString(), 0L)
        trackJobs = viewModelScope.launch {
            launch { loadLyrics(t, force = false) }
            launch {
                val bmp = ArtworkLoader.load(getApplication(), t, 900)
                _artAccent.value = bmp?.let { withContext(Dispatchers.Default) { vibrantColor(it) } }
                com.blacksingularities.openamp.Graph.artAccent.value = _artAccent.value
            }
            launch { _fileInfo.value = withContext(Dispatchers.IO) { readFileInfo(t) } }
            launch { _waveform.value = Waveform.load(getApplication(), t) }
        }
    }

    private suspend fun loadLyrics(t: Track, force: Boolean) {
        _lyrics.value = LyricsUi.Loading
        val l = runCatching { LyricsRepository.load(getApplication(), t, settings.value.lyricsOnline, force) }.getOrNull()
        _lyrics.value = l?.let { LyricsUi.Loaded(it) } ?: LyricsUi.None
    }

    fun searchLyricsOnline() {
        val t = currentTrack.value ?: return
        viewModelScope.launch {
            _lyrics.value = LyricsUi.Loading
            val l = runCatching { LyricsRepository.load(getApplication(), t, true, force = true) }.getOrNull()
            _lyrics.value = l?.let { LyricsUi.Loaded(it) } ?: LyricsUi.None
        }
    }

    fun importLyrics(uri: Uri) {
        val t = currentTrack.value ?: return
        viewModelScope.launch {
            val l = runCatching { LyricsRepository.importFile(getApplication(), t, uri) }.getOrNull()
            if (l != null) _lyrics.value = LyricsUi.Loaded(l)
        }
    }

    fun shiftLyrics(deltaMs: Long) {
        val t = currentTrack.value ?: return
        val v = if (deltaMs == 0L) 0L else _lyricsOffset.value + deltaMs
        _lyricsOffset.value = v
        offsets.edit().putLong(t.id.toString(), v).apply()
    }

    private fun readFileInfo(t: Track): FileInfo {
        var rate = 0
        var ch = 0
        var depth = 0
        var bitrate = 0
        runCatching {
            val ex = MediaExtractor()
            try {
                ex.setDataSource(getApplication(), t.uri, null)
                for (i in 0 until ex.trackCount) {
                    val f = ex.getTrackFormat(i)
                    if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") != true) continue
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (f.containsKey("bits-per-sample")) depth = f.getInteger("bits-per-sample")
                    if (f.containsKey(MediaFormat.KEY_BIT_RATE)) bitrate = f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
                    break
                }
            } finally {
                ex.release()
            }
        }
        if (bitrate == 0) runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(getApplication(), t.uri)
                bitrate = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0) / 1000
            } finally {
                r.release()
            }
        }
        if (bitrate == 0 && t.durationMs > 0) bitrate = (t.size * 8 / t.durationMs).toInt()
        return FileInfo(t.format, bitrate, rate, ch, depth)
    }

    // ---------- Nawigacja ----------

    val route: Route get() = stack.last()
    fun open(r: Route) { stack.add(r) }
    fun back(): Boolean {
        when {
            queueOpen -> queueOpen = false
            menuOpen -> menuOpen = false
            route == Route.Library && !libraryRoot -> libraryRoot = true
            stack.size > 1 -> { if (route == Route.Search) query = ""; stack.removeAt(stack.lastIndex) }
            else -> return false
        }
        return true
    }

    fun message(text: String) { _messages.tryEmit(text) }

    // ---------- Ustawienia ----------

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        val before = settings.value
        Graph.settings.update(block)
        val after = settings.value
        if (before.language != after.language) I18n.apply(after.language)
        if (before.autoUpdate != after.autoUpdate) {
            if (after.autoUpdate) UpdateWorker.schedule(getApplication()) else UpdateWorker.cancel(getApplication())
        }
        if (before.minDurationSec != after.minDurationSec || before.excludedFolders != after.excludedFolders || before.hiddenFormats != after.hiddenFormats) rescan()
    }

    // ---------- AI ----------

    fun runAnalysis() {
        viewModelScope.launch {
            val err = Graph.curator.analyze(Graph.library.tracks.value, userData.value, I18n.english)
            message(if (err != null) tr("Analiza nie powiodła się: {0}", err) else tr("Gotowe – nowe kategorie i miksy"))
        }
    }

    // ---------- Pliki ----------

    /** Parametry techniczne dowolnego pliku (do okna właściwości). */
    suspend fun fileInfoFor(t: Track): FileInfo = withContext(Dispatchers.IO) { readFileInfo(t) }

    /** Po usunięciu pliku: znika z kolejki, a skan czyści playlisty i miksy. */
    fun onDeleted(ids: Collection<Long>) {
        val q = player.state.value.queue
        q.indices.reversed().filter { q[it] in ids }.forEach { player.removeAt(it) }
        message(tr("Usunięto z urządzenia"))
        rescan()
    }

    fun rescan() {
        viewModelScope.launch {
            ArtworkLoader.clearMissing()
            Graph.rescan()
        }
    }

    // ---------- Aktualizacje ----------

    fun checkUpdate(silent: Boolean = false) {
        if (_update.value is UpdateState.Checking || _update.value is UpdateState.Downloading) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { parseRelease(UpdateManager.httpGet(UPDATE_API)) } }
            Graph.settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            result.onSuccess { info ->
                if (isNewer(info.version, UpdateManager.currentVersion)) {
                    _update.value = UpdateState.Available(info)
                    if (silent) {
                        updateDialog = true
                        UpdateWorker.notifyAvailable(getApplication(), info)
                    }
                } else _update.value = UpdateState.UpToDate
            }.onFailure {
                _update.value = if (silent) UpdateState.Idle else UpdateState.Failed(it.message ?: it.toString())
            }
        }
    }

    fun downloadAndInstall() {
        val info = when (val s = _update.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Ready -> return install(s)
            is UpdateState.Failed -> return checkUpdate()
            else -> return
        }
        val ctx = getApplication<Application>()
        if (!UpdateManager.canInstall(ctx)) {
            message(tr("Zezwól na instalację aplikacji z OpenAMP, aby zaktualizować"))
            UpdateManager.openInstallPermissionSettings(ctx)
            return
        }
        viewModelScope.launch {
            _update.value = UpdateState.Downloading(info, 0f)
            runCatching {
                UpdateManager.download(ctx, info) { p -> _update.value = UpdateState.Downloading(info, p) }
            }.onSuccess { file ->
                val ready = UpdateState.Ready(info, file)
                _update.value = ready
                install(ready)
            }.onFailure { _update.value = UpdateState.Failed(it.message ?: it.toString()) }
        }
    }

    private fun install(s: UpdateState.Ready) {
        val ctx = getApplication<Application>()
        _update.value = UpdateState.Installing(s.info)
        viewModelScope.launch {
            runCatching { UpdateManager.install(ctx, s.file) }
                .onSuccess {
                    // Gdy użytkownik anuluje okno instalatora, przycisk „Zainstaluj” wraca.
                    kotlinx.coroutines.delay(5000)
                    if (_update.value is UpdateState.Installing) _update.value = s
                }
                .onFailure { _update.value = UpdateState.Failed(it.message ?: it.toString()) }
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
