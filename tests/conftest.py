"""Wspólne fikstury. Zmienne środowiskowe ustawiamy przed importem `szkolenia.api`
(moduł tworzy `app` przy imporcie) — w testach baza w pamięci, bez danych demo i bez AI."""
from __future__ import annotations

import os

os.environ.setdefault("SZKOLENIA_DB", ":memory:")
os.environ.setdefault("SZKOLENIA_SEED", "0")
os.environ["HR_AI_OFFLINE"] = "1"

from datetime import date  # noqa: E402

import pytest  # noqa: E402

from szkolenia.db import Store  # noqa: E402
from szkolenia.llm import LLMUnavailable  # noqa: E402
from szkolenia.service import TrainingService  # noqa: E402

T = date(2026, 3, 16)

MATERIAL = """Szkolenie testowe

Sygnalista to osoba, która zgłasza informację o naruszeniu prawa uzyskaną w kontekście związanym z pracą.
Działania odwetowe to działania wobec sygnalisty spowodowane zgłoszeniem, na przykład zwolnienie.
Zgłoszenie wewnętrzne to przekazanie informacji o naruszeniu w ramach procedury firmy.
Zgłoszenie zewnętrzne to przekazanie informacji o naruszeniu właściwemu organowi publicznemu.
Potwierdzenie przyjęcia zgłoszenia przekazujemy sygnaliście w terminie 7 dni od jego otrzymania.
Informację zwrotną sygnalista otrzymuje w terminie nie dłuższym niż 3 miesiące od potwierdzenia przyjęcia.
Ustawa o ochronie sygnalistów obowiązuje od 25 września 2024 r. i dotyczy także naszej firmy.
"""


class FakeLLM:
    """Atrapa `StructuredLLM`: zwraca przygotowaną odpowiedź albo rzuca wyjątek."""

    name = "fake"

    def __init__(self, response=None, error: Exception | None = None):
        self.response = response
        self.error = error
        self.calls: list[dict] = []

    def generate(self, *, system, prompt, schema, effort="medium"):
        self.calls.append({"system": system, "prompt": prompt, "schema": schema})
        if self.error:
            raise self.error
        return self.response


@pytest.fixture
def service() -> TrainingService:
    return TrainingService(Store(":memory:"), llm_factory=lambda: None)


def make_training(service: TrainingService, **overrides) -> dict:
    data = {"title": "Ochrona sygnalistów", "category": "Sygnaliści", "validity_months": 12,
            "pass_threshold": 80, "mode": "elearning", "material": MATERIAL, "questions_per_test": 10}
    data.update(overrides)
    return service.create_training(data)


def make_employee(service: TrainingService, today: date = T, **overrides) -> tuple[dict, list[dict]]:
    n = service.stats()["employees"] + 1
    data = {"full_name": f"Osoba Testowa {n}", "email": f"osoba{n}@firma-demo.example", "department": "Magazyn",
            "position": "Magazynier", "hire_date": today, "manager_id": None}
    data.update(overrides)
    return service.create_employee(data, today)


def add_rule(service: TrainingService, training_id: int, today: date = T, **overrides) -> dict:
    data = {"training_id": training_id, "department": None, "position": None, "due_days": 30}
    data.update(overrides)
    return service.create_rule(data, today)[0]


def approve_generated(service: TrainingService, training_id: int, count: int = 5) -> list[dict]:
    created = service.generate_questions(training_id, count)["created"]
    return [service.approve_question(q["id"]) for q in created]


__all__ = ["FakeLLM", "LLMUnavailable", "MATERIAL", "T", "add_rule", "approve_generated", "make_employee", "make_training"]
