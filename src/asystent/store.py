"""Trwałość w SQLite (stdlib): dokumenty, fragmenty, interakcje, zgłoszenia do HR."""
from __future__ import annotations

import json
import sqlite3
import threading
from collections.abc import Iterable, Sequence
from typing import Any

SCHEMA = """
CREATE TABLE IF NOT EXISTS documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    doc_key TEXT NOT NULL,
    title TEXT NOT NULL,
    version TEXT NOT NULL,
    effective_from TEXT NOT NULL,
    uploaded_at TEXT NOT NULL,
    filename TEXT NOT NULL,
    content TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    withdrawn INTEGER NOT NULL DEFAULT 0,
    injection_flag INTEGER NOT NULL DEFAULT 0,
    UNIQUE (doc_key, version)
);
CREATE TABLE IF NOT EXISTS chunks (
    id TEXT PRIMARY KEY,
    document_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL,
    section_path TEXT NOT NULL,
    text TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_chunks_document ON chunks(document_id);
CREATE TABLE IF NOT EXISTS interactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_ref TEXT NOT NULL,
    created_at TEXT NOT NULL,
    question TEXT NOT NULL,
    status TEXT NOT NULL,
    source TEXT NOT NULL,
    categories TEXT NOT NULL DEFAULT '[]',
    confidence TEXT,
    needs_hr INTEGER NOT NULL DEFAULT 0,
    best_score REAL,
    coverage REAL,
    cited TEXT NOT NULL DEFAULT '[]',
    answer_excerpt TEXT,
    feedback TEXT,
    feedback_comment TEXT,
    feedback_at TEXT
);
CREATE INDEX IF NOT EXISTS ix_interactions_created ON interactions(created_at);
CREATE TABLE IF NOT EXISTS escalations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    interaction_id INTEGER REFERENCES interactions(id) ON DELETE SET NULL,
    session_ref TEXT NOT NULL,
    created_at TEXT NOT NULL,
    question TEXT NOT NULL,
    note TEXT,
    contact TEXT,
    context TEXT NOT NULL DEFAULT '{}',
    status TEXT NOT NULL DEFAULT 'nowe',
    hr_reply TEXT,
    replied_at TEXT,
    closed_at TEXT
);
CREATE INDEX IF NOT EXISTS ix_escalations_session ON escalations(session_ref);
CREATE TABLE IF NOT EXISTS meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
"""

_JSON_COLUMNS = {"categories", "cited", "context", "section_path"}


