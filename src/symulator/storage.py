"""Trwałość sesji w SQLite (stdlib). Jedno połączenie na operację — bezpieczne dla wątków serwera."""
from __future__ import annotations

import json
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Iterator

from pydantic import BaseModel

from .report import Report
from .transcript import Turn

SCHEMA = """
CREATE TABLE IF NOT EXISTS sessions (
    id TEXT PRIMARY KEY,
    scenario_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    finished_at TEXT,
    status TEXT NOT NULL DEFAULT 'w_toku',
    ai_enabled INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS turns (
    session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    idx INTEGER NOT NULL,
    data TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (session_id, idx)
);
CREATE TABLE IF NOT EXISTS reports (
    session_id TEXT PRIMARY KEY REFERENCES sessions(id) ON DELETE CASCADE,
    data TEXT NOT NULL,
    created_at TEXT NOT NULL
);
"""


class SessionRecord(BaseModel):
    id: str
    scenario_id: str
    created_at: str
    finished_at: str | None = None
    status: str = "w_toku"
    ai_enabled: bool = False
    turns_count: int = 0


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class SessionStore:
    def __init__(self, path: str | Path) -> None:
        self.path = str(path)
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        with self._conn() as conn:
            conn.executescript(SCHEMA)

    @contextmanager
    def _conn(self) -> Iterator[sqlite3.Connection]:
        conn = sqlite3.connect(self.path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON")
        try:
            yield conn
            conn.commit()
        finally:
            conn.close()

    def create_session(self, session_id: str, scenario_id: str, *, ai_enabled: bool) -> SessionRecord:
        record = SessionRecord(id=session_id, scenario_id=scenario_id, created_at=now_iso(), ai_enabled=ai_enabled)
        with self._conn() as conn:
            conn.execute(
                "INSERT INTO sessions (id, scenario_id, created_at, status, ai_enabled) VALUES (?, ?, ?, ?, ?)",
                (record.id, record.scenario_id, record.created_at, record.status, int(ai_enabled)),
            )
        return record

    def get_session(self, session_id: str) -> SessionRecord | None:
        with self._conn() as conn:
            row = conn.execute(
                "SELECT s.*, (SELECT COUNT(*) FROM turns t WHERE t.session_id = s.id) AS turns_count FROM sessions s WHERE s.id = ?",
                (session_id,),
            ).fetchone()
        return self._record(row) if row else None

    def list_sessions(self, limit: int = 100) -> list[SessionRecord]:
        with self._conn() as conn:
            rows = conn.execute(
                "SELECT s.*, (SELECT COUNT(*) FROM turns t WHERE t.session_id = s.id) AS turns_count "
                "FROM sessions s ORDER BY s.created_at DESC, s.rowid DESC LIMIT ?",
                (limit,),
            ).fetchall()
        return [self._record(r) for r in rows]

    @staticmethod
    def _record(row: sqlite3.Row) -> SessionRecord:
        return SessionRecord(
            id=row["id"],
            scenario_id=row["scenario_id"],
            created_at=row["created_at"],
            finished_at=row["finished_at"],
            status=row["status"],
            ai_enabled=bool(row["ai_enabled"]),
            turns_count=row["turns_count"],
        )

    def add_turn(self, session_id: str, turn: Turn) -> None:
        with self._conn() as conn:
            conn.execute(
                "INSERT INTO turns (session_id, idx, data, created_at) VALUES (?, ?, ?, ?)",
                (session_id, turn.index, turn.model_dump_json(), turn.created_at or now_iso()),
            )

    def get_turns(self, session_id: str) -> list[Turn]:
        with self._conn() as conn:
            rows = conn.execute("SELECT data FROM turns WHERE session_id = ? ORDER BY idx", (session_id,)).fetchall()
        return [Turn.model_validate_json(r["data"]) for r in rows]

    def finish_session(self, session_id: str, report: Report) -> None:
        with self._conn() as conn:
            conn.execute("UPDATE sessions SET status = 'zakonczona', finished_at = ? WHERE id = ?", (now_iso(), session_id))
            conn.execute(
                "INSERT OR REPLACE INTO reports (session_id, data, created_at) VALUES (?, ?, ?)",
                (session_id, report.model_dump_json(), now_iso()),
            )

    def get_report(self, session_id: str) -> Report | None:
        with self._conn() as conn:
            row = conn.execute("SELECT data FROM reports WHERE session_id = ?", (session_id,)).fetchone()
        return Report.model_validate_json(row["data"]) if row else None

    def report_summaries(self) -> dict[str, dict]:
        """Skrót raportów do listy sesji (bez ładowania transkryptów)."""
        with self._conn() as conn:
            rows = conn.execute("SELECT session_id, data FROM reports").fetchall()
        out = {}
        for r in rows:
            data = json.loads(r["data"])
            out[r["session_id"]] = {
                "coverage_share": data.get("coverage_share"),
                "open_share": data.get("questions", {}).get("open_share"),
                "incidents": len(data.get("incidents", [])),
            }
        return out

    def delete_session(self, session_id: str) -> bool:
        with self._conn() as conn:
            cur = conn.execute("DELETE FROM sessions WHERE id = ?", (session_id,))
        return cur.rowcount > 0

    def purge_older_than(self, days: int) -> int:
        """Retencja: usuwa sesje starsze niż `days` dni (wraz z turami i raportami)."""
        cutoff = (datetime.now(timezone.utc) - timedelta(days=days)).isoformat(timespec="seconds")
        with self._conn() as conn:
            cur = conn.execute("DELETE FROM sessions WHERE created_at < ?", (cutoff,))
        return cur.rowcount
