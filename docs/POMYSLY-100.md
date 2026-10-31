# Radar Pracy — 100 pomysłów na rozwój i ranking

> Plik generowany przez `node docs/scripts/generuj-pomysly.mjs` z `docs/pomysly.json` i `docs/realizacja.json`.
> Nie edytuj go ręcznie.

## Model oceny

Każdy pomysł oceniony w skali 1–5 w pięciu wymiarach. Wynik = (0,30·W + 0,20·C + 0,25·K + 0,15·R + 0,10·Z) × 20 → 0–100. Remis rozstrzyga kolejno: W, C, K, numer pomysłu.

| Kod | Wymiar | Waga | 5 oznacza | 1 oznacza |
|---|---|---|---|---|
| W | Wartość | 30% | wyraźnie zwiększa szansę na pracę lub oszczędza godziny tygodniowo | kosmetyka, miło mieć |
| C | Częstotliwość | 20% | używane codziennie w trakcie szukania pracy | raz na całe poszukiwania |
| K | Wykonalność | 25% | ≤1 dzień, bez zewnętrznych zależności | tygodnie pracy, zewnętrzne API, umowy partnerskie |
| R | Wyróżnik | 15% | nie oferują tego portale ani arkusz kalkulacyjny | standard w każdym narzędziu |
| Z | Bezpieczeństwo | 10% | brak ryzyka prawnego i zależności od platform | zależność od regulaminów cudzych platform, dane wrażliwe |

Punktem wyjścia jest MVP: profil z CV, oferty wklejane jako tekst, dopasowanie
wymaganie po wymaganiu z cytatem z CV jako dowodem oraz prosty tracker aplikacji. Każdy z 100
pomysłów rozszerza to MVP. **50 najwyżej ocenionych zostało zaimplementowanych.**

## Podsumowanie wg kategorii

| Kategoria | Pomysłów | W top 50 | Średni wynik |
|---|---|---|---|
| A. Profil i CV | 12 | 6 | 71,9 |
| B. Pozyskiwanie i analiza ofert | 14 | 8 | 79,1 |
| C. Dopasowanie i rekomendacje | 12 | 9 | 81,7 |
| D. Aplikowanie i śledzenie | 14 | 9 | 77,3 |
| E. Przygotowanie do rozmów | 10 | 4 | 73,9 |
| F. Wynagrodzenia i negocjacje | 8 | 5 | 73,1 |
| G. Analityka i motywacja | 10 | 6 | 74,8 |
| H. Prywatność, bezpieczeństwo, dostępność | 8 | 2 | 72,8 |
| I. Platforma i integracje | 12 | 1 | 62,2 |

## Ranking — top 50 (zaimplementowane)

