package com.blacksingularities.openamp.data

import java.util.Calendar
import kotlin.math.ln
import kotlin.random.Random

/** Miks wygenerowany z nawyków słuchania. */
data class Mix(val id: String, val title: String, val subtitle: String, val tracks: List<Track>)

/**
 * „Inteligentne” funkcje biblioteki – wszystko liczone lokalnie z historii odtworzeń:
 * mieszanie bez powtórek wykonawcy, miksy dnia, zapomniane ulubione, odkrywanie,
 * dobór presetu korektora do gatunku.
 */
object Smart {
    private const val DAY = 86_400_000L

    /**
     * Sprytne mieszanie: losowość ważona (rzadziej słuchane i ulubione częściej na początku),
     * a potem rozsuwanie utworów tego samego wykonawcy, żeby nie grały jeden po drugim.
     */
    fun shuffle(tracks: List<Track>, data: UserData, seed: Long = System.nanoTime()): List<Track> {
        if (tracks.size < 3) return tracks.shuffled()
        val rnd = Random(seed)
        val now = System.currentTimeMillis()
        val keyed = tracks.map { t ->
            val st = data.stats[t.id]
            val daysSince = if (st == null || st.lastPlayed == 0L) 60.0 else ((now - st.lastPlayed) / DAY.toDouble()).coerceIn(0.0, 60.0)
            var w = 0.4 + daysSince / 60.0
            if (t.id in data.favorites) w += 0.5
            // Klucz losowania ważonego (Efraimidis–Spirakis): u^(1/w).
            t to -ln(rnd.nextDouble().coerceAtLeast(1e-12)) / w
        }.sortedBy { it.second }.map { it.first }.toMutableList()
        val out = ArrayList<Track>(keyed.size)
        while (keyed.isNotEmpty()) {
            val last = out.lastOrNull()?.artist
            val idx = keyed.indexOfFirst { it.artist != last }.takeIf { it >= 0 && it < 12 } ?: 0
            out += keyed.removeAt(idx)
        }
        return out
    }

    fun mixes(all: List<Track>, data: UserData): List<Mix> {
        if (all.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val out = ArrayList<Mix>()
        val played = all.filter { (data.stats[it.id]?.count ?: 0) > 0 }
        val genreScore = HashMap<String, Int>()
        val artistScore = HashMap<String, Int>()
        played.forEach { t ->
            val c = data.stats[t.id]?.count ?: 0
            if (t.genre.isNotBlank()) genreScore.merge(t.genre, c, Int::plus)
            artistScore.merge(t.artist, c, Int::plus)
        }
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR).toLong()

        // Mix dnia: ulubieni wykonawcy + utwory z ich otoczenia, zmienia się codziennie.
        val topArtists = artistScore.entries.sortedByDescending { it.value }.take(8).map { it.key }.toSet()
        if (topArtists.isNotEmpty()) {
            val pool = all.filter { it.artist in topArtists || (it.genre.isNotBlank() && (genreScore[it.genre] ?: 0) > 0) }
            out += Mix("daily", "Mix dnia", "Twoi wykonawcy i podobne brzmienia", shuffle(pool, data, day).take(50))
        }
        // Na tę porę: utwory ostatnio słuchane o podobnej godzinie.
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val cal = Calendar.getInstance()
        val now2 = played.filter { t ->
            val lp = data.stats[t.id]?.lastPlayed ?: 0
            cal.timeInMillis = lp
            val h = cal.get(Calendar.HOUR_OF_DAY)
            minOf(kotlin.math.abs(h - hour), 24 - kotlin.math.abs(h - hour)) <= 2
        }
        if (now2.size >= 5) out += Mix("time", "Na tę porę", "Czego zwykle słuchasz o tej godzinie", shuffle(now2, data, day + 1).take(40))
        // Zapomniane perełki: często grane kiedyś, dawno niesłuchane.
        val forgotten = all.filter { t ->
            val st = data.stats[t.id] ?: return@filter false
            st.count >= 3 && now - st.lastPlayed > 30 * DAY
        }.sortedByDescending { data.stats[it.id]?.count ?: 0 }
        if (forgotten.isNotEmpty()) out += Mix("forgotten", "Zapomniane perełki", "Kiedyś grały u Ciebie bez przerwy", forgotten.take(40))
        // Odkrywaj: nigdy nie grane, najpierw z ulubionych gatunków.
        val fresh = all.filter { data.stats[it.id] == null }
            .sortedByDescending { (genreScore[it.genre] ?: 0) + (artistScore[it.artist] ?: 0) * 2 }
        if (fresh.isNotEmpty()) out += Mix("discover", "Odkrywaj", "Utwory, których jeszcze nie znasz", fresh.take(60).shuffled(Random(day)).take(40))
        // Ulubione na rotacji
        val favs = all.filter { it.id in data.favorites }
        if (favs.size >= 3) out += Mix("favs", "Ulubione na rotacji", "Twoje serduszka w nowej kolejności", shuffle(favs, data, day + 2))
        return out
    }

    /** Preset korektora dla gatunku (dopasowanie po słowach kluczowych). */
    fun presetForGenre(genre: String): String? {
        val g = genre.lowercase()
        if (g.isBlank()) return null
        val map = listOf(
            listOf("hip", "rap", "trap", "r&b", "rnb") to "Hip-hop",
            listOf("metal", "rock", "punk", "grunge", "alternative") to "Rock",
            listOf("electr", "edm", "house", "techno", "trance", "dance", "dubstep", "drum") to "Elektroniczna",
            listOf("jazz", "blues", "swing", "soul", "funk") to "Jazz",
            listOf("classic", "klasycz", "orchestr", "baroque", "opera", "symphon") to "Klasyczna",
            listOf("acoustic", "akust", "folk", "country", "singer") to "Akustyczna",
            listOf("pop", "disco") to "Pop",
            listOf("podcast", "speech", "audiobook", "spoken", "mowa") to "Podcast",
            listOf("ambient", "chill", "lo-fi", "lofi", "downtempo") to "Ciepłe brzmienie",
            listOf("reggae", "dub", "ska") to "Bas+",
        )
        return map.firstOrNull { (keys, _) -> keys.any { g.contains(it) } }?.second
    }
}
