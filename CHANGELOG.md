# Historia zmian

## 2.3.1

- Naprawione odtwarzanie plików powyżej **192 kHz** (np. FLAC 384 kHz) – przepróbkowywane do 192 kHz, bo większość urządzeń nie otwiera wyjścia w wyższej częstotliwości.
- Plik, którego urządzenie nie odtworzy, nie zatrzymuje już muzyki bez słowa: **komunikat**, przejście do następnego utworu i wpis w dzienniku błędów odtwarzania.
- **Przytrzymanie przycisków pod okładką** (wizualizacja, wyłącznik czasowy, powtarzanie, losowanie) otwiera menu ze wszystkimi opcjami.
- **TalkBack**: przyciski aplikacji dają się aktywować czytnikiem ekranu (dotknięcie i przytrzymanie).

## 2.3.0

- **Silnik dźwięku**: korektor liczony w podwójnej precyzji; zmiany ustawień przechodzą płynnie (bez trzasków przy ruszaniu suwakami); limiter z wyprzedzeniem zamiast ucinania szczytów; kompresor z miękkim kolanem; dithering przy zapisie 16 bit. Naprawione zniekształcenie najgłośniejszych próbek (powyżej 99% skali spadały do ~76%).
- **Hi-res**: pliki 24/32-bit (FLAC, WAV, ALAC) przetwarzane i odtwarzane bez obcinania do 16 bitów.
- **ReplayGain** według utworu albo albumu, z własnym wzmocnieniem – działa od pierwszej próbki utworu, także przy odtwarzaniu bez przerw.
- Naprawione **przyciszanie**: płynne wyciszanie przy graj/pauza w jednym miejscu, odtwarzacz nie zostaje już przyciszony.
- Naprawione **pomijanie ciszy**: skraca tylko prawdziwą ciszę (poniżej −60 dB, od 1,5 s) i nie ścisza cichych fragmentów muzyki.
- **Nowy ekran korektora**: zakładki Korektor / Efekty / Wyjście, porównanie A/B (przytrzymaj), fader przedwzmacniacza, zakres wykresu ±6/±12/±15 dB, ostrzeżenie o podbiciu ponad 0 dB z „Wyrównaj”, mierniki poziomu L/P i ściszenia przez dynamikę, format wejścia i wyjścia.
- **Widżety**: nowy edytor (Elementy, Rozmiary, Wygląd) z zapisem na żywo i „Cofnij zmiany”; odstępy liczone od górnej krawędzi, także ujemne; podgląd każdego widżetu na liście; delikatne cienie elementów; wstecz przy dodawaniu nie usuwa już widżetu.

## 2.2.0

- Widżet: **style przycisków** – koło, zaokrąglony kwadrat albo obwódka pod ▶, same ikony, lub wszystkie przyciski w delikatnych kołach.
- Widżet: regulowane **odstępy nad i pod przyciskami** oraz **nad paskiem postępu** (Android 12+); okładka dopasowuje się do wolnego miejsca.
- Widżet: **wyśrodkowanie tekstu** (tytuł, wykonawca, album) i suwak **zaokrąglenia okładki** (0% – ostre rogi, 50% – koło).

## 2.1.0

