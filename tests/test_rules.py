"""Każda reguła: przykłady pozytywne (muszą zostać wykryte) i negatywne (fałszywe alarmy)."""
from __future__ import annotations

import pytest

from ogloszenia.engine import run_rule, run_rules, rx
from ogloszenia.rules import RULES, RULES_BY_ID

REQS_9 = "Wymagania:\n" + "\n".join(f"- umiejętność nr {i}" for i in range(1, 10))
REQS_8 = "Wymagania:\n" + "\n".join(f"- umiejętność nr {i}" for i in range(1, 9))
REQS_OPTIONAL = (
    "Wymagania:\n" + "\n".join(f"- umiejętność nr {i}" for i in range(1, 7))
    + "\n- mile widziana znajomość niemieckiego\n- atutem będzie SAP\n- znajomość Jira będzie plusem"
)
REQS_PLAIN = "Wymagania\n" + "\n".join(f"Umiejętność {i}" for i in range(1, 11))
NICE_TO_HAVE = "Mile widziane:\n" + "\n".join(f"- dodatek {i}" for i in range(1, 12))
LONG = (
    "Szukamy kogoś, kto odnajdzie się w naszym zespole i będzie potrafił samodzielnie organizować pracę przy "
    "jednoczesnej obsłudze wielu klientów oraz terminów, a także będzie chętnie dzielił się wiedzą z nowymi "
    "osobami w dziale, które dopiero zaczynają swoją karierę zawodową w kadrach."
)
LONG_RODO = (
    "Administratorem Twoich danych osobowych jest Przykład sp. z o.o., która przetwarza dane w celu "
    "przeprowadzenia bieżącej rekrutacji oraz, za Twoją zgodą, przyszłych rekrutacji przez okres dwunastu "
    "miesięcy, a Ty masz prawo dostępu do danych, ich sprostowania, usunięcia, ograniczenia przetwarzania i "
    "wniesienia skargi do organu nadzorczego."
)

