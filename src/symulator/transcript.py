"""Tura rozmowy i pomocnicze funkcje liczone z historii (ujawnione fakty, bieżący temat, transkrypt)."""
from __future__ import annotations

from pydantic import BaseModel, Field

from .coach import CoachAnalysis
from .models import Source
from .privacy import mask_pii
from .scenarios import Scenario

TRAINEE_LABEL = "Rekruter"
CANDIDATE_LABEL = "Kandydat"


class Turn(BaseModel):
    index: int
    question: str
    answer: str
    emotion: str
    candidate_source: Source
    revealed_fact_ids: list[str] = Field(default_factory=list)
    trap_ids: list[str] = Field(default_factory=list)
    coach: CoachAnalysis
    created_at: str = ""
    candidate_note: str | None = None


def revealed_ids(turns: list[Turn]) -> set[str]:
    return {fid for t in turns for fid in t.revealed_fact_ids}


def mentioned_traps(turns: list[Turn]) -> dict[str, int]:
    """Id pułapki → indeks tury, w której kandydat o niej wspomniał."""
    found: dict[str, int] = {}
    for t in turns:
        for trap_id in t.trap_ids:
            found.setdefault(trap_id, t.index)
    return found


def current_topic(turns: list[Turn], scenario: Scenario) -> str | None:
    """Kompetencja, o której ostatnio była mowa (wg ostatnio ujawnionego faktu)."""
    for t in reversed(turns):
        for fid in reversed(t.revealed_fact_ids):
            fact = scenario.fact(fid)
            if fact:
                return fact.competency
    return None


def format_transcript(turns: list[Turn], *, mask: bool = True, numbered: bool = False) -> str:
    lines: list[str] = []
    for t in turns:
        q = mask_pii(t.question) if mask else t.question
        a = mask_pii(t.answer) if mask else t.answer
        prefix = f"[{t.index + 1}] " if numbered else ""
        lines.append(f"{prefix}{TRAINEE_LABEL}: {q}")
        lines.append(f"{prefix}{CANDIDATE_LABEL}: {a}")
    return "\n".join(lines)


def star_progress(turns: list[Turn], scenario: Scenario) -> list[dict]:
    """Postęp STAR per kompetencja na podstawie ujawnionych faktów (dla panelu coacha)."""
    revealed = revealed_ids(turns)
    progress = []
    for comp in scenario.competencies:
        elements = {el: False for el in ("S", "T", "A", "R")}
        for fact in scenario.facts_for(comp.id):
            if fact.id in revealed:
                elements[fact.star] = True
        progress.append({"competency_id": comp.id, "name": comp.name, "elements": elements})
    return progress