| # | Pomysł | Kat. | Oceny | Wynik | Gdzie w kodzie |
|---|---|---|---|---|---|
| 1 | **#18** Detektor czerwonych flag w ofercie | B | W5 C4 K5 R5 Z5 | **96,0** | _do zrobienia_ |
| 2 | **#28** Słownik synonimów i rodzin technologii | C | W5 C5 K5 R3 Z5 | **94,0** | _do zrobienia_ |
| 3 | **#31** Ranking ofert: dopasowanie × preferencje | C | W5 C5 K5 R3 Z5 | **94,0** | _do zrobienia_ |
| 4 | **#53** Generator pytań rekrutacyjnych z oferty | E | W5 C4 K5 R4 Z5 | **93,0** | _do zrobienia_ |
| 5 | **#72** Skuteczność wg źródła i wersji CV | G | W5 C3 K5 R5 Z5 | **92,0** | _do zrobienia_ |
| 6 | **#17** Normalizacja wynagrodzeń | B | W5 C4 K4 R5 Z5 | **91,0** | _do zrobienia_ |
| 7 | **#41** Przypomnienia o follow-upie | D | W5 C4 K5 R3 Z5 | **90,0** | _do zrobienia_ |
| 8 | **#71** Lejek rekrutacyjny | G | W5 C4 K5 R3 Z5 | **90,0** | _do zrobienia_ |
| 9 | **#13** Parser struktury oferty | B | W5 C5 K4 R3 Z5 | **89,0** | _do zrobienia_ |
| 10 | **#30** Luki kompetencyjne na tle rynku | C | W5 C3 K5 R4 Z5 | **89,0** | _do zrobienia_ |
| 11 | **#5** Dopasowanie CV do oferty (tailoring) | A | W5 C4 K4 R4 Z5 | **88,0** | _do zrobienia_ |
| 12 | **#24** Odrzucanie ofert z powodem | B | W4 C5 K5 R3 Z5 | **88,0** | _do zrobienia_ |
| 13 | **#19** Wykrywanie ofert-oszustw | B | W5 C3 K4 R5 Z5 | **87,0** | _do zrobienia_ |
| 14 | **#34** Wyjaśnienie wyniku dopasowania | C | W4 C4 K5 R4 Z5 | **87,0** | _do zrobienia_ |
| 15 | **#39** Tablica Kanban z przeciąganiem | D | W5 C5 K4 R2 Z5 | **86,0** | _do zrobienia_ |
| 16 | **#42** List motywacyjny oparty na dowodach | D | W5 C4 K4 R4 Z4 | **86,0** | _do zrobienia_ |
| 17 | **#63** Kalkulator netto UoP, B2B, zlecenie | F | W5 C4 K4 R4 Z4 | **86,0** | _do zrobienia_ |
| 18 | **#4** Audyt CV pod ATS | A | W5 C3 K5 R3 Z5 | **86,0** | _do zrobienia_ |
| 19 | **#27** Dopasowanie stopniowane | C | W4 C5 K4 R4 Z5 | **86,0** | _do zrobienia_ |
| 20 | **#2** Automatyczna ekstrakcja umiejętności z CV | A | W5 C4 K4 R3 Z5 | **85,0** | _do zrobienia_ |
| 21 | **#21** Wykrywanie trybu pracy i typu umowy | B | W4 C5 K5 R2 Z5 | **85,0** | _do zrobienia_ |
| 22 | **#32** Twarde filtry deal-breakerów | C | W4 C5 K5 R2 Z5 | **85,0** | _do zrobienia_ |
| 23 | **#73** Pulpit tygodniowy | G | W4 C5 K5 R2 Z5 | **85,0** | _do zrobienia_ |
| 24 | **#54** Ocena odpowiedzi metodą STAR | E | W5 C3 K4 R4 Z5 | **84,0** | _do zrobienia_ |
| 25 | **#64** Porównywarka ofert | F | W5 C3 K4 R4 Z5 | **84,0** | _do zrobienia_ |
| 26 | **#56** Bank historii STAR | E | W4 C3 K5 R4 Z5 | **83,0** | _do zrobienia_ |
| 27 | **#65** Statystyki widełek z zebranych ofert | F | W4 C3 K5 R4 Z5 | **83,0** | _do zrobienia_ |
| 28 | **#69** Ostrzeżenie o stawce poniżej rynku | F | W4 C3 K5 R4 Z5 | **83,0** | _do zrobienia_ |
| 29 | **#75** Trendy umiejętności w ofertach | G | W4 C3 K5 R4 Z5 | **83,0** | _do zrobienia_ |
| 30 | **#82** Maskowanie danych osobowych przed AI | H | W4 C3 K5 R4 Z5 | **83,0** | _do zrobienia_ |
| 31 | **#33** Zapisane wyszukiwania z alertami | C | W5 C4 K4 R2 Z5 | **82,0** | _do zrobienia_ |
| 32 | **#16** Deduplikacja ofert | B | W4 C4 K4 R4 Z5 | **82,0** | _do zrobienia_ |
| 33 | **#97** „Co dalej?” — następne kroki | I | W4 C4 K4 R4 Z5 | **82,0** | _do zrobienia_ |
| 34 | **#79** Raport aktywności poszukiwania pracy | G | W4 C2 K5 R5 Z5 | **82,0** | _do zrobienia_ |
| 35 | **#40** Oś czasu aplikacji | D | W4 C4 K5 R2 Z5 | **81,0** | _do zrobienia_ |
| 36 | **#87** Dostępność WCAG 2.2 AA | H | W4 C5 K4 R2 Z5 | **80,0** | _do zrobienia_ |
| 37 | **#3** Warianty CV pod różne role | A | W4 C3 K5 R3 Z5 | **80,0** | _do zrobienia_ |
| 38 | **#47** Ostrzeżenie o ponownej aplikacji | D | W4 C3 K5 R3 Z5 | **80,0** | _do zrobienia_ |
| 39 | **#62** Debrief po rozmowie | E | W4 C3 K5 R3 Z5 | **80,0** | _do zrobienia_ |
| 40 | **#36** Podobne oferty | C | W4 C4 K4 R3 Z5 | **79,0** | _do zrobienia_ |
| 41 | **#8** Wykrywanie zbędnych danych wrażliwych w CV | A | W4 C2 K5 R4 Z5 | **79,0** | _do zrobienia_ |
| 42 | **#14** Import oferty z adresu URL | B | W5 C5 K3 R3 Z2 | **78,0** | _do zrobienia_ |
| 43 | **#1** Import CV z PDF i DOCX | A | W5 C3 K4 R2 Z5 | **78,0** | _do zrobienia_ |
| 44 | **#35** Uczenie preferencji z ocen | C | W4 C4 K3 R4 Z5 | **77,0** | _do zrobienia_ |
| 45 | **#43** Szablony wiadomości | D | W4 C3 K5 R2 Z5 | **77,0** | _do zrobienia_ |
| 46 | **#45** Kalendarz rozmów z eksportem .ics | D | W4 C3 K5 R2 Z5 | **77,0** | _do zrobienia_ |
| 47 | **#52** Notatki z rozmów | D | W4 C3 K5 R2 Z5 | **77,0** | _do zrobienia_ |
| 48 | **#76** Dziennik samopoczucia | G | W3 C3 K5 R4 Z5 | **77,0** | _do zrobienia_ |
| 49 | **#46** Snapshot wysłanych dokumentów | D | W4 C2 K5 R3 Z5 | **76,0** | _do zrobienia_ |
| 50 | **#66** Asystent negocjacji | F | W4 C2 K5 R3 Z5 | **76,0** | _do zrobienia_ |

