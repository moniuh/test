"""Schemat SQLite i cienka warstwa dostępu (jedno połączenie chronione blokadą)."""
from __future__ import annotations

import sqlite3
import threading
from contextlib import contextmanager
from typing import Any, Iterator

SCHEMA = """
CREATE TABLE IF NOT EXISTS employees (
    id          INTEGER PRIMARY KEY,
    full_name   TEXT NOT NULL,
    email       TEXT NOT NULL UNIQUE,
    department  TEXT NOT NULL,
    position    TEXT NOT NULL,
    hire_date   TEXT NOT NULL,
    manager_id  INTEGER REFERENCES employees(id) ON DELETE SET NULL,
    active      INTEGER NOT NULL DEFAULT 1,
    created_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS trainings (
    id                  INTEGER PRIMARY KEY,
    title               TEXT NOT NULL UNIQUE,
    category            TEXT NOT NULL,
    validity_months     INTEGER,                 -- NULL = bezterminowo
    pass_threshold      INTEGER NOT NULL DEFAULT 80,
    mode                TEXT NOT NULL CHECK (mode IN ('elearning', 'classroom')),
    material            TEXT NOT NULL DEFAULT '',
    questions_per_test  INTEGER NOT NULL DEFAULT 10,
    created_at          TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS assignment_rules (
    id           INTEGER PRIMARY KEY,
    training_id  INTEGER NOT NULL REFERENCES trainings(id) ON DELETE CASCADE,
    department   TEXT,                           -- NULL = każdy dział
    position     TEXT,                           -- NULL = każde stanowisko
    due_days     INTEGER NOT NULL,
    created_at   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS assignments (
    id            INTEGER PRIMARY KEY,
    employee_id   INTEGER NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    training_id   INTEGER NOT NULL REFERENCES trainings(id) ON DELETE CASCADE,
    rule_id       INTEGER REFERENCES assignment_rules(id) ON DELETE SET NULL,
    reason        TEXT NOT NULL CHECK (reason IN ('new_hire', 'rule', 'renewal', 'manual')),
    assigned_on   TEXT NOT NULL,
    due_date      TEXT NOT NULL,
    status        TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'completed', 'cancelled')),
    completed_on  TEXT
);
-- Najwyżej jedno otwarte przypisanie na parę pracownik × szkolenie (idempotentna synchronizacja).
CREATE UNIQUE INDEX IF NOT EXISTS uq_open_assignment
    ON assignments(employee_id, training_id) WHERE status = 'open';

CREATE TABLE IF NOT EXISTS questions (
    id              INTEGER PRIMARY KEY,
    training_id     INTEGER NOT NULL REFERENCES trainings(id) ON DELETE CASCADE,
    text            TEXT NOT NULL,
    options         TEXT NOT NULL,               -- JSON: lista 4 odpowiedzi
    correct_index   INTEGER NOT NULL,
    explanation     TEXT NOT NULL DEFAULT '',
    source_quote    TEXT NOT NULL DEFAULT '',
    quote_verified  INTEGER NOT NULL DEFAULT 0,
    status          TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'approved', 'rejected')),
    origin          TEXT NOT NULL CHECK (origin IN ('ai', 'offline', 'manual')),
    kind            TEXT,
    created_at      TEXT NOT NULL,
    reviewed_at     TEXT,
    reviewed_by     TEXT
);

CREATE TABLE IF NOT EXISTS attempts (
    id                INTEGER PRIMARY KEY,
    assignment_id     INTEGER NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    seed              INTEGER NOT NULL,
    questions         TEXT NOT NULL,             -- JSON: migawka pytań z chwili startu
    answers           TEXT,                      -- JSON: id pytania → wybrana pozycja
    score             INTEGER,
    total             INTEGER NOT NULL,
    percent           INTEGER,
    passed            INTEGER,
    started_at        TEXT NOT NULL,
    submitted_at      TEXT,
    duration_seconds  INTEGER
);

CREATE TABLE IF NOT EXISTS certificates (
    id                 INTEGER PRIMARY KEY,
    number             TEXT NOT NULL UNIQUE,
    assignment_id      INTEGER NOT NULL UNIQUE REFERENCES assignments(id) ON DELETE CASCADE,
    employee_id        INTEGER NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    training_id        INTEGER NOT NULL REFERENCES trainings(id) ON DELETE CASCADE,
    attempt_id         INTEGER REFERENCES attempts(id) ON DELETE SET NULL,
    method             TEXT NOT NULL CHECK (method IN ('test', 'confirmation', 'external')),
    issued_on          TEXT NOT NULL,
    valid_until        TEXT,
    verification_code  TEXT NOT NULL UNIQUE,
    confirmed_by       TEXT,
    note               TEXT
);

CREATE TABLE IF NOT EXISTS outbox (
    id               INTEGER PRIMARY KEY,
    dedup_key        TEXT NOT NULL UNIQUE,
    kind             TEXT NOT NULL CHECK (kind IN ('reminder', 'overdue', 'escalation')),
    assignment_id    INTEGER REFERENCES assignments(id) ON DELETE CASCADE,
    employee_id      INTEGER REFERENCES employees(id) ON DELETE CASCADE,
    recipient_name   TEXT NOT NULL,
    recipient_email  TEXT NOT NULL,
    subject          TEXT NOT NULL,
    body             TEXT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'sent')),
    created_at       TEXT NOT NULL,
    sent_at          TEXT
);
"""


class Store:
    """Połączenie SQLite współdzielone przez wątki serwera; zapisy serializuje blokada."""

    def __init__(self, path: str = ":memory:") -> None:
        self.path = path
        self._conn = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA foreign_keys = ON")
        self._lock = threading.RLock()
        self._depth = 0
        with self._lock:
            self._conn.executescript(SCHEMA)

    @contextmanager
    def transaction(self) -> Iterator["Store"]:
        """Transakcja (zagnieżdżone wywołania dołączają do zewnętrznej)."""
        with self._lock:
            outer = self._depth == 0
            if outer:
                self._conn.execute("BEGIN IMMEDIATE")
            self._depth += 1
            try:
                yield self
            except BaseException:
                self._depth -= 1
                if outer:
                    self._conn.execute("ROLLBACK")
                raise
            self._depth -= 1
            if outer:
                self._conn.execute("COMMIT")

    def execute(self, sql: str, params: tuple | dict = ()) -> sqlite3.Cursor:
        with self._lock:
            return self._conn.execute(sql, params)

    def insert(self, sql: str, params: tuple | dict = ()) -> int:
        with self._lock:
            return int(self._conn.execute(sql, params).lastrowid)

    def all(self, sql: str, params: tuple | dict = ()) -> list[dict[str, Any]]:
        with self._lock:
            return [dict(r) for r in self._conn.execute(sql, params).fetchall()]

    def one(self, sql: str, params: tuple | dict = ()) -> dict[str, Any] | None:
        with self._lock:
            row = self._conn.execute(sql, params).fetchone()
            return dict(row) if row else None

    def scalar(self, sql: str, params: tuple | dict = ()) -> Any:
        with self._lock:
            row = self._conn.execute(sql, params).fetchone()
            return row[0] if row else None

    def close(self) -> None:
        with self._lock:
            self._conn.close()
