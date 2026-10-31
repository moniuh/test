"""Generator pytań testowych z materiału szkoleniowego.

Ścieżka AI: model proponuje pytania, kod odrzuca te bez poprawnej odpowiedzi wśród opcji
albo z cytatem, którego nie ma w materiale. Ścieżka offline: deterministyczne pytania z luką
(cloze) ze zdań z definicjami, liczbami, datami i skrótami; dystraktory pochodzą z innych
terminów materiału, a liczebniki są odmieniane tak, by pasowały do luki.
"""
from __future__ import annotations

import calendar
import hashlib
import math
import random
import re
from collections import Counter
from dataclasses import dataclass, field
from typing import Iterable, Literal

from pydantic import BaseModel

from .llm import LLMUnavailable, StructuredLLM
from .prompts import QUESTION_GENERATOR_SYSTEM, build_question_prompt
from .textutil import (
    Sentence,
    detect_injection,
    mask_pii,
    normalize_for_match,
    normalize_ws,
    split_sentences,
    verify_quote,
)

BLANK = "____"
OPTIONS_PER_QUESTION = 4
MAX_QUESTION_CHARS = 600
MAX_OPTION_CHARS = 200
MAX_EXPLANATION_CHARS = 1000
MAX_QUOTE_CHARS = 800

QuestionKind = Literal["definicja", "liczba", "data", "skrot", "pojecie", "ai", "reczne"]


@dataclass
class DraftQuestion:
    text: str
    options: list[str]
    correct_index: int
    explanation: str
    source_quote: str
    kind: str
    quote_verified: bool = True

    @property
    def correct_answer(self) -> str:
        return self.options[self.correct_index]


@dataclass
class Rejection:
    question: str
    reason: str


@dataclass
class GenerationResult:
    source: Literal["ai", "offline"]
    questions: list[DraftQuestion]
    rejected: list[Rejection] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    injection_suspected: bool = False


# --- Schematy odpowiedzi modelu (proste typy, walidacja w kodzie) ---------------------------

class AIQuestion(BaseModel):
    question: str
    options: list[str]
    correct_answer: str
    explanation: str
    source_quote: str


class AIQuestionSet(BaseModel):
    questions: list[AIQuestion]
    injection_detected: bool


# --- Walidacja pytań (AI i ręcznych) --------------------------------------------------------

class QuestionValidationError(ValueError):
    pass


def check_question_structure(text: str, options: list[str], correct_index: int) -> None:
    """Wspólne reguły: treść, dokładnie 4 niepuste i różne odpowiedzi, poprawny indeks."""
    if not normalize_ws(text):
        raise QuestionValidationError("Pytanie nie ma treści.")
    if len(options) != OPTIONS_PER_QUESTION:
        raise QuestionValidationError(
            f"Pytanie musi mieć dokładnie {OPTIONS_PER_QUESTION} odpowiedzi (jest {len(options)})."
        )
    if any(not normalize_ws(o) for o in options):
        raise QuestionValidationError("Odpowiedzi nie mogą być puste.")
    if len({normalize_for_match(o) for o in options}) != len(options):
        raise QuestionValidationError("Odpowiedzi muszą się od siebie różnić.")
    if not 0 <= correct_index < len(options):
        raise QuestionValidationError("Wskazana poprawna odpowiedź nie istnieje.")


def validate_ai_question(q: AIQuestion, material: str) -> DraftQuestion:
    """Zamienia propozycję modelu na szkic pytania albo rzuca `QuestionValidationError`."""
    text = normalize_ws(q.question)[:MAX_QUESTION_CHARS]
    options = [normalize_ws(o)[:MAX_OPTION_CHARS] for o in q.options]
    check_question_structure(text, options, 0)  # treść, liczba, puste i powtórzone odpowiedzi
    wanted = normalize_for_match(q.correct_answer)
    matches = [i for i, o in enumerate(options) if normalize_for_match(o) == wanted]
    if len(matches) != 1:
        raise QuestionValidationError("Poprawna odpowiedź nie występuje wśród opcji.")
    quote = normalize_ws(q.source_quote)[:MAX_QUOTE_CHARS]
    if not verify_quote(quote, material):
        raise QuestionValidationError("Cytat źródłowy nie występuje w materiale.")
    explanation = normalize_ws(q.explanation)[:MAX_EXPLANATION_CHARS]
    if not explanation:
        raise QuestionValidationError("Brak wyjaśnienia poprawnej odpowiedzi.")
    return DraftQuestion(
        text=text, options=options, correct_index=matches[0], explanation=explanation,
        source_quote=quote, kind="ai", quote_verified=True,
    )