## Ranking — miejsca 51–100 (backlog)

| # | Pomysł | Kat. | Oceny | Wynik |
|---|---|---|---|---|
| 51 | #83 Detektor prompt injection w ofertach | H | W3 C2 K5 R5 Z5 | 76,0 |
| 52 | #51 Rozszerzenie przeglądarki „zapisz ofertę” | D | W5 C5 K2 R3 Z3 | 75,0 |
| 53 | #55 Symulator rozmowy z AI | E | W5 C3 K3 R4 Z3 | 75,0 |
| 54 | #20 Klasyfikacja poziomu stanowiska | B | W3 C4 K5 R2 Z5 | 75,0 |
| 55 | #48 Cel tygodniowy i seria | D | W3 C4 K5 R2 Z5 | 75,0 |
| 56 | #96 Interfejs PL/EN | I | W3 C5 K4 R2 Z5 | 74,0 |
| 57 | #29 Edytowalne wagi wymagań | C | W3 C3 K5 R3 Z5 | 74,0 |
| 58 | #98 Kreator pierwszego uruchomienia | I | W4 C2 K5 R2 Z5 | 73,0 |
| 59 | #81 Dziennik wywołań AI | H | W3 C2 K5 R4 Z5 | 73,0 |
| 60 | #50 Automatyczne statusy z poczty | D | W5 C4 K2 R4 Z2 | 72,0 |
| 61 | #84 Eksport, import i usunięcie danych | H | W4 C1 K5 R3 Z5 | 72,0 |
| 62 | #23 Tagi i notatki przy ofertach | B | W3 C4 K5 R1 Z5 | 72,0 |
| 63 | #90 PWA na telefon | I | W4 C4 K3 R2 Z5 | 71,0 |
| 64 | #22 Wygasanie ofert i archiwizacja | B | W3 C3 K5 R2 Z5 | 71,0 |
| 65 | #57 Pytania do rekrutera | E | W3 C3 K5 R2 Z5 | 71,0 |
| 66 | #58 Checklista przed rozmową | E | W3 C3 K5 R2 Z5 | 71,0 |
| 67 | #15 Subskrypcja kanałów RSS/JSON z ofertami | B | W4 C4 K3 R3 Z3 | 70,0 |
| 68 | #38 Dopasowanie semantyczne przez AI | C | W4 C4 K3 R3 Z3 | 70,0 |
| 69 | #7 Klauzula RODO w CV | A | W3 C2 K5 R3 Z5 | 70,0 |
| 70 | #74 Czas odpowiedzi firm | G | W3 C2 K5 R3 Z5 | 70,0 |
| 71 | #88 Tryb ciemny | H | W2 C5 K5 R1 Z5 | 70,0 |
| 72 | #85 Szyfrowanie danych w spoczynku | H | W3 C5 K3 R2 Z5 | 69,0 |
| 73 | #25 Profile firm | B | W3 C3 K4 R3 Z5 | 69,0 |
| 74 | #59 Fiszki z luk kompetencyjnych | E | W3 C3 K4 R3 Z5 | 69,0 |
| 75 | #67 Wycena benefitów | F | W3 C2 K5 R3 Z4 | 68,0 |
| 76 | #68 Koszt i czas dojazdu | F | W3 C2 K5 R2 Z5 | 67,0 |
| 77 | #44 Mini-CRM kontaktów rekruterów | D | W3 C3 K4 R2 Z5 | 66,0 |
| 78 | #91 Powiadomienia w przeglądarce i e-mail | I | W4 C3 K3 R2 Z4 | 65,0 |
| 79 | #12 Historia wersji CV z porównaniem | A | W3 C2 K4 R3 Z5 | 65,0 |
| 80 | #49 Import i eksport CSV | D | W3 C2 K5 R1 Z5 | 64,0 |
| 81 | #6 Eksport CV do PDF z szablonu | A | W4 C2 K3 R2 Z5 | 63,0 |
| 82 | #37 Role sąsiednie i ścieżki zmiany kariery | C | W3 C2 K3 R4 Z5 | 63,0 |
| 83 | #9 Mapa umiejętności z poziomami | A | W3 C2 K4 R2 Z5 | 62,0 |
| 84 | #61 Stoper odpowiedzi | E | W2 C3 K5 R1 Z5 | 62,0 |
| 85 | #78 Heatmapa aktywności | G | W2 C3 K5 R1 Z5 | 62,0 |
| 86 | #92 Docker Compose z PostgreSQL | I | W3 C1 K5 R1 Z5 | 60,0 |
| 87 | #99 Dane demonstracyjne i reset | I | W3 C1 K5 R1 Z5 | 60,0 |
| 88 | #86 Konta użytkowników i logowanie | H | W3 C5 K2 R1 Z4 | 59,0 |
| 89 | #100 Plan nauki z luk | I | W3 C2 K3 R3 Z4 | 58,0 |
| 90 | #94 Marketplace mentorów | I | W4 C2 K1 R4 Z3 | 55,0 |
| 91 | #26 Mapa ofert i czas dojazdu | B | W3 C3 K2 R3 Z3 | 55,0 |
| 92 | #77 Cotygodniowe podsumowanie e-mail | G | W3 C2 K3 R2 Z4 | 55,0 |
| 93 | #10 Linki portfolio z walidacją | A | W2 C1 K5 R1 Z5 | 54,0 |
| 94 | #89 Dokumentacja API (OpenAPI) | I | W2 C1 K5 R1 Z5 | 54,0 |
| 95 | #11 Tłumaczenie CV PL↔EN przez AI | A | W3 C2 K3 R2 Z3 | 53,0 |
| 96 | #80 Anonimowy benchmark z innymi | G | W3 C2 K2 R4 Z2 | 52,0 |
| 97 | #60 Nagrywanie i transkrypcja odpowiedzi | E | W3 C2 K2 R3 Z3 | 51,0 |
| 98 | #95 Import profilu z LinkedIn | I | W4 C2 K1 R3 Z1 | 48,0 |
| 99 | #93 Synchronizacja z Kalendarzem Google | I | W3 C2 K2 R2 Z2 | 46,0 |
| 100 | #70 Raport płacowy z danych publicznych | F | W2 C1 K2 R2 Z3 | 38,0 |

