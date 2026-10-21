package com.blacksingularities.openamp.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore.Audio.Media as M
import com.blacksingularities.openamp.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Biblioteka z MediaStore: wszystkie utwory muzyczne urządzenia. */
class MediaLibrary(private val context: Context) {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Wszystkie foldery i formaty na urządzeniu (przed filtrem) – do ustawień filtra. */
    private val _allFolders = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val allFolders: StateFlow<List<Pair<String, Int>>> = _allFolders.asStateFlow()
    private val _allFormats = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val allFormats: StateFlow<List<Pair<String, Int>>> = _allFormats.asStateFlow()

    @Volatile
    private var byId: Map<Long, Track> = emptyMap()

    /** Identyfikatory wszystkich plików na urządzeniu z ostatniego udanego skanu (przed filtrem). */
    @Volatile
    var deviceIds: Set<Long>? = null
        private set

    fun get(id: Long): Track? = byId[id]

    suspend fun scan(minDurationSec: Int, excluded: String, hiddenFormats: String = "") = withContext(Dispatchers.IO) {
        _scanning.value = true
        try {
            val everything = query(minDurationSec, emptyList())
            // Krótkie pliki też istnieją – do sprawdzania usuniętych liczy się wszystko, co jest w MediaStore.
            deviceIds = allIds()
            _allFolders.value = everything.groupingBy { it.folder }.eachCount().toList().sortedBy { it.first.lowercase() }
            _allFormats.value = everything.groupingBy { it.format.lowercase() }.eachCount().toList().sortedByDescending { it.second }
            val ex = excluded.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val fm = hiddenFormats.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            val list = everything.filter { t -> ex.none { t.path.contains(it, ignoreCase = true) } && t.format.lowercase() !in fm }
            byId = list.associateBy { it.id }
            _tracks.value = list
        } catch (_: SecurityException) {
            // Brak uprawnień – lista zostaje pusta, interfejs poprosi o dostęp.
        } finally {
            _scanning.value = false
        }
    }

    private fun allIds(): Set<Long> {
        val out = HashSet<Long>()
        context.contentResolver.query(M.EXTERNAL_CONTENT_URI, arrayOf(M._ID), null, null, null)?.use { c ->
            while (c.moveToNext()) out += c.getLong(0)
        }
        return out
    }

    private fun query(minSec: Int, excluded: List<String>): List<Track> {
        val proj = buildList {
            add(M._ID); add(M.TITLE); add(M.ARTIST); add(M.ALBUM); add(M.ALBUM_ID); add("album_artist")
            add(M.COMPOSER); add(M.YEAR); add(M.TRACK); add(M.DURATION); add(M.DATE_ADDED); add(M.SIZE)
            add(M.DATA); add(M.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= 30) { add(M.GENRE); add(M.DISC_NUMBER) }
        }.toTypedArray()
        // Część plików FLAC trafia do MediaStore bez flagi IS_MUSIC lub bez długości (np. duży blok
        // obrazka/ID3 przed nagłówkiem) – bierzemy je po rozszerzeniu/typie, a długość odczyta odtwarzacz.
        val flac = "(${M.DATA} LIKE '%.flac' OR ${M.MIME_TYPE} LIKE '%flac%')"
        val sel = "(${M.IS_MUSIC} != 0 OR $flac) AND (${M.DURATION} >= ? OR ($flac AND (${M.DURATION} IS NULL OR ${M.DURATION} <= 0)))"
        val out = ArrayList<Track>()
        context.contentResolver.query(M.EXTERNAL_CONTENT_URI, proj, sel, arrayOf((minSec * 1000L).toString()), null)?.use { cur ->
            fun idx(n: String) = cur.getColumnIndex(n)
            val iId = idx(M._ID); val iTitle = idx(M.TITLE); val iArtist = idx(M.ARTIST); val iAlbum = idx(M.ALBUM)
            val iAlbumId = idx(M.ALBUM_ID); val iAA = idx("album_artist"); val iComp = idx(M.COMPOSER)
            val iYear = idx(M.YEAR); val iTrack = idx(M.TRACK); val iDur = idx(M.DURATION); val iAdded = idx(M.DATE_ADDED)
            val iSize = idx(M.SIZE); val iData = idx(M.DATA); val iMime = idx(M.MIME_TYPE)
            val iGenre = idx("genre"); val iDisc = idx("disc_number")
            fun s(i: Int) = if (i >= 0) cur.getString(i) else null
            fun unknown(v: String?) = v.isNullOrBlank() || v == "<unknown>"
            while (cur.moveToNext()) {
                val path = s(iData) ?: ""
                if (excluded.any { path.contains(it, ignoreCase = true) }) continue
                val rawTrack = if (iTrack >= 0) cur.getInt(iTrack) else 0
                val artist = s(iArtist).takeUnless { unknown(it) } ?: tr("Nieznany wykonawca")
                out += Track(
                    id = cur.getLong(iId),
                    title = s(iTitle)?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/').substringBeforeLast('.'),
                    artist = artist,
                    album = s(iAlbum).takeUnless { unknown(it) } ?: tr("Nieznany album"),
                    albumId = cur.getLong(iAlbumId),
                    albumArtist = s(iAA).takeUnless { unknown(it) } ?: artist,
                    genre = s(iGenre).takeUnless { unknown(it) } ?: "",
                    composer = s(iComp) ?: "",
                    year = if (iYear >= 0) cur.getInt(iYear) else 0,
                    // Starsze MediaStore koduje płytę w tysiącach numeru ścieżki (2005 = płyta 2, utwór 5).
                    trackNumber = rawTrack % 1000,
                    discNumber = s(iDisc)?.substringBefore('/')?.toIntOrNull() ?: (rawTrack / 1000).coerceAtLeast(1),
                    durationMs = cur.getLong(iDur),
                    dateAdded = cur.getLong(iAdded) * 1000L,
                    size = cur.getLong(iSize),
                    path = path,
                    mime = s(iMime) ?: "",
                )
            }
        }
        return out
    }
}