# --- Generator główny ---------------------------------------------------------------------

def generate_questions(
    material: str,
    *,
    count: int,
    llm: StructuredLLM | None,
    title: str = "",
    existing: Iterable[str] = (),
) -> GenerationResult:
    """Generuje szkice pytań: AI, gdy dostępne, w przeciwnym razie (lub po błędzie) offline."""
    existing_keys = {normalize_for_match(t) for t in existing}
    injection = detect_injection(material)
    if llm is None:
        result = generate_offline(material, count=count, existing=existing_keys)
        result.injection_suspected = injection
        return result

    masked = mask_pii(material)
    try:
        response = llm.generate(
            system=QUESTION_GENERATOR_SYSTEM,
            prompt=build_question_prompt(title, masked, count, sorted(existing_keys)),
            schema=AIQuestionSet,
        )
    except LLMUnavailable as exc:
        result = generate_offline(material, count=count, existing=existing_keys)
        result.warnings.append(f"AI niedostępne ({exc}) — użyto generatora offline.")
        result.injection_suspected = injection
        return result

    accepted: list[DraftQuestion] = []
    rejected: list[Rejection] = []
    seen = set(existing_keys)
    for proposal in response.questions:
        try:
            draft = validate_ai_question(proposal, masked)
        except QuestionValidationError as exc:
            rejected.append(Rejection(question=normalize_ws(proposal.question)[:200], reason=str(exc)))
            continue
        key = normalize_for_match(draft.text)
        if key in seen:
            rejected.append(Rejection(question=draft.text[:200], reason="Duplikat istniejącego pytania."))
            continue
        seen.add(key)
        accepted.append(draft)
        if len(accepted) >= count:
            break

    injection = injection or response.injection_detected
    if not accepted:
        result = generate_offline(material, count=count, existing=existing_keys)
        result.rejected = rejected
        result.warnings.append("AI nie dostarczyło poprawnych pytań — użyto generatora offline.")
        result.injection_suspected = injection
        return result
    return GenerationResult(source="ai", questions=accepted, rejected=rejected, injection_suspected=injection)


# --- Offline: słowniki i odmiana ----------------------------------------------------------

# forma → (lemat, znacznik); znacznik pozwala odgadnąć przypadek wymagany przez lukę
_DURATION_FORMS: dict[str, tuple[str, str]] = {
    "dzień": ("dzień", "sg_nom"), "dnia": ("dzień", "sg_gen"), "dni": ("dzień", "pl_any"),
    "tydzień": ("tydzień", "sg_nom"), "tygodnia": ("tydzień", "sg_gen"),
    "tygodnie": ("tydzień", "pl_nom"), "tygodni": ("tydzień", "pl_gen"),
    "miesiąc": ("miesiąc", "sg_nom"), "miesiąca": ("miesiąc", "sg_gen"),
    "miesiące": ("miesiąc", "pl_nom"), "miesięcy": ("miesiąc", "pl_gen"),
    "rok": ("rok", "sg_nom"), "roku": ("rok", "sg_gen"), "lata": ("rok", "pl_nom"), "lat": ("rok", "pl_gen"),
    "godzina": ("godzina", "sg_nom"), "godzinę": ("godzina", "sg_acc"),
    "godziny": ("godzina", "few_or_gen"), "godzin": ("godzina", "pl_gen"),
    "minuta": ("minuta", "sg_nom"), "minutę": ("minuta", "sg_acc"),
    "minuty": ("minuta", "few_or_gen"), "minut": ("minuta", "pl_gen"),
}
_DURATION_PLURALS = {
    "dzień": ("dni", "dni"), "tydzień": ("tygodnie", "tygodni"), "miesiąc": ("miesiące", "miesięcy"),
    "rok": ("lata", "lat"), "godzina": ("godziny", "godzin"), "minuta": ("minuty", "minut"),
}
_DURATION_DAYS = {"dzień": 1.0, "tydzień": 7.0, "miesiąc": 30.4, "rok": 365.0,
                  "godzina": 1 / 24, "minuta": 1 / 1440}
