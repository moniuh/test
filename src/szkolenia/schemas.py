"""Modele wejściowe API (Pydantic v2) z komunikatami walidacji po polsku."""
from __future__ import annotations

import re
from datetime import date
from typing import Literal

from pydantic import BaseModel, Field, field_validator

_EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


def _clean(value: str | None) -> str | None:
    if value is None:
        return None
    value = " ".join(value.split())
    return value or None


class EmployeeIn(BaseModel):
    full_name: str = Field(min_length=3, max_length=120)
    email: str = Field(max_length=200)
    department: str = Field(min_length=2, max_length=80)
    position: str = Field(min_length=2, max_length=120)
    hire_date: date
    manager_id: int | None = None

    @field_validator("email")
    @classmethod
    def _email(cls, v: str) -> str:
        v = v.strip().lower()
        if not _EMAIL.match(v):
            raise ValueError("Nieprawidłowy adres e-mail.")
        return v


class EmployeeUpdate(BaseModel):
    full_name: str | None = Field(default=None, min_length=3, max_length=120)
    email: str | None = Field(default=None, max_length=200)
    department: str | None = Field(default=None, min_length=2, max_length=80)
    position: str | None = Field(default=None, min_length=2, max_length=120)
    hire_date: date | None = None
    manager_id: int | None = None
    active: bool | None = None

    @field_validator("email")
    @classmethod
    def _email(cls, v: str | None) -> str | None:
        if v is None:
            return v
        v = v.strip().lower()
        if not _EMAIL.match(v):
            raise ValueError("Nieprawidłowy adres e-mail.")
        return v


class TrainingIn(BaseModel):
    title: str = Field(min_length=3, max_length=200)
    category: str = Field(min_length=2, max_length=60)
    validity_months: int | None = Field(default=None, ge=1, le=240)
    pass_threshold: int = Field(default=80, ge=1, le=100)
    mode: Literal["elearning", "classroom"]
    material: str = Field(default="", max_length=50_000)
    questions_per_test: int = Field(default=10, ge=1, le=50)


class TrainingUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=3, max_length=200)
    category: str | None = Field(default=None, min_length=2, max_length=60)
    validity_months: int | None = Field(default=None, ge=1, le=240)
    pass_threshold: int | None = Field(default=None, ge=1, le=100)
    mode: Literal["elearning", "classroom"] | None = None
    material: str | None = Field(default=None, max_length=50_000)
    questions_per_test: int | None = Field(default=None, ge=1, le=50)


class RuleIn(BaseModel):
    training_id: int
    department: str | None = Field(default=None, max_length=80)
    position: str | None = Field(default=None, max_length=120)
    due_days: int = Field(default=30, ge=0, le=730)

    @field_validator("department", "position")
    @classmethod
    def _clean_criteria(cls, v: str | None) -> str | None:
        return _clean(v)


class AssignmentIn(BaseModel):
    employee_id: int
    training_id: int
    due_date: date


class GenerateIn(BaseModel):
    count: int = Field(default=5, ge=1, le=20)


class QuestionIn(BaseModel):
    text: str = Field(min_length=5, max_length=600)
    options: list[str] = Field(min_length=4, max_length=4)
    correct_index: int = Field(ge=0, le=3)
    explanation: str = Field(default="", max_length=1000)
    source_quote: str = Field(default="", max_length=800)


class ReviewIn(BaseModel):
    reviewer: str = Field(default="HR", max_length=120)


class AnswerIn(BaseModel):
    question_id: int
    option_index: int = Field(ge=0, le=3)


class SubmitIn(BaseModel):
    answers: list[AnswerIn] = Field(max_length=100)

    @field_validator("answers")
    @classmethod
    def _unique(cls, v: list[AnswerIn]) -> list[AnswerIn]:
        ids = [a.question_id for a in v]
        if len(ids) != len(set(ids)):
            raise ValueError("Każde pytanie może mieć tylko jedną odpowiedź.")
        return v


class ConfirmIn(BaseModel):
    trainer: str = Field(min_length=3, max_length=120)
    session_date: date


class ExternalCompletionIn(BaseModel):
    completed_on: date
    note: str = Field(default="", max_length=300)


# --- Tłumaczenie błędów walidacji Pydantic na polski ------------------------------------------

_MESSAGES = {
    "missing": "To pole jest wymagane.",
    "string_too_short": "Tekst jest za krótki (minimalna długość: {min_length}).",
    "string_too_long": "Tekst jest za długi (maksymalna długość: {max_length}).",
    "string_type": "Wymagany tekst.",
    "int_parsing": "Wymagana liczba całkowita.",
    "int_type": "Wymagana liczba całkowita.",
    "int_from_float": "Wymagana liczba całkowita.",
    "bool_parsing": "Wymagana wartość tak/nie.",
    "date_from_datetime_parsing": "Nieprawidłowa data (format RRRR-MM-DD).",
    "date_parsing": "Nieprawidłowa data (format RRRR-MM-DD).",
    "date_type": "Nieprawidłowa data (format RRRR-MM-DD).",
    "greater_than_equal": "Wartość musi być nie mniejsza niż {ge}.",
    "less_than_equal": "Wartość musi być nie większa niż {le}.",
    "literal_error": "Niedozwolona wartość. Dozwolone: {expected}.",
    "too_short": "Za mało elementów (wymagane: {min_length}).",
    "too_long": "Za dużo elementów (maks. {max_length}).",
    "list_type": "Wymagana lista.",
    "json_invalid": "Nieprawidłowy format JSON.",
    "model_attributes_type": "Nieprawidłowa struktura danych.",
    "dict_type": "Nieprawidłowa struktura danych.",
}


def polish_errors(errors: list[dict]) -> list[dict]:
    out = []
    for err in errors:
        loc = [str(p) for p in err.get("loc", ()) if p not in ("body", "query", "path")]
        etype = err.get("type", "")
        ctx = err.get("ctx") or {}
        if etype == "value_error":
            message = str(ctx.get("error") or err.get("msg", "")).removeprefix("Value error, ")
        elif etype in _MESSAGES:
            try:
                message = _MESSAGES[etype].format(**ctx)
            except (KeyError, IndexError):
                message = _MESSAGES[etype]
        else:
            message = "Nieprawidłowa wartość."
        out.append({"field": ".".join(loc) or None, "message": message})
    return out
