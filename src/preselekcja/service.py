"""Przypadki użycia aplikacji (bez FastAPI): łączy anonimizację, ocenę, decyzje, audyt i raporty."""
from __future__ import annotations

import json
import os
from datetime import date
from pathlib import Path
from typing import Any, Callable

from pydantic import BaseModel

from .anonymize import CATEGORY_LABELS, anonymize
from .bias import BiasReport, CandidateOutcome, bias_report
from .evaluation import evaluate_candidate
from .explanation import Explanation, build_explanation
from .extract import ExtractionError, extract_text, normalize_input_text
from .llm import StructuredLLM, get_llm
from .models import (
    Decision,
    DecisionCreate,
    Demographics,
    Evaluation,
    EvaluationMode,
    Job,
    JobCreate,
)
from .oversight import OversightReport, is_override, oversight_metrics
from .storage import DemographicsStore, Store, sha256_text


ANONYMOUS_ACTOR = "nieznany"


class NotFound(LookupError):
    pass


class DomainError(ValueError):
    pass


class RankingRow(BaseModel):
    candidate_id: int
    code: str
    score: float | None
    recommendation: str | None
    source: str | None
    must_met: int
    must_total: int
    verdict_counts: dict[str, int]
    injection_flagged: bool
    revealed: bool
    status: str
    source_format: str
    created_at: str


class CandidateView(BaseModel):
    """Widok kandydata w trybie blind — bez danych identyfikujących i bez danych demograficznych."""

    candidate_id: int
    code: str
    job_id: int
    job_title: str
    status: str
    revealed: bool
    source_format: str
    created_at: str
    anonymized_text: str
    anonymization: dict[str, Any]
    evaluation: Evaluation | None
    evaluations_count: int
    decisions: list[Decision]


class RevealResult(BaseModel):
    candidate_id: int
    code: str
    original_text: str
    identity: dict[str, list[str]]
    notice: str


def candidate_code(candidate_id: int) -> str:
    return f"K-{candidate_id:04d}"


def default_examples_dir() -> Path:
    env = os.getenv("PRESELEKCJA_EXAMPLES_DIR")
    return Path(env) if env else Path(__file__).resolve().parents[2] / "examples"


