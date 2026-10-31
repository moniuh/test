# Zgodność: RODO, AI Act, Kodeks pracy

> **To nie jest porada prawna — zweryfikuj z prawnikiem i aktualnym stanem prawnym.**
> Dokument pokazuje, które wymogi produkt wspiera technicznie, a które pozostają po stronie organizacji.

## Kwalifikacja

- **AI Act** (rozp. (UE) 2024/1689): system „przeznaczony do rekrutacji lub wyboru osób fizycznych, w szczególności
  do (…) analizowania i filtrowania podań o pracę oraz do oceny kandydatów” to system **wysokiego ryzyka**
  (art. 6 ust. 2 w zw. z zał. III pkt 4 lit. a). Wyjątek z art. 6 ust. 3 nie ma zastosowania, bo ocena CV
  to profilowanie osób fizycznych.
- **Role**: firma, która sama buduje i używa narzędzia, jest jednocześnie *dostawcą* (art. 16) i *podmiotem
  stosującym* (art. 26). Firma kupująca gotowe narzędzie jest podmiotem stosującym.
- **Terminy**: zakazy z art. 5 i obowiązek kompetencji w zakresie AI (art. 4) stosuje się od 2 lutego 2025 r.
  Obowiązki dla systemów wysokiego ryzyka z zał. III miały być stosowane od 2 sierpnia 2026 r. (art. 113).
  **Harmonogram mógł zostać zmieniony (pakiet Digital Omnibus, który proponował przesunięcie terminów
  dla systemów wysokiego ryzyka) — sprawdź aktualny stan** oraz polską ustawę wdrażającą (organ nadzoru, kary).

## Mapowanie wymogów na funkcje

### AI Act

| Przepis | Wymóg (skrót) | Jak wspiera to produkt | Gdzie |
|---|---|---|---|
| art. 9 | system zarządzania ryzykiem | zidentyfikowane ryzyka i środki: zmyślone dowody (weryfikacja cytatów), manipulacja (detekcja injection), dyskryminacja (anonimizacja, reguła 4/5), automation bias (override rate) | `docs/architektura.md` (ADR) |
| art. 10 ust. 2 lit. f–g | badanie i ograniczanie stronniczości | raport wskaźników selekcji per grupa i etap, impact ratio, ostrzeżenia o małej próbie; maskowanie cech chronionych przed oceną | `bias.py`, `anonymize.py` |
| art. 11, zał. IV | dokumentacja techniczna | README, architektura, model danych, opis logiki oceny i progów, testy | `README.md`, `docs/` |
| art. 12 | automatyczne rejestrowanie zdarzeń | dziennik append-only: kto, kiedy, akcja, źródło (ai/offline/człowiek/system), hash wejścia; łańcuch SHA-256 i weryfikacja integralności | `storage.py`, `GET /api/audit`, `/api/audit/verify` |
| art. 13 | przejrzystość wobec podmiotu stosującego | w UI: źródło oceny, wzór wyniku z liczbami, progi, bramki, dowód per wymaganie, powód przejścia na offline | `static/app.js`, `GET /api/config` |
| art. 14 ust. 4 lit. a, c | rozumienie i prawidłowa interpretacja wyników | dowody (cytaty) podświetlone w CV, oryginalny werdykt modelu przy degradacji, opis ograniczeń | szczegóły kandydata |
| art. 14 ust. 4 lit. b | świadomość automation bias | metryka override rate, liczba decyzji bez otwarcia dowodów, interpretacja progów; ostrzeżenie w formularzu decyzji | `oversight.py`, zakładka „Nadzór” |
| art. 14 ust. 4 lit. d | możliwość pominięcia / odwrócenia wyniku | system nigdy nie zmienia statusu sam; każda decyzja to akcja człowieka; override nie wymaga dodatkowej pracy | `service.decide` |
| art. 15 ust. 5 | odporność na próby manipulacji wynikami | detekcja prompt injection, wyłączenie linii z oceny, tagi XML, weryfikacja cytatów, wynik liczony w kodzie | `injection.py`, `evaluation.py` |
| art. 26 ust. 2 | nadzór sprawowany przez kompetentne osoby | identyfikacja osoby decydującej (`X-Actor`) w każdym wpisie | `api.py` — **uwierzytelnianie do wdrożenia** |
| art. 26 ust. 4 | adekwatne dane wejściowe | profil stanowiska jawny i zamrożony; ostrzeżenie o braku danych w CV zamiast odsiania | `models.py`, `scoring.py` |
| art. 26 ust. 5–6 | monitorowanie działania, przechowywanie logów (min. 6 miesięcy) | raporty nadzoru i stronniczości; dziennik bez usuwania | **polityka retencji do ustalenia** |
| art. 26 ust. 11 | informowanie osób, wobec których stosuje się system | generator informacji dla kandydata: użycie AI, czynniki, decyzja człowieka, prawa, kontakt | `explanation.py` |
| art. 86 | prawo do wyjaśnienia decyzji | to samo wyjaśnienie: rola AI w procesie i główne czynniki (werdykty per wymaganie); sekcja praw zawsze z szablonu | `GET /api/candidates/{id}/explanation` |
| art. 5 ust. 1 lit. f | zakaz rozpoznawania emocji w miejscu pracy | produkt nie analizuje zdjęć, głosu ani emocji; wzmianki o zdjęciu są maskowane | `anonymize.py` |

