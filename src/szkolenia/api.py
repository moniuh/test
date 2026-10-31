"""API HTTP (FastAPI) i serwowanie interfejsu."""
from __future__ import annotations

import logging
import os
from datetime import date, datetime
from pathlib import Path
from typing import Callable

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, Response
from fastapi.staticfiles import StaticFiles
from starlette.exceptions import HTTPException as StarletteHTTPException

from .db import Store
from .llm import StructuredLLM, get_llm
from .report import certificate_html, matrix_to_csv, matrix_to_html, verification_html
from .schemas import (
    AssignmentIn,
    ConfirmIn,
    EmployeeIn,
    EmployeeUpdate,
    ExternalCompletionIn,
    GenerateIn,
    QuestionIn,
    ReviewIn,
    RuleIn,
    SubmitIn,
    TrainingIn,
    TrainingUpdate,
    polish_errors,
)
from .seed import load_company, seed_demo
from .service import Conflict, InvalidInput, NotFound, Settings, TrainingService
from .reminders import ReminderSettings

log = logging.getLogger(__name__)
STATIC_DIR = Path(__file__).parent / "static"


def default_today() -> date:
    """„Dziś” aplikacji; `SZKOLENIA_DZIS=RRRR-MM-DD` pozwala przesunąć czas na potrzeby demo."""
    override = os.getenv("SZKOLENIA_DZIS")
    return date.fromisoformat(override) if override else date.today()


def _brief(assignments: list[dict]) -> list[dict]:
    return [{"id": a["id"], "employee_name": a["employee_name"], "training_title": a["training_title"],
             "due_date": a["due_date"], "reason": a["reason"], "reason_label": a["reason_label"]} for a in assignments]


def _sync_summary(result: dict) -> dict:
    return {"created": _brief(result["created"]), "renewals": _brief(result["renewals"]),
            "cancelled": len(result["cancelled"])}


