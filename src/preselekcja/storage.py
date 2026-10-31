"""Trwałość w SQLite: oferty, kandydaci, oceny, decyzje i dziennik audytowy (append-only).

Dziennik audytowy (art. 12 AI Act) jest chroniony na dwa sposoby: wyzwalacze SQLite blokują
UPDATE/DELETE, a każdy wpis zawiera skrót poprzedniego (łańcuch SHA-256), więc ręczna
modyfikacja pliku bazy jest wykrywalna przez `verify_audit_chain`.
Dane demograficzne są w osobnym pliku bazy (`DemographicsStore`) — patrz bias.py.
"""
from __future__ import annotations

import hashlib
import json
import sqlite3
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterator

from .models import Decision, Evaluation, Job, JobCreate

GENESIS_HASH = "0" * 64

_SCHEMA = """
CREATE TABLE IF NOT EXISTS jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    requirements_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    created_by TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS candidates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id INTEGER NOT NULL REFERENCES jobs(id),
    source_format TEXT NOT NULL,
    input_hash TEXT NOT NULL,
    original_text TEXT NOT NULL,
    anonymized_text TEXT NOT NULL,
    identity_json TEXT NOT NULL,
    anonymization_json TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'new',
    revealed INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS evaluations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    candidate_id INTEGER NOT NULL REFERENCES candidates(id),
    source TEXT NOT NULL,
    score REAL NOT NULL,
    recommendation TEXT NOT NULL,
    result_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS decisions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    candidate_id INTEGER NOT NULL REFERENCES candidates(id),
    status TEXT NOT NULL,
    reason TEXT NOT NULL,
    actor TEXT NOT NULL,
    ai_recommendation TEXT,
    evaluation_id INTEGER,
    is_override INTEGER NOT NULL,
    viewed_before INTEGER NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ts TEXT NOT NULL,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    entity_type TEXT,
    entity_id INTEGER,
    source TEXT NOT NULL,
    input_hash TEXT,
    details_json TEXT NOT NULL,
    prev_hash TEXT NOT NULL,
    entry_hash TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_candidates_job ON candidates(job_id);
CREATE INDEX IF NOT EXISTS idx_evaluations_candidate ON evaluations(candidate_id);
CREATE INDEX IF NOT EXISTS idx_decisions_candidate ON decisions(candidate_id);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE TRIGGER IF NOT EXISTS audit_no_update BEFORE UPDATE ON audit_log
BEGIN SELECT RAISE(ABORT, 'Dziennik audytowy jest tylko do dopisywania'); END;
CREATE TRIGGER IF NOT EXISTS audit_no_delete BEFORE DELETE ON audit_log
BEGIN SELECT RAISE(ABORT, 'Dziennik audytowy jest tylko do dopisywania'); END;
CREATE TRIGGER IF NOT EXISTS decisions_no_update BEFORE UPDATE ON decisions
BEGIN SELECT RAISE(ABORT, 'Historia decyzji jest tylko do dopisywania'); END;
CREATE TRIGGER IF NOT EXISTS decisions_no_delete BEFORE DELETE ON decisions
BEGIN SELECT RAISE(ABORT, 'Historia decyzji jest tylko do dopisywania'); END;
"""

_DEMOGRAPHICS_SCHEMA = """
CREATE TABLE IF NOT EXISTS demographics (
    candidate_id INTEGER PRIMARY KEY,
    gender TEXT NOT NULL,
    age_group TEXT NOT NULL,
    disability TEXT NOT NULL,
    consent_at TEXT NOT NULL
);
"""


def utcnow() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="microseconds")


def sha256_text(text: str | bytes) -> str:
    data = text.encode("utf-8") if isinstance(text, str) else text
    return hashlib.sha256(data).hexdigest()


