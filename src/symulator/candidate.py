"""Kandydat w rozmowie: AI (Claude gra personę) albo deterministyczny kandydat skryptowy offline.

Kandydat skryptowy dopasowuje pytanie do ukrytych faktów po słowach kluczowych (z obsługą fleksji),
wskazówkach STAR („co Pan zrobił?” → Działanie, „jaki był efekt?” → Rezultat) i bieżącym temacie.
Fakty ujawnia tylko na pytania otwarte; zamknięte zbywa, na niedozwolone reaguje zakłopotaniem.
"""
from __future__ import annotations

import logging
import re

from pydantic import BaseModel, Field

from .coach import CoachAnalysis
from .llm import LLMUnavailable, StructuredLLM
from .models import Emotion, Source
from .privacy import mask_pii
from .prompts import candidate_prompt, candidate_system_prompt
from .question_types import classify_question
from .scenarios import HiddenFact, Scenario
from .text import collapse_ws, count_keyword_hits, normalize, tokens
from .transcript import Turn, current_topic, format_transcript, mentioned_traps, revealed_ids

log = logging.getLogger(__name__)

MAX_ANSWER_CHARS = 2000
REVEAL_THRESHOLD = 2.5


class CandidateReply(BaseModel):
    answer: str
    revealed_fact_ids: list[str] = Field(default_factory=list)
    trap_ids: list[str] = Field(default_factory=list)
    emotion: str = "neutralna"
    source: Source = "offline"
    note: str | None = None


class CandidateReplyAI(BaseModel):
    """Schemat wyjścia modelu grającego kandydata."""

    answer: str
    revealed_fact_ids: list[str]
    mentioned_trap_ids: list[str]
    emotion: Emotion


# --- rozpoznawanie sytuacji w rozmowie (tekst znormalizowany) ---
_GREETING = re.compile(r"^(?:dzien dobry|witam|witamy|czesc|dobry wieczor|halo)\b")
_INTRO = re.compile(
    r"\b(?:opowiedz|opowie|prosze opowiedziec|prosze powiedziec|prosze przedstawic|przedstaw|powie|powiedz)\w*(?: \w+){0,4} (?:o sobie|sie)\b"
    r"|\bkim (?:pan|pani) jest\b|\bkilka slow o sobie\b|\bco moze (?:pan|pani) powiedziec o sobie\b|\bswoim doswiadczeniu\b"
)
_AI_QUESTION = re.compile(
    r"\bjestes\w* (?:ai|botem|sztuczn\w*|modelem|programem|czlowiekiem|prawdziw\w*)\b"
    r"|\bczy (?:pan|pani) jest (?:ai|botem|sztuczn\w*|prawdziw\w*|czlowiekiem|programem)\b|\bsztuczn\w* inteligencj\w*"
)
_CLOSING = re.compile(
    r"\bdziekuj\w*(?: \w+){0,3} (?:za rozmowe|za spotkanie|za poswiecony czas)|\bodezwiemy sie\b|\bdo widzenia\b"
    r"|\bkonczymy\b|\bto (?:juz )?wszystko z mojej strony\b"
)
_TOPICS: dict[str, re.Pattern[str]] = {
    "pytania_do_rekrutera": re.compile(r"\bczy ma (?:pan|pani) (?:jakies |do nas |do mnie )?pytani\w*|\bpytani\w* do (?:nas|mnie)\b|\bmasz (?:jakies )?pytania\b"),
    "oczekiwania_finansowe": re.compile(
        r"\boczekiwan\w* (?:finansow|placow|wynagrodz)\w*|\boczekiwan\w*(?: \w+){0,3} wynagrodz\w*|\bile (?:chcialby|chcialaby|chce)(?: pan| pani)? zarabiac\b"
        r"|\bwidelk\w*|\bjakie wynagrodzenie (?:pana|pania) satysfakcjonuje\b"
    ),
    "termin_rozpoczecia": re.compile(
        r"\bokres\w* wypowiedzeni\w*|\bkiedy (?:moze|moglby|moglaby|mogl|mogla|bylby|bylaby)(?: pan| pani)? (?:zaczac|rozpoczac|dolaczyc|gotow)\w*"
        r"|\bod kiedy\b|\bdata rozpoczecia\b|\bjak szybko (?:moze|moglby|moglaby)"
    ),
    "dyspozycyjnosc": re.compile(
        r"\bdyspozycyj\w*|\bweekend\w*|\bgrafik\w*|\bnadgodzin\w*|\bzmianow\w*|\bnocn\w*|\bnocki\b|\bsobot\w*|\bniedziel\w*"
        r"|\bgodzin\w* pracy\b|\bdojazd\w*|\bdojezdza\w*|\bdelegacj\w*|\bpraca hybrydow\w*|\bpraca zdaln\w*"
    ),
    "slabe_strony": re.compile(r"\bslab\w* stron\w*|\bwad(?:a|y|e|ami)\b|\bnad czym\b(?: \w+){0,3} popracowac\b|\bobszar\w* do (?:rozwoju|poprawy)\b"),
    "mocne_strony": re.compile(r"\bmocn\w* stron\w*|\bzalet\w*|\bw czym jest (?:pan|pani) (?:dobry|dobra|najlepszy|najlepsza)\b|\bco (?:pan|pani) robi najlepiej\b"),
    "motywacja": re.compile(
        r"\bdlaczego\b(?: \w+){0,4} (?:aplikuj|aplikowal|zglosil|zglosila|chce|chcialby|chcialaby|interesuj|zainteresowal|zmieni|odejsc|odchodzi|szuka)\w*"
        r"|\bco (?:pana|pania|cie) (?:sklonilo|przyciagnelo|zainteresowalo)\b|\bdlaczego (?:wlasnie )?(?:my|nasza firma|nasz sklep|u nas|ta firma|to stanowisko)\b"
        r"|\bmotywacj\w* do (?:zmiany|aplikowania)\b|\bdlaczego (?:pan|pani) (?:aplikuje|chce)\b"
    ),
}

