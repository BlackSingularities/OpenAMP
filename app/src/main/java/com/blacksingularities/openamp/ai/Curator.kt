package com.blacksingularities.openamp.ai

import android.content.Context
import com.blacksingularities.openamp.data.Smart
import com.blacksingularities.openamp.data.Track
import com.blacksingularities.openamp.data.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Kategoria utworów ułożona przez analizę (AI albo lokalną). */
data class AiCategory(val id: String, val name: String, val description: String, val trackIds: List<Long>)

/** Składanka utworów w kolejności grania. */
data class AiMix(val id: String, val title: String, val subtitle: String, val trackIds: List<Long>)

enum class CurationSource { NONE, CLAUDE, LOCAL }

data class Curation(
    val categories: List<AiCategory> = emptyList(),
    val mixes: List<AiMix> = emptyList(),
    val analyzedAt: Long = 0L,
    /** Biblioteka w chwili analizy – do liczenia, o ile się zmieniła. */
    val analyzedIds: Set<Long> = emptySet(),
    val source: CurationSource = CurationSource.NONE,
    /** Liczba zmian, przy której użytkownik odłożył propozycję analizy. */
    val dismissedAt: Int = 0,
) {
    fun knownIds(): Set<Long> = (categories.flatMap { it.trackIds } + mixes.flatMap { it.trackIds }).toSet()
}

sealed interface CuratorStatus {
    data object Idle : CuratorStatus
    data class Running(val stage: String) : CuratorStatus
    data class Failed(val message: String) : CuratorStatus
}

/**
 * Wyniki analizy biblioteki (kategorie i miksy) – jeden plik JSON, plus klucz API w osobnych
 * prywatnych ustawieniach. Analiza przez Claude, gdy jest klucz; bez klucza – lokalna, na telefonie.
 */
class CuratorStore(context: Context) {
    private val file = File(context.filesDir, "curation.json")
    private val prefs = context.getSharedPreferences("openamp_ai", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Curation> = _state.asStateFlow()
    val value: Curation get() = _state.value
    private val _status = MutableStateFlow<CuratorStatus>(CuratorStatus.Idle)
    val status: StateFlow<CuratorStatus> = _status.asStateFlow()
    private val _apiKey = MutableStateFlow(prefs.getString("key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun setApiKey(key: String) {
        _apiKey.value = key.trim()
        prefs.edit().putString("key", key.trim()).apply()
    }

    /** O ile utworów (dodanych + usuniętych) biblioteka różni się od stanu z ostatniej analizy. */
    fun changeCount(tracks: List<Track>): Int {
        val now = tracks.mapTo(HashSet()) { it.id }
        val before = value.analyzedIds
        return now.count { it !in before } + before.count { it !in now }
    }

    fun shouldPropose(tracks: List<Track>): Boolean {
        if (tracks.size < 5 || _status.value is CuratorStatus.Running) return false
        val c = changeCount(tracks)
        return c >= 5 && c >= value.dismissedAt + 5
    }

    fun dismiss(tracks: List<Track>) = update { it.copy(dismissedAt = changeCount(tracks)) }

    fun clear() = update { Curation(analyzedIds = emptySet()) }

    @Synchronized
    private fun update(block: (Curation) -> Curation) {
        _state.value = block(_state.value)
        save(_state.value)
    }

    fun pruneMissing(existing: Set<Long>) {
        val c = value
        if (c.knownIds().all { it in existing }) return
        update { u ->
            u.copy(
                categories = u.categories.map { it.copy(trackIds = it.trackIds.filter { id -> id in existing }) }.filter { it.trackIds.isNotEmpty() },
                mixes = u.mixes.map { it.copy(trackIds = it.trackIds.filter { id -> id in existing }) }.filter { it.trackIds.isNotEmpty() },
            )
        }
    }

    /** Pełna analiza: kategorie i miksy. Zwraca komunikat błędu albo null. */
    suspend fun analyze(tracks: List<Track>, data: UserData, model: String, english: Boolean): String? {
        if (_status.value is CuratorStatus.Running) return null
        val key = apiKey.value
        val pool = tracks.filter { it.id !in data.disliked }.ifEmpty { tracks }
        return try {
            val result = withContext(Dispatchers.IO) {
                if (key.isNotBlank()) {
                    _status.value = CuratorStatus.Running(if (english) "Claude is listening to your library…" else "Claude przegląda bibliotekę…")
                    ClaudeCurator.run(key, model, pool, data, english)
                } else {
                    _status.value = CuratorStatus.Running(if (english) "Analysing on the phone…" else "Analiza na telefonie…")
                    LocalCurator.run(pool, data, english)
                }
            }
            update {
                result.copy(
                    analyzedAt = System.currentTimeMillis(),
                    analyzedIds = tracks.mapTo(HashSet()) { it.id },
                    dismissedAt = 0,
                )
            }
            _status.value = CuratorStatus.Idle
            null
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            _status.value = CuratorStatus.Failed(msg)
            msg
        }
    }

    private fun load(): Curation = runCatching {
        if (!file.exists()) return Curation()
        val o = JSONObject(file.readText())
        fun ids(a: JSONArray?) = if (a == null) emptyList() else List(a.length()) { a.getLong(it) }
        val cats = o.optJSONArray("categories")?.let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { c -> AiCategory(c.getString("id"), c.getString("name"), c.optString("description"), ids(c.optJSONArray("tracks"))) } }
        } ?: emptyList()
        val mixes = o.optJSONArray("mixes")?.let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { c -> AiMix(c.getString("id"), c.getString("title"), c.optString("subtitle"), ids(c.optJSONArray("tracks"))) } }
        } ?: emptyList()
        Curation(
            categories = cats,
            mixes = mixes,
            analyzedAt = o.optLong("analyzedAt"),
            analyzedIds = ids(o.optJSONArray("analyzedIds")).toSet(),
            source = runCatching { CurationSource.valueOf(o.optString("source")) }.getOrDefault(CurationSource.NONE),
            dismissedAt = o.optInt("dismissedAt"),
        )
    }.getOrDefault(Curation())

    private fun save(c: Curation) {
        val o = JSONObject()
        o.put("categories", JSONArray().apply {
            c.categories.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("description", it.description).put("tracks", JSONArray(it.trackIds))) }
        })
        o.put("mixes", JSONArray().apply {
            c.mixes.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("subtitle", it.subtitle).put("tracks", JSONArray(it.trackIds))) }
        })
        o.put("analyzedAt", c.analyzedAt)
        o.put("analyzedIds", JSONArray(c.analyzedIds.toList()))
        o.put("source", c.source.name)
        o.put("dismissedAt", c.dismissedAt)
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        }
    }
}

