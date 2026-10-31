"""Reguły przypisań szkoleń według działu i stanowiska."""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from .textutil import normalize_ws


@dataclass(frozen=True)
class RuleSpec:
    id: int
    training_id: int
    department: str | None  # None = każdy dział
    position: str | None  # None = każde stanowisko
    due_days: int


@dataclass(frozen=True)
class EmployeeSpec:
    id: int
    department: str
    position: str
    hire_date: date


def _norm(value: str | None) -> str:
    return normalize_ws(value or "").casefold()


def rule_matches(rule: RuleSpec, employee: EmployeeSpec) -> bool:
    """Wszystkie ustawione kryteria muszą pasować (porównanie bez wielkości liter i nadmiarowych spacji)."""
    if rule.department and _norm(rule.department) != _norm(employee.department):
        return False
    if rule.position and _norm(rule.position) != _norm(employee.position):
        return False
    return True


def required_trainings(rules: list[RuleSpec], employee: EmployeeSpec) -> dict[int, RuleSpec]:
    """Szkolenia wymagane dla pracownika → reguła decydująca o terminie (najkrótszy termin wygrywa)."""
    chosen: dict[int, RuleSpec] = {}
    for rule in sorted(rules, key=lambda r: (r.due_days, r.id)):
        if rule.training_id not in chosen and rule_matches(rule, employee):
            chosen[rule.training_id] = rule
    return chosen


def initial_due_date(hire_date: date, due_days: int, today: date, *, on_hire: bool) -> date:
    """Termin pierwszego przypisania.

    Nowy pracownik: data zatrudnienia + N dni. Gdy regułę dodano później dla osób już
    zatrudnionych, liczymy od dziś, żeby nie powstawały przypisania przeterminowane „z urzędu”.
    """
    base = hire_date if on_hire else max(hire_date, today)
    return base + timedelta(days=due_days)
