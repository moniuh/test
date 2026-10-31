# Zgodność: mapowanie wymogów na funkcje

> **To nie jest porada prawna — zweryfikuj z prawnikiem i aktualnym stanem prawnym.**
> Narzędzie pomaga wychwycić typowe ryzyka w treści ogłoszeń, ale nie gwarantuje zgodności. Przepisy krajowe
> wdrażające dyrektywę o jawności wynagrodzeń opisano bez numerów artykułów, których nie da się tu rzetelnie
> potwierdzić.

## 1. Jawność wynagrodzeń — dyrektywa (UE) 2023/970 i nowelizacja Kodeksu pracy

Dyrektywa (UE) 2023/970 (art. 5 — przejrzystość wynagrodzeń przed zatrudnieniem) została w Polsce wdrożona
nowelizacją Kodeksu pracy obowiązującą od **24 grudnia 2025 r.** (zweryfikuj aktualny stan prawny i brzmienie
przepisów krajowych).

| Wymóg | Reguła / funkcja | Uwagi |
|---|---|---|
| Informacja o wynagrodzeniu początkowym lub jego przedziale przed rozmową (art. 5 ust. 1) | `pay.missing` (błąd), `pay.vague` (ogólnik bez liczb), `pay.details` (brak waluty, brutto/netto, okresu) | Informację można przekazać w ogłoszeniu albo przed rozmową; deklaracja „widełki przekażemy przed rozmową” wyłącza `pay.missing`. Premie i świadczenia (karta sportowa) nie są traktowane jako wynagrodzenie. |
| Zakaz pytania o historię wynagrodzeń (art. 5 ust. 2) | `pay.history` | Pytanie o **oczekiwania finansowe** jest dozwolone i nie jest flagowane; zdania „nie pytamy o obecne wynagrodzenie” są wyjątkiem. |
| Neutralne płciowo ogłoszenia i nazwy stanowisk (art. 5 ust. 3) | `gender.title` (błąd), `gender.body` (sugestia) | Akceptowane: „(K/M)”, „(k/m/x)”, „(M/K)”, „(f/m/d)”, obie formy, „Księgowy/a”, „Asystent(ka)”. Angielskie nazwy (Developer, Manager) są neutralne. |
| Niedyskryminacyjna rekrutacja (art. 5 ust. 3) | reguły `disc.*` | Patrz sekcja 2. |

## 2. Równe traktowanie — Kodeks pracy art. 18³ᵃ

Art. 18³ᵃ § 1 KP: równe traktowanie w zakresie nawiązania stosunku pracy, w szczególności bez względu na płeć, wiek,
niepełnosprawność, rasę, religię, narodowość, przekonania polityczne, przynależność związkową, pochodzenie etniczne,
wyznanie, orientację seksualną, a także zatrudnienie na czas określony / nieokreślony i w pełnym / niepełnym
wymiarze.

| Cecha | Reguła | Przykłady wykrywane | Przykłady **niewykrywane** (dozwolone) |
|---|---|---|---|
| Wiek | `disc.age` (błąd), `disc.age_indirect` (ostrzeżenie) | „do 35 lat”, „w wieku 20–30 lat”, „urodzeni po 1990”, „młody zespół”, „młody duchem” | „młodszy specjalista”, „Junior”, „min. 3 lata doświadczenia”, „ukończone 18 lat”, data urodzenia |
| Płeć | `disc.gender` | „zatrudnimy mężczyznę”, „szukamy pani”, „mężczyźni mile widziani”, „płeć: …” | „kobiety i mężczyźni”, „program mentoringowy dla kobiet”, „Pani/Pana dane” |
| Rodzina / ciąża | `disc.family` | „bez zobowiązań rodzinnych”, „nie planuje ciąży”, „bez dzieci” | „dni wolne dla rodziców”, „praca z dziećmi” |
| Wygląd | `disc.appearance` | „miła aparycja”, „dobra prezencja”, „wzrost min. 170 cm” | „wygląd aplikacji”, „prezencja produktów”, „wzrost sprzedaży” |
| Narodowość / pochodzenie | `disc.nationality` | „native speaker”, „język ojczysty”, „tylko Polacy”, „bez akcentu” | „React Native”, „biegła znajomość (C2)”, obywatelstwo z prawem do pracy |
| Niepełnosprawność / zdrowie | `disc.health` | „dobry stan zdrowia”, „pełna sprawność fizyczna”, „niepaląca” | „brak przeciwwskazań do pracy na wysokości”, badania medycyny pracy, „sprawność systemów” |
| Religia / wyznanie | `disc.religion` | „praktykujący katolik”, „świadectwo chrztu” | „wierzący w sukces”, „praktykujący lekarz” (wyjątek dla organizacji religijnych — art. 18³ᵇ KP, zweryfikuj) |
| Poglądy, związki, orientacja, rasa | `disc.other` | „przynależność związkowa: brak”, „orientacja seksualna: …” | „orientacja na klienta” |
| Zdjęcie w CV | `disc.photo` | „CV ze zdjęciem”, „załącz aktualne zdjęcie” | „zdjęcie nie jest wymagane”, „obróbka zdjęć produktów” |

