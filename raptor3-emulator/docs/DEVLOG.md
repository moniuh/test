# Dziennik iteracji — pętla ulepszeń emulatora

Konwencja pętli: każda iteracja to burza mózgów (≥10 pomysłów), wybór jednego,
implementacja, test w headless Chromium i wpis w tym dzienniku. Pomysły
niewybrane zostają w backlogu na kolejne iteracje.

---

## Iteracja 1 — Panel sekwencji startowej

**Pomysły (backlog otwarcia):**
1. Panel sekwencji startowej: zegar misji T+ i stemplowane fazy rozruchu ✔ WYBRANY
2. Wykresy telemetrii — przewijane przebiegi ciągu i ciśnienia komory
3. Schładzanie kriogeniczne przed startem (prechill) z wentami pary
4. Dym i para po wyłączeniu silnika
5. Tryby awarii (niestabilne spalanie, nadobroty pomp) + automatyczny system FDS
6. Widok przekroju silnika (płaszczyzny tnące) odsłaniający wnętrze
7. Predefiniowane ujęcia kamery + tryb kinowy auto-orbit
8. Szron narastający na orurowaniu LOX
9. Zegarowe wskaźniki (tarcze) dla ciśnienia komory i pomp
10. Eksport zarejestrowanej telemetrii do CSV
11. Warianty silnika Raptor 1 / 2 / 3 (przełączane specyfikacje i orurowanie)
12. Ograniczenie tempa zmian przepustnicy (slew rate) jak w prawdziwym sterowniku
13. Cienie rzucane przez silnik i kratownicę (shadow mapping)
14. Panel edukacyjny „POMOC" z opisem silnika + statystyki testów (localStorage)
15. Adaptacyjna rozdzielczość renderowania dla słabszych GPU

**Wybrano:** #1 — najbardziej „konsolowy" element brakujący w kokpicie: widok,
*co* silnik robi w czasie, a nie tylko *ile* pokazuje.

**Zrobione:**
- `simulation.js`: zegar misji `met` (rusza z komendą URUCHOM) i log zdarzeń
  `seq[]` — każde przejście stanu (rozruch pomp, zapłon, narastanie, praca,
  wyłączanie, silnik bezpieczny) dostaje stempel czasu.
- `hud.js`: renderowanie logu (odświeżany tylko przy nowym zdarzeniu) i zegara.
- `index.html` + `style.css`: nowy panel SEKWENCJA (lewy dolny róg), ostatnie
  zdarzenie pulsuje.

**Test:** start → pełna sekwencja stemplowana poprawnie, brak błędów konsoli.

---

## Iteracja 2 — Wykresy telemetrii

**Pomysły:**
1. Wykresy telemetrii — przewijane przebiegi ciągu i p. komory ✔ WYBRANY
2. Schładzanie kriogeniczne przed startem (prechill) z wentami pary
3. Dym i para po wyłączeniu silnika
4. Tryby awarii + automatyczny FDS
5. Widok przekroju silnika (płaszczyzny tnące)
6. Predefiniowane ujęcia kamery + tryb kinowy
7. Szron na orurowaniu LOX
8. Zegarowe tarcze wskaźników
9. Eksport telemetrii do CSV
10. Warianty silnika Raptor 1 / 2 / 3
11. Slew rate przepustnicy
12. Cienie (shadow mapping)
13. Panel „POMOC" + statystyki testów
14. Adaptacyjna rozdzielczość renderowania
15. NOWY: znaczniki zdarzeń sekwencji na wykresach
16. NOWY: pauza / zwolnione tempo symulacji

**Wybrano:** #1 — chwilowe liczby nie pokazują dynamiki rozruchu i dławienia;
wykres czyni sekwencję czytelną na pierwszy rzut oka.

**Zrobione:**
- Nowy moduł `charts.js`: `StripChart` (bufor pierścieniowy 240 próbek,
  rysowanie z siatką i poświatą) + `TelemetryCharts` (próbkowanie co 0,15 s
  → okno ~36 s) dla ciągu [kN] i ciśnienia komory [bar].
- Dwa canvasy w panelu telemetrii, spięte w pętli głównej.

**Test:** przebiegi rysują się podczas rozruchu i wyłączenia, brak błędów.

