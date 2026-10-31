"""API HTTP (FastAPI) i serwowanie UI. Logika domenowa siedzi w `service.py`."""
from __future__ import annotations

import logging
import os
from collections.abc import Callable
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse, Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, field_validator

from .llm import StructuredLLM, get_llm
from .scenarios import Scenario, ScenarioNotFound
from .service import EmptySession, SessionFinished, SessionNotFound, SimulatorService, TurnLimitReached
from .storage import SessionStore

STATIC_DIR = Path(__file__).parent / "static"
MAX_QUESTION_CHARS = 1000
log = logging.getLogger(__name__)


class StartSessionIn(BaseModel):
    scenario_id: str = Field(min_length=1, max_length=100)


class QuestionIn(BaseModel):
    question: str = Field(max_length=MAX_QUESTION_CHARS)

    @field_validator("question")
    @classmethod
    def _not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Pytanie nie może być puste.")
        return value.strip()


def _polish_error(err: dict) -> str:
    kind = err.get("type", "")
    ctx = err.get("ctx") or {}
    if kind == "missing":
        return "Pole jest wymagane."
    if kind == "string_too_short":
        return "Tekst jest za krótki."
    if kind == "string_too_long":
        return f"Tekst jest za długi (maksymalnie {ctx.get('max_length')} znaków)."
    if kind == "string_type":
        return "Oczekiwano tekstu."
    if kind in ("json_invalid", "model_attributes_type", "dict_type"):
        return "Nieprawidłowy format JSON."
    if kind == "value_error":
        return str(ctx.get("error", err.get("msg", ""))).removeprefix("Value error, ")
    return "Nieprawidłowa wartość."


def create_app(
    db_path: str | Path | None = None,
    llm_provider: Callable[[], StructuredLLM | None] | None = None,
    scenarios: dict[str, Scenario] | None = None,
) -> FastAPI:
    store = SessionStore(db_path or os.getenv("SYMULATOR_DB", "symulator.db"))
    retention = os.getenv("SYMULATOR_RETENTION_DAYS")
    if retention and retention.isdigit():
        removed = store.purge_older_than(int(retention))
        if removed:
            log.info("Retencja: usunięto %d starych sesji", removed)
    service = SimulatorService(store, scenarios=scenarios, llm_provider=llm_provider or get_llm)

    app = FastAPI(
        title="Symulator rozmów rekrutacyjnych",
        description="Trening prowadzenia rozmów: kandydat symulowany przez AI, coach na żywo i raport po sesji.",
        version="0.1.0",
    )
    app.state.service = service

    @app.exception_handler(RequestValidationError)
    async def _validation_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
        errors = [
            {"pole": ".".join(str(p) for p in e.get("loc", ()) if p != "body") or "body", "komunikat": _polish_error(e)}
            for e in exc.errors()
        ]
        return JSONResponse(status_code=422, content={"detail": "Nieprawidłowe dane wejściowe.", "bledy": errors})

    def _not_found(session_id: str) -> HTTPException:
        return HTTPException(status_code=404, detail=f"Nie znaleziono sesji „{session_id}”.")

    @app.get("/", include_in_schema=False)
    def index() -> FileResponse:
        return FileResponse(STATIC_DIR / "index.html")

    @app.get("/api/status")
    def status() -> dict:
        ai = service.ai_enabled()
        return {
            "mode": "ai" if ai else "offline",
            "ai_enabled": ai,
            "notice": "Kandydat jest symulowany przez AI." if ai else "Kandydat jest symulowany przez skrypt offline (bez AI).",
        }

    @app.get("/api/scenarios")
    def list_scenarios() -> list[dict]:
        return [s.public_view() for s in service.scenarios.values()]

    @app.get("/api/scenarios/{scenario_id}")
    def get_scenario(scenario_id: str) -> dict:
        try:
            return service.scenario(scenario_id).public_view()
        except ScenarioNotFound:
            raise HTTPException(status_code=404, detail=f"Nie znaleziono scenariusza „{scenario_id}”.") from None

    @app.post("/api/sessions", status_code=201)
    def start_session(body: StartSessionIn) -> dict:
        try:
            record = service.start_session(body.scenario_id)
        except ScenarioNotFound:
            raise HTTPException(status_code=404, detail=f"Nie znaleziono scenariusza „{body.scenario_id}”.") from None
        return service.session_view(record.id)

    @app.get("/api/sessions")
    def list_sessions() -> list[dict]:
        return service.list_sessions()

    @app.get("/api/sessions/{session_id}")
    def get_session(session_id: str) -> dict:
        try:
            return service.session_view(session_id)
        except SessionNotFound:
            raise _not_found(session_id) from None

    @app.post("/api/sessions/{session_id}/turns")
    def ask(session_id: str, body: QuestionIn) -> dict:
        try:
            return service.ask(session_id, body.question).model_dump()
        except SessionNotFound:
            raise _not_found(session_id) from None
        except SessionFinished:
            raise HTTPException(status_code=409, detail="Sesja jest zakończona — rozpocznij nową, aby dalej ćwiczyć.") from None
        except TurnLimitReached:
            raise HTTPException(status_code=409, detail="Osiągnięto limit pytań w sesji. Zakończ ją i zobacz raport.") from None

    @app.post("/api/sessions/{session_id}/finish")
    def finish(session_id: str) -> dict:
        try:
            return service.finish(session_id).model_dump()
        except SessionNotFound:
            raise _not_found(session_id) from None
        except EmptySession:
            raise HTTPException(status_code=409, detail="Zadaj co najmniej jedno pytanie, zanim zakończysz sesję.") from None

    @app.get("/api/sessions/{session_id}/report")
    def get_report(session_id: str) -> dict:
        try:
            report = service.report(session_id)
        except SessionNotFound:
            raise _not_found(session_id) from None
        if report is None:
            raise HTTPException(status_code=404, detail="Raport jeszcze nie istnieje — najpierw zakończ sesję.")
        return report.model_dump()

    @app.get("/api/sessions/{session_id}/export.md")
    def export_markdown(session_id: str) -> Response:
        try:
            text = service.export_markdown(session_id)
        except SessionNotFound:
            raise _not_found(session_id) from None
        return Response(
            content=text,
            media_type="text/markdown; charset=utf-8",
            headers={"Content-Disposition": f'attachment; filename="sesja-{session_id}.md"'},
        )

    @app.delete("/api/sessions/{session_id}", status_code=204)
    def delete_session(session_id: str) -> Response:
        try:
            service.delete(session_id)
        except SessionNotFound:
            raise _not_found(session_id) from None
        return Response(status_code=204)

    app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")
    return app


app = create_app()
