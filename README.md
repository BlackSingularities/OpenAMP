# OpenAMP

Odtwarzacz muzyki na Androida (10+) o otwartym kodzie źródłowym. Kotlin, Jetpack Compose, Media3/ExoPlayer.

## Funkcje

- **Biblioteka** – utwory, albumy, wykonawcy, gatunki, foldery, playlisty; wyszukiwanie; sortowanie m.in. według liczby odtworzeń, daty dodania i ostatniego odtworzenia; widok listy, kompaktowej listy, siatki i dużej siatki.
- **Ekran odtwarzania** – duża okładka (gest w bok zmienia utwór), tło z rozmytej okładki, kolory interfejsu dopasowane do okładki, metadane (format, częstotliwość, bitrate, kanały).
- **Analiza AI** – kategorie (nastroje, style, epoki, okazje) i miksy złożone z utworów; z własnym kluczem API Anthropic układa je Claude (wysyłana jest tylko lista utworów z metadanymi), bez klucza – analiza lokalna. Propozycja ponownej analizy po zmianie biblioteki o co najmniej 5 utworów; usunięte pliki znikają ze wszystkich list.
- **Taśma utworu** – osiem stylów (fala, lustrzana fala, linia konturu, matryca kropek, segmenty LED, taśma przesuwana, spektrogram, kaseta); przewijanie przeciągnięciem w całym polu wokół przycisków.
- **Wizualizacje** – widmo liczone z FFT bezpośrednio w torze audio (bez uprawnienia do mikrofonu): słupki, oscyloskop, okrąg wokół okładki.
- **Teksty** – LRC ze stemplami czasu (podświetlanie, przewijanie, przeskok do linii, przesunięcie czasu) albo zwykły tekst przewijany razem z utworem. Źródła: tagi ID3 (USLT/SYLT), komentarze Vorbis we FLAC, M4A, plik `.lrc` obok utworu, plik wskazany ręcznie, [LRCLIB](https://lrclib.net).
- **Dźwięk** – korektor graficzny z edytowalną krzywą i presetami, wzmocnienie basów, przestrzenność, wzmocnienie głośności, szybkość i wysokość, pomijanie ciszy, płynne przejścia głośności.
- **Wygoda** – kolejka z edycją, wyłącznik czasowy, statystyki odtworzeń, ulubione, playlisty, pamięć kolejki, powiadomienie i ekran blokady (MediaSession), wznawianie po podłączeniu słuchawek.
- **Widżety na pulpicie** – cztery rodzaje (Okładka 2×2, Pasek 4×1, Odtwarzacz 4×2+, Skróty), każdy z własną konfiguracją i podglądem na żywo: tło (rozmyta okładka, kolor okładki, ciemne, czarne, jasne, przezroczyste, własny kolor), krycie, zaokrąglenie rogów, kolor akcentu, kształt okładki, pasek postępu w trzech stylach, czasy, losowanie i powtarzanie, serduszko, pozycja w kolejce, rozmiar tekstu, działanie dotknięcia; skróty do miksów, losowania, ulubionych, wyłącznika czasowego, wyszukiwania i korektora. Układ dopasowuje się do rozmiaru widżetu.
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

---

**Projekt i wykonanie: [redzikowski.com](https://redzikowski.com)**

<a href="https://buycoffee.to/adamredzikowski" target="_blank"><img src="https://buycoffee.to/static/img/share/share-button-dark--pl.png" style="width: 351px; height: 92px" width="351" height="92" alt="Postaw kawę dla adamredzikowski na buycoffee.to"></a>
