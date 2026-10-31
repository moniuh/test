"""Modele domenowe (Pydantic). Kody w kodzie są angielskie, etykiety dla ludzi — polskie."""
from __future__ import annotations

import re
from typing import Literal

from pydantic import BaseModel, Field, field_validator, model_validator

Verdict = Literal["meets", "partial", "fails", "no_data"]
Recommendation = Literal["invite", "consider", "mismatch"]
RequirementKind = Literal["must", "nice"]
Source = Literal["ai", "offline"]
CandidateStatus = Literal["new", "invited", "rejected", "on_hold"]
DecisionStatus = Literal["invited", "rejected", "on_hold"]
EvaluationMode = Literal["auto", "offline"]

VERDICT_LABELS: dict[str, str] = {
    "meets": "spełnia",
    "partial": "częściowo",
    "fails": "nie spełnia",
    "no_data": "brak danych",
}
RECOMMENDATION_LABELS: dict[str, str] = {
    "invite": "zaproś",
    "consider": "do rozważenia",
    "mismatch": "niedopasowanie",
}
KIND_LABELS: dict[str, str] = {"must": "obowiązkowe", "nice": "mile widziane"}
STATUS_LABELS: dict[str, str] = {
    "new": "nowy",
    "invited": "zaproszony",
    "rejected": "odrzucony",
    "on_hold": "wstrzymany",
}

MIN_REJECTION_REASON = 10


def _clean_terms(values: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for value in values:
        term = " ".join(str(value).split())
        if term and term.lower() not in seen:
            seen.add(term.lower())
            out.append(term)
    return out


def slugify(text: str) -> str:
    table = str.maketrans("ąćęłńóśźżĄĆĘŁŃÓŚŹŻ", "acelnoszzACELNOSZZ")
    slug = re.sub(r"[^a-z0-9]+", "-", text.translate(table).lower()).strip("-")
    return slug[:40] or "wymaganie"


class Requirement(BaseModel):
    """Jedno wymaganie z profilu stanowiska."""

    id: str = ""
    name: str = Field(min_length=2, max_length=200)
    kind: RequirementKind = "must"
    weight: int = Field(default=3, ge=1, le=5)
    keywords: list[str] = Field(min_length=1)
    synonyms: list[str] = Field(default_factory=list)
    partial_keywords: list[str] = Field(default_factory=list)
    min_years: float | None = Field(default=None, ge=0, le=50)

    @field_validator("keywords", "synonyms", "partial_keywords")
    @classmethod
    def _terms(cls, value: list[str]) -> list[str]:
        return _clean_terms(value)

    @model_validator(mode="after")
    def _non_empty_keywords(self) -> "Requirement":
        if not self.keywords:
            raise ValueError("Wymaganie musi mieć co najmniej jedno słowo kluczowe (potrzebne w trybie offline).")
        return self


class JobCreate(BaseModel):
    title: str = Field(min_length=3, max_length=200)
    description: str = Field(default="", max_length=5000)
    requirements: list[Requirement] = Field(min_length=1, max_length=30)

    @model_validator(mode="after")
    def _assign_ids(self) -> "JobCreate":
        used: set[str] = set()
        for req in self.requirements:
            base = slugify(req.id or req.name)
            rid, n = base, 2
            while rid in used:
                rid, n = f"{base}-{n}", n + 1
            used.add(rid)
            req.id = rid
        return self


class Job(JobCreate):
    id: int
    created_at: str
    created_by: str


class InjectionFinding(BaseModel):
    rule: str
    label: str
    fragment: str
    start: int
    end: int


class Span(BaseModel):
    start: int
    end: int


class InjectionReport(BaseModel):
    flagged: bool = False
    findings: list[InjectionFinding] = Field(default_factory=list)
    omitted: list[Span] = Field(default_factory=list)
    ai_suspected: bool = False
    ai_note: str = ""


class RequirementResult(BaseModel):
    requirement_id: str
    name: str
    kind: RequirementKind
    weight: int
    verdict: Verdict
    evidence: str | None = None
    evidence_start: int | None = None
    evidence_end: int | None = None
    matched_terms: list[str] = Field(default_factory=list)
    model_verdict: Verdict | None = None
    rationale: str = ""
    note: str = ""
    points: float = 0.0


class Evaluation(BaseModel):
    source: Source
    score: float
    recommendation: Recommendation
    gates: list[str] = Field(default_factory=list)
    results: list[RequirementResult]
    injection: InjectionReport
    fallback_reason: str | None = None
    weighted_points: float = 0.0
    max_points: float = 0.0
    input_hash: str = ""
    created_at: str = ""


class AnonymizationSummary(BaseModel):
    counts: dict[str, int] = Field(default_factory=dict)
    gender_neutralized: int = 0


class DecisionCreate(BaseModel):
    status: DecisionStatus
    reason: str = Field(default="", max_length=2000)

    @model_validator(mode="after")
    def _rejection_needs_reason(self) -> "DecisionCreate":
        self.reason = self.reason.strip()
        if self.status == "rejected" and len(self.reason) < MIN_REJECTION_REASON:
            raise ValueError(
                f"Odrzucenie wymaga uzasadnienia (min. {MIN_REJECTION_REASON} znaków) — "
                "decyzję podejmuje i uzasadnia człowiek."
            )
        return self


class Decision(BaseModel):
    id: int
    candidate_id: int
    status: DecisionStatus
    reason: str
    actor: str
    ai_recommendation: Recommendation | None
    is_override: bool
    viewed_before: bool
    created_at: str


class RevealRequest(BaseModel):
    reason: str = Field(min_length=5, max_length=500)


Gender = Literal["female", "male", "other", "undisclosed"]
AgeGroup = Literal["under_30", "30_49", "50_plus", "undisclosed"]
Disability = Literal["yes", "no", "undisclosed"]

DEMOGRAPHIC_LABELS: dict[str, dict[str, str]] = {
    "gender": {"female": "kobieta", "male": "mężczyzna", "other": "inna", "undisclosed": "nie podano"},
    "age_group": {"under_30": "poniżej 30", "30_49": "30–49", "50_plus": "50 i więcej", "undisclosed": "nie podano"},
    "disability": {"yes": "tak", "no": "nie", "undisclosed": "nie podano"},
}
DIMENSION_LABELS = {"gender": "Płeć", "age_group": "Wiek", "disability": "Niepełnosprawność"}


class Demographics(BaseModel):
    """Dobrowolna ankieta kandydata — przechowywana osobno, tylko do raportu stronniczości."""

    consent: bool
    gender: Gender = "undisclosed"
    age_group: AgeGroup = "undisclosed"
    disability: Disability = "undisclosed"

    @model_validator(mode="after")
    def _needs_consent(self) -> "Demographics":
        if not self.consent:
            raise ValueError("Ankieta jest dobrowolna — zapis wymaga wyraźnej zgody kandydata (consent=true).")
        return self
