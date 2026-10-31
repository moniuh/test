"""Raport po sesji: metryki liczy kod, AI dodaje tylko uwagi jakościowe z weryfikowanymi cytatami."""
from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Literal

from pydantic import BaseModel, Field

from .detector import LEGAL_CATALOG
from .llm import LLMUnavailable, StructuredLLM
from .models import OPEN_TYPES, QUESTION_TYPES, STAR_LABELS, STAR_ORDER, Source
from .prompts import REPORT_SYSTEM, report_prompt
from .scenarios import Scenario
from .text import collapse_ws, is_verified_quote, plural, word_count
from .transcript import Turn, format_transcript, mentioned_traps, revealed_ids

log = logging.getLogger(__name__)

DISCLAIMER = (
    "Raport treningowy: kandydat był symulowany (AI lub skrypt), a wyniki służą wyłącznie do nauki prowadzenia rozmów. "
    "To nie jest ocena pracownika. Domyślnie widzi go tylko osoba trenująca."
)
TALK_SHARE_TARGET = 0.30
OPEN_SHARE_TARGET = 0.60
MAX_NOTES = 5


class CompetencyCoverage(BaseModel):
    competency_id: str
    name: str
    facts_total: int
    facts_revealed: int
    elements_revealed: list[str]
    elements_missing: list[str]
    star_completeness: float
    covered: bool


class QuestionStats(BaseModel):
    total: int
    by_type: dict[str, int]
    open_count: int
    open_share: float
    leading: int
    multiple: int
    closed: int
    behavioral: int
    followup: int


class TalkRatio(BaseModel):
    trainee_words: int
    candidate_words: int
    ratio: float | None
    trainee_share: float
    assessment: str


class LegalIncident(BaseModel):
    turn: int
    question: str
    category: str
    label: str
    explanation: str
    legal_basis: str
    suggestion: str
    source: Source
    trap_id: str | None = None


class TrapOutcome(BaseModel):
    trap_id: str
    category: str
    label: str
    note: str
    mentioned_turn: int | None
    outcome: Literal["uniknieta", "wejscie", "nie_wystapila"]
    entered_turn: int | None = None


class Recommendation(BaseModel):
    title: str
    detail: str
    priority: int


class QualitativeNote(BaseModel):
    kind: Literal["mocna_strona", "do_poprawy"]
    observation: str
    quote: str
    suggestion: str
    source: Source = "offline"


class ReportNoteAI(BaseModel):
    kind: Literal["mocna_strona", "do_poprawy"]
    observation: str
    quote: str
    suggestion: str


class ReportNotesAI(BaseModel):
    notes: list[ReportNoteAI]


class Report(BaseModel):
    session_id: str
    scenario_id: str
    scenario_title: str
    generated_at: str
    turns: int
    coverage: list[CompetencyCoverage]
    coverage_share: float
    star_average: float
    questions: QuestionStats
    talk: TalkRatio
    incidents: list[LegalIncident]
    traps: list[TrapOutcome]
    recommendations: list[Recommendation]
    notes: list[QualitativeNote] = Field(default_factory=list)
    notes_source: Source = "offline"
    rejected_notes: int = 0
    ai_error: str | None = None
    disclaimer: str = DISCLAIMER


# --- metryki (czyste funkcje) ---
def competency_coverage(scenario: Scenario, turns: list[Turn]) -> list[CompetencyCoverage]:
    revealed = revealed_ids(turns)
    result = []
    for comp in scenario.competencies:
        facts = scenario.facts_for(comp.id)
        available = [el for el in STAR_ORDER if any(f.star == el for f in facts)]
        got = [el for el in STAR_ORDER if any(f.star == el and f.id in revealed for f in facts)]
        result.append(
            CompetencyCoverage(
                competency_id=comp.id,
                name=comp.name,
                facts_total=len(facts),
                facts_revealed=sum(1 for f in facts if f.id in revealed),
                elements_revealed=got,
                elements_missing=[el for el in available if el not in got],
                star_completeness=round(len(got) / len(available), 2) if available else 0.0,
                covered=bool(got),
            )
        )
    return result


def question_stats(turns: list[Turn]) -> QuestionStats:
    by_type = {t: 0 for t in QUESTION_TYPES}
    open_count = 0
    for t in turns:
        by_type[t.coach.question_type] = by_type.get(t.coach.question_type, 0) + 1
        if t.coach.open_form or t.coach.question_type in OPEN_TYPES:
            open_count += 1
    total = len(turns)
    return QuestionStats(
        total=total,
        by_type=by_type,
        open_count=open_count,
        open_share=round(open_count / total, 2) if total else 0.0,
        leading=by_type["sugerujace"],
        multiple=by_type["wielokrotne"],
        closed=by_type["zamkniete"],
        behavioral=by_type["behawioralne"],
        followup=by_type["dopytujace"],
    )


