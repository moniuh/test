"""Przepisanie ogłoszenia na wersję zgodną: przez AI albo regułowo (offline)."""
from __future__ import annotations

import logging
import re

from pydantic import BaseModel

from . import prompts
from .audit import AI_ERRORS, detect_injection
from .engine import TextContext, run_rules, rx
from .llm import StructuredLLM
from .models import Change, Finding, Fix, RewriteResult
from .rules import RULES_BY_ID, SALARY_AMOUNT
from .scoring import compute_score
from .textutil import contains_quote, mask_pii, strip_bullet

log = logging.getLogger(__name__)

_PLACEHOLDER = re.compile(r"\[UZUPEŁNIJ:[^\]]*\]")
_REMOVAL_MODES = {"remove", "remove_item", "remove_sentence"}


class AIChange(BaseModel):
    before: str
    after: str
    reason: str


class AIRewrite(BaseModel):
    rewritten_text: str
    changes: list[AIChange]
    injection_attempt: bool


class RewriteRejected(RuntimeError):
    """Wersja AI nie przeszła kontroli faktów."""


# --- kontrola faktów --------------------------------------------------------------------------------

def amount_values(text: str) -> set[int]:
    """Kwoty wynagrodzeń w tekście jako liczby całkowite (np. „12 tys. zł” → 12000)."""
    values: set[int] = set()
    for pattern in SALARY_AMOUNT:
        for m in rx(pattern).finditer(text):
            fragment = m.group(0)
            thousands = re.search(r"\d\s*(?:tys\.?|k)(?![a-ząćęłńóśźż])", fragment, re.IGNORECASE)
            for num in re.finditer(r"\d{1,3}(?:[  .]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?", fragment):
                raw = re.sub(r"[  .](?=\d{3}(?:\D|$))", "", num.group(0)).replace(",", ".")
                try:
                    value = float(raw)
                except ValueError:
                    continue
                if thousands and value < 1000:
                    value *= 1000
                values.add(int(round(value)))
    return values


# --- przepisanie offline ----------------------------------------------------------------------------

def score_without_placeholders(text: str) -> int:
    """Wynik reguł dla tekstu bez linii „[UZUPEŁNIJ: …]” — niewypełnione pole nie usuwa problemu."""
    kept = [line for line in text.splitlines() if not _PLACEHOLDER.search(line)]
    return compute_score(run_rules("\n".join(kept)))


def _conflicts(a: Fix, b: Fix) -> bool:
    if a.start == a.end:
        return b.start < a.start < b.end
    if b.start == b.end:
        return a.start < b.start < a.end
    return a.start < b.end and b.start < a.end


def cleanup(text: str) -> str:
    """Porządkuje tekst po usunięciach: podwójne spacje, osierocone przecinki i spójniki, puste punkty."""
    text = re.sub(r"(?<=\S)[ \t]{2,}(?=\S)", " ", text)
    text = re.sub(r"[ \t]+([,.;!?])", r"\1", text)
    text = re.sub(r",[ \t]*,", ",", text)
    text = re.sub(r",([ \t]*[.;!?])", r"\1", text)
    text = re.sub(r"[ \t]+(?:i|oraz|lub|a także)[ \t]*([.;!?])", r"\1", text)
    text = re.sub(r"^[ \t]*(?:[-*•–·▪◦]|\d+[.)])[ \t]*(?:\n|$)", "", text, flags=re.MULTILINE)
    text = re.sub(r"[ \t]+$", "", text, flags=re.MULTILINE)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip("\n")


