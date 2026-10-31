"""Informacja dla kandydata o użyciu AI i czynnikach oceny (art. 26 ust. 11 i art. 86 AI Act, art. 13–15 i 22 RODO).

Wersja offline to szablon. Wersja AI (opcjonalna) przeredagowuje tylko część opisową;
sekcja o decyzji człowieka i prawach kandydata jest zawsze dołączana z szablonu przez kod,
więc model nie może jej pominąć ani zniekształcić. To szkic do przejrzenia przez rekrutera przed wysłaniem.
"""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel

from .llm import LLMUnavailable, StructuredLLM
from .models import KIND_LABELS, STATUS_LABELS, VERDICT_LABELS, Evaluation, Job
from .prompts import EXPLANATION_SYSTEM

DEFAULT_CONTACT = "rekrutacja@firma.example"


class Explanation(BaseModel):
    source: Literal["ai", "offline"]
    subject: str
    body: str
    fallback_reason: str | None = None


class AIExplanation(BaseModel):
    summary: str


def _factor_lines(job: Job, evaluation: Evaluation) -> list[str]:
    by_id = {r.requirement_id: r for r in evaluation.results}
    lines = []
    for req in job.requirements:
        r = by_id.get(req.id)
        verdict = VERDICT_LABELS[r.verdict] if r else VERDICT_LABELS["no_data"]
        lines.append(f"- {req.name} ({KIND_LABELS[req.kind]}, waga {req.weight}/5): {verdict}")
    return lines


def rights_section(contact: str = DEFAULT_CONTACT) -> str:
    return (
        "Decyzję o dalszym udziale w rekrutacji podejmuje człowiek (rekruter). System nie odrzuca kandydatów "
        "automatycznie, a ocena AI jest tylko jedną z informacji, które rekruter bierze pod uwagę.\n\n"
        "Przysługuje Ci prawo do:\n"
        "- uzyskania wyjaśnienia roli systemu AI w procesie i głównych czynników oceny,\n"
        "- przedstawienia własnego stanowiska i uzupełnienia informacji,\n"
        "- zakwestionowania wyniku i ponownej oceny zgłoszenia przez człowieka,\n"
        "- dostępu do swoich danych, ich sprostowania lub usunięcia na zasadach RODO.\n\n"
        f"Kontakt w tej sprawie: {contact}."
    )


def offline_summary(job: Job, evaluation: Evaluation) -> str:
    parts = [
        "Dziękujemy za przesłanie zgłoszenia na stanowisko „" + job.title + "”.",
        "",
        "Informujemy, że na etapie wstępnego przeglądu CV korzystamy z systemu wspomagającego opartego "
        "na sztucznej inteligencji. Przed oceną dane identyfikujące (m.in. imię i nazwisko, dane kontaktowe, "
        "adres, data urodzenia, wiek, zdjęcie, stan cywilny) są maskowane, a system porównuje treść CV "
        "wyłącznie z wymaganiami opublikowanymi w ogłoszeniu.",
        "",
        "Czynniki brane pod uwagę i wynik porównania dla Twojego CV:",
        *_factor_lines(job, evaluation),
    ]
    if any(r.verdict == "no_data" for r in evaluation.results):
        parts += [
            "",
            "„Brak danych” oznacza, że w CV nie znaleźliśmy informacji o danym wymaganiu — "
            "możesz ją uzupełnić, odpowiadając na tę wiadomość.",
        ]
    if evaluation.injection.flagged:
        parts += [
            "",
            "Część treści dokumentu została pominięta w ocenie automatycznej, ponieważ zawierała polecenia "
            "skierowane do systemu oceniającego. Rekruter widział tę informację.",
        ]
    return "\n".join(parts)


def build_explanation(
    job: Job,
    evaluation: Evaluation,
    status: str,
    *,
    llm: StructuredLLM | None = None,
    use_ai: bool = False,
    contact: str = DEFAULT_CONTACT,
) -> Explanation:
    subject = f"Informacja o wykorzystaniu AI w rekrutacji — {job.title}"
    status_line = f"Aktualny status zgłoszenia: {STATUS_LABELS.get(status, status)}."
    template = offline_summary(job, evaluation)
    fallback: str | None = None
    if use_ai and llm is not None:
        facts = template.replace("<", "‹").replace(">", "›")
        try:
            ai = llm.generate(
                system=EXPLANATION_SYSTEM,
                prompt=f"<fakty>\n{facts}\n</fakty>",
                schema=AIExplanation,
                effort="low",
            )
            summary = ai.summary.strip()[:2500]
            if summary:
                body = "\n\n".join([summary, status_line, rights_section(contact)])
                return Explanation(source="ai", subject=subject, body=body)
            fallback = "Model zwrócił pustą treść — użyto szablonu."
        except LLMUnavailable as exc:
            fallback = f"Model AI niedostępny ({exc}) — użyto szablonu."
    elif use_ai:
        fallback = "AI wyłączone — użyto szablonu."
    body = "\n\n".join([template, status_line, rights_section(contact)])
    return Explanation(source="offline", subject=subject, body=body, fallback_reason=fallback)