_PLAUSIBLE = {
    "dzień": [3, 5, 7, 10, 14, 21, 30, 60, 90], "tydzień": [2, 3, 4, 6, 8],
    "miesiąc": [2, 3, 6, 9, 12, 18, 24, 36], "rok": [2, 3, 4, 5, 6, 10],
    "godzina": [8, 12, 24, 36, 48, 72, 96], "minuta": [5, 10, 15, 20, 30, 45, 60],
}
# Dystraktor z innej jednostki tylko w podobnej skali (np. nie „12 miesięcy” zamiast „5 minut”).
_MAX_CROSS_UNIT_DISTANCE = math.log(20)
_MONTHS_GEN = ["stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca",
               "sierpnia", "września", "października", "listopada", "grudnia"]
_FIXED_UNITS = ["km/h", "kg", "mm", "cm", "m", "%", "zł", "°C", "dB"]

_DURATION_RE = re.compile(
    r"(?<![\w,.])(\d{1,3})\s+(" + "|".join(sorted(_DURATION_FORMS, key=len, reverse=True)) + r")\b"
)
_FIXED_RE = re.compile(
    r"(?<![\w,.])(\d{1,4}(?:,\d{1,2})?)(\s?)(" + "|".join(re.escape(u) for u in _FIXED_UNITS) + r")(?![\w/])"
)
_NOUN_NUM_RE = re.compile(r"(?<![\w,.§])(\d{1,5})\s+([a-ząćęłńóśźż]{3,})\b")
_DATE_RE = re.compile(r"(?<!\d)(\d{1,2})\s+(" + "|".join(_MONTHS_GEN) + r")\s+(\d{4})(?!\d)")
_NUMDATE_RE = re.compile(r"(?<![\d.])(\d{1,2})\.(\d{1,2})\.(\d{4})(?![\d])")
_ACRONYM_RE = re.compile(r"(?<![\w-])([A-ZĄĆĘŁŃÓŚŹŻ]{2,6})(?![\w-])")
_ROMAN_RE = re.compile(r"^[IVXLCDM]+$")
_REF_BEFORE_RE = re.compile(r"(?:art|ust|pkt|nr|poz|lit|§)\.?\s*$", re.IGNORECASE)
_DEF_RE = re.compile(
    r"^(?P<term>[A-ZĄĆĘŁŃÓŚŹŻ][^\d,;:()„”\"?!.]{1,60}?)"
    r"(?:\s+(?:[-–—]\s+)?(?:to|oznacza|jest\s+to)\s+|\s+[-–—]\s+(?=[a-ząćęłńóśźż]))"
    r"(?P<rest>\S.{8,})$"
)
_TERM_START_STOP = {
    "w", "we", "na", "do", "od", "po", "przy", "za", "dla", "z", "ze", "o", "u", "pod", "nad", "przed",
    "jeśli", "jeżeli", "gdy", "kiedy", "to", "ten", "ta", "te", "tak", "nie", "dlatego", "natomiast",
    "również", "także", "wtedy", "tu", "tutaj", "oto", "czyli", "jest", "są", "celem", "każdy",
    "każda", "każde", "ważne", "pamiętaj", "uwaga", "jednym", "jedną", "jednak", "zatem", "potem",
}
_TERM_INNER_STOP = {
    "ma", "mają", "może", "mogą", "musi", "muszą", "jest", "są", "należy", "powinien", "powinna",
    "powinno", "nie", "się", "który", "która", "które", "że", "aby", "jak", "oraz", "lub", "albo",
    "jego", "jej", "ich", "tym", "tego", "był", "była", "było",
}


def _is_few(n: int) -> bool:
    return n % 10 in (2, 3, 4) and n % 100 not in (12, 13, 14)


def duration_context(n: int, tag: str) -> str:
    """Przypadek luki: 'nom' (mianownik/biernik), 'gen' (dopełniacz) albo 'any' (forma wspólna)."""
    if n == 1:
        return "gen" if tag in ("sg_gen", "few_or_gen") else "nom"
    if _is_few(n):
        if tag in ("pl_nom", "few_or_gen"):
            return "nom"
        if tag == "pl_gen":
            return "gen"
    return "any"


