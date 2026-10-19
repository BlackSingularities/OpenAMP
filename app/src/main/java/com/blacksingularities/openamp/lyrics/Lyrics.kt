package com.blacksingularities.openamp.lyrics

import android.content.Context
import android.net.Uri
import com.blacksingularities.openamp.BuildConfig
import com.blacksingularities.openamp.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val timeMs: Long, val text: String)

enum class LyricsSource { SIDECAR, TAGS, ONLINE, USER }

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: LyricsSource) {
    val plain: String get() = lines.joinToString("\n") { it.text }

    /** Indeks bieżącej linii dla pozycji odtwarzania (–1 przed pierwszą). */
    fun indexAt(posMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= posMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

object LrcParser {
    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val wordTag = Regex("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>")
    private val offsetTag = Regex("\\[offset:\\s*([+-]?\\d+)\\s*]", RegexOption.IGNORE_CASE)

    fun parse(text: String, source: LyricsSource): Lyrics? {
        val clean = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (clean.isEmpty()) return null
        val offset = offsetTag.find(clean)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timed = ArrayList<LyricLine>()
        for (raw in clean.lines()) {
            val tags = timeTag.findAll(raw).toList()
            if (tags.isEmpty()) continue
            val body = raw.substring(tags.last().range.last + 1).replace(wordTag, "").trim()
            for (t in tags) {
                val (m, s, frac) = t.destructured
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                timed += LyricLine((m.toLong() * 60_000 + s.toLong() * 1000 + ms - offset).coerceAtLeast(0), body)
            }
        }
        if (timed.isNotEmpty()) {
            timed.sortBy { it.timeMs }
            return Lyrics(timed, true, source)
        }
        val lines = clean.lines().filterNot { it.trim().matches(Regex("\\[[a-zA-Z]+:.*]")) }
        if (lines.all { it.isBlank() }) return null
        return Lyrics(lines.map { LyricLine(0, it.trim()) }, false, source)
    }
}

/**
 * Szukanie tekstu: pamięć podręczna → plik .lrc/.txt obok utworu → tagi pliku (ID3 USLT/SYLT,
 * komentarze Vorbis we FLAC, atom ©lyr w MP4) → LRCLIB (opcjonalnie). Wynik z internetu
 * i plik wskazany ręcznie są zapamiętywane.
 */
object LyricsRepository {
    private fun cacheFile(context: Context, id: Long) = File(File(context.filesDir, "lyrics").apply { mkdirs() }, "$id.lrc")
    private fun missFile(context: Context, id: Long) = File(File(context.cacheDir, "lyrics_miss").apply { mkdirs() }, "$id")

    suspend fun load(context: Context, track: Track, online: Boolean, force: Boolean = false): Lyrics? = withContext(Dispatchers.IO) {
        val cache = cacheFile(context, track.id)
        if (cache.exists() && !force) {
            val src = runCatching { LyricsSource.valueOf(cache.readLines().firstOrNull()?.removePrefix("#") ?: "") }.getOrDefault(LyricsSource.USER)
            LrcParser.parse(cache.readText().substringAfter('\n'), src)?.let { return@withContext it }
        }
        sidecar(track)?.let { return@withContext it }
        runCatching { TagLyrics.read(context, track) }.getOrNull()?.let { LrcParser.parse(it, LyricsSource.TAGS) }?.let { return@withContext it }
        if (!online) return@withContext null
        val miss = missFile(context, track.id)
        if (!force && miss.exists() && System.currentTimeMillis() - miss.lastModified() < 7 * 86_400_000L) return@withContext null
        val text = runCatching { lrclib(track) }.getOrNull()
        if (text == null) {
            runCatching { miss.writeText("") }
            return@withContext null
        }
        val parsed = LrcParser.parse(text, LyricsSource.ONLINE) ?: return@withContext null
        runCatching { cache.writeText("#ONLINE\n$text") }
        parsed
    }

    suspend fun importFile(context: Context, track: Track, uri: Uri): Lyrics? = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return@withContext null
        val parsed = LrcParser.parse(text, LyricsSource.USER) ?: return@withContext null
        cacheFile(context, track.id).writeText("#USER\n$text")
        parsed
    }

    fun forget(context: Context, track: Track) {
        cacheFile(context, track.id).delete()
        missFile(context, track.id).delete()
    }

    private fun sidecar(track: Track): Lyrics? {
        if (track.path.isEmpty()) return null
        val base = track.path.substringBeforeLast('.')
        for (ext in listOf(".lrc", ".LRC", ".txt")) {
            val f = File(base + ext)
            val text = runCatching { if (f.canRead()) f.readText() else null }.getOrNull() ?: continue
            LrcParser.parse(text, LyricsSource.SIDECAR)?.let { return it }
        }
        return null
    }

    private fun lrclib(track: Track): String? {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val get = "https://lrclib.net/api/get?artist_name=${enc(track.artist)}&track_name=${enc(track.title)}" +
            "&album_name=${enc(track.album)}&duration=${track.durationMs / 1000}"
        http(get)?.let { body ->
            val o = JSONObject(body)
            pick(o)?.let { return it }
        }
        val search = "https://lrclib.net/api/search?track_name=${enc(track.title)}&artist_name=${enc(track.artist)}"
        val arr = http(search)?.let { JSONArray(it) } ?: return null
        var best: JSONObject? = null
        var bestDiff = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val diff = kotlin.math.abs(o.optDouble("duration", 0.0) - track.durationMs / 1000.0)
            val synced = !o.optString("syncedLyrics").isNullOrBlank() && o.optString("syncedLyrics") != "null"
            val score = diff - if (synced) 3 else 0
            if (diff < 8 && score < bestDiff) { bestDiff = score; best = o }
        }
        return best?.let { pick(it) }
    }

    private fun pick(o: JSONObject): String? {
        val synced = o.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        return synced ?: plain
    }

    private fun http(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("User-Agent", "OpenAMP/${BuildConfig.VERSION_NAME} (https://github.com/${BuildConfig.UPDATE_REPO})")
        }
        return try {
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
