"""Wspólne fixtures. Testy działają bez sieci: AI zastępuje FakeLLM, bazy są w katalogu tymczasowym."""
from __future__ import annotations

import os
import sys
import tempfile
from datetime import date
from pathlib import Path
from typing import Any, Callable

# Ustawione przed importem `preselekcja.api`, które tworzy aplikację (i pliki baz) na poziomie modułu.
_TMP = tempfile.mkdtemp(prefix="preselekcja-test-")
os.environ["HR_AI_OFFLINE"] = "1"
os.environ.setdefault("PRESELEKCJA_DB_PATH", os.path.join(_TMP, "app.db"))
os.environ.setdefault("PRESELEKCJA_DEMOGRAPHICS_DB_PATH", os.path.join(_TMP, "demo.db"))
sys.path.insert(0, str(Path(__file__).parent))

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from preselekcja.llm import LLMUnavailable  # noqa: E402
from preselekcja.models import JobCreate  # noqa: E402

EXAMPLES = Path(__file__).resolve().parents[1] / "examples"
TODAY = date(2026, 10, 31)


class FakeLLM:
    """Atrapa StructuredLLM: zwraca wynik funkcji `responder` i zapamiętuje prompty."""

    name = "fake"

    def __init__(self, responder: Callable[[str, type], Any] | None = None, error: str | None = None) -> None:
        self.responder = responder
        self.error = error
        self.calls: list[dict[str, Any]] = []

    def generate(self, *, system: str, prompt: str, schema: type, effort: str = "medium"):
        self.calls.append({"system": system, "prompt": prompt, "schema": schema, "effort": effort})
        if self.error:
            raise LLMUnavailable(self.error)
        return self.responder(prompt, schema)


@pytest.fixture
def job_payload() -> dict[str, Any]:
    return {
        "title": "Specjalista ds. kadr i płac",
        "description": "Oferta testowa",
        "requirements": [
            {"name": "Kadry i płace", "kind": "must", "weight": 5, "keywords": ["kadry i płace"],
             "synonyms": ["naliczanie wynagrodzeń"], "min_years": 2},
            {"name": "Prawo pracy", "kind": "must", "weight": 3, "keywords": ["prawo pracy", "Kodeks pracy"]},
            {"name": "Excel", "kind": "nice", "weight": 2, "keywords": ["Excel"]},
        ],
    }


@pytest.fixture
def job(job_payload):
    from preselekcja.models import Job

    data = JobCreate.model_validate(job_payload)
    return Job(id=1, created_at="2026-10-31T00:00:00+00:00", created_by="test", **data.model_dump())


@pytest.fixture
def make_client(tmp_path):
    from preselekcja.api import create_app

    def factory(llm: FakeLLM | None = None) -> TestClient:
        app = create_app(
            db_path=tmp_path / "app.db",
            demographics_path=tmp_path / "demo.db",
            llm_provider=(lambda: llm),
            examples_dir=EXAMPLES,
        )
        app.state.service.today = lambda: TODAY
        return TestClient(app)

    return factory


@pytest.fixture
def client(make_client) -> TestClient:
    return make_client(None)
