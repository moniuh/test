"""Warstwa dostępu do modelu językowego (Claude) z bezpiecznym trybem offline.

Każda funkcja AI w aplikacji przechodzi przez `StructuredLLM.generate`, które zwraca
zwalidowany obiekt Pydantic. Gdy model jest niedostępny (brak klucza, błąd sieci,
odmowa, ucięta odpowiedź), rzucany jest `LLMUnavailable`, a wywołujący przełącza się
na deterministyczną ścieżkę offline. Dzięki temu aplikacja działa i jest testowalna
bez dostępu do API.
"""
from __future__ import annotations

import logging
import os
from typing import Protocol, TypeVar

import anthropic
from pydantic import BaseModel

DEFAULT_MODEL = "claude-opus-5-5"
FALLBACK_BETA = "server-side-fallback-2026-07-01"

T = TypeVar("T", bound=BaseModel)
log = logging.getLogger(__name__)


class LLMUnavailable(RuntimeError):
    """Model nie dostarczył użytecznej odpowiedzi; użyj ścieżki offline."""


class StructuredLLM(Protocol):
    name: str

    def generate(self, *, system: str, prompt: str, schema: type[T], effort: str = "medium") -> T: ...


class ClaudeLLM:
    """Klient Claude zwracający ustrukturyzowane odpowiedzi (structured outputs)."""

    name = "claude"

    def __init__(self, client: anthropic.Anthropic | None = None, model: str | None = None) -> None:
        self._client = client or anthropic.Anthropic()
        self.model = model or os.getenv("HR_AI_MODEL", DEFAULT_MODEL)

    def generate(self, *, system: str, prompt: str, schema: type[T], effort: str = "medium") -> T:
        try:
            response = self._client.beta.messages.parse(
                model=self.model,
                max_tokens=16000,
                system=system,
                messages=[{"role": "user", "content": prompt}],
                output_format=schema,
                output_config={"effort": effort},
                betas=[FALLBACK_BETA],
                fallbacks="default",
            )
        except anthropic.RateLimitError as exc:
            raise LLMUnavailable("Przekroczony limit zapytań do modelu") from exc
        except anthropic.APIStatusError as exc:
            raise LLMUnavailable(f"Błąd API modelu ({exc.status_code})") from exc
        except anthropic.APIConnectionError as exc:
            raise LLMUnavailable("Brak połączenia z API modelu") from exc

        if response.stop_reason == "refusal":
            raise LLMUnavailable("Model odmówił odpowiedzi")
        if response.stop_reason == "max_tokens":
            raise LLMUnavailable("Odpowiedź modelu została ucięta")
        parsed = response.parsed_output
        if parsed is None:
            raise LLMUnavailable("Model nie zwrócił poprawnej struktury")
        log.info("LLM ok model=%s request_id=%s", response.model, getattr(response, "_request_id", None))
        return parsed


def ai_enabled() -> bool:
    """AI włączone, gdy są poświadczenia i nie wymuszono trybu offline (HR_AI_OFFLINE=1)."""
    if os.getenv("HR_AI_OFFLINE") == "1":
        return False
    return bool(os.getenv("ANTHROPIC_API_KEY") or os.getenv("ANTHROPIC_AUTH_TOKEN"))


def get_llm() -> StructuredLLM | None:
    """Zwraca klienta Claude albo None (tryb offline)."""
    return ClaudeLLM() if ai_enabled() else None