## Karty pomysłów

### A. Profil i CV

- ✅ **#1. Import CV z PDF i DOCX** — Wgranie pliku CV, ekstrakcja tekstu (PDFBox, Apache POI) i zapis jako nowa wersja CV. _(W5 C3 K4 R2 Z5 → 78,0, miejsce 43)_
- ✅ **#2. Automatyczna ekstrakcja umiejętności z CV** — Słownik technologii i kompetencji z synonimami rozpoznaje umiejętności w CV i proponuje je do profilu, z latami doświadczenia, jeśli są w tekście. _(W5 C4 K4 R3 Z5 → 85,0, miejsce 20)_
- ✅ **#3. Warianty CV pod różne role** — Kilka wersji CV (np. Backend, Fullstack) z wyborem aktywnej; dopasowanie liczy się względem wybranej wersji. _(W4 C3 K5 R3 Z5 → 80,0, miejsce 37)_
- ✅ **#4. Audyt CV pod ATS** — Kontrola sekcji, długości, danych kontaktowych, czasowników akcji i mierzalnych efektów; wynik 0–100 z listą poprawek. _(W5 C3 K5 R3 Z5 → 86,0, miejsce 18)_
- ✅ **#5. Dopasowanie CV do oferty (tailoring)** — Lista słów kluczowych z oferty, których brakuje w CV, ale które kandydat ma w profilu, z gotową propozycją zdania. _(W5 C4 K4 R4 Z5 → 88,0, miejsce 11)_
- ⬜ **#6. Eksport CV do PDF z szablonu** — Generowanie estetycznego PDF z danych profilu w kilku szablonach. _(W4 C2 K3 R2 Z5 → 63,0, miejsce 81)_
- ⬜ **#7. Klauzula RODO w CV** — Sprawdzenie obecności klauzuli zgody na przetwarzanie danych i generator aktualnej treści. _(W3 C2 K5 R3 Z5 → 70,0, miejsce 69)_
- ✅ **#8. Wykrywanie zbędnych danych wrażliwych w CV** — Ostrzeżenia o PESEL, dacie urodzenia, stanie cywilnym, zdjęciu i innych danych, których pracodawca nie potrzebuje. _(W4 C2 K5 R4 Z5 → 79,0, miejsce 41)_
- ⬜ **#9. Mapa umiejętności z poziomami** — Wizualizacja umiejętności z poziomem biegłości i latami doświadczenia. _(W3 C2 K4 R2 Z5 → 62,0, miejsce 83)_
- ⬜ **#10. Linki portfolio z walidacją** — GitHub, LinkedIn i strona osobista z kontrolą poprawności adresów. _(W2 C1 K5 R1 Z5 → 54,0, miejsce 93)_
- ⬜ **#11. Tłumaczenie CV PL↔EN przez AI** — Wersja językowa CV generowana przez model z zachowaniem terminologii. _(W3 C2 K3 R2 Z3 → 53,0, miejsce 95)_
- ⬜ **#12. Historia wersji CV z porównaniem** — Wersjonowanie treści CV i podgląd różnic między wersjami. _(W3 C2 K4 R3 Z5 → 65,0, miejsce 79)_

### B. Pozyskiwanie i analiza ofert