/** Analiza bez sieci: rodziny gatunków, dekady, wersje (remiksy, koncerty…), długość i nawyki słuchania. */
object LocalCurator {
    private val families = listOf(
        "hiphop" to listOf("hip", "rap", "trap", "r&b", "rnb", "drill", "grime"),
        "rock" to listOf("rock", "metal", "punk", "grunge", "alternative", "indie", "hardcore"),
        "electro" to listOf("electr", "edm", "house", "techno", "trance", "dance", "dubstep", "drum", "dnb", "synth"),
        "jazz" to listOf("jazz", "blues", "swing", "soul", "funk", "gospel"),
        "classical" to listOf("classic", "klasycz", "orchestr", "baroque", "opera", "symphon", "piano"),
        "acoustic" to listOf("acoustic", "akust", "folk", "country", "singer"),
        "pop" to listOf("pop", "disco", "k-pop"),
        "chill" to listOf("ambient", "chill", "lo-fi", "lofi", "downtempo", "relax", "new age"),
        "reggae" to listOf("reggae", "dub", "ska"),
        "latin" to listOf("latin", "salsa", "reggaeton", "bossa", "samba"),
    )

    private fun familyName(key: String, en: Boolean) = when (key) {
        "hiphop" -> if (en) "Hip-hop & rap" else "Hip-hop i rap"
        "rock" -> if (en) "Rock & guitars" else "Rock i gitary"
        "electro" -> if (en) "Electronic" else "Elektronika"
        "jazz" -> if (en) "Jazz, blues & soul" else "Jazz, blues i soul"
        "classical" -> if (en) "Classical" else "Klasyka"
        "acoustic" -> if (en) "Acoustic & folk" else "Akustycznie i folk"
        "pop" -> "Pop"
        "chill" -> if (en) "Chill & ambient" else "Chill i ambient"
        "reggae" -> if (en) "Reggae & ska" else "Reggae i ska"
        else -> if (en) "Latin" else "Latino"
    }

    fun familyOf(t: Track): String? {
        val g = t.genre.lowercase()
        if (g.isBlank()) return null
        return families.firstOrNull { (_, keys) -> keys.any { g.contains(it) } }?.first
    }

