package com.blacksingularities.openamp.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.blacksingularities.openamp.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Okładki: miniatury z MediaStore (szybkie, buforowane przez system), a w dużym rozmiarze –
 * obraz osadzony w pliku. Pamięć podręczna w RAM; brak okładki zapamiętywany jako pusty wpis.
 */
object ArtworkLoader {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()
    private val gate = Semaphore(4)

    fun peek(track: Track, size: Int): Bitmap? = cache.get(key(track, size))

    private fun key(track: Track, size: Int) = "${track.albumId}:${if (size > 400) "L" else "S"}:${if (size > 400) track.id else 0}"

    suspend fun load(context: Context, track: Track, size: Int): Bitmap? {
        val k = key(track, size)
        cache.get(k)?.let { return it }
        synchronized(missing) { if (k in missing) return null }
        val bmp = gate.withPermit {
            withContext(Dispatchers.IO) {
                if (size > 400) embedded(context, track, size) ?: thumbnail(context, track, size)
                else thumbnail(context, track, size) ?: embedded(context, track, size)
            }
        }
        if (bmp == null) synchronized(missing) { missing += k } else cache.put(k, bmp)
        return bmp
    }

    fun clearMissing() = synchronized(missing) { missing.clear() }

    private fun thumbnail(context: Context, track: Track, size: Int): Bitmap? = runCatching {
        context.contentResolver.loadThumbnail(track.uri, Size(size, size), null)
    }.getOrNull()

    private fun embedded(context: Context, track: Track, size: Int): Bitmap? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, track.uri)
            val bytes = r.embeddedPicture ?: return@runCatching null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= size && o.outHeight / (sample * 2) >= size) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            r.release()
        }
    }.getOrNull()
}

@Composable
fun rememberArtwork(track: Track?, size: Int): Bitmap? {
    val context = LocalContext.current
    val bmp by produceState(track?.let { ArtworkLoader.peek(it, size) }, track?.id, track?.albumId, size) {
        value = track?.let { ArtworkLoader.load(context, it, size) }
    }
    return bmp
}

/** Kolor zastępczy okładki wyliczany z nazwy albumu – każdy album ma inny, ale stały. */
fun placeholderColors(seed: String): Pair<Color, Color> {
    val h = (seed.hashCode() and 0x7FFFFFFF) % 360
    val a = Color(ColorUtils.HSLToColor(floatArrayOf(h.toFloat(), 0.45f, 0.32f)))
    val b = Color(ColorUtils.HSLToColor(floatArrayOf(((h + 40) % 360).toFloat(), 0.55f, 0.18f)))
    return a to b
}

@Composable
fun Artwork(
    track: Track?,
    modifier: Modifier = Modifier,
    size: Int = 256,
    shape: Shape,
    crossfade: Boolean = false,
) {
    val bmp = rememberArtwork(track, size)
    Box(modifier.clip(shape)) {
        val content: @Composable (ImageBitmap?) -> Unit = { img ->
            if (img != null) {
                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                val (a, b) = placeholderColors(track?.album ?: "")
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth(0.4f))
                }
            }
        }
        val image = remember(bmp) { bmp?.asImageBitmap() }
        if (crossfade) Crossfade(image, animationSpec = tween(400), label = "art") { content(it) } else content(image)
    }
}
