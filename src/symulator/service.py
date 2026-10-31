"""Warstwa usług: przebieg sesji (start → tury → raport → eksport), bez zależności od FastAPI."""
from __future__ import annotations

import threading
import uuid
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor

from pydantic import BaseModel

from .candidate import candidate_reply
from .coach import refine_with_ai, rule_analysis
from .detector import LEGAL_CATALOG
from .export import session_to_markdown
from .llm import StructuredLLM, get_llm
from .models import EMOTION_LABELS, OPEN_TYPES, STAR_LABELS
from .report import Report, build_report
from .scenarios import Scenario, ScenarioNotFound, default_scenarios
from .storage import SessionRecord, SessionStore, now_iso
from .text import word_count
from .transcript import Turn, current_topic, mentioned_traps, revealed_ids, star_progress

MAX_TURNS = 60

_STAR_HINTS = {
    "S": "Poproś o konkretną sytuację: „Proszę opowiedzieć o sytuacji, w której…”.",
    "T": "Dopytaj o zadanie: „Jaka była Pana/Pani rola? Co trzeba było osiągnąć?”.",
    "A": "Dopytaj o działania: „Co konkretnie Pan/Pani wtedy zrobił(a)?”.",
    "R": "Dopytaj o rezultat: „Jaki był efekt? Jak to się skończyło?”.",
}


class SessionNotFound(LookupError):
    pass


class SessionFinished(RuntimeError):
    pass


class TurnLimitReached(RuntimeError):
    pass


class EmptySession(RuntimeError):
    pass


class TurnResult(BaseModel):
    turn: dict
    progress: list[dict]
    star_hint: str
    trap_notice: str | None
    stats: dict


def public_turn(turn: Turn) -> dict:
    data = turn.model_dump()
    data["emotion_label"] = EMOTION_LABELS.get(turn.emotion, turn.emotion)
    data["number"] = turn.index + 1
    return data


def live_stats(turns: list[Turn]) -> dict:
    total = len(turns)
    open_count = sum(1 for t in turns if t.coach.open_form or t.coach.question_type in OPEN_TYPES)
    trainee = sum(word_count(t.question) for t in turns)
    candidate = sum(word_count(t.answer) for t in turns)
    return {
        "questions": total,
        "open_share": round(open_count / total, 2) if total else 0.0,
        "trainee_share": round(trainee / (trainee + candidate), 2) if trainee + candidate else 0.0,
        "flags": sum(len(t.coach.flags) for t in turns),
        "facts_revealed": len(revealed_ids(turns)),
    }


def star_hint(turns: list[Turn], scenario: Scenario) -> str:
    """Podpowiedź coacha: który element STAR wydobyć teraz."""
    revealed = revealed_ids(turns)
    topic = current_topic(turns, scenario)
    if topic:
        missing = [f.star for f in scenario.facts_for(topic) if f.id not in revealed]
        comp = scenario.competency(topic)
        if missing:
            return f"{comp.name}: brakuje — {', '.join(STAR_LABELS[m] for m in missing)}. {_STAR_HINTS[missing[0]]}"
        untouched = [c for c in scenario.competencies if not any(f.id in revealed for f in scenario.facts_for(c.id))]
        if untouched:
            return f"{comp.name}: historia kompletna (S-T-A-R). Przejdź do kolejnej kompetencji: {untouched[0].name}."
        return "Wszystkie kompetencje zbadane. Możesz domknąć rozmowę i zobaczyć raport."
    names = ", ".join(c.name for c in scenario.competencies)
    return f"Zacznij od pytania behawioralnego o jedną z kompetencji ({names}). {_STAR_HINTS['S']}"