def _row(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if row is None:
        return None
    data = dict(row)
    for key in _JSON_COLUMNS & data.keys():
        data[key] = json.loads(data[key]) if data[key] else None
    return data


class Store:
    """Cienka warstwa nad sqlite3; jedno połączenie chronione blokadą (FastAPI używa wątków)."""

    def __init__(self, path: str = ":memory:") -> None:
        self.path = path
        self._lock = threading.RLock()
        self._conn = sqlite3.connect(path, check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA foreign_keys = ON")
        if path != ":memory:":
            self._conn.execute("PRAGMA journal_mode = WAL")
        self._conn.executescript(SCHEMA)

    def close(self) -> None:
        with self._lock:
            self._conn.close()

    def _all(self, sql: str, params: Sequence[Any] = ()) -> list[dict[str, Any]]:
        with self._lock:
            return [_row(r) for r in self._conn.execute(sql, params).fetchall()]  # type: ignore[misc]

    def _one(self, sql: str, params: Sequence[Any] = ()) -> dict[str, Any] | None:
        with self._lock:
            return _row(self._conn.execute(sql, params).fetchone())

    def _write(self, sql: str, params: Sequence[Any] = ()) -> int:
        with self._lock, self._conn:
            cur = self._conn.execute(sql, params)
            return int(cur.lastrowid or 0) if sql.lstrip().upper().startswith("INSERT") else cur.rowcount

    # --- dokumenty ---
    def insert_document(self, doc: dict[str, Any], chunks: Iterable[tuple[int, list[str], str]]) -> int:
        """Zapisuje dokument i jego fragmenty w jednej transakcji."""
        with self._lock, self._conn:
            cur = self._conn.execute(
                "INSERT INTO documents (doc_key, title, version, effective_from, uploaded_at, filename, content,"
                " sha256, injection_flag) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (doc["doc_key"], doc["title"], doc["version"], doc["effective_from"], doc["uploaded_at"],
                 doc["filename"], doc["content"], doc["sha256"], int(doc.get("injection_flag", False))),
            )
            doc_id = int(cur.lastrowid or 0)
            self._conn.executemany(
                "INSERT INTO chunks (id, document_id, ordinal, section_path, text) VALUES (?, ?, ?, ?, ?)",
                [(f"d{doc_id}-c{ordinal}", doc_id, ordinal, json.dumps(path, ensure_ascii=False), text)
                 for ordinal, path, text in chunks],
            )
        return doc_id

    def list_documents(self) -> list[dict[str, Any]]:
        return self._all(
            "SELECT d.id, d.doc_key, d.title, d.version, d.effective_from, d.uploaded_at, d.filename, d.sha256,"
            " d.withdrawn, d.injection_flag, COUNT(c.id) AS chunk_count FROM documents d"
            " LEFT JOIN chunks c ON c.document_id = d.id GROUP BY d.id ORDER BY d.title, d.effective_from DESC"
        )

    def get_document(self, doc_id: int) -> dict[str, Any] | None:
        return self._one("SELECT * FROM documents WHERE id = ?", (doc_id,))

    def version_exists(self, doc_key: str, version: str) -> bool:
        row = self._one("SELECT 1 AS x FROM documents WHERE doc_key = ? AND version = ?", (doc_key, version))
        return row is not None

    def count_versions(self, doc_key: str) -> int:
        row = self._one("SELECT COUNT(*) AS n FROM documents WHERE doc_key = ?", (doc_key,))
        return int(row["n"]) if row else 0

    def set_withdrawn(self, doc_id: int, withdrawn: bool) -> bool:
        return self._write("UPDATE documents SET withdrawn = ? WHERE id = ?", (int(withdrawn), doc_id)) > 0

    def chunks_for(self, doc_ids: Sequence[int]) -> list[dict[str, Any]]:
        if not doc_ids:
            return []
        marks = ",".join("?" * len(doc_ids))
        return self._all(
            f"SELECT * FROM chunks WHERE document_id IN ({marks}) ORDER BY document_id, ordinal", list(doc_ids)
        )

    def count_documents(self) -> int:
        row = self._one("SELECT COUNT(*) AS n FROM documents")
        return int(row["n"]) if row else 0

    # --- interakcje ---
    def insert_interaction(self, data: dict[str, Any]) -> int:
        return self._write(
            "INSERT INTO interactions (session_ref, created_at, question, status, source, categories, confidence,"
            " needs_hr, best_score, coverage, cited, answer_excerpt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (data["session_ref"], data["created_at"], data["question"], data["status"], data["source"],
             json.dumps(data.get("categories", []), ensure_ascii=False), data.get("confidence"),
             int(data.get("needs_hr", False)), data.get("best_score"), data.get("coverage"),
             json.dumps(data.get("cited", []), ensure_ascii=False), data.get("answer_excerpt")),
        )

    def get_interaction(self, interaction_id: int) -> dict[str, Any] | None:
        return self._one("SELECT * FROM interactions WHERE id = ?", (interaction_id,))

    def set_feedback(self, interaction_id: int, vote: str, comment: str | None, at: str) -> None:
        self._write(
            "UPDATE interactions SET feedback = ?, feedback_comment = ?, feedback_at = ? WHERE id = ?",
            (vote, comment, at, interaction_id),
        )

    def interactions_since(self, since: str) -> list[dict[str, Any]]:
        return self._all("SELECT * FROM interactions WHERE created_at >= ? ORDER BY created_at", (since,))

    def escalated_interaction_ids(self) -> set[int]:
        rows = self._all("SELECT DISTINCT interaction_id FROM escalations WHERE interaction_id IS NOT NULL")
        return {int(r["interaction_id"]) for r in rows}

    # --- zgłoszenia do HR ---
    def insert_escalation(self, data: dict[str, Any]) -> int:
        return self._write(
            "INSERT INTO escalations (interaction_id, session_ref, created_at, question, note, contact, context)"
            " VALUES (?, ?, ?, ?, ?, ?, ?)",
            (data.get("interaction_id"), data["session_ref"], data["created_at"], data["question"],
             data.get("note"), data.get("contact"), json.dumps(data.get("context", {}), ensure_ascii=False)),
        )

    def get_escalation(self, escalation_id: int) -> dict[str, Any] | None:
        return self._one("SELECT * FROM escalations WHERE id = ?", (escalation_id,))

    def list_escalations(self, status: str | None = None, session_ref: str | None = None) -> list[dict[str, Any]]:
        sql, params = "SELECT * FROM escalations WHERE 1 = 1", []
        if status:
            sql += " AND status = ?"
            params.append(status)
        if session_ref:
            sql += " AND session_ref = ?"
            params.append(session_ref)
        return self._all(sql + " ORDER BY created_at DESC, id DESC", params)

    def update_escalation(self, escalation_id: int, **fields: Any) -> None:
        cols = ", ".join(f"{k} = ?" for k in fields)
        self._write(f"UPDATE escalations SET {cols} WHERE id = ?", [*fields.values(), escalation_id])

    # --- statystyki ---
    def count_by(self, table: str, column: str) -> dict[str, int]:
        assert table in {"interactions", "escalations"} and column.isidentifier()
        rows = self._all(f"SELECT {column} AS k, COUNT(*) AS n FROM {table} GROUP BY {column}")
        return {str(r["k"]): int(r["n"]) for r in rows if r["k"] is not None}

    def all_interactions(self) -> list[dict[str, Any]]:
        return self._all("SELECT * FROM interactions ORDER BY created_at")

    # --- retencja ---
    def purge_before(self, cutoff: str) -> dict[str, int]:
        """Usuwa interakcje starsze niż `cutoff` i zamknięte zgłoszenia zamknięte przed `cutoff`."""
        with self._lock, self._conn:
            esc = self._conn.execute(
                "DELETE FROM escalations WHERE status = 'zamknięte' AND COALESCE(closed_at, created_at) < ?",
                (cutoff,),
            ).rowcount
            inter = self._conn.execute("DELETE FROM interactions WHERE created_at < ?", (cutoff,)).rowcount
        return {"interactions": inter, "escalations": esc}

    def get_meta(self, key: str) -> str | None:
        row = self._one("SELECT value FROM meta WHERE key = ?", (key,))
        return row["value"] if row else None

    def set_meta(self, key: str, value: str) -> None:
        self._write("INSERT INTO meta (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                    (key, value))
