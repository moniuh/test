"""Wynik i rekomendacja liczone w kodzie z werdyktów i wag (model nigdy nie podaje liczby)."""
from __future__ import annotations

from dataclasses import dataclass

from .models import RECOMMENDATION_LABELS, Recommendation, RequirementResult

# Punkty za werdykt (mnożnik wagi). „Brak danych” = 0, ale nie jest traktowany jak „nie spełnia”:
# blokuje rekomendację „zaproś” zamiast ją przekreślać (brak informacji ≠ brak kompetencji).
VERDICT_CREDIT: dict[str, float] = {"meets": 1.0, "partial": 0.5, "fails": 0.0, "no_data": 0.0}


@dataclass(frozen=True)
class Thresholds:
    """Jawne progi rekomendacji (w punktach procentowych 0–100)."""

    invite_min: float = 70.0
    consider_min: float = 40.0


THRESHOLDS = Thresholds()


@dataclass(frozen=True)
class ScoreResult:
    score: float
    recommendation: Recommendation
    gates: list[str]
    weighted_points: float
    max_points: float


def _rank(rec: Recommendation) -> int:
    return {"mismatch": 0, "consider": 1, "invite": 2}[rec]


def compute_score(results: list[RequirementResult], thresholds: Thresholds = THRESHOLDS) -> ScoreResult:
    """Wynik = Σ(waga × punkty za werdykt) / Σ wag × 100, potem progi i reguły dla wymagań obowiązkowych.

    Reguły (bramki), które mogą tylko obniżyć rekomendację:
    - wymaganie obowiązkowe „nie spełnia” → najwyżej „niedopasowanie”,
    - wymaganie obowiązkowe „brak danych” → najwyżej „do rozważenia” (do sprawdzenia przez człowieka).
    """
    if not results:
        raise ValueError("Brak wymagań do oceny.")
    max_points = float(sum(r.weight for r in results))
    weighted = 0.0
    for r in results:
        r.points = round(r.weight * VERDICT_CREDIT[r.verdict], 2)
        weighted += r.points
    score = round(100.0 * weighted / max_points, 1) if max_points else 0.0

    if score >= thresholds.invite_min:
        rec: Recommendation = "invite"
    elif score >= thresholds.consider_min:
        rec = "consider"
    else:
        rec = "mismatch"

    gates: list[str] = []
    failed_must = [r.name for r in results if r.kind == "must" and r.verdict == "fails"]
    missing_must = [r.name for r in results if r.kind == "must" and r.verdict == "no_data"]
    if failed_must and _rank(rec) > _rank("mismatch"):
        gates.append(
            "Wymaganie obowiązkowe niespełnione (" + ", ".join(failed_must) + ") — rekomendacja obniżona do "
            f"„{RECOMMENDATION_LABELS['mismatch']}”."
        )
        rec = "mismatch"
    if missing_must and _rank(rec) > _rank("consider"):
        gates.append(
            "Brak danych o wymaganiu obowiązkowym (" + ", ".join(missing_must) + ") — rekomendacja obniżona do "
            f"„{RECOMMENDATION_LABELS['consider']}”; sprawdź w rozmowie."
        )
        rec = "consider"
    return ScoreResult(score=score, recommendation=rec, gates=gates, weighted_points=round(weighted, 2), max_points=max_points)
