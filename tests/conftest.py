"""Wspólne fikstury: tryb offline wymuszony i atrapa modelu (bez sieci)."""
from __future__ import annotations

import os
from pathlib import Path

os.environ["HR_AI_OFFLINE"] = "1"

import pytest  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"


class FakeLLM:
    """Atrapa `StructuredLLM`: zwraca przygotowaną odpowiedź dla schematu albo rzuca wyjątek."""

    name = "fake"

    def __init__(self, responses: dict | None = None, error: Exception | None = None) -> None:
        self.responses = responses or {}
        self.error = error
        self.calls: list[dict] = []

    def generate(self, *, system: str, prompt: str, schema, effort: str = "medium"):
        self.calls.append({"system": system, "prompt": prompt, "schema": schema, "effort": effort})
        if self.error is not None:
            raise self.error
        response = self.responses[schema]
        return response(prompt) if callable(response) else response


@pytest.fixture
def example_text():
    def _load(name: str) -> str:
        return (EXAMPLES / name).read_text(encoding="utf-8")

    return _load