class ScreeningService:
    def __init__(
        self,
        store: Store,
        demographics: DemographicsStore,
        llm_provider: Callable[[], StructuredLLM | None] = get_llm,
        today: Callable[[], date] = date.today,
        examples_dir: Path | None = None,
    ) -> None:
        self.store = store
        self.demographics = demographics
        self.llm_provider = llm_provider
        self.today = today
        self.examples_dir = examples_dir or default_examples_dir()

    # --- oferty ----------------------------------------------------------------
    def create_job(self, actor: str, data: JobCreate) -> Job:
        with self.store.db.transaction() as tx:
            job = self.store.create_job(tx, data, actor)
            self.store.append_audit(
                tx, actor=actor, action="job_created", source="human", entity_type="job", entity_id=job.id,
                input_hash=sha256_text(data.model_dump_json()),
                details={"title": job.title, "requirements": len(job.requirements)},
            )
        return job

    def get_job(self, job_id: int) -> Job:
        job = self.store.get_job(job_id)
        if job is None:
            raise NotFound(f"Nie znaleziono oferty #{job_id}.")
        return job

    def list_jobs(self) -> list[dict[str, Any]]:
        return self.store.list_jobs()

    # --- kandydaci -------------------------------------------------------------
    def add_candidate(
        self,
        actor: str,
        job_id: int,
        *,
        text: str | None = None,
        file_bytes: bytes | None = None,
        filename: str | None = None,
        mode: EvaluationMode = "auto",
    ) -> CandidateView:
        job = self.get_job(job_id)
        if (text and text.strip()) and file_bytes:
            raise DomainError("Podaj albo plik, albo wklejony tekst — nie oba naraz.")
        try:
            if file_bytes is not None:
                original, fmt = extract_text(file_bytes, filename or "")
                raw_hash = sha256_text(file_bytes)
                size = len(file_bytes)
            elif text and text.strip():
                original, fmt = normalize_input_text(text), "text"
                raw_hash = sha256_text(original)
                size = len(original.encode("utf-8"))
            else:
                raise DomainError("Dodaj plik CV (.txt, .pdf, .docx) albo wklej tekst.")
        except ExtractionError as exc:
            raise DomainError(str(exc)) from exc

        anon = anonymize(original)
        anonymization = {
            "counts": anon.counts,
            "labels": {k: CATEGORY_LABELS[k] for k in anon.counts},
            "gender_neutralized": anon.gender_neutralized,
        }
        with self.store.db.transaction() as tx:
            cid = self.store.create_candidate(
                tx, job_id=job.id, source_format=fmt, input_hash=raw_hash, original_text=original,
                anonymized_text=anon.text, identity=anon.identity, anonymization=anonymization,
            )
            # Nazwy pliku nie zapisujemy w dzienniku — często zawiera imię i nazwisko.
            self.store.append_audit(
                tx, actor=actor, action="cv_uploaded", source="human", entity_type="candidate", entity_id=cid,
                input_hash=raw_hash,
                details={"job_id": job.id, "format": fmt, "bytes": size, "masked": anon.counts,
                         "gender_neutralized": anon.gender_neutralized},
            )
        self._evaluate(actor, cid, job, anon.text, mode)
        return self._view(cid)

    def _evaluate(self, actor: str, candidate_id: int, job: Job, anonymized_text: str, mode: EvaluationMode) -> Evaluation:
        llm = self.llm_provider() if mode == "auto" else None
        ev = evaluate_candidate(job, anonymized_text, llm=llm, mode=mode, today=self.today())
        with self.store.db.transaction() as tx:
            eid = self.store.add_evaluation(tx, candidate_id, ev)
            self.store.append_audit(
                tx, actor=actor, action="evaluation_completed", source=ev.source, entity_type="candidate",
                entity_id=candidate_id, input_hash=ev.input_hash,
                details={
                    "evaluation_id": eid, "score": ev.score, "recommendation": ev.recommendation,
                    "verdicts": {r.requirement_id: r.verdict for r in ev.results},
                    "degraded_quotes": sum(1 for r in ev.results if r.model_verdict and r.model_verdict != r.verdict),
                    "injection_flagged": ev.injection.flagged, "fallback_reason": ev.fallback_reason,
                    "llm": getattr(llm, "name", None) if ev.source == "ai" else None,
                },
            )
            if ev.injection.flagged:
                self.store.append_audit(
                    tx, actor="system", action="injection_flagged", source=ev.source, entity_type="candidate",
                    entity_id=candidate_id, input_hash=ev.input_hash,
                    details={"rules": sorted({f.rule for f in ev.injection.findings}),
                             "ai_suspected": ev.injection.ai_suspected},
                )
        return ev

    def reevaluate(self, actor: str, candidate_id: int, mode: EvaluationMode) -> CandidateView:
        row = self._candidate_row(candidate_id)
        job = self.get_job(row["job_id"])
        self._evaluate(actor, candidate_id, job, row["anonymized_text"], mode)
        return self._view(candidate_id)

    def _candidate_row(self, candidate_id: int):
        row = self.store.get_candidate(candidate_id)
        if row is None:
            raise NotFound(f"Nie znaleziono kandydata #{candidate_id}.")
        return row

    def _view(self, candidate_id: int) -> CandidateView:
        row = self._candidate_row(candidate_id)
        job = self.get_job(row["job_id"])
        latest = self.store.latest_evaluation(candidate_id)
        return CandidateView(
            candidate_id=row["id"], code=candidate_code(row["id"]), job_id=job.id, job_title=job.title,
            status=row["status"], revealed=bool(row["revealed"]), source_format=row["source_format"],
            created_at=row["created_at"], anonymized_text=row["anonymized_text"],
            anonymization=json.loads(row["anonymization_json"]), evaluation=latest[1] if latest else None,
            evaluations_count=self.store.evaluation_count(candidate_id),
            decisions=self.store.decisions_for(candidate_id),
        )

    def candidate_detail(self, actor: str, candidate_id: int) -> CandidateView:
        view = self._view(candidate_id)
        self.store.append_audit(
            actor=actor, action="candidate_viewed", source="human", entity_type="candidate", entity_id=candidate_id,
        )
        return view

    def ranking(self, job_id: int) -> list[RankingRow]:
        job = self.get_job(job_id)
        rows: list[RankingRow] = []
        for c in self.store.list_candidates(job.id):
            latest = self.store.latest_evaluation(c["id"])
            ev = latest[1] if latest else None
            counts = {k: 0 for k in ("meets", "partial", "fails", "no_data")}
            must_met = must_total = 0
            if ev:
                for r in ev.results:
                    counts[r.verdict] += 1
                    if r.kind == "must":
                        must_total += 1
                        must_met += int(r.verdict == "meets")
            rows.append(
                RankingRow(
                    candidate_id=c["id"], code=candidate_code(c["id"]), score=ev.score if ev else None,
                    recommendation=ev.recommendation if ev else None, source=ev.source if ev else None,
                    must_met=must_met, must_total=must_total, verdict_counts=counts,
                    injection_flagged=bool(ev and ev.injection.flagged), revealed=bool(c["revealed"]),
                    status=c["status"], source_format=c["source_format"], created_at=c["created_at"],
                )
            )
        rows.sort(key=lambda r: (-(r.score if r.score is not None else -1), -r.must_met, r.candidate_id))
        return rows

    def reveal(self, actor: str, candidate_id: int, reason: str) -> RevealResult:
        row = self._candidate_row(candidate_id)
        with self.store.db.transaction() as tx:
            self.store.mark_revealed(tx, candidate_id)
            self.store.append_audit(
                tx, actor=actor, action="identity_revealed", source="human", entity_type="candidate",
                entity_id=candidate_id, input_hash=row["input_hash"], details={"reason": reason.strip()},
            )
        return RevealResult(
            candidate_id=candidate_id, code=candidate_code(candidate_id), original_text=row["original_text"],
            identity=json.loads(row["identity_json"]),
            notice="Dane osobowe odkryte świadomie — zdarzenie zapisano w dzienniku audytowym wraz z uzasadnieniem.",
        )

    # --- decyzje ---------------------------------------------------------------
    def decide(self, actor: str, candidate_id: int, data: DecisionCreate) -> Decision:
        if not actor or actor == ANONYMOUS_ACTOR:
            raise DomainError("Decyzja wymaga identyfikacji osoby decydującej (nagłówek X-Actor).")
        self._candidate_row(candidate_id)
        latest = self.store.latest_evaluation(candidate_id)
        rec = latest[1].recommendation if latest else None
        override = is_override(rec, data.status)
        viewed = self.store.has_viewed(candidate_id, actor)
        with self.store.db.transaction() as tx:
            decision = self.store.add_decision(
                tx, candidate_id=candidate_id, status=data.status, reason=data.reason, actor=actor,
                ai_recommendation=rec, evaluation_id=latest[0] if latest else None, is_override=override,
                viewed_before=viewed,
            )
            self.store.set_status(tx, candidate_id, data.status)
            self.store.append_audit(
                tx, actor=actor, action="decision_recorded", source="human", entity_type="candidate",
                entity_id=candidate_id, input_hash=sha256_text(data.model_dump_json()),
                details={
                    "decision_id": decision.id, "status": data.status, "reason": data.reason,
                    "ai_recommendation": rec, "override": override, "viewed_before": viewed,
                },
            )
        return decision

    # --- wyjaśnienie -----------------------------------------------------------
    def explanation(self, actor: str, candidate_id: int, use_ai: bool = False) -> Explanation:
        row = self._candidate_row(candidate_id)
        job = self.get_job(row["job_id"])
        latest = self.store.latest_evaluation(candidate_id)
        if latest is None:
            raise DomainError("Kandydat nie ma jeszcze oceny.")
        llm = self.llm_provider() if use_ai else None
        exp = build_explanation(job, latest[1], row["status"], llm=llm, use_ai=use_ai)
        self.store.append_audit(
            actor=actor, action="explanation_generated", source=exp.source, entity_type="candidate",
            entity_id=candidate_id, input_hash=sha256_text(exp.body), details={"fallback_reason": exp.fallback_reason},
        )
        return exp

    # --- ankieta demograficzna -------------------------------------------------
    def submit_demographics(self, candidate_id: int, data: Demographics) -> None:
        self._candidate_row(candidate_id)
        self.demographics.upsert(candidate_id, data.gender, data.age_group, data.disability)
        # W dzienniku tylko fakt złożenia ankiety — bez wartości.
        self.store.append_audit(
            actor="kandydat", action="demographics_submitted", source="human", entity_type="candidate",
            entity_id=candidate_id,
        )

    def withdraw_demographics(self, candidate_id: int) -> bool:
        self._candidate_row(candidate_id)
        removed = self.demographics.delete(candidate_id)
        self.store.append_audit(
            actor="kandydat", action="demographics_withdrawn", source="human", entity_type="candidate",
            entity_id=candidate_id, details={"removed": removed},
        )
        return removed

    # --- raporty ---------------------------------------------------------------
    def oversight_report(self, job_id: int | None = None) -> OversightReport:
        if job_id is not None:
            self.get_job(job_id)
        return oversight_metrics(self.store.all_decisions(job_id))

    def bias(self, job_id: int | None = None) -> BiasReport:
        if job_id is not None:
            self.get_job(job_id)
        demo = self.demographics.all()
        outcomes = []
        for c in self.store.list_candidates(job_id):
            latest = self.store.latest_evaluation(c["id"])
            outcomes.append(
                CandidateOutcome(
                    recommendation=latest[1].recommendation if latest else None,
                    status=c["status"],
                    demographics=demo.get(c["id"]),
                )
            )
        return bias_report(outcomes)

    def audit(self, **filters: Any) -> list[dict[str, Any]]:
        return self.store.audit_entries(**filters)

    def verify_audit(self) -> dict[str, Any]:
        return self.store.verify_audit_chain()

    # --- dane przykładowe ------------------------------------------------------
    def seed_examples(self, actor: str) -> dict[str, Any]:
        base = self.examples_dir
        job_file = base / "oferta.json"
        if not job_file.exists():
            raise NotFound("Brak katalogu z danymi przykładowymi (examples/oferta.json).")
        job = self.create_job(actor, JobCreate.model_validate_json(job_file.read_text(encoding="utf-8")))
        manifest = json.loads((base / "kandydaci.json").read_text(encoding="utf-8"))
        created = []
        for item in manifest:
            path = base / item["file"]
            view = self.add_candidate(actor, job.id, file_bytes=path.read_bytes(), filename=path.name)
            if item.get("demographics"):
                self.submit_demographics(view.candidate_id, Demographics(consent=True, **item["demographics"]))
            created.append(view.code)
        self.store.append_audit(
            actor=actor, action="demo_seeded", source="system", entity_type="job", entity_id=job.id,
            details={"candidates": created},
        )
        return {"job_id": job.id, "candidates": created}
