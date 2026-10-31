"""Klasyfikacja typu pytania (reguły) i wykrywanie wskazówek STAR.

Typ główny wybierany jest według pierwszeństwa: wielokrotne > sugerujące > zamknięte >
dopytujące > behawioralne > otwarte — bo to wady formy pytania są najważniejsze dla coacha.
"""
from __future__ import annotations

import re

from pydantic import BaseModel

from .models import OPEN_TYPES, QUESTION_TYPE_LABELS, QuestionType
from .text import normalize

_OPEN_WORDS = {
    "jak", "jaki", "jaka", "jakie", "jakiego", "jakiej", "jakim", "jakich", "jakimi", "jakiemu",
    "co", "czego", "czemu", "czym", "dlaczego", "ile", "kiedy", "gdzie", "kto", "kogo", "komu", "kim",
    "ktory", "ktora", "ktore", "ktorego", "ktorej", "ktorych", "ktorym", "ktorymi", "skad", "dokad",
}
_LEAD_IN = {"a", "i", "no", "to", "dobrze", "rozumiem", "ok", "okej", "czyli", "wiec", "zatem", "swietnie", "dziekuje", "aha", "jasne"}

_IMPERATIVE = re.compile(
    r"\b(?:opowiedz|opisz|przybliz|wyjasnij|podaj|powiedz|scharakteryzuj|rozwin|wymien|przedstaw|podziel)\w*"
    r"|\bprosze (?:o\b|opowiedziec|opisac|przyblizyc|wyjasnic|podac|powiedziec|rozwinac|wymienic|przedstawic|podzielic)"
)
_POLITE_OPEN = re.compile(
    r"\bczy (?:moglby|moglaby|mozesz|moze|moglbys|moglabys|zechcialby|zechcialaby|zechce|bylby|bylaby|da sie)\b"
    r"(?: \w+){0,3} (?:opowiedziec|opisac|przyblizyc|podac|powiedziec|podzielic|wyjasnic|rozwinac|przedstawic)"
)
_LEADING = re.compile(
    r"\bnieprawdaz\b|\bnie (?:uwaza|sadzi|mysli|zgodzi)\w*(?: \w+){0,2} ze\b|\bzgodzi sie\b|\bchyba\b|\bna pewno\b"
    r"|\bzapewne\b|\bpewnie\b|\bzakladam\w*|\bprzeciez\b|\boczywisc\w*|\bjak bardzo\b|\brozumiem\w* ze\b"
    r"|\bdomyslam sie\w* ze\b|\bczy nie (?:jest|bylo|byloby|uwaza|sadzi|mysli|warto|lepiej|lepiej by)\b|\bprawda\b"
)
_TAG_QUESTION = re.compile(r",\s*(?:tak|nie|prawda|zgadza się|racja|tak czy nie)\s*\?\s*$", re.IGNORECASE)
_COORDINATED = re.compile(
    r"\b(?:i|a|oraz|albo|lub)(?: tez| takze| jeszcze)? (?:jak|jaki\w*|co|czego|czym|dlaczego|czemu|ile|kiedy|gdzie|kto|kogo|z kim|w jaki|czy)\b"
)
_BEHAVIORAL = re.compile(
    r"\b(?:sytuacj|przyklad|przypadk|przypadek|kiedys|ostatnio|ostatni raz|w przeszlosci|w poprzedniej|we wczesniejsz|w ostatniej|w obecnej pracy)\w*"
    r"|\b(?:zrobil|zareagowal|poradzil|rozwiazal|postapil|zachowal|przekonal|zmotywowal|wdrozyl|naprawil|uspokoil|zorganizowal|poprowadzil|nauczyl|udalo|musial|zdecydowal|podjal|wspolpracowal|radzil|prowadzil|wprowadzil|odpowiadal)\w*"
)
_HYPOTHETICAL = re.compile(r"\bgdyby\b|\bby (?:pan|pani)\b|\bbys\b|\bjesliby\b|\bwyobraz\w*")
_FOLLOWUP = re.compile(
    r"\b(?:dokladnie|konkretnie|wtedy|potem|nastepnie|wspomnial\w*|wrocmy|rozwin\w*|tamt\w*)\b"
    r"|\b(?:powiedzial|mowil)\w* (?:pan|pani)\b|\bwiecej o\b|\bjaki (?:byl )?(?:efekt|rezultat|wynik|skutek)\b"
    r"|\bjak (?:to )?sie (?:skonczyl|zakonczyl)\w*|\bczym sie (?:to )?(?:skonczyl|zakonczyl)\w*|\bjaka byla (?:pana|pani|twoja) rola\b"
    r"|\bco (?:pan|pani) osobiscie\b|\bz jakim skutkiem\b|\bco z tego wyniklo\b|\bczego sie\b(?: \w+){0,3} nauczyl\w*"
    r"|\b(?:ta|tej|tamta|tamtej) sytuacj\w*|\bw tej sytuacji\b|\bten (?:klient|projekt|konflikt|problem|blad|pracownik)\b"
)

