"""Baza wiedzy: ingestia dokumentów, wersjonowanie („obowiązuje od”) i aktualne fragmenty."""
from __future__ import annotations

import hashlib
import logging
from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Literal

from .chunking import (
    SUPPORTED_EXTENSIONS,
    DocumentError,
    chunk_document,
    guess_title,
    load_document,
    slugify,
)
from .safety import detect_injection
from .search import Passage
from .store import Store

log = logging.getLogger(__name__)

DocStatus = Literal["aktualna", "archiwalna", "zaplanowana", "wycofana"]
MAX_UPLOAD_BYTES = 5 * 1024 * 1024


@dataclass(frozen=True)
class DocumentInfo:
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
    status: DocStatus


def resolve_statuses(rows: list[dict], today: date) -> dict[int, DocStatus]:
    """Status każdej wersji: aktualna = najnowsza „obowiązuje od” ≤ dziś (bez wycofanych)."""
    current: dict[str, dict] = {}
    for row in rows:
        if row["withdrawn"] or date.fromisoformat(row["effective_from"]) > today:
            continue
        best = current.get(row["doc_key"])
        if best is None or (row["effective_from"], row["uploaded_at"], row["id"]) > (
            best["effective_from"], best["uploaded_at"], best["id"]
        ):
            current[row["doc_key"]] = row
    current_ids = {r["id"] for r in current.values()}
    statuses: dict[int, DocStatus] = {}
    for row in rows:
        if row["withdrawn"]:
            statuses[row["id"]] = "wycofana"
        elif row["id"] in current_ids:
            statuses[row["id"]] = "aktualna"
        elif date.fromisoformat(row["effective_from"]) > today:
            statuses[row["id"]] = "zaplanowana"
        else:
            statuses[row["id"]] = "archiwalna"
    return statuses


class KnowledgeBase:
    def __init__(self, store: Store, now: Callable[[], datetime], today: Callable[[], date]) -> None:
        self.store = store
        self._now = now
        self._today = today
        self.revision = 0  # zmienia się przy każdej zmianie dokumentów (unieważnia indeks)

    def add_document(
        self,
        filename: str,
        data: bytes,
        *,
        title: str | None = None,
        doc_key: str | None = None,
        version: str | None = None,
        effective_from: date | None = None,
    ) -> DocumentInfo:
        """Wczytuje, dzieli i zapisuje nową wersję dokumentu."""
        if len(data) > MAX_UPLOAD_BYTES:
            raise DocumentError("Plik jest za duży — maksymalnie 5 MB")
        parsed = load_document(filename, data)
        title = (title or parsed.title or guess_title(parsed.body) or Path(filename).stem).strip()[:200]
        key = slugify(doc_key or parsed.doc_key or title)
        version = (version or parsed.version or str(self.store.count_versions(key) + 1)).strip()[:40]
        effective = effective_from or parsed.effective_from or self._today()
        if self.store.version_exists(key, version):
            raise DocumentError(f"Wersja „{version}” dokumentu „{key}” już istnieje — podaj inny numer wersji")
        drafts = chunk_document(title, parsed.body)
        if not drafts:
            raise DocumentError("Dokument nie zawiera treści, którą można podzielić na fragmenty")
        doc_id = self.store.insert_document(
            {
                "doc_key": key,
                "title": title,
                "version": version,
                "effective_from": effective.isoformat(),
                "uploaded_at": self._now().isoformat(timespec="seconds"),
                "filename": Path(filename).name[:200],
                "content": parsed.body,
                "sha256": hashlib.sha256(parsed.body.encode("utf-8")).hexdigest(),
                # Polecenia dla modelu w treści dokumentu — oznaczamy do przeglądu, treść i tak jest danymi.
                "injection_flag": detect_injection(parsed.body),
            },
            [(d.ordinal, list(d.section_path), d.text) for d in drafts],
        )
        self.revision += 1
        info = self.get(doc_id)
        assert info is not None
        return info

    def documents(self) -> list[DocumentInfo]:
        rows = self.store.list_documents()
        statuses = resolve_statuses(rows, self._today())
        return [
            DocumentInfo(
                id=r["id"], doc_key=r["doc_key"], title=r["title"], version=r["version"],
                effective_from=r["effective_from"], uploaded_at=r["uploaded_at"], filename=r["filename"],
                chunk_count=r["chunk_count"], withdrawn=bool(r["withdrawn"]),
                injection_flag=bool(r["injection_flag"]), status=statuses[r["id"]],
            )
            for r in rows
        ]

    def get(self, doc_id: int) -> DocumentInfo | None:
        return next((d for d in self.documents() if d.id == doc_id), None)

    def chunks(self, doc_id: int) -> list[dict]:
        return self.store.chunks_for([doc_id])

    def set_withdrawn(self, doc_id: int, withdrawn: bool) -> DocumentInfo | None:
        if not self.store.set_withdrawn(doc_id, withdrawn):
            return None
        self.revision += 1
        return self.get(doc_id)

    def current_passages(self) -> list[Passage]:
        """Fragmenty wyłącznie z aktualnych wersji dokumentów."""
        docs = {d.id: d for d in self.documents() if d.status == "aktualna"}
        return [
            Passage(
                chunk_id=c["id"],
                document_id=c["document_id"],
                doc_key=docs[c["document_id"]].doc_key,
                title=docs[c["document_id"]].title,
                version=docs[c["document_id"]].version,
                effective_from=docs[c["document_id"]].effective_from,
                section_path=tuple(c["section_path"]),
                text=c["text"],
            )
            for c in self.store.chunks_for(list(docs))
        ]

    def seed_directory(self, directory: Path) -> int:
        """Wczytuje przykładowe dokumenty, jeśli baza jest pusta. Zwraca liczbę dodanych."""
        if self.store.count_documents() or not directory.is_dir():
            return 0
        added = 0
        for path in sorted(directory.iterdir()):
            if path.suffix.lower() not in SUPPORTED_EXTENSIONS or path.name.lower().startswith("readme"):
                continue
            try:
                self.add_document(path.name, path.read_bytes())
                added += 1
            except DocumentError as exc:
                log.warning("Pominięto %s: %s", path.name, exc)
        return added
