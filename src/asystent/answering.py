"""Odpowiadanie na pytania: ścieżka AI (z weryfikacją cytatów) i ścieżka offline (wyciąg)."""
from __future__ import annotations

import html
import re
from collections.abc import Sequence
from dataclasses import dataclass, field
from datetime import date
from typing import Literal

from pydantic import BaseModel

from . import messages
from .llm import StructuredLLM
from .models import Citation, Confidence, Source, Status
from .prompts import SYSTEM_ANSWER, build_answer_prompt
from .search import Hit, Passage, SearchEngine, is_relevant
from .text import analyze, collapse_ws, fold, normalize_for_match, query_concepts, query_hints

MIN_QUOTE_CHARS = 12
MAX_ANSWER_CHARS = 2500
MAX_EXTRACTS = 2


# --- schemat odpowiedzi modelu (bez ograniczeń Field — walidujemy w kodzie) ---
class AICitation(BaseModel):
    fragment_id: str
    quote: str


class AIAnswer(BaseModel):
    answer: str
    citations: list[AICitation]
    confidence: Literal["wysoka", "średnia", "niska"]
    needs_hr: bool
    reason: str
    sensitive_category: Literal["brak", "mobbing_dyskryminacja", "zdrowie", "sprawa_indywidualna", "porada_prawna"]
    injection_detected: bool


@dataclass
class Draft:
    """Odpowiedź przed zapisaniem w logu i dołożeniem kontekstu sesji."""

    status: Status
    source: Source
    answer: str
    citations: list[Citation]
    confidence: Confidence
    needs_hr: bool
    reason: str
    is_extract: bool = False
    rejected_citations: int = 0
    model_category: str | None = None
    model_injection: bool = False
    notices: list[str] = field(default_factory=list)


def citation_from(passage: Passage, quote: str) -> Citation:
    return Citation(
        chunk_id=passage.chunk_id,
        document_id=passage.document_id,
        doc_key=passage.doc_key,
        title=passage.title,
        version=passage.version,
        effective_from=passage.effective_from,
        section_path=list(passage.section_path),
        section=passage.section,
        quote=quote,
        fragment=passage.text,
    )


def clean_quote(quote: str) -> str:
    """Cytat do wyświetlenia: odkodowane encje, jednolite białe znaki, bez otaczających cudzysłowów."""
    return collapse_ws(html.unescape(quote)).strip(" \"'„”«»")


def verify_quote(quote: str, source: str, min_chars: int = MIN_QUOTE_CHARS) -> bool:
    """Czy cytat jest dosłownym podciągiem źródła (po normalizacji białych znaków).

    Wielokropek dzieli cytat na części, które muszą wystąpić w źródle po kolei.
    """
    text = normalize_for_match(clean_quote(quote))
    if len(text) < min_chars:
        return False
    src = normalize_for_match(source)
    segments = [s.strip(" \"'") for s in re.split(r"\s*(?:\.\.\.|…)\s*", text)]
    segments = [s for s in segments if s]
    if not segments or (len(segments) > 1 and any(len(s) < min_chars for s in segments)):
        return False
    position = 0
    for segment in segments:
        found = src.find(segment, position)
        if found < 0 and segment.endswith("."):
            found = src.find(segment[:-1], position)
        if found < 0:
            return False
        position = found + len(segment)
    return True


def ai_answer(
    llm: StructuredLLM,
    question: str,
    hits: Sequence[Hit],
    today: date,
    rule_categories: Sequence[str] = (),
) -> Draft:
    """Odpowiedź modelu z weryfikacją cytatów. Rzuca LLMUnavailable, gdy model zawiedzie.

    Bez ani jednego potwierdzonego cytatu odpowiedź modelu nie jest pokazywana —
    zwracamy „brak podstawy” z przekierowaniem do HR.
    """
    ids = [f"F{i + 1}" for i in range(len(hits))]
    by_id = {fid: hit.passage for fid, hit in zip(ids, hits, strict=True)}
    prompt = build_answer_prompt(question, hits, ids, today, rule_categories)
    result = llm.generate(system=SYSTEM_ANSWER, prompt=prompt, schema=AIAnswer)

    citations: list[Citation] = []
    seen: set[tuple[str, str]] = set()
    rejected = 0
    for item in result.citations:
        passage = by_id.get(item.fragment_id.strip().upper())
        if passage is None or not verify_quote(item.quote, passage.text):
            rejected += 1
            continue
        quote = clean_quote(item.quote)
        if (passage.chunk_id, quote) not in seen:
            seen.add((passage.chunk_id, quote))
            citations.append(citation_from(passage, quote))

    category = None if result.sensitive_category == "brak" else result.sensitive_category
    notices = [messages.REJECTED_CITATIONS.format(n=rejected)] if rejected else []
    if not citations:
        reason = "Brak potwierdzonego cytatu z dokumentów firmy."
        if rejected:
            reason += f" Odrzucono {rejected} cytat(y) niezgodne z treścią fragmentów."
        return Draft(
            status="no_basis", source="ai", answer=messages.NO_BASIS, citations=[], confidence="niska",
            needs_hr=True, reason=reason, rejected_citations=rejected, model_category=category,
            model_injection=result.injection_detected, notices=notices,
        )
    confidence: Confidence = result.confidence
    if rejected and confidence == "wysoka":
        confidence = "średnia"  # część cytatów się nie potwierdziła — nie ufamy w pełni
    return Draft(
        status="answered",
        source="ai",
        answer=result.answer.strip()[:MAX_ANSWER_CHARS],
        citations=citations,
        confidence=confidence,
        needs_hr=result.needs_hr or confidence == "niska",
        reason=result.reason.strip()[:500],
        rejected_citations=rejected,
        model_category=category,
        model_injection=result.injection_detected,
        notices=notices,
    )