_GENERIC_AWKWARD = [
    "Przepraszam, ale nie czuję się komfortowo z tym pytaniem. Wolałabym/wolałbym rozmawiać o doświadczeniu zawodowym.",
]
_LEADING_ANSWERS = {
    "gadatliwy": ["Tak, oczywiście, absolutnie się zgadzam!", "No pewnie, że tak!", "Tak, tak, zdecydowanie."],
    "zestresowany": ["Yyy… tak, chyba tak.", "Tak… oczywiście.", "No… tak, zgadzam się."],
    "unikajacy_konkretow": ["Jasne, to oczywiste.", "No pewnie, wiadomo.", "Tak, zgadza się, generalnie tak."],
}
_MULTI_PREFIX = {
    "gadatliwy": "O, dużo pytań naraz! To zacznę od pierwszego…",
    "zestresowany": "Przepraszam, odpowiem może na pierwszą część…",
    "unikajacy_konkretow": "To może po kolei, zacznę od pierwszego.",
}
_MULTI_SUFFIX = {
    "zestresowany": "A jaka była druga część pytania? Przepraszam, uciekła mi.",
}
_VAGUE_RESULT = "Ogólnie skończyło się dobrze, tak myślę."
_GREETING_ANSWER = "Dzień dobry, dziękuję za zaproszenie na rozmowę."
_CLOSING_ANSWER = "Dziękuję za rozmowę! Czekam na informację o kolejnych krokach."
_AI_TRANSPARENCY = (
    "Tak — jestem symulowanym kandydatem w narzędziu treningowym; moją rolę odgrywa program (AI lub skrypt). "
    "Możemy wrócić do rozmowy?"
)


def _pick(options: list[str], index: int) -> str:
    return options[index % len(options)] if options else ""


