"""Modele wyników (Pydantic) wspólne dla logiki domenowej i API."""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel

Status = Literal["answered", "no_basis", "sensitive"]
Source = Literal["ai", "offline"]
Confidence = Literal["wysoka", "średnia", "niska"]


class Citation(BaseModel):
    chunk_id: str
    document_id: int
    doc_key: str
    title: str
    version: str
    effective_from: str
    section_path: list[str]
    section: str
    quote: str
    fragment: str


class Answer(BaseModel):
    interaction_id: int
    session_token: str
    status: Status
    source: Source
    answer: str
    citations: list[Citation]
    confidence: Confidence
    needs_hr: bool
    reason: str
    is_extract: bool = False
    categories: list[str] = []
    category_labels: list[str] = []
    injection_suspected: bool = False
    rejected_citations: int = 0
    notices: list[str] = []
    hr_contact: str = ""


class Escalation(BaseModel):
    id: int
    interaction_id: int | None
    created_at: str
    question: str
    note: str | None
    contact: str | None
    context: dict
    status: Literal["nowe", "odpowiedziane", "zamknięte"]
    hr_reply: str | None
    replied_at: str | None
    closed_at: str | None


class GapGroup(BaseModel):
    keyword: str
    count: int
    no_basis: int
    low_confidence: int
    thumbs_down: int
    escalated: int
    examples: list[str]
    suggestion: str
    suggested_document: str | None


class GapsReport(BaseModel):
    days: int
    total_questions: int
    gap_questions: int
    groups: list[GapGroup]


class Stats(BaseModel):
    total_questions: int
    by_status: dict[str, int]
    by_source: dict[str, int]
    by_confidence: dict[str, int]
    feedback: dict[str, int]
    escalations: dict[str, int]
    top_documents: list[dict]
    per_day: list[dict]
    answered_rate: float
    documents_current: int
    documents_total: int
