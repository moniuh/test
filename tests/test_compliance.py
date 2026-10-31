from datetime import date, timedelta

import pytest

from szkolenia.compliance import (
    EXPIRING_WINDOW_DAYS,
    add_months,
    compliance_status,
    compute_valid_until,
    needs_renewal,
)

TODAY = date(2026, 3, 16)


@pytest.mark.parametrize(("start", "months", "expected"), [
    (date(2026, 1, 31), 1, date(2026, 2, 28)),      # brak 31 lutego → ostatni dzień miesiąca
    (date(2024, 2, 29), 12, date(2025, 2, 28)),     # rok przestępny → zwykły
    (date(2023, 2, 28), 12, date(2024, 2, 28)),
    (date(2026, 11, 15), 3, date(2027, 2, 15)),     # przejście przez koniec roku
    (date(2026, 3, 31), -1, date(2026, 2, 28)),     # odejmowanie miesięcy
    (date(2026, 5, 10), 36, date(2029, 5, 10)),
])
def test_add_months(start, months, expected):
    assert add_months(start, months) == expected


def test_valid_until_none_for_unlimited_training():
    assert compute_valid_until(TODAY, None) is None
    assert compute_valid_until(date(2020, 1, 1), 72) == date(2026, 1, 1)


@pytest.mark.parametrize(("days_left", "expected"), [
    (EXPIRING_WINDOW_DAYS + 1, "valid"),
    (EXPIRING_WINDOW_DAYS, "expiring"),   # granica okna: 30 dni → już „wygasa”
    (1, "expiring"),
    (0, "expiring"),                       # ostatni dzień ważności — jeszcze ważne
    (-1, "overdue"),                       # dzień po → przeterminowane
    (-400, "overdue"),
])
def test_completed_status_boundaries(days_left, expected):
    valid_until = TODAY + timedelta(days=days_left)
    assert compliance_status(today=TODAY, completed=True, valid_until=valid_until) == expected


def test_completed_without_expiry_is_always_valid():
    assert compliance_status(today=date(2099, 1, 1), completed=True, valid_until=None) == "valid"


@pytest.mark.parametrize(("due_offset", "expected"), [(5, "incomplete"), (0, "incomplete"), (-1, "overdue")])
def test_not_completed_status_depends_on_due_date(due_offset, expected):
    due = TODAY + timedelta(days=due_offset)
    assert compliance_status(today=TODAY, completed=False, due_date=due) == expected


def test_not_completed_without_assignment_is_incomplete():
    assert compliance_status(today=TODAY, completed=False) == "incomplete"


def test_status_follows_injected_today():
    valid_until = date(2026, 6, 30)
    assert compliance_status(today=date(2026, 5, 30), completed=True, valid_until=valid_until) == "valid"  # 31 dni
    assert compliance_status(today=date(2026, 5, 31), completed=True, valid_until=valid_until) == "expiring"  # 30 dni
    assert compliance_status(today=date(2026, 6, 30), completed=True, valid_until=valid_until) == "expiring"
    assert compliance_status(today=date(2026, 7, 1), completed=True, valid_until=valid_until) == "overdue"


def test_needs_renewal_window():
    valid_until = TODAY + timedelta(days=30)
    assert needs_renewal(valid_until, TODAY)
    assert not needs_renewal(valid_until + timedelta(days=1), TODAY)
    assert needs_renewal(TODAY - timedelta(days=10), TODAY)   # po upływie ważności — tym bardziej
    assert not needs_renewal(None, TODAY)