def create_app(
    *,
    db_path: str | None = None,
    llm_factory: Callable[[], StructuredLLM | None] = get_llm,
    clock: Callable[[], date] = default_today,
    seed: bool | None = None,
) -> FastAPI:
    db_path = db_path or os.getenv("SZKOLENIA_DB", "szkolenia.db")
    seed = os.getenv("SZKOLENIA_SEED", "1") != "0" if seed is None else seed
    company = os.getenv("SZKOLENIA_FIRMA") or (load_company()["company"] if seed else "Firma Demo sp. z o.o.")
    settings = Settings(
        company_name=company,
        reminders=ReminderSettings(hr_email=os.getenv("SZKOLENIA_HR_EMAIL", "hr@firma-demo.example")),
    )
    service = TrainingService(Store(db_path), settings=settings, llm_factory=llm_factory)
    if seed and not service.stats()["trainings"]:
        log.info("Pusta baza — ładuję dane demonstracyjne")
        seed_demo(service, clock())
    service.run_daily(clock())

    app = FastAPI(title="Szkolenia obowiązkowe — LMS lite", version="0.1.0",
                  description="Ewidencja szkoleń obowiązkowych z generatorem testów AI i raportem pod kontrolę.")
    app.state.service = service
    app.state.clock = clock

    # --- Błędy po polsku ---------------------------------------------------------------
    @app.exception_handler(NotFound)
    async def _not_found(_: Request, exc: NotFound) -> JSONResponse:
        return JSONResponse({"detail": str(exc)}, status_code=404)

    @app.exception_handler(Conflict)
    async def _conflict(_: Request, exc: Conflict) -> JSONResponse:
        return JSONResponse({"detail": str(exc)}, status_code=409)

    @app.exception_handler(InvalidInput)
    async def _invalid(_: Request, exc: InvalidInput) -> JSONResponse:
        return JSONResponse({"detail": str(exc)}, status_code=422)

    @app.exception_handler(RequestValidationError)
    async def _validation(_: Request, exc: RequestValidationError) -> JSONResponse:
        return JSONResponse({"detail": "Nieprawidłowe dane wejściowe.", "errors": polish_errors(exc.errors())},
                            status_code=422)

    @app.exception_handler(StarletteHTTPException)
    async def _http(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        messages = {404: "Nie znaleziono zasobu.", 405: "Niedozwolona metoda HTTP."}
        return JSONResponse({"detail": messages.get(exc.status_code, str(exc.detail))}, status_code=exc.status_code)

    today = clock

    # --- Ogólne --------------------------------------------------------------------------
    @app.get("/api/health")
    def health() -> dict:
        mode = "ai" if llm_factory() is not None else "offline"
        return {"status": "ok", "ai_mode": mode, "today": today().isoformat(), "company": settings.company_name}

    @app.get("/api/dashboard")
    def dashboard(department: str | None = None) -> dict:
        return service.dashboard(today(), department or None)

    @app.post("/api/jobs/daily")
    def daily() -> dict:
        return service.run_daily(today())

    # --- Pracownicy ----------------------------------------------------------------------
    @app.get("/api/departments")
    def departments() -> list[str]:
        return service.departments()

    @app.get("/api/employees")
    def employees(department: str | None = None) -> list[dict]:
        matrix = service.compliance_matrix(today(), department or None)
        percent = {r["employee"]["id"]: r["compliance_percent"] for r in matrix["rows"]}
        return [{**e, "compliance_percent": percent.get(e["id"])} for e in service.list_employees(department or None)]

    @app.post("/api/employees", status_code=201)
    def create_employee(body: EmployeeIn) -> dict:
        employee, created = service.create_employee(body.model_dump(), today())
        return {"employee": employee, "assignments_created": _brief(created)}

    @app.get("/api/employees/{employee_id}")
    def get_employee(employee_id: int) -> dict:
        return service.get_employee(employee_id)

    @app.patch("/api/employees/{employee_id}")
    def update_employee(employee_id: int, body: EmployeeUpdate) -> dict:
        data = body.model_dump(exclude_unset=True)
        employee, result = service.update_employee(employee_id, data, today())
        return {"employee": employee, "sync": _sync_summary(result)}

    @app.get("/api/employees/{employee_id}/trainings")
    def employee_trainings(employee_id: int) -> dict:
        return service.employee_trainings(employee_id, today())

    # --- Szkolenia, reguły, pytania --------------------------------------------------------
    @app.get("/api/trainings")
    def trainings() -> list[dict]:
        return service.list_trainings()

    @app.post("/api/trainings", status_code=201)
    def create_training(body: TrainingIn) -> dict:
        return service.create_training(body.model_dump())

    @app.get("/api/trainings/{training_id}")
    def get_training(training_id: int) -> dict:
        return service.get_training(training_id)

    @app.patch("/api/trainings/{training_id}")
    def update_training(training_id: int, body: TrainingUpdate) -> dict:
        training, reverted = service.update_training(training_id, body.model_dump(exclude_unset=True))
        return {"training": training, "questions_reverted": reverted}

    @app.get("/api/rules")
    def rules(training_id: int | None = None) -> list[dict]:
        return service.list_rules(training_id)

    @app.post("/api/rules", status_code=201)
    def create_rule(body: RuleIn) -> dict:
        rule, result = service.create_rule(body.model_dump(), today())
        return {"rule": rule, "sync": _sync_summary(result)}

    @app.delete("/api/rules/{rule_id}")
    def delete_rule(rule_id: int) -> dict:
        return {"sync": _sync_summary(service.delete_rule(rule_id, today()))}

    @app.get("/api/trainings/{training_id}/questions")
    def questions(training_id: int, status: str | None = None) -> list[dict]:
        return service.list_questions(training_id, status or None)

    @app.post("/api/trainings/{training_id}/questions/generate")
    def generate(training_id: int, body: GenerateIn) -> dict:
        return service.generate_questions(training_id, body.count)

    @app.post("/api/trainings/{training_id}/questions", status_code=201)
    def create_question(training_id: int, body: QuestionIn) -> dict:
        return service.create_question(training_id, body.model_dump())

    @app.put("/api/questions/{question_id}")
    def update_question(question_id: int, body: QuestionIn) -> dict:
        return service.update_question(question_id, body.model_dump())

    @app.post("/api/questions/{question_id}/approve")
    def approve(question_id: int, body: ReviewIn | None = None) -> dict:
        return service.approve_question(question_id, (body or ReviewIn()).reviewer)

    @app.post("/api/questions/{question_id}/reject")
    def reject(question_id: int, body: ReviewIn | None = None) -> dict:
        return service.reject_question(question_id, (body or ReviewIn()).reviewer)

    @app.delete("/api/questions/{question_id}", status_code=204)
    def delete_question(question_id: int) -> Response:
        service.delete_question(question_id)
        return Response(status_code=204)

    # --- Przypisania, testy, potwierdzenia --------------------------------------------------
    @app.post("/api/assignments", status_code=201)
    def create_assignment(body: AssignmentIn) -> dict:
        return service.create_manual_assignment(body.employee_id, body.training_id, body.due_date, today())

    @app.get("/api/assignments/{assignment_id}")
    def get_assignment(assignment_id: int) -> dict:
        return service.get_assignment(assignment_id)

    @app.post("/api/assignments/{assignment_id}/attempts", status_code=201)
    def start_attempt(assignment_id: int) -> dict:
        return service.start_attempt(assignment_id)

    @app.post("/api/assignments/{assignment_id}/confirm")
    def confirm(assignment_id: int, body: ConfirmIn) -> dict:
        return service.confirm_attendance(assignment_id, body.trainer, body.session_date, today())

    @app.post("/api/assignments/{assignment_id}/external")
    def external(assignment_id: int, body: ExternalCompletionIn) -> dict:
        return service.record_external_completion(assignment_id, body.completed_on, body.note, today())

    @app.get("/api/attempts/{attempt_id}")
    def get_attempt(attempt_id: int) -> dict:
        return service.get_attempt(attempt_id)

    @app.post("/api/attempts/{attempt_id}/submit")
    def submit(attempt_id: int, body: SubmitIn) -> dict:
        answers = {a.question_id: a.option_index for a in body.answers}
        return service.submit_attempt(attempt_id, answers, today())

    # --- Zaświadczenia i weryfikacja -------------------------------------------------------
    @app.get("/api/certificates")
    def certificates(employee_id: int | None = None) -> list[dict]:
        return service.list_certificates(employee_id)

    @app.get("/api/certificates/{certificate_id}")
    def get_certificate(certificate_id: int) -> dict:
        return service.get_certificate(certificate_id)

    @app.get("/api/verify/{code}")
    def verify(code: str) -> dict:
        return service.verify_certificate(code, today())

    @app.get("/certificates/{certificate_id}", response_class=HTMLResponse)
    def certificate_page(certificate_id: int, request: Request) -> HTMLResponse:
        cert = service.get_certificate(certificate_id)
        url = str(request.url_for("verify_page", code=cert["verification_code"]))
        return HTMLResponse(certificate_html(cert, settings.company_name, url))

    @app.get("/verify/{code}", response_class=HTMLResponse, name="verify_page")
    def verify_page(code: str) -> HTMLResponse:
        try:
            result = service.verify_certificate(code, today())
        except NotFound:
            return HTMLResponse(verification_html(None, code), status_code=404)
        return HTMLResponse(verification_html(result, code))

    # --- Raport pod kontrolę --------------------------------------------------------------
    @app.get("/api/report")
    def report(department: str | None = None) -> dict:
        return service.compliance_matrix(today(), department or None)

    @app.get("/api/report.csv")
    def report_csv(department: str | None = None) -> Response:
        matrix = service.compliance_matrix(today(), department or None)
        name = f"raport-szkolen-{today().isoformat()}.csv"
        return Response(matrix_to_csv(matrix), media_type="text/csv; charset=utf-8",
                        headers={"Content-Disposition": f'attachment; filename="{name}"'})

    @app.get("/report/print", response_class=HTMLResponse)
    def report_print(department: str | None = None) -> HTMLResponse:
        matrix = service.compliance_matrix(today(), department or None)
        return HTMLResponse(matrix_to_html(matrix, datetime.now().strftime("%d.%m.%Y %H:%M")))

    # --- Kolejka przypomnień ---------------------------------------------------------------
    @app.get("/api/outbox")
    def outbox(status: str | None = None) -> list[dict]:
        return service.list_outbox(status or None)

    @app.post("/api/outbox/send")
    def send_outbox() -> dict:
        return {"sent": service.send_pending()}

    # --- Interfejs -----------------------------------------------------------------------
    app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")

    @app.get("/", include_in_schema=False)
    def index() -> FileResponse:
        return FileResponse(STATIC_DIR / "index.html")

    return app


app = create_app()