- **Nowe statystyki**: łączny czas z przeliczeniem na doby muzyki, dziś, ten tydzień z trendem, średnia dzienna, seria dni i rekord; wykres aktywności (14 dni / 30 dni / rok) z linią średniej i podglądem wartości po dotknięciu; rytm tygodnia i pora dnia; rankingi utworów (odtworzenia albo czas), wykonawców, albumów i gatunków z odtwarzaniem po dotknięciu; dekady, formaty plików, pokrycie biblioteki, ostatnio słuchane i zapomniane perełki.
- Ekran odtwarzania: pod tagami pliku następny utwór z kolejki.
- Wizualizacje na okładce z lekkim cieniem – widoczne także na jasnych okładkach; okładka przy starcie odtwarzania rośnie tylko o 2 px.
- **Tło „Żywe kolory”**: płynące, nakładające się plamy światła reagujące na muzykę – bas powiększa plamy, średnie i wysokie tony poruszają pozostałymi, uderzenia dają krótki błysk, głośność przyspiesza ruch. Barwy z okładki (kilka kolorów, nie jeden), z akcentu, z gotowych palet (zachód słońca, ocean, neon, las, pastele, monochromatyczne) albo wokół własnego koloru; płynne przejścia między utworami. Suwaki ruchu, reakcji na muzykę i intensywności, animowany podgląd palet.
- **Jasny motyw** naprawiony: tekst, kafelki, karty, menu i arkusze biorą kolory z motywu zamiast stałej bieli i czerni.
- Spójny design w całej aplikacji: wspólne promienie zaokrągleń (małe, wiersze i kafelki, karty, okna, pigułki) i niższe odstępy w pigułkach, zakładkach i segmentach.
- Edytor widżetu z launchera wygląda dokładnie jak w aplikacji (motyw, akcent z okładki, nagłówek).
- Ekran odtwarzania: mniejsze odstępy górnego paska, o połowę mniejszy margines okładki.

## 2.0

- **Modele AI jak w OpenDesktop**: okno wyboru z wyszukiwarką, filtrem „Darmowe” i sortowaniem po nazwie, cenie i kontekście; przy modelu cena (USD za 1 mln tokenów) i długość kontekstu, gdy dostawca je podaje. Modele niebędące czatem są odfiltrowane.
- Ustawienia: sekcja **Ogólne** na początku; wizualizacje pogrupowane według typów (słupki, fale i linie, wokół okładki).
- Przełączniki w ustawieniach wyśrodkowane, w jednym wierszu i równej wysokości.
- Podpowiedź po przytrzymaniu przycisku znika płynnie – bez pustej pigułki.
- Widżet **Odtwarzacz**: okładka we własnym kontenerze – przy 100% ma wysokość całego wiersza, a z przyciskami w kolumnie sięga aż do taśmy postępu na dole.
- Obok okładki tytuł i „Wykonawca – Album” w jednym wierszu (do wyłączenia).
- Trzy położenia przycisków: w kolumnie obok okładki (wyśrodkowane pod tytułem), w osobnym wierszu nad taśmą albo pod taśmą.
- Zwarty edytor widżetu: zakładki (Układ, Okładka, Pasek, Przyciski, Tekst, Wygląd), karty z listami rozwijanymi zamiast dużych siatek kafelków.
- W całej aplikacji wybór spośród więcej niż trzech opcji to lista rozwijana (także w korektorze: liczba pasm, profil wyjścia, typ filtra).
- Podglądy na listach: style paska widżetu, wizualizacje i taśmy utworu widać przed wybraniem – w menu i pod wybraną opcją.

## 1.6.8

- **AI: wielu dostawców** – Anthropic, OpenAI, Google Gemini, OpenRouter, Mistral, xAI, DeepSeek, Groq, Cerebras, Together, Fireworks, DeepInfra, Perplexity, Cohere, NVIDIA, Hugging Face, Moonshot, Qwen, Z.ai, a także Ollama, LM Studio i dowolny serwer zgodny z OpenAI. Każdy ma własny klucz, adres i model; lista modeli jest pobierana z API dostawcy.
- Ekran odtwarzania: wizualizacje na okładce nad dolnymi przyciskami, mniejsze przyciski pod tytułem, tytuł i wykonawca mogą być wyśrodkowane, z regulowanym rozmiarem.
- Widżet: przyciski nad albo pod taśmą, zwarty układ bez odstępów i marginesy od 0 dp; widżet jest domyślnie bardziej zwarty.
- Powiadomienie: domyślnie zwarte (niskie także po rozwinięciu) i ze stałym przyciskiem **X** zamykającym odtwarzacz.
- Przebudowane ustawienia aplikacji: siedem sekcji, w każdej karty z podtytułem, wybory i suwaki na górze, przełączniki-kafelki w kartach.