def inflect_duration(n: int, lemma: str, context: str) -> str | None:
    """Liczebnik z rzeczownikiem w formie pasującej do luki, np. (3, rok, nom) → „3 lata”.

    Zwraca None, gdy forma byłaby niejednoznaczna dla danego kontekstu.
    """
    if n < 2:
        return None
    pl_nom, pl_gen = _DURATION_PLURALS[lemma]
    if context == "gen":
        return f"{n} {pl_gen}"
    if context == "nom":
        return f"{n} {pl_nom if _is_few(n) else pl_gen}"
    if lemma == "dzień" or not _is_few(n):
        return f"{n} {pl_gen}"
    return None


def _nice(x: float) -> int:
    if x < 10:
        return max(1, round(x))
    if x < 100:
        return int(round(x / 5) * 5)
    if x < 1000:
        return int(round(x / 10) * 10)
    return int(round(x / 50) * 50)


def _closeness(a: float, b: float) -> float:
    return abs(math.log(max(a, 1e-9)) - math.log(max(b, 1e-9)))


def _fmt_number(value: float) -> str:
    return str(int(value)) if float(value).is_integer() else f"{value:.2f}".rstrip("0").replace(".", ",")


# --- Offline: ekstrakcja terminów ---------------------------------------------------------

@dataclass(frozen=True)
class _Candidate:
    kind: str
    sentence_index: int
    start: int
    end: int
    answer: str
    distractors: tuple[str, ...]


@dataclass
class _Pools:
    durations: list[tuple[int, str]] = field(default_factory=list)  # (liczba, lemat)
    fixed: dict[str, list[float]] = field(default_factory=dict)
    nouns: dict[str, list[int]] = field(default_factory=dict)
    dates: list[tuple[int, int, int]] = field(default_factory=list)
    numdates: list[tuple[int, int, int]] = field(default_factory=list)
    acronyms: Counter = field(default_factory=Counter)
    definitions: list[tuple[str, int]] = field(default_factory=list)  # (termin, indeks zdania)


def _valid_term(term: str) -> bool:
    words = term.split()
    if not 1 <= len(words) <= 5:
        return False
    first = words[0].casefold()
    if first in _TERM_START_STOP or first.startswith("naj"):
        return False
    if any(w.casefold() in _TERM_INNER_STOP for w in words):
        return False
    return all(re.fullmatch(r"[\wąćęłńóśźżĄĆĘŁŃÓŚŹŻ-]+", w) for w in words)


def _usable(sentence: Sentence) -> bool:
    """Pełne zdanie: 5–45 słów i znak końca zdania (nagłówki odpadają)."""
    text = sentence.text.rstrip()
    words = text.split()
    return 5 <= len(words) <= 45 and BLANK not in text and text.endswith((".", "!", "?", ";", "…"))


def _definition(sentence: Sentence) -> tuple[str, str] | None:
    match = _DEF_RE.match(sentence.text)
    if not match:
        return None
    term = match.group("term").strip()
    rest = match.group("rest")
    if not _valid_term(term) or normalize_for_match(term) in normalize_for_match(rest):
        return None
    return term, rest


def _collect_pools(sentences: list[Sentence]) -> _Pools:
    pools = _Pools()
    for idx, s in enumerate(sentences):
        text = s.text
        for m in _DURATION_RE.finditer(text):
            n = int(m.group(1))
            pools.durations.append((n, _DURATION_FORMS[m.group(2)][0]))
        for m in _FIXED_RE.finditer(text):
            pools.fixed.setdefault(m.group(3), []).append(float(m.group(1).replace(",", ".")))
        for m in _NOUN_NUM_RE.finditer(text):
            n, noun = int(m.group(1)), m.group(2)
            if _plain_noun_number(text, m, n, noun):
                pools.nouns.setdefault(noun, []).append(n)
        for m in _DATE_RE.finditer(text):
            pools.dates.append((int(m.group(1)), _MONTHS_GEN.index(m.group(2)) + 1, int(m.group(3))))
        for m in _NUMDATE_RE.finditer(text):
            pools.numdates.append((int(m.group(1)), int(m.group(2)), int(m.group(3))))
        if text.upper() != text:
            for m in _ACRONYM_RE.finditer(text):
                if not _ROMAN_RE.match(m.group(1)):
                    pools.acronyms[m.group(1)] += 1
        if _usable(s):
            definition = _definition(s)
            if definition:
                pools.definitions.append((definition[0], idx))
    return pools


