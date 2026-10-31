# Radar Pracy

**Agregator ogłoszeń o pracę z tailoringiem CV.** Oferty z wielu źródeł (kanały RSS/Atom, strony
kariery firm z danymi schema.org, wklejone ogłoszenia i linki) trafiają do jednej skrzynki, gdzie są
automatycznie analizowane: wymagania, widełki sprowadzone do wspólnej miary, tryb pracy, czerwone
flagi, duplikaty. Każdą ofertę widzisz z wynikiem dopasowania do Twojego CV (wymaganie po wymaganiu,
z cytatem z CV jako dowodem), a pod wybraną ofertę generujesz **dopasowaną, prawdziwą wersję CV**:
właściwe kompetencje na górze, punkty doświadczenia w kolejności trafności, nic zmyślonego (pilnuje
tego strażnik prawdomówności).

- **Stack:** React 19 + TypeScript (Vite) · Java 21 + Spring Boot 4 (JPA, H2/PostgreSQL) · opcjonalnie Claude API
- **Dane zostają u Ciebie.** Aplikacja jest jednoosobowa i działa w pełni bez AI. Z kluczem API
  wybrane funkcje (np. list motywacyjny) korzystają z Claude, ale tylko na danych po zamaskowaniu
  imienia i nazwiska, kontaktu, PESEL i adresu.
- **Pomysły i ranking:** [docs/POMYSLY-100.md](docs/POMYSLY-100.md) — 100 pomysłów na rozwój,
  ocenionych jednym modelem punktowym; 50 najwyżej ocenionych jest zaimplementowanych.
- **Architektura:** [docs/ARCHITEKTURA.md](docs/ARCHITEKTURA.md)

## Co potrafi

**Agregacja ogłoszeń** (`/agregator`)
- Źródła: kanały RSS/Atom, strony kariery firm z danymi schema.org `JobPosting`, ogłoszenia wklejone
  lub zaimportowane z adresu URL. Pobieranie cykliczne i na żądanie, z historią przebiegów.
- Etykieta pobierania: robots.txt, limit zapytań na domenę, identyfikujący User-Agent, ochrona SSRF,
  link „Źródło” do oryginału przy każdej ofercie.
- Każda pobrana oferta jest analizowana: wymagania, widełki sprowadzone do wspólnej miary, tryb pracy,
  forma umowy, poziom stanowiska, data ważności. Duplikaty z różnych źródeł są wykrywane.
- Skrzynka nowych ofert posortowana rankingiem, wyszukiwarka pełnotekstowa (BM25), filtry na poziomie
  źródła, automatyczna archiwizacja wygasłych ofert, ostrzeżenia o ukrytych poleceniach dla AI w treści ogłoszeń.

**Ocena ofert** (`/oferty`, `/oferty/:id`)
- Dopasowanie do CV wymaganie po wymaganiu z cytatem z CV jako dowodem; słownik ~160 umiejętności
  z synonimami (k8s = Kubernetes) i rodzinami (MySQL ≈ PostgreSQL); lata doświadczenia i poziom języka
  liczone częściowo; wyjaśnienie „dlaczego 74%”.
- Ranking: dopasowanie × wynagrodzenie × tryb pracy × lokalizacja × świeżość, wykluczenia z Twoich
  deal-breakerów, uczenie preferencji z zapisanych i odrzuconych ofert, zapisane wyszukiwania z licznikiem nowych.
- Analiza ogłoszenia: czerwone flagi (z podstawą prawną), ryzyko oszustwa, duplikaty, podobne oferty.

**Tailoring CV** (`/dopasuj-cv`)
- Generator dopasowanej wersji CV pod ofertę: kluczowe kompetencje z wymagań na górze, punkty
  doświadczenia w kolejności trafności, podsumowanie z prawdziwych danych; opcjonalnie redakcja AI.
- Strażnik prawdomówności: blokuje dopisane umiejętności, liczby, daty i pracodawców, których nie ma
  w CV bazowym ani w profilu.
- Wynik dopasowania przed i po, widok zmian, eksport DOCX, biblioteka wersji powiązanych z ofertami i aplikacjami.
- Podpowiedzi słów kluczowych pod ofertę, audyt CV pod ATS, wykrywanie zbędnych danych wrażliwych,
  import CV z PDF/DOCX, wyciąganie umiejętności z CV.

**Aplikowanie i przygotowanie**
- Tablica Kanban aplikacji (także z klawiatury), oś czasu, przypomnienia o follow-upie, list motywacyjny
  z cytatów CV, szablony wiadomości, rozmowy z eksportem do kalendarza (.ics), zapis wysłanych dokumentów.
- Pytania rekrutacyjne wyprowadzone z oferty, ocena odpowiedzi metodą STAR, bank historii, podsumowania rozmów.
- Wynagrodzenia: kalkulator netto (UoP, zlecenie, B2B liniowy/ryczałt/skala, parametry 2026), porównywarka
  ofert, statystyki widełek z zebranych ofert, ostrzeżenie o stawce poniżej rynku, asystent negocjacji.