## 1.6.7

- Widżet **Odtwarzacz**: okładka po lewej, po prawej, u góry albo jako tło, z suwakiem rozmiaru i kadrowaniem w pionie (widżet **Okładka** – powiększenie kadru).
- Pasek odtwarzania w widżetach w stylach jak w aplikacji: **Jak w aplikacji**, fala dźwiękowa, lustrzana fala, linia konturu, matryca kropek, segmenty LED, a także cienka linia, linia z uchwytem i gruby pasek; regulowana wysokość paska.
- Mniejszy odstęp między paskiem a przyciskami (do ustawienia suwakiem), czasy pod paskiem albo po jego bokach.
- Osobne przełączniki losowania i powtarzania, do tego poprzedni/następny, wielkość przycisków, margines wewnętrzny, liczba wierszy i pogrubienie tytułu.
- Ustawienia aplikacji i konfiguracja widżetów w układzie jak w OpenPDF: jedna lista sekcji z nagłówkami, kafelki-przełączniki (przytrzymanie pokazuje opis) i zwarte karty z wyborami i suwakami.

## 1.6.6 — Transparent application icon

- Use the transparent application mark in launcher, splash and existing branding surfaces.
- Preserve alpha in legacy and adaptive Android icons; refresh themed icon resources.

## 1.6.5

- Wizualizacje na okładce: cztery nowe style – **Puls** (poświata i okładka pulsujące z basem), **Lustrzane słupki**, **Fala w okręgu** i **Krzywa widma**.
- Wszystkie wizualizacje mocniej reagują na dźwięk: automatyczne wzmocnienie dla cichszych nagrań, większy kontrast, szybszy spadek słupków i wykrywanie uderzeń basu.

## 1.6.4

- Ekran odtwarzania: subtelniejszy górny pasek nad okładką – **Lista** w lewym górnym rogu, „więcej” (kropki) w prawym, bez tła i z przygaszonymi ikonami.

## 1.6.3

- Naprawiony crash przy starcie: po doczytaniu okładki powiadomienie było odświeżane z wątku w tle, a odtwarzacz wolno obsługiwać tylko z głównego wątku.

## 1.6.2

- Zapis błędów: gdy aplikacja się wywróci, przy następnym uruchomieniu pokazuje szczegóły błędu z przyciskami **Kopiuj** i **Udostępnij**.

## 1.6.1

- Naprawiony crash zaraz po uruchomieniu: nowe powiadomienie wysyłało okładkę w pełnej rozdzielczości (1024 px) w obu widokach, przez co system odrzucał je przy starcie odtwarzacza. Okładka w powiadomieniu jest teraz przeskalowana.

## 1.6.0

