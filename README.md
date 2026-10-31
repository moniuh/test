# Radar Pracy

Asystent szukania pracy dla kandydata: zbiera oferty, porównuje je z Twoim CV wymaganie po
wymaganiu (z cytatem z CV jako dowodem), prowadzi przez aplikowanie, rozmowy i negocjacje,
a na koniec pokazuje, co działa, a co nie.

- **Stack:** React 19 + TypeScript (Vite) · Java 21 + Spring Boot 4 (JPA, H2/PostgreSQL) · opcjonalnie Claude API
- **Dane zostają u Ciebie.** Aplikacja jest jednoosobowa i działa w pełni bez AI. Z kluczem API
  wybrane funkcje (np. list motywacyjny) korzystają z Claude, ale tylko na danych po zamaskowaniu
  imienia i nazwiska, kontaktu, PESEL i adresu.
- **Pomysły i ranking:** [docs/POMYSLY-100.md](docs/POMYSLY-100.md) — 100 pomysłów na rozwój,
  ocenionych jednym modelem punktowym; 50 najwyżej ocenionych jest zaimplementowanych.
- **Architektura:** [docs/ARCHITEKTURA.md](docs/ARCHITEKTURA.md)

<!-- FUNKCJE -->

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
| `RADAR_DEMO_DATA` | `true` | dane demonstracyjne przy pustej bazie (fikcyjna kandydatka i firmy) |
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
- Import z adresu URL pobiera pojedynczą stronę na żądanie użytkownika; część portali blokuje pobieranie —
  wtedy wystarczy wkleić treść ogłoszenia.
- Schemat bazy tworzy Hibernate (`ddl-auto: update`). Przed wdrożeniem produkcyjnym warto przejść na migracje.
- Aplikacja jest jednoosobowa (bez kont użytkowników) — pomysł #86 czeka w backlogu.
