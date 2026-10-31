# Zgodność: RODO, AI Act, prawo pracy

> **To nie jest porada prawna — zweryfikuj z prawnikiem i aktualnym stanem prawnym.**
> Poniższe mapowanie opisuje, jak funkcje aplikacji wspierają spełnienie wymogów; samo wdrożenie narzędzia
> nie zapewnia zgodności. Dokumenty w `data/dokumenty/` dotyczą fikcyjnej firmy i nie są wzorem regulaminów.

## Klasyfikacja systemu

Asystent odpowiada na pytania o treść regulaminów firmy i kieruje sprawy do ludzi. **Nie podejmuje decyzji
dotyczących pracowników** (zatrudnienie, awans, wynagrodzenie, rozwiązanie umowy, przydział zadań, ocena
pracy) i nie monitoruje zachowania — nie korzysta też z danych kadrowych konkretnych osób. Przy takim
przeznaczeniu nie mieści się on, według naszej oceny, w kategoriach systemów wysokiego ryzyka z obszaru
zatrudnienia (AI Act, załącznik III pkt 4). Rozszerzenie go o funkcje z tej listy (np. ocenianie pracowników
na podstawie rozmów) zmieniłoby klasyfikację i wymaga ponownej analizy.

**Terminy AI Act.** Zakazane praktyki i obowiązek kompetencji w zakresie AI (art. 5 i art. 4) stosuje się od
2 lutego 2025 r., obowiązki przejrzystości (art. 50) — zgodnie z pierwotnym harmonogramem od 2 sierpnia 2026 r.,
a obowiązki dla systemów wysokiego ryzyka z załącznika III — pierwotnie również od 2 sierpnia 2026 r.
**Harmonogram dla systemów wysokiego ryzyka mógł zostać zmieniony (pakiet Digital Omnibus) i trzeba go
sprawdzić w aktualnym stanie prawnym.**

## RODO

| Wymóg | Jak aplikacja go wspiera |
|---|---|
| Minimalizacja danych (art. 5 ust. 1 lit. c) | Czat bez tożsamości: pseudonimowy token sesji, w bazie tylko jego skrót. Przed zapisem i przed wysłaniem do modelu maskowane są e-maile, telefony, PESEL, numery kont, nazwiska i „pan/pani X”. Do modelu trafia tylko pytanie i fragmenty regulaminów. |
| Ograniczenie przechowywania (art. 5 ust. 1 lit. e) | `HR_RETENTION_DAYS` (domyślnie 90 dni): automatyczne czyszczenie raz na dobę i ręczne z panelu HR (`POST /api/hr/retention/purge`). Usuwane są pytania, oceny i zamknięte zgłoszenia. |
| Szczególne kategorie danych — zdrowie (art. 9; art. 22¹ᵇ Kodeksu pracy) | Reguły wykrywają wzmianki o zdrowiu; szczegóły są zastępowane znacznikiem `[informacja o zdrowiu]` przed zapisem, eskalacją i wysłaniem do modelu. Asystent prosi, by ich nie podawać, i nigdy o nie nie dopytuje. Regulamin przykładowy wprost mówi, że pracownik nie podaje rozpoznania. |
| Ochrona danych w fazie projektowania (art. 25) | Domyślnie brak tożsamości, kontakt do HR podawany dobrowolnie tylko przy zgłoszeniu, odmowa ujawniania danych innych pracowników („ile zarabia Kowalski”). |
| Bezpieczeństwo (art. 32) | Opcjonalny token panelu HR (`HR_PANEL_TOKEN`, porównanie w czasie stałym), sprawdzanie, czy ocenę i eskalację wykonuje właściciel sesji, brak danych osobowych w logach aplikacji. Wdrożenie produkcyjne wymaga SSO, HTTPS i kontroli dostępu do pliku bazy. |
| Zautomatyzowane decyzje (art. 22) | Brak — asystent informuje i kieruje do HR; decyzje w sprawach indywidualnych podejmują ludzie. |
| Obowiązek informacyjny (art. 13) | UI informuje o pseudonimowości i okresie przechowywania. Przed wdrożeniem trzeba uzupełnić klauzulę informacyjną (administrator, cel, podstawa prawna, odbiorcy — w tym dostawca modelu). |
| Podmiot przetwarzający (art. 28) | W trybie AI dostawca modelu przetwarza pytania (po maskowaniu) i fragmenty regulaminów — potrzebna umowa powierzenia i ocena transferu danych. Tryb offline (`HR_AI_OFFLINE=1`) nie wysyła niczego na zewnątrz. |
| Ocena skutków (art. 35) | Zalecana przed wdrożeniem, zwłaszcza przy integracji z danymi kadrowymi (roadmapa). |