- **Analiza AI biblioteki**: nowa grupa **Kategorie AI** (nastroje, style, epoki, okazje) i miksy AI w „Miksach dla Ciebie”. Z własnym kluczem API Anthropic analizę robi Claude (Opus 5.5, Sonnet 5.5 lub Haiku 5.5 – do modelu trafia tylko lista utworów z metadanymi, nie pliki); bez klucza działa analiza lokalna na telefonie. Propozycja analizy pojawia się, gdy biblioteka zmieni się o co najmniej 5 utworów.
- Pliki usunięte z urządzenia znikają same z playlist, ulubionych, kategorii i miksów; nowa opcja **Usuń z urządzenia** w menu utworu i przy zaznaczaniu wielu utworów.
- Miksy dla Ciebie i ekran startowy biblioteki opierają się wyłącznie na utworach (bez pasków albumów); widok miksów: karty albo lista.
- Listy z **przyklejonymi nagłówkami sekcji** (litery, wykonawcy, lata, daty, długość…) – dotknięcie nagłówka otwiera szybki skok do dowolnej sekcji.
- Sortowanie tylko tam, gdzie ma sens; w miksach, ostatnio dodanych, kolejce itp. zostaje sam wybór widoku. Kompaktowe menu rozwijane, mniejsze nagłówki list i zwartszy cały interfejs.
- **Ustawienia od nowa**: zakładki (Wygląd, Odtwarzacz, Dźwięk, Biblioteka, AI, Blokada, Inne) i zwarte wiersze w stylu OpenPDF; gdy włączone są kolory z okładki, wybór akcentu jest zablokowany.
- Ekran odtwarzania: przycisk **Lista** w prawym górnym rogu (pierwsze dotknięcie – bieżąca lista odtwarzania, drugie w tym samym miejscu – biblioteka), dotknięcie okładki niczego już nie robi, dolny pasek chowa się po chwili bez dotyku.
- **Taśmy utworu** przerobione i nowe: fala, lustrzana fala, linia konturu, matryca kropek, segmenty LED, taśma przesuwana z podziałką, spektrogram w kolorze, kaseta ze szpulami (zniknął prosty pasek). Przewijanie przeciągnięciem w całym polu wokół przycisków.
- Tekst utworu na rozmytej okładce z miniaturą i tytułem; pasek przesunięcia tekstu już nie nachodzi na okładkę ani przyciski.
- Ładniejszy tryb AMOLED: oddychająca poświata w kolorze okładki, pierścień postępu, data, następny utwór, płynne przejścia tekstu i wędrowanie treści w granicach ekranu.
- **Nowe powiadomienie** (domyślnie): duża okładka na całą wysokość po lewej, „wykonawca – tytuł” nad wyśrodkowanymi przyciskami, tło w kolorze okładki; do wyboru także systemowy odtwarzacz.
- Ekran blokady mocniej przesiąka kolorem okładki; AOD rozbudowany (poświata, pierścień postępu, następny utwór) i przesuwany w granicach liczonych z rozmiaru treści.
- Korektor dopracowany i bez zbędnych podpisów; rozbudowane **Właściwości pliku** (dźwięk, plik, historia słuchania, kopiowanie ścieżki, udostępnianie, usuwanie).
- Menu dolnego paska bez dublujących się pozycji.

## 1.5.0

- Nowe powiadomienie odtwarzacza: duża okładka w pełnej jakości (obraz osadzony w pliku zamiast przestarzałych miniatur), dwa konfigurowalne przyciski obok poprzedni / odtwórz / następny – lubię, losowo, powtarzanie lub **zamknij odtwarzacz** (domyślnie: lubię i zamknij). Ikony przycisków odzwierciedlają bieżący stan.
- Samoczynne zamykanie: po pauzie odtwarzacz i powiadomienie znikają same (od razu, po 1, 5, 15, 30, 60 min albo nigdy – domyślnie 5 min). Kolejka i pozycja zostają zapamiętane.
- Odtwarzacz na ekranie blokady (opcja): po wygaszeniu ekranu podczas grania nad blokadą pojawia się pełny odtwarzacz – zegar i data, duża okładka, rozmyte tło lub kolor okładki, przewijanie, sterowanie, serduszko, losowanie/powtarzanie, „Następnie: …”; przesunięcie w górę wraca do blokady systemu, odblokowanie odciskiem zamyka go samo.
- Uproszczony tryb AOD: po chwili bez dotyku czarny, przygaszony ekran z zegarem, tytułem, małą okładką i cienkim paskiem postępu, przesuwany przeciw wypaleniu; podczas ładowania lub zawsze podczas grania, z ustawianym czasem i przyciemnieniem.
- Nowe sekcje ustawień „Powiadomienie” i „Ekran blokady i AOD” z podglądem.

## 1.4.0

