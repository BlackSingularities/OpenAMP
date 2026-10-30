package com.blacksingularities.openamp.wear

/**
 * Wspólny język telefonu i zegarka (ten sam plik kompilują moduły `:app` i `:wear`).
 *
 * Telefon publikuje migawkę odtwarzania jako element danych (Data Layer) pod [PATH_STATE];
 * zegarek odpowiada krótkimi wiadomościami pod [PATH_CMD] w postaci `akcja` albo `akcja:argument`.
 * Pozycję w utworze zegarek dolicza sam z [K_POSITION] i [K_UPDATED_AT], więc telefon nie musi
 * wysyłać stanu co sekundę.
 */
object WearProtocol {
    const val PATH_STATE = "/openamp/state"
    const val PATH_CMD = "/openamp/cmd"

    // ---------- Migawka odtwarzania ----------
    const val K_TRACK_ID = "trackId"
    const val K_TITLE = "title"
    const val K_ARTIST = "artist"
    const val K_ALBUM = "album"
    const val K_PLAYING = "playing"
    const val K_POSITION = "position"
    /** Czas (System.currentTimeMillis) odczytu pozycji – zegarek dolicza od niego resztę. */
    const val K_UPDATED_AT = "updatedAt"
    const val K_DURATION = "duration"
    const val K_SHUFFLE = "shuffle"
    /** OFF / ALL / ONE. */
    const val K_REPEAT = "repeat"
    const val K_FAVORITE = "favorite"
    const val K_INDEX = "index"
    const val K_COUNT = "count"
    /** Kolor akcentu wzięty z okładki (ARGB) albo 0. */
    const val K_ACCENT = "accent"
    const val K_VOLUME = "volume"
    const val K_VOLUME_MAX = "volumeMax"
    /** Kolejka od bieżącego utworu w przód: identyfikatory, tytuły i wykonawcy. */
    const val K_QUEUE_IDS = "queueIds"
    const val K_QUEUE_TITLES = "queueTitles"
    const val K_QUEUE_ARTISTS = "queueArtists"
    /** Okładka bieżącego utworu (JPEG). */
    const val ASSET_ART = "art"

    // ---------- Polecenia z zegarka ----------
    const val CMD_TOGGLE = "toggle"
    const val CMD_PLAY = "play"
    const val CMD_PAUSE = "pause"
    const val CMD_NEXT = "next"
    const val CMD_PREV = "prev"
    /** `seek:<ms>`. */
    const val CMD_SEEK = "seek"
    const val CMD_FAVORITE = "fav"
    const val CMD_SHUFFLE = "shuffle"
    const val CMD_REPEAT = "repeat"
    /** `vol:<-1|1>`. */
    const val CMD_VOLUME = "vol"
    /** `skip:<pozycja w kolejce>`. */
    const val CMD_SKIP = "skip"
    /** Zegarek prosi o świeżą migawkę (po uruchomieniu aplikacji albo kafelka). */
    const val CMD_REFRESH = "refresh"

    fun message(action: String, arg: Any? = null): ByteArray =
        (if (arg == null) action else "$action:$arg").toByteArray(Charsets.UTF_8)

    /** Rozkłada wiadomość na akcję i argument. */
    fun parse(bytes: ByteArray): Pair<String, String?> {
        val raw = String(bytes, Charsets.UTF_8)
        val i = raw.indexOf(':')
        return if (i < 0) raw to null else raw.substring(0, i) to raw.substring(i + 1)
    }
}