_STAR_CUES: dict[str, re.Pattern[str]] = {
    "S": re.compile(
        r"\b(?:sytuacj|kontekst|okolicznos|przyklad|przypadk|przypadek)\w*|\bco sie (?:wtedy )?(?:stal|wydarzyl)\w*"
        r"|\bjak to wygladal\w*|\bkiedy to\b|\bgdzie to\b"
    ),
    "T": re.compile(
        r"\b(?:zadani|rol[ai]|role|odpowiedzialn|obowiazk|cel|celem|celu)\w*|\bco (?:pan|pani) mial\w*|\bza co (?:pan|pani) odpowiada\w*"
        r"|\bczego (?:od pana|od pani|oczekiwal)\w*|\bco nalezalo\b"
    ),
    "A": re.compile(
        r"\b(?:zrobil|dzialan|krok|kroki|postapil|zareagowal|podjal|rozwiazal|poradzil|konkretnie|zaproponowal|wdrozyl|zorganizowal|przekonal)\w*"
        r"|\bw jaki sposob\b|\bjak (?:pan|pani) (?:to )?(?:rozwiazal|zrobil|zalatwil|ogarnal)\w*|\bco (?:pan|pani) (?:wtedy )?(?:zrobil|powiedzial)\w*"
    ),
    "R": re.compile(
        r"\b(?:efekt|rezultat|wynik|skutek|skutki|skonczyl|zakonczyl|udalo|osiagn|wyniklo|nauczyl|wniosk|feedback|liczb|zmierzy|mierzy|poprawil|ocenil|reakcj)\w*"
        r"|\binformacj\w* zwrotn\w*|\bco sie zmienil\w*|\bjak (?:to )?sie skonczyl\w*"
    ),
}


class TypeVerdict(BaseModel):
    question_type: QuestionType
    open_form: bool
    sub_questions: int
    star_cues: list[str]


def _sub_questions(question: str) -> list[str]:
    parts = [p.strip() for p in question.split("?")]
    meaningful = [p for p in parts if len(normalize(p).split()) >= 2]
    return meaningful or [question.strip()]


def _strip_lead_in(norm: str) -> list[str]:
    words = norm.split()
    while words and words[0] in _LEAD_IN:
        words = words[1:]
    return words


_PREPOSITIONS = {"z", "o", "na", "w", "od", "po", "przez", "za", "dla", "do", "u", "przy"}


def _clause_opening(words: list[str]) -> str | None:
    """Zwraca „czy”, „open” albo None, zależnie od tego, czym zaczyna się zdanie składowe."""
    if not words:
        return None
    if words[0] == "czy":
        return "czy"
    if words[0] in _OPEN_WORDS:
        return "open"
    if words[0] in _PREPOSITIONS and len(words) > 1 and words[1] in _OPEN_WORDS:
        return "open"  # „w jaki sposób”, „z kim”, „po co”
    return None


