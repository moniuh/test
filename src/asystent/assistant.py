"""Usługa asystenta: pytania pracowników, bezpieczniki, eskalacje, luki w wiedzy, statystyki, retencja.

Moduł nie zależy od FastAPI — API jest cienką warstwą nad klasą `Assistant`.
"""
from __future__ import annotations

import hashlib
import logging
import re
import secrets
import threading
from collections import Counter, defaultdict
from collections.abc import Callable
from datetime import date, datetime, timedelta

from . import messages
from .answering import Draft, ai_answer, best_span, citation_from, offline_answer
from .config import Settings, local_date, utcnow
from .knowledge import KnowledgeBase
from .llm import LLMUnavailable, StructuredLLM, get_llm
from .models import Answer, Escalation, GapGroup, GapsReport, Stats
from .safety import (
    CATEGORY_LABELS,
    HARASSMENT,
    HEALTH,
    INDIVIDUAL,
    LEGAL,
    check_question,
    mask_health,
    mask_pii,
)
from .search import SearchEngine, is_relevant
from .store import Store
from .text import collapse_ws, surface_words

log = logging.getLogger(__name__)

MAX_QUESTION_CHARS = 1000
_TOKEN_RE = re.compile(r"^[A-Za-z0-9_-]{16,64}$")
_HARASSMENT_QUERY = "jak i gdzie zgłosić naruszenie: e-mail, formularz, komisja, spotkanie, platforma zgłoszeń"
# Słowa zbyt ogólne, by grupować po nich luki w wiedzy (rdzenie po normalizacji).
_GAP_IGNORE = frozenset({"prac", "pracownik", "firm", "pracodawc", "moz", "mog", "dzial", "hr", "pytan", "chcial",
                         "wiedz", "musz", "mus", "obowiazuj", "jest"})


class DomainError(Exception):
    """Błąd biznesowy z komunikatem po polsku (API mapuje na kod HTTP)."""

    status_code = 400


class NotFoundError(DomainError):
    status_code = 404


class ForbiddenError(DomainError):
    status_code = 403


def new_session_token() -> str:
    """Losowy, pseudonimowy identyfikator sesji (nie wiąże się z tożsamością pracownika)."""
    return secrets.token_urlsafe(18)


def session_ref(token: str) -> str:
    """W bazie trzymamy tylko skrót tokenu — kto ma dostęp do bazy, nie podszyje się pod sesję."""
    return hashlib.sha256(f"asystent-sesja:{token}".encode()).hexdigest()[:16]


def valid_token(token: str | None) -> bool:
    return bool(token and _TOKEN_RE.match(token))


class _CachedLLM:
    """Domyślna fabryka: jeden klient Claude na proces (albo None w trybie offline)."""

    def __init__(self) -> None:
        self._llm: StructuredLLM | None = None
        self._lock = threading.Lock()

    def __call__(self) -> StructuredLLM | None:
        with self._lock:
            if self._llm is None:
                self._llm = get_llm()
            return self._llm


