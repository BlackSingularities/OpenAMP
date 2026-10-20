package com.blacksingularities.openamp

import android.app.Application
import com.blacksingularities.openamp.data.MediaLibrary
import com.blacksingularities.openamp.data.SettingsStore
import com.blacksingularities.openamp.data.UserDataStore
import com.blacksingularities.openamp.i18n.I18n
import com.blacksingularities.openamp.playback.AudioFx
import com.blacksingularities.openamp.playback.Dsp
import com.blacksingularities.openamp.update.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Wspólne obiekty aplikacji (jeden proces: interfejs i serwis odtwarzania). */
object Graph {
    lateinit var app: Application
    lateinit var settings: SettingsStore
    lateinit var userData: UserDataStore
    lateinit var library: MediaLibrary
    lateinit var curator: com.blacksingularities.openamp.ai.CuratorStore
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val scanLock = Mutex()
    private var scannedOnce = false

    /** Wyłącznik czasowy: moment zatrzymania (ms) albo „do końca utworu”. */
    private val _sleepAt = MutableStateFlow<Long?>(null)
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()
    private val _sleepEndOfTrack = MutableStateFlow(false)
    val sleepEndOfTrack: StateFlow<Boolean> = _sleepEndOfTrack.asStateFlow()

    fun setSleep(minutes: Int?) {
        _sleepEndOfTrack.value = false
        _sleepAt.value = minutes?.let { System.currentTimeMillis() + it * 60_000L }
    }

    fun setSleepEndOfTrack() {
        _sleepAt.value = null
        _sleepEndOfTrack.value = true
    }

    fun clearSleep() {
        _sleepAt.value = null
        _sleepEndOfTrack.value = false
    }

    suspend fun rescan() = scanLock.withLock {
        val s = settings.value
        library.scan(s.minDurationSec, s.excludedFolders, s.hiddenFormats)
        scannedOnce = true
        pruneDeleted()
    }

    /**
     * Pliki usunięte z urządzenia znikają z playlist, ulubionych oraz kategorii i miksów AI.
     * Zabezpieczenie: gdy naraz „zniknęła” ponad połowa biblioteki (np. odmontowana karta),
     * niczego nie usuwamy.
     */
    private fun pruneDeleted() {
        val ids = library.deviceIds ?: return
        if (ids.isEmpty()) return
        val known = userData.value.playlists.flatMap { it.trackIds }.toSet() + userData.value.favorites + curator.value.knownIds()
        val missing = known.count { it !in ids }
        if (known.size > 20 && missing > known.size / 2) return
        userData.pruneMissing(ids)
        curator.pruneMissing(ids)
    }

    suspend fun ensureScanned() {
        if (!scannedOnce) rescan()
    }
}

class OpenAmpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Graph.app = this
        Graph.settings = SettingsStore(this)
        Graph.userData = UserDataStore(this)
        Graph.library = MediaLibrary(this)
        Graph.curator = com.blacksingularities.openamp.ai.CuratorStore(this)
        I18n.apply(Graph.settings.value.language)
        AudioFx.init(this)
        Dsp.init(this)
        if (Graph.settings.value.autoUpdate) UpdateWorker.schedule(this) else UpdateWorker.cancel(this)
        Graph.scope.launch { Graph.ensureScanned() }
    }
}
