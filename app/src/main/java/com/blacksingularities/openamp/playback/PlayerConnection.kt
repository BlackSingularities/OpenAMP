package com.blacksingularities.openamp.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.RepeatMode
import com.blacksingularities.openamp.data.Smart
import com.blacksingularities.openamp.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val connected: Boolean = false,
    val trackId: Long? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val durationMs: Long = 0,
    val queue: List<Long> = emptyList(),
    val index: Int = -1,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    /** Sąsiedzi w kolejności odtwarzania (z uwzględnieniem losowania i powtarzania). */
    val nextId: Long? = null,
    /** Indeksy kolejki w faktycznej kolejności grania (przy losowaniu – wylosowanej). */
    val order: List<Int> = emptyList(),
    val prevId: Long? = null,
)

/** Kontroler sesji po stronie interfejsu; stan odtwarzacza jako StateFlow. */
class PlayerConnection(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()
    private var fadeJob: Job? = null

    fun connect() {
        if (controller != null) return
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val c = runCatching { MediaController.Builder(context, token).buildAsync().await() }.getOrNull() ?: return@launch
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            })
            refresh()
            while (isActive) {
                _position.value = c.currentPosition
                delay(if (c.isPlaying) 50 else 250)
            }
        }
    }

    fun release() {
        controller?.release()
        controller = null
    }

    private fun refresh() {
        val c = controller ?: return
        _state.value = PlayerUiState(
            connected = true,
            trackId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING),
            buffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = c.duration.coerceAtLeast(0),
            queue = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId.toLongOrNull() ?: -1 },
            index = c.currentMediaItemIndex,
            shuffle = c.shuffleModeEnabled,
            repeat = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            speed = c.playbackParameters.speed,
            pitch = c.playbackParameters.pitch,
            order = playOrder(c),
            nextId = c.nextMediaItemIndex.takeIf { it >= 0 }?.let { c.getMediaItemAt(it).mediaId.toLongOrNull() },
            prevId = c.previousMediaItemIndex.takeIf { it >= 0 }?.let { c.getMediaItemAt(it).mediaId.toLongOrNull() },
        )
        _position.value = c.currentPosition
    }

    private fun playOrder(c: MediaController): List<Int> {
        val tl = c.currentTimeline
        if (tl.isEmpty) return emptyList()
        val sh = c.shuffleModeEnabled
        val out = ArrayList<Int>(tl.windowCount)
        var i = tl.getFirstWindowIndex(sh)
        while (i != androidx.media3.common.C.INDEX_UNSET && out.size < tl.windowCount) {
            out += i
            i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, sh)
        }
        return out
    }

    private fun items(tracks: List<Track>): List<MediaItem> = tracks.map { it.toRequestItem() }

    fun play(tracks: List<Track>, start: Int = 0, shuffle: Boolean? = null) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        // Sprytne mieszanie: kolejność układa OpenAMP (bez powtórek wykonawcy, rzadziej grane wcześniej).
        if (shuffle == true && Graph.settings.value.smartShuffle) {
            c.shuffleModeEnabled = false
            c.setMediaItems(items(Smart.shuffle(tracks, Graph.userData.value)), 0, 0)
            c.prepare(); c.volume = 1f; c.play()
            return
        }
        if (shuffle != null) c.shuffleModeEnabled = shuffle
        val startIndex = if (shuffle == true) tracks.indices.random() else start.coerceIn(0, tracks.size - 1)
        c.setMediaItems(items(tracks), startIndex, 0)
        c.prepare()
        c.volume = 1f
        c.play()
    }

    fun playNext(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(tracks)
        c.addMediaItems(c.currentMediaItemIndex + 1, items(tracks))
    }

    fun enqueue(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(tracks)
        c.addMediaItems(items(tracks))
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying || c.playWhenReady) pause() else resume()
    }

    fun resume() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
        fadeJob?.cancel()
        if (Graph.settings.value.fade) {
            c.volume = 0f
            c.play()
            fadeJob = scope.launch {
                for (i in 1..10) { c.volume = i / 10f; delay(25) }
            }
        } else {
            c.volume = 1f
            c.play()
        }
    }

    fun pause() {
        val c = controller ?: return
        fadeJob?.cancel()
        if (Graph.settings.value.fade && c.isPlaying) {
            fadeJob = scope.launch {
                val v = c.volume
                for (i in 1..10) { c.volume = v * (1 - i / 10f); delay(25) }
                c.pause()
                c.volume = 1f
            }
        } else c.pause()
    }

    fun next() = controller?.seekToNext() ?: Unit
    /** Poprzedni utwór zawsze (bez „od początku bieżącego”) – dla gestu przesunięcia. */
    fun previousTrack() {
        val c = controller ?: return
        if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekTo(0)
    }

    fun previous() {
        val c = controller ?: return
        // Na początku utworu – poprzedni; dalej – od początku bieżącego.
        if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _position.value = ms
    }

    fun skipTo(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0)
        if (!c.isPlaying) resume()
    }

    fun removeAt(index: Int) = controller?.removeMediaItem(index) ?: Unit
    fun move(from: Int, to: Int) = controller?.moveMediaItem(from, to) ?: Unit
    fun clearQueue() = controller?.clearMediaItems() ?: Unit

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeedPitch(speed: Float, pitch: Float) {
        controller?.playbackParameters = PlaybackParameters(speed, pitch)
    }
}
