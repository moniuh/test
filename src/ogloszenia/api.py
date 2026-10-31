"""API HTTP (FastAPI) i serwowanie interfejsu WWW."""
from __future__ import annotations

from collections.abc import Callable
from pathlib import Path
from typing import Any, Literal

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, field_validator
from starlette.exceptions import HTTPException as StarletteHTTPException

from . import examples
from .audit import audit_text
from .batch import MAX_ITEMS, MAX_TEXT, BatchFormatError, parse_batch, run_batch
from .llm import StructuredLLM, get_llm
from .models import CATEGORY_LABELS, SEVERITY_LABELS, AuditResult, BatchInput, BatchReport, RewriteResult
from .rewrite import rewrite_text
from .rules import RULES
from .scoring import FORMULA

STATIC_DIR = Path(__file__).parent / "static"

# Domyślne (angielskie) komunikaty Starlette → po polsku.
_HTTP_PL = {
    "Not Found": "Nie znaleziono zasobu.",
    "Method Not Allowed": "Niedozwolona metoda HTTP dla tego adresu.",
    "Internal Server Error": "Wewnętrzny błąd serwera.",
}

_ERRORS_PL = {
    "missing": "To pole jest wymagane.",
    "string_too_short": "Tekst jest za krótki (min. {min_length} znaków).",
    "string_too_long": "Tekst jest za długi (maks. {max_length} znaków).",
    "too_short": "Za mało elementów (min. {min_length}).",
    "too_long": "Za dużo elementów (maks. {max_length}).",
    "string_type": "Oczekiwano tekstu.",
    "bool_type": "Oczekiwano wartości true lub false.",
    "bool_parsing": "Oczekiwano wartości true lub false.",
    "literal_error": "Niedozwolona wartość (dozwolone: {expected}).",
    "json_invalid": "Niepoprawny JSON w treści żądania.",
    "model_attributes_type": "Oczekiwano obiektu JSON.",
    "dict_type": "Oczekiwano obiektu JSON.",
    "list_type": "Oczekiwano listy.",
}


class AuditRequest(BaseModel):
    text: str = Field(min_length=1, max_length=MAX_TEXT, description="Treść ogłoszenia")
    use_ai: bool = Field(True, description="Czy użyć AI (jeśli dostępne)")

    @field_validator("text")
    @classmethod
    def _not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Treść ogłoszenia jest pusta.")
        return value


class BatchItemIn(BaseModel):
    id: str | None = None
    title: str | None = None
    text: str = Field(min_length=1, max_length=MAX_TEXT)


class BatchRequest(BaseModel):
    items: list[BatchItemIn] = Field(min_length=1, max_length=MAX_ITEMS)


class BatchImportRequest(BaseModel):
    content: str = Field(min_length=1, max_length=5_000_000, description="Zawartość pliku CSV lub JSON")
    format: Literal["auto", "json", "csv"] = "auto"


def _polish_errors(exc: RequestValidationError) -> list[dict[str, str]]:
    errors = []
    for err in exc.errors():
        loc = [str(part) for part in err.get("loc", ()) if part != "body"]
        ctx = err.get("ctx") or {}
        if err.get("type") == "value_error":
            message = str(ctx.get("error", err.get("msg", "")))
        else:
            template = _ERRORS_PL.get(err.get("type", ""), "Nieprawidłowa wartość.")
            try:
                message = template.format(**ctx)
            except (KeyError, IndexError):
                message = template
        errors.append({"pole": ".".join(loc) or "(treść żądania)", "komunikat": message})
    return errors


def create_app(
    llm_provider: Callable[[], StructuredLLM | None] = get_llm,
    examples_base: Path | None = None,
) -> FastAPI:
    app = FastAPI(
        title="Audytor ogłoszeń o pracę",
        version="0.1.0",
        description="Sprawdza ogłoszenia o pracę pod kątem jawności wynagrodzeń, neutralności płciowej, "
        "dyskryminacji, danych nadmiarowych i przejrzystości. To nie jest porada prawna.",
    )

    @app.exception_handler(RequestValidationError)
    async def _validation_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
        return JSONResponse(status_code=422, content={"detail": "Nieprawidłowe dane wejściowe.",
                                                      "errors": _polish_errors(exc)})

    @app.exception_handler(StarletteHTTPException)
    async def _http_handler(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        detail = _HTTP_PL.get(exc.detail, exc.detail) if isinstance(exc.detail, str) else exc.detail
        return JSONResponse(status_code=exc.status_code, content={"detail": detail}, headers=exc.headers)

    def _llm(use_ai: bool) -> StructuredLLM | None:
        return llm_provider() if use_ai else None

    @app.get("/api/health")
    def health() -> dict[str, Any]:
        return {"status": "ok", "ai_enabled": llm_provider() is not None, "rules": len(RULES), "formula": FORMULA}

    @app.get("/api/rules")
    def rules() -> list[dict[str, Any]]:
        return [
            {
                "id": r.id,
                "category": r.category,
                "category_label": CATEGORY_LABELS[r.category],
                "severity": r.severity,
                "severity_label": SEVERITY_LABELS[r.severity],
                "title": r.title,
                "kind": r.kind,
                "explanation": r.explanation,
                "legal_basis": r.legal_basis,
                "suggestion": r.suggestion,
                "patterns": len(r.patterns),
                "exceptions": len(r.exceptions) + len(r.sentence_exceptions) + len(r.doc_exceptions),
                "params": r.params,
                "auto_fix": r.fix != "none" or any(p.replace is not None or p.fix not in (None, "none")
                                                     for p in r.patterns) or r.template is not None,
            }
            for r in RULES
        ]

    @app.get("/api/examples")
    def list_examples() -> list[dict[str, str]]:
        return examples.list_examples(examples_base)

    @app.get("/api/examples/{example_id}")
    def get_example(example_id: str) -> dict[str, str]:
        example = examples.load_example(example_id, examples_base)
        if example is None:
            raise HTTPException(status_code=404, detail="Nie znaleziono przykładu o podanym identyfikatorze.")
        return example

    @app.get("/api/batch/sample")
    def batch_sample(format: Literal["csv", "json"] = Query("csv")) -> dict[str, str]:  # noqa: A002
        content = examples.batch_sample(format, examples_base)
        if content is None:
            raise HTTPException(status_code=404, detail="Brak przykładowego pliku wsadowego.")
        return {"format": format, "content": content}

    @app.post("/api/audit", response_model=AuditResult)
    def audit(req: AuditRequest) -> AuditResult:
        return audit_text(req.text, _llm(req.use_ai))

    @app.post("/api/rewrite", response_model=RewriteResult)
    def rewrite(req: AuditRequest) -> RewriteResult:
        return rewrite_text(req.text, _llm(req.use_ai))

    @app.post("/api/batch", response_model=BatchReport)
    def batch(req: BatchRequest) -> BatchReport:
        items = [BatchInput(id=i.id or str(n), title=i.title, text=i.text) for n, i in enumerate(req.items, 1)]
        blank = [i.id for i in items if not i.text.strip()]
        if blank:
            raise HTTPException(status_code=422, detail=f"Pusta treść ogłoszenia: {', '.join(blank)}.")
        return run_batch(items)

    @app.post("/api/batch/import", response_model=BatchReport)
    def batch_import(req: BatchImportRequest) -> BatchReport:
        try:
            items, skipped = parse_batch(req.content, req.format)
            return run_batch(items, skipped)
        except BatchFormatError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

    @app.get("/", include_in_schema=False)
    def index() -> FileResponse:
        return FileResponse(STATIC_DIR / "index.html")

    app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")
    return app


app = create_app()