- Pulpit z listą „Co dalej?”, lejek rekrutacyjny, skuteczność źródeł i wersji CV, luki kompetencyjne
  na tle rynku, trendy umiejętności, dziennik samopoczucia, raport aktywności (druk/PDF/CSV).

Pełna lista z numerami pomysłów i miejscem w kodzie: [docs/POMYSLY-100.md](docs/POMYSLY-100.md).


## Uruchomienie

Wymagania: Java 21, Maven 3.9+, Node.js 22.

```bash
# backend (http://localhost:8080) — przy pierwszym starcie tworzy dane demonstracyjne
cd backend
mvn spring-boot:run

# frontend (http://localhost:5173, proxy /api → :8080)
cd frontend
npm ci
npm run dev
```

Jeden plik JAR z wbudowanym frontendem:

```bash
./scripts/build-all.sh
java -jar backend/target/radar-pracy-0.2.0.jar   # http://localhost:8080
```

### Konfiguracja

| Zmienna | Domyślnie | Znaczenie |
|---|---|---|
| `ANTHROPIC_API_KEY` | — | włącza funkcje AI; bez klucza wszystko działa na regułach offline |
| `RADAR_AI_OFFLINE` | `false` | wymusza tryb offline nawet z kluczem |
| `RADAR_AI_MODEL` | `claude-opus-5-5` | model Claude |
| `RADAR_DB_URL` | plik H2 `./data/radarpracy` | np. `jdbc:postgresql://localhost:5432/radar` (sterownik PostgreSQL jest w zależnościach) |
| `RADAR_DB_USER` / `RADAR_DB_PASSWORD` | `sa` / pusty | dane logowania do bazy |
| `RADAR_DEMO_DATA` | `true` | dane demonstracyjne przy pustej bazie (fikcyjna kandydatka, firmy i źródła ofert) |
| `RADAR_AGGREGATOR_SCHEDULER` | `true` | cykliczne pobieranie ofert ze źródeł |
| `RADAR_AGGREGATOR_INTERVAL` | `60` | co ile minut pobierać oferty |
| `RADAR_CALENDAR_ZONE` | `Europe/Warsaw` | strefa terminów rozmów w eksporcie .ics |
| `PORT` | `8080` | port backendu |

Kursy walut, liczba godzin w miesiącu i przeliczniki B2B ↔ UoP są w `backend/src/main/resources/application.yml`
(`radar.salary.*`).

### Testy

```bash
cd backend && mvn verify          # testy jednostkowe i integracyjne (tryb offline)
cd frontend && npm test && npm run build
node docs/scripts/generuj-pomysly.mjs --check   # czy ranking pomysłów jest aktualny
```

CI (GitHub Actions) uruchamia wszystkie trzy kroki.

## Zasady projektowe

1. **AI doradza, człowiek decyduje.** Wynik dopasowania, ranking, audyt CV, ryzyko oszustwa i statystyki
   płac liczy deterministyczny kod; model co najwyżej pisze tekst.
2. **Każdy dowód jest sprawdzany.** Cytaty z CV i ogłoszeń są dosłownymi fragmentami źródła.
3. **Działa bez AI.** Każda funkcja z AI ma ścieżkę offline, a interfejs pokazuje źródło wyniku.
4. **Treści od użytkownika to dane, nie polecenia.** Do modelu trafiają w tagach XML, po maskowaniu danych osobowych.
5. **Dostępność.** WCAG 2.2 AA: obsługa klawiaturą (także tablicy Kanban), etykiety pól, ogłaszanie zmian,
   kontrast, wersja do druku. Strony są sprawdzane automatycznie narzędziem axe-core.

## Ograniczenia

- Kalkulator netto i przeliczniki wynagrodzeń to szacunki na parametrach z jednego roku, a nie porada podatkowa.
- Agregator korzysta wyłącznie z oficjalnych kanałów (RSS/Atom, dane strukturalne stron kariery) i nie
  pobiera danych z portali, których regulamin tego zabrania. Import z URL pobiera pojedynczą stronę na żądanie
  użytkownika; strony renderowane wyłącznie skryptem lub blokujące pobieranie wymagają wklejenia treści.
- Ochrona SSRF sprawdza adres IP po rozwiązaniu DNS, więc pobieranie wymaga działającego DNS na serwerze
  (w środowiskach, gdzie adresy rozwiązuje wyłącznie proxy HTTP, źródła sieciowe zostaną odrzucone).
- Schemat bazy tworzy Hibernate (`ddl-auto: update`). Przed wdrożeniem produkcyjnym warto przejść na migracje.
- Aplikacja jest jednoosobowa (bez kont użytkowników) — pomysł #86 czeka w backlogu.
