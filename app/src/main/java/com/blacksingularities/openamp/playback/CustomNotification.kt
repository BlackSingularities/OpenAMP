package com.blacksingularities.openamp.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.R
import com.blacksingularities.openamp.data.NotifStyle
import com.blacksingularities.openamp.ui.ArtworkLoader
import com.blacksingularities.openamp.ui.theme.vibrantColor
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.launch

/**
 * Powiadomienie w dwóch stylach: systemowy odtwarzacz (MediaStyle – panel w szybkich ustawieniach)
 * albo własny układ: okładka na całą wysokość po lewej, „wykonawca – tytuł” nad wyśrodkowanymi
 * przyciskami, tło w kolorze okładki. Nagłówek z ikoną aplikacji dokłada system (Android 12+).
 */
class OpenAmpNotificationProvider(private val context: Context) : MediaNotification.Provider {
    private val system = DefaultMediaNotificationProvider.Builder(context)
        .setChannelName(R.string.app_name)
        .build()
        .apply { setSmallIcon(R.drawable.ic_notification) }

    private var artFor: Long? = null
    private var art: Bitmap? = null
    private var artColor: Int? = null

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        if (Graph.settings.value.notifStyle == NotifStyle.SYSTEM) {
            return system.createNotification(mediaSession, mediaButtonPreferences, actionFactory, onNotificationChangedCallback)
        }
        val player = mediaSession.player
        val id = player.currentMediaItem?.mediaId?.toLongOrNull()
        val track = id?.let { Graph.library.get(it) }
        if (id != artFor) {
            artFor = id
            art = track?.let { ArtworkLoader.peek(it, 1024) }
            artColor = art?.let { runCatching { vibrantColor(it) }.getOrNull() }
            if (art == null && track != null) {
                // Okładka wczytuje się w tle – potem podmieniamy powiadomienie.
                Graph.scope.launch {
                    val bmp = ArtworkLoader.load(context, track, 1024) ?: return@launch
                    if (artFor != id) return@launch
                    art = bmp
                    artColor = runCatching { vibrantColor(bmp) }.getOrNull()
                    val n = build(mediaSession, mediaButtonPreferences, actionFactory)
                    onNotificationChangedCallback.onNotificationChanged(n)
                }
            }
        }
        return build(mediaSession, mediaButtonPreferences, actionFactory)
    }

    private fun build(session: MediaSession, buttons: ImmutableList<CommandButton>, factory: MediaNotification.ActionFactory): MediaNotification {
        ensureChannel()
        val player = session.player
        val meta = player.mediaMetadata
        val title = meta.title?.toString().orEmpty()
        val artist = meta.artist?.toString().orEmpty()
        val text = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" – ").ifBlank { context.getString(R.string.app_name) }

        // Tło w kolorze okładki (przyciemnionym, żeby biały tekst był czytelny); bez okładki – kolory systemu.
        val bg = artColor?.let { c -> FloatArray(3).also { ColorUtils.colorToHSL(c, it) }.let { hsl -> hsl[2] = 0.24f; hsl[1] = (hsl[1] * 0.8f).coerceAtMost(0.7f); ColorUtils.HSLToColor(hsl) } }
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val fg = if (bg != null || night) Color.WHITE else Color.parseColor("#DE000000")

        fun views(layout: Int, big: Boolean): RemoteViews = RemoteViews(context.packageName, layout).apply {
            setTextViewText(R.id.n_text, text)
            setTextColor(R.id.n_text, fg)
            val bmp = art
            if (bmp != null) setImageViewBitmap(R.id.n_cover, bmp) else { setImageViewResource(R.id.n_cover, R.drawable.ic_w_note); setInt(R.id.n_cover, "setColorFilter", fg) }
            fun bind(view: Int, icon: Int, intent: android.app.PendingIntent?) {
                setImageViewResource(view, icon)
                setInt(view, "setColorFilter", fg)
                if (intent != null) setOnClickPendingIntent(view, intent)
            }
            bind(R.id.n_prev, R.drawable.ic_w_prev, factory.createMediaActionPendingIntent(session, Player.COMMAND_SEEK_TO_PREVIOUS.toLong()))
            bind(R.id.n_play, if (player.playWhenReady) R.drawable.ic_w_pause else R.drawable.ic_w_play, factory.createMediaActionPendingIntent(session, Player.COMMAND_PLAY_PAUSE.toLong()))
            bind(R.id.n_next, R.drawable.ic_w_next, factory.createMediaActionPendingIntent(session, Player.COMMAND_SEEK_TO_NEXT.toLong()))
            if (big) {
                val custom = buttons.filter { it.sessionCommand != null }
                listOf(R.id.n_b1, R.id.n_b2).forEachIndexed { i, view ->
                    val b = custom.getOrNull(i)
                    if (b == null) setViewVisibility(view, android.view.View.INVISIBLE)
                    else {
                        setViewVisibility(view, android.view.View.VISIBLE)
                        val action = factory.createCustomActionFromCustomCommandButton(session, b)
                        bind(view, iconFor(b.sessionCommand?.customAction, player), action.actionIntent)
                    }
                }
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setCustomContentView(views(R.layout.notif_player_small, false))
            .setCustomBigContentView(views(R.layout.notif_player_big, true))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(session.sessionActivity)
            .setDeleteIntent(factory.createMediaActionPendingIntent(session, Player.COMMAND_STOP.toLong()))
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(player.playWhenReady)
        if (bg != null) builder.setColor(bg).setColorized(true)
        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    private fun iconFor(action: String?, player: Player): Int = when (action) {
        SessionCommands.LIKE -> {
            val fav = player.currentMediaItem?.mediaId?.toLongOrNull()?.let { it in Graph.userData.value.favorites } == true
            if (fav) R.drawable.ic_w_heart else R.drawable.ic_w_heart_outline
        }
        SessionCommands.SHUFFLE -> R.drawable.ic_w_shuffle
        SessionCommands.REPEAT -> if (player.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_w_repeat_one else R.drawable.ic_w_repeat
        SessionCommands.CLOSE -> R.drawable.ic_w_close
        else -> R.drawable.ic_w_note
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        system.handleCustomCommand(session, action, extras)

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val CHANNEL = "openamp_player"
        /** Ten sam numer co w domyślnym dostawcy – zmiana stylu podmienia powiadomienie. */
        const val NOTIFICATION_ID = 1001
    }
}