def _describe_fix(text: str, finding: Finding, fix: Fix) -> Change:
    before = text[fix.start:fix.end]
    after = fix.text
    if fix.mode == "title_marker":
        ctx = TextContext(text)
        span = ctx.title_span or (fix.start, fix.end)
        title = text[span[0]:span[1]]
        return Change(kind="replaced", before=title, after=title + fix.text, reason=finding.title,
                      rule_id=finding.rule_id)
    # Poprawka wielkiej litery po usunięciu początku zdania: pokaż jako samo usunięcie.
    if len(after) == 1 and after.isupper() and before[-1:] == after.lower():
        before, after = before[:-1], ""
    kind = "removed" if not after.strip() else "replaced"
    if fix.mode in _REMOVAL_MODES and not after.strip():
        kind = "removed"
    return Change(kind=kind, before=strip_bullet(before.strip("\n")).strip(" \t\n,"), after=after.strip(),
                  reason=finding.title, rule_id=finding.rule_id)


def _insert_templates(text: str, templates: list[tuple[str, str]]) -> str:
    """Dopisuje brakujące sekcje: „top” pod tytułem, „end” na końcu ogłoszenia."""
    top = [t for t, pos in templates if pos == "top"]
    end = [t for t, pos in templates if pos == "end"]
    if top:
        block = "\n".join(top)
        span = TextContext(text).title_span
        if span:
            line_end = text.find("\n", span[1])
            line_end = len(text) if line_end == -1 else line_end
            head, rest = text[:line_end], text[line_end:].lstrip("\n")
            text = head + "\n\n" + block + ("\n\n" + rest if rest.strip() else "")
        else:
            text = block + "\n\n" + text
    if end:
        text = text.rstrip("\n") + "\n\n" + "\n".join(end)
    return text


def rewrite_offline(text: str, findings: list[Finding] | None = None, note: str | None = None) -> RewriteResult:
    """Stosuje poprawki z reguł, a brakujące sekcje dopisuje jako szablony „[UZUPEŁNIJ: …]”."""
    rule_findings = [f for f in (findings if findings is not None else run_rules(text)) if f.source == "rule"]
    with_fix = sorted(
        ((f, f.fix) for f in rule_findings if f.fix is not None),
        key=lambda item: (item[1].start, -(item[1].end - item[1].start)),
    )
    accepted: list[tuple[Finding, Fix]] = []
    changes: list[Change] = []
    for finding, fix in with_fix:
        if any(_conflicts(fix, other) for _, other in accepted):
            covered = any(o.start <= fix.start and fix.end <= o.end and not o.text.strip() for _, o in accepted)
            if not covered:
                changes.append(Change(kind="manual", before=finding.fragment or "", after="",
                                      reason=f"{finding.title}: {finding.suggestion}", rule_id=finding.rule_id))
            continue
        accepted.append((finding, fix))

    new_text = text
    for _, fix in sorted(accepted, key=lambda item: (item[1].start, item[1].end), reverse=True):
        new_text = new_text[: fix.start] + fix.text + new_text[fix.end:]
    for finding, fix in accepted:
        changes.append(_describe_fix(text, finding, fix))
    new_text = cleanup(new_text)

    templates: list[tuple[str, str]] = []
    for finding in rule_findings:
        rule = RULES_BY_ID.get(finding.rule_id)
        if finding.fix is None and finding.start is None and rule and rule.template:
            placeholder = _PLACEHOLDER.search(rule.template)
            if placeholder and placeholder.group(0) in new_text:
                continue
            templates.append((rule.template, rule.template_position))
            changes.append(Change(kind="added", before="", after=rule.template, reason=finding.title,
                                  rule_id=finding.rule_id))
        elif finding.fix is None and finding.start is not None:
            changes.append(Change(kind="manual", before=finding.fragment or "", after="",
                                  reason=f"{finding.title}: {finding.suggestion}", rule_id=finding.rule_id))
    new_text = _insert_templates(new_text, templates)

    warnings = []
    if _PLACEHOLDER.search(new_text):
        warnings.append(
            "Uzupełnij pola oznaczone [UZUPEŁNIJ: …] — wynik „po” liczymy tak, jakby były puste, więc wzrośnie po "
            "ich wypełnieniu."
        )
    if any(c.kind == "manual" for c in changes):
        warnings.append("Część problemów wymaga ręcznej poprawy (lista zmian: „do ręcznej poprawy”).")
    return RewriteResult(
        source="offline",
        text=new_text,
        changes=changes,
        warnings=warnings,
        score_before=compute_score(rule_findings),
        score_after=score_without_placeholders(new_text),
        ai_note=note,
    )