Wspólny wyjątek: klauzula równego traktowania w tym samym zdaniu („bez względu na wiek, płeć, wyznanie…”)
nie jest dyskryminacją.

Nie wykrywamy automatycznie dyskryminacji ze względu na rodzaj umowy lub wymiar etatu — to zwykle dotyczy
warunków zatrudnienia, a nie treści ogłoszenia; może to wychwycić przejście AI.

## 3. Zakres danych od kandydata — Kodeks pracy art. 22¹ i RODO

Art. 22¹ § 1 KP: od kandydata pracodawca żąda imienia i nazwiska, daty urodzenia, danych kontaktowych,
wykształcenia, kwalifikacji zawodowych i przebiegu dotychczasowego zatrudnienia (trzy ostatnie — gdy są niezbędne
do pracy na stanowisku). Inne dane — tylko gdy wymaga tego przepis szczególny albo na podstawie dobrowolnej zgody
(art. 22¹ᵃ KP).

| Wymóg | Reguła | Uwagi |
|---|---|---|
| Brak żądania danych spoza katalogu | `data.pesel`, `data.id_document`, `data.marital` (błędy), `data.other` (ostrzeżenie: imiona rodziców, nazwisko rodowe, nr konta, adres zameldowania, niekaralność, profile społecznościowe, grupa krwi) | **Data urodzenia nie jest flagowana.** Wyjątki: dane pobierane po zatrudnieniu („PESEL po podpisaniu umowy”), wymóg ustawowy (niekaralność „wymagana przez ustawę…”), zdania zaprzeczone („nie podawaj PESEL”). |
| Minimalizacja danych (RODO art. 5 ust. 1 lit. c) | jw. + `disc.photo` | |
| Obowiązek informacyjny (RODO art. 13) | `data.rodo_clause` (sugestia) | Wystarczy też link do klauzuli. |
| Nieaktualna podstawa | `data.outdated_consent` (sugestia) | Ustawa z 29.08.1997 r. nie obowiązuje od 25.05.2018 r. |

## 4. Przetwarzanie danych przez samo narzędzie (RODO)

| Zasada | Realizacja |
|---|---|
| Minimalizacja | przed wysłaniem do modelu maskowane są e-maile, telefony, PESEL i numery kont; model dostaje tylko treść ogłoszenia i listę wykrytych fragmentów |
| Ograniczenie przechowywania | brak bazy danych i zapisu treści; logi zawierają tylko komunikaty techniczne (bez treści ogłoszeń) |
| Integralność | dane przykładowe są fikcyjne; w trybie offline nic nie opuszcza serwera |
| Przejrzystość | każdy wynik ma pole `source` (`ai` / `offline`); UI pokazuje, które znaleziska pochodzą z AI i które cytaty AI odrzucono |

Ograniczenie: imiona i nazwiska osób kontaktowych w ogłoszeniu nie są maskowane — przy włączonym AI rozważ ich
usunięcie przed audytem lub umowę powierzenia z dostawcą modelu.

## 5. AI Act (rozporządzenie (UE) 2024/1689)

- Załącznik III pkt 4 obejmuje systemy AI do rekrutacji i wyboru, w szczególności do **umieszczania
  ukierunkowanych ogłoszeń o pracę**, analizy i filtrowania aplikacji oraz oceny kandydatów. Narzędzie **nie
  kieruje** ogłoszeń do odbiorców, nie analizuje aplikacji ani nie ocenia kandydatów — sprawdza i redaguje treść
  ogłoszenia, a decyzję podejmuje człowiek. Kwalifikację konkretnego wdrożenia oceń jednak indywidualnie
  (np. przy integracji z systemem targetowania ogłoszeń).
- **Harmonogram stosowania obowiązków dla systemów wysokiego ryzyka mógł zostać zmieniony (pakiet Digital
  Omnibus) — sprawdź aktualne terminy.**
- Niezależnie od klasyfikacji obowiązuje art. 4 (kompetencje w zakresie AI — szkolenie osób korzystających).
- Środki zaprojektowane w narzędziu: człowiek zatwierdza każdą zmianę, wynik liczy deterministyczny kod,
  cytaty AI są weryfikowane, wersja AI z nowymi kwotami jest odrzucana, prompt injection jest wykrywany i
  sygnalizowany, a każde źródło wyniku jest oznaczone.

## 6. Czego narzędzie nie robi

- Nie gwarantuje zgodności i nie zastępuje przeglądu prawnego.
- Nie ocenia, czy wymóg jest uzasadniony charakterem pracy (np. uprawnienia, badania, wymogi BHP) — wyjaśnienia
  podpowiadają, jak taki wymóg sformułować neutralnie.
- Nie sprawdza procesu rekrutacji poza treścią ogłoszenia (np. czy informacja o wynagrodzeniu faktycznie trafia
  do kandydata przed rozmową).
