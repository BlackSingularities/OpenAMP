# OpenAMP

Odtwarzacz muzyki na Androida (10+) o otwartym kodzie źródłowym. Kotlin, Jetpack Compose, Media3/ExoPlayer.

## Funkcje

- **Biblioteka** – utwory, albumy, wykonawcy, gatunki, foldery, playlisty; wyszukiwanie; sortowanie m.in. według liczby odtworzeń, daty dodania i ostatniego odtworzenia; widok listy, kompaktowej listy, siatki i dużej siatki.
- **Ekran odtwarzania** – duża okładka (gest w bok zmienia utwór), tło z rozmytej okładki, kolory interfejsu dopasowane do okładki, metadane (format, częstotliwość, bitrate, kanały).
- **Taśma utworu** – fala dźwiękowa całego pliku przesuwa się pod głowicą (przeciąganie przewija), alternatywnie spektrogram na żywo.
- **Wizualizacje** – widmo liczone z FFT bezpośrednio w torze audio (bez uprawnienia do mikrofonu): słupki, oscyloskop, okrąg wokół okładki.
- **Teksty** – LRC ze stemplami czasu (podświetlanie, przewijanie, przeskok do linii, przesunięcie czasu) albo zwykły tekst przewijany razem z utworem. Źródła: tagi ID3 (USLT/SYLT), komentarze Vorbis we FLAC, M4A, plik `.lrc` obok utworu, plik wskazany ręcznie, [LRCLIB](https://lrclib.net).
- **Dźwięk** – korektor graficzny z edytowalną krzywą i presetami, wzmocnienie basów, przestrzenność, wzmocnienie głośności, szybkość i wysokość, pomijanie ciszy, płynne przejścia głośności.
- **Wygoda** – kolejka z edycją, wyłącznik czasowy, statystyki odtworzeń, ulubione, playlisty, pamięć kolejki, powiadomienie i ekran blokady (MediaSession), wznawianie po podłączeniu słuchawek.
- **Język** – polski i angielski (przełącznik w ustawieniach).
- **Aktualizacje** – aplikacja sama sprawdza GitHub Releases, powiadamia o nowej wersji, pobiera i instaluje APK.

## Instalacja

Pobierz najnowszy plik `OpenAMP-x.y.z.apk` z [Releases](https://github.com/BlackSingularities/OpenAMP/releases/latest) i zainstaluj go. Kolejne wersje zainstalujesz z poziomu aplikacji (Ustawienia → Aktualizacje).

## Budowanie

```
gradle :app:assembleDebug
```

Wymaga JDK 17 i Android SDK 36. Każdy push na `main` buduje podpisany APK i publikuje wydanie (wersja z pliku `VERSION`).

## Licencja

MIT