_ABBREVIATIONS = {"np", "tzw", "ust", "art", "pkt", "r", "tj", "zl", "godz", "ok", "nr", "wewn", "tel", "ul",
                  "itd", "itp", "cz", "m.in", "in", "lit", "dz", "poz"}
_SENTENCE_END_RE = re.compile(r"[.!?;](?=\s+[A-ZĄĆĘŁŃÓŚŹŻ„\"(0-9])")


def split_units(text: str) -> list[tuple[int, int]]:
    """Dzieli tekst na jednostki (wiersze/punkty, a w nich zdania) jako zakresy znaków."""
    units: list[tuple[int, int]] = []
    for line in re.finditer(r"[^\n]+", text):
        base, content = line.start(), line.group(0)
        start = 0
        for match in _SENTENCE_END_RE.finditer(content):
            before = re.search(r"(\S+)$", content[: match.start()])
            word = fold(before.group(1)) if before else ""
            if not word or word.isdigit() or len(word) <= 1 or word.rstrip(".") in _ABBREVIATIONS:
                continue
            units.append((base + start, base + match.end()))
            start = match.end()
        units.append((base + start, base + len(content)))
    cleaned: list[tuple[int, int]] = []
    for s, e in units:
        segment = text[s:e]
        lead = len(segment) - len(segment.lstrip())
        trail = len(segment) - len(segment.rstrip())
        if e - trail > s + lead:
            cleaned.append((s + lead, e - trail))
    return cleaned


def best_span(text: str, question: str, engine: SearchEngine, min_chars: int = 80) -> str:
    """Najtrafniejszy dosłowny wycinek fragmentu (zdanie/punkt, ewentualnie z następnym)."""
    units = split_units(text)
    if not units:
        return text.strip()
    concepts = query_concepts(question)
    # Pytanie „ile…?”/„kiedy…?” szuka liczby lub terminu — premiujemy jednostki z cyframi.
    wants_number = bool(set(query_hints(question)) & {"=ilosc", "=termin"})
    bonus = max((engine.concept_weight(c) for c in concepts), default=1.0)
    best_index, best_score = 0, -1.0
    for i, (s, e) in enumerate(units):
        unit = text[s:e]
        present = set(analyze(unit))
        score = sum(engine.concept_weight(c) for c in concepts if c & present)
        if wants_number and re.search(r"\d", unit[3:]):  # pomijamy numer punktu „1.”
            score += bonus
        if score > best_score:
            best_index, best_score = i, score
    start, end = units[best_index]
    if end - start < min_chars and best_index + 1 < len(units):
        end = units[best_index + 1][1]
    return text[start:end]


def confidence_from(hit: Hit, min_score: float) -> Confidence:
    if hit.coverage >= 0.9 and hit.score >= 2 * min_score:
        return "wysoka"
    if hit.coverage >= 0.6:
        return "średnia"
    return "niska"


def offline_answer(
    question: str,
    hits: Sequence[Hit],
    engine: SearchEngine,
    min_score: float,
    min_coverage: float,
    legal: bool = False,
) -> Draft:
    """Odpowiedź ekstrakcyjna: najlepsze fragmenty w formie „Według dokumentu …, § …: «…»”."""
    relevant = [h for h in hits if is_relevant(h, min_score, min_coverage)]
    if not relevant:
        best = hits[0] if hits else None
        reason = "Żaden fragment dokumentów nie przekroczył progu trafności."
        if best:
            reason += f" Najlepsze dopasowanie: {best.passage.path_label} (pokrycie {best.coverage:.0%})."
        return Draft(
            status="no_basis", source="offline", answer=messages.LEGAL_NO_BASIS if legal else messages.NO_BASIS,
            citations=[], confidence="niska", needs_hr=True, reason=reason,
        )
    top = relevant[0]
    chosen = [top] + [h for h in relevant[1:] if h.score >= 0.6 * top.score][: MAX_EXTRACTS - 1]
    citations: list[Citation] = []
    lines: list[str] = []
    for hit in chosen:
        quote = collapse_ws(best_span(hit.passage.text, question, engine))
        citations.append(citation_from(hit.passage, quote))
        lines.append(f"Według dokumentu „{hit.passage.title}”, {hit.passage.section}: «{quote}»")
    confidence = confidence_from(top, min_score)
    return Draft(
        status="answered",
        source="offline",
        answer="\n\n".join(lines),
        citations=citations,
        confidence=confidence,
        needs_hr=confidence == "niska",
        reason=(
            ("Wyciąg z najlepiej dopasowanego fragmentu" if len(chosen) == 1
             else f"Wyciąg z {len(chosen)} najlepiej dopasowanych fragmentów")
            + f" (pokrycie pojęć z pytania: {top.coverage:.0%})."
        ),
        is_extract=True,
        notices=[messages.EXTRACT_NOTE],
    )
