package com.blacksingularities.openamp.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.MainActivity
import com.blacksingularities.openamp.R
import com.blacksingularities.openamp.data.RepeatMode
import com.blacksingularities.openamp.data.Smart
import com.blacksingularities.openamp.data.Track
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val spectrum = SpectrumProcessor()

    // Liczenie odtworzeń: czas faktycznie słuchany w bieżącym utworze.
    private var listenedMs = 0L
    private var pendingListen = 0L
    private var pendingId: Long? = null
    private var counted = false
    private var lastTick = 0L

    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private var headsetCallback: AudioDeviceCallback? = null

    override fun onCreate() {
        super.onCreate()
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(DspProcessor(), spectrum))
                    .build()
        }
        val s = Graph.settings.value
        player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(s.pauseOnDisconnect)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.skipSilenceEnabled = s.skipSilence
        player.shuffleModeEnabled = s.shuffle
        player.repeatMode = when (s.repeat) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        player.playbackParameters = PlaybackParameters(s.speed, s.pitch)
        AudioFx.attach(player.audioSessionId)
        player.addListener(listener)

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(callback)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.app_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )

        scope.launch { Dsp.state.collect { AudioFx.refresh() } }
        observeSettings()
        tickLoop()
        registerHeadset()
        scope.launch { restoreQueue() }
    }

    private val callback = object : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { resolve(it) }.toMutableList())
    }

    /** Element kolejki przekazany przez kontroler ma tylko identyfikator – tu dostaje adres pliku. */
    private fun resolve(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        val id = item.mediaId.toLongOrNull()
        val track = id?.let { Graph.library.get(it) }
        return if (track != null) track.toMediaItem() else item.buildUpon()
            .setUri(item.requestMetadata.mediaUri ?: id?.let { Uri.parse("content://media/external/audio/media/$it") })
            .build()
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            flushListen()
            pendingId = mediaItem?.mediaId?.toLongOrNull()
            listenedMs = 0
            counted = false
            // Inteligentny korektor: preset dobrany do gatunku utworu.
            if (Graph.settings.value.smartEq) {
                val genre = mediaItem?.mediaId?.toLongOrNull()?.let { Graph.library.get(it)?.genre }
                    ?: mediaItem?.mediaMetadata?.genre?.toString()
                Smart.presetForGenre(genre ?: "")?.let { Dsp.applyPreset(it) }
            }
            if (Graph.sleepEndOfTrack.value && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                Graph.clearSleep()
                player.pause()
            }
            saveQueue()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) { saveQueue(); flushListen() }
            lastTick = System.currentTimeMillis()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            AudioFx.attach(audioSessionId)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            Graph.settings.update { it.copy(shuffle = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val r = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }
            Graph.settings.update { it.copy(repeat = r) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            Graph.settings.update { it.copy(speed = playbackParameters.speed, pitch = playbackParameters.pitch) }
        }
    }

    private fun observeSettings() {
        scope.launch {
            Graph.settings.state.collectLatest { s ->
                player.skipSilenceEnabled = s.skipSilence
                player.setHandleAudioBecomingNoisy(s.pauseOnDisconnect)
            }
        }
    }

    /** Co sekundę: liczenie odtworzeń, wyłącznik czasowy, zapis pozycji. */
    private fun tickLoop() {
        scope.launch {
            var saveCounter = 0
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                if (player.isPlaying) {
                    val dt = (now - lastTick).coerceIn(0, 2000)
                    listenedMs += dt
                    pendingListen += dt
                    if (pendingId == null) pendingId = player.currentMediaItem?.mediaId?.toLongOrNull()
                    if (pendingListen > 60_000) flushListen()
                    val dur = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    val need = minOf(dur * Graph.settings.value.countPlayPercent / 100, 4 * 60_000L)
                    if (!counted && listenedMs >= need) {
                        counted = true
                        player.currentMediaItem?.mediaId?.toLongOrNull()?.let { Graph.userData.recordPlay(it) }
                    }
                    if (++saveCounter >= 10) { saveCounter = 0; saveQueue() }
                }
                lastTick = now
                val at = Graph.sleepAt.value
                if (at != null && now >= at) {
                    Graph.clearSleep()
                    fadeOutAndPause()
                }
            }
        }
    }

    private fun flushListen() {
        val id = pendingId
        if (id != null && pendingListen > 0) Graph.userData.recordListen(id, pendingListen)
        pendingListen = 0
    }

    private fun fadeOutAndPause() {
        scope.launch {
            val start = player.volume
            for (i in 1..20) { player.volume = start * (1 - i / 20f); delay(100) }
            player.pause()
            player.volume = start
        }
    }

    private fun registerHeadset() {
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (!Graph.settings.value.resumeOnHeadset) return
                val headset = addedDevices.any {
                    it.isSink && it.type in setOf(
                        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET,
                    )
                }
                if (headset && player.mediaItemCount > 0 && !player.isPlaying) player.play()
            }
        }
        // Profil korektora wg aktualnego wyjścia (słuchawki / Bluetooth / USB / głośnik).
        val profileCb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = Dsp.switchProfile(currentOutputKind())
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = Dsp.switchProfile(currentOutputKind())
        }
        audioManager.registerAudioDeviceCallback(profileCb, Handler(Looper.getMainLooper()))
        Dsp.switchProfile(currentOutputKind())
        // Przy rejestracji system zgłasza już podłączone urządzenia – ignorujemy pierwsze wywołanie.
        Handler(Looper.getMainLooper()).postDelayed({
            audioManager.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
            headsetCallback = cb
        }, 1500)
    }

    private fun currentOutputKind(): String {
        val outs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        return when {
            outs.any { it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it == AudioDeviceInfo.TYPE_BLE_HEADSET || it == AudioDeviceInfo.TYPE_BLE_SPEAKER } -> "BLUETOOTH"
            outs.any { it == AudioDeviceInfo.TYPE_WIRED_HEADSET || it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it == AudioDeviceInfo.TYPE_USB_HEADSET } -> "HEADPHONES"
            outs.any { it == AudioDeviceInfo.TYPE_USB_DEVICE || it == AudioDeviceInfo.TYPE_USB_ACCESSORY } -> "USB"
            else -> "SPEAKER"
        }
    }

    private fun prefs() = getSharedPreferences("openamp_queue", MODE_PRIVATE)

    private fun saveQueue() {
        if (!Graph.settings.value.rememberQueue) return
        val ids = (0 until player.mediaItemCount).joinToString(",") { player.getMediaItemAt(it).mediaId }
        prefs().edit()
            .putString("ids", ids)
            .putInt("index", player.currentMediaItemIndex)
            .putLong("pos", player.currentPosition.coerceAtLeast(0))
            .apply()
    }

    private suspend fun restoreQueue() {
        if (!Graph.settings.value.rememberQueue || player.mediaItemCount > 0) return
        val ids = prefs().getString("ids", null)?.split(',')?.mapNotNull { it.toLongOrNull() } ?: return
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) { Graph.ensureScanned() }
        if (player.mediaItemCount > 0) return
        val items = ids.mapNotNull { Graph.library.get(it)?.toMediaItem() }
        if (items.isEmpty()) return
        val index = prefs().getInt("index", 0).coerceIn(0, items.size - 1)
        player.setMediaItems(items, index, prefs().getLong("pos", 0))
        player.prepare()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueue()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveQueue()
        flushListen()
        headsetCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        scope.cancel()
        AudioFx.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(albumArtist)
            .setGenre(genre.ifBlank { null })
            .setTrackNumber(trackNumber.takeIf { it > 0 })
            .setReleaseYear(year.takeIf { it > 0 })
            .setDurationMs(durationMs)
            .setArtworkUri(Uri.parse("content://media/external/audio/albumart/$albumId"))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build(),
    )
    .build()

/** Wersja dla kontrolera: identyfikator + metadane (adres uzupełnia serwis). */
fun Track.toRequestItem(): MediaItem = toMediaItem().buildUpon()
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .build()
