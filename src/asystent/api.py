"""API HTTP (FastAPI) i serwowanie UI. Logika jest w `Assistant`; tu tylko walidacja i mapowanie."""
from __future__ import annotations

import base64
import binascii
import logging
import secrets
from datetime import date
from pathlib import Path
from typing import Annotated, Literal

from fastapi import Depends, FastAPI, Header, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from .assistant import Assistant, DomainError, NotFoundError, new_session_token, session_ref
from .chunking import DocumentError
from .config import Settings
from .knowledge import DocumentInfo
from .llm import ai_enabled
from .models import Answer, Escalation, GapsReport, Stats
from .store import Store

log = logging.getLogger(__name__)
STATIC_DIR = Path(__file__).parent / "static"


# --- modele żądań ---
class AskIn(BaseModel):
    question: str = Field(min_length=1, max_length=1000)
    session_token: str | None = Field(default=None, max_length=64)


class FeedbackIn(BaseModel):
    session_token: str = Field(max_length=64)
    vote: Literal["up", "down"]
    comment: str | None = Field(default=None, max_length=500)


class EscalationIn(BaseModel):
    session_token: str | None = Field(default=None, max_length=64)
    interaction_id: int | None = None
    question: str | None = Field(default=None, max_length=1000)
    note: str | None = Field(default=None, max_length=1000)
    contact: str | None = Field(default=None, max_length=200)


class UploadIn(BaseModel):
    filename: str = Field(min_length=1, max_length=200)
    content_base64: str = Field(min_length=1)
    title: str | None = Field(default=None, max_length=200)
    doc_key: str | None = Field(default=None, max_length=80)
    version: str | None = Field(default=None, max_length=40)
    effective_from: date | None = None


class ReplyIn(BaseModel):
    reply: str = Field(min_length=1, max_length=4000)
    close: bool = False


class WithdrawIn(BaseModel):
    withdrawn: bool = True


class PurgeIn(BaseModel):
    days: int | None = Field(default=None, ge=1, le=3650)


class SessionOut(BaseModel):
    session_token: str
    session_id: str


class EscalationOut(BaseModel):
    session_token: str
    escalation: Escalation


class DocumentOut(BaseModel):
    id: int
    doc_key: str
    title: str
    version: str
    effective_from: str
    uploaded_at: str
    filename: str
    chunk_count: int
    withdrawn: bool
    injection_flag: bool
    status: str


class ChunkOut(BaseModel):
    id: str
    ordinal: int
    section_path: list[str]
    text: str


class DocumentDetailOut(DocumentOut):
    chunks: list[ChunkOut]


class UnauthorizedError(DomainError):
    status_code = 401


_ERROR_MESSAGES = {
    "missing": "Pole jest wymagane",
    "string_too_short": "Tekst jest za krótki",
    "string_too_long": "Tekst jest za długi",
    "string_type": "Oczekiwano tekstu",
    "literal_error": "Niedozwolona wartość",
    "int_parsing": "Oczekiwano liczby całkowitej",
    "int_type": "Oczekiwano liczby całkowitej",
    "bool_parsing": "Oczekiwano wartości logicznej",
    "date_from_datetime_parsing": "Nieprawidłowa data — oczekiwany format RRRR-MM-DD",
    "date_parsing": "Nieprawidłowa data — oczekiwany format RRRR-MM-DD",
    "date_type": "Nieprawidłowa data — oczekiwany format RRRR-MM-DD",
    "greater_than_equal": "Wartość jest za mała",
    "less_than_equal": "Wartość jest za duża",
    "json_invalid": "Niepoprawny JSON",
    "model_attributes_type": "Oczekiwano obiektu JSON",
    "dict_type": "Oczekiwano obiektu JSON",
}


def get_assistant(request: Request) -> Assistant:
    return request.app.state.assistant


AssistantDep = Annotated[Assistant, Depends(get_assistant)]


def _doc_out(doc: DocumentInfo) -> DocumentOut:
    return DocumentOut(**doc.__dict__)


