"""Konfiguracja ze zmiennych środowiskowych."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from datetime import date, datetime, timezone
from pathlib import Path

try:
    from zoneinfo import ZoneInfo

    _LOCAL_TZ = ZoneInfo("Europe/Warsaw")
except Exception:  # brak bazy stref czasowych w systemie
    _LOCAL_TZ = timezone.utc


def default_docs_dir() -> Path:
    """Katalog z przykładowymi dokumentami (data/dokumenty w repozytorium)."""
    return Path(__file__).resolve().parents[2] / "data" / "dokumenty"


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def local_date(moment: datetime) -> date:
    """Data „obowiązuje od” liczymy w czasie polskim."""
    return moment.astimezone(_LOCAL_TZ).date()


def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


def _env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except ValueError:
        return default


@dataclass
class Settings:
    db_path: str = "asystent.db"
    docs_dir: Path | None = field(default_factory=default_docs_dir)
    retention_days: int = 90
    hr_token: str | None = None
    top_k: int = 5
    min_score: float = 2.0
    min_coverage: float = 0.5
    hr_contact: str = "hr@zefirion.example"
    safety_doc_keys: tuple[str, ...] = ("procedura-antymobbingowa", "procedura-sygnalistow")

    @classmethod
    def from_env(cls) -> Settings:
        docs = os.getenv("HR_DOCS_DIR")
        return cls(
            db_path=os.getenv("HR_DB_PATH", "asystent.db"),
            docs_dir=Path(docs) if docs else default_docs_dir(),
            retention_days=max(1, _env_int("HR_RETENTION_DAYS", 90)),
            hr_token=os.getenv("HR_PANEL_TOKEN") or None,
            top_k=max(1, _env_int("HR_TOP_K", 5)),
            min_score=_env_float("HR_MIN_SCORE", 2.0),
            min_coverage=_env_float("HR_MIN_COVERAGE", 0.5),
            hr_contact=os.getenv("HR_CONTACT", "hr@zefirion.example"),
        )
