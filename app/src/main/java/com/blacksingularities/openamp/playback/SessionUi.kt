package com.blacksingularities.openamp.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.blacksingularities.openamp.Graph
import com.blacksingularities.openamp.R
import com.blacksingularities.openamp.data.NotifButton
import com.blacksingularities.openamp.i18n.tr
import com.blacksingularities.openamp.ui.ArtworkLoader
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.guava.future

/** Adres okładki utworu w metadanych sesji – rozwiązywany przez [ArtBitmapLoader]. */
fun artworkUriFor(trackId: Long): Uri = Uri.parse("${ArtBitmapLoader.SCHEME}://track/$trackId")

/**
 * Okładki dla powiadomienia, ekranu blokady i kontrolerów systemowych: zawsze obraz osadzony
 * w pliku w dużej rozdzielczości (albo miniatura MediaStore), a nie przestarzały „albumart”.
 */
class ArtBitmapLoader(private val context: Context) : BitmapLoader {
    private val fallback = DataSourceBitmapLoader(context)

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri.scheme != SCHEME) return fallback.loadBitmap(uri)
        val id = uri.lastPathSegment?.toLongOrNull()
        return Graph.scope.future {
            val track = id?.let { Graph.library.get(it) } ?: error("Brak utworu $id")
            ArtworkLoader.load(context, track, 1024) ?: error("Brak okładki")
        }
    }

    companion object {
        const val SCHEME = "openamp-art"
    }
}

/** Polecenia przycisków powiadomienia. */
object SessionCommands {
    const val LIKE = "openamp.LIKE"
    const val SHUFFLE = "openamp.SHUFFLE"
    const val REPEAT = "openamp.REPEAT"
    const val CLOSE = "openamp.CLOSE"
    val all = listOf(LIKE, SHUFFLE, REPEAT, CLOSE).map { SessionCommand(it, Bundle.EMPTY) }

    /** Przyciski wybrane w ustawieniach, z ikonami odpowiadającymi bieżącemu stanowi. */
    fun buttons(player: Player): List<CommandButton> {
        val s = Graph.settings.value
        val first = button(s.notifButton1, player, CommandButton.SLOT_BACK_SECONDARY)
        val second = button(s.notifButton2, player, CommandButton.SLOT_FORWARD_SECONDARY)
        return listOfNotNull(first, second).distinctBy { it.sessionCommand?.customAction }
    }

    private fun button(kind: NotifButton, player: Player, slot: Int): CommandButton? {
        val (icon, custom, name, action) = when (kind) {
            NotifButton.NONE -> return null
            NotifButton.LIKE -> {
                val fav = player.currentMediaItem?.mediaId?.toLongOrNull()?.let { it in Graph.userData.value.favorites } == true
                Quad(
                    if (fav) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED, 0,
                    if (fav) tr("Usuń z ulubionych") else tr("Dodaj do ulubionych"), LIKE,
                )
            }
            NotifButton.SHUFFLE -> Quad(
                if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF, 0, tr("Losowo"), SHUFFLE,
            )
            NotifButton.REPEAT -> Quad(
                when (player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                    Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                    else -> CommandButton.ICON_REPEAT_OFF
                }, 0, tr("Powtarzanie"), REPEAT,
            )
            NotifButton.CLOSE -> Quad(CommandButton.ICON_UNDEFINED, R.drawable.ic_w_close, tr("Zamknij odtwarzacz"), CLOSE)
        }
        val b = CommandButton.Builder(icon)
            .setDisplayName(name)
            .setSessionCommand(SessionCommand(action, Bundle.EMPTY))
            .setSlots(slot, CommandButton.SLOT_OVERFLOW)
        if (custom != 0) b.setCustomIconResId(custom)
        return b.build()
    }

    /** Stały przycisk X w powiadomieniu – zamyka cały odtwarzacz. */
    fun closeButton(): CommandButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
        .setDisplayName(tr("Zamknij odtwarzacz"))
        .setSessionCommand(SessionCommand(CLOSE, Bundle.EMPTY))
        .setCustomIconResId(R.drawable.ic_w_close)
        .build()

    private data class Quad(val icon: Int, val custom: Int, val name: String, val action: String)
}
