"""Ocena offline: dopasowanie słów kluczowych i synonimów z prostą fleksją polską.

Dla każdego wymagania szukamy zdań CV zawierających słowo kluczowe/synonim. Werdykt:
- „nie spełnia” — dopasowanie poprzedzone zaprzeczeniem („brak doświadczenia w …”, „nie znam …”),
- „częściowo” — kwalifikator osłabiający („podstawy”, „kurs”, „w trakcie”), dopasowanie tylko
  terminu z listy „częściowo” albo staż krótszy niż wymagany / niepotwierdzony,
- „spełnia” — dopasowanie bez powyższych zastrzeżeń,
- „brak danych” — brak dopasowania.
Dowodem jest wyciągnięte zdanie (dosłowny fragment CV z offsetami).
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field
from datetime import date

from .models import Requirement, RequirementResult, Verdict
from .textutil import Sentence, Token, find_phrase, fold, split_sentences, tokenize

_NEGATIONS = {"brak", "nie", "bez", "nigdy", "no", "not", "without", "lack"}
_NEGATION_EXCEPTIONS = re.compile(r"\b(?:nie\s+tylko|bez\s+problemu|bez\s+trudu|bez\s+zarzutu|not\s+only)\b")
_QUALIFIERS = re.compile(
    r"\b(?:podstaw\w*|kurs\w*|w\s+trakcie|ucze\s+sie|uczylem/am\s+sie|poczatkuj\w*|elementarn\w*|"
    r"teoretyczn\w*|biern\w*|ogoln\w*\s+znajomosc|basic\w*|beginner|learning|course)\b"
)
_NUMBER_WORDS = {
    "jeden": 1.0, "jednego": 1.0, "poltora": 1.5, "dwa": 2.0, "dwoch": 2.0,
    "trzy": 3.0, "trzech": 3.0, "cztery": 4.0, "czterech": 4.0, "piec": 5.0, "pieciu": 5.0,
    "szesc": 6.0, "szesciu": 6.0, "siedem": 7.0, "osiem": 8.0, "dziewiec": 9.0, "dziesiec": 10.0,
}
_YEARS_RE = re.compile(
    r"(?:ponad\s+|powyzej\s+|over\s+|ok\.\s+|okolo\s+)?"
    r"(\d+(?:[.,]\d+)?|jeden|jednego|poltora|dwa|dwoch|trzy|trzech|cztery|czterech|piec|pieciu|szesc|"
    r"szesciu|siedem|osiem|dziewiec|dziesiec)\s*\+?\s*(lat|lata|rok|roku|years?|yrs)\b"
)
_LETNI_RE = re.compile(r"(?<!\d)(\d{1,2})\s*-\s*letni\w*")
_PRESENT = r"obecnie|nadal|teraz|dzis|aktualnie|present|now|current"
_RANGE_RE = re.compile(
    rf"(?:(\d{{1,2}})[./])?((?:19|20)\d{{2}})\s*(?:-|do|to)\s*(?:(?:(\d{{1,2}})[./])?((?:19|20)\d{{2}})|({_PRESENT}))"
)
_RANK = {"meets": 3, "partial": 2, "fails": 1, "no_data": 0}


@dataclass
class _Hit:
    sentence: Sentence
    verdict: Verdict
    terms: list[str] = field(default_factory=list)
    note: str = ""


def _normalize_dashes(text: str) -> str:
    return fold(text).replace("–", "-").replace("—", "-")


def explicit_years(text: str) -> float | None:
    """Największa liczba lat podana wprost („5 lat”, „ponad 3 lata”, „półtora roku”)."""
    norm = _normalize_dashes(text)
    values = [_NUMBER_WORDS.get(m.group(1)) or float(m.group(1).replace(",", ".")) for m in _YEARS_RE.finditer(norm)]
    values += [float(m.group(1)) for m in _LETNI_RE.finditer(norm)]
    values = [v for v in values if 0 < v <= 50]
    return max(values) if values else None


def date_ranges(text: str, today: date) -> list[tuple[float, float]]:
    """Zakresy dat zatrudnienia jako (początek, koniec) w latach ułamkowych."""
    out: list[tuple[float, float]] = []
    for m in _RANGE_RE.finditer(_normalize_dashes(text)):
        start = int(m.group(2)) + (int(m.group(1) or 1) - 1) / 12
        if m.group(5):
            end = today.year + (today.month - 1) / 12
        else:
            end = int(m.group(4)) + (int(m.group(3) or 1) - 1) / 12
        if 0 <= end - start <= 50:
            out.append((start, end))
    return out


def merged_years(ranges: list[tuple[float, float]]) -> float:
    total, cur_start, cur_end = 0.0, None, None
    for s, e in sorted(ranges):
        if cur_end is None or s > cur_end:
            if cur_end is not None:
                total += cur_end - cur_start
            cur_start, cur_end = s, e
        else:
            cur_end = max(cur_end, e)
    if cur_end is not None:
        total += cur_end - cur_start
    return round(total, 1)


_CLAUSE_BREAK = re.compile(r"[,;:()]|\b(?:ale|lecz|jednak|but)\b")


def _years(value: float) -> str:
    return f"{value:g}".replace(".", ",")


def _negated(tokens: list[Token], first: int, sentence: str) -> bool:
    """Zaprzeczenie do 6 słów przed dopasowaniem, w tym samym członie zdania (bez przecinka/„ale” pomiędzy)."""
    if _NEGATION_EXCEPTIONS.search(fold(sentence)):
        return False
    for tok in reversed(tokens[max(0, first - 6) : first]):
        if fold(tok.text) in _NEGATIONS:
            between = fold(sentence[tok.end : tokens[first].start])
            return not _CLAUSE_BREAK.search(between)
    return False


def _context_lines(text: str, sentence: Sentence, max_back: int = 4) -> list[str]:
    """Linia zdania i najbliższe wcześniejsze linie tego samego bloku (nagłówek stanowiska z datami)."""
    line_start = text.rfind("\n", 0, sentence.start) + 1
    line_end = text.find("\n", sentence.end)
    lines = [text[line_start : line_end if line_end >= 0 else len(text)]]
    cursor = line_start - 1
    for _ in range(max_back):
        if cursor <= 0:
            break
        prev_start = text.rfind("\n", 0, cursor) + 1
        prev = text[prev_start:cursor]
        if not prev.strip():
            break
        lines.append(prev)
        cursor = prev_start - 1
    return lines


def evaluate_requirement(
    req: Requirement, text: str, sentences: list[Sentence], today: date
) -> RequirementResult:
    hits: list[_Hit] = []
    positive_terms = req.keywords + req.synonyms
    for sent in sentences:
        tokens = tokenize(sent.text, offset=0)
        matched: list[tuple[str, int]] = []
        for term in positive_terms:
            span = find_phrase(term, tokens)
            if span:
                matched.append((term, span[0]))
        if matched:
            first = min(i for _, i in matched)
            terms = [t for t, _ in matched]
            if _negated(tokens, first, sent.text):
                hits.append(_Hit(sent, "fails", terms, "Dopasowanie poprzedzone zaprzeczeniem."))
            elif _QUALIFIERS.search(fold(sent.text)):
                hits.append(_Hit(sent, "partial", terms, "Kwalifikator osłabiający (np. „podstawy”, „kurs”)."))
            else:
                hits.append(_Hit(sent, "meets", terms))
            continue
        partial = [t for t in req.partial_keywords if find_phrase(t, tokens)]
        if partial:
            hits.append(_Hit(sent, "partial", partial, "Dopasowanie terminu oznaczającego częściowe spełnienie."))

    if not hits:
        return RequirementResult(
            requirement_id=req.id, name=req.name, kind=req.kind, weight=req.weight, verdict="no_data",
            note="Nie znaleziono słów kluczowych ani synonimów.",
        )

    if req.min_years:
        positive = [h for h in hits if h.verdict == "meets"]
        if positive:
            explicit = [y for h in positive if (y := explicit_years(h.sentence.text)) is not None]
            ranges: list[tuple[float, float]] = []
            for h in positive:
                for line in _context_lines(text, h.sentence):
                    found = date_ranges(line, today)
                    if found:
                        ranges.extend(found)
                        break
            years = max([*explicit, merged_years(ranges) if ranges else 0.0]) if (explicit or ranges) else None
            for h in positive:
                if years is None:
                    h.verdict, h.note = "partial", f"Nie znaleziono informacji o stażu (wymagane min. {_years(req.min_years)} lat)."
                elif years < req.min_years:
                    h.verdict, h.note = "partial", f"Staż ok. {_years(years)} lat — wymagane min. {_years(req.min_years)}."
                else:
                    h.note = f"Staż ok. {_years(years)} lat (wymagane min. {_years(req.min_years)})."

    # Najlepszy werdykt; przy remisie zdanie z informacją o stażu (bardziej treściwy dowód), potem najwcześniejsze.
    best = max(
        hits,
        key=lambda h: (
            _RANK[h.verdict],
            explicit_years(h.sentence.text) is not None or bool(date_ranges(h.sentence.text, today)),
            -h.sentence.start,
        ),
    )
    terms = sorted({t for h in hits for t in h.terms}, key=str.lower)
    return RequirementResult(
        requirement_id=req.id, name=req.name, kind=req.kind, weight=req.weight, verdict=best.verdict,
        evidence=best.sentence.text, evidence_start=best.sentence.start, evidence_end=best.sentence.end,
        matched_terms=terms, note=best.note,
    )


def evaluate_offline(
    requirements: list[Requirement], text: str, excluded: list[Sentence] | None = None, today: date | None = None
) -> list[RequirementResult]:
    """Ocena wszystkich wymagań; zdania z `excluded` (np. próba manipulacji) są pomijane."""
    today = today or date.today()
    blocked = [(s.start, s.end) for s in (excluded or [])]
    sentences = [
        s for s in split_sentences(text) if not any(a < s.end and b > s.start for a, b in blocked)
    ]
    return [evaluate_requirement(req, text, sentences, today) for req in requirements]
