# Zgodność: RODO, AI Act, prawo pracy

> **To nie jest porada prawna — zweryfikuj z prawnikiem i aktualnym stanem prawnym.**
> Opis poniżej mapuje wymogi na funkcje aplikacji i wskazuje obszary do sprawdzenia przed wdrożeniem.

## Charakter narzędzia

Symulator jest **narzędziem treningowym** dla osób prowadzących rozmowy rekrutacyjne. Kandydat jest fikcyjny
(odgrywa go model AI albo skrypt), a wyniki służą nauce, nie ocenie pracownika. Ten charakter wspierają decyzje
produktowe: brak kont i rankingów, brak widoku przełożonego, raport z zastrzeżeniem „to nie jest ocena
pracownika”, eksport tylko z inicjatywy osoby trenującej.

## AI Act (rozporządzenie (UE) 2024/1689)

| Wymóg | Jak realizuje aplikacja |
|---|---|
| **Przejrzystość — art. 50 ust. 1**: osoba wchodząca w interakcję z systemem AI ma o tym wiedzieć | stały baner „Kandydat jest symulowany komputerowo… To nie jest prawdziwa osoba” na każdym ekranie, oznaczenie „kandydat symulowany” w czacie, źródło każdej odpowiedzi (AI / skrypt offline); kandydat zapytany „czy jesteś AI?” potwierdza to w obu trybach |
| **Systemy wysokiego ryzyka — załącznik III pkt 4** (zatrudnienie: rekrutacja i wybór kandydatów; decyzje dotyczące stosunku pracy, monitorowanie i ocena wyników i zachowania pracowników) | narzędzie nie ocenia prawdziwych kandydatów ani nie podejmuje decyzji kadrowych. Ryzyko pojawiłoby się, gdyby organizacja używała raportów do oceny menedżerów — dlatego aplikacja nie ma rankingów ani panelu przełożonego, a raport ma zastrzeżenie. Wdrażając narzędzie w firmie, zapisz w regulaminie, że wyniki treningu nie są podstawą ocen okresowych |
| **Kompetencje w zakresie AI — art. 4** | raport i coach uczą rozpoznawania ograniczeń (źródło AI/offline przy każdym wyniku, odrzucanie niezweryfikowanych cytatów, informacja o awarii modelu) |
| **Nadzór człowieka i rozliczalność** | metryki liczy kod, model tylko dodaje uwagi z weryfikowanymi cytatami; podstawy prawne pochodzą z katalogu w kodzie, nie z modelu |

**Terminy:** przepisy o przejrzystości (art. 50) i obowiązki dla systemów wysokiego ryzyka z załącznika III
miały być stosowane od 2 sierpnia 2026 r. Harmonogram dla systemów wysokiego ryzyka **mógł zostać zmieniony
(pakiet Digital Omnibus)** — sprawdź aktualny stan przed wdrożeniem.

## RODO

| Zasada | Realizacja |
|---|---|
| Minimalizacja (art. 5 ust. 1 lit. c) | kandydat i firmy są fikcyjne; jedyne dane osobowe to treść wpisana przez trenującego. Przed wysłaniem do modelu `privacy.mask_pii` maskuje e-maile, telefony, PESEL, numery kont i adresy |
| Ograniczenie przechowywania (art. 5 ust. 1 lit. e) | dane w lokalnej bazie SQLite; `SYMULATOR_RETENTION_DAYS` usuwa starsze sesje przy starcie; każdą sesję można usunąć (`DELETE /api/sessions/{id}`, przycisk „Usuń”) |
| Dane szczególnych kategorii (art. 9) | aplikacja uczy **nie** zbierać takich danych; scenariusze zawierają wyłącznie fikcyjne informacje (pułapki) |
| Podmiot przetwarzający (art. 28) | w trybie AI treść pytań i transkrypt trafiają do dostawcy modelu — wymagana umowa powierzenia i ocena transferu; w trybie offline nic nie opuszcza serwera |
| Przejrzystość wobec trenującego (art. 13) | trenujący powinien wiedzieć, jakie dane są zapisywane (transkrypty, raporty), jak długo i kto ma dostęp — przygotuj klauzulę informacyjną przy wdrożeniu |