CASES: dict[str, dict[str, list[str]]] = {
    "pay.missing": {
        "positive": [
            "Oferujemy atrakcyjne wynagrodzenie i pracę w zgranym zespole.",
            "Kasjer\nPraca w sklepie, umowa o pracę.",
            "Premia kwartalna do 2000 zł i karta sportowa.",
        ],
        "negative": [
            "Wynagrodzenie: 8 000–10 000 zł brutto/mies.",
            "Stawka 35 zł/h brutto.",
            "Widełki: 12-15k netto (B2B)",
            "Wynagrodzenie 7500–9000 brutto",
            "Widełki wynagrodzenia przekażemy przed pierwszą rozmową.",
            "Stawka: 30–35/h",
        ],
    },
    "pay.vague": {
        "positive": [
            "Oferujemy atrakcyjne wynagrodzenie.",
            "Konkurencyjne zarobki i premie.",
            "Wynagrodzenie zależne od doświadczenia.",
            "Wynagrodzenie do negocjacji.",
            "Zarobki bez limitu!",
        ],
        "negative": [
            "Oferujemy atrakcyjne wynagrodzenie: 9 000–11 000 zł brutto/mies.",
            "Gwarantujemy wysokie wynagrodzenie chorobowe.",
            "Wynagrodzenie: 8000 zł brutto miesięcznie, dobra atmosfera.",
        ],
    },
    "pay.details": {
        "positive": [
            "Wynagrodzenie: 7500–9000",
            "Wynagrodzenie 8000–9500 brutto",
            "Pensja 6000 zł",
            "Stawka 12-15k",
            "Wynagrodzenie: 7500–9000\n- karta sportowa z dopłatą 100 zł",
        ],
        "negative": [
            "Wynagrodzenie: 14 000–18 000 zł brutto miesięcznie",
            "Stawka: 100–125 zł netto + VAT za godzinę (B2B)",
            "Wynagrodzenie:\n9 000–11 000 zł\n(brutto, miesięcznie)",
            "Budżet szkoleniowy 4 000 zł rocznie.",
            "Dofinansowanie do karty sportowej 100 zł.",
            "Stawka godzinowa: 32 zł brutto.",
        ],
    },
    "pay.history": {
        "positive": [
            "Prosimy o podanie obecnego wynagrodzenia.",
            "W CV wpisz swoje poprzednie zarobki.",
            "Podaj aktualną pensję.",
            "Prosimy o informację o wynagrodzeniu w obecnym miejscu pracy.",
            "Prześlij zaświadczenie o zarobkach z poprzedniej pracy.",
            "Please include your current salary.",
            "Prosimy o podanie obecnego i oczekiwanego wynagrodzenia.",
            "Jaka była Twoja ostatnia pensja?",
        ],
        "negative": [
            "Prosimy o podanie oczekiwań finansowych.",
            "Jakie są Twoje oczekiwania finansowe?",
            "Nie pytamy o obecne wynagrodzenie.",
            "Wynagrodzenie nie niższe niż aktualna płaca minimalna.",
            "Aktualnie oferujemy wynagrodzenie 7000 zł brutto.",
            "Obecnie zatrudniamy 50 osób.",
            "Naliczanie list płac i rozliczanie PIT.",
        ],
    },
    "gender.title": {
        "positive": [
            "Programista Java\n\nOpis stanowiska.",
            "Księgowa\nBiuro rachunkowe w Gdańsku.",
            "O firmie\nStanowisko: Kierownik zmiany\nOpis.",
            "# Magazynier\nPraca w magazynie.",
            "Zatrudnimy kierowcę C+E",
            "Opiekunka osób starszych",
        ],
        "negative": [
            "Programista Java (K/M)",
            "Programista / Programistka Java",
            "Księgowy/a",
            "Asystent(ka) zarządu",
            "Java Developer",
            "Specjalista ds. kadr (k/m/x)",
            "Opiekun klienta (M/K)",
            "Młodszy specjalista / Młodsza specjalistka (f/m/d)",
            "Oferta pracy\nKierownik zmiany w treści ogłoszenia.",
            "Sprzedawca/czyni w sklepie",
        ],
    },
    "gender.body": {
        "positive": [
            "Jesteś zmotywowany i samodzielny?",
            "Idealny kandydat zna Excel.",
            "Szukamy kandydata z doświadczeniem.",
            "Kandydaci powinni znać SQL.",
        ],
        "negative": [
            "Jesteś osobą zmotywowaną.",
            "Jesteś gotowy/a na wyzwania?",
            "Szukamy kandydatów i kandydatek.",
            "Kandydat/ka powinien znać SQL.",
            "Kandydatka lub kandydat z doświadczeniem.",
            "Jesteś w stanie pracować w weekendy?",
            "Jesteśmy dynamicznym zespołem.",
        ],
    },
    "disc.age": {
        "positive": [
            "Szukamy osoby do 35 lat.",
            "Szukamy osoby do 35 lat z doświadczeniem.",
            "Wiek: 20–35 lat",
            "Kandydaci w wieku 25-40 lat.",
            "Wiek max. 40",
            "Nie starszy niż 50 lat.",
            "Mile widziani urodzeni po 1990 roku.",
            "Do 35. roku życia.",
            "Idealny kandydat ma 25–40 lat.",
        ],
        "negative": [
            "Minimum 3 lata doświadczenia.",
            "Do 10 lat doświadczenia w branży.",
            "Powyżej 10 lat komercyjnego doświadczenia.",
            "Umowa na okres do 2 lat.",
            "Wymagane ukończone 18 lat (sprzedaż alkoholu).",
            "Zatrudniamy osoby bez względu na wiek, w tym powyżej 50 lat.",
            "Firma działa na rynku od 25 lat.",
            "W CV podaj datę urodzenia.",
            "Praca w godzinach 8–16.",
            "10-15 lat doświadczenia zawodowego.",
        ],
    },
    "disc.age_indirect": {
        "positive": [
            "Dołącz do młodego zespołu!",
            "Pracujemy w młodym, dynamicznym zespole.",
            "Szukamy osoby młodej duchem.",
            "Zapraszamy młodych ludzi.",
            "Idealna praca w wieku studenckim.",
            "Młoda ekipa czeka!",
        ],
        "negative": [
            "Młodszy specjalista ds. kadr",
            "Stanowisko: Junior Developer",
            "Praca z młodzieżą w świetlicy.",
            "Pracujemy z młodymi ludźmi z domów dziecka.",
            "Zgrany zespół i przyjazna atmosfera.",
            "Młodsza księgowa w biurze rachunkowym.",
        ],
    },
    "disc.gender": {
        "positive": [
            "Zatrudnimy mężczyznę do pracy w magazynie.",
            "Szukamy pani do sprzątania biura.",
            "Tylko kobiety.",
            "Mężczyźni mile widziani.",
            "Płeć: mężczyzna",
            "Praca dla kobiet, lekka.",
            "Szukamy energicznej kobiety na recepcję.",
        ],
        "negative": [
            "Zatrudnimy kobiety i mężczyzn.",
            "Zapraszamy osoby bez względu na płeć.",
            "Program mentoringowy dla kobiet w IT.",
            "Administratorem Pani/Pana danych jest Firma.",
            "Szukamy osoby do pracy w magazynie.",
            "Szukamy pań i panów do obsługi klienta.",
        ],
    },
    "disc.family": {
        "positive": [
            "Osoba bez zobowiązań rodzinnych.",
            "Kandydatka nie planuje ciąży.",
            "Prosimy o informację o sytuacji rodzinnej.",
            "Liczba dzieci:",
            "Osoba bez dzieci, dyspozycyjna.",
            "Kawaler lub panna.",
            "Nie zatrudniamy matek.",
        ],
        "negative": [
            "Dodatkowe dni wolne dla rodziców.",
            "Praca z dziećmi w przedszkolu.",
            "Rejestracja bez zobowiązań finansowych.",
            "Wspieramy rodziców bez względu na sytuację rodzinną.",
            "Dofinansowanie do przedszkola dla dzieci pracowników.",
        ],
    },
    "disc.appearance": {
        "positive": [
            "Wymagamy miłej aparycji.",
            "Dobra prezencja.",
            "Atrakcyjny wygląd.",
            "Wzrost min. 170 cm.",
            "Szczupła sylwetka.",
            "Bez widocznych tatuaży.",
        ],
        "negative": [
            "Dbałość o atrakcyjny wygląd aplikacji.",
            "Dbanie o prezencję produktów na półkach.",
            "Wzrost sprzedaży o 20%.",
            "Dobra prezentacja wyników przed zarządem.",
            "Obowiązuje strój służbowy.",
        ],
    },
    "disc.nationality": {
        "positive": [
            "Native speaker języka angielskiego.",
            "Wymagamy: native speaker.",
            "Rodzimy użytkownik języka niemieckiego.",
            "Język ojczysty: polski.",
            "Tylko Polacy.",
            "Narodowość polska.",
            "Obywatelstwo polskie.",
            "Nie zatrudniamy obcokrajowców.",
            "Poszukujemy Polaka z pasją.",
            "Mówisz bez akcentu.",
        ],
        "negative": [
            "Znajomość React Native.",
            "Biegła znajomość języka angielskiego (C2).",
            "Tworzenie aplikacji native na iOS.",
            "Zatrudniamy osoby niezależnie od narodowości.",
            "Wymagane obywatelstwo polskie lub prawo do pracy w Polsce.",
            "Współpraca z Narodowym Bankiem Polskim.",
        ],
    },
    "disc.health": {
        "positive": [
            "Dobry stan zdrowia.",
            "Pełna sprawność fizyczna.",
            "Osoba niepaląca.",
            "Brak orzeczenia o niepełnosprawności.",
            "Nie zatrudniamy osób niepełnosprawnych.",
            "Bez chorób przewlekłych.",
            "Kandydat zdrowy psychicznie.",
        ],
        "negative": [
            "Aktualne badania lekarskie (medycyna pracy) zapewniamy.",
            "Brak przeciwwskazań zdrowotnych do pracy na wysokości.",
            "Dbanie o pełną sprawność systemów.",
            "Zapraszamy również osoby z orzeczeniem o niepełnosprawności.",
            "Praca wymaga podnoszenia ciężarów do 20 kg.",
            "Pakiet prywatnej opieki zdrowotnej.",
        ],
    },
    "disc.religion": {
        "positive": [
            "Kandydat praktykujący katolik.",
            "Osoba wierząca i praktykująca.",
            "Wyznanie: rzymskokatolickie.",
            "Wymagane świadectwo chrztu.",
            "Mile widziana wiara katolicka.",
            "Kandydat jest katolikiem.",
        ],
        "negative": [
            "Osoba wierząca w sukces zespołu.",
            "Praktykujący lekarz z prawem wykonywania zawodu.",
            "Zatrudniamy bez względu na religię i wyznanie.",
            "Szkolenia z różnorodności w miejscu pracy.",
        ],
    },
    "disc.other": {
        "positive": [
            "Przynależność związkowa: brak.",
            "Osoby niezrzeszone.",
            "Poglądy konserwatywne mile widziane.",
            "Orientacja seksualna: hetero.",
            "Kolor skóry: biały.",
        ],
        "negative": [
            "Bez względu na orientację seksualną i przynależność związkową.",
            "Orientacja na klienta.",
            "Pochodzenie produktów: Polska.",
        ],
    },
    "disc.photo": {
        "positive": [
            "CV ze zdjęciem prosimy przesyłać na adres.",
            "Prosimy o załączenie aktualnego zdjęcia.",
            "Mile widziane zdjęcie do CV.",
            "Zgłoszenia z aktualnym zdjęciem.",
            "Wymagane zdjęcie portretowe.",
        ],
        "negative": [
            "CV (bez zdjęcia) prześlij przez formularz.",
            "Zdjęcie w CV nie jest wymagane.",
            "Prosimy nie załączać zdjęć do CV.",
            "Obróbka zdjęć produktów w Photoshopie.",
            "Dodawanie zdjęć produktów do sklepu internetowego.",
        ],
    },
    "data.pesel": {
        "positive": [
            "Prosimy o podanie numeru PESEL.",
            "W CV: imię, nazwisko, PESEL.",
            "Nr PESEL",
        ],
        "negative": [
            "Prosimy nie podawać numeru PESEL.",
            "PESEL pobierzemy po podpisaniu umowy.",
            "Nie wymagamy numeru PESEL ani zdjęcia.",
            "Wymagamy: imię, nazwisko, data urodzenia.",
        ],
    },
    "data.id_document": {
        "positive": [
            "Prosimy o skan dowodu osobistego.",
            "Seria i numer dowodu.",
            "Numer paszportu.",
            "Kopia dokumentu tożsamości.",
        ],
        "negative": [
            "Weryfikacja numeru dowodu dostawy (WZ).",
            "Dowód osobisty okażesz w dniu podpisania umowy.",
            "Nie potrzebujemy numeru dowodu osobistego.",
        ],
    },
    "data.marital": {
        "positive": ["Stan cywilny: wolny.", "Podaj stan cywilny.", "Osoba zamężna."],
        "negative": [
            "Praca w Urzędzie Stanu Cywilnego.",
            "Nie pytamy o stan cywilny.",
            "Zatrudniamy bez względu na stan cywilny.",
        ],
    },
    "data.other": {
        "positive": [
            "Podaj imiona rodziców.",
            "Nazwisko rodowe.",
            "Numer konta bankowego.",
            "Adres zameldowania.",
            "Zaświadczenie o niekaralności.",
            "Link do profilu na Facebooku.",
            "Grupa krwi.",
        ],
        "negative": [
            "Numer konta podasz po zatrudnieniu.",
            "Zaświadczenie o niekaralności wymagane przez ustawę o ochronie osób i mienia.",
            "Link do profilu LinkedIn (opcjonalnie).",
        ],
    },
    "data.rodo_clause": {
        "positive": ["Programista\nOferujemy pracę zdalną.", "Krótki tekst bez klauzuli."],
        "negative": [
            "Administratorem danych osobowych jest Firma sp. z o.o.",
            "Klauzula informacyjna RODO w formularzu.",
            "Szczegóły: https://firma.example/rodo",
            "Dane przetwarzamy zgodnie z RODO.",
        ],
    },
    "data.outdated_consent": {
        "positive": [
            "zgodnie z ustawą z dnia 29 sierpnia 1997 r. o ochronie danych osobowych",
            "(Dz. U. z 2002 r. Nr 101, poz. 926)",
            "Ustawa o ochronie danych osobowych z 1997 r.",
        ],
        "negative": [
            "zgodnie z RODO (rozporządzenie (UE) 2016/679)",
            "Ustawa z dnia 29 sierpnia 1997 r. o ochronie danych osobowych nie obowiązuje od 2018 r.",
        ],
    },
    "clarity.location": {
        "positive": ["Programista Python\nUmowa o pracę.", "Kierowca\nPraca od zaraz."],
        "negative": [
            "Praca w Warszawie.",
            "Lokalizacja: Opole",
            "Praca w pełni zdalna.",
            "Biuro w Rzeszowie.",
            "Praca w magazynie w Strykowie.",
            "Praca z dowolnego miejsca w Polsce.",
        ],
    },
    "clarity.work_mode": {
        "positive": ["Analityk w Krakowie, umowa o pracę.", "Księgowa\nWarszawa"],
        "negative": [
            "Praca hybrydowa, Kraków.",
            "Praca stacjonarna w biurze.",
            "Remote",
            "Magazynier\nPraca w Strykowie.",
            "Kierowca kat. C",
        ],
    },
    "clarity.contract": {
        "positive": ["Oferujemy pracę w Warszawie.", "Programista\nWynagrodzenie 10 000 zł brutto"],
        "negative": [
            "Umowa o pracę na pełny etat.",
            "Współpraca B2B.",
            "Umowa zlecenie.",
            "Forma zatrudnienia: do wyboru.",
            "Wolontariat w schronisku.",
        ],
    },
    "clarity.duties": {
        "positive": [
            "Programista\nWymagania: Python.",
            "Oferujemy umowę o pracę.",
            "Wymagania obowiązkowe: Python.",
        ],
        "negative": [
            "Obowiązki: obsługa klienta.",
            "Twoje zadania: raporty.",
            "Czym będziesz się zajmować:",
            "Zakres obowiązków",
            "Będziesz odpowiedzialny za sprzedaż.",
            "Pełna treść oferty: https://firma.example/oferta",
        ],
    },
    "clarity.requirements_count": {
        "positive": [REQS_9, REQS_PLAIN],
        "negative": [REQS_8, REQS_OPTIONAL, NICE_TO_HAVE],
    },
    "clarity.jargon": {
        "positive": [
            "Szukamy ninja JavaScriptu.",
            "Jesteś rockstarem sprzedaży?",
            "Excel guru.",
            "Rodzinna atmosfera.",
            "Jesteśmy jak rodzina.",
            "Work hard, play hard!",
        ],
        "negative": [
            "Rodzinna firma z tradycjami.",
            "Przyjazna atmosfera w zespole.",
            "Animator i magik na imprezy dla dzieci.",
            "Zapewniamy rodzinną atmosferę hotelu naszym gościom.",
        ],
    },
    "clarity.long_sentence": {
        "positive": [LONG, "- " + LONG.replace(",", "")],
        "negative": [
            "Krótkie zdanie. Drugie krótkie zdanie.",
            LONG_RODO,
            "Szukamy osoby z doświadczeniem np. w obsłudze klienta, ok. 2 lata, ul. Długa 5. Praca od zaraz.",
        ],
    },
}