def talk_ratio(turns: list[Turn]) -> TalkRatio:
    trainee = sum(word_count(t.question) for t in turns)
    candidate = sum(word_count(t.answer) for t in turns)
    total = trainee + candidate
    share = round(trainee / total, 2) if total else 0.0
    if not total:
        assessment = "brak danych"
    elif share <= TALK_SHARE_TARGET:
        assessment = "dobra proporcja — kandydat mówił najwięcej"
    elif share <= 0.45:
        assessment = "do poprawy — rekruter mówi zbyt dużo"
    else:
        assessment = "rekruter zdominował rozmowę"
    return TalkRatio(
        trainee_words=trainee,
        candidate_words=candidate,
        ratio=round(trainee / candidate, 2) if candidate else None,
        trainee_share=share,
        assessment=assessment,
    )


def legal_incidents(turns: list[Turn]) -> list[LegalIncident]:
    incidents = []
    for t in turns:
        for f in t.coach.flags:
            incidents.append(
                LegalIncident(
                    turn=t.index + 1,
                    question=t.question,
                    category=f.category,
                    label=f.label,
                    explanation=f.explanation,
                    legal_basis=f.legal_basis,
                    suggestion=f.suggestion,
                    source=f.source,
                    trap_id=f.trap_id,
                )
            )
    return incidents


def trap_outcomes(scenario: Scenario, turns: list[Turn]) -> list[TrapOutcome]:
    mentioned = mentioned_traps(turns)
    outcomes = []
    for trap in scenario.traps:
        label = LEGAL_CATALOG[trap.category].label
        if trap.id not in mentioned:
            outcomes.append(TrapOutcome(trap_id=trap.id, category=trap.category, label=label, note=trap.note, mentioned_turn=None, outcome="nie_wystapila"))
            continue
        at = mentioned[trap.id]
        entered = next(
            (
                t
                for t in turns
                if t.index > at and any(f.trap_id == trap.id or f.category == trap.category for f in t.coach.flags)
            ),
            None,
        )
        outcomes.append(
            TrapOutcome(
                trap_id=trap.id,
                category=trap.category,
                label=label,
                note=trap.note,
                mentioned_turn=at + 1,
                outcome="wejscie" if entered else "uniknieta",
                entered_turn=entered.index + 1 if entered else None,
            )
        )
    return outcomes


def _pct(x: float) -> str:
    return f"{round(x * 100)}%"


