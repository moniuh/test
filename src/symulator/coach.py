"""Coach na żywo: analiza każdego pytania trenującego (reguły + opcjonalnie klasyfikacja AI)."""
from __future__ import annotations

import logging
from typing import Literal

from pydantic import BaseModel, Field

from .detector import IllegalFlag, detect_illegal, detect_injection, flag_for_category
from .llm import LLMUnavailable, StructuredLLM
from .models import OPEN_TYPES, QuestionType, Source
from .privacy import mask_pii
from .prompts import COACH_SYSTEM, coach_prompt
from .question_types import TYPE_TIPS, classify_question, type_label
from .scenarios import Trap
from .text import count_keyword_hits, tokens

log = logging.getLogger(__name__)

ILLEGAL_TIP = "To pytanie jest niedozwolone — nie zadawaj go. Skorzystaj z dozwolonego sformułowania poniżej."


class CoachAIVerdict(BaseModel):
    """Schemat odpowiedzi modelu — celowo prosty, bez ograniczeń pól."""

    question_type: QuestionType
    illegal: bool
    category: Literal[
        "brak", "rodzina", "wiek", "zdrowie", "religia", "narodowosc", "zwiazki_zawodowe",
        "orientacja", "poglady", "wynagrodzenie_historia", "inne",
    ]
    explanation: str
    suggestion: str
    tip: str


class CoachAnalysis(BaseModel):
    question_type: str
    question_type_label: str
    open_form: bool
    sub_questions: int
    star_cues: list[str]
    type_source: Source = "offline"
    tip: str
    flags: list[IllegalFlag] = Field(default_factory=list)
    injection_suspected: bool = False
    source: Source = "offline"
    ai_error: str | None = None

    @property
    def illegal(self) -> bool:
        return bool(self.flags)


def trap_followup_flags(question: str, active_traps: list[Trap]) -> list[IllegalFlag]:
    """Dopytywanie o wątek prywatny, o którym kandydat sam wspomniał (np. „a z kolanem już dobrze?”)."""
    toks = tokens(question)
    flags = []
    for trap in active_traps:
        if trap.followup_keywords and count_keyword_hits(toks, trap.followup_keywords):
            flag = flag_for_category(trap.category, source="offline")
            flag.trap_id = trap.id
            flag.matched = "dopytanie o wątek prywatny wspomniany przez kandydata"
            flags.append(flag)
    return flags


def rule_analysis(question: str, *, has_history: bool, active_traps: list[Trap] | None = None) -> CoachAnalysis:
    verdict = classify_question(question, has_history=has_history)
    flags = detect_illegal(question)
    for flag in trap_followup_flags(question, active_traps or []):
        existing = next((f for f in flags if f.category == flag.category), None)
        if existing:
            existing.trap_id = flag.trap_id
        else:
            flags.append(flag)
    return CoachAnalysis(
        question_type=verdict.question_type,
        question_type_label=type_label(verdict.question_type),
        open_form=verdict.open_form,
        sub_questions=verdict.sub_questions,
        star_cues=verdict.star_cues,
        tip=ILLEGAL_TIP if flags else TYPE_TIPS[verdict.question_type],
        flags=flags,
        injection_suspected=detect_injection(question),
    )


def merge_ai_verdict(analysis: CoachAnalysis, verdict: CoachAIVerdict) -> CoachAnalysis:
    """Łączy reguły z werdyktem AI. Flaga z reguł zostaje zawsze (ostrożność); AI może dodać nową."""
    merged = analysis.model_copy(deep=True)
    merged.question_type = verdict.question_type
    merged.question_type_label = type_label(verdict.question_type)
    merged.open_form = verdict.question_type in OPEN_TYPES or (verdict.question_type == "wielokrotne" and analysis.open_form)
    merged.type_source = "ai"
    merged.tip = verdict.tip.strip()[:300] or TYPE_TIPS[verdict.question_type]
    if verdict.illegal:
        category = verdict.category if verdict.category != "brak" else "inne"
        if category not in {f.category for f in merged.flags}:
            merged.flags.append(
                flag_for_category(category, source="ai", explanation=verdict.explanation, suggestion=verdict.suggestion)
            )
    if merged.flags:
        merged.tip = ILLEGAL_TIP
    merged.source = "ai"
    return merged


def refine_with_ai(analysis: CoachAnalysis, *, question: str, last_answer: str, llm: StructuredLLM | None) -> CoachAnalysis:
    if llm is None:
        return analysis
    try:
        verdict = llm.generate(
            system=COACH_SYSTEM,
            prompt=coach_prompt(question=mask_pii(question), last_answer=mask_pii(last_answer)),
            schema=CoachAIVerdict,
            effort="low",
        )
    except LLMUnavailable as exc:
        log.warning("Coach AI niedostępny, zostaję przy regułach: %s", exc)
        return analysis.model_copy(update={"ai_error": str(exc)})
    return merge_ai_verdict(analysis, verdict)


def analyze_question(
    question: str,
    *,
    last_answer: str = "",
    has_history: bool = False,
    active_traps: list[Trap] | None = None,
    llm: StructuredLLM | None = None,
) -> CoachAnalysis:
    """Pełna analiza pytania: reguły zawsze, AI jeśli dostępne."""
    analysis = rule_analysis(question, has_history=has_history, active_traps=active_traps)
    return refine_with_ai(analysis, question=question, last_answer=last_answer, llm=llm)