def _ids(rule_id: str, kind: str) -> list[tuple[str, str]]:
    return [(rule_id, text) for text in CASES[rule_id][kind]]


POSITIVE = [case for rid in CASES for case in _ids(rid, "positive")]
NEGATIVE = [case for rid in CASES for case in _ids(rid, "negative")]


def test_every_rule_has_positive_and_negative_examples():
    missing = [r.id for r in RULES if r.id not in CASES]
    assert not missing, f"Reguły bez przykładów: {missing}"
    for rule_id, cases in CASES.items():
        assert rule_id in RULES_BY_ID, rule_id
        assert len(cases["positive"]) >= 2 and len(cases["negative"]) >= 2, rule_id


@pytest.mark.parametrize(("rule_id", "text"), POSITIVE, ids=[f"{r}|{t[:40]}" for r, t in POSITIVE])
def test_positive_examples_are_detected(rule_id, text):
    findings = run_rule(RULES_BY_ID[rule_id], text)
    assert findings, f"{rule_id} nie wykrył problemu w: {text!r}"
    for f in findings:
        if f.start is not None:
            assert text[f.start:f.end] == f.fragment


@pytest.mark.parametrize(("rule_id", "text"), NEGATIVE, ids=[f"{r}|{t[:40]}" for r, t in NEGATIVE])
def test_negative_examples_are_not_flagged(rule_id, text):
    findings = run_rule(RULES_BY_ID[rule_id], text)
    assert not findings, f"{rule_id} — fałszywy alarm: {[f.fragment for f in findings]} w {text!r}"