Transkrypty i raporty to dane osobowe **osoby trenującej** (opisują jej sposób prowadzenia rozmowy).
Domyślnie widzi je tylko ona; udostępnianie wyników przełożonym wymagałoby osobnej podstawy, informacji
i — w zależności od organizacji — konsultacji z przedstawicielstwem pracowników.

## Prawo pracy — pytania niedozwolone w rekrutacji

Detektor odwołuje się do poniższych przepisów (katalog `LEGAL_CATALOG` w `detector.py`):

| Kategoria | Przykład pytania | Podstawa wskazywana w aplikacji | Dozwolona alternatywa |
|---|---|---|---|
| rodzina, ciąża, stan cywilny | „Czy ma Pan dzieci?” | art. 18³ᵃ § 1 KP (zakaz dyskryminacji przy nawiązaniu stosunku pracy), art. 22¹ § 1 KP (zamknięty katalog danych kandydata) | pytanie o grafik wymagany na stanowisku |
| wiek | „Ile ma Pan lat?” | art. 18³ᵃ § 1 KP; data urodzenia tylko w ramach art. 22¹ KP | pytanie o doświadczenie |
| zdrowie, niepełnosprawność | „Czy często Pan choruje?” | art. 18³ᵃ § 1 i 22¹ KP, art. 9 RODO; zdolność do pracy ocenia lekarz w badaniach wstępnych (art. 229 KP) | opis konkretnych obowiązków stanowiska |
| religia | „Czy chodzi Pan do kościoła?” | art. 18³ᵃ § 1 i 22¹ KP, art. 9 RODO | pytanie o dostępność w grafiku |
| narodowość, pochodzenie | „Skąd Pan pochodzi?” | art. 18³ᵃ § 1 i 22¹ KP | prawo do pracy w Polsce, znajomość języka wymaganego na stanowisku |
| związki zawodowe | „Czy należy Pan do związku?” | art. 18³ᵃ § 1 KP, art. 3 ustawy o związkach zawodowych, art. 9 RODO | pytanie o rozwiązywanie sporów w zespole |
| orientacja seksualna | — | art. 18³ᵃ § 1 i 22¹ KP, art. 9 RODO | powrót do kompetencji |
| poglądy polityczne | „Na kogo Pan głosował?” | art. 18³ᵃ § 1 i 22¹ KP, art. 9 RODO | pytanie o zachowanie przy różnicy zdań |
| obecne lub poprzednie wynagrodzenie | „Ile Pan zarabia obecnie?” | art. 5 ust. 2 dyrektywy (UE) 2023/970 (zakaz pytania o historię wynagrodzeń; transpozycja do 7 czerwca 2026 r.); w Polsce wdrażany nowelizacją Kodeksu pracy o jawności wynagrodzeń — sprawdź aktualne brzmienie i numerację przepisów | „Jakie są Pana oczekiwania finansowe?” + widełki od pracodawcy |
| inne (np. karalność bez podstawy w przepisach, zadłużenie, wygląd) | „Czy był Pan karany?” | art. 22¹ § 1 i § 4 KP (inne dane tylko, gdy wymagają tego przepisy) | pytanie o kwalifikacje |

**Pułapki.** Kandydat w scenariuszach sam wspomina o sprawie prywatnej (dzieci, rehabilitacja, pochodzenie
rodziny, praktyki religijne, przynależność związkowa). Dopytywanie o nią jest oznaczane jako pytanie niedozwolone,
a raport pokazuje, czy trenujący „wszedł w pułapkę”. To odwzorowuje częstą sytuację: informacja podana
dobrowolnie przez kandydata nie uprawnia rekrutera do drążenia tematu ani do brania jej pod uwagę.

## Ograniczenia zgodności

- Reguły są heurystyczne (fałszywe alarmy i przeoczenia są możliwe); w trybie AI uzupełnia je klasyfikacja modelu.
- Katalog podstaw prawnych odzwierciedla stan wiedzy autora w chwili tworzenia — przepisy (zwłaszcza o jawności
  wynagrodzeń i harmonogram AI Act) mogą się zmienić.
- Aplikacja nie zastępuje szkolenia z prawa pracy ani konsultacji z działem prawnym.
