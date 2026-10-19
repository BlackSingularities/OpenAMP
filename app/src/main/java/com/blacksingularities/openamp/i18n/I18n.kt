package com.blacksingularities.openamp.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class AppLanguage { SYSTEM, PL, EN }

/**
 * Tłumaczenia: teksty w kodzie są po polsku i przechodzą przez [tr]. Dla języka angielskiego
 * słownik [En] podaje odpowiedniki. [I18n.english] jest stanem Compose, więc zmiana języka
 * w ustawieniach od razu przerysowuje cały interfejs.
 */
object I18n {
    var english by mutableStateOf(false)
        private set

    fun apply(language: AppLanguage) {
        english = when (language) {
            AppLanguage.PL -> false
            AppLanguage.EN -> true
            AppLanguage.SYSTEM -> Locale.getDefault().language != "pl"
        }
    }
}

fun tr(pl: String, vararg args: Any?): String {
    var s = if (I18n.english) En[pl] ?: pl else pl
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}

/** Liczebniki: 1 utwór, 2–4 utwory, 5+ utworów (po angielsku: track / tracks). */
fun plural(n: Int, one: String, few: String, many: String): String {
    val form = when {
        n == 1 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }
    return tr(form, n)
}

private val En: Map<String, String> = mapOf(
    // Ogólne
    "Biblioteka" to "Library",
    "Ustawienia" to "Settings",
    "Korektor" to "Equalizer",
    "Teraz odtwarzane" to "Now playing",
    "Kolejka" to "Queue",
    "Szukaj" to "Search",
    "Szukaj w bibliotece" to "Search library",
    "Anuluj" to "Cancel",
    "Zapisz" to "Save",
    "Usuń" to "Delete",
    "Zamknij" to "Close",
    "Gotowe" to "Done",
    "Wstecz" to "Back",
    "Więcej" to "More",
    "Włączone" to "On",
    "Wyłączone" to "Off",
    "Nieznany wykonawca" to "Unknown artist",
    "Nieznany album" to "Unknown album",
    "Nieznany gatunek" to "Unknown genre",
    "Brak utworów" to "No tracks",
    "Nic nie jest odtwarzane" to "Nothing is playing",
    "Wybierz utwór z biblioteki" to "Pick a track from the library",

    // Uprawnienia
    "Dostęp do muzyki" to "Access to music",
    "OpenAMP potrzebuje dostępu do plików audio na urządzeniu, aby zbudować bibliotekę." to
        "OpenAMP needs access to audio files on this device to build your library.",
    "Zezwól" to "Allow",
    "Otwórz ustawienia aplikacji" to "Open app settings",

    // Zakładki biblioteki
    "Utwory" to "Tracks",
    "Albumy" to "Albums",
    "Wykonawcy" to "Artists",
    "Gatunki" to "Genres",
    "Foldery" to "Folders",
    "Playlisty" to "Playlists",

    // Sortowanie i widok
    "Sortuj" to "Sort",
    "Sortowanie" to "Sorting",
    "Tytuł" to "Title",
    "Wykonawca" to "Artist",
    "Album" to "Album",
    "Rok" to "Year",
    "Czas trwania" to "Duration",
    "Data dodania" to "Date added",
    "Najczęściej odtwarzane" to "Most played",
    "Ostatnio odtwarzane" to "Recently played",
    "Ostatnio dodane" to "Recently added",
    "Numer utworu" to "Track number",
    "Rozmiar pliku" to "File size",
    "Nazwa" to "Name",
    "Liczba utworów" to "Track count",
    "Losowo" to "Random",
    "Malejąco" to "Descending",
    "Rosnąco" to "Ascending",
    "Widok" to "View",
    "Lista" to "List",
    "Siatka" to "Grid",
    "Siatka duża" to "Large grid",
    "Kompaktowa lista" to "Compact list",

    // Akcje
    "Odtwórz" to "Play",
    "Odtwórz wszystko" to "Play all",
    "Wymieszaj" to "Shuffle",
    "Wymieszaj wszystko" to "Shuffle all",
    "Odtwórz następny" to "Play next",
    "Dodaj do kolejki" to "Add to queue",
    "Dodaj do playlisty" to "Add to playlist",
    "Ulubione" to "Favorites",
    "Dodaj do ulubionych" to "Add to favorites",
    "Usuń z ulubionych" to "Remove from favorites",
    "Przejdź do albumu" to "Go to album",
    "Przejdź do wykonawcy" to "Go to artist",
    "Szczegóły" to "Details",
    "Udostępnij" to "Share",
    "Usuń z kolejki" to "Remove from queue",
    "Usuń z playlisty" to "Remove from playlist",
    "Wyczyść kolejkę" to "Clear queue",
    "Nowa playlista" to "New playlist",
    "Nazwa playlisty" to "Playlist name",
    "Utwórz" to "Create",
    "Zmień nazwę" to "Rename",
    "Usuń playlistę" to "Delete playlist",
    "Dodano do kolejki" to "Added to queue",
    "Dodano do playlisty {0}" to "Added to playlist {0}",
    "Zagra jako następny" to "Plays next",
    "Odśwież bibliotekę" to "Rescan library",
    "Skanowanie biblioteki…" to "Scanning library…",
    "{0} utwór" to "{0} track",
    "{0} utwory" to "{0} tracks",
    "{0} utworów" to "{0} tracks",
    "{0} album" to "{0} album",
    "{0} albumy" to "{0} albums",
    "{0} albumów" to "{0} albums",
    "{0} odtworzenie" to "{0} play",
    "{0} odtworzenia" to "{0} plays",
    "{0} odtworzeń" to "{0} plays",
    "Inteligentne playlisty" to "Smart playlists",
    "Twoje playlisty" to "Your playlists",
    "Nigdy nieodtwarzane" to "Never played",
    "Brak wyników" to "No results",

    // Odtwarzacz
    "Tekst" to "Lyrics",
    "Tekst utworu" to "Lyrics",
    "Brak tekstu dla tego utworu" to "No lyrics for this track",
    "Szukam tekstu…" to "Looking for lyrics…",
    "Szukaj tekstu w internecie" to "Search lyrics online",
    "Wczytaj plik .lrc" to "Load .lrc file",
    "Tekst zsynchronizowany" to "Synced lyrics",
    "Tekst statyczny" to "Plain lyrics",
    "Źródło: {0}" to "Source: {0}",
    "plik obok utworu" to "file next to the track",
    "tagi pliku" to "file tags",
    "wczytany plik" to "loaded file",
    "Przesunięcie tekstu" to "Lyrics offset",
    "Powtarzanie" to "Repeat",
    "Losowa kolejność" to "Shuffle",
    "Szybkość" to "Speed",
    "Wysokość dźwięku" to "Pitch",
    "Szybkość i wysokość" to "Speed & pitch",
    "Wyłącznik czasowy" to "Sleep timer",
    "Muzyka zatrzyma się za {0}" to "Music stops in {0}",
    "Do końca utworu" to "End of track",
    "Wyłącz wyłącznik" to "Cancel timer",
    "{0} min" to "{0} min",
    "Informacje o pliku" to "File info",
    "Format" to "Format",
    "Bitrate" to "Bitrate",
    "Częstotliwość próbkowania" to "Sample rate",
    "Kanały" to "Channels",
    "Ścieżka" to "Path",
    "Rozmiar" to "Size",
    "Gatunek" to "Genre",
    "Kompozytor" to "Composer",
    "Odtworzenia" to "Plays",
    "Ostatnio" to "Last played",
    "nigdy" to "never",
    "Mono" to "Mono",
    "Stereo" to "Stereo",
    "Następny" to "Up next",
    "Poprzedni" to "Previous",
    "Pauza" to "Pause",
    "Resetuj" to "Reset",

    // Korektor
    "Korektor graficzny" to "Graphic equalizer",
    "Włącz efekty" to "Enable effects",
    "Preset" to "Preset",
    "Własny" to "Custom",
    "Płaski" to "Flat",
    "Wzmocnienie basów" to "Bass boost",
    "Przestrzenność" to "Virtualizer",
    "Głośność (wzmocnienie)" to "Loudness boost",
    "Wzmocnienie wstępne" to "Preamp",
    "Ten telefon nie udostępnia korektora systemowego." to "This device does not provide a system equalizer.",
    "Zapisz preset" to "Save preset",
    "Nazwa presetu" to "Preset name",
    "Twoje presety" to "Your presets",
    "Presety urządzenia" to "Device presets",
    "Efekty działają po rozpoczęciu odtwarzania." to "Effects apply once playback starts.",
    "Balans" to "Balance",
    "Rock" to "Rock",
    "Pop" to "Pop",
    "Jazz" to "Jazz",
    "Klasyczna" to "Classical",
    "Elektroniczna" to "Electronic",
    "Bas+" to "Bass+",
    "Wokal" to "Vocal",
    "Soprany+" to "Treble+",

    // Ustawienia
    "Wygląd" to "Appearance",
    "Język" to "Language",
    "Systemowy" to "System",
    "Polski" to "Polski",
    "English" to "English",
    "Motyw" to "Theme",
    "Ciemny" to "Dark",
    "Jasny" to "Light",
    "Czarny (AMOLED)" to "Black (AMOLED)",
    "Kolory z okładki" to "Colors from artwork",
    "Akcent interfejsu dopasowuje się do okładki odtwarzanego utworu" to "The interface accent follows the current artwork",
    "Kolor akcentu" to "Accent color",
    "Ekran odtwarzania" to "Now playing screen",
    "Wizualizacja" to "Visualizer",
    "Słupki widma" to "Spectrum bars",
    "Fala" to "Wave",
    "Okrąg" to "Circle",
    "Brak" to "None",
    "Taśma utworu" to "Track strip",
    "Fala dźwiękowa" to "Waveform",
    "Spektrogram" to "Spectrogram",
    "Prosty pasek" to "Simple bar",
    "Rozmyte tło z okładki" to "Blurred artwork background",
    "Rozmiar tekstu" to "Lyrics text size",
    "Szukaj tekstów w internecie (LRCLIB)" to "Search lyrics online (LRCLIB)",
    "Gdy plik nie ma tekstu, OpenAMP zapyta bazę LRCLIB i zapamięta wynik" to
        "When a file has no lyrics, OpenAMP asks LRCLIB and remembers the result",
    "Pokazuj tekst domyślnie" to "Show lyrics by default",
    "Odtwarzanie" to "Playback",
    "Pomijanie ciszy" to "Skip silence",
    "Wznawiaj po podłączeniu słuchawek" to "Resume when headphones connect",
    "Wstrzymaj po odłączeniu słuchawek" to "Pause when headphones disconnect",
    "Pamiętaj kolejkę i pozycję" to "Remember queue and position",
    "Płynne przejście głośności" to "Fade on play/pause",
    "Licz odtworzenie po" to "Count a play after",
    "{0}% utworu" to "{0}% of the track",
    "Minimalna długość utworu" to "Minimum track length",
    "{0} s" to "{0} s",
    "Krótsze pliki (dzwonki, nagrania) są pomijane" to "Shorter files (ringtones, recordings) are skipped",
    "Pomijane foldery" to "Excluded folders",
    "Ścieżki zawierające ten tekst nie trafią do biblioteki, oddzielone przecinkami" to
        "Paths containing this text are left out of the library, comma separated",
    "Statystyki" to "Statistics",
    "Wyczyść statystyki odtworzeń" to "Clear play statistics",
    "Statystyki wyczyszczone" to "Statistics cleared",
    "O aplikacji" to "About",
    "Wersja {0}" to "Version {0}",
    "Odtwarzacz muzyki o otwartym kodzie źródłowym" to "Open-source music player",
    "Kod źródłowy na GitHubie" to "Source code on GitHub",

    // Aktualizacje
    "Aktualizacje" to "Updates",
    "Sprawdzaj aktualizacje automatycznie" to "Check for updates automatically",
    "Raz dziennie w tle i przy starcie; powiadomienie o nowej wersji" to
        "Daily in the background and on launch; notifies about a new version",
    "Sprawdź teraz" to "Check now",
    "Sprawdzanie…" to "Checking…",
    "Masz najnowszą wersję" to "You're up to date",
    "Dostępna wersja {0}" to "Version {0} available",
    "Pobierz i zainstaluj" to "Download and install",
    "Pobieranie… {0}%" to "Downloading… {0}%",
    "Instalowanie…" to "Installing…",
    "Zainstaluj" to "Install",
    "Co nowego" to "What's new",
    "Później" to "Later",
    "Nowa wersja OpenAMP" to "New OpenAMP version",
    "Dostępna jest wersja {0}. Dotknij, aby zaktualizować." to "Version {0} is available. Tap to update.",
    "Zezwól na instalację aplikacji z OpenAMP, aby zaktualizować" to "Allow OpenAMP to install apps to update",
    "Błąd aktualizacji: {0}" to "Update error: {0}",
    "Serwer odpowiedział kodem {0}" to "Server responded with code {0}",
    "Pobieranie nie powiodło się ({0})" to "Download failed ({0})",
    "Nie można zapisać pliku aktualizacji" to "Cannot save the update file",
    "Wydanie nie zawiera pliku APK" to "The release has no APK file",
    "OpenAMP zaktualizowany do {0}" to "OpenAMP updated to {0}",
    "Dotknij, aby otworzyć aplikację" to "Tap to open the app",
    "Aktualizacja nieudana: {0}" to "Update failed: {0}",
    "kod {0}" to "code {0}",
    "Odtwarzanie muzyki" to "Music playback",
    "Ostatnio sprawdzono: {0}" to "Last checked: {0}",
)
