"""Monitoring stronniczości: wskaźniki selekcji per grupa i etap oraz adverse impact ratio (reguła 4/5).

Dane demograficzne są dobrowolne, przechowywane osobno i używane wyłącznie tutaj, w agregacie.
Reguła 4/5: wskaźnik selekcji grupy < 80% wskaźnika grupy najlepiej wypadającej sygnalizuje
możliwy niekorzystny wpływ. To heurystyka do dalszej analizy, a nie dowód dyskryminacji;
przy małych próbach wynik jest niestabilny.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Mapping

from pydantic import BaseModel, Field

from .models import DEMOGRAPHIC_LABELS, DIMENSION_LABELS

FOUR_FIFTHS = 0.8
SMALL_SAMPLE = 30
UNDISCLOSED = "undisclosed"

STAGES: dict[str, str] = {
    "ai_pass": "AI: przejście sita (rekomendacja „zaproś” lub „do rozważenia”)",
    "ai_invite": "AI: rekomendacja „zaproś”",
    "human_invited": "Człowiek: decyzja „zaproszony”",
}


@dataclass(frozen=True)
class GroupCounts:
    selected: int
    total: int


class GroupResult(BaseModel):
    group: str
    label: str
    selected: int
    total: int
    rate: float | None
    impact_ratio: float | None
    flagged: bool
    small_sample: bool
    excluded_from_ratio: bool = False


class StageResult(BaseModel):
    stage: str
    label: str
    groups: list[GroupResult]
    reference_group: str | None
    flagged_groups: list[str]


class DimensionResult(BaseModel):
    dimension: str
    label: str
    respondents: int
    stages: list[StageResult]


class BiasReport(BaseModel):
    candidates_total: int
    respondents: int
    dimensions: list[DimensionResult]
    warnings: list[str] = Field(default_factory=list)
    threshold: float = FOUR_FIFTHS
    small_sample: int = SMALL_SAMPLE


def adverse_impact(
    groups: Mapping[str, GroupCounts], labels: Mapping[str, str] | None = None
) -> tuple[list[GroupResult], str | None]:
    """Liczy wskaźnik selekcji i impact ratio względem grupy o najwyższym wskaźniku.

    Grupa „nie podano” jest pokazywana, ale nie uczestniczy w porównaniu.
    """
    labels = labels or {}
    rates = {
        g: c.selected / c.total
        for g, c in groups.items()
        if c.total > 0 and g != UNDISCLOSED
    }
    reference = max(rates, key=lambda g: (rates[g], groups[g].total)) if rates else None
    ref_rate = rates[reference] if reference else 0.0
    out: list[GroupResult] = []
    for g, c in groups.items():
        rate = c.selected / c.total if c.total else None
        excluded = g == UNDISCLOSED
        ratio = None
        if not excluded and rate is not None and ref_rate > 0:
            ratio = round(rate / ref_rate, 3)
        out.append(
            GroupResult(
                group=g,
                label=labels.get(g, g),
                selected=c.selected,
                total=c.total,
                rate=round(rate, 3) if rate is not None else None,
                impact_ratio=ratio,
                flagged=ratio is not None and ratio < FOUR_FIFTHS,
                small_sample=c.total < SMALL_SAMPLE,
                excluded_from_ratio=excluded,
            )
        )
    return out, reference


@dataclass(frozen=True)
class CandidateOutcome:
    """Wynik kandydata na etapach selekcji + jego (dobrowolne) dane demograficzne."""

    recommendation: str | None
    status: str
    demographics: Mapping[str, str] | None


def _selected(stage: str, o: CandidateOutcome) -> bool:
    if stage == "ai_pass":
        return o.recommendation in ("invite", "consider")
    if stage == "ai_invite":
        return o.recommendation == "invite"
    return o.status == "invited"


def bias_report(outcomes: Iterable[CandidateOutcome]) -> BiasReport:
    outcomes = list(outcomes)
    respondents = [o for o in outcomes if o.demographics]
    warnings: list[str] = []
    dimensions: list[DimensionResult] = []
    for dim, dim_label in DIMENSION_LABELS.items():
        labels = DEMOGRAPHIC_LABELS[dim]
        stages: list[StageResult] = []
        for stage, stage_label in STAGES.items():
            counts: dict[str, list[int]] = {g: [0, 0] for g in labels}
            for o in respondents:
                g = (o.demographics or {}).get(dim, UNDISCLOSED)
                if g not in counts:
                    g = UNDISCLOSED
                counts[g][1] += 1
                counts[g][0] += int(_selected(stage, o))
            groups = {g: GroupCounts(sel, tot) for g, (sel, tot) in counts.items() if tot > 0}
            results, reference = adverse_impact(groups, labels)
            stages.append(
                StageResult(
                    stage=stage,
                    label=stage_label,
                    groups=results,
                    reference_group=reference,
                    flagged_groups=[r.group for r in results if r.flagged],
                )
            )
        dimensions.append(
            DimensionResult(dimension=dim, label=dim_label, respondents=len(respondents), stages=stages)
        )
    if not respondents:
        warnings.append("Brak danych z dobrowolnej ankiety — raport stronniczości nie może zostać policzony.")
    elif len(respondents) < SMALL_SAMPLE:
        warnings.append(
            f"Mała próba ({len(respondents)} ankiet; zalecane min. {SMALL_SAMPLE} na grupę). "
            "Wskaźniki są niestabilne — traktuj je jako sygnał do obserwacji, nie jako wniosek."
        )
    if outcomes and len(respondents) < len(outcomes) / 2:
        warnings.append(
            f"Ankietę wypełniło {len(respondents)} z {len(outcomes)} kandydatów — wyniki mogą nie być reprezentatywne."
        )
    return BiasReport(
        candidates_total=len(outcomes), respondents=len(respondents), dimensions=dimensions, warnings=warnings
    )
