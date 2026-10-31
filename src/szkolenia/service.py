"""Operacje aplikacji: spina czyste funkcje domenowe z bazą. Bez zależności od FastAPI."""
from __future__ import annotations

import json
import logging
import secrets
import sqlite3
from dataclasses import dataclass, field
from datetime import date, datetime
from typing import Any, Callable

from .compliance import (
    COMPLIANT_STATUSES,
    STATUS_LABELS,
    compliance_status,
    compute_valid_until,
    days_overdue,
    needs_renewal,
)
from .db import Store
from .llm import StructuredLLM, get_llm
from .quiz import GradingError, QuestionSnapshot, build_quiz, grade, select_questions
from .quizgen import QuestionValidationError, check_question_structure, generate_questions
from .reminders import ReminderContext, ReminderSettings, plan_messages
from .rules import EmployeeSpec, RuleSpec, initial_due_date, required_trainings
from .textutil import normalize_ws, verify_quote

log = logging.getLogger(__name__)

CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"  # bez 0/O i 1/I/L — łatwiej przepisać z wydruku
MODE_LABELS = {"elearning": "e-learning", "classroom": "stacjonarne"}
QUESTION_STATUS_LABELS = {"draft": "szkic", "approved": "zatwierdzone", "rejected": "odrzucone"}
REASON_LABELS = {"new_hire": "nowy pracownik", "rule": "reguła", "renewal": "odnowienie", "manual": "ręczne"}
METHOD_LABELS = {"test": "test online", "confirmation": "potwierdzenie prowadzącego", "external": "wpis z ewidencji"}
_STATUS_ORDER = {"overdue": 0, "incomplete": 1, "expiring": 2, "valid": 3, "not_applicable": 4}


class NotFound(LookupError):
    """Brak obiektu (API: 404)."""


class Conflict(RuntimeError):
    """Operacja niemożliwa w obecnym stanie (API: 409)."""


class InvalidInput(ValueError):
    """Dane poprawne składniowo, ale sprzeczne z regułami (API: 422)."""


@dataclass
class Settings:
    company_name: str = "Firma Demo sp. z o.o."
    reminders: ReminderSettings = field(default_factory=ReminderSettings)


def _d(value: str | None) -> date | None:
    return date.fromisoformat(value) if value else None


def _iso(value: date | None) -> str | None:
    return value.isoformat() if value else None


def _now() -> datetime:
    return datetime.now().replace(microsecond=0)


def normalize_code(code: str) -> str:
    raw = "".join(ch for ch in code.upper() if ch.isalnum())
    return "-".join(raw[i:i + 4] for i in range(0, len(raw), 4))


