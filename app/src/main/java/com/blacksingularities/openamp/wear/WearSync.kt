package com.blacksingularities.openamp.wear

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.Player
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.RepeatMode
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.ui.ArtworkLoader
import com.blacksingularities.openamp.ui.theme.vibrantColor
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Migawka odtwarzania wysyłana na zegarek (bez odwołań do samego odtwarzacza). */
private data class WearSnapshot(
    val trackId: Long?,
    val title: String,
    val artist: String,
    val album: String,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val shuffle: Boolean,
    val repeat: RepeatMode,
    val index: Int,
    val count: Int,
    val queue: List<Long>,
)

/**
 * Odpowiednik [com.blacksingularities.openamp.widget.WidgetHub] dla zegarka: serwis odtwarzania
 * zgłasza tu każdą zmianę, a hub publikuje ją w Data Layer. Zegarek dostaje tytuł, wykonawcę,
 * okładkę, pozycję, kolejkę i głośność; pozycję między publikacjami dolicza sam.
 */
object WearSync {
    private val trigger = MutableStateFlow(0L)
    private var started = false
    private val publishLock = Mutex()

    @Volatile private var live: WearSnapshot? = null

    /** Ostatnio zakodowana okładka (ten sam utwór nie jest kodowany dwa razy). */
    private var artId: Long? = null
    private var artBytes: ByteArray? = null
    private var artColor: Int = 0

    /** Czy w pobliżu jest zegarek – sprawdzane raz na minutę, żeby nie kodować okładek bez potrzeby. */
    private var nodesAt = 0L
    private var hasNodes = false

    private var ticks = 0

    /** Wywoływane przez serwis (wątek główny) po każdej zmianie odtwarzacza. */
    fun publish(player: Player) {
        live = snapshot(player)
        request()
    }

    /** Co sekundę z serwisu: podtrzymanie pozycji na zegarku co 10 s grania. */
    fun tick(player: Player) {
        if (!player.isPlaying) return
        if (++ticks % 10 != 0) return
        live = snapshot(player)
        request()
    }

    /** Serwis kończy pracę: zostaje ostatni utwór, wstrzymany. */
    fun serviceStopped() {
        live = live?.copy(playing = false)
        request()
    }

    /** Zegarek poprosił o świeżą migawkę – bez zwlekania na odświeżenie listy urządzeń. */
    fun forcePublish() {
        nodesAt = 0
        request()
    }

    fun request() {
        if (!Graph.settings.value.wearRemote) return
        ensureStarted()
        trigger.update { it + 1 }
    }

    private fun ensureStarted() {
        synchronized(this) {
            if (started) return
            started = true
        }
        Graph.scope.launch {
            trigger.collect {
                delay(150)
                runCatching { withTimeout(15_000) { publishNow(Graph.app) } }
            }
        }
        // Serduszko na zegarku zmienia się razem z ulubionymi w aplikacji.
        Graph.scope.launch {
            Graph.userData.state.map { it.favorites }.distinctUntilChanged().collect { request() }
        }
    }

    private fun snapshot(player: Player): WearSnapshot {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull()
        val md = player.currentMediaItem?.mediaMetadata
        val track = id?.let { Graph.library.get(it) }
        // Kolejka od bieżącego utworu w przód, w faktycznej kolejności grania.
        val queue = ArrayList<Long>(QUEUE_MAX)
        val tl = player.currentTimeline
        if (!tl.isEmpty) {
            var i = player.currentMediaItemIndex
            while (queue.size < QUEUE_MAX && i != C.INDEX_UNSET) {
                player.getMediaItemAt(i).mediaId.toLongOrNull()?.let { queue += it }
                i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            }
        }
        return WearSnapshot(
            trackId = id,
            title = track?.title ?: md?.title?.toString() ?: "",
            artist = track?.artist ?: md?.artist?.toString() ?: "",
            album = track?.album ?: md?.albumTitle?.toString() ?: "",
            playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING),
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.coerceAtLeast(0),
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            index = player.currentMediaItemIndex,
            count = player.mediaItemCount,
            queue = queue,
        )
    }

    private suspend fun publishNow(context: Context): Unit = publishLock.withLock {
        val s = live ?: return
        if (!nearbyWatch(context)) return
        val art = artwork(context, s.trackId)
        val tracks = s.queue.mapNotNull { Graph.library.get(it) }
        val audio = context.getSystemService(AudioManager::class.java)
        val req = PutDataMapRequest.create(WearProtocol.PATH_STATE).apply {
            dataMap.putLong(WearProtocol.K_TRACK_ID, s.trackId ?: -1L)
            dataMap.putString(WearProtocol.K_TITLE, s.title)
            dataMap.putString(WearProtocol.K_ARTIST, s.artist)
            dataMap.putString(WearProtocol.K_ALBUM, s.album)
            dataMap.putBoolean(WearProtocol.K_PLAYING, s.playing)
            dataMap.putLong(WearProtocol.K_POSITION, s.positionMs)
            dataMap.putLong(WearProtocol.K_UPDATED_AT, System.currentTimeMillis())
            dataMap.putLong(WearProtocol.K_DURATION, s.durationMs)
            dataMap.putBoolean(WearProtocol.K_SHUFFLE, s.shuffle)
            dataMap.putString(WearProtocol.K_REPEAT, s.repeat.name)
            dataMap.putBoolean(WearProtocol.K_FAVORITE, s.trackId != null && s.trackId in Graph.userData.value.favorites)
            dataMap.putInt(WearProtocol.K_INDEX, s.index)
            dataMap.putInt(WearProtocol.K_COUNT, s.count)
            dataMap.putInt(WearProtocol.K_ACCENT, artColor)
            dataMap.putInt(WearProtocol.K_VOLUME, audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
            dataMap.putInt(WearProtocol.K_VOLUME_MAX, audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0)
            dataMap.putLongArray(WearProtocol.K_QUEUE_IDS, tracks.map { it.id }.toLongArray())
            dataMap.putStringArrayList(WearProtocol.K_QUEUE_TITLES, ArrayList(tracks.map { it.title }))
            dataMap.putStringArrayList(WearProtocol.K_QUEUE_ARTISTS, ArrayList(tracks.map { it.artist }))
            if (art != null) dataMap.putAsset(WearProtocol.ASSET_ART, Asset.createFromBytes(art))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(req).await()
        Unit
    }

    /** Okładka w rozmiarze dobrym na zegarek, kodowana raz na utwór. */
    private suspend fun artwork(context: Context, trackId: Long?): ByteArray? {
        if (trackId == null) {
            artId = null
            artBytes = null
            artColor = 0
            return null
        }
        if (artId == trackId) return artBytes
        val track: Track? = Graph.library.get(trackId)
        val bmp = track?.let { runCatching { ArtworkLoader.load(context, it, 320) }.getOrNull() }
        artId = trackId
        artColor = bmp?.let { runCatching { vibrantColor(it) }.getOrNull() } ?: 0
        artBytes = bmp?.let { encode(it) }
        return artBytes
    }

    private suspend fun encode(bmp: Bitmap): ByteArray? = withContext(Dispatchers.Default) {
        runCatching {
            ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
        }.getOrNull()
    }

    private suspend fun nearbyWatch(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - nodesAt < 60_000) return hasNodes
        hasNodes = runCatching {
            Wearable.getNodeClient(context).connectedNodes.await().isNotEmpty()
        }.getOrDefault(false)
        nodesAt = now
        return hasNodes
    }

    private const val QUEUE_MAX = 20
}
