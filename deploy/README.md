# Wdrożenie „AI w HR” w Dockerze

Jedno polecenie uruchamia wszystkie pięć aplikacji portfolio i portal startowy. Obrazy budują
się bezpośrednio z branchy `ai-hr/*` na GitHubie, więc lokalnie potrzebujesz tylko tego katalogu.

| Usługa | Adres | Branch |
|---|---|---|
| Portal startowy | <http://localhost:8080> | ten katalog |
| Audytor ogłoszeń o pracę | <http://localhost:8001> | `ai-hr/ogloszenia-zgodnosc` |
| Preselekcja CV | <http://localhost:8002> | `ai-hr/preselekcja-cv` |
| Trener rozmów rekrutacyjnych | <http://localhost:8003> | `ai-hr/symulator-rozmow` |
| Szkolenia obowiązkowe (LMS) | <http://localhost:8004> | `ai-hr/szkolenia-lms` |
| Asystent kadrowy | <http://localhost:8005> | `ai-hr/asystent-kadrowy` |

## Wymagania

- **Windows / macOS:** [Docker Desktop](https://www.docker.com/products/docker-desktop/)
  (na Windows z backendem WSL 2). Uruchom go przed poleceniami poniżej.
- **Linux:** Docker Engine z wtyczką Compose (`docker compose version` ≥ 2.24).
- Git (do pobrania tego katalogu) i ok. 2 GB wolnego miejsca na obrazy.

## Pierwsze uruchomienie

```bash
git clone --branch ccr-69ef4109-9sgm79 --depth 1 https://github.com/moniuh/test.git ai-w-hr
cd ai-w-hr/deploy
cp .env.example .env          # Windows (PowerShell / cmd): copy .env.example .env
docker compose up -d --build
```

Pierwsza budowa trwa kilka minut. Potem otwórz <http://localhost:8080>: portal pokazuje,
które aplikacje już działają. Stan kontenerów sprawdzisz też tak:

```bash
docker compose ps             # kolumna STATUS powinna pokazać „healthy”
docker compose logs -f asystent
```

Aplikacje startują z fikcyjnymi danymi demonstracyjnymi. W preselekcji CV kliknij
„Wczytaj dane przykładowe”.

## Funkcje AI

Bez klucza wszystko działa w trybie offline (reguły i heurystyki), a interfejs pokazuje źródło
każdego wyniku. Żeby włączyć AI, wpisz klucz w `.env`:

```dotenv
ANTHROPIC_API_KEY=sk-ant-...
```

Potem zastosuj zmianę (bez przebudowy):

```bash
docker compose up -d
```

Do modelu trafiają tylko dane potrzebne do danej funkcji, z zamaskowanymi danymi osobowymi.
Szczegóły są w `docs/zgodnosc.md` na branchu każdej aplikacji.

## Bezpieczeństwo

- Aplikacje **nie mają logowania**. Dlatego porty są domyślnie dostępne tylko z tego komputera
  (`BIND_ADDR=127.0.0.1`).
- Jeśli chcesz udostępnić je w sieci lokalnej, ustaw `BIND_ADDR=0.0.0.0` i koniecznie
  `HR_PANEL_TOKEN` (chroni panel HR asystenta). Nie wystawiaj aplikacji do internetu bez
  reverse proxy z uwierzytelnianiem i HTTPS.
- Kontenery działają jako użytkownik bez uprawnień roota.
- Plik `.env` zawiera klucz API: nie commituj go.

## Dane i kopie zapasowe

Bazy SQLite leżą w wolumenach Dockera i przetrwają restart, aktualizację oraz `docker compose down`:

| Wolumen | Zawartość |
|---|---|
| `ai-w-hr_preselekcja-dane` | oferty, kandydaci, oceny, decyzje, dziennik audytowy |
| `ai-w-hr_preselekcja-demografia` | dobrowolne dane demograficzne (osobno od ocen) |
| `ai-w-hr_symulator-dane` | sesje treningowe |
| `ai-w-hr_szkolenia-dane` | pracownicy, szkolenia, wyniki, zaświadczenia |
| `ai-w-hr_asystent-dane` | dokumenty, rozmowy (z retencją), zgłoszenia do HR |

Kopia zapasowa jednego wolumenu do bieżącego katalogu:

```bash
docker run --rm -v ai-w-hr_szkolenia-dane:/dane -v "$PWD":/kopia alpine \
  tar czf /kopia/szkolenia-$(date +%F).tar.gz -C /dane .
```

W PowerShell zapisz polecenie w jednej linii i zamiast `$(date +%F)` wpisz datę ręcznie.

## Aktualizacja

```bash
git pull                       # nowa wersja tego katalogu
docker compose build --pull    # pobiera najnowszy stan branchy z GitHuba
docker compose up -d
```

## Zatrzymanie i usunięcie

```bash
docker compose stop            # zatrzymuje, dane zostają
docker compose down            # usuwa kontenery, dane zostają w wolumenach
docker compose down -v         # usuwa też wszystkie dane (nieodwracalne)
```

## Rozwiązywanie problemów

| Objaw | Co zrobić |
|---|---|
| `port is already allocated` | Port 8001–8005 lub 8080 jest zajęty. Zmień `PORTAL_PORT` w `.env` albo lewą stronę mapowania portu w `docker-compose.yml` (wtedy popraw też `data-port` w `portal/index.html`). |
| `env_file … required` lub nieznane pole | Za stara wtyczka Compose. Zaktualizuj Docker Desktop albo `docker-compose-plugin` (wymagane ≥ 2.24). |
| Budowa nie może pobrać repozytorium | Sprawdź dostęp do `github.com`. Za firmowym proxy skonfiguruj proxy w Docker Desktop (Settings → Resources → Proxies). |
| Kontener `unhealthy` | `docker compose logs <usługa>` pokaże przyczynę. |
| AI nie działa mimo klucza | Sprawdź, czy `HR_AI_OFFLINE=0` i czy po zmianie `.env` uruchomiono `docker compose up -d`. Każda aplikacja pokazuje tryb w nagłówku. |

## Budowa z lokalnych checkoutów (dla deweloperów)

Każdy branch ma własny `Dockerfile`. Żeby zbudować obraz z lokalnych zmian zamiast z GitHuba:

```bash
git clone --branch ai-hr/preselekcja-cv https://github.com/moniuh/test.git ../preselekcja
# ... zmiany w ../preselekcja ...
docker build -t ai-hr/preselekcja:latest ../preselekcja
docker compose up -d --no-build preselekcja
```
