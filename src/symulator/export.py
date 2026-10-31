"""Eksport sesji (transkrypt + raport) do Markdown."""
from __future__ import annotations

from .models import EMOTION_LABELS, QUESTION_TYPE_LABELS, STAR_LABELS, STAR_ORDER
from .report import Report
from .scenarios import Scenario
from .storage import SessionRecord
from .transcript import Turn

_SOURCE = {"ai": "AI", "offline": "offline (reguły / skrypt)"}
_TRAP_OUTCOME = {"uniknieta": "uniknięta ✅", "wejscie": "wejście w pułapkę ⚠️", "nie_wystapila": "nie wystąpiła"}


def _pct(x: float) -> str:
    return f"{round(x * 100)}%"


def _md(text: str) -> str:
    """Neutralizuje znaki tabel Markdown w treści użytkownika."""
    return text.replace("|", "\\|").replace("\n", " ")


def session_to_markdown(session: SessionRecord, scenario: Scenario, turns: list[Turn], report: Report | None) -> str:
    out: list[str] = []
    out.append(f"# Sesja treningowa: {scenario.title}")
    out.append("")
    out.append(f"- **Stanowisko:** {scenario.position} — {scenario.company}")
    out.append(f"- **Kandydat (symulowany):** {scenario.persona.name}")
    out.append(f"- **Rozpoczęta:** {session.created_at}" + (f" · **zakończona:** {session.finished_at}" if session.finished_at else ""))
    out.append(f"- **Liczba pytań:** {len(turns)}")
    out.append("")
    out.append("> Kandydat w tej rozmowie był symulowany przez sztuczną inteligencję lub skrypt. "
               "Materiał treningowy — nie jest oceną pracownika.")
    out.append("")

    if report:
        out.append("## Raport")
        out.append("")
        out.append("| Metryka | Wartość |")
        out.append("|---|---|")
        out.append(f"| Pokrycie kompetencji | {_pct(report.coverage_share)} |")
        out.append(f"| Średnia kompletność STAR | {_pct(report.star_average)} |")
        out.append(f"| Pytania otwarte | {_pct(report.questions.open_share)} ({report.questions.open_count}/{report.questions.total}) |")
        out.append(f"| Pytania sugerujące | {report.questions.leading} |")
        out.append(f"| Pytania wielokrotne | {report.questions.multiple} |")
        ratio = f"{report.talk.ratio:.2f}" if report.talk.ratio is not None else "—"
        out.append(f"| Proporcja mówienia (słowa rekrutera : kandydata) | {report.talk.trainee_words} : {report.talk.candidate_words} (= {ratio}; udział rekrutera {_pct(report.talk.trainee_share)}) |")
        out.append(f"| Incydenty prawne | {len(report.incidents)} |")
        out.append("")

        out.append("### Kompletność STAR per kompetencja")
        out.append("")
        out.append("| Kompetencja | " + " | ".join(STAR_LABELS[e] for e in STAR_ORDER) + " | Kompletność |")
        out.append("|---|" + "---|" * (len(STAR_ORDER) + 1))
        for c in report.coverage:
            cells = " | ".join("✔" if e in c.elements_revealed else "—" for e in STAR_ORDER)
            out.append(f"| {c.name} | {cells} | {_pct(c.star_completeness)} |")
        out.append("")

        out.append("### Najważniejsze rekomendacje")
        out.append("")
        for i, r in enumerate(report.recommendations, 1):
            out.append(f"{i}. **{r.title}.** {r.detail}")
        out.append("")

        if report.incidents:
            out.append("### Incydenty prawne")
            out.append("")
            for inc in report.incidents:
                out.append(f"- **Tura {inc.turn} — {inc.label}:** „{_md(inc.question)}”")
                out.append(f"  - Dlaczego nie wolno: {inc.explanation}")
                out.append(f"  - Podstawa: {inc.legal_basis}")
                out.append(f"  - Zamiast tego: {inc.suggestion}")
            out.append("")

        if report.traps:
            out.append("### Pułapki (wątki prywatne poruszone przez kandydata)")
            out.append("")
            for t in report.traps:
                where = f" (tura {t.mentioned_turn})" if t.mentioned_turn else ""
                out.append(f"- {t.label}{where}: {_TRAP_OUTCOME[t.outcome]}")
            out.append("")

        if report.notes:
            out.append(f"### Uwagi jakościowe ({_SOURCE[report.notes_source]})")
            out.append("")
            for n in report.notes:
                kind = "Mocna strona" if n.kind == "mocna_strona" else "Do poprawy"
                out.append(f"- **{kind}:** {n.observation}")
                out.append(f"  > {_md(n.quote)}")
                out.append(f"  - Wskazówka: {n.suggestion}")
            out.append("")

    out.append("## Transkrypt")
    out.append("")
    for t in turns:
        qlabel = QUESTION_TYPE_LABELS.get(t.coach.question_type, t.coach.question_type)
        out.append(f"**[{t.index + 1}] Rekruter** _(pytanie {qlabel})_: {t.question}")
        for f in t.coach.flags:
            out.append(f"> ⚠️ Pytanie niedozwolone — {f.label}. {f.suggestion}")
        emotion = EMOTION_LABELS.get(t.emotion, t.emotion)
        out.append(f"**Kandydat** _({emotion}; źródło: {_SOURCE[t.candidate_source]})_: {t.answer}")
        out.append("")
    return "\n".join(out).rstrip() + "\n"
