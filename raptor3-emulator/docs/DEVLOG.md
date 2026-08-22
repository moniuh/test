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