def _entry_hash(prev_hash: str, fields: dict[str, Any]) -> str:
    canonical = json.dumps(fields, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return sha256_text(prev_hash + canonical)


class _Db:
    def __init__(self, path: str | Path, schema: str) -> None:
        self.path = str(path)
        self._lock = threading.RLock()
        self._conn = sqlite3.connect(self.path, check_same_thread=False, isolation_level=None)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA foreign_keys = ON")
        self._conn.executescript(schema)

    def query(self, sql: str, params: tuple | list = ()) -> list[sqlite3.Row]:
        with self._lock:
            return self._conn.execute(sql, params).fetchall()

    def execute(self, sql: str, params: tuple | list = ()) -> int:
        with self._lock:
            cur = self._conn.execute(sql, params)
            return int(cur.lastrowid or 0)

    def transaction(self) -> "_Tx":
        return _Tx(self)

    def close(self) -> None:
        with self._lock:
            self._conn.close()


class _Tx:
    def __init__(self, db: _Db) -> None:
        self.db = db

    def __enter__(self) -> sqlite3.Connection:
        self.db._lock.acquire()
        self.db._conn.execute("BEGIN IMMEDIATE")
        return self.db._conn

    def __exit__(self, exc_type, exc, tb) -> None:
        try:
            self.db._conn.execute("ROLLBACK" if exc_type else "COMMIT")
        finally:
            self.db._lock.release()


class Store:
    """Repozytorium danych procesu rekrutacji (bez danych demograficznych)."""

    def __init__(self, path: str | Path) -> None:
        self.db = _Db(path, _SCHEMA)

    # --- audyt -----------------------------------------------------------------
    def append_audit(
        self,
        conn: sqlite3.Connection | None = None,
        *,
        actor: str,
        action: str,
        source: str,
        entity_type: str | None = None,
        entity_id: int | None = None,
        input_hash: str | None = None,
        details: dict[str, Any] | None = None,
    ) -> int:
        if conn is None:
            with self.db.transaction() as tx:
                return self.append_audit(
                    tx, actor=actor, action=action, source=source, entity_type=entity_type,
                    entity_id=entity_id, input_hash=input_hash, details=details,
                )
        row = conn.execute("SELECT entry_hash FROM audit_log ORDER BY id DESC LIMIT 1").fetchone()
        prev = row["entry_hash"] if row else GENESIS_HASH
        fields = {
            "ts": utcnow(),
            "actor": actor,
            "action": action,
            "entity_type": entity_type,
            "entity_id": entity_id,
            "source": source,
            "input_hash": input_hash,
            "details": details or {},
        }
        entry = _entry_hash(prev, fields)
        cur = conn.execute(
            "INSERT INTO audit_log (ts, actor, action, entity_type, entity_id, source, input_hash, details_json,"
            " prev_hash, entry_hash) VALUES (?,?,?,?,?,?,?,?,?,?)",
            (
                fields["ts"], actor, action, entity_type, entity_id, source, input_hash,
                json.dumps(fields["details"], ensure_ascii=False, sort_keys=True), prev, entry,
            ),
        )
        return int(cur.lastrowid)

    def audit_entries(
        self, *, limit: int = 200, offset: int = 0, entity_type: str | None = None, entity_id: int | None = None,
        action: str | None = None,
    ) -> list[dict[str, Any]]:
        where, params = [], []
        if entity_type:
            where.append("entity_type = ?")
            params.append(entity_type)
        if entity_id is not None:
            where.append("entity_id = ?")
            params.append(entity_id)
        if action:
            where.append("action = ?")
            params.append(action)
        sql = "SELECT * FROM audit_log" + (" WHERE " + " AND ".join(where) if where else "")
        sql += " ORDER BY id DESC LIMIT ? OFFSET ?"
        rows = self.db.query(sql, [*params, limit, offset])
        return [self._audit_row(r) for r in rows]

    def audit_count(self) -> int:
        return int(self.db.query("SELECT COUNT(*) AS n FROM audit_log")[0]["n"])

    @staticmethod
    def _audit_row(r: sqlite3.Row) -> dict[str, Any]:
        return {
            "id": r["id"],
            "ts": r["ts"],
            "actor": r["actor"],
            "action": r["action"],
            "entity_type": r["entity_type"],
            "entity_id": r["entity_id"],
            "source": r["source"],
            "input_hash": r["input_hash"],
            "details": json.loads(r["details_json"]),
            "prev_hash": r["prev_hash"],
            "entry_hash": r["entry_hash"],
        }

    def iter_audit(self) -> Iterator[dict[str, Any]]:
        for r in self.db.query("SELECT * FROM audit_log ORDER BY id ASC"):
            yield self._audit_row(r)

    def verify_audit_chain(self) -> dict[str, Any]:
        prev = GENESIS_HASH
        checked = 0
        for e in self.iter_audit():
            fields = {k: e[k] for k in ("ts", "actor", "action", "entity_type", "entity_id", "source", "input_hash")}
            fields["details"] = e["details"]
            if e["prev_hash"] != prev or _entry_hash(prev, fields) != e["entry_hash"]:
                return {"ok": False, "checked": checked, "broken_at": e["id"],
                        "message": f"Łańcuch skrótów przerwany przy wpisie #{e['id']} — dziennik mógł zostać zmodyfikowany."}
            prev = e["entry_hash"]
            checked += 1
        return {"ok": True, "checked": checked, "broken_at": None,
                "message": f"Integralność potwierdzona ({checked} wpisów)."}

    # --- oferty ----------------------------------------------------------------
    def create_job(self, conn: sqlite3.Connection, job: JobCreate, actor: str) -> Job:
        now = utcnow()
        reqs = json.dumps([r.model_dump() for r in job.requirements], ensure_ascii=False)
        cur = conn.execute(
            "INSERT INTO jobs (title, description, requirements_json, created_at, created_by) VALUES (?,?,?,?,?)",
            (job.title, job.description, reqs, now, actor),
        )
        fields = job.model_dump(include={"title", "description", "requirements"})
        return Job(id=int(cur.lastrowid), created_at=now, created_by=actor, **fields)

    @staticmethod
    def _job(r: sqlite3.Row) -> Job:
        return Job(
            id=r["id"], title=r["title"], description=r["description"],
            requirements=json.loads(r["requirements_json"]), created_at=r["created_at"], created_by=r["created_by"],
        )

    def get_job(self, job_id: int) -> Job | None:
        rows = self.db.query("SELECT * FROM jobs WHERE id = ?", (job_id,))
        return self._job(rows[0]) if rows else None

    def list_jobs(self) -> list[dict[str, Any]]:
        rows = self.db.query(
            "SELECT j.*, (SELECT COUNT(*) FROM candidates c WHERE c.job_id = j.id) AS n FROM jobs j ORDER BY j.id DESC"
        )
        return [{**self._job(r).model_dump(), "candidates": r["n"]} for r in rows]

    # --- kandydaci -------------------------------------------------------------
    def create_candidate(
        self, conn: sqlite3.Connection, *, job_id: int, source_format: str, input_hash: str, original_text: str,
        anonymized_text: str, identity: dict, anonymization: dict,
    ) -> int:
        cur = conn.execute(
            "INSERT INTO candidates (job_id, source_format, input_hash, original_text, anonymized_text, identity_json,"
            " anonymization_json, created_at) VALUES (?,?,?,?,?,?,?,?)",
            (job_id, source_format, input_hash, original_text, anonymized_text,
             json.dumps(identity, ensure_ascii=False), json.dumps(anonymization, ensure_ascii=False), utcnow()),
        )
        return int(cur.lastrowid)

    def get_candidate(self, candidate_id: int) -> sqlite3.Row | None:
        rows = self.db.query("SELECT * FROM candidates WHERE id = ?", (candidate_id,))
        return rows[0] if rows else None

    def list_candidates(self, job_id: int | None = None) -> list[sqlite3.Row]:
        if job_id is None:
            return self.db.query("SELECT * FROM candidates ORDER BY id")
        return self.db.query("SELECT * FROM candidates WHERE job_id = ? ORDER BY id", (job_id,))

    def set_status(self, conn: sqlite3.Connection, candidate_id: int, status: str) -> None:
        conn.execute("UPDATE candidates SET status = ? WHERE id = ?", (status, candidate_id))

    def mark_revealed(self, conn: sqlite3.Connection, candidate_id: int) -> None:
        conn.execute("UPDATE candidates SET revealed = 1 WHERE id = ?", (candidate_id,))

    # --- oceny -----------------------------------------------------------------
    def add_evaluation(self, conn: sqlite3.Connection, candidate_id: int, ev: Evaluation) -> int:
        cur = conn.execute(
            "INSERT INTO evaluations (candidate_id, source, score, recommendation, result_json, created_at)"
            " VALUES (?,?,?,?,?,?)",
            (candidate_id, ev.source, ev.score, ev.recommendation, ev.model_dump_json(), ev.created_at or utcnow()),
        )
        return int(cur.lastrowid)

    def latest_evaluation(self, candidate_id: int) -> tuple[int, Evaluation] | None:
        rows = self.db.query(
            "SELECT id, result_json FROM evaluations WHERE candidate_id = ? ORDER BY id DESC LIMIT 1", (candidate_id,)
        )
        if not rows:
            return None
        return rows[0]["id"], Evaluation.model_validate_json(rows[0]["result_json"])

    def evaluation_count(self, candidate_id: int) -> int:
        return int(self.db.query("SELECT COUNT(*) AS n FROM evaluations WHERE candidate_id = ?", (candidate_id,))[0]["n"])

    # --- decyzje ---------------------------------------------------------------
    def add_decision(
        self, conn: sqlite3.Connection, *, candidate_id: int, status: str, reason: str, actor: str,
        ai_recommendation: str | None, evaluation_id: int | None, is_override: bool, viewed_before: bool,
    ) -> Decision:
        now = utcnow()
        cur = conn.execute(
            "INSERT INTO decisions (candidate_id, status, reason, actor, ai_recommendation, evaluation_id, is_override,"
            " viewed_before, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
            (candidate_id, status, reason, actor, ai_recommendation, evaluation_id, int(is_override),
             int(viewed_before), now),
        )
        return Decision(
            id=int(cur.lastrowid), candidate_id=candidate_id, status=status, reason=reason, actor=actor,
            ai_recommendation=ai_recommendation, is_override=is_override, viewed_before=viewed_before, created_at=now,
        )

    @staticmethod
    def _decision(r: sqlite3.Row) -> Decision:
        return Decision(
            id=r["id"], candidate_id=r["candidate_id"], status=r["status"], reason=r["reason"], actor=r["actor"],
            ai_recommendation=r["ai_recommendation"], is_override=bool(r["is_override"]),
            viewed_before=bool(r["viewed_before"]), created_at=r["created_at"],
        )

    def decisions_for(self, candidate_id: int) -> list[Decision]:
        rows = self.db.query("SELECT * FROM decisions WHERE candidate_id = ? ORDER BY id", (candidate_id,))
        return [self._decision(r) for r in rows]

    def all_decisions(self, job_id: int | None = None) -> list[Decision]:
        if job_id is None:
            rows = self.db.query("SELECT * FROM decisions ORDER BY id")
        else:
            rows = self.db.query(
                "SELECT d.* FROM decisions d JOIN candidates c ON c.id = d.candidate_id WHERE c.job_id = ? ORDER BY d.id",
                (job_id,),
            )
        return [self._decision(r) for r in rows]

    def has_viewed(self, candidate_id: int, actor: str) -> bool:
        rows = self.db.query(
            "SELECT 1 FROM audit_log WHERE action = 'candidate_viewed' AND entity_type = 'candidate'"
            " AND entity_id = ? AND actor = ? LIMIT 1",
            (candidate_id, actor),
        )
        return bool(rows)


class DemographicsStore:
    """Osobny plik bazy na dobrowolne dane demograficzne. Nigdy nie trafiają do oceny ani do modelu."""

    def __init__(self, path: str | Path) -> None:
        self.db = _Db(path, _DEMOGRAPHICS_SCHEMA)

    def upsert(self, candidate_id: int, gender: str, age_group: str, disability: str) -> None:
        self.db.execute(
            "INSERT INTO demographics (candidate_id, gender, age_group, disability, consent_at) VALUES (?,?,?,?,?)"
            " ON CONFLICT(candidate_id) DO UPDATE SET gender=excluded.gender, age_group=excluded.age_group,"
            " disability=excluded.disability, consent_at=excluded.consent_at",
            (candidate_id, gender, age_group, disability, utcnow()),
        )

    def delete(self, candidate_id: int) -> bool:
        before = len(self.db.query("SELECT 1 FROM demographics WHERE candidate_id = ?", (candidate_id,)))
        self.db.execute("DELETE FROM demographics WHERE candidate_id = ?", (candidate_id,))
        return bool(before)

    def all(self) -> dict[int, dict[str, str]]:
        rows = self.db.query("SELECT * FROM demographics")
        return {
            r["candidate_id"]: {"gender": r["gender"], "age_group": r["age_group"], "disability": r["disability"]}
            for r in rows
        }
