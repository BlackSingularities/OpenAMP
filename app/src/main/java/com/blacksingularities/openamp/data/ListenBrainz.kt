package com.blacksingularities.openamp.data

import com.blacksingularities.openamp.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Scrobbling do ListenBrainz: „teraz gra” przy zmianie utworu i zapis odsłuchu, gdy utwór zostanie zaliczony.
 * Token użytkownika z https://listenbrainz.org/settings/ – zostaje na telefonie.
 */
object ListenBrainz {
    private const val API = "https://api.listenbrainz.org/1"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun playingNow(token: String, t: Track) {
        if (token.isBlank()) return
        scope.launch { runCatching { submit(token, "playing_now", t, null) } }
    }

    fun scrobble(token: String, t: Track, listenedAtSec: Long) {
        if (token.isBlank()) return
        scope.launch { runCatching { submit(token, "single", t, listenedAtSec) } }
    }

    /** Sprawdza token; zwraca nazwę użytkownika albo null, gdy token jest nieprawidłowy. */
    suspend fun validate(token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = request("$API/validate-token", token, null) ?: return@runCatching null
            val o = JSONObject(body)
            if (o.optBoolean("valid")) o.optString("user_name") else null
        }.getOrNull()
    }

    private fun submit(token: String, type: String, t: Track, listenedAt: Long?) {
        val info = JSONObject()
            .put("media_player", "OpenAMP")
            .put("submission_client", "OpenAMP")
            .put("submission_client_version", BuildConfig.VERSION_NAME)
        if (t.durationMs > 0) info.put("duration_ms", t.durationMs)
        if (t.trackNumber > 0) info.put("tracknumber", t.trackNumber)
        val meta = JSONObject()
            .put("artist_name", t.artist.ifBlank { "Unknown" })
            .put("track_name", t.title)
            .put("additional_info", info)
        if (t.album.isNotBlank()) meta.put("release_name", t.album)
        val listen = JSONObject().put("track_metadata", meta)
        if (listenedAt != null) listen.put("listened_at", listenedAt)
        val payload = JSONObject().put("listen_type", type).put("payload", JSONArray().put(listen))
        request("$API/submit-listens", token, payload.toString())
    }

    private fun request(url: String, token: String, json: String?): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("Authorization", "Token $token")
            setRequestProperty("User-Agent", "OpenAMP/${BuildConfig.VERSION_NAME} (https://github.com/${BuildConfig.UPDATE_REPO})")
            if (json != null) {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(json.toByteArray()) }
            }
        }
        return try {
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