# --- przepisanie przez AI -----------------------------------------------------------------------------

def rewrite_ai(text: str, llm: StructuredLLM, findings: list[Finding] | None = None) -> RewriteResult:
    """Przepisanie przez model z kontrolą faktów (kwoty, dane kontaktowe, weryfikacja „przed”)."""
    rule_findings = findings if findings is not None else run_rules(text)
    masked = mask_pii(text)
    problems = [f"{f.title}: „{f.fragment}”" if f.fragment else f.title for f in rule_findings]
    response = llm.generate(
        system=prompts.REWRITE_SYSTEM,
        prompt=prompts.build_rewrite_prompt(masked.masked, problems),
        schema=AIRewrite,
        effort="medium",
    )
    new_text = masked.unmask(response.rewritten_text).strip()
    if len(new_text) < 20:
        raise RewriteRejected("model zwrócił pustą lub zbyt krótką wersję")
    invented = amount_values(new_text) - amount_values(text)
    if invented:
        listed = ", ".join(f"{v:,}".replace(",", " ") for v in sorted(invented))
        raise RewriteRejected(f"wersja AI zawierała kwoty, których nie ma w oryginale ({listed})")

    warnings: list[str] = []
    lost = amount_values(text) - amount_values(new_text)
    if lost:
        warnings.append("Wersja AI pominęła kwoty z oryginału: " + ", ".join(str(v) for v in sorted(lost)) + ".")
    for token, value in masked.placeholders.items():
        if value not in new_text:
            kind = token.strip("[]").split("_")[0].lower()
            warnings.append(f"Wersja AI pominęła dane z oryginału ({kind}) — sprawdź dane kontaktowe.")
    if re.search(r"\[(?:EMAIL|TELEFON|PESEL|KONTO|ZNACZNIK)_\d+\]", new_text):
        warnings.append("Wersja AI zawiera nieznane znaczniki danych — usuń je przed publikacją.")
    if response.injection_attempt or detect_injection(text):
        warnings.append("W ogłoszeniu wykryto tekst przypominający polecenia dla AI — został zignorowany.")

    changes = []
    for change in response.changes[:40]:
        before = masked.unmask(change.before).strip()
        after = masked.unmask(change.after).strip()
        kind = "replaced" if before and after else ("removed" if before else "added")
        changes.append(Change(kind=kind, before=before, after=after, reason=" ".join(change.reason.split())[:300],
                              verified=not before or contains_quote(text, before)))
    if any(not c.verified for c in changes):
        warnings.append("Część pozycji listy zmian nie odpowiada dokładnie tekstowi oryginału (oznaczone).")

    score_before = compute_score([f for f in rule_findings if f.source == "rule"])
    score_after = score_without_placeholders(new_text)
    if score_after < score_before:
        warnings.append("Wersja AI ma w regułach niższy wynik niż oryginał — przejrzyj ją uważnie.")
    return RewriteResult(source="ai", text=new_text, changes=changes, warnings=warnings,
                         score_before=score_before, score_after=score_after)


def rewrite_text(text: str, llm: StructuredLLM | None = None) -> RewriteResult:
    """Przepisanie przez AI, a gdy AI jest wyłączone lub zawiedzie — regułowo."""
    findings = run_rules(text)
    if llm is None:
        return rewrite_offline(text, findings, note="Tryb offline: przepisanie regułowe.")
    try:
        return rewrite_ai(text, llm, findings)
    except (*AI_ERRORS, RewriteRejected) as exc:
        log.warning("Przepisanie AI niedostępne: %s", exc)
        note = f"Odrzucono lub brak wersji AI ({exc}); użyto przepisania regułowego."
        return rewrite_offline(text, findings, note=note)