# --- integralność danych reguł ------------------------------------------------------------------------

def test_rule_ids_unique_and_regexes_compile():
    ids = [r.id for r in RULES]
    assert len(ids) == len(set(ids))
    for rule in RULES:
        for pattern in rule.patterns:
            rx(pattern.regex)
        for regex in (*rule.exceptions, *rule.sentence_exceptions, *rule.doc_exceptions):
            rx(regex)


@pytest.mark.parametrize("rule", RULES, ids=[r.id for r in RULES])
def test_rule_is_complete_data(rule):
    assert rule.title and rule.explanation and rule.legal_basis and rule.suggestion
    assert rule.exceptions or rule.sentence_exceptions or rule.doc_exceptions, "reguła bez wyjątków"
    if rule.kind == "pattern":
        assert rule.patterns
    if rule.kind == "missing":
        assert rule.patterns and rule.template and "[UZUPEŁNIJ:" in rule.template
    if rule.kind == "check":
        assert rule.check
    if rule.only_if_absent:
        assert RULES_BY_ID[rule.only_if_absent].kind == "missing"


def test_pay_rules_carry_verification_note():
    for rule_id in ("pay.missing", "pay.vague", "pay.details", "pay.history", "gender.title"):
        assert "zweryfikuj aktualny stan prawny" in RULES_BY_ID[rule_id].legal_basis