def create_app(settings: Settings | None = None, assistant: Assistant | None = None) -> FastAPI:
    settings = settings or (assistant.settings if assistant else Settings.from_env())
    if assistant is None:
        assistant = Assistant(Store(settings.db_path), settings)
        seeded = assistant.kb.seed_directory(settings.docs_dir) if settings.docs_dir else 0
        if seeded:
            log.info("Wczytano %d przykładowych dokumentów", seeded)
        assistant.purge()

    app = FastAPI(
        title="Asystent kadrowy",
        description="Odpowiedzi na pytania pracowników z cytatami z regulaminów firmy.",
        version="0.1.0",
    )
    app.state.assistant = assistant

    @app.exception_handler(DomainError)
    async def _domain_error(_: Request, exc: DomainError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content={"detail": str(exc)})

    @app.exception_handler(DocumentError)
    async def _document_error(_: Request, exc: DocumentError) -> JSONResponse:
        return JSONResponse(status_code=422, content={"detail": str(exc)})

    @app.exception_handler(RequestValidationError)
    async def _validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        errors = [
            {
                "pole": ".".join(str(p) for p in err.get("loc", ()) if p != "body") or "body",
                "komunikat": _ERROR_MESSAGES.get(err.get("type", ""), "Nieprawidłowa wartość"),
            }
            for err in exc.errors()
        ]
        return JSONResponse(status_code=422, content={"detail": "Nieprawidłowe dane wejściowe", "bledy": errors})

    def require_hr(x_hr_token: Annotated[str | None, Header()] = None) -> None:
        """Panel HR: gdy ustawiono HR_PANEL_TOKEN, wymagany nagłówek X-HR-Token."""
        expected = settings.hr_token
        if expected and not (x_hr_token and secrets.compare_digest(x_hr_token, expected)):
            raise UnauthorizedError("Brak dostępu do panelu HR — podaj prawidłowy token (nagłówek X-HR-Token)")

    hr = [Depends(require_hr)]

    # --- pracownik ---
    @app.get("/", include_in_schema=False)
    def index() -> FileResponse:
        return FileResponse(STATIC_DIR / "index.html")

    @app.get("/api/health")
    def health(a: AssistantDep) -> dict:
        docs = a.kb.documents()
        return {
            "status": "ok",
            "mode": "ai" if ai_enabled() else "offline",
            "documents_current": sum(d.status == "aktualna" for d in docs),
            "chunks_indexed": len(a.engine().passages),
            "hr_token_required": bool(settings.hr_token),
            "retention_days": settings.retention_days,
            "hr_contact": settings.hr_contact,
        }

    @app.post("/api/sessions", response_model=SessionOut)
    def create_session() -> SessionOut:
        token = new_session_token()
        return SessionOut(session_token=token, session_id=session_ref(token)[:8])

    @app.post("/api/ask", response_model=Answer)
    def ask(body: AskIn, a: AssistantDep) -> Answer:
        return a.ask(body.question, body.session_token)

    @app.post("/api/interactions/{interaction_id}/feedback")
    def feedback(interaction_id: int, body: FeedbackIn, a: AssistantDep) -> dict:
        a.feedback(interaction_id, body.session_token, body.vote, body.comment)
        return {"ok": True}

    @app.post("/api/escalations", response_model=EscalationOut, status_code=201)
    def escalate(body: EscalationIn, a: AssistantDep) -> EscalationOut:
        token, escalation = a.escalate(body.session_token, body.interaction_id, body.question, body.note, body.contact)
        return EscalationOut(session_token=token, escalation=escalation)

    @app.get("/api/me/escalations", response_model=list[Escalation])
    def my_escalations(a: AssistantDep, x_session_token: Annotated[str | None, Header()] = None) -> list[Escalation]:
        return a.my_escalations(x_session_token)

    # --- panel HR ---
    @app.get("/api/hr/documents", response_model=list[DocumentOut], dependencies=hr)
    def list_documents(a: AssistantDep) -> list[DocumentOut]:
        return [_doc_out(d) for d in a.kb.documents()]

    @app.post("/api/hr/documents", response_model=DocumentOut, status_code=201, dependencies=hr)
    def upload_document(body: UploadIn, a: AssistantDep) -> DocumentOut:
        try:
            data = base64.b64decode(body.content_base64, validate=True)
        except (binascii.Error, ValueError) as exc:
            raise DomainError("Treść pliku musi być zakodowana w Base64") from exc
        doc = a.kb.add_document(
            body.filename, data, title=body.title or None, doc_key=body.doc_key or None,
            version=body.version or None, effective_from=body.effective_from,
        )
        return _doc_out(doc)

    @app.get("/api/hr/documents/{doc_id}", response_model=DocumentDetailOut, dependencies=hr)
    def document_detail(doc_id: int, a: AssistantDep) -> DocumentDetailOut:
        doc = a.kb.get(doc_id)
        if doc is None:
            raise NotFoundError("Nie znaleziono dokumentu")
        chunks = [ChunkOut(id=c["id"], ordinal=c["ordinal"], section_path=c["section_path"], text=c["text"])
                  for c in a.kb.chunks(doc_id)]
        return DocumentDetailOut(**doc.__dict__, chunks=chunks)

    @app.post("/api/hr/documents/{doc_id}/withdraw", response_model=DocumentOut, dependencies=hr)
    def withdraw_document(doc_id: int, body: WithdrawIn, a: AssistantDep) -> DocumentOut:
        doc = a.kb.set_withdrawn(doc_id, body.withdrawn)
        if doc is None:
            raise NotFoundError("Nie znaleziono dokumentu")
        return _doc_out(doc)

    @app.get("/api/hr/search", dependencies=hr)
    def search(a: AssistantDep, q: Annotated[str, Query(min_length=1, max_length=300)], k: int = 5) -> list[dict]:
        return [
            {"chunk_id": h.passage.chunk_id, "path": h.passage.path_label, "score": round(h.score, 3),
             "coverage": round(h.coverage, 3), "text": h.passage.text}
            for h in a.engine().search(q, k=max(1, min(k, 20)))
        ]

    @app.get("/api/hr/escalations", response_model=list[Escalation], dependencies=hr)
    def list_escalations(a: AssistantDep, status: str | None = None) -> list[Escalation]:
        return a.escalations(status or None)

    @app.post("/api/hr/escalations/{escalation_id}/reply", response_model=Escalation, dependencies=hr)
    def reply(escalation_id: int, body: ReplyIn, a: AssistantDep) -> Escalation:
        return a.reply(escalation_id, body.reply, body.close)

    @app.post("/api/hr/escalations/{escalation_id}/close", response_model=Escalation, dependencies=hr)
    def close(escalation_id: int, a: AssistantDep) -> Escalation:
        return a.close(escalation_id)

    @app.get("/api/hr/gaps", response_model=GapsReport, dependencies=hr)
    def gaps(a: AssistantDep, days: Annotated[int, Query(ge=1, le=3650)] = 90) -> GapsReport:
        return a.knowledge_gaps(days)

    @app.get("/api/hr/stats", response_model=Stats, dependencies=hr)
    def stats(a: AssistantDep) -> Stats:
        return a.stats()

    @app.get("/api/hr/retention", dependencies=hr)
    def retention(a: AssistantDep) -> dict:
        return a.retention_info()

    @app.post("/api/hr/retention/purge", dependencies=hr)
    def purge(body: PurgeIn, a: AssistantDep) -> dict:
        return a.purge(body.days)

    app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")
    return app


app = create_app()