- Widżety na pulpicie – cztery rodzaje: **Okładka** (2×2, okładka na całą powierzchnię), **Pasek** (4×1), **Odtwarzacz** (4×2 i większy: postęp, czasy, losowanie, powtarzanie, serduszko, pozycja w kolejce) i **Skróty** (do sześciu: graj/pauza, losuj wszystko, Mix dnia, ulubione, nowe, najczęstsze, odkrywaj, perełki, niegrane, wyłącznik czasowy, szukaj, korektor – z opcjonalnym paskiem bieżącego utworu).
- Każdy widżet ma własne ustawienia z podglądem na żywo: tło (rozmyta okładka, kolor okładki, ciemne, czarne, jasne, przezroczyste, dowolny kolor), krycie, zaokrąglenie rogów, kolor akcentu (z okładki, z aplikacji, neutralny, dowolny), kształt okładki, styl paska postępu (linia, gruby, segmenty), widoczne elementy, rozmiar tekstu i działanie dotknięcia okładki.
- Układ widżetów dopasowuje się do rozmiaru (okładka rośnie razem z widżetem, zbędne elementy chowają się na małych rozmiarach); pasek postępu odświeża się co 2 s tylko przy włączonym ekranie.
- Widżety działają także bez uruchomionej aplikacji: pokazują ostatni utwór z zapamiętanej kolejki, a ▶ wznawia odtwarzanie (albo losuje całą bibliotekę).
- Nowa sekcja „Widżety na pulpicie” w ustawieniach: lista widżetów z edycją i dodawanie nowych prosto na pulpit.

## 1.3.1

- Płynne przełączanie utworów przesunięciem okładki – bez przeskoków i migania okładek.
- Przycisk „Biblioteka” przeniesiony z paska nad listą do prawego górnego rogu nagłówka listy (także na bieżącej liście odtwarzania).
- Usunięty niedziałający przycisk lupki z list utworów; wyszukiwanie ma osobny widok pod lupką na dolnym pasku (pole od razu aktywne, wyniki na bieżąco).

## 1.3.0

- Inteligentny gest okładki: okładka podąża za palcem, obok wysuwa się okładka następnego lub poprzedniego utworu (zgodnie z losowaniem), decyzja wg odległości lub szybkości rzutu, opór na końcu kolejki, wibracja przy progu.
- Dotknięcie okładki otwiera bieżącą listę odtwarzania w faktycznej kolejności grania (także losowej), przewiniętą do grającego utworu.
- Płynne, animowane skalowanie listy szczypaniem.
- Filtr biblioteki: wybór folderów i formatów plików, minimalna długość utworu, ukrywanie utworów „nie lubię”.
- Dolny pasek na całą szerokość, przy samej krawędzi ekranu.
- Przyciski „lubię / nie lubię” opcjonalne i mniejsze, przycisk tekstu w rogu okładki, okrągłe przyciski akcji, parametry pliku na dole ekranu.

## 1.2.0

- Nowy układ w stylu OpenPDF: płaski czarno-biały interfejs, przyciski-koła i pigułki, sprężysta reakcja na dotyk z haptyką, dymki z nazwą po przytrzymaniu.
- Odtwarzacz jest stroną główną: duża okładka z przyciskami „lubię / nie lubię” i kolejką, tytuł i wykonawca w pigułkach, rząd szybkich akcji z licznikiem pozycji w kolejce, fala dźwiękowa całego utworu z nałożonymi przyciskami sterowania i parametry pliku.
- Dolny pasek: Biblioteka, Korektor, Szukaj, Menu (skróty do kolejki, statystyk, ustawień, wyłącznika, tekstu, skanowania i aktualizacji).
- Kompaktowa biblioteka nastawiona na utwory: kategorie (wszystkie, ostatnio dodane, najczęściej i ostatnio odtwarzane, ulubione, miksy, albumy, wykonawcy, gatunki, foldery, playlisty), wiersze z metadanymi i sześć poziomów gęstości – od samego tekstu po siatkę – zmieniane szczypaniem dwoma palcami.
- Zaznaczanie wielu utworów: odtwórz, zagraj jako następne, dodaj do kolejki, do playlisty lub ulubionych.
- Korektor: od 5 do 31 pasm, typ filtra dla każdego pasma (szczyt, półki, dolno- i górnoprzepustowy, wycięcie), kompresor dynamiki, crossfeed dla słuchawek.
- Inteligentne profile korektora: osobne ustawienia dla głośnika, słuchawek, Bluetooth i USB przełączane samoczynnie po zmianie wyjścia.
- „Nie lubię”: pomija utwór i wyklucza go z miksów i sprytnego mieszania.
- Inteligentny tryb AMOLED: po 20 s bez dotyku podczas grania ekran przechodzi w czarny tryb z zegarem, okładką i bieżącą linią tekstu, z ochroną przed wypaleniem.
- Nowe style paska przewijania: fala całego utworu (domyślna) i przesuwana taśma.