def _plain_noun_number(text: str, m: re.Match, n: int, noun: str) -> bool:
    if noun in _DURATION_FORMS or noun in _MONTHS_GEN or noun in {"r", "roku"}:
        return False
    if 1900 <= n <= 2100 or n < 5 or _is_few(n):
        return False
    return not _REF_BEFORE_RE.search(text[: m.start()])


def _pick(answer: str, candidates: Iterable[str], sentence_text: str, limit: int = 3) -> list[str]:
    """Wybiera różne dystraktory, pomijając te równe odpowiedzi lub obecne już w zdaniu."""
    seen = {normalize_for_match(answer)}
    lowered = normalize_for_match(sentence_text)
    chosen: list[str] = []
    for c in candidates:
        key = normalize_for_match(c)
        if not key or key in seen or re.search(rf"(?<!\w){re.escape(key)}(?!\w)", lowered):
            continue
        seen.add(key)
        chosen.append(c)
        if len(chosen) == limit:
            break
    return chosen


def _duration_candidates(sentence: Sentence, idx: int, pools: _Pools) -> list[_Candidate]:
    out = []
    for m in _DURATION_RE.finditer(sentence.text):
        n = int(m.group(1))
        lemma, tag = _DURATION_FORMS[m.group(2)]
        ctx = duration_context(n, tag)
        answer_days = n * _DURATION_DAYS[lemma]
        material = sorted(
            {(v, u) for v, u in pools.durations
             if _closeness(v * _DURATION_DAYS[u], answer_days) <= _MAX_CROSS_UNIT_DISTANCE},
            key=lambda vu, lm=lemma, ad=answer_days: (vu[1] != lm, _closeness(vu[0] * _DURATION_DAYS[vu[1]], ad)),
        )
        perturbed = [(v, lemma) for v in sorted(_PLAUSIBLE[lemma], key=lambda v, n0=n: _closeness(v, n0))]
        # Najpierw terminy z materiału, potem warianty liczby; bez dwóch opcji o tej samej długości trwania.
        used_days = [round(answer_days, 3)]
        options: list[str] = []
        for value, unit in material + perturbed:
            days = round(value * _DURATION_DAYS[unit], 3)
            surface = inflect_duration(value, unit, ctx)
            if surface and all(abs(days - u) / max(u, 1e-9) >= 0.05 for u in used_days):
                used_days.append(days)
                options.append(surface)
        distractors = _pick(m.group(0), options, sentence.text)
        if len(distractors) == 3:
            out.append(_Candidate("liczba", idx, m.start(), m.end(), m.group(0), tuple(distractors)))
    return out


def _fixed_candidates(sentence: Sentence, idx: int, pools: _Pools) -> list[_Candidate]:
    out = []
    for m in _FIXED_RE.finditer(sentence.text):
        value = float(m.group(1).replace(",", "."))
        sep, unit = m.group(2), m.group(3)
        values = sorted({v for v in pools.fixed.get(unit, []) if v != value}, key=lambda v: _closeness(v, value))
        if value >= 1 and value.is_integer():
            if unit == "%":
                extra = sorted({50, 60, 70, 75, 80, 90, 100} - {value}, key=lambda v: abs(v - value))
            else:
                extra = [_nice(value * f) for f in (0.75, 1.5, 2, 0.5, 3, 0.25)]
            values += [v for v in extra if v > 0 and v != value]
        surfaces = [f"{_fmt_number(v)}{sep}{unit}" for v in values]
        distractors = _pick(m.group(0), surfaces, sentence.text)
        if len(distractors) == 3:
            out.append(_Candidate("liczba", idx, m.start(), m.end(), m.group(0), tuple(distractors)))
    return out


