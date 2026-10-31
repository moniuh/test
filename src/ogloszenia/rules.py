"""Reguły audytu ogłoszeń jako dane.

Każda reguła to rekord `Rule`: wzorce regex (wielkość liter bez znaczenia, końcówki fleksyjne
zapisane jawnie), wyjątki, wyjaśnienie, podstawa prawna i sposób poprawki. Silnik (`engine.py`)
nie zna treści reguł — tylko interpretuje te dane. Reguły typu `check` wskazują nazwę funkcji
sprawdzającej, a ich parametry (progi) są w `params`.

Podstawy prawne podajemy ostrożnie: tam, gdzie numer przepisu krajowego nie jest pewny,
odsyłamy do nowelizacji i prosimy o weryfikację aktualnego stanu prawnego.
"""
from __future__ import annotations

from .models import Rule, RulePattern

P = RulePattern
VERIFY = "zweryfikuj aktualny stan prawny"

# --- Wspólne fragmenty wzorców -------------------------------------------------------------
CURRENCY = r"(?:zł(?:otych|ote|oty)?|pln|eur(?:o)?|€|usd|\$|chf|gbp|£)(?![a-ząćęłńóśźż])"
NUM_BIG = r"(?:\d{1,3}(?:[  .]\d{3})+|\d{4,7})(?:[.,]\d{1,2})?(?!\s*(?:r\.|rok|roku)\b)"
NUM_K = r"\d{1,3}(?:[.,]\d{1,2})?\s*(?:tys\.?|k)(?![a-ząćęłńóśźż])"
NUM_ANY = r"\d+(?:[  .]\d{3})*(?:[.,]\d{1,2})?"
RANGE_SEP = r"\s*(?:-|–|—|do)\s*"
SALARY_WORD = (
    r"(?:wynagrodzeni\w*|widełk\w*|pensj\w*|stawk\w*|zarob\w*|uposażeni\w*|salary|płac[aąy]\b)"
)
# Kwota wynagrodzenia: liczba z walutą albo słowo „wynagrodzenie/widełki/stawka” z liczbą w tej linii.
SALARY_AMOUNT = (
    rf"(?:od\s+)?{NUM_ANY}(?:\s*(?:tys\.?|k))?(?:{RANGE_SEP}{NUM_ANY})?\s*(?:tys\.?|k)?\s*{CURRENCY}",
    rf"{SALARY_WORD}[^\n\d]{{0,40}}(?:{NUM_BIG}|{NUM_K}|\d{{1,3}}(?:[.,]\d{{1,2}})?{RANGE_SEP}{NUM_K})"
    rf"(?:{RANGE_SEP}(?:{NUM_BIG}|{NUM_K}|{NUM_ANY}))?",
    rf"{SALARY_WORD}[^\n\d]{{0,40}}\d{{2,3}}(?:[.,]\d{{1,2}})?(?:{RANGE_SEP}\d{{2,3}}(?:[.,]\d{{1,2}})?)?"
    r"\s*(?:/\s*(?:h|godz)|za\s+godzin|na\s+godzin)",
)
CURRENCY_PRESENT = rf"(?:\d\s*|\b){CURRENCY}"
GROSS_NET = r"\bbrutto\b|\bnetto\b|\bgross\b|\bnet\b|\+\s*vat\b|\bna\s+rękę\b"
PERIOD = (
    r"/\s*(?:h|godz\w*|mies\w*|msc|m-c|mc|rok|dzień|dzien)\b|\bmiesięczn\w*|\bmies\.|\bmiesiąc\w*"
    r"|\bm-c\b|\bmsc\b|\brocz\w*|\bgodzin\w*|\bgodz\.|\bdzienn\w*|\bna\s+rok\b|\bper\s+(?:month|hour|year)\b"
    r"|\bza\s+(?:godzinę|dzień|miesiąc)\b|\bdniówk\w*|\bgodzinow\w*"
)
# Zdanie z kwotą świadczenia dodatkowego (premia, karta sportowa), w którym nie ma mowy o wynagrodzeniu.
BENEFIT_ONLY = (
    r"^(?!.*(?:wynagrodz|pensj|widełk|stawk|zarob|płac[aąy]\b|salary)).*\b(?:budżet|dofinansowan|dopłat|kart[aęy]"
    r"|bon(?:y|ów)?\b|premi|bonus|nagrod|dodat|ryczałt|zwrot|voucher|pakiet|polecen|kaucj|szkoleni|benefit"
    r"|kafeteri|lunch|posiłk|abonament|opłat|grant|stypendi)"
)
# Klauzula równego traktowania — w jej zdaniu wyliczenie cech nie jest dyskryminacją.
EQUALITY = (
    r"bez\s+względu\s+na|niezależnie\s+od|bez\s+dyskryminacji|równ\w*\s+(?:traktowani\w*|szans\w*)"
    r"|nie\s+dyskryminuj\w*|nie\s+ma\s+znaczenia"
)
# Zdania typu „nie podawaj PESEL”, „nie wymagamy zdjęcia”.
NEGATED_REQUEST = (
    r"\bnie\s+(?:podawaj\w*|podawać|wpisuj\w*|umieszczaj\w*|przesyłaj\w*|załączaj\w*|dołączaj\w*"
    r"|dodawaj\w*|wymagamy|potrzebujemy|zbieramy|pytamy|prosimy)|\bnie\s+(?:jest|są)\s+(?:wymagan|potrzebn)\w*"
)
AFTER_HIRING = (
    r"po\s+(?:zatrudnieniu|podpisaniu|przyjęciu|nawiązaniu)|przy\s+(?:podpisaniu|zawarciu|podpisywaniu)\s+umowy"
    r"|w\s+dniu\s+(?:rozpoczęcia|podpisania)|przed\s+podpisaniem\s+umowy|na\s+etapie\s+zatrudnienia"
)
REQUIRED_BY_LAW = (
    r"wymagan\w*\s+(?:przez|przepis|ustaw)\w*|zgodnie\s+z\s+(?:ustaw|przepis)\w*|z\s+mocy\s+(?:ustawy|prawa)"
    r"|w\s+przypadkach?\s+przewidzianych|wymóg\s+ustawowy|ustaw\w*\s+o\s+\w+"
)

CITIES = (
    r"warszaw\w*|krak[oó]w\w*|wrocław\w*|wroclaw\w*|pozna[nń]\w*|gdańsk\w*|gdansk\w*|łód[zź]\w*|łodzi"
    r"|katowic\w*|lublin\w*|szczecin\w*|bydgoszcz\w*|białymstoku|białystok\w*|białegostoku|rzesz[oó]w\w*"
    r"|gdyni\w*|gdynia|sopo\w*|toru[nń]\w*|olsztyn\w*|kielc\w*|opol\w*|częstochow\w*|radom\w*|gliwic\w*"
    r"|zielon\w*\s+gór\w*|trójmiast\w*|cała\s+polska|całej\s+polski|terenie\s+(?:całego\s+)?kraju"
)

HARD_ADJ = r"(?:y|ego|emu|ym|ej|ą|a|e|ych|ymi)"
SOFT_ADJ = r"(?:i|iego|iemu|im|iej|ią|ia|ie|ich|imi)"
PAY_NOUN = r"(?:wynagrodzeni\w*|zarobk\w*|zarobki|pensj\w*|płac[aąy]\b|uposażeni\w*|pobor\w*)"

_HARD_ENDINGS = ("y", "ego", "emu", "ym", "ej", "ą", "a", "e", "ych", "ymi")
_SOFT_TO_HARD = {"i": "y", "iego": "ego", "iemu": "emu", "im": "ym", "iej": "ej", "ią": "ą",
                 "ia": "a", "ie": "e", "ich": "ych", "imi": "ymi"}


def _pay_history_word_map() -> dict[str, str]:
    """„obecnego wynagrodzenia” → „oczekiwanego wynagrodzenia” (z zachowaniem przypadka)."""
    mapping: dict[str, str] = {}
    for stem in ("obecn", "aktualn", "dotychczasow", "wcześniejsz"):
        for ending in _HARD_ENDINGS:
            mapping[stem + ending] = "oczekiwan" + ending
    for stem in ("poprzedn", "ostatn"):
        for soft, hard in _SOFT_TO_HARD.items():
            mapping[stem + soft] = "oczekiwan" + hard
    return mapping