---

## Iteracja 3 — Schładzanie kriogeniczne (prechill) + opary

**Pomysły:**
1. Prechill z wentami pary przed rozruchem pomp ✔ WYBRANY
2. Dym i para po wyłączeniu (pełny efekt na bazie nowego systemu cząstek)
3. Tryby awarii + FDS
4. Widok przekroju silnika
5. Kamery predefiniowane + tryb kinowy
6. Szron na orurowaniu LOX
7. Tarcze zegarowe wskaźników
8. Eksport CSV
9. Warianty Raptor 1/2/3
10. Slew rate przepustnicy
11. Cienie
12. Panel „POMOC" + statystyki
13. Adaptacyjna rozdzielczość
14. Znaczniki zdarzeń na wykresach
15. NOWY: dźwięk syku wentów w fazie schładzania

**Wybrano:** #1 — prawdziwy Raptor przed startem długo „dymi" zimnym gazem;
sekwencja bez tej fazy wyglądała na zbyt natychmiastową. Przy okazji powstaje
ogólny system cząstek, który obsłuży też dym i parę (pomysł #2).

**Zrobione:**
- Nowy moduł `particles.js`: `PuffSystem` — cząstki na GPU (THREE.Points +
  shader, bufor pierścieniowy 700 sztuk, dryf, rozrost i zanik w vertex
  shaderze; jedno wywołanie draw).
- `simulation.js`: nowy stan `PRECHILL` (3,2 s) między GOTOWY a ROZRUCHEM POMP,
  wpis „SCHŁADZANIE WSTĘPNE" w sekwencji.
- `main.js`: emisja oparów — zimny gaz osiadający z dyszy + poziome pióropusze
  z wentów przy turbopompach; lekki boiloff także w trakcie wyłączania.

**Test:** stan SCHŁADZANIE widoczny z oparami, pełna sekwencja przechodzi,
brak błędów konsoli.

---

## Iteracja 4 — Para na płycie i dym po wyłączeniu

**Pomysły:**
1. Kłęby pary/pyłu odbite od płyty przy pracy + dym resztkowy po wyłączeniu ✔ WYBRANY
2. Tryby awarii + FDS
3. Widok przekroju silnika
4. Kamery predefiniowane + tryb kinowy
5. Szron na orurowaniu LOX
6. Tarcze zegarowe wskaźników
7. Eksport CSV
8. Warianty Raptor 1/2/3
9. Slew rate przepustnicy
10. Cienie
11. Panel „POMOC" + statystyki
12. Adaptacyjna rozdzielczość
13. Znaczniki zdarzeń na wykresach
14. Syk wentów w fazie schładzania (dźwięk)
15. NOWY: podświetlanie pary światłem pióropusza (cząstki „lit")
16. NOWY: deflektor płomienia w płycie zamiast płaskiego odbicia

**Wybrano:** #1 — naturalna kontynuacja systemu cząstek; test statyczny bez
chmury pary u podstawy wyglądał sterylnie.

**Zrobione (`main.js`):**
- `emitSteam()` — pierścień emisji wokół punktu uderzenia strugi, prędkość
  radialna rosnąca z mocą; aktywne tylko przy ciśnieniu otoczenia > ~0,22 atm
  (w próżni nie ma czego odbijać).
- `emitSmoke()` — po komendzie wyłączenia ~4,5 s szarych kłębów unoszących się
  z dyszy, intensywność wygasa liniowo.
- Dwa nowe `PuffSystem` (para 900, dym 500 cząstek).

**Test:** para roluje po płycie przy pracy, dym unosi się po wyłączeniu,
brak błędów konsoli.

---

## Iteracja 5 — Tryby awarii i system FDS

**Pomysły:**
1. Awarie testowe + automatyczny nadzór FDS ✔ WYBRANY
2. Widok przekroju silnika
3. Kamery predefiniowane + tryb kinowy
4. Szron na orurowaniu LOX
5. Tarcze zegarowe wskaźników
6. Eksport CSV
7. Warianty Raptor 1/2/3
8. Slew rate przepustnicy
9. Cienie
10. Panel „POMOC" + statystyki
11. Adaptacyjna rozdzielczość
12. Znaczniki zdarzeń na wykresach
13. Podświetlanie pary światłem pióropusza
14. Deflektor płomienia w płycie
15. NOWY: historia awarii w panelu sekwencji z osobnym kolorem
16. NOWY: tryb „hard mode" — losowe awarie bez przycisku

**Wybrano:** #1 — emulator bez możliwości zepsucia czegoś to symulator idealnego
świata; FDS dodaje dramaturgię i drugi tor logiki sterownika.

**Zrobione:**
- `simulation.js`: `injectFailure()` — w stanie GOTOWY uzbraja BRAK ZAPŁONU,
  w PRACY losuje NIESTABILNE SPALANIE (rosnące oscylacje, FDS tnie po 2,2 s)
  lub NADOBROTY POMPY CH4 (niekontrolowany wzrost obrotów, odcięcie >105%).
  Nowy stan `ABORT` z szybszym zamknięciem zaworów; komunikat alarmu
  utrzymywany do następnego startu.
- `hud.js` + `index.html` + `style.css`: migający czerwony baner
  „FDS · <powód>", przycisk ⚠ AWARIA, czerwony pasek pompy powyżej 100%.
- `sound.js`: brzęczyk alarmu (przerywany 1180 Hz, tylko przy włączonym dźwięku).
- Wzmocnione drgania kamery przy niestabilnym spalaniu.

**Test:** oba scenariusze (awaria w locie, nieudany zapłon) kończą się
sekwencją „AWARIA → FDS: AWARYJNE WYŁĄCZENIE → SILNIK BEZPIECZNY";
baner widoczny; brak błędów.

---

## Iteracja 6 — Widok przekroju

**Pomysły:**
1. Widok przekroju (płaszczyzna tnąca) ✔ WYBRANY
2. Kamery predefiniowane + tryb kinowy
3. Szron na orurowaniu LOX
4. Tarcze zegarowe wskaźników
5. Eksport CSV
6. Warianty Raptor 1/2/3
7. Slew rate przepustnicy
8. Cienie
9. Panel „POMOC" + statystyki
10. Adaptacyjna rozdzielczość
11. Znaczniki zdarzeń na wykresach
12. Historia awarii z osobnym kolorem w sekwencji
13. Tryb „hard mode" — losowe awarie
14. Podświetlanie pary światłem pióropusza
15. NOWY: animowane przesuwanie płaszczyzny przekroju suwakiem
16. NOWY: etykiety części silnika (adnotacje 3D) w trybie przekroju

**Wybrano:** #1 — najciekawsza rzecz w silniku dzieje się w środku; przekrój
zamienia model w pomoc edukacyjną.

**Zrobione:**
- `main.js`: `renderer.localClippingEnabled`, płaszczyzna tnąca X=0 i przycisk
  PRZEKRÓJ. Materiały silnika są klonowane per-siatka przy pierwszym użyciu
  (materiały bazowe są współdzielone ze stanowiskiem — bez klonowania przekrój
  ciąłby też kratownicę); referencja materiału poświaty dyszy odtwarzana po
  klonowaniu przez `userData.glow`.
- `engineModel.js`: płyta wtryskiwacza (proceduralna tekstura pierścieni
  otworów) i zapalnik w komorze — widoczne tylko w przekroju.
- Pióropusz i cząstki pozostają niecięte.

**Test:** przekrój włącza/wyłącza się w locie, wnętrze komory i dzwonu
widoczne z poświatą, brak błędów.

---

## Iteracja 7 — Ujęcia kamery i tryb kinowy

**Pomysły:**
1. Predefiniowane ujęcia + tryb kinowy auto-orbit ✔ WYBRANY
2. Szron na orurowaniu LOX
3. Tarcze zegarowe wskaźników
4. Eksport CSV
5. Warianty Raptor 1/2/3
6. Slew rate przepustnicy
7. Cienie
8. Panel „POMOC" + statystyki
9. Adaptacyjna rozdzielczość
10. Znaczniki zdarzeń na wykresach
11. Suwak płaszczyzny przekroju
12. Etykiety części silnika w 3D
13. Hard mode z losowymi awariami
14. NOWY: zapis ujęcia użytkownika pod klawiszem (custom preset)
15. NOWY: tryb kinowy reagujący na fazę sekwencji (najazd przy zapłonie)

**Wybrano:** #1 — dobre ujęcia sprzedają całą resztę; ręczne ustawianie
kamery na „Z DOŁU" wymagało gimnastyki.

**Zrobione (`main.js` + UI):**
- Cztery presety (OGÓLNY / DYSZA / POMPY / Z DOŁU) z płynnym przelotem
  (smoothstep 1,3 s); `maxPolarAngle` poszerzony, żeby dało się zajrzeć
  w dyszę od dołu.
- TRYB KINOWY: automatyczna orbita z falowaniem wysokości; złapanie sceny
  myszą lub wybór presetu wyłącza go natychmiast.

**Test:** przelot do „Z DOŁU" podczas pracy (diamenty Macha z bliska),
tryb kinowy włącza się i wyłącza, brak błędów.

---

## Iteracja 8 — Szron na częściach kriogenicznych

**Pomysły:**
1. Szron na pompach i przewodach kriogenicznych ✔ WYBRANY
2. Tarcze zegarowe wskaźników
3. Eksport CSV
4. Warianty Raptor 1/2/3
5. Slew rate przepustnicy
6. Cienie
7. Panel „POMOC" + statystyki
8. Adaptacyjna rozdzielczość
9. Znaczniki zdarzeń na wykresach
10. Suwak płaszczyzny przekroju
11. Etykiety części 3D
12. Hard mode
13. Zapis własnego ujęcia kamery
14. NOWY: kapiące krople skroplin przy szronie (cząstki)
15. NOWY: wskaźnik temperatury części kriogenicznych w telemetrii

**Wybrano:** #1 — najbardziej charakterystyczny wizualny znak zatankowanego
silnika; razem z prechillem (iter. 3) domyka opowieść o kriogenice.

**Zrobione:**
- `simulation.js`: poziom `frost` 0..1 — narasta w SCHŁADZANIU (~2 s),
  utrzymuje się przy pracy, sublimuje po powrocie do GOTOWY.
- `engineModel.js`: `markCryo()` — obudowy i woluty obu pomp, kopułki,
  przewody zasilające i centralny kanał LOX dostają własne materiały
  (LOX szroni mocniej niż CH4).
- `main.js`: co klatkę lerp koloru do bieli + wzrost szorstkości / spadek
  metaliczności proporcjonalnie do szronu; rejestr materiałów odporny na
  klonowanie w trybie przekroju.

**Test:** po schładzaniu `frost = 0.90`, pompy matowo-białe w ujęciu POMPY,
komora bez zmian; brak błędów.

---

## Iteracja 9 — Tarcze zegarowe

**Pomysły:**
1. Zegarowe tarcze dla p. komory i obrotów pomp ✔ WYBRANY
2. Eksport CSV
3. Warianty Raptor 1/2/3
4. Slew rate przepustnicy
5. Cienie
6. Panel „POMOC" + statystyki
7. Adaptacyjna rozdzielczość
8. Znaczniki zdarzeń na wykresach
9. Suwak płaszczyzny przekroju
10. Etykiety części 3D
11. Hard mode
12. Zapis własnego ujęcia kamery
13. Kapiące skropliny przy szronie
14. Wskaźnik temperatur kriogenicznych
15. NOWY: tarcza ciągu zamiast dużej liczby (spójny rząd 4 tarcz)
16. NOWY: tryb ciemny/jasny HUD

**Wybrano:** #1 — paski słabo pokazują położenie względem redline;
tarcza z czerwonym polem robi to natychmiast, a przy okazji panel
telemetrii robi się bardziej „kokpitowy" i krótszy w pionie.

**Zrobione:**
- `charts.js`: klasa `DialGauge` — łuk 270° z torem, polem redline,
  wskazówką i wartością; przerysowanie tylko przy zmianie wartości.
- `hud.js` + `index.html`: rząd trzech tarcz (CIŚN. KOMORY / POMPA CH₄ /
  POMPA LOX ×1000 obr/min) zamiast trzech pasków; przekroczenie redline
  barwi łuk i wartość na czerwono (NADOBROTY to teraz widać na tarczy).

**Test:** tarcze rysują się i aktualizują, redline aktywne przy nadobrotach,
brak błędów.

---

## Iteracja 10 — Rejestrator telemetrii i eksport CSV

**Pomysły:**
1. Rejestrator telemetrii z eksportem CSV ✔ WYBRANY
2. Warianty Raptor 1/2/3
3. Slew rate przepustnicy
4. Cienie
5. Panel „POMOC" + statystyki
6. Adaptacyjna rozdzielczość
7. Znaczniki zdarzeń na wykresach
8. Suwak płaszczyzny przekroju
9. Etykiety części 3D
10. Hard mode
11. Zapis własnego ujęcia kamery
12. Kapiące skropliny
13. Wskaźnik temperatur kriogenicznych
14. Tarcza ciągu (czwarta)
15. NOWY: odtwarzanie zapisanego testu (replay) z pliku CSV
16. NOWY: porównanie dwóch przebiegów na jednym wykresie

**Wybrano:** #1 — zamyka pętlę „test → dane": każdy odpał można zabrać
do arkusza albo notebooka i przeanalizować.

**Zrobione:**
- Nowy moduł `recorder.js`: próbkowanie 10 Hz przez cały test (start przy
  wyjściu z GOTOWY, do 20 000 wierszy), kolumny: czas misji, stan, ciąg,
  p. komory, obroty obu pomp, przepływy, Isp, przepustnica, p. otoczenia,
  gimbal XY. Eksport przez Blob + link (nazwa `raptor3_test_NNN.csv`).
- Przycisk EKSPORT CSV z licznikiem próbek w panelu telemetrii,
  nieaktywny dopóki nie ma danych.

**Test:** nagłówek i wiersze CSV poprawne (2741,9 kN / 349,6 bar przy 100%),
pobranie `raptor3_test_001.csv` wyzwala się; brak błędów.

---

## Iteracja 11 — Warianty silnika Raptor 1 / 2 / 3

**Pomysły:**
1. Przełączane warianty R1/R2/R3 ✔ WYBRANY
2. Slew rate przepustnicy
3. Cienie
4. Panel „POMOC" + statystyki
5. Adaptacyjna rozdzielczość
6. Znaczniki zdarzeń na wykresach
7. Suwak płaszczyzny przekroju
8. Etykiety części 3D
9. Hard mode
10. Zapis własnego ujęcia kamery
11. Kapiące skropliny
12. Wskaźnik temperatur kriogenicznych
13. Replay z CSV
14. Porównanie dwóch przebiegów na wykresie
15. NOWY: tabela porównawcza wariantów w panelu POMOC

**Wybrano:** #1 — najlepiej pokazuje ewolucję silnika: R1 obrośnięty
orurowaniem przy 185 tf kontra „czysty" R3 przy 280 tf.

**Zrobione:**
- `simulation.js`: `VARIANTS` (ciąg SL, Isp, p. komory, masa — wartości
  przybliżone) i `setVariant()` mutujące wspólny obiekt `SPEC`;
  ciąg próżniowy przeliczany z pola wylotu dyszy.
- `engineModel.js`: dwie grupy zewnętrznego orurowania — R1 (gęste rurki
  wokół komory i dzwonu + skrzynka sterownika) i R2 (częściowe); R3 czysty.
- `main.js` + `hud.js` + UI: przyciski R1/R2/R3 (zablokowane w trakcie
  testu), nagłówek strony i tarcza p. komory przeskalowują się do wariantu
  (R1: redline przy 250 bar).

**Test:** R1 daje 1815 kN / 185 tf, przepływy i Isp spadają, orurowanie
widoczne, powrót do R3 przywraca 280 tf; brak błędów.

---

## Iteracja 12 — Slew rate przepustnicy

**Pomysły:**
1. Ogranicznik tempa zmian przepustnicy (zadana vs rzeczywista) ✔ WYBRANY
2. Cienie
3. Panel „POMOC" + statystyki
4. Adaptacyjna rozdzielczość
5. Znaczniki zdarzeń na wykresach
6. Suwak płaszczyzny przekroju
7. Etykiety części 3D
8. Hard mode
9. Zapis własnego ujęcia kamery
10. Kapiące skropliny
11. Wskaźnik temperatur kriogenicznych
12. Replay z CSV
13. Porównanie przebiegów
14. Tabela porównawcza wariantów
15. NOWY: krzywa odpowiedzi przepustnicy w CSV (kolumna zadanej)

**Wybrano:** #1 — natychmiastowa odpowiedź na suwak była najbardziej
„growym" uproszczeniem modelu; realny sterownik dławi z ograniczonym tempem.

**Zrobione:**
- `simulation.js`: rozdzielenie `throttleCmd` (zadana) od `throttle`
  (rzeczywista); ogranicznik 28 %/s w górę i 45 %/s w dół działa w każdej
  klatce przed logiką stanów.
- `hud.js`: etykieta suwaka pokazuje `zadana% → rzeczywista%` w trakcie
  przejścia, a po zrównaniu pojedynczą wartość.

**Test:** skok 100→40%: po 0,5 s rzeczywista 77,5% (dokładnie 45 %/s),
po 2,5 s równo 40%; etykieta przejściowa poprawna; brak błędów.

---

## Iteracja 13 — Cienie

**Pomysły:**
1. Shadow mapping dla silnika i stanowiska ✔ WYBRANY
2. Panel „POMOC" + statystyki
3. Adaptacyjna rozdzielczość
4. Znaczniki zdarzeń na wykresach
5. Suwak płaszczyzny przekroju
6. Etykiety części 3D
7. Hard mode
8. Zapis własnego ujęcia kamery
9. Kapiące skropliny
10. Wskaźnik temperatur kriogenicznych
11. Replay z CSV
12. Porównanie przebiegów
13. Tabela porównawcza wariantów
14. Kolumna zadanej przepustnicy w CSV
15. NOWY: migotanie cieni od światła pióropusza (drugie źródło z cieniem)

**Wybrano:** #1 — scena bez cieni wyglądała na „pływającą"; jeden reflektor
z mapą cieni kotwiczy bryły na płycie przy znośnym koszcie.

**Zrobione (`main.js`):**
- `PCFSoftShadowMap`, cień tylko z głównego reflektora (mapa 2048²,
  bias/normalBias dobrane przeciw acne).
- Silnik, kratownica i siłowniki rzucają i przyjmują cienie; płyta
  przyjmuje. Pominięte: shadery pióropusza, cząstki, sprite błysku
  i poświata wnętrza dyszy.

**Test:** długie miękkie cienie nóg i silnika na płycie, bez artefaktów,
brak błędów konsoli.

---

## Iteracja 14 — Panel POMOC i statystyki stanowiska

**Pomysły:**
1. Panel edukacyjny POMOC + trwałe statystyki testów ✔ WYBRANY
2. Adaptacyjna rozdzielczość
3. Znaczniki zdarzeń na wykresach
4. Suwak płaszczyzny przekroju
5. Etykiety części 3D
6. Hard mode
7. Zapis własnego ujęcia kamery
8. Kapiące skropliny
9. Wskaźnik temperatur kriogenicznych
10. Replay z CSV
11. Porównanie przebiegów
12. Kolumna zadanej przepustnicy w CSV
13. Migotanie cieni od pióropusza
14. NOWY: samouczek krok po kroku (onboarding) przy pierwszej wizycie
15. NOWY: reset statystyk przyciskiem w panelu POMOC

**Wybrano:** #1 — emulator ma walor edukacyjny, ale nigdzie nie tłumaczył,
czym jest FFSC ani co robią przyciski; statystyki dodają motywację do
kolejnych testów.

**Zrobione:**
- Nowy moduł `stats.js`: licznik testów, łączny czas pracy, zużyty propelent
  i liczba awaryjnych wyłączeń — zapisywane w `localStorage` (odporne na tryb
  prywatny przez try/catch), aktualizowane przy każdym powrocie do GOTOWY.
- Modal POMOC (`index.html` + `style.css` + `main.js`): opis silnika po
  polsku, instrukcja obsługi wszystkich funkcji, tabela porównawcza
  R1/R2/R3, sekcja statystyk; zamykanie przez ✕, tło i Escape.

**Test:** po dwóch testach statystyki `{tests:2, 15 s, 8,8 t}` przeżywają
przeładowanie strony; modal otwiera się i zamyka; brak błędów.
