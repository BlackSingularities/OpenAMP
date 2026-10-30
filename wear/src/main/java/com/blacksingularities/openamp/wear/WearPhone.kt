package com.blacksingularities.openamp.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Utwór z kolejki pokazywany na zegarku. */
data class QueueItem(val id: Long, val title: String, val artist: String)

/** To, co gra na telefonie – widziane z zegarka. */
data class NowPlaying(
    /** Czy telefon przysłał już jakąkolwiek migawkę. */
    val known: Boolean = false,
    val trackId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val updatedAt: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: String = "OFF",
    val favorite: Boolean = false,
    val index: Int = -1,
    val count: Int = 0,
    val accent: Int = 0,
    val volume: Int = 0,
    val volumeMax: Int = 0,
    val queue: List<QueueItem> = emptyList(),
) {
    /** Pozycja doliczona od chwili ostatniej migawki. */
    fun position(now: Long = System.currentTimeMillis()): Long {
        val p = if (playing) positionMs + (now - updatedAt) else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }

    val hasTrack: Boolean get() = trackId != null && trackId >= 0 && (title.isNotBlank() || artist.isNotBlank())
}

/**
 * Jedyne miejsce, przez które zegarek rozmawia z telefonem: odbiera migawki odtwarzania
 * z Data Layer i odsyła polecenia (graj, następny, głośność…).
 */
object WearPhone {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private val _art = MutableStateFlow<Bitmap?>(null)
    val art: StateFlow<Bitmap?> = _art.asStateFlow()

    /** Okładka jest pobierana raz na utwór. */
    private var artId: Long? = null

    private var listening = false

    private val listener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
        events.forEach { e ->
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == WearProtocol.PATH_STATE) {
                consume(DataMapItem.fromDataItem(e.dataItem).dataMap)
            }
        }
    }

    /** Ekran z pilotem jest widoczny: nasłuch zmian i prośba o świeżą migawkę. */
    fun start(context: Context) {
        attach(context)
        val app = context.applicationContext
        if (!listening) {
            listening = true
            Wearable.getDataClient(app).addListener(listener)
        }
        scope.launch {
            runCatching {
                Wearable.getDataClient(app).getDataItems(
                    android.net.Uri.Builder().scheme("wear").path(WearProtocol.PATH_STATE).build(),
                ).await().use { items ->
                    items.firstOrNull()?.let { consume(DataMapItem.fromDataItem(it).dataMap) }
                }
            }
        }
        send(app, WearProtocol.CMD_REFRESH)
    }

    fun stop(context: Context) {
        if (!listening) return
        listening = false
        runCatching { Wearable.getDataClient(context.applicationContext).removeListener(listener) }
    }

    /** Migawka z telefonu (także z serwisu działającego w tle). */
    fun consume(map: DataMap) {
        val ids = map.getLongArray(WearProtocol.K_QUEUE_IDS) ?: LongArray(0)
        val titles = map.getStringArrayList(WearProtocol.K_QUEUE_TITLES) ?: arrayListOf()
        val artists = map.getStringArrayList(WearProtocol.K_QUEUE_ARTISTS) ?: arrayListOf()
        val trackId = map.getLong(WearProtocol.K_TRACK_ID, -1L).takeIf { it >= 0 }
        _state.value = NowPlaying(
            known = true,
            trackId = trackId,
            title = map.getString(WearProtocol.K_TITLE, ""),
            artist = map.getString(WearProtocol.K_ARTIST, ""),
            album = map.getString(WearProtocol.K_ALBUM, ""),
            playing = map.getBoolean(WearProtocol.K_PLAYING, false),
            positionMs = map.getLong(WearProtocol.K_POSITION, 0),
            updatedAt = map.getLong(WearProtocol.K_UPDATED_AT, System.currentTimeMillis()),
            durationMs = map.getLong(WearProtocol.K_DURATION, 0),
            shuffle = map.getBoolean(WearProtocol.K_SHUFFLE, false),
            repeat = map.getString(WearProtocol.K_REPEAT, "OFF"),
            favorite = map.getBoolean(WearProtocol.K_FAVORITE, false),
            index = map.getInt(WearProtocol.K_INDEX, -1),
            count = map.getInt(WearProtocol.K_COUNT, 0),
            accent = map.getInt(WearProtocol.K_ACCENT, 0),
            volume = map.getInt(WearProtocol.K_VOLUME, 0),
            volumeMax = map.getInt(WearProtocol.K_VOLUME_MAX, 0),
            queue = ids.mapIndexed { i, id ->
                QueueItem(id, titles.getOrElse(i) { "" }, artists.getOrElse(i) { "" })
            },
        )
        val asset = map.getAsset(WearProtocol.ASSET_ART)
        if (trackId == null) {
            artId = null
            _art.value = null
        } else if (asset != null && artId != trackId) {
            artId = trackId
            loadArt(asset)
        }
    }

    private fun loadArt(asset: Asset) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    Wearable.getDataClient(appContext ?: return@runCatching null)
                        .getFdForAsset(asset).await().inputStream.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bmp != null) _art.value = bmp
        }
    }

    /** Kontekst aplikacji – ustawiany przy pierwszym użyciu (pilot, kafelek, komplikacja). */
    @Volatile private var appContext: Context? = null

    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** Polecenie do telefonu; interfejs zmienia się od razu, nie czekając na potwierdzenie. */
    fun send(context: Context, action: String, arg: Any? = null) {
        attach(context)
        optimistic(action)
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val nodes = Wearable.getNodeClient(app).connectedNodes.await()
                val client = Wearable.getMessageClient(app)
                nodes.forEach { node ->
                    client.sendMessage(node.id, WearProtocol.PATH_CMD, WearProtocol.message(action, arg)).await()
                }
            }
        }
    }

    /** Natychmiastowa reakcja przycisków (telefon dosyła właściwy stan chwilę później). */
    private fun optimistic(action: String) {
        when (action) {
            WearProtocol.CMD_TOGGLE -> _state.update {
                it.copy(playing = !it.playing, positionMs = it.position(), updatedAt = System.currentTimeMillis())
            }
            WearProtocol.CMD_PLAY -> _state.update { it.copy(playing = true, positionMs = it.position(), updatedAt = System.currentTimeMillis()) }
            WearProtocol.CMD_PAUSE -> _state.update { it.copy(playing = false, positionMs = it.position(), updatedAt = System.currentTimeMillis()) }
            WearProtocol.CMD_FAVORITE -> _state.update { it.copy(favorite = !it.favorite) }
            WearProtocol.CMD_SHUFFLE -> _state.update { it.copy(shuffle = !it.shuffle) }
            WearProtocol.CMD_REPEAT -> _state.update {
                it.copy(
                    repeat = when (it.repeat) {
                        "OFF" -> "ALL"
                        "ALL" -> "ONE"
                        else -> "OFF"
                    },
                )
            }
        }
    }
}