- ✅ **#13. Parser struktury oferty** — Z wklejonego tekstu: stanowisko, firma, lokalizacja, widełki, wymagania obowiązkowe i mile widziane. _(W5 C5 K4 R3 Z5 → 89,0, miejsce 9)_
- ✅ **#14. Import oferty z adresu URL** — Pobranie strony ogłoszenia na żądanie użytkownika i ekstrakcja treści do parsera. _(W5 C5 K3 R3 Z2 → 78,0, miejsce 42)_
- ⬜ **#15. Subskrypcja kanałów RSS/JSON z ofertami** — Cykliczny import ofert z kanałów udostępnianych przez portale i firmy. _(W4 C4 K3 R3 Z3 → 70,0, miejsce 67)_
- ✅ **#16. Deduplikacja ofert** — Wykrywanie tej samej oferty opublikowanej w kilku źródłach (podobieństwo tytułu, firmy i treści). _(W4 C4 K4 R4 Z5 → 82,0, miejsce 32)_
- ✅ **#17. Normalizacja wynagrodzeń** — Sprowadzenie widełek godzinowych, dziennych, miesięcznych i rocznych, brutto, netto i B2B do wspólnej miary miesięcznej. _(W5 C4 K4 R5 Z5 → 91,0, miejsce 6)_
- ✅ **#18. Detektor czerwonych flag w ofercie** — Brak widełek, nieodpłatny okres próbny, umowa o dzieło na stałą pracę, „rodzinna atmosfera” i inne sygnały ostrzegawcze z uzasadnieniem. _(W5 C4 K5 R5 Z5 → 96,0, miejsce 1)_
- ✅ **#19. Wykrywanie ofert-oszustw** — Prośby o opłaty, przelewy, przyjmowanie paczek, dane dokumentów na starcie i komunikatory zamiast rozmowy. _(W5 C3 K4 R5 Z5 → 87,0, miejsce 13)_
- ⬜ **#20. Klasyfikacja poziomu stanowiska** — Junior, mid lub senior na podstawie treści ogłoszenia i wymaganych lat doświadczenia. _(W3 C4 K5 R2 Z5 → 75,0, miejsce 54)_
- ✅ **#21. Wykrywanie trybu pracy i typu umowy** — Zdalna, hybrydowa, stacjonarna; UoP, B2B, zlecenie, dzieło — rozpoznane z treści. _(W4 C5 K5 R2 Z5 → 85,0, miejsce 21)_
- ⬜ **#22. Wygasanie ofert i archiwizacja** — Data ważności ogłoszenia i automatyczne przenoszenie przeterminowanych ofert do archiwum. _(W3 C3 K5 R2 Z5 → 71,0, miejsce 64)_
- ⬜ **#23. Tagi i notatki przy ofertach** — Własne etykiety i notatki do filtrowania ofert. _(W3 C4 K5 R1 Z5 → 72,0, miejsce 62)_
- ✅ **#24. Odrzucanie ofert z powodem** — Odrzucenie jednym kliknięciem z powodem (wynagrodzenie, lokalizacja, technologia), który zasila ranking. _(W4 C5 K5 R3 Z5 → 88,0, miejsce 12)_
- ⬜ **#25. Profile firm** — Agregacja ofert i aplikacji po firmie, notatki i własna ocena pracodawcy. _(W3 C3 K4 R3 Z5 → 69,0, miejsce 73)_
- ⬜ **#26. Mapa ofert i czas dojazdu** — Geokodowanie adresów i czas dojazdu z domu. _(W3 C3 K2 R3 Z3 → 55,0, miejsce 91)_

### C. Dopasowanie i rekomendacje

- ✅ **#27. Dopasowanie stopniowane** — Częściowe trafienia: brakujące lata doświadczenia, poziom języka, pokrewna technologia z tej samej rodziny. _(W4 C5 K4 R4 Z5 → 86,0, miejsce 19)_
- ✅ **#28. Słownik synonimów i rodzin technologii** — React.js = React, k8s = Kubernetes, Postgres = PostgreSQL; rodziny (bazy SQL, chmury) dają częściowe dopasowanie. _(W5 C5 K5 R3 Z5 → 94,0, miejsce 2)_
- ⬜ **#29. Edytowalne wagi wymagań** — Użytkownik decyduje, które wymagania liczą się bardziej w wyniku. _(W3 C3 K5 R3 Z5 → 74,0, miejsce 57)_
- ✅ **#30. Luki kompetencyjne na tle rynku** — Które umiejętności najczęściej blokują dopasowanie w zebranych ofertach i ile ofert odblokowałaby nauka każdej z nich. _(W5 C3 K5 R4 Z5 → 89,0, miejsce 10)_
- ✅ **#31. Ranking ofert: dopasowanie × preferencje** — Kolejność ofert wg dopasowania, wynagrodzenia, trybu pracy i lokalizacji, z rozbiciem na składniki. _(W5 C5 K5 R3 Z5 → 94,0, miejsce 3)_
- ✅ **#32. Twarde filtry deal-breakerów** — Wykluczenie ofert z wykluczającymi warunkami (np. tylko praca w biurze, wynagrodzenie poniżej minimum, słowa zakazane). _(W4 C5 K5 R2 Z5 → 85,0, miejsce 22)_
- ✅ **#33. Zapisane wyszukiwania z alertami** — Zapis filtrów i licznik nowych ofert spełniających kryteria od ostatniego sprawdzenia. _(W5 C4 K4 R2 Z5 → 82,0, miejsce 31)_
- ✅ **#34. Wyjaśnienie wyniku dopasowania** — „Dlaczego 72%”: wkład każdego wymagania w punktach, z cytatem z CV jako dowodem. _(W4 C4 K5 R4 Z5 → 87,0, miejsce 14)_
- ✅ **#35. Uczenie preferencji z ocen** — Polubione i odrzucone oferty korygują wagi cech w rankingu (np. technologie, tryb pracy). _(W4 C4 K3 R4 Z5 → 77,0, miejsce 44)_
- ✅ **#36. Podobne oferty** — TF-IDF i podobieństwo cosinusowe — „więcej takich ofert”. _(W4 C4 K4 R3 Z5 → 79,0, miejsce 40)_
- ⬜ **#37. Role sąsiednie i ścieżki zmiany kariery** — Podpowiedzi stanowisk o dużym pokryciu umiejętności (np. QA → SDET). _(W3 C2 K3 R4 Z5 → 63,0, miejsce 82)_
- ⬜ **#38. Dopasowanie semantyczne przez AI** — Druga opinia modelu językowego dla wymagań, których nie rozpoznał słownik. _(W4 C4 K3 R3 Z3 → 70,0, miejsce 68)_

