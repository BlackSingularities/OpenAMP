package com.blacksingularities.openamp.wear

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.playback.PlaybackService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** Polecenia z zegarka (Data Layer) – odbierane także wtedy, gdy aplikacja nie jest na ekranie. */
class WearCommandService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearProtocol.PATH_CMD) return
        if (!Graph.settings.value.wearRemote) return
        val (action, arg) = WearProtocol.parse(event.data)
        WearRemote.run(applicationContext, action, arg)
    }
}

/**
 * Wykonanie polecenia z zegarka na tej samej sesji, z której korzysta powiadomienie i widżety.
 * Kontroler jest tworzony na żądanie i zwalniany po minucie bezczynności.
 */
object WearRemote {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var idle: Job? = null

    fun run(context: Context, action: String, arg: String?) {
        scope.launch {
            // Głośność i ulubione nie potrzebują sesji.
            when (action) {
                WearProtocol.CMD_VOLUME -> {
                    volume(context, arg?.toIntOrNull() ?: 0)
                    WearSync.forcePublish()
                    return@launch
                }
                WearProtocol.CMD_REFRESH -> {
                    WearSync.forcePublish()
                    return@launch
                }
            }
            val c = ensure(context) ?: return@launch
            runCatching { apply(c, action, arg) }
            WearSync.forcePublish()
            scheduleRelease()
        }
    }

    private fun apply(c: MediaController, action: String, arg: String?) {
        when (action) {
            WearProtocol.CMD_TOGGLE -> if (c.isPlaying || c.playWhenReady) c.pause() else resume(c)
            WearProtocol.CMD_PLAY -> resume(c)
            WearProtocol.CMD_PAUSE -> c.pause()
            WearProtocol.CMD_NEXT -> c.seekToNext()
            WearProtocol.CMD_PREV -> if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
            WearProtocol.CMD_SEEK -> arg?.toLongOrNull()?.let { c.seekTo(it) }
            WearProtocol.CMD_SHUFFLE -> c.shuffleModeEnabled = !c.shuffleModeEnabled
            WearProtocol.CMD_REPEAT -> c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            WearProtocol.CMD_FAVORITE -> c.currentMediaItem?.mediaId?.toLongOrNull()?.let { Graph.userData.toggleFavorite(it) }
            WearProtocol.CMD_SKIP -> arg?.toLongOrNull()?.let { id ->
                val index = (0 until c.mediaItemCount).firstOrNull { c.getMediaItemAt(it).mediaId.toLongOrNull() == id } ?: return@let
                c.seekTo(index, 0)
                resume(c)
            }
        }
    }

    private fun resume(c: MediaController) {
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
        c.play()
    }

    private fun volume(context: Context, step: Int) {
        if (step == 0) return
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (step > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0,
        )
    }

    private suspend fun ensure(context: Context): MediaController? {
        controller?.let { if (it.isConnected) return it }
        controller?.release()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        // Gdy odtwarzacz jest zamknięty, system może nie pozwolić go wznowić z tła – wtedy
        // zegarek nie dostanie nowej migawki i pokaże podpowiedź „Otwórz OpenAMP na telefonie”.
        controller = runCatching { MediaController.Builder(context, token).buildAsync().await() }.getOrNull()
        return controller
    }

    private fun scheduleRelease() {
        idle?.cancel()
        idle = scope.launch {
            delay(60_000)
            controller?.release()
            controller = null
        }
    }
}
