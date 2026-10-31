"""Wynik 0–100 liczony jawną formułą z listy znalezisk."""
from __future__ import annotations

from collections import defaultdict

from .models import CATEGORY_LABELS, CategorySummary, Finding, SeverityCounts

WEIGHTS: dict[str, int] = {"error": 15, "warning": 6, "suggestion": 2}
MAX_REPEATS = 2

FORMULA = (
    "wynik = max(0, 100 − Σ kar); kara reguły = waga × min(liczba wystąpień, 2); "
    "wagi: błąd 15, ostrzeżenie 6, sugestia 2; każde znalezisko AI liczone osobno. "
    "Wynik kategorii = max(0, 100 − suma kar w kategorii)."
)

VERDICTS: dict[str, str] = {
    "high_risk": "wysokie ryzyko",
    "needs_work": "wymaga poprawy",
    "minor": "drobne poprawki",
    "ok": "zgodne z dobrymi praktykami",
}


def _group_key(finding: Finding) -> str:
    # Powtórzenia tej samej reguły są przycinane; znaleziska AI to osobne problemy.
    return finding.rule_id if finding.source == "rule" else f"ai:{finding.start}:{finding.end}"


def penalties(findings: list[Finding]) -> dict[str, tuple[str, int]]:
    """Kara na grupę: klucz grupy → (kategoria, kara)."""
    groups: dict[str, list[Finding]] = defaultdict(list)
    for f in findings:
        groups[_group_key(f)].append(f)
    result = {}
    for key, items in groups.items():
        worst = min(items, key=lambda f: list(WEIGHTS).index(f.severity))
        result[key] = (worst.category, WEIGHTS[worst.severity] * min(len(items), MAX_REPEATS))
    return result


def compute_score(findings: list[Finding]) -> int:
    return max(0, 100 - sum(p for _, p in penalties(findings).values()))


def count_severities(findings: list[Finding]) -> SeverityCounts:
    counts = SeverityCounts()
    for f in findings:
        setattr(counts, f.severity, getattr(counts, f.severity) + 1)
    return counts


def category_summary(findings: list[Finding]) -> list[CategorySummary]:
    summaries = {cat: CategorySummary(category=cat, label=label) for cat, label in CATEGORY_LABELS.items()}
    for f in findings:
        s = summaries[f.category]
        if f.severity == "error":
            s.errors += 1
        elif f.severity == "warning":
            s.warnings += 1
        else:
            s.suggestions += 1
    for category, penalty in penalties(findings).values():
        summaries[category].penalty += penalty
    for s in summaries.values():
        s.score = max(0, 100 - s.penalty)
    return list(summaries.values())


def verdict(score: int, counts: SeverityCounts) -> str:
    if score < 40:
        return "high_risk"
    if counts.error or score < 70:
        return "needs_work"
    if counts.warning or score < 90:
        return "minor"
    return "ok"
