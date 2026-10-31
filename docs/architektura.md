# Architektura

LMS lite to **rejestr obowiązków szkoleniowych z alarmem i raportem**, a nie pełna platforma e-learningowa.
Jeden proces FastAPI, baza SQLite, statyczny interfejs w czystym JS. Logika domenowa nie zależy od FastAPI.

```mermaid
flowchart LR
    UI["static/index.html + app.js<br/>(pulpit, pracownicy, szkolenia,<br/>moje szkolenia, raport, outbox)"] -->|JSON| API["api.py<br/>FastAPI, błędy po polsku"]
    API --> SVC["service.py<br/>TrainingService"]
    SVC --> DB[("SQLite<br/>db.py")]
    SVC --> RULES["rules.py<br/>reguły przypisań"]
    SVC --> COMP["compliance.py<br/>ważność i statusy"]
    SVC --> REM["reminders.py<br/>etapy przypomnień"]
    SVC --> QUIZ["quiz.py<br/>losowanie z ziarnem, ocena"]
    SVC --> GEN["quizgen.py<br/>generator pytań"]
    GEN -->|"materiał w &lt;material&gt;,<br/>PII zamaskowane"| LLM["llm.py<br/>Claude (structured outputs)"]
    GEN -->|"brak klucza / LLMUnavailable /<br/>0 poprawnych pytań"| OFF["cloze offline<br/>(definicje, liczby, daty, skróty)"]
    API --> REP["report.py<br/>CSV, druk, zaświadczenie"]
    CRON["cron / przycisk<br/>POST /api/jobs/daily"] --> API
```

## Model danych

```mermaid
erDiagram
    employees ||--o{ employees : "przełożony (manager_id)"
    employees ||--o{ assignments : ""
    trainings ||--o{ assignment_rules : ""
    trainings ||--o{ assignments : ""
    trainings ||--o{ questions : ""
    assignment_rules |o--o{ assignments : "rule_id"
    assignments ||--o{ attempts : ""
    assignments ||--o| certificates : ""
    attempts |o--o| certificates : ""
    assignments ||--o{ outbox : ""

    employees { int id string full_name string email string department string position date hire_date int manager_id bool active }
    trainings { int id string title string category int validity_months int pass_threshold string mode text material int questions_per_test }
    assignment_rules { int id int training_id string department string position int due_days }
    assignments { int id int employee_id int training_id string reason date assigned_on date due_date string status date completed_on }
    questions { int id int training_id text text json options int correct_index text explanation text source_quote bool quote_verified string status string origin }
    attempts { int id int assignment_id int seed json questions json answers int score int percent bool passed datetime started_at datetime submitted_at int duration_seconds }
    certificates { int id string number string method date issued_on date valid_until string verification_code string confirmed_by string note }
    outbox { int id string dedup_key string kind string recipient_email string subject text body string status datetime sent_at }
```

- `assignments.status`: `open` → `completed` | `cancelled`. Indeks częściowy `UNIQUE(employee_id, training_id) WHERE status='open'`
  gwarantuje najwyżej jedno otwarte przypisanie na parę — synchronizacja jest idempotentna także przy błędzie w kodzie.
- `assignments.reason`: `new_hire` (auto-przypisanie nowego pracownika), `rule` (reguła dodana później), `renewal`, `manual`.
- `certificates.method`: `test` (zaliczony test), `confirmation` (szkolenie stacjonarne potwierdzone przez prowadzącego),
  `external` (wpis z papierowej ewidencji lub od zewnętrznej firmy szkoleniowej).
- `attempts.questions` to **migawka** pytań z chwili startu: edycja pytania przez HR nie zmienia oceny ani dowodu,
  co widział pracownik.
- `outbox.dedup_key` jest unikalny (`a{id_przypisania}:przed:7`, `…:po_terminie`, `…:eskalacja`).

## Decyzje projektowe (ADR w skrócie)

**ADR-1. Status liczony „w locie” z daty „dziś”, nie przechowywany.**
Status pary pracownik × szkolenie wynika z ostatniego zaświadczenia i otwartego przypisania (`compliance.compliance_status`).
Dzięki temu raport jest zawsze aktualny, a testy wstrzykują dowolne „dziś”. Reguły:

| Sytuacja | Status |
|---|---|
| ukończone, bezterminowe albo ważne > 30 dni | aktualne |
| ukończone, do końca ważności 0–30 dni (dzień `ważne do` włącznie) | wygasa ≤ 30 dni |
| ukończone, `dziś > ważne do` | przeterminowane |
| nieukończone, `dziś > termin` | przeterminowane |
| nieukończone w terminie (lub bez terminu) | nieukończone |
| brak reguły i ręcznego przypisania | nie dotyczy |

`ważne do = data ukończenia + N miesięcy`; gdy dzień nie istnieje (31 stycznia + 1 miesiąc), bierzemy ostatni dzień miesiąca.

**ADR-2. Odnowienie powstaje na początku okna 30 dni, termin = data wygaśnięcia.**
Wymóg „po upływie ważności powstaje nowe przypisanie” jest spełniony z zapasem: przypisanie istnieje już wtedy,
gdy szkolenie zaczyna „wygasać”, więc pracownik może je odnowić w terminie, a przypomnienia mają do czego się odnosić.
Gdy przegląd dzienny nie działał, odnowienie powstanie przy pierwszym uruchomieniu (z terminem w przeszłości → od razu przeterminowane).

**ADR-3. Termin pierwszego przypisania.** Nowy pracownik: `data zatrudnienia + N dni` z reguły (najkrótszy termin,
jeśli pasuje kilka reguł). Reguła dodana dla osób już zatrudnionych: `max(data zatrudnienia, dziś) + N` — inaczej
dodanie reguły tworzyłoby masowo przypisania przeterminowane „z urzędu”.

**ADR-4. Przypomnienia idempotentne przez etapy i `dedup_key`.** Etap = najbliższy próg z (30, 7, 1) dni przed terminem.
Pominięte etapy nie są wysyłane wstecz (system wyłączony przez tydzień nie wyśle trzech zaległych maili naraz).
Po terminie: jedna wiadomość do pracownika i jedna eskalacja do przełożonego (brak przełożonego → skrzynka HR).
`INSERT OR IGNORE` na unikalnym kluczu — ponowny przegląd tego samego dnia nic nie dodaje.

**ADR-5. AI pisze szkice, człowiek publikuje, kod liczy wynik.** Model proponuje pytania; kod odrzuca te bez dokładnie
4 różnych odpowiedzi, bez poprawnej odpowiedzi wśród opcji i z cytatem, którego nie ma w materiale (podciąg po
normalizacji białych znaków, cudzysłowów i wielkości liter, min. 15 znaków). Pytanie zawsze startuje jako „szkic”;
zatwierdzenie wymaga zweryfikowanego cytatu; edycja cofa do szkicu; zmiana materiału ponownie weryfikuje cytaty
i cofa do szkicu pytania, których dowód zniknął. Ocena testu jest deterministyczna — model nie ocenia ludzi.

**ADR-6. Generator offline: cloze z kontrolą fleksji.** Bez klucza API aplikacja nadal tworzy sensowne pytania:
- *definicje* („X to …”, „X – …”) → luka na pojęciu, dystraktory = inne definiowane pojęcia (wszystkie w mianowniku);
- *pojęcia na początku zdania* (podmiot, czyli mianownik) → te same dystraktory;
- *liczby z jednostką czasu* → dystraktory z innych terminów materiału, a potem warianty liczby; liczebnik jest
  odmieniany zgodnie z przypadkiem luki (`co 3 lata` → `2 lata`, `6 lat`; `w ciągu 3 miesięcy` → `6 miesięcy`;
  forma niejednoznaczna → tylko liczby, dla których forma jest wspólna);