/** Sortowanie i grupowanie – czyste funkcje używane przez interfejs. */
object LibraryOps {
    private val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }

    fun sortTracks(list: List<Track>, sort: TrackSort, desc: Boolean, stats: Map<Long, PlayStat>, seed: Int = 0): List<Track> {
        val cmp: Comparator<Track> = when (sort) {
            TrackSort.TITLE -> compareBy(collator) { it.title }
            TrackSort.ARTIST -> compareBy<Track, String>(collator) { it.artist }.thenBy(collator) { it.album }.thenBy { it.discNumber }.thenBy { it.trackNumber }
            TrackSort.ALBUM -> compareBy<Track, String>(collator) { it.album }.thenBy { it.discNumber }.thenBy { it.trackNumber }
            TrackSort.YEAR -> compareBy<Track> { it.year }.thenBy(collator) { it.album }.thenBy { it.trackNumber }
            TrackSort.DURATION -> compareBy { it.durationMs }
            TrackSort.DATE_ADDED -> compareByDescending<Track> { it.dateAdded }
            TrackSort.PLAY_COUNT -> compareByDescending<Track> { stats[it.id]?.count ?: 0 }.thenByDescending { stats[it.id]?.lastPlayed ?: 0 }
            TrackSort.LAST_PLAYED -> compareByDescending { stats[it.id]?.lastPlayed ?: 0 }
            TrackSort.SIZE -> compareByDescending { it.size }
            TrackSort.RANDOM -> return list.shuffled(kotlin.random.Random(seed))
        }
        val sorted = list.sortedWith(cmp)
        return if (desc) sorted.reversed() else sorted
    }

    fun albums(list: List<Track>): List<Album> =
        list.groupBy { it.albumId }.map { (id, t) ->
            val sorted = t.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.title }))
            val artists = sorted.map { it.albumArtist }.distinct()
            Album(id, sorted.first().album, if (artists.size == 1) artists[0] else sorted.first().albumArtist, sorted.maxOf { it.year }, sorted)
        }

    fun artists(list: List<Track>): List<TrackGroup> =
        list.groupBy { it.artist.lowercase() }.map { (k, t) -> TrackGroup(k, t.first().artist, albumOrder(t)) }

    fun genres(list: List<Track>): List<TrackGroup> =
        list.groupBy { it.genre.ifBlank { tr("Nieznany gatunek") } }.map { (k, t) -> TrackGroup(k, k, albumOrder(t)) }

    fun folders(list: List<Track>): List<TrackGroup> =
        list.groupBy { it.folder }.map { (k, t) ->
            TrackGroup(k, k.substringAfterLast('/').ifEmpty { "/" }, t.sortedWith(compareBy(collator) { it.path }), subtitle = k)
        }

    private fun albumOrder(t: List<Track>) =
        t.sortedWith(compareBy<Track> { -it.year }.thenBy(collator) { it.album }.thenBy { it.discNumber }.thenBy { it.trackNumber })

    fun <T> sortGroups(
        list: List<T>, sort: GroupSort, desc: Boolean, stats: Map<Long, PlayStat>,
        name: (T) -> String, tracks: (T) -> List<Track>,
    ): List<T> {
        val cmp: Comparator<T> = when (sort) {
            GroupSort.NAME -> compareBy(collator) { name(it) }
            GroupSort.TRACK_COUNT -> compareByDescending { tracks(it).size }
            GroupSort.YEAR -> compareByDescending { tracks(it).maxOfOrNull { t -> t.year } ?: 0 }
            GroupSort.PLAY_COUNT -> compareByDescending { tracks(it).sumOf { t -> stats[t.id]?.count ?: 0 } }
            GroupSort.DATE_ADDED -> compareByDescending { tracks(it).maxOfOrNull { t -> t.dateAdded } ?: 0 }
            GroupSort.RANDOM -> return list.shuffled()
        }
        val sorted = list.sortedWith(cmp)
        return if (desc) sorted.reversed() else sorted
    }

    fun smart(list: List<Track>, kind: SmartList, data: UserData): List<Track> = when (kind) {
        SmartList.FAVORITES -> list.filter { it.id in data.favorites }
        SmartList.MOST_PLAYED -> list.filter { (data.stats[it.id]?.count ?: 0) > 0 }
            .sortedByDescending { data.stats[it.id]?.count ?: 0 }.take(100)
        SmartList.RECENTLY_PLAYED -> list.filter { (data.stats[it.id]?.lastPlayed ?: 0) > 0 }
            .sortedByDescending { data.stats[it.id]?.lastPlayed ?: 0 }.take(100)
        SmartList.RECENTLY_ADDED -> list.sortedByDescending { it.dateAdded }.take(100)
        SmartList.NEVER_PLAYED -> list.filter { data.stats[it.id] == null }
    }

    fun search(list: List<Track>, q: String): List<Track> {
        val words = q.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return list
        return list.filter { t ->
            val hay = "${t.title} ${t.artist} ${t.album} ${t.genre} ${t.albumArtist}".lowercase()
            words.all { hay.contains(it) }
        }
    }
}