# Słownik par nazw stanowisk: (forma męska, forma żeńska, regex form męskich, regex form żeńskich).
GENDERED_TITLES: tuple[tuple[str, str, str, str], ...] = (
    ("Programista", "Programistka", r"programist(?:a|y|ę|ą|ów)", r"programistk(?:a|i|ę|ą)"),
    ("Specjalista", "Specjalistka", r"specjalist(?:a|y|ę|ą|ów)", r"specjalistk(?:a|i|ę|ą)"),
    ("Analityk", "Analityczka", r"analityk(?:a|iem|ów)?", r"analityczk(?:a|i|ę|ą)"),
    ("Księgowy", "Księgowa", r"księgow(?:y|ego|ym)", r"księgow(?:a|ej|ą)"),
    ("Kierownik", "Kierowniczka", r"kierownik(?:a|iem|ów)?", r"kierowniczk(?:a|i|ę|ą)"),
    ("Kierowca", "Kierowczyni", r"kierowc(?:a|y|ę|ą|ów)", r"kierowczyn(?:i|ię|ią)"),
    ("Magazynier", "Magazynierka", r"magazynier(?:a|em|ów)?", r"magazynierk(?:a|i|ę|ą)"),
    ("Sprzedawca", "Sprzedawczyni", r"sprzedawc(?:a|y|ę|ą|ów)", r"sprzedawczyn(?:i|ię|ią)"),
    ("Kasjer", "Kasjerka", r"kasjer(?:a|em|ów)?", r"kasjerk(?:a|i|ę|ą)"),
    ("Asystent", "Asystentka", r"asystent(?:a|em|ów)?", r"asystentk(?:a|i|ę|ą)"),
    ("Sekretarz", "Sekretarka", r"sekretarz(?:a|em)?", r"sekretark(?:a|i|ę|ą)"),
    ("Recepcjonista", "Recepcjonistka", r"recepcjonist(?:a|y|ę|ą)", r"recepcjonistk(?:a|i|ę|ą)"),
    ("Pielęgniarz", "Pielęgniarka", r"pielęgniarz(?:a|em|y)?", r"pielęgniark(?:a|i|ę|ą)"),
    ("Opiekun", "Opiekunka", r"opiekun(?:a|em|ów)?", r"opiekunk(?:a|i|ę|ą)"),
    ("Konsultant", "Konsultantka", r"konsultant(?:a|em|ów)?", r"konsultantk(?:a|i|ę|ą)"),
    ("Doradca", "Doradczyni", r"doradc(?:a|y|ę|ą|ów)", r"doradczyn(?:i|ię|ią)"),
    ("Przedstawiciel", "Przedstawicielka", r"przedstawiciel(?:a|em|i)?", r"przedstawicielk(?:a|i|ę|ą)"),
    ("Menedżer", "Menedżerka", r"men[ea]dżer(?:a|em|ów)?", r"men[ea]dżerk(?:a|i|ę|ą)"),
    ("Dyrektor", "Dyrektorka", r"dyrektor(?:a|em)?", r"dyrektork(?:a|i|ę|ą)"),
    ("Koordynator", "Koordynatorka", r"koordynator(?:a|em|ów)?", r"koordynatork(?:a|i|ę|ą)"),
    ("Projektant", "Projektantka", r"projektant(?:a|em|ów)?", r"projektantk(?:a|i|ę|ą)"),
    ("Inżynier", "Inżynierka", r"inżynier(?:a|em|ów)?", r"inżynierk(?:a|i|ę|ą)"),
    ("Tester", "Testerka", r"tester(?:a|em|ów)?", r"testerk(?:a|i|ę|ą)"),
    ("Grafik", "Graficzka", r"grafik(?:a|iem|ów)?", r"graficzk(?:a|i|ę|ą)"),
    ("Kucharz", "Kucharka", r"kucharz(?:a|em|y)?", r"kuchark(?:a|i|ę|ą)"),
    ("Kelner", "Kelnerka", r"kelner(?:a|em|ów)?", r"kelnerk(?:a|i|ę|ą)"),
    ("Sprzątacz", "Sprzątaczka", r"sprzątacz(?:a|em|y)?", r"sprzątaczk(?:a|i|ę|ą)"),
    ("Technik", "Techniczka", r"technik(?:a|iem|ów)?", r"techniczk(?:a|i|ę|ą)"),
    ("Monter", "Monterka", r"monter(?:a|em|ów)?", r"monterk(?:a|i|ę|ą)"),
    ("Elektryk", "Elektryczka", r"elektryk(?:a|iem|ów)?", r"elektryczk(?:a|i|ę|ą)"),
    ("Mechanik", "Mechaniczka", r"mechanik(?:a|iem|ów)?", r"mechaniczk(?:a|i|ę|ą)"),
    ("Administrator", "Administratorka", r"administrator(?:a|em|ów)?", r"administratork(?:a|i|ę|ą)"),
    ("Architekt", "Architektka", r"architekt(?:a|em|ów)?", r"architektk(?:a|i|ę|ą)"),
    ("Lekarz", "Lekarka", r"lekarz(?:a|em|y)?", r"lekark(?:a|i|ę|ą)"),
    ("Nauczyciel", "Nauczycielka", r"nauczyciel(?:a|em|i)?", r"nauczycielk(?:a|i|ę|ą)"),
    ("Pracownik", "Pracowniczka", r"pracownik(?:a|iem|ów)?", r"pracowniczk(?:a|i|ę|ą)"),
    ("Operator", "Operatorka", r"operator(?:a|em|ów)?", r"operatork(?:a|i|ę|ą)"),
    ("Logistyk", "Logistyczka", r"logistyk(?:a|iem|ów)?", r"logistyczk(?:a|i|ę|ą)"),
    ("Kontroler", "Kontrolerka", r"kontroler(?:a|em|ów)?", r"kontrolerk(?:a|i|ę|ą)"),
    ("Redaktor", "Redaktorka", r"redaktor(?:a|em|ów)?", r"redaktork(?:a|i|ę|ą)"),
    ("Tłumacz", "Tłumaczka", r"tłumacz(?:a|em|y)?", r"tłumaczk(?:a|i|ę|ą)"),
    ("Rekruter", "Rekruterka", r"rekruter(?:a|em|ów)?", r"rekruterk(?:a|i|ę|ą)"),
    ("Ekspert", "Ekspertka", r"ekspert(?:a|em|ów)?", r"ekspertk(?:a|i|ę|ą)"),
    ("Referent", "Referentka", r"referent(?:a|em|ów)?", r"referentk(?:a|i|ę|ą)"),
    ("Informatyk", "Informatyczka", r"informatyk(?:a|iem|ów)?", r"informatyczk(?:a|i|ę|ą)"),
    ("Handlowiec", "Handlowczyni", r"handlowiec|handlowc(?:a|em|ów)", r"handlowczyn(?:i|ię|ią)"),
    ("Fizjoterapeuta", "Fizjoterapeutka", r"fizjoterapeut(?:a|y|ę|ą)", r"fizjoterapeutk(?:a|i|ę|ą)"),
    ("Farmaceuta", "Farmaceutka", r"farmaceut(?:a|y|ę|ą)", r"farmaceutk(?:a|i|ę|ą)"),
    ("Psycholog", "Psycholożka", r"psycholog(?:a|iem)?", r"psycholożk(?:a|i|ę|ą)"),
    ("Prawnik", "Prawniczka", r"prawnik(?:a|iem|ów)?", r"prawniczk(?:a|i|ę|ą)"),
    ("Barista", "Baristka", r"barist(?:a|y|ę|ą)", r"baristk(?:a|i|ę|ą)"),
    ("Fryzjer", "Fryzjerka", r"fryzjer(?:a|em|ów)?", r"fryzjerk(?:a|i|ę|ą)"),
    ("Spawacz", "Spawaczka", r"spawacz(?:a|em|y)?", r"spawaczk(?:a|i|ę|ą)"),
    ("Brygadzista", "Brygadzistka", r"brygadzist(?:a|y|ę|ą)", r"brygadzistk(?:a|i|ę|ą)"),
    ("Laborant", "Laborantka", r"laborant(?:a|em|ów)?", r"laborantk(?:a|i|ę|ą)"),
    ("Ratownik", "Ratowniczka", r"ratownik(?:a|iem|ów)?", r"ratowniczk(?:a|i|ę|ą)"),
    ("Kurier", "Kurierka", r"kurier(?:a|em|ów)?", r"kurierk(?:a|i|ę|ą)"),
)

# Oznaczenia neutralności tytułu: „(K/M)”, „(k/m/x)”, „(M/K)”, „(f/m/d)”, „Programista/-ka”, „Księgowy/a”.
TITLE_NEUTRAL_MARKERS = (
    r"\(\s*[kmfxdn]\s*(?:[/|,]\s*[kmfxdn]\s*)+\)",
    r"\(\s*(?:kobieta|mężczyzna)\s*/\s*(?:kobieta|mężczyzna)\s*\)",
    r"\w\s*/\s*-?\s*(?:ka|a|czka|czyni|yni|ini)\b",
    r"\w\s*\(\s*-?\s*(?:ka|a|czka|czyni|yni|ini)\s*\)",
)

PAY_LAW = (
    "Dyrektywa (UE) 2023/970, art. 5 ust. 1 (informacja o wynagrodzeniu początkowym lub jego przedziale "
    "przed rozmową kwalifikacyjną); Kodeks pracy po nowelizacji obowiązującej od 24.12.2025 r. — " + VERIFY
)
PAY_HISTORY_LAW = (
    "Dyrektywa (UE) 2023/970, art. 5 ust. 2 (zakaz pytania o historię wynagrodzeń); Kodeks pracy po "
    "nowelizacji obowiązującej od 24.12.2025 r. — " + VERIFY
)
GENDER_LAW = (
    "Dyrektywa (UE) 2023/970, art. 5 ust. 3 (neutralne płciowo ogłoszenia i nazwy stanowisk); Kodeks pracy "
    "art. 18³ᵃ (równe traktowanie bez względu na płeć) — " + VERIFY
)


def disc_law(trait: str) -> str:
    return (
        "Kodeks pracy art. 18³ᵃ § 1 (równe traktowanie w zakresie nawiązania stosunku pracy, w szczególności "
        f"bez względu na {trait})"
    )