class SimulatorService:
    def __init__(
        self,
        store: SessionStore,
        scenarios: dict[str, Scenario] | None = None,
        llm_provider: Callable[[], StructuredLLM | None] = get_llm,
    ) -> None:
        self.store = store
        self.scenarios = scenarios if scenarios is not None else default_scenarios()
        self.llm_provider = llm_provider
        self._locks: dict[str, threading.Lock] = {}
        self._locks_guard = threading.Lock()

    def _lock(self, session_id: str) -> threading.Lock:
        with self._locks_guard:
            return self._locks.setdefault(session_id, threading.Lock())

    def scenario(self, scenario_id: str) -> Scenario:
        try:
            return self.scenarios[scenario_id]
        except KeyError as exc:
            raise ScenarioNotFound(scenario_id) from exc

    def ai_enabled(self) -> bool:
        return self.llm_provider() is not None

    # --- sesje ---
    def start_session(self, scenario_id: str) -> SessionRecord:
        self.scenario(scenario_id)
        return self.store.create_session(uuid.uuid4().hex[:12], scenario_id, ai_enabled=self.ai_enabled())

    def load(self, session_id: str) -> tuple[SessionRecord, Scenario, list[Turn]]:
        record = self.store.get_session(session_id)
        if record is None:
            raise SessionNotFound(session_id)
        return record, self.scenario(record.scenario_id), self.store.get_turns(session_id)

    def session_view(self, session_id: str) -> dict:
        record, scenario, turns = self.load(session_id)
        return {
            "session": record.model_dump(),
            "scenario": scenario.public_view(),
            "turns": [public_turn(t) for t in turns],
            "progress": star_progress(turns, scenario),
            "star_hint": star_hint(turns, scenario),
            "stats": live_stats(turns),
        }

    def list_sessions(self) -> list[dict]:
        summaries = self.store.report_summaries()
        out = []
        for rec in self.store.list_sessions():
            scenario = self.scenarios.get(rec.scenario_id)
            out.append({
                **rec.model_dump(),
                "scenario_title": scenario.title if scenario else rec.scenario_id,
                "summary": summaries.get(rec.id),
            })
        return out

    def ask(self, session_id: str, question: str) -> TurnResult:
        question = question.strip()
        with self._lock(session_id):
            record, scenario, turns = self.load(session_id)
            if record.status == "zakonczona":
                raise SessionFinished(session_id)
            if len(turns) >= MAX_TURNS:
                raise TurnLimitReached(session_id)

            llm = self.llm_provider()
            active_traps = [t for tid in mentioned_traps(turns) if (t := scenario.trap(tid))]
            analysis = rule_analysis(question, has_history=bool(turns), active_traps=active_traps)
            last_answer = turns[-1].answer if turns else ""
            if llm is None:
                reply = candidate_reply(scenario, question, analysis, turns, None)
                final_analysis = analysis
            else:
                # Coach AI i kandydat AI to niezależne wywołania — równolegle skracają czas tury o połowę.
                with ThreadPoolExecutor(max_workers=2) as pool:
                    coach_future = pool.submit(refine_with_ai, analysis, question=question, last_answer=last_answer, llm=llm)
                    reply_future = pool.submit(candidate_reply, scenario, question, analysis, turns, llm)
                    final_analysis = coach_future.result()
                    reply = reply_future.result()

            turn = Turn(
                index=len(turns),
                question=question,
                answer=reply.answer,
                emotion=reply.emotion,
                candidate_source=reply.source,
                revealed_fact_ids=reply.revealed_fact_ids,
                trap_ids=reply.trap_ids,
                coach=final_analysis,
                created_at=now_iso(),
                candidate_note=reply.note,
            )
            self.store.add_turn(session_id, turn)

        all_turns = [*turns, turn]
        trap_notice = None
        if turn.trap_ids:
            labels = ", ".join(LEGAL_CATALOG[scenario.trap(t).category].label.lower() for t in turn.trap_ids)
            trap_notice = (
                f"Uwaga: kandydat sam poruszył wątek prywatny ({labels}). Nie dopytuj — przyjmij informację neutralnie "
                "i wróć do kompetencji."
            )
        return TurnResult(
            turn=public_turn(turn),
            progress=star_progress(all_turns, scenario),
            star_hint=star_hint(all_turns, scenario),
            trap_notice=trap_notice,
            stats=live_stats(all_turns),
        )

    def finish(self, session_id: str) -> Report:
        with self._lock(session_id):
            record, scenario, turns = self.load(session_id)
            if record.status == "zakonczona":
                existing = self.store.get_report(session_id)
                if existing:
                    return existing
            if not turns:
                raise EmptySession(session_id)
            report = build_report(scenario, session_id, turns, self.llm_provider())
            self.store.finish_session(session_id, report)
            return report

    def report(self, session_id: str) -> Report | None:
        self.load(session_id)
        return self.store.get_report(session_id)

    def export_markdown(self, session_id: str) -> str:
        record, scenario, turns = self.load(session_id)
        return session_to_markdown(record, scenario, turns, self.store.get_report(session_id))

    def delete(self, session_id: str) -> None:
        if not self.store.delete_session(session_id):
            raise SessionNotFound(session_id)