def _is_closed(sub: str) -> bool:
    """Pytanie zamknięte: zaczyna się od „czy” albo czasownika (tak/nie), a nie od zaimka pytającego."""
    norm = normalize(sub)
    if _POLITE_OPEN.search(norm) or _IMPERATIVE.search(norm):
        return False
    # Pierwsze zdanie składowe zaczynające się od „czy” lub zaimka decyduje („Rozumiem, a co…”, „Czy wie Pan, jak…”).
    for clause in re.split(r"[,;:]", sub):
        opening = _clause_opening(_strip_lead_in(normalize(clause)))
        if opening:
            return opening == "czy"
    words = _strip_lead_in(norm)
    first_czy = words.index("czy") if "czy" in words else len(words)
    first_open = next((i for i, w in enumerate(words) if w in _OPEN_WORDS), len(words))
    # Bez zaimka pytającego to pytanie rozstrzygnięcia („Ma Pan doświadczenie?”).
    return not first_open < first_czy


_INTERROGATIVE = re.compile(r"\b(?:czy|" + "|".join(sorted(_OPEN_WORDS)) + r")\b")


def _coordinated_count(norm: str) -> int:
    """Liczba dodatkowych pytań dołączonych spójnikiem po pierwszym zaimku („Jak… i dlaczego…”)."""
    first = _INTERROGATIVE.search(norm)
    if not first:
        return 0
    return sum(1 for m in _COORDINATED.finditer(norm) if m.start() > first.start())


def star_cues(question: str) -> list[str]:
    norm = normalize(question)
    return [el for el, pattern in _STAR_CUES.items() if pattern.search(norm)]


def classify_question(question: str, *, has_history: bool = False) -> TypeVerdict:
    """Klasyfikuje pytanie trenującego regułami. `has_history` — czy kandydat już coś powiedział."""
    norm = normalize(question)
    subs = _sub_questions(question)
    has_qmark = "?" in question
    count = len(subs) if has_qmark else 1
    count = max(count, 1 + _coordinated_count(normalize(subs[0])))

    leading = bool(_LEADING.search(norm) or _TAG_QUESTION.search(question.strip()))
    imperative = bool(_IMPERATIVE.search(norm) or _POLITE_OPEN.search(norm))
    if has_qmark:
        closed = _is_closed(subs[0])
    else:
        closed = False  # wypowiedź bez pytajnika: polecenie albo komentarz — nie karzemy jako zamkniętej
    behavioral = bool(_BEHAVIORAL.search(norm)) and not _HYPOTHETICAL.search(norm) and (imperative or not closed)
    words = _strip_lead_in(norm)
    starts_with_conj = norm.split()[:1] in (["a"], ["i"], ["czyli"])
    followup = has_history and (bool(_FOLLOWUP.search(norm)) or (starts_with_conj and len(words) <= 12))

    qtype: QuestionType
    if count >= 2:
        qtype = "wielokrotne"
    elif leading:
        qtype = "sugerujace"
    elif closed:
        qtype = "zamkniete"
    elif followup:
        qtype = "dopytujace"
    elif behavioral:
        qtype = "behawioralne"
    else:
        qtype = "otwarte"

    open_form = qtype in OPEN_TYPES or (qtype == "wielokrotne" and not closed and not leading)
    return TypeVerdict(question_type=qtype, open_form=open_form, sub_questions=count, star_cues=star_cues(question))


TYPE_TIPS: dict[str, str] = {
    "otwarte": "Dobre pytanie otwarte. Teraz poproś o konkretny przykład z przeszłości (STAR).",
    "zamkniete": "Pytanie zamknięte — kandydat może odpowiedzieć „tak” albo „nie”. Zacznij od „Jak…”, „Co…”, „Proszę opowiedzieć o…”.",
    "sugerujace": "Pytanie sugeruje odpowiedź. Zadaj je neutralnie — bez „prawda?”, „na pewno”, „chyba”, „zakładam, że”.",
    "wielokrotne": "Kilka pytań naraz — kandydat odpowie tylko na część. Zadawaj jedno pytanie na raz.",
    "behawioralne": "Świetnie — pytanie o konkretną sytuację z przeszłości. Dopytaj o zadanie, działania i rezultat.",
    "dopytujace": "Dobre dopytanie. Upewnij się, że poznasz osobisty wkład kandydata i mierzalny rezultat.",
}


def type_label(question_type: str) -> str:
    return QUESTION_TYPE_LABELS.get(question_type, question_type)