    fun run(tracks: List<Track>, data: UserData, en: Boolean): Curation {
        val cats = ArrayList<AiCategory>()
        val mixes = ArrayList<AiMix>()
        fun cat(id: String, name: String, desc: String, list: List<Track>, min: Int = 5) {
            if (list.size >= min) cats += AiCategory(id, name, desc, list.map { it.id })
        }
        val plays = { t: Track -> data.stats[t.id]?.count ?: 0 }

        // Rodziny gatunków, a z pozostałych gatunków – te z co najmniej 8 utworami.
        val byFamily = tracks.groupBy { familyOf(it) }
        families.forEach { (key, _) ->
            val list = byFamily[key].orEmpty()
            cat("fam_$key", familyName(key, en), if (en) "By genre tags" else "Według tagów gatunku", list)
        }
        byFamily[null].orEmpty().filter { it.genre.isNotBlank() }.groupBy { it.genre.trim() }
            .filter { it.value.size >= 8 }
            .forEach { (g, list) -> cat("genre_${g.lowercase()}", g, if (en) "Genre" else "Gatunek", list) }

        // Dekady
        tracks.filter { it.year in 1900..2100 }.groupBy { it.year / 10 * 10 }.toSortedMap().forEach { (dec, list) ->
            val name = if (en) "${dec}s" else "Lata ${dec % 100}.".let { if (dec >= 2000) "Lata $dec" else it }
            cat("decade_$dec", name, if (en) "Released in the ${dec}s" else "Wydane w latach $dec–${dec + 9}", list)
        }

        // Wersje z tytułu
        val kinds = listOf(
            Triple("remix", listOf("remix", "rmx", "rework", "edit)", "bootleg", "mashup"), if (en) "Remixes" else "Remiksy"),
            Triple("live", listOf("live", "na żywo", "concert", "koncert"), if (en) "Live" else "Na żywo"),
            Triple("acoustic_v", listOf("acoustic", "unplugged", "akustycz"), if (en) "Acoustic versions" else "Wersje akustyczne"),
            Triple("instr", listOf("instrumental", "instr.", "karaoke"), if (en) "Instrumentals" else "Instrumentalne"),
            Triple("cover", listOf("cover"), if (en) "Covers" else "Covery"),
        )
        kinds.forEach { (id, keys, name) ->
            val list = tracks.filter { t -> val h = (t.title + " " + t.album).lowercase(); keys.any { h.contains(it) } }
            cat("kind_$id", name, if (en) "Found in titles" else "Rozpoznane po tytułach", list, min = 3)
        }

        // Długość
        cat("long", if (en) "Long journeys" else "Długie podróże", if (en) "Over 7 minutes" else "Ponad 7 minut", tracks.filter { it.durationMs > 7 * 60_000 }, 3)
        cat("short", if (en) "Quick hits" else "Krótkie strzały", if (en) "Under 2:30" else "Poniżej 2:30", tracks.filter { it.durationMs in 1..150_000 }, 5)

        // Nawyki
        val topArtists = tracks.groupBy { it.artist }.mapValues { (_, l) -> l.sumOf(plays) }.filter { it.value > 0 }
            .entries.sortedByDescending { it.value }.take(10).map { it.key }.toSet()
        cat("top_artists", if (en) "Your artists" else "Twoi wykonawcy", if (en) "The ones you play most" else "Najczęściej słuchani", tracks.filter { it.artist in topArtists })
        cat("hidden", if (en) "Never played" else "Jeszcze nie grane", if (en) "Waiting for their turn" else "Czekają na swoją kolej", tracks.filter { data.stats[it.id] == null }, 10)

        // Miksy: z rodzin i dekad, które słuchasz najczęściej – kolejność bez powtórek wykonawcy.
        val seed = System.currentTimeMillis() / 86_400_000L
        cats.filter { it.id.startsWith("fam_") || it.id.startsWith("genre_") }
            .sortedByDescending { c -> c.trackIds.sumOf { data.stats[it]?.count ?: 0 } + c.trackIds.size / 10 }
            .take(4).forEachIndexed { i, c ->
                val list = tracks.filter { it.id in c.trackIds.toSet() }
                mixes += AiMix("mix_${c.id}", if (en) "${c.name} mix" else "Mix: ${c.name}", c.description, Smart.shuffle(list, data, seed + i).take(40).map { it.id })
            }
        cats.filter { it.id.startsWith("decade_") }.sortedByDescending { it.trackIds.size }.take(2).forEachIndexed { i, c ->
            val list = tracks.filter { it.id in c.trackIds.toSet() }
            mixes += AiMix("mix_${c.id}", if (en) "Back to the ${c.name}" else "Powrót: ${c.name}", c.description, Smart.shuffle(list, data, seed + 10 + i).take(40).map { it.id })
        }
        val fresh = tracks.filter { data.stats[it.id] == null }
        if (fresh.size >= 10) mixes += AiMix("mix_fresh", if (en) "Fresh picks" else "Świeże odkrycia", if (en) "Tracks you haven't played yet" else "Utwory jeszcze niegrane", Smart.shuffle(fresh, data, seed + 20).take(40).map { it.id })
        return Curation(categories = cats, mixes = mixes, source = CurationSource.LOCAL)
    }
}
