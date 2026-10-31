"""Audyt ogłoszenia: reguły → (opcjonalnie) drugie przejście AI → scalenie → wynik."""
from __future__ import annotations

import logging
import re
from typing import Literal

import anthropic
from pydantic import BaseModel, ValidationError

from . import prompts
from .engine import TextContext, run_rules, sort_findings
from .llm import LLMUnavailable, StructuredLLM
from .models import CATEGORY_LABELS, AuditResult, Finding, RejectedQuote
from .scoring import FORMULA, VERDICTS, category_summary, compute_score, count_severities, verdict
from .textutil import MaskedText, count_words, locate_quote, mask_pii

log = logging.getLogger(__name__)

MAX_AI_ISSUES = 8
AI_ERRORS = (LLMUnavailable, anthropic.AnthropicError, ValidationError)

INJECTION_PATTERNS = (
    r"zignoruj\w*\s+(?:wszystkie\s+|poprzednie\s+|powyższe\s+|wcześniejsze\s+)*(?:instrukcj|polece|zasad)\w*",
    r"ignore\s+(?:all\s+|any\s+|previous\s+|prior\s+|the\s+above\s+)*(?:instructions|rules)",
    r"\bsystem\s*prompt",
    r"\bjesteś\s+teraz\s+(?:asystentem|modelem|ai|chatbotem)",
    r"\byou\s+are\s+now\b",
    r"\b(?:oceń|ocen|daj|przyznaj)\w*\s+(?:temu\s+|to\s+)?(?:ogłoszeni\w*\s+)?(?:wynik\s+|ocenę\s+)?(?:na\s+)?100\b",
    r"</?\s*(?:ogloszenie|ogłoszenie|system|instructions?)\s*>",
)


class AIIssue(BaseModel):
    quote: str
    category: Literal["pay_transparency", "gender_neutrality", "discrimination", "excessive_data", "clarity"]
    severity: Literal["error", "warning", "suggestion"]
    title: str
    explanation: str
    legal_basis: str
    suggestion: str


class AIReview(BaseModel):
    issues: list[AIIssue]
    injection_attempt: bool


def _clip(value: str, limit: int) -> str:
    value = " ".join(value.split())
    return value if len(value) <= limit else value[: limit - 1].rstrip() + "…"


def detect_injection(text: str) -> bool:
    """Heurystyka: czy ogłoszenie zawiera tekst przypominający polecenia dla modelu."""
    return any(re.search(p, text, re.IGNORECASE) for p in INJECTION_PATTERNS)


def merge_ai_findings(
    text: str,
    rule_findings: list[Finding],
    issues: list[AIIssue],
    masked: MaskedText | None = None,
) -> tuple[list[Finding], list[RejectedQuote]]:
    """Weryfikuje cytaty AI (muszą być podciągiem tekstu) i odrzuca te, które nakładają się na już wykryte."""
    lookup = masked.masked if masked else text
    taken = [(f.start, f.end) for f in rule_findings if f.start is not None and f.end is not None]
    accepted: list[Finding] = []
    rejected: list[RejectedQuote] = []
    for issue in issues[: MAX_AI_ISSUES * 2]:
        quote = _clip(issue.quote, 300)
        spans = locate_quote(lookup, issue.quote)
        if not spans:
            rejected.append(RejectedQuote(quote=quote, reason="fragmentu nie ma w treści ogłoszenia"))
            continue
        if masked:
            spans = [masked.to_original(s, e) for s, e in spans]
        free = [s for s in spans if not any(s[0] < t[1] and t[0] < s[1] for t in taken)]
        if not free:
            rejected.append(RejectedQuote(quote=quote, reason="pokrywa się z już wykrytym znaleziskiem"))
            continue
        if len(accepted) >= MAX_AI_ISSUES:
            rejected.append(RejectedQuote(quote=quote, reason="przekroczony limit znalezisk AI"))
            continue
        start, end = free[0]
        taken.append((start, end))
        accepted.append(
            Finding(
                rule_id="ai",
                category=issue.category,
                severity=issue.severity,
                title=_clip(issue.title, 90) or f"AI: {CATEGORY_LABELS[issue.category]}",
                explanation=_clip(issue.explanation, 700),
                legal_basis=_clip(issue.legal_basis, 300) + " (wskazane przez AI — zweryfikuj)",
                suggestion=_clip(issue.suggestion, 400),
                start=start,
                end=end,
                fragment=text[start:end],
                source="ai",
            )
        )
    return accepted, rejected


def ai_review(
    text: str, rule_findings: list[Finding], llm: StructuredLLM
) -> tuple[list[Finding], list[RejectedQuote], bool]:
    """Drugie przejście AI na tekście z zamaskowanymi danymi osobowymi."""
    masked = mask_pii(text)
    detected = [f.fragment for f in rule_findings if f.fragment]
    review = llm.generate(
        system=prompts.REVIEW_SYSTEM,
        prompt=prompts.build_review_prompt(masked.masked, detected),
        schema=AIReview,
        effort="medium",
    )
    accepted, rejected = merge_ai_findings(text, rule_findings, review.issues, masked)
    return accepted, rejected, review.injection_attempt


def audit_text(text: str, llm: StructuredLLM | None = None) -> AuditResult:
    """Pełny audyt. Bez `llm` (lub gdy AI zawiedzie) wynik pochodzi wyłącznie z reguł."""
    findings = run_rules(text)
    source: Literal["ai", "offline"] = "offline"
    note: str | None = None
    rejected: list[RejectedQuote] = []
    injection = detect_injection(text)
    if llm is not None:
        try:
            accepted, rejected, ai_injection = ai_review(text, findings, llm)
            findings = sort_findings(findings + accepted)
            source = "ai"
            injection = injection or ai_injection
        except AI_ERRORS as exc:
            log.warning("Drugie przejście AI niedostępne: %s", exc)
            note = f"AI niedostępne ({exc}); wynik pochodzi z silnika reguł."
    else:
        note = "Tryb offline: wynik pochodzi z silnika reguł."
    score = compute_score(findings)
    counts = count_severities(findings)
    verdict_code = verdict(score, counts)
    ctx = TextContext(text)
    title = text[ctx.title_span[0]:ctx.title_span[1]] if ctx.title_span else None
    return AuditResult(
        score=score,
        verdict=verdict_code,
        verdict_label=VERDICTS[verdict_code],
        formula=FORMULA,
        counts=counts,
        categories=category_summary(findings),
        findings=findings,
        source=source,
        ai_note=note,
        ai_rejected=rejected,
        injection_suspected=injection,
        title=title,
        words=count_words(text),
    )
