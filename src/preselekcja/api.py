"""API HTTP (FastAPI) i serwowanie interfejsu. Logika jest w `service.py` i modułach domenowych."""
from __future__ import annotations

import os
from pathlib import Path
from typing import Any, Callable, Literal

from fastapi import FastAPI, File, Form, Header, Query, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse, PlainTextResponse
from fastapi.staticfiles import StaticFiles

from . import __version__
from .anonymize import CATEGORY_LABELS
from .bias import FOUR_FIFTHS, SMALL_SAMPLE, STAGES
from .extract import MAX_UPLOAD_BYTES
from .llm import StructuredLLM, get_llm
from .models import (
    DEMOGRAPHIC_LABELS,
    DIMENSION_LABELS,
    KIND_LABELS,
    MIN_REJECTION_REASON,
    RECOMMENDATION_LABELS,
    STATUS_LABELS,
    VERDICT_LABELS,
    DecisionCreate,
    Demographics,
    JobCreate,
    RevealRequest,
)
from .oversight import HIGH_OVERRIDE, LOW_OVERRIDE
from .scoring import THRESHOLDS, VERDICT_CREDIT
from .service import ANONYMOUS_ACTOR, DomainError, NotFound, ScreeningService
from .storage import DemographicsStore, Store

STATIC_DIR = Path(__file__).parent / "static"

_MESSAGES = {
    "missing": "Pole wymagane.",
    "string_too_short": "Za krótki tekst (minimalna liczba znaków: {min_length}).",
    "string_too_long": "Za długi tekst (maksymalna liczba znaków: {max_length}).",
    "too_short": "Za mało elementów (minimum: {min_length}).",
    "too_long": "Za dużo elementów (maksimum: {max_length}).",
    "greater_than_equal": "Wartość musi być ≥ {ge}.",
    "less_than_equal": "Wartość musi być ≤ {le}.",
    "int_parsing": "Oczekiwano liczby całkowitej.",
    "int_type": "Oczekiwano liczby całkowitej.",
    "float_parsing": "Oczekiwano liczby.",
    "bool_parsing": "Oczekiwano wartości tak/nie (true/false).",
    "literal_error": "Niedozwolona wartość. Dozwolone: {expected}.",
    "json_invalid": "Niepoprawny JSON.",
    "model_attributes_type": "Oczekiwano obiektu JSON.",
    "list_type": "Oczekiwano listy.",
    "string_type": "Oczekiwano tekstu.",
}


def _polish_errors(errors: list[dict[str, Any]]) -> list[dict[str, str]]:
    out = []
    for e in errors:
        loc = [str(p) for p in e.get("loc", ()) if p not in ("body", "query", "path", "header")]
        etype = e.get("type", "")
        if etype == "value_error":
            msg = str(e.get("msg", "")).removeprefix("Value error, ")
        elif etype in _MESSAGES:
            ctx = dict(e.get("ctx") or {})
            if "expected" in ctx:
                ctx["expected"] = str(ctx["expected"]).replace(" or ", " lub ")
            try:
                msg = _MESSAGES[etype].format(**ctx)
            except (KeyError, IndexError):
                msg = _MESSAGES[etype]
        else:
            msg = "Niepoprawna wartość."
        out.append({"field": ".".join(loc) or "(całość)", "message": msg})
    return out