- *jednostki nieodmienne* (kg, km/h, cm, %) i *liczba + rzeczownik w dopełniaczu* (`50 osób`) → warianty liczby;
- *daty* (`25 września 2024`) → inne daty z materiału i przesunięcia z zachowaniem dopełniacza nazwy miesiąca;
- *skróty* (UDT, PIP, ŚOI) → inne skróty z materiału, o ile są co najmniej 4.
Odrzucamy nagłówki, zdania z odpowiedzią w treści i dystraktory obecne w zdaniu; jedna luka na zdanie, rodzaje pytań
na przemian. Kolejność opcji zależy od skrótu SHA-256 zdania, więc wynik jest powtarzalny.

**ADR-7. Losowość testu odtwarzalna z ziarna.** Podejście zapisuje `seed` i migawkę pytań. Wybór pytań, kolejność
i permutacje odpowiedzi to osobne generatory `random.Random(f"{seed}:…")` — niezależne od kolejności z bazy,
więc ocena (i ewentualny spór) odtwarza dokładnie to, co widział pracownik. Próg porównujemy na liczbach
całkowitych (`score * 100 >= threshold * total`), bez błędów zaokrągleń. Po niezaliczeniu nie pokazujemy
poprawnych odpowiedzi.

**ADR-8. Zaświadczenie z kodem weryfikacyjnym.** Numer `SZK/RRRR/NNNN` (kolejny w roku), kod 12 znaków z alfabetu bez
0/O/1/I/L (ok. 60 bitów, łatwy do przepisania z wydruku). Publiczna weryfikacja zwraca tylko numer, imię i nazwisko,
szkolenie i daty — bez e-maila i stanowiska.

**ADR-9. SQLite, jedno połączenie, blokada.** Dla firmy 50–1000 osób wystarcza. Zapisy serializuje `RLock`,
transakcje zagnieżdżone dołączają do zewnętrznej. Migracja do Postgresa wymaga zmiany tylko `db.py` i SQL w serwisie.

**ADR-10. Wysyłka jako atrapa.** Wiadomości trafiają do tabeli `outbox`; „wysłanie” oznacza je jako wysłane
i loguje. Podłączenie SMTP to jedna funkcja w `TrainingService.send_pending`.

## Przepływy

```mermaid
sequenceDiagram
    actor HR
    participant API
    participant Gen as quizgen
    participant LLM as Claude
    actor P as Pracownik
    HR->>API: POST /trainings/{id}/questions/generate
    API->>Gen: materiał + istniejące pytania
    alt klucz API dostępny
        Gen->>LLM: <material> (PII zamaskowane)
        LLM-->>Gen: pytania + cytaty
        Gen->>Gen: walidacja: 4 opcje, poprawna wśród opcji, cytat w materiale
    else offline lub błąd modelu
        Gen->>Gen: cloze z definicji, liczb, dat, skrótów
    end
    Gen-->>API: szkice (source: ai | offline), odrzucone z powodem
    HR->>API: edycja / zatwierdzenie (wymaga zweryfikowanego cytatu)
    P->>API: POST /assignments/{id}/attempts
    API-->>P: pytania zatwierdzone, kolejność z ziarna, bez odpowiedzi
    P->>API: POST /attempts/{id}/submit
    API->>API: ocena deterministyczna, próg
    API-->>P: wynik + zaświadczenie (nr, kod weryfikacyjny)
```

Przegląd dzienny (`POST /api/jobs/daily`, uruchamiany też przy starcie aplikacji):
1. `sync_assignments` — brakujące przypisania z reguł, odnowienia w oknie 30 dni, anulowanie niewymaganych;
2. `queue_reminders` — etapy przypomnień i eskalacje do `outbox` (idempotentnie).

## Dane demonstracyjne

`seed.py` tworzy fikcyjną „Firma Demo sp. z o.o.” (10 osób, 3 działy, 7 szkoleń, 12 reguł) z `data/`.
Historia powstaje przez prawdziwe operacje serwisu (testy z ziarnem, potwierdzenia prowadzącego, wpisy z ewidencji)
z datami liczonymi względem dzisiejszej daty, więc demo zawsze pokazuje wszystkie statusy. Pytania demo powstają
generatorem offline (powtarzalnie, bez kosztów API); pytania o sygnalistach zostają szkicami, żeby pokazać przegląd HR.