def test_all_five_categories_covered():
    assert {r.category for r in RULES} == {
        "pay_transparency", "gender_neutrality", "discrimination", "excessive_data", "clarity"
    }


# --- pułapki fałszywych alarmów na pełnych tekstach -------------------------------------------------

@pytest.mark.parametrize("text", [
    "Młodszy specjalista ds. kadr (K/M)",
    "Junior Developer — dołącz do zgranego zespołu",
    "W CV podaj imię i nazwisko, datę urodzenia oraz dane kontaktowe.",
    "Jakie są Twoje oczekiwania finansowe?",
    "Znajomość React Native mile widziana.",
    "Zapraszamy osoby bez względu na wiek, płeć, niepełnosprawność, religię, wyznanie i narodowość.",
])
def test_false_positive_traps_produce_no_problem_findings(text):
    flagged = {f.rule_id for f in run_rules(text) if f.start is not None}
    assert not flagged, flagged


@pytest.mark.parametrize("name", ["programista-dobre.txt", "mlodszy-specjalista-dobre.txt"])
def test_good_examples_have_no_findings(example_text, name):
    assert run_rules(example_text(name)) == []


@pytest.mark.parametrize("name", ["magazynier-zle.txt", "recepcjonistka-zle.txt", "handlowiec-zle.txt"])
def test_bad_examples_have_errors(example_text, name):
    findings = run_rules(example_text(name))
    assert sum(f.severity == "error" for f in findings) >= 5