def recommendations(
    scenario: Scenario,
    coverage: list[CompetencyCoverage],
    stats: QuestionStats,
    talk: TalkRatio,
    incidents: list[LegalIncident],
    traps: list[TrapOutcome],
) -> list[Recommendation]:
    """Trzy najważniejsze rekomendacje — reguły z priorytetami, liczone w kodzie."""
    recs: list[Recommendation] = []
    if incidents:
        first = incidents[0]
        cats = ", ".join(dict.fromkeys(i.label.lower() for i in incidents))
        recs.append(Recommendation(
            title="Wyeliminuj pytania niedozwolone",
            detail=f"Pytania niedozwolone w tej rozmowie: {len(incidents)} ({cats}). Zamiast „{collapse_ws(first.question)}” zapytaj: {first.suggestion}",
            priority=100,
        ))
    entered = [t for t in traps if t.outcome == "wejscie"]
    if entered:
        recs.append(Recommendation(
            title="Nie dopytuj o wątki prywatne, które kandydat sam porusza",
            detail=entered[0].note,
            priority=90,
        ))
    uncovered = [c for c in coverage if not c.covered]
    if uncovered and stats.total:
        example = scenario.competency(uncovered[0].competency_id)
        recs.append(Recommendation(
            title="Zbadaj wszystkie kompetencje",
            detail=f"Nie wydobyto żadnych faktów dla: {', '.join(c.name for c in uncovered)}. Zacznij od pytania behawioralnego, np. „{example.sample_question if example else 'Proszę opowiedzieć o sytuacji, w której…'}”",
            priority=80,
        ))
    missing_r = [c.name for c in coverage if c.covered and "R" in c.elements_missing]
    if missing_r:
        recs.append(Recommendation(
            title="Dopytuj o rezultat (R w STAR)",
            detail=f"Bez rezultatu zostały: {', '.join(missing_r)}. Zapytaj: „Jaki był efekt?”, „Jak to się skończyło?”, „Po czym poznał(a) Pan/Pani, że się udało?”",
            priority=75,
        ))
    missing_a = [c.name for c in coverage if c.covered and "A" in c.elements_missing]
    if missing_a:
        recs.append(Recommendation(
            title="Dopytuj o konkretne działania (A w STAR)",
            detail=f"Nie poznano osobistych działań kandydata w: {', '.join(missing_a)}. Zapytaj: „Co konkretnie Pan/Pani wtedy zrobił(a)?”",
            priority=72,
        ))
    if talk.trainee_share > TALK_SHARE_TARGET and talk.trainee_words:
        recs.append(Recommendation(
            title="Mów mniej, słuchaj więcej",
            detail=f"Twoje wypowiedzi to {_pct(talk.trainee_share)} wszystkich słów w rozmowie. Celuj w najwyżej {_pct(TALK_SHARE_TARGET)} — krótkie pytania i cisza dają kandydatowi miejsce.",
            priority=70 if talk.trainee_share > 0.45 else 45,
        ))
    if stats.total and stats.behavioral == 0:
        recs.append(Recommendation(
            title="Pytaj o konkretne sytuacje z przeszłości",
            detail="Nie padło żadne pytanie behawioralne. Zacznij od „Proszę opowiedzieć o sytuacji, w której…” i prowadź kandydata przez sytuację, zadanie, działanie i rezultat.",
            priority=68,
        ))
    if stats.total and stats.open_share < OPEN_SHARE_TARGET:
        recs.append(Recommendation(
            title="Zadawaj więcej pytań otwartych",
            detail=f"Pytania otwarte to {_pct(stats.open_share)} (cel: co najmniej {_pct(OPEN_SHARE_TARGET)}). Zamień „Czy…?” na „Jak…?”, „Co…?”, „Proszę opowiedzieć…”.",
            priority=65,
        ))
    if stats.leading:
        recs.append(Recommendation(
            title="Unikaj pytań sugerujących",
            detail=f"Pytania sugerujące odpowiedź: {plural(stats.leading, 'pytanie', 'pytania', 'pytań')}. Kandydat wtedy przytakuje i niczego się nie dowiadujesz. Usuń „prawda?”, „na pewno”, „chyba”.",
            priority=60 + 3 * stats.leading,
        ))
    if stats.multiple:
        recs.append(Recommendation(
            title="Jedno pytanie na raz",
            detail=f"Pytania wielokrotne: {plural(stats.multiple, 'pytanie', 'pytania', 'pytań')} — kandydat odpowiada tylko na część. Zadaj pierwsze, wysłuchaj, potem dopytaj.",
            priority=55 + 3 * stats.multiple,
        ))
    if 0 < stats.total < 5:
        recs.append(Recommendation(
            title="Poprowadź dłuższą rozmowę",
            detail="Sesja była krótka. Zaplanuj 2–3 pytania behawioralne na kompetencję i dopytania o każdy element STAR.",
            priority=50,
        ))
    recs.sort(key=lambda r: -r.priority)
    top = recs[:3]
    positives = [
        Recommendation(title="Utrzymaj dobre nawyki", detail=f"Pytania otwarte: {_pct(stats.open_share)}, dopytania: {stats.followup}. Tak trzymaj — kolejnym krokiem może być trudniejszy scenariusz.", priority=10),
        Recommendation(title="Ćwicz na innych personach", detail="Spróbuj scenariusza z kandydatem zestresowanym albo unikającym konkretów — wymagają innego tempa dopytywania.", priority=5),
        Recommendation(title="Zapisuj notatki STAR w trakcie", detail="Notuj S, T, A, R osobno dla każdej kompetencji — od razu widać, o co jeszcze dopytać.", priority=1),
    ]
    for pos in positives:
        if len(top) >= 3:
            break
        top.append(pos)
    return top