## 1.1.0

- Nowy silnik dźwięku OpenAMP (DSP w torze odtwarzacza, niezależny od korektora telefonu): 10-pasmowy korektor parametryczny z regulacją częstotliwości, wzmocnienia i dobroci Q każdego pasma, półki basów i sopranów, przedwzmacniacz z trybem automatycznym, balans, szerokość stereo, mono, zamiana kanałów i limiter.
- Nowy ekran korektora: krzywa charakterystyki na tle widma na żywo z przeciąganymi punktami, fadery jak w mikserze, pokrętła, 18 presetów z miniaturami krzywych, własne presety.
- Inteligentny korektor: preset dobierany do gatunku utworu.
- Sprytne mieszanie: bez powtórek wykonawcy pod rząd, rzadziej słuchane i ulubione częściej.
- Ekran Start z miksami z historii słuchania: Mix dnia, Na tę porę, Zapomniane perełki, Odkrywaj, Ulubione na rotacji, oraz ostatnio odtwarzane, najczęściej odtwarzane i nowe albumy.
- Ekran statystyk: czas słuchania, wykres 14 dni, najczęściej odtwarzane utwory, ulubieni wykonawcy i gatunki.
- Prawdziwy czarny motyw AMOLED (domyślny) i dowolny kolor akcentu z próbnika (HEX).
- Ekran odtwarzania: tła (rozmyta okładka, gradient, zorza reagująca na muzykę, czysta czerń), kształty okładki (zaokrąglona, kwadratowa, okrągła, obracająca się płyta winylowa), opcje gestów, informacji technicznych, wygaszania ekranu i wyrównania tekstu.

## 1.0.0

Pierwsze wydanie OpenAMP.

- Biblioteka z MediaStore: utwory, albumy, wykonawcy, gatunki, foldery i playlisty.
- Sortowanie: tytuł, wykonawca, album, rok, czas, data dodania, liczba odtworzeń, ostatnio odtwarzane, rozmiar, losowo; widok listy, kompaktowej listy i siatki.
- Inteligentne playlisty: ulubione, najczęściej i ostatnio odtwarzane, ostatnio dodane, nigdy nieodtwarzane.
- Ekran odtwarzania: duża okładka, metadane techniczne, wizualizacja widma (słupki, fala, okrąg), przesuwana „taśma” utworu z falą dźwiękową albo spektrogramem.
- Teksty utworów: zsynchronizowane (LRC) i zwykłe, z tagów pliku, pliku .lrc albo z bazy LRCLIB; przesunięcie czasu.
- Korektor z krzywą, presety, własne presety, wzmocnienie basów, przestrzenność i wzmocnienie głośności.
- Szybkość i wysokość dźwięku, wyłącznik czasowy, kolejka, pamięć kolejki i pozycji.
- Interfejs po polsku i angielsku, motywy (ciemny, jasny, AMOLED), kolory z okładki.
- Automatyczne aktualizacje z GitHub Releases z powiadomieniem.