def create_app(
    db_path: str | Path | None = None,
    demographics_path: str | Path | None = None,
    llm_provider: Callable[[], StructuredLLM | None] | None = None,
    examples_dir: Path | None = None,
) -> FastAPI:
    db_path = db_path or os.getenv("PRESELEKCJA_DB_PATH", "preselekcja.db")
    demographics_path = demographics_path or os.getenv("PRESELEKCJA_DEMOGRAPHICS_DB_PATH", "preselekcja-demografia.db")
    provider = llm_provider or get_llm
    service = ScreeningService(
        Store(db_path), DemographicsStore(demographics_path), llm_provider=provider, examples_dir=examples_dir
    )

    app = FastAPI(
        title="Preselekcja CV",
        version=__version__,
        description="Wstępna selekcja CV z nadzorem człowieka, anonimizacją i audytem stronniczości.",
    )
    app.state.service = service

    @app.exception_handler(RequestValidationError)
    async def _validation(_: Request, exc: RequestValidationError) -> JSONResponse:
        errors = _polish_errors(exc.errors())
        summary = "; ".join(f"{e['field']}: {e['message'].rstrip('.')}" for e in errors)
        return JSONResponse(status_code=422, content={"detail": f"Błędne dane wejściowe — {summary}", "errors": errors})

    @app.exception_handler(NotFound)
    async def _not_found(_: Request, exc: NotFound) -> JSONResponse:
        return JSONResponse(status_code=404, content={"detail": str(exc)})

    @app.exception_handler(DomainError)
    async def _domain(_: Request, exc: DomainError) -> JSONResponse:
        return JSONResponse(status_code=422, content={"detail": str(exc)})

    def actor_of(x_actor: str | None) -> str:
        value = " ".join((x_actor or "").split())[:80]
        return value or ANONYMOUS_ACTOR

    def require_actor(x_actor: str | None) -> str:
        actor = actor_of(x_actor)
        if actor == ANONYMOUS_ACTOR:
            raise DomainError("Ta akcja wymaga identyfikacji użytkownika — ustaw nagłówek X-Actor (np. „rekruter-1”).")
        return actor

    # --- system ----------------------------------------------------------------
    @app.get("/api/health")
    def health() -> dict[str, Any]:
        return {"status": "ok", "version": __version__, "ai_enabled": provider() is not None}

    @app.get("/api/config")
    def config() -> dict[str, Any]:
        return {
            "thresholds": {"invite_min": THRESHOLDS.invite_min, "consider_min": THRESHOLDS.consider_min},
            "verdict_credit": VERDICT_CREDIT,
            "labels": {
                "verdict": VERDICT_LABELS, "recommendation": RECOMMENDATION_LABELS, "status": STATUS_LABELS,
                "kind": KIND_LABELS, "demographics": DEMOGRAPHIC_LABELS, "dimensions": DIMENSION_LABELS,
                "masked": CATEGORY_LABELS, "stages": STAGES,
            },
            "min_rejection_reason": MIN_REJECTION_REASON,
            "four_fifths": FOUR_FIFTHS,
            "small_sample": SMALL_SAMPLE,
            "override_bounds": {"low": LOW_OVERRIDE, "high": HIGH_OVERRIDE},
            "max_upload_mb": MAX_UPLOAD_BYTES // (1024 * 1024),
        }

    # --- oferty ----------------------------------------------------------------
    @app.post("/api/jobs", status_code=201)
    def create_job(data: JobCreate, x_actor: str | None = Header(default=None)):
        return service.create_job(actor_of(x_actor), data)

    @app.get("/api/jobs")
    def list_jobs():
        return service.list_jobs()

    @app.get("/api/jobs/{job_id}")
    def get_job(job_id: int):
        return service.get_job(job_id)

    # Endpoint synchroniczny: FastAPI uruchamia go w puli wątków, więc wywołanie modelu nie blokuje pętli zdarzeń.
    @app.post("/api/jobs/{job_id}/candidates", status_code=201)
    def add_candidate(
        job_id: int,
        file: UploadFile | None = File(default=None),
        text: str | None = Form(default=None),
        mode: Literal["auto", "offline"] = Form(default="auto"),
        x_actor: str | None = Header(default=None),
    ):
        data = None
        filename = None
        if file is not None and file.filename:
            data = file.file.read(MAX_UPLOAD_BYTES + 1)
            filename = file.filename
        return service.add_candidate(actor_of(x_actor), job_id, text=text, file_bytes=data, filename=filename, mode=mode)

    @app.get("/api/jobs/{job_id}/candidates")
    def ranking(job_id: int):
        return service.ranking(job_id)

    # --- kandydaci -------------------------------------------------------------
    @app.get("/api/candidates/{candidate_id}")
    def candidate(candidate_id: int, x_actor: str | None = Header(default=None)):
        return service.candidate_detail(actor_of(x_actor), candidate_id)

    @app.post("/api/candidates/{candidate_id}/evaluate")
    def evaluate(
        candidate_id: int,
        mode: Literal["auto", "offline"] = Query(default="auto"),
        x_actor: str | None = Header(default=None),
    ):
        return service.reevaluate(actor_of(x_actor), candidate_id, mode)

    @app.post("/api/candidates/{candidate_id}/reveal")
    def reveal(candidate_id: int, data: RevealRequest, x_actor: str | None = Header(default=None)):
        return service.reveal(require_actor(x_actor), candidate_id, data.reason)

    @app.post("/api/candidates/{candidate_id}/decisions", status_code=201)
    def decide(candidate_id: int, data: DecisionCreate, x_actor: str | None = Header(default=None)):
        return service.decide(require_actor(x_actor), candidate_id, data)

    @app.get("/api/candidates/{candidate_id}/explanation")
    def explanation(candidate_id: int, ai: bool = Query(default=False), x_actor: str | None = Header(default=None)):
        return service.explanation(actor_of(x_actor), candidate_id, use_ai=ai)

    @app.put("/api/candidates/{candidate_id}/demographics", status_code=204)
    def demographics(candidate_id: int, data: Demographics) -> None:
        service.submit_demographics(candidate_id, data)

    @app.delete("/api/candidates/{candidate_id}/demographics", status_code=204)
    def withdraw(candidate_id: int) -> None:
        service.withdraw_demographics(candidate_id)

    # --- audyt i raporty -------------------------------------------------------
    @app.get("/api/audit")
    def audit(
        limit: int = Query(default=200, ge=1, le=1000),
        offset: int = Query(default=0, ge=0),
        entity_type: str | None = None,
        entity_id: int | None = None,
        action: str | None = None,
    ):
        return service.audit(limit=limit, offset=offset, entity_type=entity_type, entity_id=entity_id, action=action)

    @app.get("/api/audit/verify")
    def verify():
        return service.verify_audit()

    @app.get("/api/reports/oversight")
    def oversight(job_id: int | None = None):
        return service.oversight_report(job_id)

    @app.get("/api/reports/bias")
    def bias(job_id: int | None = None):
        return service.bias(job_id)

    @app.post("/api/demo/seed", status_code=201)
    def seed(x_actor: str | None = Header(default=None)):
        return service.seed_examples(actor_of(x_actor))

    # --- UI --------------------------------------------------------------------
    @app.get("/", include_in_schema=False)
    def index():
        return FileResponse(STATIC_DIR / "index.html")

    @app.get("/favicon.ico", include_in_schema=False)
    def favicon():
        return PlainTextResponse("", status_code=204)

    app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")
    return app


app = create_app()