def offline_notes(turns: list[Turn]) -> list[QualitativeNote]:
    """Uwagi regułowe — cytaty to dosłowne pytania trenującego, więc zawsze są prawdziwe."""
    notes: list[QualitativeNote] = []

    def first(pred):
        return next((t for t in turns if pred(t)), None)

    good = first(lambda t: t.coach.question_type == "behawioralne" and t.revealed_fact_ids)
    if good:
        notes.append(QualitativeNote(kind="mocna_strona", observation="Pytanie behawioralne otworzyło konkretną historię kandydata.", quote=good.question, suggestion="Zaczynaj w ten sposób każdą kompetencję, a potem dopytuj o zadanie, działania i rezultat."))
    dig = first(lambda t: t.coach.question_type == "dopytujace" and t.revealed_fact_ids)
    if dig:
        notes.append(QualitativeNote(kind="mocna_strona", observation="Dopytanie pogłębiło odpowiedź i wydobyło kolejny element STAR.", quote=dig.question, suggestion="Dopytuj, dopóki nie usłyszysz mierzalnego rezultatu."))
    for qtype, observation, suggestion in (
        ("sugerujace", "Pytanie podsunęło odpowiedź — kandydat tylko przytaknął.", "Zadaj to samo pytanie neutralnie, bez sugestii oczekiwanej odpowiedzi."),
        ("wielokrotne", "Kilka pytań w jednym — kandydat odpowiedział tylko na część.", "Rozbij je na osobne pytania i zadawaj po kolei."),
        ("zamkniete", "Pytanie zamknięte dało krótką odpowiedź bez konkretów.", "Przeformułuj: „Proszę opowiedzieć o…” albo „Jak…?”."),
    ):
        t = first(lambda t, q=qtype: t.coach.question_type == q)
        if t:
            notes.append(QualitativeNote(kind="do_poprawy", observation=observation, quote=t.question, suggestion=suggestion))
    return notes[:MAX_NOTES]


def _metrics_summary(coverage: list[CompetencyCoverage], stats: QuestionStats, talk: TalkRatio, incidents: list[LegalIncident]) -> str:
    lines = [
        f"Pytań: {stats.total}; otwartych: {_pct(stats.open_share)}; sugerujących: {stats.leading}; wielokrotnych: {stats.multiple}; behawioralnych: {stats.behavioral}; dopytujących: {stats.followup}.",
        f"Udział słów rekrutera: {_pct(talk.trainee_share)}.",
        f"Pytania niedozwolone: {len(incidents)}.",
    ]
    for c in coverage:
        got = ", ".join(STAR_LABELS[e] for e in c.elements_revealed) or "nic"
        lines.append(f"Kompetencja {c.name}: wydobyto {got}.")
    return "\n".join(lines)


def ai_notes(turns: list[Turn], llm: StructuredLLM, summary: str) -> tuple[list[QualitativeNote], int]:
    """Uwagi od modelu; cytat musi być podciągiem transkryptu (po normalizacji białych znaków)."""
    transcript = format_transcript(turns, mask=True, numbered=True)
    result = llm.generate(system=REPORT_SYSTEM, prompt=report_prompt(transcript=transcript, metrics_summary=summary), schema=ReportNotesAI, effort="medium")
    verified: list[QualitativeNote] = []
    rejected = 0
    for note in result.notes:
        if not is_verified_quote(note.quote, transcript):
            rejected += 1
            log.info("Odrzucono uwagę z niezweryfikowanym cytatem: %r", note.quote[:80])
            continue
        verified.append(QualitativeNote(
            kind=note.kind,
            observation=collapse_ws(note.observation)[:500],
            quote=collapse_ws(note.quote).strip("\"'„”“«» ")[:300],
            suggestion=collapse_ws(note.suggestion)[:500],
            source="ai",
        ))
    return verified[:MAX_NOTES], rejected


def build_report(scenario: Scenario, session_id: str, turns: list[Turn], llm: StructuredLLM | None = None) -> Report:
    coverage = competency_coverage(scenario, turns)
    stats = question_stats(turns)
    talk = talk_ratio(turns)
    incidents = legal_incidents(turns)
    traps = trap_outcomes(scenario, turns)
    report = Report(
        session_id=session_id,
        scenario_id=scenario.id,
        scenario_title=scenario.title,
        generated_at=datetime.now(timezone.utc).isoformat(timespec="seconds"),
        turns=len(turns),
        coverage=coverage,
        coverage_share=round(sum(c.covered for c in coverage) / len(coverage), 2) if coverage else 0.0,
        star_average=round(sum(c.star_completeness for c in coverage) / len(coverage), 2) if coverage else 0.0,
        questions=stats,
        talk=talk,
        incidents=incidents,
        traps=traps,
        recommendations=recommendations(scenario, coverage, stats, talk, incidents, traps),
        notes=offline_notes(turns),
    )
    if llm is not None and turns:
        try:
            notes, rejected = ai_notes(turns, llm, _metrics_summary(coverage, stats, talk, incidents))
        except LLMUnavailable as exc:
            log.warning("Uwagi AI niedostępne, zostają regułowe: %s", exc)
            report.ai_error = str(exc)
        else:
            report.rejected_notes = rejected
            if notes:
                report.notes = notes
                report.notes_source = "ai"
    return report