### D. Aplikowanie i śledzenie

- ✅ **#39. Tablica Kanban z przeciąganiem** — Aplikacje w kolumnach statusów, zmiana statusu przez przeciągnięcie lub klawiaturą. _(W5 C5 K4 R2 Z5 → 86,0, miejsce 15)_
- ✅ **#40. Oś czasu aplikacji** — Historia zmian statusów, rozmów, wiadomości i notatek w jednym widoku. _(W4 C4 K5 R2 Z5 → 81,0, miejsce 35)_
- ✅ **#41. Przypomnienia o follow-upie** — Automatyczny termin kontaktu (np. 7 dni bez odpowiedzi) i lista zaległych follow-upów. _(W5 C4 K5 R3 Z5 → 90,0, miejsce 7)_
- ✅ **#42. List motywacyjny oparty na dowodach** — List budowany z cytatów CV dopasowanych do wymagań oferty; tryb offline z szablonu i opcjonalnie AI. _(W5 C4 K4 R4 Z4 → 86,0, miejsce 16)_
- ✅ **#43. Szablony wiadomości** — Follow-up, podziękowanie po rozmowie, prośba o informację zwrotną, odmowa i przyjęcie oferty — z podstawieniem danych. _(W4 C3 K5 R2 Z5 → 77,0, miejsce 45)_
- ⬜ **#44. Mini-CRM kontaktów rekruterów** — Baza osób, z którymi kandydat rozmawiał, z historią kontaktów. _(W3 C3 K4 R2 Z5 → 66,0, miejsce 77)_
- ✅ **#45. Kalendarz rozmów z eksportem .ics** — Terminy rozmów przy aplikacjach i eksport do dowolnego kalendarza. _(W4 C3 K5 R2 Z5 → 77,0, miejsce 46)_
- ✅ **#46. Snapshot wysłanych dokumentów** — Zapis dokładnej treści CV i listu wysłanych do firmy w chwili aplikowania. _(W4 C2 K5 R3 Z5 → 76,0, miejsce 49)_
- ✅ **#47. Ostrzeżenie o ponownej aplikacji** — Informacja, że kandydat już aplikował do tej firmy lub na tę samą rolę, z datą i wynikiem. _(W4 C3 K5 R3 Z5 → 80,0, miejsce 38)_
- ⬜ **#48. Cel tygodniowy i seria** — Liczba aplikacji na tydzień i licznik tygodni z osiągniętym celem. _(W3 C4 K5 R2 Z5 → 75,0, miejsce 55)_
- ⬜ **#49. Import i eksport CSV** — Przeniesienie trackera z arkusza kalkulacyjnego i z powrotem. _(W3 C2 K5 R1 Z5 → 64,0, miejsce 80)_
- ⬜ **#50. Automatyczne statusy z poczty** — Rozpoznawanie odpowiedzi firm w skrzynce Gmail i aktualizacja statusu. _(W5 C4 K2 R4 Z2 → 72,0, miejsce 60)_
- ⬜ **#51. Rozszerzenie przeglądarki „zapisz ofertę”** — Jedno kliknięcie na stronie portalu zapisuje ofertę w aplikacji. _(W5 C5 K2 R3 Z3 → 75,0, miejsce 52)_
- ✅ **#52. Notatki z rozmów** — Osoby prowadzące, zadane pytania, wrażenia i ustalenia przy każdej rozmowie. _(W4 C3 K5 R2 Z5 → 77,0, miejsce 47)_

### E. Przygotowanie do rozmów