def _noun_number_candidates(sentence: Sentence, idx: int, pools: _Pools) -> list[_Candidate]:
    out = []
    for m in _NOUN_NUM_RE.finditer(sentence.text):
        n, noun = int(m.group(1)), m.group(2)
        if not _plain_noun_number(sentence.text, m, n, noun):
            continue
        values = sorted({v for v in pools.nouns.get(noun, []) if v != n}, key=lambda v: _closeness(v, n))
        values += [_nice(n * f) for f in (0.5, 1.5, 2, 0.2, 4, 5)]
        values = [v for v in values if v >= 5 and not _is_few(v) and v != n]
        distractors = _pick(m.group(0), [f"{v} {noun}" for v in values], sentence.text)
        if len(distractors) == 3:
            out.append(_Candidate("liczba", idx, m.start(), m.end(), m.group(0), tuple(distractors)))
    return out


def _date_shift(day: int, month: int, year: int) -> list[tuple[int, int, int]]:
    shifts = [(0, 1), (0, -1), (1, 0), (0, 3), (-1, 0), (0, -4)]  # (lata, miesiące)
    out = []
    for dy, dm in shifts:
        index = month - 1 + dm
        y, mth = year + dy + index // 12, index % 12 + 1
        if day <= calendar.monthrange(y, mth)[1]:
            out.append((day, mth, y))
    return out


def _date_candidates(sentence: Sentence, idx: int, pools: _Pools) -> list[_Candidate]:
    out = []
    for m in _DATE_RE.finditer(sentence.text):
        d, mo, y = int(m.group(1)), _MONTHS_GEN.index(m.group(2)) + 1, int(m.group(3))
        pool = [x for x in pools.dates if x != (d, mo, y)] + _date_shift(d, mo, y)
        surfaces = [f"{dd} {_MONTHS_GEN[mm - 1]} {yy}" for dd, mm, yy in pool]
        distractors = _pick(m.group(0), surfaces, sentence.text)
        if len(distractors) == 3:
            out.append(_Candidate("data", idx, m.start(), m.end(), m.group(0), tuple(distractors)))
    for m in _NUMDATE_RE.finditer(sentence.text):
        d, mo, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
        if not 1 <= mo <= 12:
            continue
        pad_d, pad_m = len(m.group(1)), len(m.group(2))
        pool = [x for x in pools.numdates if x != (d, mo, y)] + _date_shift(d, mo, y)
        surfaces = [f"{dd:0{pad_d}d}.{mm:0{pad_m}d}.{yy}" for dd, mm, yy in pool]
        distractors = _pick(m.group(0), surfaces, sentence.text)
        if len(distractors) == 3:
            out.append(_Candidate("data", idx, m.start(), m.end(), m.group(0), tuple(distractors)))
    return out


def _acronym_candidates(sentence: Sentence, idx: int, pools: _Pools) -> list[_Candidate]:
    out = []
    if sentence.text.upper() == sentence.text:
        return out
    ranked = [a for a, _ in sorted(pools.acronyms.items(), key=lambda kv: (-kv[1], kv[0]))]
    for m in _ACRONYM_RE.finditer(sentence.text):
        acronym = m.group(1)
        if _ROMAN_RE.match(acronym) or sentence.text.count(acronym) > 1:
            continue
        before, after = sentence.text[: m.start()], sentence.text[m.end():]
        if before.endswith("(") and after.startswith(")"):
            continue  # rozwinięcie skrótu tuż obok zdradza odpowiedź
        distractors = _pick(acronym, ranked, sentence.text)
        if len(distractors) == 3:
            out.append(_Candidate("skrot", idx, m.start(), m.end(), acronym, tuple(distractors)))
    return out


def _definition_candidates(sentences: list[Sentence], pools: _Pools) -> list[_Candidate]:
    out = []
    terms = [t for t, _ in pools.definitions]
    for term, idx in pools.definitions:
        others = [t for t in terms if t != term]
        distractors = _pick(term, others, sentences[idx].text)
        if len(distractors) == 3:
            out.append(_Candidate("definicja", idx, 0, len(term), term, tuple(distractors)))
    # Te same pojęcia użyte na początku innych zdań (podmiot → mianownik, jak w definicji).
    definition_sentences = {idx for _, idx in pools.definitions}
    for term, _ in pools.definitions:
        key = normalize_for_match(term)
        for idx, s in enumerate(sentences):
            if idx in definition_sentences or not _usable(s):
                continue
            head = s.text[: len(term)]
            tail = s.text[len(term):]
            if normalize_for_match(head) != key or not tail[:1].isspace():
                continue
            if key in normalize_for_match(tail):
                continue
            distractors = _pick(head, [t for t in terms if t != term], s.text)
            if len(distractors) == 3:
                out.append(_Candidate("pojecie", idx, 0, len(term), head, tuple(distractors)))
    return out


