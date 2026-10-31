"""Metryki nadzoru człowieka (art. 14 AI Act): override rate jako wskaźnik automation bias.

Decyzja jest „niezgodna z AI” (override), gdy rekomendacja miała kierunek, a człowiek wybrał przeciwny:
- rekomendacja „zaproś” → decyzja „odrzucony”,
- rekomendacja „niedopasowanie” → decyzja „zaproszony”.
Rekomendacja „do rozważenia” nie wskazuje kierunku, więc nie wchodzi do mianownika.
Liczymy ostatnią decyzję końcową (zaproszony/odrzucony) każdego kandydata; „wstrzymany” to nie decyzja końcowa.
"""
from __future__ import annotations

from typing import Iterable

from pydantic import BaseModel, Field

from .models import Decision

MIN_DECISIONS_TO_INTERPRET = 10
LOW_OVERRIDE = 0.05
HIGH_OVERRIDE = 0.40


def is_override(recommendation: str | None, status: str) -> bool:
    return (recommendation == "invite" and status == "rejected") or (
        recommendation == "mismatch" and status == "invited"
    )


class OversightReport(BaseModel):
    final_decisions: int
    directional_decisions: int
    overrides: int
    override_rate: float | None
    agreement_rate: float | None
    on_hold: int
    decisions_without_review: int
    interpretation: list[str] = Field(default_factory=list)


def oversight_metrics(decisions: Iterable[Decision]) -> OversightReport:
    latest_final: dict[int, Decision] = {}
    latest_any: dict[int, Decision] = {}
    for d in sorted(decisions, key=lambda d: (d.created_at, d.id)):
        latest_any[d.candidate_id] = d
        if d.status in ("invited", "rejected"):
            latest_final[d.candidate_id] = d
    finals = list(latest_final.values())
    directional = [d for d in finals if d.ai_recommendation in ("invite", "mismatch")]
    overrides = sum(1 for d in directional if is_override(d.ai_recommendation, d.status))
    rate = round(overrides / len(directional), 3) if directional else None
    without_review = sum(1 for d in finals if not d.viewed_before)
    on_hold = sum(1 for d in latest_any.values() if d.status == "on_hold")

    notes: list[str] = []
    if len(directional) < MIN_DECISIONS_TO_INTERPRET:
        notes.append(
            f"Za mało decyzji z jednoznaczną rekomendacją ({len(directional)} < {MIN_DECISIONS_TO_INTERPRET}), "
            "by interpretować override rate."
        )
    elif rate is not None and rate < LOW_OVERRIDE:
        notes.append(
            "Bardzo niski odsetek decyzji niezgodnych z AI — możliwy automation bias (bezrefleksyjne "
            "zatwierdzanie). Sprawdź próbkę decyzji i czy rekruterzy czytają dowody."
        )
    elif rate is not None and rate > HIGH_OVERRIDE:
        notes.append(
            "Wysoki odsetek decyzji niezgodnych z AI — profil stanowiska lub ocena mogą być źle skalibrowane."
        )
    else:
        notes.append("Odsetek decyzji niezgodnych z AI w typowym zakresie — utrzymuj regularny przegląd.")
    if without_review:
        notes.append(
            f"{without_review} decyzji podjęto bez otwarcia szczegółów kandydata (dowodów) — sygnał ryzyka automation bias."
        )
    return OversightReport(
        final_decisions=len(finals),
        directional_decisions=len(directional),
        overrides=overrides,
        override_rate=rate,
        agreement_rate=round(1 - rate, 3) if rate is not None else None,
        on_hold=on_hold,
        decisions_without_review=without_review,
        interpretation=notes,
    )
