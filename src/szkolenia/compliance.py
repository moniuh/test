"""Ważność szkoleń i statusy zgodności — czyste funkcje z wstrzykiwaną datą „dziś”."""
from __future__ import annotations

import calendar
from datetime import date
from typing import Literal

EXPIRING_WINDOW_DAYS = 30

ComplianceStatus = Literal["valid", "expiring", "overdue", "incomplete", "not_applicable"]

STATUS_LABELS: dict[str, str] = {
    "valid": "aktualne",
    "expiring": "wygasa ≤ 30 dni",
    "overdue": "przeterminowane",
    "incomplete": "nieukończone",
    "not_applicable": "nie dotyczy",
}

# Stany zaliczane do „zgodnych” w raporcie: szkolenie jest ważne na dziś.
COMPLIANT_STATUSES = frozenset({"valid", "expiring"})


def add_months(d: date, months: int) -> date:
    """Dodaje miesiące; gdy dzień nie istnieje w miesiącu docelowym — ostatni dzień miesiąca."""
    index = d.month - 1 + months
    year, month = d.year + index // 12, index % 12 + 1
    day = min(d.day, calendar.monthrange(year, month)[1])
    return date(year, month, day)


def compute_valid_until(completed_on: date, validity_months: int | None) -> date | None:
    """Ostatni dzień ważności (włącznie) albo None dla szkoleń bezterminowych."""
    if validity_months is None:
        return None
    return add_months(completed_on, validity_months)


def compliance_status(
    *,
    today: date,
    completed: bool,
    valid_until: date | None = None,
    due_date: date | None = None,
) -> ComplianceStatus:
    """Status pary pracownik × szkolenie.

    - ukończone i bezterminowe albo ważne dłużej niż 30 dni → `valid`,
    - ukończone, ważne jeszcze 0–30 dni (dzień `valid_until` włącznie) → `expiring`,
    - ukończone, ale `today > valid_until` → `overdue`,
    - nieukończone po terminie (`today > due_date`) → `overdue`, w terminie → `incomplete`.
    """
    if completed:
        if valid_until is None:
            return "valid"
        days_left = (valid_until - today).days
        if days_left < 0:
            return "overdue"
        if days_left <= EXPIRING_WINDOW_DAYS:
            return "expiring"
        return "valid"
    if due_date is not None and today > due_date:
        return "overdue"
    return "incomplete"


def needs_renewal(valid_until: date | None, today: date, window_days: int = EXPIRING_WINDOW_DAYS) -> bool:
    """Odnowienie przypisujemy od początku okna wygasania (najpóźniej po upływie ważności)."""
    if valid_until is None:
        return False
    return (valid_until - today).days <= window_days


def days_overdue(due: date, today: date) -> int:
    return max(0, (today - due).days)