- ✅ **#53. Generator pytań rekrutacyjnych z oferty** — Pytania techniczne i behawioralne wyprowadzone z wymagań konkretnej oferty, z luką kompetencyjną na pierwszym miejscu. _(W5 C4 K5 R4 Z5 → 93,0, miejsce 4)_
- ✅ **#54. Ocena odpowiedzi metodą STAR** — Heurystyki sprawdzają sytuację, zadanie, działanie, rezultat, liczby i długość odpowiedzi. _(W5 C3 K4 R4 Z5 → 84,0, miejsce 24)_
- ⬜ **#55. Symulator rozmowy z AI** — Model w roli rekrutera prowadzi rozmowę na podstawie oferty. _(W5 C3 K3 R4 Z3 → 75,0, miejsce 53)_
- ✅ **#56. Bank historii STAR** — Własne osiągnięcia zapisane raz i podpowiadane do pytań oraz wymagań. _(W4 C3 K5 R4 Z5 → 83,0, miejsce 26)_
- ⬜ **#57. Pytania do rekrutera** — Generator pytań, które kandydat zadaje firmie, zależnie od etapu. _(W3 C3 K5 R2 Z5 → 71,0, miejsce 65)_
- ⬜ **#58. Checklista przed rozmową** — Research firmy, sprzęt, dojazd, dokumenty. _(W3 C3 K5 R2 Z5 → 71,0, miejsce 66)_
- ⬜ **#59. Fiszki z luk kompetencyjnych** — Materiał do powtórek generowany z brakujących umiejętności. _(W3 C3 K4 R3 Z5 → 69,0, miejsce 74)_
- ⬜ **#60. Nagrywanie i transkrypcja odpowiedzi** — Ćwiczenie odpowiedzi głosowych z zapisem tekstu. _(W3 C2 K2 R3 Z3 → 51,0, miejsce 97)_
- ⬜ **#61. Stoper odpowiedzi** — Limit czasu na odpowiedź w trybie ćwiczeń. _(W2 C3 K5 R1 Z5 → 62,0, miejsce 84)_
- ✅ **#62. Debrief po rozmowie** — Co poszło dobrze, co poprawić, jakie padły pytania; wnioski zbierane między rozmowami. _(W4 C3 K5 R3 Z5 → 80,0, miejsce 39)_

### F. Wynagrodzenia i negocjacje

- ✅ **#63. Kalkulator netto UoP, B2B, zlecenie** — Szacunek kwoty „na rękę” z parametrami podatkowymi na dany rok zapisanymi w jednym miejscu. _(W5 C4 K4 R4 Z4 → 86,0, miejsce 17)_
- ✅ **#64. Porównywarka ofert** — Zestawienie kilku ofert: netto, benefity, dni wolne, dojazd — z wartością całkowitą. _(W5 C3 K4 R4 Z5 → 84,0, miejsce 25)_
- ✅ **#65. Statystyki widełek z zebranych ofert** — Mediana i rozrzut wynagrodzeń wg poziomu i typu umowy. _(W4 C3 K5 R4 Z5 → 83,0, miejsce 27)_
- ✅ **#66. Asystent negocjacji** — Kotwica, kontroferta i gotowe sformułowania na podstawie oferty i danych rynkowych. _(W4 C2 K5 R3 Z5 → 76,0, miejsce 50)_
- ⬜ **#67. Wycena benefitów** — Wartość pieniężna karty sportowej, opieki medycznej, PPK i budżetu szkoleniowego. _(W3 C2 K5 R3 Z4 → 68,0, miejsce 75)_
- ⬜ **#68. Koszt i czas dojazdu** — Miesięczny koszt i czas dojazdu w porównaniu ofert. _(W3 C2 K5 R2 Z5 → 67,0, miejsce 76)_
- ✅ **#69. Ostrzeżenie o stawce poniżej rynku** — Porównanie widełek oferty z medianą podobnych ofert. _(W4 C3 K5 R4 Z5 → 83,0, miejsce 28)_
- ⬜ **#70. Raport płacowy z danych publicznych** — Integracja z danymi GUS o wynagrodzeniach w zawodach. _(W2 C1 K2 R2 Z3 → 38,0, miejsce 100)_

### G. Analityka i motywacja

- ✅ **#71. Lejek rekrutacyjny** — Aplikacje → odpowiedzi → rozmowy → oferty, z konwersją między etapami. _(W5 C4 K5 R3 Z5 → 90,0, miejsce 8)_
- ✅ **#72. Skuteczność wg źródła i wersji CV** — Odsetek odpowiedzi i rozmów dla każdego źródła oferty i wariantu CV (test A/B CV). _(W5 C3 K5 R5 Z5 → 92,0, miejsce 5)_
- ✅ **#73. Pulpit tygodniowy** — Aktywność w bieżącym tygodniu względem celu, najbliższe rozmowy i najlepsze nowe oferty. _(W4 C5 K5 R2 Z5 → 85,0, miejsce 23)_
- ⬜ **#74. Czas odpowiedzi firm** — Średni czas od aplikacji do pierwszej odpowiedzi. _(W3 C2 K5 R3 Z5 → 70,0, miejsce 70)_
- ✅ **#75. Trendy umiejętności w ofertach** — Najczęściej wymagane technologie w zebranych ofertach i ich pokrycie przez profil. _(W4 C3 K5 R4 Z5 → 83,0, miejsce 29)_
- ✅ **#76. Dziennik samopoczucia** — Krótki wpis nastroju i energii; ostrzeżenie przy spadku i sugestia przerwy. _(W3 C3 K5 R4 Z5 → 77,0, miejsce 48)_
- ⬜ **#77. Cotygodniowe podsumowanie e-mail** — Raport aktywności wysyłany pocztą. _(W3 C2 K3 R2 Z4 → 55,0, miejsce 92)_
- ⬜ **#78. Heatmapa aktywności** — Kalendarz dni z aplikacjami i rozmowami. _(W2 C3 K5 R1 Z5 → 62,0, miejsce 85)_
- ✅ **#79. Raport aktywności poszukiwania pracy** — Zestawienie aplikacji i rozmów do wydruku lub CSV, np. dla doradcy kariery lub urzędu pracy. _(W4 C2 K5 R5 Z5 → 82,0, miejsce 34)_
- ⬜ **#80. Anonimowy benchmark z innymi** — Porównanie własnych wskaźników ze zagregowanymi danymi innych użytkowników. _(W3 C2 K2 R4 Z2 → 52,0, miejsce 96)_

