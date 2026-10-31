import os

# Przed importem aplikacji: baza w pamięci, tryb offline, panel HR bez tokenu.
os.environ["HR_DB_PATH"] = ":memory:"
os.environ["HR_AI_OFFLINE"] = "1"
os.environ.pop("HR_PANEL_TOKEN", None)

import re  # noqa: E402
from datetime import datetime, timedelta, timezone  # noqa: E402

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from asystent.assistant import Assistant  # noqa: E402
from asystent.config import Settings, default_docs_dir  # noqa: E402
from asystent.store import Store  # noqa: E402

FIXED_NOW = datetime(2026, 10, 31, 10, 0, tzinfo=timezone.utc)
_FRAGMENT_RE = re.compile(r'<fragment id="(F\d+)"[^>]*>\n(.*?)\n</fragment>', re.DOTALL)


class Clock:
    def __init__(self, now: datetime = FIXED_NOW) -> None:
        self.now = now

    def __call__(self) -> datetime:
        return self.now

    def advance(self, **kwargs) -> None:
        self.now += timedelta(**kwargs)


class FakeLLM:
    """Atrapa StructuredLLM: zwraca przygotowaną odpowiedź albo rzuca błąd; zapisuje wywołania."""

    name = "fake"

    def __init__(self, response=None, error: Exception | None = None) -> None:
        self.response = response
        self.error = error
        self.calls: list[dict] = []

    def generate(self, *, system, prompt, schema, effort="medium"):
        self.calls.append({"system": system, "prompt": prompt, "schema": schema})
        if self.error:
            raise self.error
        data = self.response(prompt) if callable(self.response) else self.response
        return schema.model_validate(data)


def fragments(prompt: str) -> dict[str, str]:
    """Mapa id → treść fragmentu z promptu (do budowania odpowiedzi atrapy)."""
    return {fid: text for fid, text in _FRAGMENT_RE.findall(prompt)}


def fragment_with(prompt: str, needle: str) -> str:
    for fid, text in fragments(prompt).items():
        if needle in text:
            return fid
    raise AssertionError(f"Brak fragmentu z „{needle}” w promptcie")


def ai_response(**overrides) -> dict:
    base = {
        "answer": "Odpowiedź.",
        "citations": [],
        "confidence": "wysoka",
        "needs_hr": False,
        "reason": "Fragment odpowiada wprost.",
        "sensitive_category": "brak",
        "injection_detected": False,
    }
    base.update(overrides)
    return base


@pytest.fixture
def clock() -> Clock:
    return Clock()


@pytest.fixture
def make_assistant(clock):
    def factory(llm=None, settings: Settings | None = None, seed: bool = True) -> Assistant:
        assistant = Assistant(Store(":memory:"), settings or Settings(db_path=":memory:"),
                              llm_factory=lambda: llm, now=clock)
        if seed:
            assistant.kb.seed_directory(default_docs_dir())
        return assistant

    return factory


@pytest.fixture
def assistant(make_assistant) -> Assistant:
    return make_assistant()


@pytest.fixture
def client(make_assistant) -> TestClient:
    from asystent.api import create_app

    return TestClient(create_app(assistant=make_assistant()))
