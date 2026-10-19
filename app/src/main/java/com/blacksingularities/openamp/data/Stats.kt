package com.blacksingularities.openamp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PlayStat(val count: Int = 0, val lastPlayed: Long = 0L, val listenMs: Long = 0L)

data class UserData(
    val stats: Map<Long, PlayStat> = emptyMap(),
    val favorites: Set<Long> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    /** Czas słuchania w poszczególnych dniach (numer dnia od 1970 → ms). */
    val daily: Map<Long, Long> = emptyMap(),
)

/**
 * Statystyki odtworzeń, ulubione i playlisty użytkownika – jeden plik JSON w pamięci aplikacji.
 * Zapis jest atomowy (plik tymczasowy + zamiana).
 */
class UserDataStore(context: Context) {
    private val file = File(context.filesDir, "userdata.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserData> = _state.asStateFlow()
    val value: UserData get() = _state.value

    @Synchronized
    private fun update(block: (UserData) -> UserData) {
        _state.value = block(_state.value)
        save(_state.value)
    }

    fun recordPlay(trackId: Long) = update { d ->
        val s = d.stats[trackId] ?: PlayStat()
        d.copy(stats = d.stats + (trackId to s.copy(count = s.count + 1, lastPlayed = System.currentTimeMillis())))
    }

    /** Dopisuje faktycznie przesłuchany czas (do statystyk i wykresu dni). */
    fun recordListen(trackId: Long, ms: Long) {
        if (ms <= 0) return
        update { d ->
            val s = d.stats[trackId] ?: PlayStat()
            val day = Math.floorDiv(System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()), 86_400_000L)
            d.copy(
                stats = d.stats + (trackId to s.copy(listenMs = s.listenMs + ms)),
                daily = d.daily + (day to ((d.daily[day] ?: 0L) + ms)),
            )
        }
    }

    fun clearStats() = update { it.copy(stats = emptyMap(), daily = emptyMap()) }

    fun toggleFavorite(trackId: Long) = update { d ->
        d.copy(favorites = if (trackId in d.favorites) d.favorites - trackId else d.favorites + trackId)
    }

    fun createPlaylist(name: String, ids: List<Long> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        update { it.copy(playlists = it.playlists + Playlist(id, name, ids)) }
        return id
    }

    fun renamePlaylist(id: String, name: String) = update { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun deletePlaylist(id: String) = update { d -> d.copy(playlists = d.playlists.filterNot { it.id == id }) }

    fun addToPlaylist(id: String, ids: List<Long>) = update { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(trackIds = it.trackIds + ids) else it })
    }

    fun removeFromPlaylist(id: String, index: Int) = update { d ->
        d.copy(playlists = d.playlists.map {
            if (it.id == id && index in it.trackIds.indices) it.copy(trackIds = it.trackIds.toMutableList().apply { removeAt(index) }) else it
        })
    }

    fun movePlaylistItem(id: String, from: Int, to: Int) = update { d ->
        d.copy(playlists = d.playlists.map {
            if (it.id != id || from !in it.trackIds.indices || to !in it.trackIds.indices) it
            else it.copy(trackIds = it.trackIds.toMutableList().apply { add(to, removeAt(from)) })
        })
    }

    private fun load(): UserData = runCatching {
        if (!file.exists()) return UserData()
        val o = JSONObject(file.readText())
        val stats = HashMap<Long, PlayStat>()
        o.optJSONObject("stats")?.let { s ->
            for (k in s.keys()) {
                val a = s.getJSONArray(k)
                stats[k.toLong()] = PlayStat(a.getInt(0), a.getLong(1), a.optLong(2, 0L))
            }
        }
        val fav = HashSet<Long>()
        o.optJSONArray("favorites")?.let { a -> for (i in 0 until a.length()) fav += a.getLong(i) }
        val lists = ArrayList<Playlist>()
        o.optJSONArray("playlists")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                val t = p.getJSONArray("tracks")
                lists += Playlist(p.getString("id"), p.getString("name"), List(t.length()) { t.getLong(it) })
            }
        }
        val daily = HashMap<Long, Long>()
        o.optJSONObject("daily")?.let { dd -> for (k in dd.keys()) daily[k.toLong()] = dd.getLong(k) }
        UserData(stats, fav, lists, daily)
    }.getOrDefault(UserData())

    private fun save(d: UserData) {
        val o = JSONObject()
        o.put("stats", JSONObject().apply { d.stats.forEach { (k, v) -> put(k.toString(), JSONArray().put(v.count).put(v.lastPlayed).put(v.listenMs)) } })
        o.put("daily", JSONObject().apply { d.daily.forEach { (k, v) -> put(k.toString(), v) } })
        o.put("favorites", JSONArray(d.favorites.toList()))
        o.put("playlists", JSONArray().apply {
            d.playlists.forEach { p -> put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", JSONArray(p.trackIds))) }
        })
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        }
    }
}