class TrainingService:
    def __init__(
        self,
        store: Store,
        *,
        settings: Settings | None = None,
        llm_factory: Callable[[], StructuredLLM | None] = get_llm,
    ) -> None:
        self.store = store
        self.settings = settings or Settings()
        self.llm_factory = llm_factory

    # --- Pracownicy ----------------------------------------------------------------------

    def list_employees(self, department: str | None = None) -> list[dict]:
        sql = """SELECT e.*, m.full_name AS manager_name FROM employees e
                 LEFT JOIN employees m ON m.id = e.manager_id WHERE e.active = 1"""
        params: tuple = ()
        if department:
            sql += " AND e.department = ?"
            params = (department,)
        return self.store.all(sql + " ORDER BY e.department, e.full_name", params)

    def departments(self) -> list[str]:
        rows = self.store.all("SELECT DISTINCT department FROM employees WHERE active = 1 ORDER BY department")
        return [r["department"] for r in rows]

    def get_employee(self, employee_id: int) -> dict:
        row = self.store.one(
            """SELECT e.*, m.full_name AS manager_name, m.email AS manager_email FROM employees e
               LEFT JOIN employees m ON m.id = e.manager_id WHERE e.id = ?""",
            (employee_id,),
        )
        if not row:
            raise NotFound("Nie znaleziono pracownika.")
        return row

    def _check_manager(self, manager_id: int | None, employee_id: int | None = None) -> None:
        if manager_id is None:
            return
        if manager_id == employee_id:
            raise InvalidInput("Pracownik nie może być swoim przełożonym.")
        if not self.store.one("SELECT id FROM employees WHERE id = ?", (manager_id,)):
            raise InvalidInput("Wskazany przełożony nie istnieje.")

    def create_employee(self, data: dict, today: date) -> tuple[dict, list[dict]]:
        """Dodaje pracownika i od razu przypisuje szkolenia z reguł (termin = zatrudnienie + N dni)."""
        self._check_manager(data.get("manager_id"))
        with self.store.transaction():
            try:
                employee_id = self.store.insert(
                    """INSERT INTO employees (full_name, email, department, position, hire_date, manager_id, created_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?)""",
                    (normalize_ws(data["full_name"]), data["email"].strip().lower(),
                     normalize_ws(data["department"]), normalize_ws(data["position"]),
                     _iso(data["hire_date"]), data.get("manager_id"), _now().isoformat()),
                )
            except sqlite3.IntegrityError as exc:
                raise Conflict("Pracownik z tym adresem e-mail już istnieje.") from exc
            result = self.sync_assignments(today, employee_ids=[employee_id], on_hire=True)
        return self.get_employee(employee_id), result["created"]

    def update_employee(self, employee_id: int, data: dict, today: date) -> tuple[dict, dict]:
        current = self.get_employee(employee_id)
        merged = {**current, **{k: v for k, v in data.items() if v is not None}}
        if "manager_id" in data:
            merged["manager_id"] = data["manager_id"]
        self._check_manager(merged.get("manager_id"), employee_id)
        with self.store.transaction():
            try:
                self.store.execute(
                    """UPDATE employees SET full_name=?, email=?, department=?, position=?, hire_date=?,
                       manager_id=?, active=? WHERE id=?""",
                    (normalize_ws(merged["full_name"]), merged["email"].strip().lower(),
                     normalize_ws(merged["department"]), normalize_ws(merged["position"]),
                     _iso(merged["hire_date"]) if isinstance(merged["hire_date"], date) else merged["hire_date"],
                     merged.get("manager_id"), int(bool(merged.get("active", 1))), employee_id),
                )
            except sqlite3.IntegrityError as exc:
                raise Conflict("Pracownik z tym adresem e-mail już istnieje.") from exc
            if not merged.get("active", 1):
                self.store.execute(
                    "UPDATE assignments SET status='cancelled' WHERE employee_id=? AND status='open'", (employee_id,)
                )
                result = {"created": [], "renewals": [], "cancelled": []}
            else:
                result = self.sync_assignments(today, employee_ids=[employee_id])
        return self.get_employee(employee_id), result

    # --- Szkolenia i reguły ----------------------------------------------------------------

    def list_trainings(self) -> list[dict]:
        rows = self.store.all("SELECT * FROM trainings ORDER BY category, title")
        counts = self.store.all("SELECT training_id, status, COUNT(*) AS n FROM questions GROUP BY training_id, status")
        rule_counts = {r["training_id"]: r["n"] for r in self.store.all(
            "SELECT training_id, COUNT(*) AS n FROM assignment_rules GROUP BY training_id")}
        for row in rows:
            row["question_counts"] = {s: 0 for s in QUESTION_STATUS_LABELS}
            row["rules_count"] = rule_counts.get(row["id"], 0)
            row["mode_label"] = MODE_LABELS[row["mode"]]
        by_id = {r["id"]: r for r in rows}
        for c in counts:
            by_id[c["training_id"]]["question_counts"][c["status"]] = c["n"]
        return rows

    def get_training(self, training_id: int) -> dict:
        row = self.store.one("SELECT * FROM trainings WHERE id = ?", (training_id,))
        if not row:
            raise NotFound("Nie znaleziono szkolenia.")
        row["mode_label"] = MODE_LABELS[row["mode"]]
        row["rules"] = self.list_rules(training_id)
        counts = {s: 0 for s in QUESTION_STATUS_LABELS}
        for c in self.store.all("SELECT status, COUNT(*) AS n FROM questions WHERE training_id=? GROUP BY status",
                                (training_id,)):
            counts[c["status"]] = c["n"]
        row["question_counts"] = counts
        return row

    def create_training(self, data: dict) -> dict:
        try:
            training_id = self.store.insert(
                """INSERT INTO trainings (title, category, validity_months, pass_threshold, mode, material,
                   questions_per_test, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                (normalize_ws(data["title"]), normalize_ws(data["category"]), data.get("validity_months"),
                 data.get("pass_threshold", 80), data["mode"], data.get("material", "").strip(),
                 data.get("questions_per_test", 10), _now().isoformat()),
            )
        except sqlite3.IntegrityError as exc:
            raise Conflict("Szkolenie o takim tytule już istnieje.") from exc
        return self.get_training(training_id)

    def update_training(self, training_id: int, data: dict) -> tuple[dict, int]:
        """Aktualizuje szkolenie. Po zmianie materiału ponownie weryfikuje cytaty pytań.

        Zwraca liczbę zatwierdzonych pytań cofniętych do szkicu (cytat zniknął z materiału).
        """
        current = self.get_training(training_id)
        fields = ("title", "category", "validity_months", "pass_threshold", "mode", "material", "questions_per_test")
        merged = {f: current[f] for f in fields}
        for f in fields:
            if f in data:
                merged[f] = data[f]
        reverted = 0
        with self.store.transaction():
            try:
                self.store.execute(
                    """UPDATE trainings SET title=?, category=?, validity_months=?, pass_threshold=?, mode=?,
                       material=?, questions_per_test=? WHERE id=?""",
                    (normalize_ws(merged["title"]), normalize_ws(merged["category"]), merged["validity_months"],
                     merged["pass_threshold"], merged["mode"], (merged["material"] or "").strip(),
                     merged["questions_per_test"], training_id),
                )
            except sqlite3.IntegrityError as exc:
                raise Conflict("Szkolenie o takim tytule już istnieje.") from exc
            if merged["material"] != current["material"]:
                reverted = self._reverify_quotes(training_id, merged["material"] or "")
        return self.get_training(training_id), reverted

    def _reverify_quotes(self, training_id: int, material: str) -> int:
        reverted = 0
        for q in self.store.all("SELECT id, source_quote, status FROM questions WHERE training_id=?", (training_id,)):
            ok = verify_quote(q["source_quote"], material)
            status = q["status"]
            if status == "approved" and not ok:
                status, reverted = "draft", reverted + 1
            self.store.execute("UPDATE questions SET quote_verified=?, status=? WHERE id=?", (int(ok), status, q["id"]))
        return reverted

    def list_rules(self, training_id: int | None = None) -> list[dict]:
        sql = """SELECT r.*, t.title AS training_title FROM assignment_rules r
                 JOIN trainings t ON t.id = r.training_id"""
        if training_id is not None:
            return self.store.all(sql + " WHERE r.training_id = ? ORDER BY r.id", (training_id,))
        return self.store.all(sql + " ORDER BY t.title, r.id")

    def create_rule(self, data: dict, today: date) -> tuple[dict, dict]:
        self.get_training(data["training_id"])
        department = normalize_ws(data.get("department") or "") or None
        position = normalize_ws(data.get("position") or "") or None
        with self.store.transaction():
            rule_id = self.store.insert(
                """INSERT INTO assignment_rules (training_id, department, position, due_days, created_at)
                   VALUES (?, ?, ?, ?, ?)""",
                (data["training_id"], department, position, data["due_days"], _now().isoformat()),
            )
            result = self.sync_assignments(today)
        rule = self.store.one("SELECT * FROM assignment_rules WHERE id=?", (rule_id,))
        return rule, result

    def delete_rule(self, rule_id: int, today: date) -> dict:
        if not self.store.one("SELECT id FROM assignment_rules WHERE id=?", (rule_id,)):
            raise NotFound("Nie znaleziono reguły.")
        with self.store.transaction():
            self.store.execute("DELETE FROM assignment_rules WHERE id=?", (rule_id,))
            return self.sync_assignments(today)

    # --- Synchronizacja przypisań --------------------------------------------------------

    def _rule_specs(self) -> list[RuleSpec]:
        return [RuleSpec(r["id"], r["training_id"], r["department"], r["position"], r["due_days"])
                for r in self.store.all("SELECT * FROM assignment_rules")]

    @staticmethod
    def _employee_spec(row: dict) -> EmployeeSpec:
        return EmployeeSpec(row["id"], row["department"], row["position"], date.fromisoformat(row["hire_date"]))

    def _latest_certificates(self) -> dict[tuple[int, int], dict]:
        latest: dict[tuple[int, int], dict] = {}
        for c in self.store.all("SELECT * FROM certificates ORDER BY issued_on, id"):
            latest[(c["employee_id"], c["training_id"])] = c
        return latest

    def _required_map(self, employees: list[dict]) -> dict[int, dict[int, RuleSpec | None]]:
        """Pracownik → wymagane szkolenia (z reguł albo z ręcznych przypisań)."""
        rules = self._rule_specs()
        manual: dict[int, set[int]] = {}
        for a in self.store.all("SELECT employee_id, training_id FROM assignments WHERE reason='manual' AND status!='cancelled'"):
            manual.setdefault(a["employee_id"], set()).add(a["training_id"])
        result: dict[int, dict[int, RuleSpec | None]] = {}
        for e in employees:
            req: dict[int, RuleSpec | None] = dict(required_trainings(rules, self._employee_spec(e)))
            for training_id in manual.get(e["id"], set()):
                req.setdefault(training_id, None)
            result[e["id"]] = req
        return result

    def sync_assignments(self, today: date, employee_ids: list[int] | None = None, *, on_hire: bool = False) -> dict:
        """Tworzy brakujące przypisania i odnowienia, anuluje niewymagane. Idempotentne."""
        employees = self.store.all("SELECT * FROM employees WHERE active = 1")
        if employee_ids is not None:
            employees = [e for e in employees if e["id"] in set(employee_ids)]
        required = self._required_map(employees)
        latest = self._latest_certificates()
        open_rows = self.store.all("SELECT * FROM assignments WHERE status='open'")
        open_by_pair = {(a["employee_id"], a["training_id"]): a for a in open_rows}
        created, renewals, cancelled = [], [], []
        with self.store.transaction():
            for e in employees:
                hire = date.fromisoformat(e["hire_date"])
                for training_id, rule in required[e["id"]].items():
                    pair = (e["id"], training_id)
                    if pair in open_by_pair:
                        continue
                    cert = latest.get(pair)
                    if cert is None:
                        if rule is None:
                            continue
                        due = initial_due_date(hire, rule.due_days, today, on_hire=on_hire)
                        reason = "new_hire" if on_hire else "rule"
                        created.append(self._insert_assignment(e["id"], training_id, rule.id, reason, today, due))
                    elif needs_renewal(_d(cert["valid_until"]), today):
                        due = _d(cert["valid_until"])
                        renewals.append(self._insert_assignment(
                            e["id"], training_id, rule.id if rule else None, "renewal", today, due))
                for (emp_id, training_id), a in open_by_pair.items():
                    if emp_id == e["id"] and training_id not in required[e["id"]] and a["reason"] != "manual":
                        self.store.execute("UPDATE assignments SET status='cancelled' WHERE id=?", (a["id"],))
                        cancelled.append(a)
        return {"created": created, "renewals": renewals, "cancelled": cancelled}

    def _insert_assignment(self, employee_id: int, training_id: int, rule_id: int | None,
                           reason: str, assigned_on: date, due: date) -> dict:
        assignment_id = self.store.insert(
            """INSERT INTO assignments (employee_id, training_id, rule_id, reason, assigned_on, due_date)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (employee_id, training_id, rule_id, reason, assigned_on.isoformat(), due.isoformat()),
        )
        return self.get_assignment(assignment_id)

    def get_assignment(self, assignment_id: int) -> dict:
        row = self.store.one(
            """SELECT a.*, t.title AS training_title, t.mode AS training_mode, e.full_name AS employee_name
               FROM assignments a JOIN trainings t ON t.id = a.training_id
               JOIN employees e ON e.id = a.employee_id WHERE a.id = ?""",
            (assignment_id,),
        )
        if not row:
            raise NotFound("Nie znaleziono przypisania.")
        row["reason_label"] = REASON_LABELS[row["reason"]]
        return row

    def create_manual_assignment(self, employee_id: int, training_id: int, due: date, today: date) -> dict:
        self.get_employee(employee_id)
        self.get_training(training_id)
        try:
            return self._insert_assignment(employee_id, training_id, None, "manual", today, due)
        except sqlite3.IntegrityError as exc:
            raise Conflict("Pracownik ma już otwarte przypisanie do tego szkolenia.") from exc

    def run_daily(self, today: date) -> dict:
        """Przegląd dzienny (np. z crona): przypisania, odnowienia, przypomnienia."""
        sync = self.sync_assignments(today)
        queued = self.queue_reminders(today)
        return {
            "today": today.isoformat(),
            "created": len(sync["created"]),
            "renewals": len(sync["renewals"]),
            "cancelled": len(sync["cancelled"]),
            "reminders_queued": queued,
        }

    # --- Przypomnienia (outbox) ------------------------------------------------------------

    def queue_reminders(self, today: date) -> int:
        """Dodaje do kolejki należne przypomnienia; ponowne uruchomienie nie dubluje wiadomości."""
        rows = self.store.all(
            """SELECT a.id, a.due_date, a.employee_id, e.full_name, e.email, e.department,
                      t.title, m.full_name AS manager_name, m.email AS manager_email
               FROM assignments a
               JOIN employees e ON e.id = a.employee_id AND e.active = 1
               JOIN trainings t ON t.id = a.training_id
               LEFT JOIN employees m ON m.id = e.manager_id AND m.active = 1
               WHERE a.status = 'open'"""
        )
        queued = 0
        created_at = _now().isoformat()
        with self.store.transaction():
            for r in rows:
                ctx = ReminderContext(
                    assignment_id=r["id"], employee_name=r["full_name"], employee_email=r["email"],
                    department=r["department"], training_title=r["title"], due_date=date.fromisoformat(r["due_date"]),
                    manager_name=r["manager_name"], manager_email=r["manager_email"],
                )
                for msg in plan_messages(ctx, today, self.settings.reminders):
                    cur = self.store.execute(
                        """INSERT OR IGNORE INTO outbox (dedup_key, kind, assignment_id, employee_id, recipient_name,
                           recipient_email, subject, body, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        (msg.dedup_key, msg.kind, r["id"], r["employee_id"], msg.recipient_name,
                         msg.recipient_email, msg.subject, msg.body, created_at),
                    )
                    queued += cur.rowcount
        return queued

    def list_outbox(self, status: str | None = None, limit: int = 500) -> list[dict]:
        sql = "SELECT * FROM outbox"
        params: tuple = ()
        if status:
            sql += " WHERE status = ?"
            params = (status,)
        return self.store.all(sql + " ORDER BY id DESC LIMIT ?", (*params, limit))

    def send_pending(self, now: datetime | None = None) -> int:
        """Wysyłka-atrapa: oznacza wiadomości jako wysłane (tu podłącz SMTP lub API poczty)."""
        stamp = (now or _now()).isoformat()
        with self.store.transaction():
            pending = self.store.all("SELECT id, recipient_email, subject FROM outbox WHERE status='pending'")
            for m in pending:
                log.info("Wysyłka (stub) do=%s temat=%s", m["recipient_email"], m["subject"])
            self.store.execute("UPDATE outbox SET status='sent', sent_at=? WHERE status='pending'", (stamp,))
        return len(pending)

    # --- Pytania -----------------------------------------------------------------------

    @staticmethod
    def _question_out(row: dict) -> dict:
        row = dict(row)
        row["options"] = json.loads(row["options"])
        row["quote_verified"] = bool(row["quote_verified"])
        row["status_label"] = QUESTION_STATUS_LABELS[row["status"]]
        return row

    def list_questions(self, training_id: int, status: str | None = None) -> list[dict]:
        self.get_training(training_id)
        sql = "SELECT * FROM questions WHERE training_id = ?"
        params: tuple = (training_id,)
        if status:
            sql += " AND status = ?"
            params += (status,)
        return [self._question_out(r) for r in self.store.all(sql + " ORDER BY id", params)]

    def get_question(self, question_id: int) -> dict:
        row = self.store.one("SELECT * FROM questions WHERE id = ?", (question_id,))
        if not row:
            raise NotFound("Nie znaleziono pytania.")
        return self._question_out(row)

    def generate_questions(self, training_id: int, count: int) -> dict:
        training = self.get_training(training_id)
        if not training["material"].strip():
            raise Conflict("Szkolenie nie ma materiału — dodaj treść, z której powstaną pytania.")
        existing = [r["text"] for r in self.store.all("SELECT text FROM questions WHERE training_id=?", (training_id,))]
        result = generate_questions(
            training["material"], count=count, llm=self.llm_factory(), title=training["title"], existing=existing,
        )
        created = []
        now = _now().isoformat()
        with self.store.transaction():
            for q in result.questions:
                verified = verify_quote(q.source_quote, training["material"])
                qid = self.store.insert(
                    """INSERT INTO questions (training_id, text, options, correct_index, explanation, source_quote,
                       quote_verified, status, origin, kind, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'draft', ?, ?, ?)""",
                    (training_id, q.text, json.dumps(q.options, ensure_ascii=False), q.correct_index, q.explanation,
                     q.source_quote, int(verified), result.source, q.kind, now),
                )
                created.append(self.get_question(qid))
        return {
            "source": result.source,
            "created": created,
            "rejected": [{"question": r.question, "reason": r.reason} for r in result.rejected],
            "warnings": result.warnings,
            "injection_suspected": result.injection_suspected,
        }

    def _validated_fields(self, training: dict, data: dict) -> dict:
        options = [normalize_ws(o) for o in data["options"]]
        text = normalize_ws(data["text"])
        try:
            check_question_structure(text, options, data["correct_index"])
        except QuestionValidationError as exc:
            raise InvalidInput(str(exc)) from exc
        quote = normalize_ws(data.get("source_quote") or "")
        return {
            "text": text, "options": json.dumps(options, ensure_ascii=False), "correct_index": data["correct_index"],
            "explanation": normalize_ws(data.get("explanation") or ""), "source_quote": quote,
            "quote_verified": int(verify_quote(quote, training["material"])),
        }

    def create_question(self, training_id: int, data: dict) -> dict:
        training = self.get_training(training_id)
        f = self._validated_fields(training, data)
        qid = self.store.insert(
            """INSERT INTO questions (training_id, text, options, correct_index, explanation, source_quote,
               quote_verified, status, origin, kind, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'draft', 'manual', 'reczne', ?)""",
            (training_id, f["text"], f["options"], f["correct_index"], f["explanation"], f["source_quote"],
             f["quote_verified"], _now().isoformat()),
        )
        return self.get_question(qid)

    def update_question(self, question_id: int, data: dict) -> dict:
        """Edycja przez HR. Każda zmiana cofa pytanie do szkicu — wymaga ponownego zatwierdzenia."""
        q = self.get_question(question_id)
        training = self.get_training(q["training_id"])
        f = self._validated_fields(training, data)
        self.store.execute(
            """UPDATE questions SET text=?, options=?, correct_index=?, explanation=?, source_quote=?,
               quote_verified=?, status='draft', reviewed_at=NULL, reviewed_by=NULL WHERE id=?""",
            (f["text"], f["options"], f["correct_index"], f["explanation"], f["source_quote"],
             f["quote_verified"], question_id),
        )
        return self.get_question(question_id)

    def approve_question(self, question_id: int, reviewer: str = "HR") -> dict:
        q = self.get_question(question_id)
        training = self.get_training(q["training_id"])
        try:
            check_question_structure(q["text"], q["options"], q["correct_index"])
        except QuestionValidationError as exc:
            raise Conflict(str(exc)) from exc
        if not verify_quote(q["source_quote"], training["material"]):
            raise Conflict("Cytat źródłowy nie występuje w materiale — popraw cytat przed zatwierdzeniem.")
        self.store.execute(
            "UPDATE questions SET status='approved', quote_verified=1, reviewed_at=?, reviewed_by=? WHERE id=?",
            (_now().isoformat(), normalize_ws(reviewer) or "HR", question_id),
        )
        return self.get_question(question_id)

    def reject_question(self, question_id: int, reviewer: str = "HR") -> dict:
        self.get_question(question_id)
        self.store.execute(
            "UPDATE questions SET status='rejected', reviewed_at=?, reviewed_by=? WHERE id=?",
            (_now().isoformat(), normalize_ws(reviewer) or "HR", question_id),
        )
        return self.get_question(question_id)

    def delete_question(self, question_id: int) -> None:
        self.get_question(question_id)
        self.store.execute("DELETE FROM questions WHERE id=?", (question_id,))

    # --- Test: podejścia i ocenianie ------------------------------------------------------

    def _open_assignment(self, assignment_id: int) -> tuple[dict, dict]:
        a = self.get_assignment(assignment_id)
        if a["status"] != "open":
            raise Conflict("To przypisanie jest już zamknięte.")
        return a, self.get_training(a["training_id"])

    def start_attempt(self, assignment_id: int, now: datetime | None = None, seed: int | None = None) -> dict:
        a, training = self._open_assignment(assignment_id)
        if training["mode"] != "elearning":
            raise Conflict("Szkolenie stacjonarne zalicza potwierdzenie prowadzącego — bez testu online.")
        pool = [
            QuestionSnapshot(id=r["id"], text=r["text"], options=tuple(json.loads(r["options"])),
                             correct_index=r["correct_index"], explanation=r["explanation"],
                             source_quote=r["source_quote"])
            for r in self.store.all("SELECT * FROM questions WHERE training_id=? AND status='approved'", (training["id"],))
        ]
        if not pool:
            raise Conflict("Test jest jeszcze niedostępny: HR nie zatwierdził żadnych pytań.")
        seed = secrets.randbelow(2**31) if seed is None else seed
        selected = select_questions(pool, seed, training["questions_per_test"])
        started = now or _now()
        attempt_id = self.store.insert(
            "INSERT INTO attempts (assignment_id, seed, questions, total, started_at) VALUES (?, ?, ?, ?, ?)",
            (assignment_id, seed, json.dumps([q.to_dict() for q in selected], ensure_ascii=False),
             len(selected), started.isoformat()),
        )
        items = build_quiz(selected, seed)
        return {
            "attempt_id": attempt_id,
            "assignment_id": assignment_id,
            "training": {"id": training["id"], "title": training["title"], "pass_threshold": training["pass_threshold"]},
            "started_at": started.isoformat(),
            "total": len(items),
            "questions": [{"id": it.question.id, "text": it.question.text, "options": it.options} for it in items],
        }

    def _attempt_items(self, attempt: dict):
        snapshot = [QuestionSnapshot.from_dict(q) for q in json.loads(attempt["questions"])]
        return build_quiz(snapshot, attempt["seed"])

    def submit_attempt(self, attempt_id: int, answers: dict[int, int], today: date,
                       now: datetime | None = None) -> dict:
        attempt = self.store.one("SELECT * FROM attempts WHERE id=?", (attempt_id,))
        if not attempt:
            raise NotFound("Nie znaleziono podejścia.")
        if attempt["submitted_at"]:
            raise Conflict("To podejście zostało już zakończone.")
        a, training = self._open_assignment(attempt["assignment_id"])
        items = self._attempt_items(attempt)
        try:
            result = grade(items, answers, training["pass_threshold"])
        except GradingError as exc:
            raise InvalidInput(str(exc)) from exc
        submitted = now or _now()
        duration = max(0, int((submitted - datetime.fromisoformat(attempt["started_at"])).total_seconds()))
        certificate = None
        with self.store.transaction():
            self.store.execute(
                """UPDATE attempts SET answers=?, score=?, percent=?, passed=?, submitted_at=?, duration_seconds=?
                   WHERE id=?""",
                (json.dumps({str(k): v for k, v in answers.items()}), result.score, result.percent,
                 int(result.passed), submitted.isoformat(), duration, attempt_id),
            )
            if result.passed:
                certificate = self._complete(a, training, today, "test", attempt_id=attempt_id)
        feedback = []
        for item, graded in zip(items, result.items, strict=True):
            entry = {"question_id": item.question.id, "text": item.question.text, "options": item.options,
                     "chosen_index": graded.chosen_display_index, "correct": graded.correct}
            if result.passed:
                # Po niezaliczeniu nie zdradzamy poprawnych odpowiedzi — kolejne podejście ma sens.
                entry.update(correct_index=graded.correct_display_index, explanation=item.question.explanation,
                             source_quote=item.question.source_quote)
            feedback.append(entry)
        return {
            "attempt_id": attempt_id, "score": result.score, "total": result.total, "percent": result.percent,
            "passed": result.passed, "pass_threshold": training["pass_threshold"], "duration_seconds": duration,
            "items": feedback, "certificate": certificate,
        }

    def get_attempt(self, attempt_id: int) -> dict:
        attempt = self.store.one("SELECT * FROM attempts WHERE id=?", (attempt_id,))
        if not attempt:
            raise NotFound("Nie znaleziono podejścia.")
        attempt["questions"] = json.loads(attempt["questions"])
        attempt["answers"] = json.loads(attempt["answers"]) if attempt["answers"] else None
        attempt["passed"] = None if attempt["passed"] is None else bool(attempt["passed"])
        return attempt

    def confirm_attendance(self, assignment_id: int, trainer: str, session_date: date, today: date) -> dict:
        """Szkolenie stacjonarne (np. instruktaż stanowiskowy) — zalicza je potwierdzenie prowadzącego."""
        a, training = self._open_assignment(assignment_id)
        if training["mode"] != "classroom":
            raise Conflict("Potwierdzenie prowadzącego dotyczy tylko szkoleń stacjonarnych.")
        if session_date > today:
            raise InvalidInput("Data szkolenia nie może być z przyszłości.")
        trainer = normalize_ws(trainer)
        if len(trainer) < 3:
            raise InvalidInput("Podaj imię i nazwisko prowadzącego.")
        with self.store.transaction():
            return self._complete(a, training, session_date, "confirmation", confirmed_by=trainer)

    def record_external_completion(self, assignment_id: int, completed_on: date, note: str, today: date) -> dict:
        """Wpis ukończenia spoza systemu (np. przeniesienie z papierowej ewidencji)."""
        a, training = self._open_assignment(assignment_id)
        if completed_on > today:
            raise InvalidInput("Data ukończenia nie może być z przyszłości.")
        with self.store.transaction():
            return self._complete(a, training, completed_on, "external", note=normalize_ws(note))

    def _complete(self, assignment: dict, training: dict, completed_on: date, method: str, *,
                  attempt_id: int | None = None, confirmed_by: str | None = None, note: str | None = None) -> dict:
        self.store.execute(
            "UPDATE assignments SET status='completed', completed_on=? WHERE id=?",
            (completed_on.isoformat(), assignment["id"]),
        )
        valid_until = compute_valid_until(completed_on, training["validity_months"])
        year = completed_on.year
        seq = self.store.scalar("SELECT COUNT(*) FROM certificates WHERE number LIKE ?", (f"SZK/{year}/%",)) + 1
        while True:
            code = normalize_code("".join(secrets.choice(CODE_ALPHABET) for _ in range(12)))
            if not self.store.one("SELECT id FROM certificates WHERE verification_code=?", (code,)):
                break
        cert_id = self.store.insert(
            """INSERT INTO certificates (number, assignment_id, employee_id, training_id, attempt_id, method,
               issued_on, valid_until, verification_code, confirmed_by, note)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (f"SZK/{year}/{seq:04d}", assignment["id"], assignment["employee_id"], training["id"], attempt_id,
             method, completed_on.isoformat(), _iso(valid_until), code, confirmed_by, note),
        )
        return self.get_certificate(cert_id)

    # --- Zaświadczenia -------------------------------------------------------------------

    def get_certificate(self, certificate_id: int) -> dict:
        row = self.store.one(
            """SELECT c.*, e.full_name, e.department, e.position, t.title AS training_title, t.category,
                      t.pass_threshold, at.percent AS attempt_percent
               FROM certificates c JOIN employees e ON e.id = c.employee_id
               JOIN trainings t ON t.id = c.training_id
               LEFT JOIN attempts at ON at.id = c.attempt_id WHERE c.id = ?""",
            (certificate_id,),
        )
        if not row:
            raise NotFound("Nie znaleziono zaświadczenia.")
        row["method_label"] = METHOD_LABELS[row["method"]]
        return row

    def verify_certificate(self, code: str, today: date) -> dict:
        """Publiczna weryfikacja kodu — zwraca tylko dane potrzebne do potwierdzenia autentyczności."""
        normalized = normalize_code(code)
        row = self.store.one("SELECT id FROM certificates WHERE verification_code = ?", (normalized,))
        if not row:
            raise NotFound("Nie znaleziono zaświadczenia o podanym kodzie.")
        c = self.get_certificate(row["id"])
        valid_until = _d(c["valid_until"])
        is_valid = valid_until is None or valid_until >= today
        return {
            "code": normalized, "number": c["number"], "full_name": c["full_name"],
            "training_title": c["training_title"], "issued_on": c["issued_on"], "valid_until": c["valid_until"],
            "is_valid": is_valid, "status_label": "ważne" if is_valid else "wygasło",
        }

    def list_certificates(self, employee_id: int | None = None) -> list[dict]:
        sql = """SELECT c.id, c.number, c.issued_on, c.valid_until, c.method, c.verification_code,
                        e.full_name, t.title AS training_title
                 FROM certificates c JOIN employees e ON e.id = c.employee_id JOIN trainings t ON t.id = c.training_id"""
        if employee_id is not None:
            return self.store.all(sql + " WHERE c.employee_id = ? ORDER BY c.issued_on DESC", (employee_id,))
        return self.store.all(sql + " ORDER BY c.issued_on DESC")

    # --- Zgodność: macierz, pulpit, widok pracownika ---------------------------------------

    def _cells(self, today: date, employees: list[dict]) -> dict[tuple[int, int], dict]:
        trainings = self.store.all("SELECT * FROM trainings")
        required = self._required_map(employees)
        latest = self._latest_certificates()
        open_by_pair = {(a["employee_id"], a["training_id"]): a
                        for a in self.store.all("SELECT * FROM assignments WHERE status='open'")}
        cells: dict[tuple[int, int], dict] = {}
        for e in employees:
            for t in trainings:
                pair = (e["id"], t["id"])
                cert, open_a = latest.get(pair), open_by_pair.get(pair)
                is_required = t["id"] in required[e["id"]] or open_a is not None
                if not is_required:
                    status = "not_applicable"
                elif cert:
                    status = compliance_status(today=today, completed=True, valid_until=_d(cert["valid_until"]))
                else:
                    status = compliance_status(today=today, completed=False,
                                               due_date=_d(open_a["due_date"]) if open_a else None)
                cells[pair] = {
                    "status": status,
                    "status_label": STATUS_LABELS[status],
                    "required": is_required,
                    "completed_on": cert["issued_on"] if cert else None,
                    "valid_until": cert["valid_until"] if cert else None,
                    "certificate_id": cert["id"] if cert else None,
                    "certificate_number": cert["number"] if cert else None,
                    "due_date": open_a["due_date"] if open_a else None,
                    "assignment_id": open_a["id"] if open_a else None,
                }
        return cells

    def compliance_matrix(self, today: date, department: str | None = None) -> dict:
        employees = self.list_employees(department)
        trainings = self.store.all("SELECT id, title, category, validity_months, mode FROM trainings ORDER BY category, title")
        cells = self._cells(today, employees)
        rows = []
        for e in employees:
            row_cells = {str(t["id"]): cells[(e["id"], t["id"])] for t in trainings}
            req = [c for c in row_cells.values() if c["required"]]
            ok = sum(c["status"] in COMPLIANT_STATUSES for c in req)
            rows.append({
                "employee": {k: e[k] for k in ("id", "full_name", "email", "department", "position", "hire_date")},
                "cells": row_cells,
                "compliance_percent": round(100 * ok / len(req)) if req else 100,
            })
        return {"today": today.isoformat(), "department": department, "company": self.settings.company_name,
                "trainings": trainings, "rows": rows}

    def dashboard(self, today: date, department: str | None = None) -> dict:
        employees = self.list_employees(department)
        trainings = {t["id"]: t for t in self.store.all("SELECT id, title, category FROM trainings")}
        cells = self._cells(today, employees)
        by_emp = {e["id"]: e for e in employees}
        counts = {s: 0 for s in ("valid", "expiring", "overdue", "incomplete")}
        per_training: dict[int, dict] = {}
        per_department: dict[str, dict] = {}
        overdue, expiring = [], []
        for (emp_id, training_id), c in cells.items():
            if not c["required"]:
                continue
            e, t = by_emp[emp_id], trainings[training_id]
            counts[c["status"]] += 1
            ok = c["status"] in COMPLIANT_STATUSES
            for bucket, key, label in ((per_training, training_id, t["title"]), (per_department, e["department"], e["department"])):
                b = bucket.setdefault(key, {"label": label, "total": 0, "compliant": 0})
                b["total"] += 1
                b["compliant"] += ok
            base = {"employee_id": emp_id, "full_name": e["full_name"], "department": e["department"],
                    "training_id": training_id, "training_title": t["title"], "assignment_id": c["assignment_id"]}
            if c["status"] == "overdue":
                since = _d(c["valid_until"]) if c["completed_on"] else _d(c["due_date"])
                reason = "ważność minęła" if c["completed_on"] else "termin minął"
                overdue.append({**base, "since": _iso(since), "reason": reason, "days": days_overdue(since, today)})
            elif c["status"] == "expiring":
                expiring.append({**base, "valid_until": c["valid_until"],
                                 "days_left": (_d(c["valid_until"]) - today).days})
        total = sum(counts.values())
        compliant = counts["valid"] + counts["expiring"]

        def summarize(bucket: dict) -> list[dict]:
            return sorted(
                ({**b, "percent": round(100 * b["compliant"] / b["total"]) if b["total"] else 100} for b in bucket.values()),
                key=lambda b: (b["percent"], b["label"]),
            )

        return {
            "today": today.isoformat(),
            "department": department,
            "compliance_percent": round(100 * compliant / total) if total else 100,
            "total_required": total,
            "counts": counts,
            "by_training": summarize(per_training),
            "by_department": summarize(per_department),
            "overdue": sorted(overdue, key=lambda x: -x["days"]),
            "expiring": sorted(expiring, key=lambda x: x["days_left"]),
            "pending_messages": self.store.scalar("SELECT COUNT(*) FROM outbox WHERE status='pending'"),
            "drafts_to_review": self.store.scalar("SELECT COUNT(*) FROM questions WHERE status='draft'"),
        }

    def employee_trainings(self, employee_id: int, today: date) -> dict:
        """Widok „Moje szkolenia”: co jest wymagane, do kiedy, co można zrobić teraz."""
        employee = self.get_employee(employee_id)
        cells = self._cells(today, [employee])
        trainings = {t["id"]: t for t in self.store.all("SELECT * FROM trainings")}
        approved = {r["training_id"]: r["n"] for r in self.store.all(
            "SELECT training_id, COUNT(*) AS n FROM questions WHERE status='approved' GROUP BY training_id")}
        items = []
        for (_, training_id), c in cells.items():
            if not c["required"]:
                continue
            t = trainings[training_id]
            attempts = []
            if c["assignment_id"]:
                attempts = self.store.all(
                    """SELECT id, percent, passed, submitted_at FROM attempts
                       WHERE assignment_id=? AND submitted_at IS NOT NULL ORDER BY id""",
                    (c["assignment_id"],),
                )
            can_test = bool(c["assignment_id"]) and t["mode"] == "elearning" and approved.get(training_id, 0) > 0
            items.append({
                **c,
                "training": {"id": t["id"], "title": t["title"], "category": t["category"], "mode": t["mode"],
                             "mode_label": MODE_LABELS[t["mode"]], "validity_months": t["validity_months"],
                             "pass_threshold": t["pass_threshold"]},
                "approved_questions": approved.get(training_id, 0),
                "can_take_test": can_test,
                "needs_confirmation": bool(c["assignment_id"]) and t["mode"] == "classroom",
                "attempts": [{**a, "passed": bool(a["passed"])} for a in attempts],
            })
        items.sort(key=lambda x: (_STATUS_ORDER[x["status"]], x["due_date"] or "9999", x["training"]["title"]))
        return {"employee": employee, "today": today.isoformat(), "items": items,
                "certificates": self.list_certificates(employee_id)}

    def stats(self) -> dict[str, Any]:
        return {
            "employees": self.store.scalar("SELECT COUNT(*) FROM employees WHERE active=1"),
            "trainings": self.store.scalar("SELECT COUNT(*) FROM trainings"),
        }