def _first_part(question: str) -> str:
    """Pierwsze pytanie z pytania wielokrotnego (kandydat odpowiada tylko na nie)."""
    head = question.split("?")[0]
    match = re.search(r"\s(?:i|a|oraz|albo|lub)\s+(?:jak|jaki\w*|co|czego|czym|dlaczego|czemu|ile|kiedy|gdzie|kto|czy)\b", head, re.IGNORECASE)
    return (head[: match.start()] if match else head).strip() + "?"


class OfflineCandidate:
    """Deterministyczny kandydat skryptowy — ta sama historia i pytanie dają tę samą odpowiedź."""

    def __init__(self, scenario: Scenario) -> None:
        self.scenario = scenario
        self.persona = scenario.persona
        self._order = {f.id: i for i, f in enumerate(scenario.facts)}

    # --- dopasowanie faktów ---
    def _score(self, fact: HiddenFact, toks: list[str], cues: list[str], topic: str | None) -> float:
        comp = self.scenario.competency(fact.competency)
        score = 2.0 * count_keyword_hits(toks, fact.keywords)
        score += count_keyword_hits(toks, comp.keywords) if comp else 0
        if fact.star in cues:
            score += 2.0
        if topic == fact.competency:
            score += 1.5
        return score

    def _best_fact(self, question: str, cues: list[str], topic: str | None, revealed: set[str], *, eligible_only: bool = True) -> tuple[HiddenFact | None, float]:
        toks = tokens(question)
        best: tuple[HiddenFact | None, float] = (None, 0.0)
        for fact in self.scenario.facts:
            if fact.id in revealed:
                continue
            if eligible_only and not all(r in revealed for r in fact.requires):
                continue
            score = self._score(fact, toks, cues, topic)
            if score > best[1]:
                best = (fact, score)
        return best

    def _next_in_topic(self, topic: str | None, revealed: set[str]) -> HiddenFact | None:
        if not topic:
            return None
        return next((f for f in self.scenario.facts_for(topic) if f.id not in revealed), None)

    def _untouched_hint(self, revealed: set[str]) -> str:
        touched = {self.scenario.fact(fid).competency for fid in revealed if self.scenario.fact(fid)}
        for comp in self.scenario.competencies:
            if comp.id not in touched:
                first = self.scenario.facts_for(comp.id)
                if first and first[0].hint:
                    return first[0].hint
        return ""

    # --- styl ---
    def _wrap_reveal(self, content: str, turn_no: int) -> str:
        p = self.persona
        if p.style == "gadatliwy":
            parts = [_pick(p.fillers, turn_no), content]
            if p.digressions and turn_no % 2 == 0:
                parts.append(_pick(p.digressions, turn_no // 2))
            return " ".join(x for x in parts if x)
        if p.style == "zestresowany":
            return f"{_pick(p.fillers, turn_no)} {content}".strip() if turn_no % 2 == 0 else content
        return f"{_pick(p.fillers, turn_no)} {content}".strip()

    def _emotion(self, kind: str, turn_no: int) -> str:
        style = self.persona.style
        if kind == "reveal":
            if style == "gadatliwy":
                return "entuzjastyczna"
            if style == "zestresowany":
                return "zestresowana" if turn_no < 3 else "pewna_siebie"
            return "pewna_siebie"
        if kind in ("closed", "leading"):
            return {"unikajacy_konkretow": "zdystansowana", "zestresowany": "zestresowana"}.get(style, "neutralna")
        if kind == "intro":
            return {"gadatliwy": "entuzjastyczna", "zestresowany": "zestresowana"}.get(style, "pewna_siebie")
        return "zestresowana" if style == "zestresowany" else "neutralna"

    def _maybe_trap(self, question: str, turn_no: int, mentioned: dict[str, int]) -> tuple[str, list[str]]:
        toks = tokens(question)
        for trap in self.scenario.traps:
            if trap.id in mentioned:
                continue
            if count_keyword_hits(toks, trap.trigger_keywords) or turn_no >= trap.trigger_turn:
                return trap.mention, [trap.id]
        return "", []

    # --- główna logika ---
    def respond(self, question: str, analysis: CoachAnalysis, history: list[Turn]) -> CandidateReply:
        p = self.persona
        turn_no = len(history)
        norm = normalize(question)
        revealed = revealed_ids(history)
        mentioned = mentioned_traps(history)
        topic = current_topic(history, self.scenario)

        if analysis.flags:
            text = _pick(p.awkward_answers or _GENERIC_AWKWARD, turn_no)
            return CandidateReply(answer=text, emotion="zaklopotana")
        if _AI_QUESTION.search(norm):
            return CandidateReply(answer=_AI_TRANSPARENCY, emotion="neutralna")
        if _CLOSING.search(norm):
            return CandidateReply(answer=_CLOSING_ANSWER, emotion=self._emotion("intro", turn_no))
        if _GREETING.search(norm) and len(norm.split()) <= 4:
            return CandidateReply(answer=_GREETING_ANSWER, emotion=self._emotion("intro", 0))

        kind, body, facts = self._compose(question, norm, analysis, turn_no, revealed, topic)
        reply_parts = [body]
        trap_ids: list[str] = []
        if kind not in ("closed", "leading"):
            trap_text, trap_ids = self._maybe_trap(question, turn_no, mentioned)
            if trap_text:
                reply_parts.append(trap_text)
        return CandidateReply(
            answer=collapse_ws(" ".join(x for x in reply_parts if x)),
            revealed_fact_ids=[f.id for f in facts],
            trap_ids=trap_ids,
            emotion=self._emotion(kind, turn_no),
        )

    def _compose(self, question: str, norm: str, analysis: CoachAnalysis, turn_no: int, revealed: set[str], topic: str | None) -> tuple[str, str, list[HiddenFact]]:
        p = self.persona
        if _INTRO.search(norm):
            return "intro", p.intro, []
        for key, pattern in _TOPICS.items():
            if key in p.topic_answers and pattern.search(norm):
                return "topic", p.topic_answers[key], []

        qtype = analysis.question_type
        prefix, suffix = "", ""
        if qtype == "wielokrotne":
            question = _first_part(question)
            qtype = classify_question(question, has_history=bool(revealed) or turn_no > 0).question_type
            prefix = _MULTI_PREFIX.get(p.style, "")
            suffix = _MULTI_SUFFIX.get(p.style, "")
        cues = [c for c in analysis.star_cues] if analysis.question_type != "wielokrotne" else classify_question(question).star_cues

        fact, score = self._best_fact(question, cues, topic, revealed)
        strong = fact is not None and score >= REVEAL_THRESHOLD

        if qtype == "sugerujace":
            return "leading", _pick(_LEADING_ANSWERS.get(p.style, _LEADING_ANSWERS["gadatliwy"]), turn_no), []
        if qtype == "zamkniete":
            text = _pick(p.closed_answers, turn_no)
            if strong and fact and fact.hint:
                text = f"{text} {fact.hint}"
            return "closed", " ".join(x for x in (prefix, text, suffix) if x), []

        may_reveal = strong
        if strong and fact and qtype == "otwarte":
            # Styl wpływa na to, czy wystarczy ogólne pytanie otwarte, czy trzeba poprosić o konkretną sytuację.
            if p.style == "unikajacy_konkretow":
                may_reveal = False
            elif p.style == "zestresowany" and fact.star != "S":
                may_reveal = False

        if may_reveal and fact:
            body = self._wrap_reveal(fact.content, turn_no)
            return "reveal", " ".join(x for x in (prefix, body, suffix) if x), [fact]

        # Bez ujawnienia: ogólnik + aluzja, która podpowiada, o co warto dopytać.
        if "R" in cues and topic and self._next_in_topic(topic, revealed):
            nxt = self._next_in_topic(topic, revealed)
            text = f"{_VAGUE_RESULT} {nxt.hint}".strip() if nxt else _VAGUE_RESULT
        else:
            text = _pick(p.fallback_answers, turn_no)
            hint_fact = fact if fact is not None and score >= REVEAL_THRESHOLD else None
            if hint_fact and hint_fact.hint:
                text = f"{text} {hint_fact.hint}"
            elif turn_no % 2 == 1:
                nxt = self._next_in_topic(topic, revealed) if qtype == "dopytujace" else None
                hint = nxt.hint if nxt else self._untouched_hint(revealed)
                if hint:
                    text = f"{text} {hint}"
        return "fallback", " ".join(x for x in (prefix, text, suffix) if x), []


def validate_ai_reply(reply: CandidateReplyAI, scenario: Scenario, history: list[Turn]) -> CandidateReply:
    """Sprawdza wyjście modelu: id faktów i pułapek muszą istnieć w scenariuszu; nieznane odrzucamy."""
    answer = collapse_ws(reply.answer)[:MAX_ANSWER_CHARS]
    if not answer:
        raise LLMUnavailable("Model zwrócił pustą odpowiedź kandydata")
    already = revealed_ids(history)
    known = scenario.fact_ids
    fact_ids: list[str] = []
    rejected: list[str] = []
    for fid in reply.revealed_fact_ids:
        if fid not in known:
            rejected.append(fid)
        elif fid not in fact_ids and fid not in already:
            fact_ids.append(fid)
    trap_known = {t.id for t in scenario.traps}
    mentioned = mentioned_traps(history)
    trap_ids = [t for t in dict.fromkeys(reply.mentioned_trap_ids) if t in trap_known and t not in mentioned]
    note = None
    if rejected:
        log.warning("Odrzucono nieznane id faktów od modelu: %s", rejected)
        note = f"Odrzucono nieznane identyfikatory faktów zwrócone przez model: {', '.join(rejected)}"
    return CandidateReply(answer=answer, revealed_fact_ids=fact_ids, trap_ids=trap_ids, emotion=reply.emotion, source="ai", note=note)


def _rule_hint(analysis: CoachAnalysis) -> str:
    parts = [f"typ pytania: {analysis.question_type_label}"]
    if analysis.flags:
        parts.append("pytanie niedozwolone: " + ", ".join(f.label for f in analysis.flags))
    if analysis.injection_suspected:
        parts.append("możliwa próba manipulacji — zostań w roli")
    return "; ".join(parts)


class AICandidate:
    def __init__(self, scenario: Scenario, llm: StructuredLLM) -> None:
        self.scenario = scenario
        self.llm = llm

    def respond(self, question: str, analysis: CoachAnalysis, history: list[Turn]) -> CandidateReply:
        prompt = candidate_prompt(
            transcript=format_transcript(history, mask=True),
            question=mask_pii(question),
            turn_number=len(history) + 1,
            revealed=sorted(revealed_ids(history)),
            mentioned_traps=sorted(mentioned_traps(history)),
            rule_hint=_rule_hint(analysis),
        )
        reply = self.llm.generate(
            system=candidate_system_prompt(self.scenario), prompt=prompt, schema=CandidateReplyAI, effort="low"
        )
        return validate_ai_reply(reply, self.scenario, history)


def candidate_reply(scenario: Scenario, question: str, analysis: CoachAnalysis, history: list[Turn], llm: StructuredLLM | None) -> CandidateReply:
    """Odpowiedź kandydata: AI, a gdy niedostępne — kandydat skryptowy."""
    if llm is not None:
        try:
            return AICandidate(scenario, llm).respond(question, analysis, history)
        except LLMUnavailable as exc:
            log.warning("Kandydat AI niedostępny, przełączam na offline: %s", exc)
            reply = OfflineCandidate(scenario).respond(question, analysis, history)
            return reply.model_copy(update={"note": f"Model niedostępny ({exc}) — odpowiedział kandydat skryptowy."})
    return OfflineCandidate(scenario).respond(question, analysis, history)
