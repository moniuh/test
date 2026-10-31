"""Ocena kandydata: tryb AI (z weryfikacją cytatów) albo offline, zawsze z wynikiem liczonym w kodzie."""
from __future__ import annotations

import hashlib
import json
from datetime import date, datetime, timezone
from typing import Literal

from pydantic import BaseModel

from .injection import detect_injection, scoring_text, suspicious_sentences
from .llm import LLMUnavailable, StructuredLLM
from .matching import evaluate_offline
from .models import Evaluation, EvaluationMode, Job, RequirementResult, Span
from .prompts import SCREENING_SYSTEM
from .scoring import compute_score
from .textutil import find_quote


class AIRequirementVerdict(BaseModel):
    requirement_id: str
    verdict: Literal["meets", "partial", "fails", "no_data"]
    quote: str
    rationale: str


class AIScreening(BaseModel):
    verdicts: list[AIRequirementVerdict]
    injection_suspected: bool
    injection_note: str


def _escape(text: str) -> str:
    """Neutralizuje nawiasy ostre, żeby dane nie mogły zamknąć tagu XML w prompcie."""
    return text.replace("<", "‹").replace(">", "›")


def build_screening_prompt(job: Job, cv_text: str) -> str:
    lines = [f"<stanowisko>{_escape(job.title)}</stanowisko>", "<wymagania>"]
    for r in job.requirements:
        years = f' min_lat="{r.min_years:g}"' if r.min_years else ""
        kind = "obowiązkowe" if r.kind == "must" else "mile widziane"
        lines.append(f'<wymaganie id="{r.id}" typ="{kind}"{years}>{_escape(r.name)}</wymaganie>')
    lines += ["</wymagania>", "<cv>", _escape(cv_text), "</cv>"]
    return "\n".join(lines)


def input_hash(job: Job, text: str) -> str:
    payload = json.dumps(
        {"requirements": [r.model_dump() for r in job.requirements], "title": job.title, "text": text},
        ensure_ascii=False,
        sort_keys=True,
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def apply_ai_verdicts(job: Job, ai: AIScreening, sent_text: str, display_text: str) -> list[RequirementResult]:
    """Łączy werdykty modelu z profilem i weryfikuje każdy cytat w kodzie.

    Werdykt inny niż „brak danych” wymaga cytatu, który jest podciągiem tekstu wysłanego do modelu;
    w przeciwnym razie jest degradowany do „brak danych” (zmyślony dowód nie może podnieść wyniku).
    """
    by_id: dict[str, AIRequirementVerdict] = {}
    for v in ai.verdicts:
        by_id.setdefault(v.requirement_id.strip(), v)
    results: list[RequirementResult] = []
    for req in job.requirements:
        base = dict(requirement_id=req.id, name=req.name, kind=req.kind, weight=req.weight)
        v = by_id.get(req.id)
        if v is None:
            results.append(RequirementResult(**base, verdict="no_data", note="Model nie ocenił tego wymagania."))
            continue
        rationale = " ".join(v.rationale.split())[:500]
        if v.verdict == "no_data":
            results.append(RequirementResult(**base, verdict="no_data", model_verdict="no_data", rationale=rationale))
            continue
        quote = v.quote.strip()
        in_sent = find_quote(sent_text, quote)
        span = find_quote(display_text, quote) if in_sent else None
        if span is None:
            results.append(
                RequirementResult(
                    **base,
                    verdict="no_data",
                    model_verdict=v.verdict,
                    rationale=rationale,
                    note="Cytat podany przez model nie występuje w CV — werdykt zdegradowano do „brak danych”.",
                )
            )
            continue
        start, end = span
        results.append(
            RequirementResult(
                **base,
                verdict=v.verdict,
                model_verdict=v.verdict,
                evidence=display_text[start:end],
                evidence_start=start,
                evidence_end=end,
                rationale=rationale,
                note="Cytat zweryfikowany w treści CV.",
            )
        )
    return results


def evaluate_candidate(
    job: Job,
    anonymized_text: str,
    *,
    llm: StructuredLLM | None,
    mode: EvaluationMode = "auto",
    today: date | None = None,
) -> Evaluation:
    """Ocenia CV (zanonimizowane) względem profilu. AI tylko gdy dostępne; inaczej offline."""
    report = detect_injection(anonymized_text)
    excluded = suspicious_sentences(anonymized_text, report)
    report.omitted = [Span(start=s.start, end=s.end) for s in excluded]
    text_for_model = scoring_text(anonymized_text, report)

    results: list[RequirementResult] | None = None
    source: Literal["ai", "offline"] = "offline"
    fallback_reason: str | None = None
    if mode == "auto" and llm is not None:
        try:
            ai = llm.generate(
                system=SCREENING_SYSTEM,
                prompt=build_screening_prompt(job, text_for_model),
                schema=AIScreening,
                effort="medium",
            )
            if not ai.verdicts:
                raise LLMUnavailable("Model nie zwrócił żadnych werdyktów")
            results = apply_ai_verdicts(job, ai, text_for_model, anonymized_text)
            source = "ai"
            if ai.injection_suspected:
                report.ai_suspected = True
                report.ai_note = " ".join(ai.injection_note.split())[:300]
                report.flagged = True
        except LLMUnavailable as exc:
            fallback_reason = f"Model AI niedostępny ({exc}) — użyto oceny offline."

    if results is None:
        results = evaluate_offline(job.requirements, anonymized_text, excluded, today)

    score = compute_score(results)
    return Evaluation(
        source=source,
        score=score.score,
        recommendation=score.recommendation,
        gates=score.gates,
        results=results,
        injection=report,
        fallback_reason=fallback_reason,
        weighted_points=score.weighted_points,
        max_points=score.max_points,
        input_hash=input_hash(job, text_for_model),
        created_at=datetime.now(timezone.utc).isoformat(timespec="seconds"),
    )