class Assistant:
    def __init__(
        self,
        store: Store,
        settings: Settings | None = None,
        llm_factory: Callable[[], StructuredLLM | None] | None = None,
        now: Callable[[], datetime] = utcnow,
    ) -> None:
        self.store = store
        self.settings = settings or Settings()
        self._llm_factory = llm_factory or _CachedLLM()
        self._now = now
        self.kb = KnowledgeBase(store, now=self._now, today=self.today)
        self._engine: SearchEngine | None = None
        self._engine_key: tuple[int, date] | None = None
        self._lock = threading.Lock()

    # --- czas i indeks ---
    def today(self) -> date:
        return local_date(self._now())

    def _stamp(self) -> str:
        return self._now().isoformat(timespec="seconds")

    def engine(self) -> SearchEngine:
        """Indeks BM25 aktualnych wersji; przebudowa po zmianie dokumentów lub daty."""
        key = (self.kb.revision, self.today())
        with self._lock:
            if self._engine is None or self._engine_key != key:
                self._engine = SearchEngine(self.kb.current_passages())
                self._engine_key = key
            return self._engine

    # --- pytania ---
    def ask(self, question: str, session_token: str | None = None) -> Answer:
        question = (question or "").strip()
        if not question:
            raise DomainError("Pytanie nie może być puste")
        if len(question) > MAX_QUESTION_CHARS:
            raise DomainError(f"Pytanie jest za długie — maksymalnie {MAX_QUESTION_CHARS} znaków")
        token = session_token if valid_token(session_token) else new_session_token()
        self.maybe_purge()

        check = check_question(question)
        safe_question = mask_pii(question)  # tylko ta wersja trafia do modelu i do logu
        notices: list[str] = []
        if check.has(HEALTH):
            safe_question = mask_health(safe_question)
            notices.append(messages.HEALTH_MASKED)

        engine = self.engine()
        hits = engine.search(question, k=self.settings.top_k)  # wyszukiwanie lokalne, bez wysyłki danych
        if check.has(HARASSMENT):
            draft = self._harassment(engine, source="offline")
        elif check.has(INDIVIDUAL):
            draft = self._individual(question, hits, engine, others=check.others_data)
        else:
            draft = self._answer(question, safe_question, hits, engine, check.categories)

        categories = list(check.categories)
        if draft.model_category and draft.model_category not in categories:
            categories.append(draft.model_category)
            if draft.model_category == HARASSMENT:
                # Model rozpoznał temat, którego reguły nie wyłapały — ta sama ścieżka co dla reguł.
                draft = self._harassment(engine, source="ai")
            elif draft.model_category == INDIVIDUAL and draft.status == "answered":
                draft.needs_hr = True
                draft.notices.append(messages.INDIVIDUAL_PARTIAL)
            elif draft.model_category == HEALTH:
                notices.append(messages.HEALTH_SOFT)
        if LEGAL in categories and draft.status == "no_basis":
            draft.answer = messages.LEGAL_NO_BASIS
        elif LEGAL in categories and draft.status == "answered":
            notices.append(messages.LEGAL_DISCLAIMER)
        injection = check.injection or draft.model_injection
        if injection:
            notices.append(messages.INJECTION)

        interaction_id = self.store.insert_interaction(
            {
                "session_ref": session_ref(token),
                "created_at": self._stamp(),
                "question": safe_question,
                "status": draft.status,
                "source": draft.source,
                "categories": categories,
                "confidence": draft.confidence,
                "needs_hr": draft.needs_hr,
                "best_score": round(hits[0].score, 3) if hits else None,
                "coverage": round(hits[0].coverage, 3) if hits else None,
                "cited": [
                    {"chunk_id": c.chunk_id, "document_id": c.document_id, "title": c.title, "section": c.section}
                    for c in draft.citations
                ],
                "answer_excerpt": draft.answer[:300],
            }
        )
        return Answer(
            interaction_id=interaction_id,
            session_token=token,
            status=draft.status,
            source=draft.source,
            answer=draft.answer,
            citations=draft.citations,
            confidence=draft.confidence,
            needs_hr=draft.needs_hr,
            reason=draft.reason,
            is_extract=draft.is_extract,
            categories=categories,
            category_labels=[CATEGORY_LABELS[c] for c in categories if c in CATEGORY_LABELS],
            injection_suspected=injection,
            rejected_citations=draft.rejected_citations,
            notices=draft.notices + notices,
            hr_contact=self.settings.hr_contact,
        )

    def _answer(self, question: str, safe_question: str, hits, engine: SearchEngine, categories) -> Draft:
        fallback_note = None
        llm = self._llm_factory()
        if llm is not None and hits:
            try:
                return ai_answer(llm, safe_question, hits, self.today(), categories)
            except LLMUnavailable as exc:
                log.warning("Model niedostępny, przechodzę na tryb offline: %s", exc)
                fallback_note = f"Model AI jest chwilowo niedostępny ({exc}) — pokazuję wyciąg z dokumentów."
        draft = offline_answer(
            question, hits, engine, self.settings.min_score, self.settings.min_coverage, legal=LEGAL in categories
        )
        if fallback_note:
            draft.notices.insert(0, fallback_note)
        return draft

    def _harassment(self, engine: SearchEngine, source: str) -> Draft:
        """Mobbing, molestowanie, dyskryminacja, odwet: kanały z oficjalnej procedury, bez oceniania sprawy."""
        # Po jednym, najlepszym fragmencie z każdej procedury (kolejność wg konfiguracji).
        hits = [
            h for key in self.settings.safety_doc_keys
            for h in engine.search(_HARASSMENT_QUERY, k=1, doc_keys=[key])
        ]
        citations = [
            citation_from(h.passage, collapse_ws(best_span(h.passage.text, _HARASSMENT_QUERY, engine)))
            for h in hits
        ]
        return Draft(
            status="sensitive",
            source=source,  # type: ignore[arg-type]
            answer=messages.HARASSMENT if citations else messages.HARASSMENT_NO_PROCEDURE,
            citations=citations,
            confidence="wysoka" if citations else "niska",
            needs_hr=True,
            reason="Temat wrażliwy (mobbing, molestowanie, dyskryminacja lub odwet) — kieruję do oficjalnej procedury.",
            notices=[],
        )

    def _individual(self, question: str, hits, engine: SearchEngine, others: bool) -> Draft:
        if others:
            return Draft(
                status="sensitive", source="offline", answer=messages.INDIVIDUAL_OTHERS, citations=[],
                confidence="wysoka", needs_hr=True, reason="Pytanie dotyczy danych innej osoby.",
            )
        related = [h for h in hits if is_relevant(h, self.settings.min_score, self.settings.min_coverage)][:1]
        citations = [
            citation_from(h.passage, collapse_ws(best_span(h.passage.text, question, engine))) for h in related
        ]
        return Draft(
            status="sensitive",
            source="offline",
            answer=messages.INDIVIDUAL_OWN,
            citations=citations,
            confidence="wysoka",
            needs_hr=True,
            reason="Sprawa indywidualna — wymaga decyzji lub danych kadrowych, których asystent nie ma.",
            notices=[messages.INDIVIDUAL_RELATED] if citations else [],
        )

    # --- ocena odpowiedzi ---
    def _own_interaction(self, interaction_id: int, token: str | None) -> dict:
        row = self.store.get_interaction(interaction_id)
        if row is None:
            raise NotFoundError("Nie znaleziono odpowiedzi o podanym identyfikatorze")
        if not valid_token(token) or session_ref(token) != row["session_ref"]:  # type: ignore[arg-type]
            raise ForbiddenError("Ta odpowiedź należy do innej sesji")
        return row

    def feedback(self, interaction_id: int, token: str | None, vote: str, comment: str | None = None) -> None:
        if vote not in ("up", "down"):
            raise DomainError("Ocena musi mieć wartość „up” albo „down”")
        self._own_interaction(interaction_id, token)
        clean = mask_health(mask_pii(comment.strip()))[:500] if comment and comment.strip() else None
        self.store.set_feedback(interaction_id, vote, clean, self._stamp())

    # --- eskalacje ---
    def escalate(
        self,
        token: str | None,
        interaction_id: int | None = None,
        question: str | None = None,
        note: str | None = None,
        contact: str | None = None,
    ) -> tuple[str, Escalation]:
        token = token if valid_token(token) else new_session_token()
        context: dict = {}
        if interaction_id is not None:
            row = self._own_interaction(interaction_id, token)
            text = row["question"]
            context = {
                "status": row["status"],
                "source": row["source"],
                "categories": row["categories"] or [],
                "confidence": row["confidence"],
                "cited": [f"{c['title']} › {c['section']}" for c in row["cited"] or []],
                "answer_excerpt": row["answer_excerpt"],
            }
        elif question and question.strip():
            text = mask_pii(question.strip()[:MAX_QUESTION_CHARS])
            if check_question(text).has(HEALTH):
                text = mask_health(text)
        else:
            raise DomainError("Podaj treść pytania albo identyfikator odpowiedzi asystenta")
        clean_note = mask_health(mask_pii(note.strip()))[:1000] if note and note.strip() else None
        escalation_id = self.store.insert_escalation(
            {
                "interaction_id": interaction_id,
                "session_ref": session_ref(token),
                "created_at": self._stamp(),
                "question": text,
                "note": clean_note,
                "contact": contact.strip()[:200] if contact and contact.strip() else None,
                "context": context,
            }
        )
        return token, self._escalation(escalation_id)

    def _escalation(self, escalation_id: int) -> Escalation:
        row = self.store.get_escalation(escalation_id)
        if row is None:
            raise NotFoundError("Nie znaleziono zgłoszenia")
        return Escalation(**{k: row[k] for k in Escalation.model_fields})

    def my_escalations(self, token: str | None) -> list[Escalation]:
        if not valid_token(token):
            return []
        return [self._escalation(r["id"]) for r in self.store.list_escalations(session_ref=session_ref(token))]  # type: ignore[arg-type]

    def escalations(self, status: str | None = None) -> list[Escalation]:
        if status and status not in ("nowe", "odpowiedziane", "zamknięte"):
            raise DomainError("Nieznany status zgłoszenia")
        return [self._escalation(r["id"]) for r in self.store.list_escalations(status=status)]

    def reply(self, escalation_id: int, reply: str, close: bool = False) -> Escalation:
        current = self._escalation(escalation_id)
        if current.status == "zamknięte":
            raise DomainError("Zgłoszenie jest już zamknięte")
        reply = reply.strip()
        if not reply:
            raise DomainError("Odpowiedź nie może być pusta")
        stamp = self._stamp()
        fields = {"hr_reply": reply[:4000], "replied_at": stamp, "status": "zamknięte" if close else "odpowiedziane"}
        if close:
            fields["closed_at"] = stamp
        self.store.update_escalation(escalation_id, **fields)
        return self._escalation(escalation_id)

    def close(self, escalation_id: int) -> Escalation:
        current = self._escalation(escalation_id)
        if current.status != "zamknięte":
            self.store.update_escalation(escalation_id, status="zamknięte", closed_at=self._stamp())
        return self._escalation(escalation_id)

    # --- luki w wiedzy ---
    def knowledge_gaps(self, days: int = 90, limit: int = 10) -> GapsReport:
        """Pytania bez dobrej odpowiedzi pogrupowane po słowach kluczowych + podpowiedź dokumentu."""
        since = (self._now() - timedelta(days=days)).isoformat(timespec="seconds")
        rows = self.store.interactions_since(since)
        escalated = self.store.escalated_interaction_ids()
        gaps = [
            r for r in rows
            if r["status"] != "sensitive"
            and (r["status"] == "no_basis" or r["confidence"] == "niska" or r["feedback"] == "down"
                 or r["id"] in escalated)
        ]
        keywords: list[set[str]] = []
        surfaces: dict[str, Counter[str]] = defaultdict(Counter)
        for row in gaps:
            found: set[str] = set()
            for stem_, word in surface_words(re.sub(r"\[[^\]]*\]", " ", row["question"])):
                if len(stem_) < 3 or stem_ in _GAP_IGNORE or not stem_.isalpha():
                    continue
                found.add(stem_)
                surfaces[stem_][word] += 1
            keywords.append(found)

        remaining = list(range(len(gaps)))
        groups: list[GapGroup] = []
        engine = self.engine()
        while remaining and len(groups) < limit:
            counts = Counter(k for i in remaining for k in keywords[i])
            if not counts:
                break
            keyword = min(counts, key=lambda k: (-counts[k], k))
            members = [i for i in remaining if keyword in keywords[i]]
            remaining = [i for i in remaining if keyword not in keywords[i]]
            groups.append(self._gap_group(keyword, [gaps[i] for i in members], [keywords[i] for i in members],
                                          surfaces, escalated, engine))
        if remaining and len(groups) < limit:
            groups.append(self._gap_group(None, [gaps[i] for i in remaining], [], surfaces, escalated, engine))
        return GapsReport(days=days, total_questions=len(rows), gap_questions=len(gaps), groups=groups)

    def _gap_group(self, keyword, rows, keyword_sets, surfaces, escalated, engine: SearchEngine) -> GapGroup:
        display = surfaces[keyword].most_common(1)[0][0] if keyword else "inne"
        related = Counter(k for ks in keyword_sets for k in ks if k != keyword)
        query_words = [display] + [surfaces[k].most_common(1)[0][0] for k, _ in related.most_common(2)]
        hit = next(iter(engine.search(" ".join(query_words), k=1)), None) if keyword else None
        if hit and hit.coverage >= 0.5:
            suggested = hit.passage.title
            suggestion = (f"Uzupełnij dokument „{hit.passage.title}” ({hit.passage.section}) — temat jest w nim "
                          f"poruszony, ale obecna treść nie odpowiada na te pytania.")
        else:
            suggested = None
            suggestion = ("Brak dokumentu na ten temat — dodaj nową sekcję, np. do „FAQ onboardingowe”, "
                          "albo nowy dokument.")
        return GapGroup(
            keyword=display,
            count=len(rows),
            no_basis=sum(r["status"] == "no_basis" for r in rows),
            low_confidence=sum(r["confidence"] == "niska" and r["status"] != "no_basis" for r in rows),
            thumbs_down=sum(r["feedback"] == "down" for r in rows),
            escalated=sum(r["id"] in escalated for r in rows),
            examples=list(dict.fromkeys(r["question"] for r in reversed(rows)))[:3],
            suggestion=suggestion,
            suggested_document=suggested,
        )

    # --- statystyki ---
    def stats(self, days_back: int = 14) -> Stats:
        rows = self.store.all_interactions()
        total = len(rows)
        by_status = Counter(r["status"] for r in rows)
        top_docs = Counter(c["title"] for r in rows for c in (r["cited"] or []))
        today = self._now().date()
        per_day_counter = Counter(r["created_at"][:10] for r in rows)
        per_day = [
            {"date": d.isoformat(), "count": per_day_counter.get(d.isoformat(), 0)}
            for d in (today - timedelta(days=i) for i in range(days_back - 1, -1, -1))
        ]
        docs = self.kb.documents()
        return Stats(
            total_questions=total,
            by_status=dict(by_status),
            by_source=dict(Counter(r["source"] for r in rows)),
            by_confidence=dict(Counter(r["confidence"] for r in rows if r["status"] == "answered")),
            feedback={vote: sum(r["feedback"] == vote for r in rows) for vote in ("up", "down")},
            escalations=self.store.count_by("escalations", "status"),
            top_documents=[{"title": t, "count": n} for t, n in top_docs.most_common(5)],
            per_day=per_day,
            answered_rate=round(by_status.get("answered", 0) / total, 3) if total else 0.0,
            documents_current=sum(d.status == "aktualna" for d in docs),
            documents_total=len(docs),
        )

    # --- retencja ---
    def purge(self, days: int | None = None) -> dict:
        """Usuwa logi starsze niż okres retencji (domyślnie HR_RETENTION_DAYS)."""
        days = days if days is not None else self.settings.retention_days
        if days < 1:
            raise DomainError("Okres retencji musi wynosić co najmniej 1 dzień")
        now = self._now()
        cutoff = (now - timedelta(days=days)).isoformat(timespec="seconds")
        counts = self.store.purge_before(cutoff)
        self.store.set_meta("last_purge", now.isoformat(timespec="seconds"))
        log.info("Retencja: usunięto %s (starsze niż %s)", counts, cutoff)
        return {"days": days, "cutoff": cutoff, **counts}

    def maybe_purge(self) -> None:
        """Czyszczenie raz na dobę, przy pierwszym pytaniu danego dnia."""
        last = self.store.get_meta("last_purge")
        if last is None or last[:10] != self._now().date().isoformat():
            self.purge()

    def retention_info(self) -> dict:
        return {"days": self.settings.retention_days, "last_purge": self.store.get_meta("last_purge")}
