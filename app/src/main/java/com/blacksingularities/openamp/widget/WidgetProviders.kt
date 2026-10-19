package com.blacksingularities.openamp.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.data.SmartList
import com.blacksingularities.openamp.data.Smart
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.playback.PlaybackService
import com.blacksingularities.openamp.playback.toRequestItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Wspólna część wszystkich widżetów: rysowanie przez [WidgetHub], przyciski przez [WidgetActions]. */
abstract class OpenAmpWidget(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action?.startsWith(WidgetActions.PREFIX) == true) {
            val pending = goAsync()
            WidgetActions.scope.launch {
                try {
                    withTimeoutOrNull(9_000) { WidgetActions.perform(context.applicationContext, intent) }
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        renderAsync(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        renderAsync(context, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetConfigStore.delete(context, it) }
    }

    private fun renderAsync(context: Context, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        Graph.scope.launch {
            try {
                withTimeoutOrNull(9_000) { WidgetHub.render(app, kind, ids) }
            } finally {
                pending.finish()
            }
        }
    }
}

class CoverWidget : OpenAmpWidget(WidgetKind.COVER)
class BarWidget : OpenAmpWidget(WidgetKind.BAR)
class PlayerWidget : OpenAmpWidget(WidgetKind.PLAYER)
class ShortcutsWidget : OpenAmpWidget(WidgetKind.SHORTCUTS)

/**
 * Polecenia z przycisków widżetów. Kontroler sesji łączy się z serwisem odtwarzania na żądanie
 * i rozłącza się sam po chwili bezczynności, żeby nie trzymać serwisu przy życiu.
 */
object WidgetActions {
    const val PREFIX = "com.blacksingularities.openamp.widget."
    const val PLAY_PAUSE = PREFIX + "PLAY_PAUSE"
    const val NEXT = PREFIX + "NEXT"
    const val PREV = PREFIX + "PREV"
    const val SHUFFLE = PREFIX + "SHUFFLE"
    const val REPEAT = PREFIX + "REPEAT"
    const val LIKE = PREFIX + "LIKE"
    const val SHORTCUT = PREFIX + "SHORTCUT"
    const val EXTRA_SHORTCUT = "shortcut"

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var releaseJob: Job? = null

    private suspend fun controller(context: Context): MediaController? = withContext(Dispatchers.Main) {
        controller?.takeIf { it.isConnected }?.let { return@withContext it }
        controller?.release()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = runCatching { MediaController.Builder(context, token).buildAsync().await() }.getOrNull()
        controller = c
        c
    }

    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(45_000)
            controller?.release()
            controller = null
        }
    }

    suspend fun perform(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val shortcut = intent.getStringExtra(EXTRA_SHORTCUT)?.let { n -> WidgetShortcut.entries.firstOrNull { it.name == n } }
        when {
            action == LIKE -> {
                val id = WidgetHub.currentTrackIdFast() ?: WidgetHub.current(context).trackId ?: return
                Graph.userData.toggleFavorite(id)
                WidgetHub.request()
                return
            }
            action == SHORTCUT && shortcut == WidgetShortcut.SLEEP -> {
                if (Graph.sleepAt.value != null || Graph.sleepEndOfTrack.value) {
                    Graph.clearSleep()
                    toast(context, tr("Wyłącznik czasowy wyłączony"))
                } else {
                    Graph.setSleep(30)
                    toast(context, tr("Wyłącznik czasowy: 30 min"))
                }
                WidgetHub.request()
                return
            }
        }
        val c = controller(context) ?: return
        when (action) {
            PLAY_PAUSE -> togglePlay(context, c)
            NEXT -> if (c.mediaItemCount > 0) c.seekToNext()
            PREV -> if (c.mediaItemCount > 0) {
                if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0) else c.seekToPreviousMediaItem()
            }
            SHUFFLE -> c.shuffleModeEnabled = !c.shuffleModeEnabled
            REPEAT -> c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            SHORTCUT -> when (shortcut) {
                null -> Unit
                WidgetShortcut.RESUME -> resume(context, c)
                else -> {
                    val list = tracksFor(shortcut)
                    if (list.isEmpty()) toast(context, tr("Brak utworów")) else playList(c, list)
                }
            }
        }
        scheduleRelease()
    }

    private suspend fun waitForQueue(c: MediaController) {
        // Świeżo uruchomiony serwis odtwarza zapamiętaną kolejkę w tle – dajemy mu chwilę.
        var waited = 0
        while (c.mediaItemCount == 0 && waited < 3_000) { delay(100); waited += 100 }
    }

    private suspend fun togglePlay(context: Context, c: MediaController) {
        if (c.mediaItemCount > 0 && (c.isPlaying || c.playWhenReady)) {
            c.pause()
            return
        }
        resume(context, c)
    }

    private suspend fun resume(context: Context, c: MediaController) {
        if (c.mediaItemCount == 0) waitForQueue(c)
        if (c.mediaItemCount == 0) {
            val list = tracksFor(WidgetShortcut.SHUFFLE_ALL)
            if (list.isEmpty()) toast(context, tr("Brak utworów")) else playList(c, list)
            return
        }
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
        c.volume = 1f
        c.play()
    }

    private fun playList(c: MediaController, list: List<Track>) {
        c.shuffleModeEnabled = false
        c.setMediaItems(list.map { it.toRequestItem() }, 0, 0)
        c.prepare()
        c.volume = 1f
        c.play()
    }

    private suspend fun tracksFor(s: WidgetShortcut): List<Track> = withContext(Dispatchers.Default) {
        runCatching { Graph.ensureScanned() }
        val all = Graph.library.tracks.value
        val data = Graph.userData.value
        fun mixed(list: List<Track>) = if (Graph.settings.value.smartShuffle) Smart.shuffle(list, data) else list.shuffled()
        fun mix(id: String) = Smart.mixes(all, data).firstOrNull { it.id == id }?.tracks
        when (s) {
            WidgetShortcut.SHUFFLE_ALL, WidgetShortcut.RESUME -> mixed(all)
            WidgetShortcut.DAILY_MIX -> mix("daily") ?: mixed(all)
            WidgetShortcut.DISCOVER -> mix("discover") ?: mixed(Graph.library.smart(all, SmartList.NEVER_PLAYED, data))
            WidgetShortcut.FORGOTTEN -> mix("forgotten") ?: emptyList()
            WidgetShortcut.FAVORITES -> mixed(Graph.library.smart(all, SmartList.FAVORITES, data))
            WidgetShortcut.RECENT_ADDED -> Graph.library.smart(all, SmartList.RECENTLY_ADDED, data)
            WidgetShortcut.MOST_PLAYED -> Graph.library.smart(all, SmartList.MOST_PLAYED, data)
            WidgetShortcut.NEVER_PLAYED -> mixed(Graph.library.smart(all, SmartList.NEVER_PLAYED, data))
            WidgetShortcut.SLEEP, WidgetShortcut.SEARCH, WidgetShortcut.EQUALIZER -> emptyList()
        }
    }

    private fun toast(context: Context, text: String) {
        scope.launch { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }
}