### H. Prywatność, bezpieczeństwo, dostępność

- ⬜ **#81. Dziennik wywołań AI** — Co, kiedy i w jakiej postaci (po maskowaniu) zostało wysłane do modelu. _(W3 C2 K5 R4 Z5 → 73,0, miejsce 59)_
- ✅ **#82. Maskowanie danych osobowych przed AI** — Imię i nazwisko, e-mail, telefon, PESEL i adres zastępowane znacznikami przed wysłaniem do modelu i przywracane w wyniku. _(W4 C3 K5 R4 Z5 → 83,0, miejsce 30)_
- ⬜ **#83. Detektor prompt injection w ofertach** — Wykrywanie ukrytych poleceń dla AI w treści ogłoszeń. _(W3 C2 K5 R5 Z5 → 76,0, miejsce 51)_
- ⬜ **#84. Eksport, import i usunięcie danych** — Kopia zapasowa całości w JSON i trwałe usunięcie danych (RODO). _(W4 C1 K5 R3 Z5 → 72,0, miejsce 61)_
- ⬜ **#85. Szyfrowanie danych w spoczynku** — Szyfrowana baza danych z hasłem użytkownika. _(W3 C5 K3 R2 Z5 → 69,0, miejsce 72)_
- ⬜ **#86. Konta użytkowników i logowanie** — Tryb wieloosobowy z uwierzytelnianiem i separacją danych. _(W3 C5 K2 R1 Z4 → 59,0, miejsce 88)_
- ✅ **#87. Dostępność WCAG 2.2 AA** — Pełna obsługa klawiaturą, etykiety ARIA, kontrast, widoczny fokus, link „przejdź do treści”. _(W4 C5 K4 R2 Z5 → 80,0, miejsce 36)_
- ⬜ **#88. Tryb ciemny** — Motyw ciemny zgodny z ustawieniem systemu. _(W2 C5 K5 R1 Z5 → 70,0, miejsce 71)_

### I. Platforma i integracje

- ⬜ **#89. Dokumentacja API (OpenAPI)** — Interaktywna specyfikacja REST. _(W2 C1 K5 R1 Z5 → 54,0, miejsce 94)_
- ⬜ **#90. PWA na telefon** — Instalowalna aplikacja z pracą offline. _(W4 C4 K3 R2 Z5 → 71,0, miejsce 63)_
- ⬜ **#91. Powiadomienia w przeglądarce i e-mail** — Alerty o follow-upach i nowych dopasowaniach poza aplikacją. _(W4 C3 K3 R2 Z4 → 65,0, miejsce 78)_
- ⬜ **#92. Docker Compose z PostgreSQL** — Gotowe wdrożenie z bazą produkcyjną. _(W3 C1 K5 R1 Z5 → 60,0, miejsce 86)_
- ⬜ **#93. Synchronizacja z Kalendarzem Google** — Dwukierunkowa synchronizacja terminów rozmów. _(W3 C2 K2 R2 Z2 → 46,0, miejsce 99)_
- ⬜ **#94. Marketplace mentorów** — Płatna recenzja CV i próbna rozmowa z człowiekiem. _(W4 C2 K1 R4 Z3 → 55,0, miejsce 90)_
- ⬜ **#95. Import profilu z LinkedIn** — Pobranie doświadczenia i umiejętności z profilu LinkedIn. _(W4 C2 K1 R3 Z1 → 48,0, miejsce 98)_
- ⬜ **#96. Interfejs PL/EN** — Przełączanie języka interfejsu. _(W3 C5 K4 R2 Z5 → 74,0, miejsce 56)_
- ✅ **#97. „Co dalej?” — następne kroki** — Lista najważniejszych działań na dziś wyliczona z danych (follow-upy, rozmowy, dobre oferty bez aplikacji, debriefy). _(W4 C4 K4 R4 Z5 → 82,0, miejsce 33)_
- ⬜ **#98. Kreator pierwszego uruchomienia** — Profil, CV i preferencje w trzech krokach. _(W4 C2 K5 R2 Z5 → 73,0, miejsce 58)_
- ⬜ **#99. Dane demonstracyjne i reset** — Przykładowy profil i oferty do wypróbowania aplikacji oraz przywrócenie stanu początkowego. _(W3 C1 K5 R1 Z5 → 60,0, miejsce 87)_
- ⬜ **#100. Plan nauki z luk** — Zestaw zasobów do nauki dla brakujących umiejętności. _(W3 C2 K3 R3 Z4 → 58,0, miejsce 89)_
