package com.blacksingularities.openamp.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import android.os.SystemClock
import androidx.media3.common.Player
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.RepeatMode
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.playback.Waveform
import com.blacksingularities.openamp.ui.ArtworkLoader
import com.blacksingularities.openamp.ui.theme.vibrantColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** Migawka odtwarzacza dla widżetów (bez odwołań do samego odtwarzacza). */
data class WidgetState(
    val trackId: Long? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    /** Chwila odczytu pozycji (elapsedRealtime) – do wyliczenia bieżącej pozycji podczas grania. */
    val at: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val index: Int = -1,
    val count: Int = 0,
) {
    fun position(now: Long = SystemClock.elapsedRealtime()): Long {
        val p = if (playing) positionMs + (now - at) else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * Centrum widżetów: serwis odtwarzania zgłasza tu zmiany, a hub przerysowuje wszystkie
 * widżety na pulpicie (pełna aktualizacja przy zmianie utworu/stanu, co 2 s tylko pasek postępu).
 * Gdy serwis nie działa, widżety pokazują ostatni zapamiętany utwór z kolejki.
 */
object WidgetHub {
    @Volatile private var live: WidgetState? = null
    private val trigger = MutableStateFlow(0L)
    private var started = false
    private val renderLock = Mutex()
    private val artLock = Mutex()

    private var artId: Long? = null
    private var art: Bitmap? = null
    private var artColor: Int? = null

    /** Utwory, dla których fala dźwiękowa jest właśnie liczona w tle. */
    private val wavePending = mutableSetOf<Long>()

    /** Widżety z paskiem postępu lub czasem: (rodzaj, id). */
    @Volatile private var progressTargets: List<Pair<WidgetKind, Int>> = emptyList()
    private var ticks = 0

    fun providerClass(kind: WidgetKind): Class<*> = when (kind) {
        WidgetKind.COVER -> CoverWidget::class.java
        WidgetKind.BAR -> BarWidget::class.java
        WidgetKind.PLAYER -> PlayerWidget::class.java
        WidgetKind.SHORTCUTS -> ShortcutsWidget::class.java
    }

    fun ids(context: Context, kind: WidgetKind): IntArray = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, providerClass(kind)))
    }.getOrDefault(IntArray(0))

    fun anyWidgets(context: Context): Boolean = WidgetKind.entries.any { ids(context, it).isNotEmpty() }

    /** Wywoływane przez serwis (wątek główny) po każdej zmianie odtwarzacza. */
    fun publish(player: Player) {
        live = snapshot(player)
        request()
    }

    /** Co sekundę z serwisu: odświeżenie pozycji, co 2 s – pasek postępu na pulpicie. */
    fun tick(player: Player) {
        if (progressTargets.isEmpty() || !player.isPlaying) return
        live = snapshot(player)
        if (++ticks % 2 != 0) return
        val pm = Graph.app.getSystemService(PowerManager::class.java)
        if (pm?.isInteractive == false) return
        Graph.scope.launch { runCatching { withTimeout(8_000) { updateProgress(Graph.app) } } }
    }

    /** Serwis kończy pracę: zostaje ostatni utwór, wstrzymany. */
    fun serviceStopped() {
        live = live?.copy(playing = false, positionMs = live?.position() ?: 0)
        request()
    }

    fun request() {
        ensureStarted()
        trigger.update { it + 1 }
    }

    private fun ensureStarted() {
        synchronized(this) {
            if (started) return
            started = true
        }
        Graph.scope.launch {
            // Bez przerywania: każde przerysowanie kończy się (StateFlow sam zlewa zaległe zdarzenia).
            trigger.collect {
                delay(120)
                // Limit czasu: zawieszone przerysowanie nie może zablokować kolejnych na zawsze.
                runCatching { withTimeout(15_000) { renderAll(Graph.app) } }.exceptionOrNull()?.let { log(Graph.app, "renderAll", it) }
            }
        }
        // Serduszko i wyłącznik czasowy też są widoczne na widżetach.
        Graph.scope.launch {
            Graph.userData.state.map { it.favorites }.distinctUntilChanged().collect { trigger.update { it + 1 } }
        }
        Graph.scope.launch {
            Graph.sleepAt.collect { trigger.update { it + 1 } }
        }
        // Pasek „jak w aplikacji” zmienia się razem ze stylem taśmy na ekranie odtwarzania.
        Graph.scope.launch {
            Graph.settings.state.map { it.seekStyle }.distinctUntilChanged().collect { trigger.update { it + 1 } }
        }
    }

    private fun snapshot(p: Player): WidgetState {
        val md = p.mediaMetadata
        return WidgetState(
            trackId = p.currentMediaItem?.mediaId?.toLongOrNull(),
            title = md.title?.toString(),
            artist = md.artist?.toString(),
            album = md.albumTitle?.toString(),
            playing = p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING),
            positionMs = p.currentPosition.coerceAtLeast(0),
            at = SystemClock.elapsedRealtime(),
            durationMs = p.duration.takeIf { it > 0 } ?: 0,
            shuffle = p.shuffleModeEnabled,
            repeat = when (p.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            index = p.currentMediaItemIndex,
            count = p.mediaItemCount,
        )
    }

    /** Stan bez działającego serwisu: utwór zapamiętany razem z kolejką. */
    private suspend fun saved(context: Context): WidgetState {
        val prefs = context.getSharedPreferences("openamp_queue", Context.MODE_PRIVATE)
        val ids = prefs.getString("ids", null)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
        if (ids.isEmpty()) return WidgetState()
        val index = prefs.getInt("index", 0).coerceIn(0, ids.size - 1)
        if (Graph.library.tracks.value.isEmpty()) runCatching { Graph.ensureScanned() }
        val t = Graph.library.get(ids[index]) ?: return WidgetState(count = ids.size)
        return WidgetState(
            trackId = t.id, title = t.title, artist = t.artist, album = t.album,
            positionMs = prefs.getLong("pos", 0), durationMs = t.durationMs,
            shuffle = Graph.settings.value.shuffle, repeat = Graph.settings.value.repeat,
            index = index, count = ids.size,
        )
    }

    suspend fun current(context: Context): WidgetState = live ?: saved(context)

    fun currentTrackIdFast(): Long? = live?.trackId

    private suspend fun artwork(context: Context, track: Track?): Pair<Bitmap?, Int?> = artLock.withLock {
        if (track == null) return@withLock null to null
        if (artId == track.id) return@withLock art to artColor
        // Odczyt w osobnym zadaniu: zawieszony plik nie trzyma blokady – po 3 s rysujemy bez okładki.
        val job = Graph.scope.async(Dispatchers.IO) { runCatching { ArtworkLoader.load(context, track, 360) }.getOrNull() }
        val bmp = withTimeoutOrNull(3_000) { job.await() }
        artId = track.id
        art = bmp
        artColor = bmp?.let { runCatching { vibrantColor(it) }.getOrNull() }
        art to artColor
    }

    private fun sizeOf(mgr: AppWidgetManager, id: Int, kind: WidgetKind): WidgetRenderer.Size {
        val o = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
        val def = WidgetRenderer.defaultSize(kind)
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 } ?: def.wDp
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)?.takeIf { it > 0 } ?: def.hDp
        return WidgetRenderer.Size(w, h)
    }

    /** Dane wejściowe do narysowania widżetu – wspólne dla pulpitu i podglądu w konfiguracji. */
    suspend fun input(context: Context, kind: WidgetKind, id: Int, cfg: WidgetConfig, size: WidgetRenderer.Size? = null, lite: Boolean = false): WidgetRenderer.Input {
        val state = current(context)
        val track = state.trackId?.let { Graph.library.get(it) }
        // Okładka z limitem czasu – zawieszony odczyt nie może zatrzymać odświeżania widżetów.
        val (bmp, color) = if (lite) null to null else withTimeoutOrNull(4_000) { artwork(context, track) } ?: (null to null)
        val mgr = AppWidgetManager.getInstance(context)
        val appSeek = Graph.settings.value.seekStyle
        val style = WidgetRenderer.resolveStyle(cfg.progressStyle, appSeek)
        val wave = if (!lite && track != null && kind != WidgetKind.SHORTCUTS && cfg.showProgress && WidgetRenderer.usesWave(style)) waveOf(context, track) else null
        return WidgetRenderer.Input(
            kind = kind, id = id, cfg = cfg, state = state,
            size = size ?: sizeOf(mgr, id, kind),
            art = bmp, artColor = color,
            favorite = state.trackId != null && state.trackId in Graph.userData.value.favorites,
            sleepOn = Graph.sleepAt.value != null || Graph.sleepEndOfTrack.value,
            appAccent = Graph.settings.value.accent,
            wave = wave,
            appSeek = appSeek,
        )
    }

    /** Fala z pamięci podręcznej; gdy jej nie ma – liczona w tle, a widżety przerysowane po wszystkim. */
    private fun waveOf(context: Context, track: Track): FloatArray? {
        Waveform.cached(context, track)?.let { return it }
        val start = synchronized(wavePending) { wavePending.add(track.id) }
        if (start) Graph.scope.launch(Dispatchers.Default) {
            val ok = runCatching { Waveform.load(context.applicationContext, track) }.getOrNull() != null
            synchronized(wavePending) { wavePending.remove(track.id) }
            if (ok) request()
        }
        return null
    }

    suspend fun render(context: Context, kind: WidgetKind, ids: IntArray) = renderLock.withLock {
        if (ids.isEmpty()) return@withLock
        val mgr = AppWidgetManager.getInstance(context)
        for (id in ids) {
            val cfg = WidgetConfigStore.load(context, id, kind)
            // Najpierw pełny widżet; gdy się nie uda (np. zbyt duże bitmapy odrzucone przez system) –
            // mniejsze tło, a w ostateczności wersja bez okładki i fali, byle stan utworu był aktualny.
            var done = false
            for (attempt in 0..3) {
                val lite = attempt == 3
                val r = runCatching {
                    val inp = input(context, kind, id, cfg, lite = lite)
                    mgr.updateAppWidget(id, WidgetRenderer.build(context, inp))
                }
                if (r.isSuccess) { done = true; break }
                log(context, "render $kind#$id try $attempt", r.exceptionOrNull())
                if (attempt < 2 && WidgetRenderer.bgLimit > 360) WidgetRenderer.bgLimit = (WidgetRenderer.bgLimit * 2) / 3
            }
            if (!done) log(context, "render $kind#$id gave up", null)
        }
        refreshTargets(context)
    }

    suspend fun renderAll(context: Context) {
        for (kind in WidgetKind.entries) render(context, kind, ids(context, kind))
    }

    private fun refreshTargets(context: Context) {
        progressTargets = WidgetKind.entries.filter { it != WidgetKind.SHORTCUTS }.flatMap { kind ->
            ids(context, kind).filter { id ->
                val c = WidgetConfigStore.load(context, id, kind)
                c.showProgress || (kind == WidgetKind.PLAYER && c.showTime)
            }.map { kind to it }
        }
    }

    /** Ostatnie błędy rysowania widżetów (pliki aplikacji, widget_log.txt) – do diagnozy. */
    private fun log(context: Context, what: String, e: Throwable?) {
        android.util.Log.w("OpenAmpWidget", what, e)
        runCatching {
            val f = java.io.File(context.filesDir, "widget_log.txt")
            if (f.length() > 64_000) f.delete()
            f.appendText("${java.util.Date()} $what: ${e?.stackTraceToString()?.take(1500) ?: ""}\n")
        }
    }

    private suspend fun updateProgress(context: Context) = renderLock.withLock {
        val mgr = AppWidgetManager.getInstance(context)
        for ((kind, id) in progressTargets) {
            val cfg = WidgetConfigStore.load(context, id, kind)
            val views = runCatching { WidgetRenderer.buildProgress(context, input(context, kind, id, cfg)) }.getOrNull() ?: continue
            runCatching { mgr.partiallyUpdateAppWidget(id, views) }
        }
    }
}