DATA_LAW = (
    "Kodeks pracy art. 22¹ § 1 (katalog danych od kandydata: imię i nazwisko, data urodzenia, dane kontaktowe, "
    "wykształcenie, kwalifikacje zawodowe, przebieg zatrudnienia); RODO art. 5 ust. 1 lit. c (minimalizacja danych)"
)
GOOD_PRACTICE = "Dobra praktyka (brak bezpośredniego wymogu prawnego)"

PAY_TEMPLATE_MARK = "[UZUPEŁNIJ: widełki wynagrodzenia"

RULES: tuple[Rule, ...] = (
    # ---------------------------------------------------------------- jawność wynagrodzeń
    Rule(
        id="pay.missing",
        category="pay_transparency",
        severity="error",
        kind="missing",
        title="Brak informacji o wynagrodzeniu",
        patterns=tuple(P(regex=r) for r in SALARY_AMOUNT),
        sentence_exceptions=(BENEFIT_ONLY,),
        doc_exceptions=(
            r"(?:widełk\w*|wynagrodzeni\w*|stawk\w*)[^\n.]{0,80}\bprzed\s+(?:pierwsz\w*\s+)?rozmow\w*",
            r"\bprzed\s+(?:pierwsz\w*\s+)?rozmow\w*[^\n.]{0,80}(?:widełk\w*|wynagrodzeni\w*|stawk\w*)",
        ),
        explanation=(
            "Ogłoszenie nie podaje kwoty ani przedziału wynagrodzenia. Kandydat ma prawo poznać wynagrodzenie "
            "początkowe lub jego przedział przed rozmową — najprościej podać widełki w ogłoszeniu. Jeśli "
            "przekazujecie je przed rozmową, napisz to w ogłoszeniu."
        ),
        legal_basis=PAY_LAW,
        suggestion="Dodaj widełki, np. „Wynagrodzenie: 9 000–11 000 zł brutto miesięcznie (umowa o pracę)”.",
        template="Wynagrodzenie: [UZUPEŁNIJ: widełki wynagrodzenia brutto/mies. w zł]",
    ),
    Rule(
        id="pay.vague",
        category="pay_transparency",
        severity="warning",
        title="Ogólnik zamiast kwoty wynagrodzenia",
        patterns=(
            P(
                regex=(
                    r"\b(?:atrakcyjn|konkurencyjn|adekwatn|godn|motywując|satysfakcjonując|wysok|ponadprzeciętn"
                    r"|dobr|uczciw|stabiln|niebanaln|odpowiedni|rynkow)\w*\s+(?P<n>" + PAY_NOUN + r")"
                ),
                replace=r"\g<n> [UZUPEŁNIJ: widełki wynagrodzenia brutto/mies. w zł]",
            ),
            P(
                regex=(
                    r"\b(?P<n>wynagrodzeni\w*|stawk\w*|zarobki)\s+(?:do\s+(?:negocjacji|uzgodnienia|ustalenia)"
                    r"|(?:uzależnion|zależn)\w*\s+od\s+(?:posiadan\w*\s+|twoich\s+)?(?:umiejętno|kwalifikacj"
                    r"|doświadczeni|kompetencj|możliwo|wynik|zaangażowani)\w*"
                    r"|(?:adekwatn|odpowiedni)\w*\s+do\s+(?:posiadan\w*\s+|twoich\s+)?(?:umiejętno|kwalifikacj"
                    r"|doświadczeni|kompetencj|możliwo|wynik|zaangażowani)\w*|na\s+poziomie\s+rynkowym)"
                ),
                replace=r"\g<n> [UZUPEŁNIJ: widełki wynagrodzenia brutto/mies. w zł]",
            ),
            P(regex=r"\b(?:nielimitowan\w*\s+zarobk\w*|zarobki\s+bez\s+(?:limitu|ograniczeń))"),
        ),
        exceptions=(r"wynagrodzeni\w*\s+(?:chorobow|urlopow|za\s+nadgodzin|za\s+czas\s+choroby)\w*",),
        only_if_absent="pay.missing",
        explanation=(
            "Sformułowania typu „atrakcyjne wynagrodzenie” bez liczb nie spełniają wymogu przekazania "
            "informacji o wynagrodzeniu początkowym lub jego przedziale i utrudniają porównanie ofert."
        ),
        legal_basis=PAY_LAW,
        suggestion="Zastąp ogólnik konkretnym przedziałem, np. „9 000–11 000 zł brutto/mies.”.",
    ),
    Rule(
        id="pay.details",
        category="pay_transparency",
        severity="warning",
        kind="check",
        check="salary_details",
        title="Niepełna informacja o wynagrodzeniu",
        patterns=tuple(P(regex=r) for r in SALARY_AMOUNT),
        sentence_exceptions=(BENEFIT_ONLY,),
        exceptions=(r"(?:płac\w*|wynagrodzeni\w*)\s+minimaln\w*[^\n]{0,40}",),
        explanation=(
            "Podana kwota nie mówi, czy to brutto czy netto, w jakiej walucie lub za jaki okres. Bez tego "
            "kandydat nie oceni oferty, a porównywanie wynagrodzeń jest utrudnione."
        ),
        legal_basis=PAY_LAW,
        suggestion="Doprecyzuj kwotę: waluta, brutto/netto i okres, np. „12 000–15 000 zł brutto/mies.”.",
    ),
    Rule(
        id="pay.history",
        category="pay_transparency",
        severity="error",
        title="Pytanie o obecne lub poprzednie wynagrodzenie",
        patterns=(
            P(
                regex=(
                    r"\b(?:(?:obecn|aktualn|dotychczasow|wcześniejsz)" + HARD_ADJ + r"|(?:poprzedn|ostatn)"
                    + SOFT_ADJ + r")\s+(?:i|oraz)\s+(?P<o>oczekiwan\w*)(?=\s+(?:wynagrodz|zarob|pensj|staw))"
                ),
                replace=r"\g<o>",
            ),
            P(
                regex=(
                    r"\b(?:(?:obecn|aktualn|dotychczasow|wcześniejsz)" + HARD_ADJ + r"|(?:poprzedn|ostatn)" + SOFT_ADJ
                    + r")\s+" + PAY_NOUN + r"(?=\s+(?:i|oraz)\s+oczekiwa)"
                ),
                fix="remove_item",
            ),
            P(regex=r"\b(?:obecn|aktualn|dotychczasow|wcześniejsz)" + HARD_ADJ + r"\s+" + PAY_NOUN),
            P(regex=r"\b(?:poprzedn|ostatn)" + SOFT_ADJ + r"\s+" + PAY_NOUN),
            P(
                regex=(
                    r"\b(?:wynagrodzeni\w*|zarobk\w*|zarobki|pensj\w*)\s+(?:w|z|u)\s+(?:obecn|aktualn"
                    r"|dotychczasow|poprzedn|ostatn|wcześniejsz)\w*\s+(?:miejsc\w*\s+pracy|firm\w*|prac\w*"
                    r"|pracodawc\w*|zatrudnieni\w*)"
                ),
                fix="remove_item",
            ),
            P(regex=r"\bhistori\w*\s+(?:wynagrodze\w*|zarobk\w*|zarobków|płac)\b", fix="remove_item"),
            P(regex=r"\b(?:current|previous|past)\s+salary\b|\bsalary\s+history\b", fix="remove_item"),
            P(
                regex=(
                    r"\b(?:zaświadczeni\w*\s+o\s+(?:zarobkach|wynagrodzeniu|dochodach)|pas(?:ek|ka)\s+"
                    r"(?:płacow\w*|wypłaty)|odcin(?:ek|ka)\s+wypłaty)"
                    r"(?:\s+(?:z|w)\s+(?:obecn|aktualn|poprzedn|ostatn|dotychczasow)\w*\s+(?:prac\w*|miejsc\w*\s+pracy"
                    r"|firm\w*|pracodawc\w*))?"
                ),
                fix="remove_item",
            ),
        ),
        exceptions=(r"(?:płac\w*|wynagrodzeni\w*)\s+minimaln\w*",),
        sentence_exceptions=(r"\bnie\s+(?:pytamy|prosimy|wymagamy|zbieramy|oczekujemy\s+informacji)",),
        explanation=(
            "Pracodawca nie powinien pytać kandydata o wynagrodzenie w obecnym ani poprzednim miejscu pracy — "
            "utrwala to lukę płacową. Pytanie o oczekiwania finansowe jest dozwolone."
        ),
        legal_basis=PAY_HISTORY_LAW,
        suggestion="Zapytaj o oczekiwania finansowe zamiast o obecne lub poprzednie wynagrodzenie.",
        fix="word_map",
        word_map=_pay_history_word_map(),
    ),
    # ---------------------------------------------------------------- neutralność płciowa
    Rule(
        id="gender.title",
        category="gender_neutrality",
        severity="error",
        kind="check",
        check="title_gender",
        title="Nazwa stanowiska tylko w jednej formie rodzajowej",
        exceptions=TITLE_NEUTRAL_MARKERS,
        explanation=(
            "Tytuł ogłoszenia zawiera nazwę stanowiska tylko w formie męskiej lub tylko żeńskiej. Ogłoszenia i "
            "nazwy stanowisk powinny być neutralne płciowo — dodaj oznaczenie (K/M) albo obie formy."
        ),
        legal_basis=GENDER_LAW,
        suggestion="Dodaj „(K/M)” lub obie formy, np. „Programista / Programistka (K/M)”.",
        fix="title_marker",
    ),
    Rule(
        id="gender.body",
        category="gender_neutrality",
        severity="suggestion",
        title="Opis kandydata tylko w formie męskiej",
        patterns=(
            P(
                regex=(
                    r"\bjesteś\s+(?P<a>\w{2,}(?:y|ki|gi))(?P<s1>,\s*|\s+i\s+|\s+oraz\s+)(?P<b>\w{2,}(?:y|ki|gi))"
                    r"(?P<s2>,\s*|\s+i\s+|\s+oraz\s+)(?P<c>\w{2,}(?:y|ki|gi))\b(?!\s*[/(])"
                ),
                replace=r"jesteś \g<a>/a\g<s1>\g<b>/a\g<s2>\g<c>/a",
            ),
            P(
                regex=(
                    r"\bjesteś\s+(?P<a>\w{2,}(?:y|ki|gi))(?P<s1>,\s*|\s+i\s+|\s+oraz\s+)(?P<b>\w{2,}(?:y|ki|gi))"
                    r"\b(?!\s*[/(])"
                ),
                replace=r"jesteś \g<a>/a\g<s1>\g<b>/a",
            ),
            P(regex=r"\bjesteś\s+(?P<a>\w{2,}(?:y|ki|gi))\b(?!\s*[/(])", replace=r"jesteś \g<a>/a"),
            P(
                regex=(
                    r"\b(?:(?:idealn|wymarzon)\w*\s+)?kandydat(?:a|em|owi|ów|om|ami|ach)?\b(?!\s*[/(])"
                    r"|\bkandydaci\b(?!\s*[/(])"
                ),
            ),
        ),
        exceptions=(
            r"kandydat\w*\s*(?:/|\()\s*-?\s*\w+",
            r"kandydat\w*\s+(?:i|oraz|lub)\s+kandydat(?:k\w*|ek)\b",
            r"kandydat(?:k\w*|ek)\s+(?:i|oraz|lub)\s+kandydat\w*",
            r"\bjesteś\s+\w+\s*(?:/\s*a|\(\s*a\s*\))",
        ),
        explanation=(
            "Opis wymagań zwraca się do kandydata wyłącznie w rodzaju męskim („jesteś zmotywowany”, „idealny "
            "kandydat”). Język niewłączający może zniechęcać kobiety do aplikowania."
        ),
        legal_basis=GENDER_LAW,
        suggestion="Użyj form neutralnych („osoba kandydująca”, „jesteś osobą zmotywowaną”) lub obu form.",
        fix="word_map",
        word_map={
            "kandydat": "kandydat/ka", "kandydata": "kandydata/ki", "kandydatem": "kandydatem/ką",
            "kandydatowi": "kandydatowi/ce", "kandydaci": "kandydaci/kandydatki", "kandydatów": "kandydatów/ek",
            "kandydatom": "kandydatom/kom", "kandydatami": "kandydatami/kami", "kandydatach": "kandydatach/kach",
            "idealny": "idealny/a", "idealnego": "idealnego/ej", "idealnym": "idealnym/ą", "idealnych": "idealnych",
            "wymarzony": "wymarzony/a", "wymarzonego": "wymarzonego/ej", "wymarzonym": "wymarzonym/ą",
        },
    ),
    # ---------------------------------------------------------------- dyskryminacja
    Rule(
        id="disc.age",
        category="discrimination",
        severity="error",
        title="Ograniczenie wieku kandydatów",
        patterns=(
            P(
                regex=(
                    r"\bdo\s+\d{2}\s*\.?\s*(?:lat|roku\s+życia|r\.\s*ż\.?)(?![a-ząćęłńóśźż])"
                    r"(?!\s*(?:\w+(?:ego|ych)\s+)?(?:doświadcz|stażu|staż|pracy|praktyk|w\s+branży|w\s+zawodzie|na\s+rynku))"
                ),
            ),
            P(regex=r"\b(?:w\s+wieku|wiek\w*\s*:?)\s*(?:od\s+)?\d{2}\s*(?:-|–|—|do)\s*\d{2}(?:\s*(?:lat|r\.))?"),
            P(
                regex=(
                    r"\b(?:w\s+wieku|wiek\w*)\s*:?\s*(?:do|max\.?|maks\.?|maksymalnie|poniżej|powyżej|minimum|min\.?"
                    r"|od)\s*\d{2}(?:\s*(?:lat|r\.|roku\s+życia))?"
                ),
            ),
            P(
                regex=(
                    r"\b(?:poniżej|powyżej|nie\s+więcej\s+niż|maksymalnie|max\.?|maks\.?)\s*\d{2}\s*\.?\s*"
                    r"(?:lat|roku\s+życia)(?![a-ząćęłńóśźż])"
                    r"(?!\s*(?:\w+(?:ego|ych)\s+)?(?:doświadcz|stażu|staż|pracy|praktyk|w\s+branży|w\s+zawodzie|na\s+rynku))"
                ),
            ),
            P(
                regex=(
                    r"\b(?:1[6-9]|[2-6]\d)\s*(?:-|–|—)\s*(?:1[6-9]|[2-7]\d)\s+lat\b"
                    r"(?!\s*(?:\w+(?:ego|ych)\s+)?(?:doświadcz|stażu|staż|pracy|praktyk|w\s+branży|w\s+zawodzie|na\s+rynku"
                    r"|istnieni|działalno))"
                ),
            ),
            P(regex=r"\bnie\s+starsz\w*\s+niż(?:\s+\d{2}(?:\s*(?:lat|roku\s+życia))?)?"),
            P(regex=r"\b(?:urodz(?:on|en)\w*|rocznik\w*)\s+(?:po|przed|od|z\s+lat)\s+(?:roku\s+)?(?:19|20)\d{2}"),
        ),
        exceptions=(r"\b1[68]\s*\.?\s*(?:lat|roku)", r"ukończon\w*\s+1[68]"),
        sentence_exceptions=(EQUALITY,),
        explanation=(
            "Granica wieku w ogłoszeniu to dyskryminacja bezpośrednia ze względu na wiek. Wyjątkiem są wymogi "
            "wynikające z przepisów (np. pełnoletność)."
        ),
        legal_basis=disc_law("wiek"),
        suggestion="Usuń ograniczenie wieku; opisz wymagane kompetencje i doświadczenie.",
        fix="remove",
    ),
    Rule(
        id="disc.age_indirect",
        category="discrimination",
        severity="warning",
        title="Sugerowanie preferencji wiekowej („młody zespół”)",
        patterns=(
            P(
                regex=(
                    r"\bmłod(?:y|ego|emu|ym|a|ej|ą|e|ych|ymi|zi)\s*(?:,\s*|\s+i\s+)"
                    r"(?=[a-ząćęłńóśźż]+\s+(?:zesp|ekip|kadr|team))"
                ),
                replace="",
            ),
            P(
                regex=r"\bmłod(?P<e>y|ego|emu|ym|a|ej|ą|e|ych|ymi)\s+(?P<n>zesp[oó]\w*|ekip\w*|kadr\w*|team\w*)",
                replace=r"zgran\g<e> \g<n>",
            ),
            P(
                regex=(
                    r"\bmłod(?:y|ego|ym|a|ej|ą|e|ych|ymi|zi)\s*(?:(?:,\s*|\s+i\s+)[\w-]+\s+)?(?:duchem|ludzi\w*"
                    r"|ludźmi|osob\w*|kandydat\w*|pracowni\w*|wiekiem|mężczy\w*|kobiet\w*|absolwent\w*)"
                )
            ),
            P(regex=r"\b(?:osob\w*|kandydat\w*|pracowni\w*|ludzi\w*)\s+młod(?:y|ego|ym|a|ej|ą|e|ych|ymi|zi)\b"),
            P(regex=r"\bw\s+wieku\s+studenckim\b|\bwiek\w*\s+studenck\w*"),
        ),
        exceptions=(
            r"młodsz\w*",
            r"młodzież\w*",
            r"(?:pracy|praca|pracujemy|zajęcia|zajęć|pracować|pracuje\w*)\s+z\s+młod\w*",
        ),
        sentence_exceptions=(EQUALITY,),
        explanation=(
            "Określenia typu „młody zespół” czy „osoba młoda duchem” sugerują, że starsze osoby nie są mile "
            "widziane — to ryzyko dyskryminacji pośredniej ze względu na wiek. „Młodszy specjalista” "
            "i „Junior” to poziom stanowiska i są w porządku."
        ),
        legal_basis=disc_law("wiek"),
        suggestion="Opisz zespół bez odniesień do wieku, np. „zgrany zespół”, „zespół otwarty na nowe pomysły”.",
    ),
    Rule(
        id="disc.gender",
        category="discrimination",
        severity="error",
        title="Preferencja płci kandydata",
        patterns=(
            P(
                regex=(
                    r"\b(?:tylko|wyłącznie|jedynie|preferujemy|preferowan\w*|poszukujemy|szukamy|zatrudnimy"
                    r"|przyjmiemy|zatrudni)\s+(?:(?:tylko|wyłącznie|jedynie)\s+)?(?:kobiet\w*|mężczy\w*|mężczyźni"
                    r"|panią|pani|pana|panów|panie|panowie|pań)\b"
                ),
            ),
            # Z przymiotnikiem pomiędzy („szukamy energicznej kobiety”) — podmiana słów zepsułaby składnię.
            P(
                regex=(
                    r"\b(?:tylko|wyłącznie|jedynie|preferujemy|preferowan\w*|poszukujemy|szukamy|zatrudnimy"
                    r"|przyjmiemy|zatrudni)\s+(?:[\w-]+,?\s+){1,3}(?:kobiet\w*|mężczy\w*|mężczyźni|panią|pani"
                    r"|pana|panów|panie|panowie|pań)\b"
                ),
                fix="none",
            ),
            P(
                regex=(
                    r"\b(?:kobiet\w*|mężczy\w*|mężczyźni)\b[^.\n]{0,20}?\b(?:mile\s+widzian\w*|preferowan\w*"
                    r"|wymagan\w*|w\s+pierwszej\s+kolejności)"
                ),
                fix="remove_item",
            ),
            P(regex=r"\bpłeć\s*:\s*\w+", fix="remove_item"),
            P(regex=r"\b(?:tylko\s+|wyłącznie\s+)?dla\s+(?:kobiet|mężczyzn|pań|panów)\b", fix="remove"),
        ),
        exceptions=(
            r"kobiet\w*\s*(?:i|oraz|lub|,)\s*mężczy\w*",
            r"mężczy\w*\s*(?:i|oraz|lub|,)\s*kobiet\w*",
            r"\bpan\w*\s*(?:/|i|oraz|lub)\s*pani\w*",
            r"\bpani\w*\s*(?:/|i|oraz|lub)\s*pan\w*",
            r"\bpań\s+i\s+panów|\bpanów\s+i\s+pań",
            r"(?:program\w*|mentoring\w*|inicjatyw\w*|warsztat\w*|szkoleni\w*|społeczno\w*|wsparci\w*|grup\w*)"
            r"\s+(?:\w+\s+){0,2}dla\s+(?:kobiet|mężczyzn)",
        ),
        sentence_exceptions=(EQUALITY,),
        explanation=(
            "Wskazanie płci kandydata (wprost lub przez „szukamy pani”, „mężczyzna mile widziany”) to "
            "dyskryminacja bezpośrednia ze względu na płeć."
        ),
        legal_basis=disc_law("płeć"),
        suggestion="Pisz o „osobie” na stanowisku; jeśli praca wymaga np. dźwigania, opisz to jako wymóg.",
        fix="word_map",
        word_map={
            "kobieta": "osoba", "kobiety": "osoby", "kobietę": "osobę", "kobiecie": "osobie", "kobietą": "osobą",
            "kobiet": "osób", "kobietom": "osobom", "kobietami": "osobami",
            "mężczyzna": "osoba", "mężczyzny": "osoby", "mężczyznę": "osobę", "mężczyźnie": "osobie",
            "mężczyzną": "osobą", "mężczyźni": "osoby", "mężczyzn": "osoby", "mężczyznom": "osobom",
            "mężczyznami": "osobami", "panią": "osobę", "pani": "osoby", "pana": "osoby", "panów": "osoby",
            "panie": "osoby", "panowie": "osoby", "pań": "osób", "tylko": "", "wyłącznie": "", "jedynie": "",
        },
    ),
    Rule(
        id="disc.family",
        category="discrimination",
        severity="error",
        title="Wymagania dotyczące sytuacji rodzinnej",
        patterns=(
            P(regex=r"\bbez\s+zobowiązań(?:\s+rodzinnych)?\b"),
            P(
                regex=(
                    r"\b(?:nie\s+)?plan\w*\s+(?:ciąży|macierzyństw\w*|powiększeni\w*\s+rodziny|posiadani\w*\s+"
                    r"dzieci|rodzinn\w*|założeni\w*\s+rodziny)"
                ),
                fix="remove_sentence",
            ),
            P(regex=r"\b(?:kobiet\w*\s+)?w\s+ciąży\b|\bciężarn\w*", fix="remove_sentence"),
            P(regex=r"\b(?:sytuacj\w*|stan\w*)\s+rodzinn\w*"),
            P(regex=r"\b(?:posiadani\w*|liczb\w*|wiek\w*)\s+dzieci\b"),
            P(regex=r"\b(?:bez|nieposiadając\w*|niemając\w*|nie\s+mając\w*)\s+(?:małych\s+)?dzieci\b"),
            P(regex=r"\b(?:panna|kawaler|osoba\s+samotna|singiel\w*|singielk\w*)\b"),
            P(
                regex=(
                    r"\bnie\s+(?:zatrudniamy|przyjmujemy|rozpatrujemy)\s+(?:matek|rodziców|kobiet\s+z\s+dziećmi"
                    r"|osób\s+z\s+(?:małymi\s+)?dziećmi)"
                ),
                fix="remove_sentence",
            ),
        ),
        exceptions=(r"bez\s+zobowiązań\s+(?:finansow|umown|kosztów|opłat)\w*",),
        sentence_exceptions=(EQUALITY,),
        explanation=(
            "Wymagania dotyczące rodziny, dzieci, ciąży czy stanu cywilnego wykluczają rodziców i kobiety — to "
            "dyskryminacja (m.in. ze względu na płeć). Jeśli praca jest wzbroniona kobietom w ciąży z mocy "
            "przepisów, opisz warunki pracy zamiast pytać o ciążę."
        ),
        legal_basis=disc_law("płeć") + "; Kodeks pracy art. 22¹ § 1 (zakres danych od kandydata)",
        suggestion="Usuń wymaganie; jeśli ważna jest dyspozycyjność, opisz konkretny grafik pracy.",
        fix="remove_item",
    ),
    Rule(
        id="disc.appearance",
        category="discrimination",
        severity="error",
        title="Wymagania dotyczące wyglądu",
        patterns=(
            P(
                regex=(
                    r"\b(?:mił|dobr|atrakcyjn|nienagann|reprezentacyjn|zadban|ładn|przyjemn|estetyczn|wysok)\w*"
                    r"\s+(?:aparycj\w*|prezencj\w*|powierzchowno\w*|wygląd\w*|sylwetk\w*|urod\w*)"
                ),
            ),
            P(regex=r"\baparycj\w*"),
            P(regex=r"\bprezencj\w*"),
            P(regex=r"\bwzrost\s*(?::|min\.?|minimum|powyżej|od)?\s*\d{3}\s*cm"),
            P(regex=r"\b(?:szczupł|wysportowan|zgrabn)\w*\s+(?:sylwetk\w*|budow\w*)"),
            P(regex=r"\b(?:bez|brak)\s+(?:widocznych\s+)?(?:tatuaż\w*|kolczyk\w*|piercing\w*)"),
        ),
        exceptions=(
            r"wygląd\w*\s+(?:aplikacj|stron|interfejs|produkt|stoisk|witryn|sklep|ekspozycj|towar|półek|lokal|biur"
            r"|ui|graficzn|serwis|raport|dokument|prezentacj|opakowa)\w*",
            r"prezencj\w*\s+(?:produkt|towar|ekspozycj|półek|stoisk|marki|oferty)\w*",
        ),
        sentence_exceptions=(EQUALITY,),
        explanation=(
            "Wymagania typu „miła aparycja”, „dobra prezencja” czy wzrost są subiektywne i mogą prowadzić do "
            "dyskryminacji (np. ze względu na płeć, wiek, niepełnosprawność). Wymóg stroju lub BHP opisz neutralnie."
        ),
        legal_basis=disc_law("płeć, wiek, niepełnosprawność"),
        suggestion="Usuń wymaganie wyglądu; jeśli obowiązuje strój służbowy, napisz o dress code.",
        fix="remove_item",
    ),
    Rule(
        id="disc.nationality",
        category="discrimination",
        severity="error",
        title="Wymóg narodowości lub „native speaker”",
        patterns=(
            P(
                regex=r"\bnative[\s-]*speaker\w*\s+(?P<lang>języka\s+\w+)",
                replace=r"biegła znajomość \g<lang> (poziom C2)",
            ),
            P(regex=r"\bnative[\s-]*speaker\w*", replace="biegła znajomość języka (poziom C2)"),
            P(
                regex=r"\b(?P<v>jest|jesteś)\s+native[\s-]*speaker(?:em|ką)\s+języka\s+(?P<stem>\w+?)iego\b",
                replace=r"\g<v> osobą biegle posługującą się językiem \g<stem>im (poziom C2)",
            ),
            P(regex=r"\bnative\s+level\b", replace="poziom C2"),
            P(
                regex=r"\b(?:rodzim|natywn)\w*\s+(?:użytkowni|mówc)\w*\s+(?P<lang>języka\s+\w+)",
                replace=r"biegła znajomość \g<lang> (poziom C2)",
            ),
            P(regex=r"\b(?:rodzim|natywn)\w*\s+(?:użytkowni|mówc)\w*", replace="biegła znajomość języka (poziom C2)"),
            P(regex=r"\bjęzyk\w*\s+ojczyst\w*"),
            P(
                regex=(
                    r"\b(?:tylko|wyłącznie|jedynie)\s+(?:dla\s+)?(?:polak\w*|polac\w*|polk\w*|obywatel\w*\s+polsk\w*"
                    r"|osób\s+narodowości\s+\w+)"
                ),
                fix="remove_sentence",
            ),
            P(
                regex=r"(?P<v>\b(?:poszukuj\w*|szukamy)\s+)(?:pola(?:k|ka|ków|cy)|polk(?:a|i|ę))\b",
                replace=r"\g<v>osoby",
            ),
            P(
                regex=r"(?P<v>\b(?:zatrudni\w*|przyjm\w*)\s+)(?:pola(?:k|ka|ków|cy)|polk(?:a|i|ę))\b",
                replace=r"\g<v>osobę",
            ),
            P(regex=r"\bnarodowo\w*\s*:?\s+\w+sk\w*", fix="remove_item"),
            P(regex=r"\bpolsk\w*\s+(?:pochodzeni\w*|narodowo\w*|korzeni\w*)", fix="remove_item"),
            P(regex=r"\bobywatelstw\w*\s+(?:polsk|ue|unii|europejsk)\w*", fix="remove_item"),
            P(
                regex=(
                    r"\bbez\s+(?:obcokrajowc\w*|cudzoziemc\w*|imigrant\w*)|\bnie\s+(?:zatrudniamy|przyjmujemy"
                    r"|rozpatrujemy)\s+(?:\w+\s+)?(?:obcokrajowc\w*|cudzoziemc\w*|imigrant\w*|osób\s+z\s+zagranicy)"
                ),
                fix="remove_sentence",
            ),
            P(regex=r"\bbez\s+(?:obcego\s+)?akcentu\b", fix="remove"),
        ),
        exceptions=(
            r"react[\s-]+native",
            r"native\s+(?:app|aplikacj|mobile|code|kod|android|ios|module|moduł)\w*",
            r"\bnarodow\w*\s+(?:bank|fundusz|instytut|muzeum|teatr|centrum)\w*",
        ),
        sentence_exceptions=(
            EQUALITY,
            r"prawo\s+do\s+pracy|zezwoleni\w*\s+na\s+prac\w*|pozwoleni\w*\s+na\s+prac\w*|służb\w*\s+cywiln\w*",
        ),
        explanation=(
            "Wymóg „native speaker”, „języka ojczystego” czy narodowości wyklucza osoby, które znają język "
            "biegle, ale nie są rodzimymi użytkownikami — to dyskryminacja ze względu na narodowość lub "
            "pochodzenie etniczne. Wymóg obywatelstwa jest dopuszczalny tylko, gdy wynika z ustawy."
        ),
        legal_basis=disc_law("narodowość, pochodzenie etniczne"),
        suggestion="Określ wymagany poziom języka, np. „biegła znajomość języka angielskiego (C1/C2)”.",
        fix="remove_item",
    ),
    Rule(
        id="disc.health",
        category="discrimination",
        severity="error",
        title="Wymagania dotyczące zdrowia lub niepełnosprawności",
        patterns=(
            P(regex=r"\b(?:dobr|doskonał|bardzo\s+dobr|nienagann|pełn)\w*\s+(?:stan\w*\s+zdrowia|zdrowi\w*)"),
            P(regex=r"\bpełn\w*\s+sprawno\w*(?:\s+(?:fizyczn|psychofizyczn|ruchow)\w*)?"),
            P(regex=r"\b(?:osob\w*|kandydat\w*)\s+(?:zdrow\w*|niepaląc\w*|w\s+pełni\s+sprawn\w*)"),
            P(regex=r"\bniepaląc\w*"),
            P(regex=r"\b(?:bez|brak)\s+(?:orzeczeni\w*\s+o\s+)?niepełnosprawno\w*"),
            P(
                regex=(
                    r"\bnie\s+(?:zatrudniamy|przyjmujemy|rozpatrujemy)\s+(?:\w+\s+)?(?:osób\s+)?(?:niepełnosprawn\w*"
                    r"|z\s+(?:orzeczeniem|niepełnosprawno\w*)|chor\w*)"
                ),
                fix="remove_sentence",
            ),
            P(regex=r"\bbez\s+(?:chorób\s+przewlekłych|nałogów|problemów\s+zdrowotnych)"),
            P(regex=r"\bzdrow\w*\s+(?:psychicznie|fizycznie)"),
        ),
        exceptions=(
            r"przeciwwskaza\w*",
            r"orzeczeni\w*\s+lekarsk\w*",
            r"badani\w*\s+(?:lekarsk|okresow|medycyn|wstępn)\w*",
            r"sprawno\w*\s+(?:system|urządze|maszyn|sprzęt|infrastruktur|instalac|pojazd|floty|sieci|serwer|technicz"
            r"|operacyjn|działa|organizacyjn)\w*",
        ),
        sentence_exceptions=(
            EQUALITY,
            r"zapraszamy\s+(?:również\s+|także\s+)?osoby\s+z\s+(?:orzeczeniem|niepełnosprawno)",
        ),
        explanation=(
            "Wymaganie „dobrego stanu zdrowia” czy „pełnej sprawności” wyklucza osoby z niepełnosprawnościami "
            "i chorobami przewlekłymi. Zdolność do pracy potwierdza lekarz medycyny pracy; w ogłoszeniu opisz "
            "konkretne wymagania stanowiska (np. „praca wymaga podnoszenia do 20 kg”)."
        ),
        legal_basis=disc_law("niepełnosprawność"),
        suggestion="Opisz konkretne warunki pracy zamiast wymagać zdrowia; dodaj informację o dostępności.",
        fix="remove_item",
    ),
    Rule(
        id="disc.religion",
        category="discrimination",
        severity="error",
        title="Wymagania dotyczące religii lub wyznania",
        patterns=(
            P(regex=r"\b(?:osob\w*|kandydat\w*|pracowni\w*)\s+(?:\w+\s+)?(?:wierząc\w*|praktykując\w*|katolick\w*|religijn\w*)"),
            P(regex=r"\b(?:katolik\w*|katoliczk\w*|wierząc\w*\s+i\s+praktykując\w*|praktykując\w*\s+(?:katolik|chrześcijan)\w*)"),
            P(regex=r"\b(?:wyznani\w*|religi\w*|światopogląd\w*)\s*:\s*\w+"),
            P(regex=r"\b(?:świadectw\w*\s+chrztu|zaświadczeni\w*\s+od\s+proboszcza)"),
            P(regex=r"\b(?:wymagan\w*|mile\s+widzian\w*|preferowan\w*)\s+(?:\w+\s+)?(?:wyznani\w*|religi\w*|wiar\w*)"),
        ),
        exceptions=(
            r"wierząc\w*\s+w\s+(?!boga)\w+",
            r"praktykując\w*\s+(?:lekarz|adwokat|radc|prawnik|psycholog|fizjoterapeut|farmaceut|architekt)\w*",
        ),
        sentence_exceptions=(EQUALITY,),
        explanation=(
            "Wymaganie określonej religii lub praktyk religijnych to dyskryminacja ze względu na religię lub "
            "wyznanie. Wyjątek dotyczy organizacji, których etyka opiera się na religii, gdy jest to istotny "
            "wymóg zawodowy (art. 18³ᵇ KP — zweryfikuj)."
        ),
        legal_basis=disc_law("religię, wyznanie"),
        suggestion="Usuń wymaganie dotyczące religii; opisz rzeczywiste obowiązki.",
        fix="remove_item",
    ),
    Rule(
        id="disc.other",
        category="discrimination",
        severity="error",
        title="Wymagania dotyczące poglądów, związków zawodowych, orientacji lub pochodzenia",
        patterns=(
            P(regex=r"\bprzynależno\w*\s+(?:związkow\w*|do\s+związk\w*|partyjn\w*|polityczn\w*)"),
            P(regex=r"\b(?:niezrzeszon\w*|bez\s+przynależności)\b"),
            P(regex=r"\b(?:przekonani\w*|poglądy|poglądów)\s+(?:polityczn|prawicow|lewicow|konserwatywn|liberaln)\w*"),
            P(regex=r"\borientacj\w*\s+seksualn\w*|\bheteroseksualn\w*|\bhomoseksualn\w*"),
            P(regex=r"\bpochodzeni\w*\s+etniczn\w*|\bkolor\w*\s+skóry|\bras\w*\s*:\s*\w+|\bbiał\w*\s+ras\w*"),
        ),
        exceptions=(r"orientacj\w*\s+na\s+(?:klienta|cel|wynik)\w*",),
        sentence_exceptions=(EQUALITY,),
        explanation=(
            "Wymagania dotyczące przekonań politycznych, przynależności związkowej, orientacji seksualnej, rasy "
            "czy pochodzenia etnicznego są zakazane w rekrutacji."
        ),
        legal_basis=disc_law(
            "przekonania polityczne, przynależność związkową, orientację seksualną, rasę, pochodzenie etniczne"
        ),
        suggestion="Usuń wymaganie — te cechy nie mogą być kryterium wyboru.",
        fix="remove_item",
    ),
    Rule(
        id="disc.photo",
        category="discrimination",
        severity="error",
        title="Żądanie zdjęcia w CV",
        patterns=(
            P(
                regex=(
                    r"(?P<d>\b(?:cv|życiorys\w*|aplikacj\w*|zgłoszeni\w*|dokument\w*))\s+(?:wraz\s+)?(?:ze|z)\s+"
                    r"(?:aktualn\w*\s+|wyraźn\w*\s+)?(?:zdjęci\w*|fotografi\w*)"
                ),
                replace=r"\g<d>",
            ),
            P(
                regex=(
                    r"\b(?:załącz\w*|prześl\w*|przesłani\w*|dołącz\w*|dodaj\w*|dodani\w*|wymagan\w*|obowiązkow\w*)"
                    r"\s+(?:\w+\s+){0,2}(?:zdjęci\w*|fotografi\w*|fotk\w*)"
                ),
            ),
            P(
                regex=(
                    r"\b(?:zdjęci\w*|fotografi\w*)\s+(?:do\s+cv|w\s+cv|portretow\w*|legitymacyjn\w*|całej\s+sylwetki"
                    r"|twarzy)"
                ),
            ),
            P(regex=r"\bz\s+(?:aktualnym\s+)?zdjęciem\b", fix="remove"),
        ),
        exceptions=(
            r"zdję\w*\s+(?:produkt|towar|reklam|ślubn|wnętrz|nieruchomo|katalog|do\s+sklep|na\s+stron|w\s+system)\w*",
            r"obr[óo]bk\w*\s+(?:\w+\s+)?zdjęć",
            r"fotografi\w*\s+(?:produkt|reklam|ślubn|kulinarn|mod)\w*",
        ),
        sentence_exceptions=(NEGATED_REQUEST, r"\bbez\s+zdjęci\w*"),
        explanation=(
            "Zdjęcie nie należy do danych, których pracodawca może żądać od kandydata. Ujawnia płeć, wiek, "
            "pochodzenie czy niepełnosprawność i sprzyja dyskryminacji. Można je przetwarzać tylko za dobrowolną "
            "zgodą kandydata (art. 22¹ᵃ KP)."
        ),
        legal_basis=disc_law("płeć, wiek, rasę, pochodzenie etniczne") + "; " + DATA_LAW,
        suggestion="Usuń prośbę o zdjęcie; możesz dopisać „Prosimy nie dołączać zdjęcia do CV”.",
        fix="remove_item",
    ),
    # ---------------------------------------------------------------- dane nadmiarowe i RODO
    Rule(
        id="data.pesel",
        category="excessive_data",
        severity="error",
        title="Żądanie numeru PESEL",
        patterns=(P(regex=r"\b(?:(?:numer\w*|nr\.?)\s+)?pesel\w*\b"),),
        exceptions=(r"\bbez\s+(?:numeru\s+)?pesel",),
        sentence_exceptions=(NEGATED_REQUEST, AFTER_HIRING),
        explanation=(
            "PESEL nie należy do danych, których można żądać od kandydata (pracodawca pozyskuje go dopiero od "
            "pracownika po zatrudnieniu). Data urodzenia jest dopuszczalna."
        ),
        legal_basis=DATA_LAW,
        suggestion="Usuń prośbę o PESEL.",
        fix="remove_item",
    ),
    Rule(
        id="data.id_document",
        category="excessive_data",
        severity="error",
        title="Żądanie danych lub kopii dowodu tożsamości",
        patterns=(
            P(
                regex=(
                    r"\b(?:numer\w*|nr\.?|seri\w*(?:\s+i\s+numer\w*)?|skan\w*|kopi\w*|zdjęci\w*|ksero\w*)\s+"
                    r"(?:dowodu(?:\s+osobistego)?|paszportu|dokumentu\s+tożsamości)"
                )
            ),
        ),
        exceptions=(r"dowod\w*\s+(?:dostaw|zakup|wpłat|sprzedaż|księgow|wydani|przyjęci|rejestracyjn)\w*",),
        sentence_exceptions=(NEGATED_REQUEST, AFTER_HIRING),
        explanation=(
            "Numer, seria czy skan dowodu osobistego lub paszportu nie są potrzebne na etapie rekrutacji — to "
            "dane nadmiarowe, a kopia dokumentu zwiększa ryzyko kradzieży tożsamości."
        ),
        legal_basis=DATA_LAW,
        suggestion="Usuń prośbę o dane z dokumentu tożsamości.",
        fix="remove_item",
    ),
    Rule(
        id="data.marital",
        category="excessive_data",
        severity="error",
        title="Pytanie o stan cywilny",
        patterns=(P(regex=r"\bstan\w*\s+cywiln\w*"), P(regex=r"\b(?:żonat\w*|zamężn\w*|rozwiedzion\w*)\b")),
        exceptions=(r"urz[ąę]d\w*\s+stanu\s+cywilnego|akt\w*\s+stanu\s+cywilnego",),
        sentence_exceptions=(NEGATED_REQUEST, EQUALITY),
        explanation=(
            "Stan cywilny nie należy do danych, których można żądać od kandydata, i może prowadzić do "
            "dyskryminacji ze względu na płeć lub sytuację rodzinną."
        ),
        legal_basis=DATA_LAW,
        suggestion="Usuń pytanie o stan cywilny.",
        fix="remove_item",
    ),
    Rule(
        id="data.other",
        category="excessive_data",
        severity="warning",
        title="Inne dane nadmiarowe",
        patterns=(
            P(regex=r"\bimion\w*\s+(?:rodziców|ojca|matki)"),
            P(regex=r"\bnazwisk\w*\s+(?:rodow\w*|panieńsk\w*)"),
            P(regex=r"\b(?:numer\w*|nr\.?)\s+(?:konta|rachunku)(?:\s+bankow\w*)?"),
            P(regex=r"\badres\w*\s+zameldowani\w*"),
            P(regex=r"\b(?:zaświadczeni\w*|informacj\w*)\s+o\s+niekaralno\w*"),
            P(regex=r"\b(?:link\w*\s+do\s+)?profil\w*\s+(?:na\s+)?(?:facebook\w*|instagram\w*|tiktok\w*)"),
            P(regex=r"\bgrup\w*\s+krwi\b"),
        ),
        sentence_exceptions=(NEGATED_REQUEST, AFTER_HIRING, REQUIRED_BY_LAW),
        explanation=(
            "Te dane wykraczają poza zakres danych od kandydata. Zaświadczenie o niekaralności można żądać tylko, "
            "gdy wymaga tego przepis szczególny — wtedy wskaż go w ogłoszeniu."
        ),
        legal_basis=DATA_LAW,
        suggestion="Usuń prośbę o te dane albo wskaż przepis, który jej wymaga.",
        fix="remove_item",
    ),
    Rule(
        id="data.rodo_clause",
        category="excessive_data",
        severity="suggestion",
        kind="missing",
        title="Brak klauzuli informacyjnej RODO",
        patterns=(
            P(
                regex=(
                    r"\brodo\b|\bgdpr\b|2016/679|klauzul\w*\s+informacyjn\w*|administrator\w*\s+(?:\w+\s+){0,2}danych"
                    r"|przetwarza\w*\s+(?:\w+\s+){0,2}danych\s+osobowych|ochron\w*\s+danych\s+osobowych"
                    r"|polityk\w*\s+prywatności"
                )
            ),
        ),
        doc_exceptions=(r"(?:https?://|www\.)\S*(?:rodo|privacy|prywatn|klauzul|gdpr)\S*",),
        explanation=(
            "Ogłoszenie nie zawiera klauzuli informacyjnej ani odesłania do niej. Kandydat powinien wiedzieć, kto "
            "i w jakim celu przetwarza jego dane."
        ),
        legal_basis="RODO (rozporządzenie (UE) 2016/679), art. 13 (obowiązek informacyjny)",
        suggestion="Dodaj krótką klauzulę informacyjną lub link do pełnej klauzuli dla kandydatów.",
        template=(
            "Klauzula informacyjna (RODO): [UZUPEŁNIJ: administrator danych, cel i podstawa przetwarzania, okres "
            "przechowywania, prawa osoby kandydującej lub link do pełnej klauzuli]"
        ),
        template_position="end",
    ),
    Rule(
        id="data.outdated_consent",
        category="excessive_data",
        severity="suggestion",
        title="Nieaktualna podstawa prawna zgody na przetwarzanie danych",
        patterns=(
            P(regex=r"\bustaw\w*\s+z\s+dnia\s+29\s*(?:\.\s*0?8\s*\.\s*|\s+sierpnia\s+)1997"),
            P(regex=r"\bustaw\w*\s+o\s+ochronie\s+danych\s+osobowych\s+z\s+(?:dnia\s+)?(?:29\s+sierpnia\s+)?1997"),
            P(regex=r"Dz\.\s*U\.\s*(?:z\s+)?2002\s*r?\.?\s*,?\s*Nr\s*101\s*,?\s*poz\.\s*926"),
        ),
        sentence_exceptions=(r"nie\s+obowiązuj\w*|uchylon\w*|zastąpion\w*",),
        explanation=(
            "Ustawa o ochronie danych osobowych z 1997 r. nie obowiązuje od 25 maja 2018 r. Odwołanie do niej "
            "wygląda nieprofesjonalnie i sugeruje, że proces nie był aktualizowany pod RODO."
        ),
        legal_basis=(
            "RODO (rozporządzenie (UE) 2016/679); ustawa z 10 maja 2018 r. o ochronie danych osobowych"
        ),
        suggestion="Zastąp odwołanie aktualną klauzulą zgodną z RODO.",
    ),
    # ---------------------------------------------------------------- przejrzystość
    Rule(
        id="clarity.location",
        category="clarity",
        severity="warning",
        kind="missing",
        title="Brak lokalizacji",
        patterns=(
            P(
                regex=(
                    r"\blokalizacj\w*|\bmiejsc\w*\s+(?:pracy|wykonywania)|\bpraca\s+(?:zdaln|hybrydow|stacjonarn)\w*"
                    r"|\bzdaln\w*|\bhybryd\w*|\bremote\b|\bbiur\w*\s+(?:w|przy|na)\s+|\bsiedzib\w*|\bul\.\s*\w+"
                    r"|\b(?:" + CITIES + r")"
                    # „magazyn w Strykowie”, „praca w Opolu” — nazwa własna z wielkiej litery po miejscu pracy
                    r"|\b(?:siedzib|biur|oddzia|zakład|magazyn|sklep|placówk|fili|hal[ai]|fabryk|restauracj|hotel"
                    r"|prac)\w*\s+(?:w|we|na)\s+(?-i:[A-ZŁŚŻŹĆ][a-ząćęłńóśźż]{2,})"
                )
            ),
        ),
        doc_exceptions=(r"\bz\s+dowolnego\s+miejsca|\bdowoln\w*\s+lokalizacj\w*|\bw\s+całej\s+polsce",),
        explanation="Ogłoszenie nie mówi, gdzie wykonywana jest praca. Kandydat nie oceni dojazdu ani relokacji.",
        legal_basis=GOOD_PRACTICE,
        suggestion="Podaj miasto lub adres biura albo napisz, że praca jest zdalna.",
        template="Lokalizacja: [UZUPEŁNIJ: miasto / adres biura lub „praca zdalna”]",
    ),
    Rule(
        id="clarity.work_mode",
        category="clarity",
        severity="suggestion",
        kind="missing",
        title="Brak trybu pracy",
        patterns=(
            P(
                regex=(
                    r"\bzdaln\w*|\bhybryd\w*|\bstacjonarn\w*|\bremote\b|\bon-?site\b|\bw\s+biurze|\bz\s+biura"
                    r"|\bhome\s+office|\bz\s+domu|\bna\s+miejscu|\bw\s+terenie|\bterenow\w*|\bw\s+magazynie"
                    r"|\bna\s+produkcji|\bw\s+sklepie|\bw\s+restauracji|\bw\s+hotelu|\bna\s+recepcji|\bw\s+placówce"
                    r"|\bw\s+(?:naszym\s+)?(?:zakładzie|oddziale|szpitalu|przychodni)|\bw\s+kuchni"
                )
            ),
        ),
        # Przy pracy fizycznej na miejscu (magazyn, sklep, kierowca) tryb pracy wynika z charakteru stanowiska.
        doc_exceptions=(
            r"\b(?:kierowc|kurier|magazyn|produkcj|sklep|kasjer|kelner|kucharz|kuchar|recepcj|pielęgnia|opiekun"
            r"|sprzątan|sprzątacz|monter|budow|spawacz|fryzjer|barist|ratownik|elektryk|mechanik)\w*",
        ),
        explanation="Brakuje informacji, czy praca jest stacjonarna, hybrydowa czy zdalna.",
        legal_basis=GOOD_PRACTICE,
        suggestion="Dopisz tryb pracy, np. „praca hybrydowa: 2 dni w biurze”.",
        template="Tryb pracy: [UZUPEŁNIJ: stacjonarny / hybrydowy / zdalny]",
    ),
    Rule(
        id="clarity.contract",
        category="clarity",
        severity="warning",
        kind="missing",
        title="Brak rodzaju umowy",
        patterns=(
            P(
                regex=(
                    r"\bumow\w*\s+(?:o\s+prac\w*|o\s+dzieło|zleceni\w*|cywilnoprawn\w*|na\s+okres|b2b|na\s+czas"
                    r"|stał\w*)|\bb2b\b|\buop\b|\bkontrakt\w*|\bform\w*\s+(?:zatrudnieni|współprac)\w*"
                    r"|\brodzaj\w*\s+umowy|\bpełn\w*\s+etat\w*|\bpół\s+etatu|\b\d/\d\s+etatu|\bzatrudnieni\w*\s+na\s+"
                    r"podstawie|\bjdg\b|\bstaż\w*\s+(?:płatn|z\s+urzędu)\w*"
                )
            ),
        ),
        doc_exceptions=(r"\bwolontariat\w*|\bwolontariusz\w*|\bpraktyk\w*\s+(?:studenck|zawodow|absolwenck)\w*",),
        explanation=(
            "Ogłoszenie nie podaje rodzaju umowy ani wymiaru etatu, a to kluczowa informacja przy ocenie oferty."
        ),
        legal_basis=GOOD_PRACTICE,
        suggestion="Dopisz rodzaj umowy i wymiar etatu, np. „umowa o pracę, pełny etat”.",
        template="Forma zatrudnienia: [UZUPEŁNIJ: rodzaj umowy (np. umowa o pracę, B2B) i wymiar etatu]",
    ),
    Rule(
        id="clarity.duties",
        category="clarity",
        severity="warning",
        kind="missing",
        title="Brak zakresu obowiązków",
        patterns=(
            P(
                regex=(
                    r"\bobowiązk(?:i|ów|ach|ami)?\b|\bzadani(?:a|e|ach|ami)\b|\bzadań\b|\bzakres\w*\s+(?:prac|obowiązk"
                    r"|odpowiedzialno)\w*|\bczym\s+(?:będziesz|się\s+będziesz)|\bco\s+będziesz\s+robi\w*"
                    r"|\btwoja\s+rola|\bbędziesz\s+(?:odpowiedzialn\w*|zajmowa\w*|odpowiada\w*)"
                    r"|\bodpowiedzialno\w*\s+za|\bresponsibilities\b"
                )
            ),
        ),
        doc_exceptions=(r"pełn\w*\s+(?:treść|opis|ogłoszeni|ofert)\w*[^\n]{0,40}(?:https?://|www\.)",),
        explanation="Brakuje opisu, czym osoba na stanowisku będzie się zajmować.",
        legal_basis=GOOD_PRACTICE,
        suggestion="Dodaj 3–6 punktów z najważniejszymi zadaniami.",
        template="Zakres obowiązków: [UZUPEŁNIJ: 3–6 najważniejszych zadań na stanowisku]",
    ),
    Rule(
        id="clarity.requirements_count",
        category="clarity",
        severity="suggestion",
        kind="check",
        check="requirements_count",
        params={"max_items": 8},
        title="Zbyt długa lista wymagań obowiązkowych",
        # Pozycje „mile widziane” nie są wymaganiami obowiązkowymi.
        exceptions=(r"mile\s+widzian|atut|plusem|dodatkow\w*\s+zalet|nice\s+to\s+have",),
        explanation=(
            "Długa lista wymagań obowiązkowych zniechęca osoby, które spełniają większość z nich — badania "
            "pokazują, że częściej rezygnują z aplikowania kobiety."
        ),
        legal_basis=GOOD_PRACTICE,
        suggestion="Zostaw maksymalnie 8 wymagań koniecznych, resztę przenieś do „mile widziane”.",
    ),
    Rule(
        id="clarity.jargon",
        category="clarity",
        severity="suggestion",
        title="Korpo-żargon i wyolbrzymienia",
        patterns=(
            P(
                regex=(
                    r"\b(?:ninj\w*|rockstar\w*|rock\s+star\w*|guru|wymiatacz\w*|superbohater\w*|czarodziej\w*"
                    r"|magik\w*|jedi|unicorn\w*|jednoroż\w*)\b"
                )
            ),
            P(regex=r"\brodzinn(?P<e>\w*)\s+atmosfer(?P<f>\w*)", replace=r"przyjazn\g<e> atmosfer\g<f>"),
            P(
                regex=r"\bjesteśmy\s+(?:jak\s+)?(?:jedn\w*\s+)?(?:wielk\w*\s+)?rodzin\w*",
                replace="jesteśmy zgranym zespołem",
            ),
            P(regex=r"\bwork\s+hard,?\s+play\s+hard\b"),
        ),
        exceptions=(
            r"magik\w*\s+(?:na\s+imprez|estradow|dla\s+dzieci)\w*",
            r"rodzinn\w*\s+atmosfer\w*\s+(?:hotelu|pensjonatu|restauracji|naszego\s+lokalu)",
        ),
        explanation=(
            "Określenia typu „ninja”, „rockstar” czy „rodzinna atmosfera” są nieprecyzyjne, mogą zniechęcać część "
            "osób i bywają odbierane jako sygnał nadmiernych oczekiwań lub zacierania granic."
        ),
        legal_basis=GOOD_PRACTICE,
        suggestion="Zastąp żargon konkretnym opisem kompetencji lub kultury pracy.",
    ),
    Rule(
        id="clarity.long_sentence",
        category="clarity",
        severity="suggestion",
        kind="check",
        check="long_sentences",
        params={"max_words": 35},
        title="Bardzo długie zdanie",
        sentence_exceptions=(r"rodo|danych\s+osobowych|2016/679|administrator\w*|klauzul\w*|przetwarza\w*",),
        explanation="Zdanie jest bardzo długie i trudne w odbiorze — utrudnia to czytanie, także osobom z dysleksją.",
        legal_basis=GOOD_PRACTICE,
        suggestion="Podziel zdanie na krótsze (do ok. 20–25 słów) lub zamień na listę.",
    ),
)

RULES_BY_ID: dict[str, Rule] = {rule.id: rule for rule in RULES}
