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