## AI Act

| Wymóg | Jak aplikacja go wspiera |
|---|---|
| Przejrzystość wobec osób korzystających z AI (art. 50) | UI pokazuje tryb pracy (AI/offline) i źródło każdej odpowiedzi; odpowiedzi AI są oznaczone znacznikiem „AI”. |
| Zakazane praktyki (art. 5) | Brak rozpoznawania emocji, scoringu społecznego czy kategoryzacji biometrycznej. |
| Kompetencje w zakresie AI (art. 4) | Komunikaty o ograniczeniach (wyciąg zamiast interpretacji, zastrzeżenie przy pytaniach prawnych), widoczne cytaty i raport luk wspierają świadome korzystanie; organizacja nadal musi przeszkolić zespół HR. |
| Nadzór człowieka i rzetelność (dobre praktyki, art. 14–15 jako punkt odniesienia) | Każdy cytat jest weryfikowany w kodzie; bez potwierdzonego cytatu odpowiedź nie jest pokazywana. Sprawy wrażliwe i niepewne trafiają do człowieka (eskalacja z kontekstem). Dziennik interakcji i statystyki pozwalają monitorować jakość. |
| Odporność na manipulację (art. 15 jako punkt odniesienia) | Pytanie i dokumenty są przekazywane modelowi jako dane w tagach XML z neutralizacją znaczników; próby prompt injection są flagowane w odpowiedzi i przy dokumencie. |

## Prawo pracy

| Obszar | Jak aplikacja go wspiera |
|---|---|
| Regulamin pracy i regulamin wynagradzania (art. 104 i 77² Kodeksu pracy) | Odpowiedzi tylko na podstawie aktualnie obowiązującej wersji, z wersją i datą „obowiązuje od” przy każdym cytacie; wersje archiwalne nie odpowiadają, nowe wersje zaczynają obowiązywać w swoim dniu. Asystent nie zastępuje doręczenia regulaminów ani zapoznania z nimi pracowników. |
| Mobbing i dyskryminacja (art. 94³, art. 18³ᵃ–18³ᵉ Kodeksu pracy) | Asystent nie ocenia sprawy, nie zbiera szczegółów i kieruje do oficjalnej procedury i kanałów zgłoszeń, przypominając o zakazie działań odwetowych. Czat nie jest kanałem zgłoszeń. |
| Ochrona sygnalistów (ustawa z 14 czerwca 2024 r. o ochronie sygnalistów) | Pytania o zgłoszenia naruszeń są odsyłane do procedury zgłoszeń wewnętrznych (kanały, zgłoszenia anonimowe, terminy, ochrona przed odwetem). Asystent nie przyjmuje zgłoszeń i nie przechowuje ich treści poza zwykłym logiem pytania podlegającym retencji. |
| Praca zdalna (art. 67¹⁸–67³⁴ Kodeksu pracy) | Odpowiedzi z obowiązującej polityki pracy zdalnej z wersjonowaniem — po zmianie zasad stara wersja przestaje odpowiadać. |
| Jawność wynagrodzeń i poufność danych innych osób | Asystent nie ujawnia wynagrodzeń, ocen ani danych zdrowotnych innych pracowników; może przytoczyć zasady (np. przedziały wynagrodzeń, prawo do informacji) z regulaminu. |
| Porady prawne | Zastrzeżenie, że odpowiedź opiera się na regulaminach firmy, a nie na analizie przepisów; gdy regulaminy milczą — asystent mówi to wprost i kieruje do HR. Regulaminy firmy nie mogą być mniej korzystne niż przepisy — za ich zgodność odpowiada pracodawca, nie asystent. |
