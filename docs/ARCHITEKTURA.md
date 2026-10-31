# Radar Pracy — architektura

## Widok ogólny

```mermaid
flowchart LR
    subgraph FE["Frontend: React 19 + Vite + TypeScript"]
        PAGES["Strony: Pulpit, Agregator, Oferty, Oferta,<br/>Tailoring CV, Aplikacje, Rozmowy,<br/>Wynagrodzenia, Analityka, Profil"]
        SLOTS["Panele modułów (features/*)<br/>wpięte w strony rdzenia"]
    end
    subgraph BE["Backend: Java 21 + Spring Boot 4"]
        API["REST /api/*"]
        CORE["Rdzeń: profil i CV, oferty,<br/>dopasowanie, wynagrodzenia, aplikacje"]
        MODS["Moduły: aggregator, tailoring, profile.cv,<br/>offer.analysis, matching.ranking, application.*,<br/>interview, salary.tools, analytics"]
        DICT["Słownik umiejętności<br/>(synonimy, rodziny, „obejmuje”)"]
        AI["ClaudeClient + PiiMasker<br/>(opcjonalnie, z fallbackiem offline)"]
    end
    DB[("H2 (dev) / PostgreSQL")]
    CLAUDE["Claude API"]
    SRC["Źródła ofert: RSS/Atom,<br/>strony kariery (JSON-LD)"]

    PAGES --> API
    SLOTS --> API
    API --> CORE
    API --> MODS
    MODS --> CORE
    CORE --> DICT
    MODS --> DICT
    CORE --> DB
    MODS --> DB
    MODS -. "tylko zamaskowane dane" .-> AI
    SRC -- "harmonogram, robots.txt, limit" --> MODS
    AI -. "ANTHROPIC_API_KEY" .-> CLAUDE
```

## Przepływ dopasowania oferty do CV

```mermaid
sequenceDiagram
    participant U as Kandydat
    participant FE as Frontend
    participant M as MatchService
    participant D as SkillDictionary
    U->>FE: otwiera ofertę
    FE->>M: GET /api/offers/{id}/match?cvId=
    M->>D: umiejętności w CV (+ relacja „obejmuje”)
    loop każde wymaganie
        M->>D: umiejętności w wymaganiu
        alt umiejętność w CV
            M-->>M: FULL + cytat z CV (podciąg, sprawdzany)
        else tylko w profilu
            M-->>M: PROFILE_ONLY (80%) + „dopisz do CV”
        else pokrewna z tej samej rodziny
            M-->>M: RELATED (40%) + cytat pokrewnej
        else brak
            M-->>M: MISSING
        end
        M-->>M: korekta: lata doświadczenia, poziom języka (CEFR)
    end
    M-->>FE: wynik 0–100 (wagi 3/1), wyjaśnienie „dlaczego”
```

## Przepływ agregatora

```mermaid
flowchart LR
    S["Źródło (RSS/Atom/JSON-LD)"] --> F["Pobranie<br/>robots.txt · limit · SSRF"]
    F --> P["Parsowanie wpisów"]
    P --> FL{"Filtry źródła"}
    FL -- odrzucone --> X["licznik filteredOut"]
    FL --> D{"Duplikat?<br/>externalId · URL · tytuł+firma"}
    D -- tak --> XD["licznik duplicates"]
    D -- nie --> A["Analiza: wymagania, widełki,<br/>tryb pracy, umowa, poziom,<br/>sygnały prompt injection"]
    A --> O["JobOffer (NEW)"]
    O --> I["Skrzynka: ranking + dopasowanie"]
    I --> T["Tailoring CV pod ofertę"]
```

## Zasady

1. **AI doradza, człowiek decyduje.** Model nie podejmuje decyzji i nie liczy wyników.
2. **Dowody są weryfikowane w kodzie** — każdy cytat jest dosłownym fragmentem CV lub ogłoszenia.
3. **Liczby liczy kod** — wynik dopasowania, ranking, audyt CV, ryzyko oszustwa, statystyki płac.
4. **Działa bez AI** — każda funkcja z AI ma ścieżkę offline; interfejs pokazuje źródło (`ai`/`offline`).
5. **Dane użytkownika to dane, nie polecenia** — treści trafiają do modelu w tagach XML z instrukcją.
6. **Minimalizacja danych** — imię i nazwisko, kontakt, PESEL, adres itp. są maskowane przed wysłaniem.
7. **Dostępność** — WCAG 2.2 AA: klawiatura, etykiety, ogłaszanie zmian, kontrast, wydruk.

## Układ kodu

| Warstwa | Ścieżka | Zawartość |
|---|---|---|
| Rdzeń backendu | `backend/src/main/java/pl/radarpracy/{common,skills,profile,offer,salary,matching,application,ai,demo}` | encje, serwisy, REST, słownik, dopasowanie, AI |
| Moduły backendu | `aggregator`, `tailoring`, `profile/cv`, `offer/analysis`, `matching/ranking`, `application/*`, `interview`, `salary/tools`, `analytics` | funkcje z rankingu 100 pomysłów |
| Rdzeń frontendu | `frontend/src/{api,components,hooks,pages}` | klient API, typy, komponenty UI, strony rdzenia |
| Moduły frontendu | `frontend/src/features/<moduł>` | strony i panele modułów |
| Dokumentacja | `docs/` | ranking pomysłów, architektura, generator rankingu |

## Dane

Encje rdzenia: `CandidateProfile` (singleton), `CvVariant`, `JobOffer` (wymagania jako JSON),
`JobApplication`, `ApplicationEvent`, `Interview`. Moduły dodają własne tabele z prefiksem
(`aggregator_*`, `tailoring_*`, `ranking_*`, `interview_*`, `analytics_*`) powiązane z encjami rdzenia po id. Schemat tworzy Hibernate (`ddl-auto: update`); przed
wdrożeniem produkcyjnym warto przejść na migracje (Flyway).