_KIND_ORDER = ("definicja", "liczba", "data", "skrot", "pojecie")


def _to_question(candidate: _Candidate, sentence: Sentence) -> DraftQuestion:
    cloze = sentence.text[: candidate.start] + BLANK + sentence.text[candidate.end:]
    lead = "Uzupełnij lukę w definicji" if candidate.kind == "definicja" else "Uzupełnij lukę w zdaniu z materiału"
    options = [candidate.answer, *candidate.distractors]
    digest = hashlib.sha256(f"{sentence.text}|{candidate.answer}".encode()).hexdigest()
    random.Random(digest).shuffle(options)
    return DraftQuestion(
        text=f"{lead}: „{cloze}”",
        options=options,
        correct_index=options.index(candidate.answer),
        explanation=f"Poprawna odpowiedź to „{candidate.answer}”. Materiał: „{sentence.text}”",
        source_quote=sentence.text,
        kind=candidate.kind,
        quote_verified=True,
    )


def generate_offline(material: str, *, count: int, existing: Iterable[str] = ()) -> GenerationResult:
    """Deterministyczny generator pytań z luką. Ten sam materiał → te same pytania."""
    sentences = split_sentences(material)
    pools = _collect_pools(sentences)
    by_kind: dict[str, list[_Candidate]] = {k: [] for k in _KIND_ORDER}
    for c in _definition_candidates(sentences, pools):
        by_kind[c.kind].append(c)
    for idx, s in enumerate(sentences):
        if not _usable(s):
            continue
        for c in (*_duration_candidates(s, idx, pools), *_fixed_candidates(s, idx, pools),
                  *_noun_number_candidates(s, idx, pools)):
            by_kind["liczba"].append(c)
        by_kind["data"].extend(_date_candidates(s, idx, pools))
        by_kind["skrot"].extend(_acronym_candidates(s, idx, pools))
    for kind in _KIND_ORDER:
        by_kind[kind].sort(key=lambda c: (c.sentence_index, c.start))

    # Zdanie z liczbą, datą lub skrótem daje lepsze pytanie niż luka na samym pojęciu.
    busy = {c.sentence_index for k in ("liczba", "data", "skrot") for c in by_kind[k]}
    by_kind["pojecie"] = [c for c in by_kind["pojecie"] if c.sentence_index not in busy]

    existing_keys = {normalize_for_match(t) for t in existing}
    used_sentences: set[int] = set()
    used_answers: set[tuple[str, str]] = set()
    questions: list[DraftQuestion] = []
    cursors = {k: 0 for k in _KIND_ORDER}
    # Na zmianę z każdego rodzaju — test nie składa się z samych liczb ani samych definicji.
    while len(questions) < count and any(cursors[k] < len(by_kind[k]) for k in _KIND_ORDER):
        for kind in _KIND_ORDER:
            while cursors[kind] < len(by_kind[kind]):
                c = by_kind[kind][cursors[kind]]
                cursors[kind] += 1
                # Ta sama odpowiedź może wrócić tylko w pytaniu innego rodzaju.
                answer_key = (c.kind, normalize_for_match(c.answer))
                if c.sentence_index in used_sentences or answer_key in used_answers:
                    continue
                question = _to_question(c, sentences[c.sentence_index])
                if normalize_for_match(question.text) in existing_keys:
                    continue
                used_sentences.add(c.sentence_index)
                used_answers.add(answer_key)
                questions.append(question)
                break
            if len(questions) >= count:
                break

    warnings = []
    if len(questions) < count:
        warnings.append(
            f"Generator offline znalazł {len(questions)} z {count} pytań — materiał ma za mało "
            "definicji, liczb, dat lub skrótów. Dodaj pytania ręcznie albo włącz AI."
        )
    return GenerationResult(source="offline", questions=questions, warnings=warnings)