def test_offsets_handle_polish_characters():
    text = "Żółta łódź — Kierownik zmiany\nSzukamy osoby do 35 lat."
    findings = run_rules(text)
    for f in findings:
        if f.start is not None:
            assert text[f.start:f.end] == f.fragment
    assert any(f.fragment == "do 35 lat" for f in findings)


def test_case_insensitive_and_inflection():
    for text in ("NATIVE SPEAKER", "Native Speakerem języka niemieckiego", "native-speaker"):
        assert run_rule(RULES_BY_ID["disc.nationality"], text)
    for text in ("młodego zespołu", "młodym zespole", "MŁODY ZESPÓŁ", "młodą ekipą"):
        assert run_rule(RULES_BY_ID["disc.age_indirect"], text), text


def test_salary_details_lists_missing_aspects():
    [finding] = run_rule(RULES_BY_ID["pay.details"], "Wynagrodzenie 8000–9500 brutto")
    assert "waluty" in finding.explanation and "okresu" in finding.explanation
    assert "brutto/netto" not in finding.explanation


def test_title_finding_names_both_forms():
    [finding] = run_rule(RULES_BY_ID["gender.title"], "Księgowa\nopis")
    assert finding.fragment == "Księgowa"
    assert "Księgowy / Księgowa" in finding.suggestion
    assert finding.fix and finding.fix.text == " (K/M)"


def test_requirements_count_reports_number():
    [finding] = run_rule(RULES_BY_ID["clarity.requirements_count"], REQS_9)
    assert "9 pozycji" in finding.explanation and finding.fragment == "Wymagania:"


def test_findings_sorted_with_document_level_last():
    findings = run_rules("Magazynier\nSzukamy osoby do 35 lat.")
    positions = [f.start for f in findings]
    first_none = positions.index(None)
    assert all(p is not None for p in positions[:first_none])
    assert all(p is None for p in positions[first_none:])
    assert [f.id for f in findings] == [f"f{i}" for i in range(1, len(findings) + 1)]
