"""Wspólne fixtures. Testy działają bez sieci: AI zastępuje fake `StructuredLLM`."""
from __future__ import annotations

import os
import tempfile
from collections.abc import Callable
from pathlib import Path

# Przed importem aplikacji: tryb offline i baza w katalogu tymczasowym (moduł api tworzy `app` przy imporcie).
os.environ["HR_AI_OFFLINE"] = "1"
os.environ.setdefault("SYMULATOR_DB", str(Path(tempfile.mkdtemp()) / "test-import.db"))

import pytest
from fastapi.testclient import TestClient

from symulator.coach import rule_analysis
from symulator.llm import LLMUnavailable
from symulator.scenarios import Scenario, get_scenario
from symulator.transcript import Turn, mentioned_traps


class FakeLLM:
    """Fake modelu: odpowiedź wybierana po typie schematu; wyjątek = symulacja awarii."""

    name = "fake"

    def __init__(self, responses: dict | None = None) -> None:
        self.responses = responses or {}
        self.calls: list[dict] = []

    def generate(self, *, system: str, prompt: str, schema: type, effort: str = "medium"):
        self.calls.append({"system": system, "prompt": prompt, "schema": schema, "effort": effort})
        response = self.responses.get(schema)
        if response is None:
            raise LLMUnavailable("Fake: brak odpowiedzi dla schematu")
        if isinstance(response, Exception):
            raise response
        if callable(response):
            return response(system=system, prompt=prompt)
        return response


@pytest.fixture
def fake_llm_cls():
    return FakeLLM


@pytest.fixture
def cs_scenario() -> Scenario:
    return get_scenario("obsluga-klienta")


@pytest.fixture
def dev_scenario() -> Scenario:
    return get_scenario("junior-developer")


@pytest.fixture
def warehouse_scenario() -> Scenario:
    return get_scenario("kierownik-zmiany")


def make_turn(
    index: int,
    question: str,
    answer: str = "Odpowiedź.",
    revealed: list[str] | None = None,
    traps: list[str] | None = None,
    has_history: bool | None = None,
) -> Turn:
    return Turn(
        index=index,
        question=question,
        answer=answer,
        emotion="neutralna",
        candidate_source="offline",
        revealed_fact_ids=revealed or [],
        trap_ids=traps or [],
        coach=rule_analysis(question, has_history=index > 0 if has_history is None else has_history),
    )


@pytest.fixture
def turn_factory() -> Callable[..., Turn]:
    return make_turn


def play(scenario: Scenario, questions: list[str], llm=None) -> list[Turn]:
    """Rozgrywa rozmowę offline (albo z fake LLM) i zwraca tury."""
    from symulator.candidate import candidate_reply
    from symulator.coach import analyze_question

    turns: list[Turn] = []
    for q in questions:
        active = [t for tid in mentioned_traps(turns) if (t := scenario.trap(tid))]
        analysis = analyze_question(q, has_history=bool(turns), active_traps=active, llm=llm)
        reply = candidate_reply(scenario, q, analysis, turns, llm)
        turns.append(
            Turn(
                index=len(turns),
                question=q,
                answer=reply.answer,
                emotion=reply.emotion,
                candidate_source=reply.source,
                revealed_fact_ids=reply.revealed_fact_ids,
                trap_ids=reply.trap_ids,
                coach=analysis,
                candidate_note=reply.note,
            )
        )
    return turns


@pytest.fixture
def player() -> Callable[..., list[Turn]]:
    return play


@pytest.fixture
def client(tmp_path) -> TestClient:
    from symulator.api import create_app

    app = create_app(db_path=tmp_path / "test.db", llm_provider=lambda: None)
    return TestClient(app)


@pytest.fixture
def client_factory(tmp_path) -> Callable[..., TestClient]:
    def _make(llm=None) -> TestClient:
        from symulator.api import create_app

        app = create_app(db_path=tmp_path / "test-ai.db", llm_provider=lambda: llm)
        return TestClient(app)

    return _make