### RODO

| Przepis | Wymóg | Jak wspiera to produkt |
|---|---|---|
| art. 5 ust. 1 lit. c, art. 25 | minimalizacja, privacy by design i by default | widok domyślny zanonimizowany; do modelu trafia tylko zanonimizowany tekst; dziennik bez treści CV i bez nazw plików; ankieta w osobnej bazie |
| art. 9, art. 22¹b KP | dane szczególnych kategorii (np. niepełnosprawność) | ankieta tylko z wyraźną zgodą, wycofanie zgody usuwa dane, wyłącznie agregaty. **Uwaga:** wg art. 22¹b KP dane szczególnych kategorii kandydata można przetwarzać na podstawie zgody tylko wtedy, gdy przekazanie następuje z jego inicjatywy — zweryfikuj, czy ankieta to spełnia, albo zrezygnuj z pytania o niepełnosprawność / powierz ankietę podmiotowi zewnętrznemu |
| art. 13–15 | informacja i dostęp | wyjaśnienie dla kandydata; odkrycie oryginału na potrzeby żądania dostępu (logowane) |
| art. 22 | brak decyzji opartych wyłącznie na zautomatyzowanym przetwarzaniu | brak automatycznych odrzuceń; decyzja i uzasadnienie człowieka; metryki pokazujące, czy udział człowieka jest rzeczywisty. Wyrok TSUE C-634/21 (SCHUFA) wskazuje, że wynik, który *de facto przesądza* o decyzji, może sam być „decyzją” — stąd monitoring automation bias |
| art. 28, art. 44 i nast. | powierzenie przetwarzania dostawcy modelu, transfery | tryb offline działa bez wysyłki danych; dla trybu AI **wymagana umowa powierzenia i analiza transferu** |
| art. 35 | ocena skutków (DPIA) | ocena i punktacja kandydatów z użyciem nowej technologii — DPIA najpewniej wymagana; dokumentacja z `docs/` jest wsadem. **Do wykonania przez organizację** |

### Kodeks pracy

| Przepis | Wymóg | Jak wspiera to produkt |
|---|---|---|
| art. 22¹ § 1 | zamknięty katalog danych od kandydata (imię i nazwisko, data urodzenia, dane kontaktowe, wykształcenie, kwalifikacje, przebieg zatrudnienia) | dane spoza katalogu (stan cywilny, dzieci, zdjęcie, wiek, PESEL, narodowość) są maskowane przed oceniającymi; dane z katalogu niepotrzebne do oceny (imię, nazwisko, data urodzenia, kontakt) również |
| art. 22¹a | inne dane tylko za zgodą; brak zgody nie może skutkować niekorzystnym traktowaniem | ankieta dobrowolna, odpowiedzi nigdy nie trafiają do oceny |
| art. 11³, art. 18³a–18³e | zakaz dyskryminacji w dostępie do zatrudnienia; ciężar dowodu po stronie pracodawcy (art. 18³b) | anonimizacja i neutralizacja form rodzajowych, jawne kryteria i wagi, dowody per wymaganie, uzasadnienia odrzuceń i dziennik — materiał do wykazania obiektywnych przesłanek; raport 4/5 jako wczesny sygnał |

## Czego produkt nie zapewnia (obowiązki organizacji)

- Uwierzytelnianie, role i szkolenie osób sprawujących nadzór (art. 4, art. 26 ust. 2 AI Act).
- System zarządzania jakością, ocena zgodności, deklaracja, oznakowanie CE i rejestracja w unijnej bazie danych
  (art. 16, 17, 43, 47–49 AI Act) — jeśli organizacja jest dostawcą.
- Monitorowanie po wprowadzeniu do obrotu i zgłaszanie poważnych incydentów (art. 72–73 AI Act).
- DPIA, rejestr czynności przetwarzania, klauzule informacyjne, polityka retencji i usuwania danych kandydatów.
- Umowa powierzenia z dostawcą modelu i ocena transferu danych (tryb AI).
- Ocena skutków dla praw podstawowych (art. 27 AI Act) — dotyczy m.in. podmiotów publicznych i świadczących usługi
  publiczne; sprawdź, czy organizacja jest objęta.
- Walidacja jakości oceny na reprezentatywnych danych i okresowy przegląd progów oraz profili stanowisk.
- Reguła 4/5 to heurystyka (wywodzi się z praktyki amerykańskiej), a nie test prawny w UE; przy małych próbach
  wyniki są niestabilne — potrzebna analiza statystyczna i jakościowa.
