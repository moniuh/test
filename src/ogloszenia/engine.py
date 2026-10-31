"""Silnik reguł: interpretuje rekordy `Rule` i zwraca znaleziska z offsetami oraz propozycjami poprawek.

Semantyka pól reguły:
- `patterns` — dla `pattern`: każde dopasowanie to znalezisko; dla `missing`: obecność któregokolwiek
  dopasowania oznacza, że element jest w ogłoszeniu; dla `check`: dane wejściowe funkcji sprawdzającej.
- `exceptions` — dopasowanie wyjątku nakładające się na znalezisko je anuluje (dla `check` tytułu:
  wyjątek w tytule anuluje sprawdzenie).
- `sentence_exceptions` — wyjątek w tym samym zdaniu / punkcie listy anuluje znalezisko
  (dla `missing`: dopasowanie w takim zdaniu nie liczy się jako obecność).
- `doc_exceptions` — wyjątek gdziekolwiek w tekście wyłącza regułę.
"""
from __future__ import annotations

import re
from collections.abc import Callable, Iterable
from functools import cache, cached_property

from .models import Finding, Fix, Rule, RulePattern
from .rules import CURRENCY_PRESENT, GENDERED_TITLES, GROSS_NET, PERIOD, RULES, RULES_BY_ID
from .textutil import SentenceIndex, count_words, is_bullet, line_bounds

FLAGS = re.IGNORECASE | re.MULTILINE
_BULLET_PREFIX = re.compile(r"[ \t]*(?:[-*•–·▪◦✓✔]|\d+[.)])?[ \t]*")
_REQ_HEADER = re.compile(
    r"^[ \t]*(?:#+[ \t]*)?(?:\*\*)?(?:wymagania|wymagamy|oczekujemy|oczekiwania|czego\s+oczekujemy|nasze\s+wymagania"
    r"|twój\s+profil|kogo\s+szukamy|co\s+musisz\s+mieć|musisz\s+mieć|must[\s-]have|jesteś\s+osobą,?\s+która)"
    r"(?:[ \t]+\w+){0,3}[ \t]*:?(?:\*\*)?[ \t]*$",
    FLAGS,
)
_SECTION_STOP = re.compile(
    r"mile\s+widzian|oferujemy|co\s+oferujemy|obowiązki|zakres|benefit|nice[\s-]to[\s-]have|o\s+nas\b|o\s+firmie"
    r"|aplikuj|jak\s+aplikować|kontakt|wynagrodzenie|klauzul|atutem|będzie\s+plusem|dajemy|zapewniamy|proponujemy",
    re.IGNORECASE,
)


@cache
def rx(pattern: str) -> re.Pattern[str]:
    return re.compile(pattern, FLAGS)


class TextContext:
    """Tekst ogłoszenia z leniwie liczonymi pomocniczymi strukturami (zdania, tytuł)."""

    def __init__(self, text: str) -> None:
        self.text = text
        self.sentences = SentenceIndex(text)

    def sentence_text(self, pos: int) -> str:
        s, e = self.sentences.at(pos)
        return self.text[s:e]

    @cached_property
    def title_span(self) -> tuple[int, int] | None:
        """Tytuł: linia „Stanowisko: …” albo pierwsza niepusta, krótka linia ogłoszenia."""
        m = rx(r"^[ \t]*(?:stanowisko|nazwa\s+stanowiska)[ \t]*:[ \t]*(\S[^\n]*?)[ \t]*$").search(self.text)
        if m:
            return m.span(1)
        pos = 0
        for line in self.text.splitlines(keepends=True):
            content = line.rstrip("\r\n")
            stripped = content.strip()
            if stripped:
                # Nagłówek sekcji („Wymagania:”) albo długi akapit to nie tytuł.
                if len(stripped) > 120 or stripped.rstrip("*").endswith(":"):
                    return None
                lead = re.match(r"[ \t#*]*", content).end()
                end = len(content.rstrip(" \t*"))
                return (pos + lead, pos + end) if end > lead else None
            pos += len(line)
        return None


# --- pomocnicze -----------------------------------------------------------------------------------

def _overlaps(a: tuple[int, int], b: tuple[int, int]) -> bool:
    return a[0] < b[1] and b[0] < a[1]


def _doc_excepted(rule: Rule, text: str) -> bool:
    return any(rx(p).search(text) for p in rule.doc_exceptions)


def _sentence_excepted(rule: Rule, ctx: TextContext, pos: int) -> bool:
    if not rule.sentence_exceptions:
        return False
    sentence = ctx.sentence_text(pos)
    return any(rx(p).search(sentence) for p in rule.sentence_exceptions)


def _exception_spans(rule: Rule, text: str) -> list[tuple[int, int]]:
    return [m.span() for p in rule.exceptions for m in rx(p).finditer(text)]


def presence_matches(rule: Rule, ctx: TextContext) -> list[re.Match[str]]:
    """Dopasowania wzorców reguły z pominięciem tych w zdaniach objętych wyjątkiem."""
    found = []
    for pattern in rule.patterns:
        for m in rx(pattern.regex).finditer(ctx.text):
            if m.end() > m.start() and not _sentence_excepted(rule, ctx, m.start()):
                found.append(m)
    return found


def _finding(rule: Rule, ctx: TextContext, start: int | None, end: int | None, *,
             fix: Fix | None = None, explanation: str | None = None, suggestion: str | None = None) -> Finding:
    fragment = None
    if start is not None and end is not None:
        raw = ctx.text[start:end]
        end = start + len(raw.rstrip())
        fragment = ctx.text[start:end]
    return Finding(
        rule_id=rule.id,
        category=rule.category,
        severity=rule.severity,
        title=rule.title,
        explanation=explanation or rule.explanation,
        legal_basis=rule.legal_basis,
        suggestion=suggestion or rule.suggestion,
        start=start,
        end=end,
        fragment=fragment,
        source="rule",
        fix=fix,
    )


# --- poprawki --------------------------------------------------------------------------------------

def apply_word_map(fragment: str, mapping: dict[str, str]) -> str:
    """Podmienia słowa wg słownika (z zachowaniem wielkiej litery) i porządkuje spacje."""

    def sub(m: re.Match[str]) -> str:
        word = m.group(0)
        repl = mapping.get(word.lower())
        if repl is None:
            return word
        return repl[:1].upper() + repl[1:] if repl and word[0].isupper() else repl

    out = re.sub(r"\w+", sub, fragment)
    out = re.sub(r"(?<=\S)[ \t]{2,}(?=\S)", " ", out)
    if fragment[:1] not in (" ", "\t"):
        out = out.lstrip(" \t")
    if fragment[-1:] not in (" ", "\t"):
        out = out.rstrip(" \t")
    return out


def _replacement_fix(text: str, start: int, end: int, new: str, mode: str) -> Fix:
    original = text[start:end]
    if original[:1].isupper() and new[:1].islower():
        new = new[0].upper() + new[1:]
    if not new.strip() and original[:1].isupper():
        # Usunięcie na początku zdania: następne słowo dostaje wielką literę.
        j = end
        while j < len(text) and text[j] in " \t,":
            j += 1
        if j < len(text) and text[j].islower():
            return Fix(start=start, end=j + 1, text=text[j].upper(), mode=mode)
    return Fix(start=start, end=end, text=new, mode=mode)


def list_item_span(text: str, start: int, end: int) -> tuple[int, int] | None:
    """Zakres do usunięcia, gdy fragment jest pozycją wyliczenia („a, PESEL, b” → „a, b”)."""
    ls, le = line_bounds(text, start)
    before, after = text[ls:start], text[end:le]
    mb = re.search(r"(?:[ \t]*,[ \t]*|[ \t]+(?:i|oraz|lub|albo|ani|a\s+także)[ \t]+)$", before, re.IGNORECASE)
    if mb:
        head = before[: mb.start()]
        if head.strip() and not head.rstrip().endswith(":") and not _BULLET_PREFIX.fullmatch(head):
            return ls + mb.start(), end
    ma = re.match(r"[ \t]*,[ \t]*|[ \t]+(?:i|oraz|lub|albo|ani)[ \t]+", after, re.IGNORECASE)
    if ma and after[ma.end():].strip():
        return start, end + ma.end()
    return None


def sentence_removal_span(ctx: TextContext, pos: int) -> tuple[int, int]:
    """Zakres zdania do usunięcia; gdy zdanie to cała linia (punkt listy) — razem z linią."""
    text = ctx.text
    s, e = ctx.sentences.at(pos)
    ls, le = line_bounds(text, s)
    if _BULLET_PREFIX.fullmatch(text[ls:s]) and not text[e:le].strip():
        if le < len(text):
            return ls, le + 1
        return (ls - 1 if ls > 0 else ls), le
    while e < le and text[e] in " \t":
        e += 1
    return s, e


def build_fix(rule: Rule, pattern: RulePattern, m: re.Match[str], ctx: TextContext) -> Fix | None:
    mode = pattern.fix or ("replace" if pattern.replace is not None else rule.fix)
    text = ctx.text
    start, end = m.span()
    if mode == "none":
        return None
    if mode == "replace":
        return _replacement_fix(text, start, end, m.expand(pattern.replace or ""), mode)
    if mode == "word_map":
        new = apply_word_map(text[start:end], rule.word_map)
        return None if new == text[start:end] else _replacement_fix(text, start, end, new, mode)
    if mode == "remove":
        return _replacement_fix(text, start, end, "", mode)
    if mode == "remove_item":
        span = list_item_span(text, start, end)
        if span:
            return _replacement_fix(text, span[0], span[1], "", mode)
        mode = "remove_sentence"
    if mode == "remove_sentence":
        s, e = sentence_removal_span(ctx, start)
        return Fix(start=s, end=e, text="", mode=mode)
    return None


# --- typy reguł ------------------------------------------------------------------------------------

def _run_pattern(rule: Rule, ctx: TextContext) -> list[Finding]:
    text = ctx.text
    if _doc_excepted(rule, text):
        return []
    if rule.only_if_absent and _feature_present(rule.only_if_absent, ctx):
        return []
    exc_spans = _exception_spans(rule, text)
    candidates: list[tuple[int, int, RulePattern, re.Match[str]]] = []
    for pattern in rule.patterns:
        for m in rx(pattern.regex).finditer(text):
            span = m.span()
            if span[0] == span[1] or any(_overlaps(span, x) for x in exc_spans):
                continue
            if _sentence_excepted(rule, ctx, span[0]):
                continue
            candidates.append((span[0], span[1], pattern, m))
    # W obrębie jednej reguły nakładające się dopasowania liczymy raz (wygrywa wcześniejsze/dłuższe).
    candidates.sort(key=lambda c: (c[0], -(c[1] - c[0])))
    findings, last_end = [], -1
    for start, end, pattern, m in candidates:
        if start < last_end:
            continue
        last_end = end
        findings.append(_finding(rule, ctx, start, end, fix=build_fix(rule, pattern, m, ctx)))
    return findings


def _feature_present(rule_id: str, ctx: TextContext) -> bool:
    return bool(presence_matches(RULES_BY_ID[rule_id], ctx))


def _run_missing(rule: Rule, ctx: TextContext) -> list[Finding]:
    if _doc_excepted(rule, ctx.text) or presence_matches(rule, ctx):
        return []
    return [_finding(rule, ctx, None, None)]


def _check_title_gender(rule: Rule, ctx: TextContext) -> list[Finding]:
    span = ctx.title_span
    if span is None:
        return []
    title = ctx.text[span[0]:span[1]]
    if any(rx(p).search(title) for p in rule.exceptions):
        return []
    hits = []
    for masc, fem, masc_re, fem_re in GENDERED_TITLES:
        mm = rx(rf"\b(?:{masc_re})\b").search(title)
        fm = rx(rf"\b(?:{fem_re})\b").search(title)
        if mm and fm:
            return []  # obie formy w tytule — tytuł neutralny
        hit = mm or fm
        if hit:
            hits.append((hit.start(), hit.end(), masc, fem, bool(mm)))
    if not hits:
        return []
    start, end, masc, fem, is_masc = min(hits)
    form = "męskiej" if is_masc else "żeńskiej"
    explanation = f"{rule.explanation} Wykryto formę {form}: „{title[start:end]}”."
    suggestion = f"Dodaj „(K/M)” lub użyj obu form: „{masc} / {fem}”."
    fix = Fix(start=span[1], end=span[1], text=" (K/M)", mode="title_marker")
    return [_finding(rule, ctx, span[0] + start, span[0] + end, fix=fix, explanation=explanation,
                     suggestion=suggestion)]


def _check_salary_details(rule: Rule, ctx: TextContext) -> list[Finding]:
    text = ctx.text
    findings: list[Finding] = []
    seen_lines: set[int] = set()
    exc_spans = _exception_spans(rule, text)
    matches = sorted(presence_matches(rule, ctx), key=lambda m: m.start())
    for m in matches:
        ls, le = line_bounds(text, m.start())
        if ls in seen_lines or any(_overlaps(m.span(), x) for x in exc_spans):
            continue
        seen_lines.add(ls)
        # Sąsiednie linie dołączamy tylko, gdy nie mają cyfr (np. „(brutto, miesięcznie)”),
        # żeby nie „pożyczyć” waluty od innej kwoty, np. dopłaty do karty sportowej.
        context = text[ls:le]
        if ls > 0:
            prev = text[line_bounds(text, ls - 1)[0]:ls]
            context = (prev if not re.search(r"\d", prev) else "") + context
        if le < len(text):
            nxt = text[le:line_bounds(text, le + 1)[1]]
            context += nxt if not re.search(r"\d", nxt) else ""
        missing = []
        if not rx(CURRENCY_PRESENT).search(context):
            missing.append("waluty")
        if not rx(GROSS_NET).search(context):
            missing.append("informacji brutto/netto")
        if not rx(PERIOD).search(context):
            missing.append("okresu (np. miesięcznie, za godzinę)")
        if missing:
            explanation = f"{rule.explanation} Brakuje: {', '.join(missing)}."
            findings.append(_finding(rule, ctx, m.start(), m.end(), explanation=explanation))
    return findings


def _check_requirements_count(rule: Rule, ctx: TextContext) -> list[Finding]:
    text = ctx.text
    limit = rule.params.get("max_items", 8)
    findings = []
    lines = text.splitlines(keepends=True)
    offsets, pos = [], 0
    for line in lines:
        offsets.append(pos)
        pos += len(line)
    for i, line in enumerate(lines):
        if not _REQ_HEADER.match(line.rstrip("\r\n")):
            continue
        items, bullets_seen = 0, False
        for nxt in lines[i + 1:]:
            content = nxt.strip()
            if not content:
                if items:
                    break
                continue
            if is_bullet(nxt):
                bullets_seen = True
                if not any(rx(p).search(content) for p in rule.exceptions):
                    items += 1
                continue
            if _SECTION_STOP.search(content) and len(content) < 60 or content.endswith(":") or bullets_seen:
                break
            if len(content) < 150:
                items += 1
        if items > limit:
            start = offsets[i] + (len(line) - len(line.lstrip()))
            end = offsets[i] + len(line.rstrip())
            explanation = f"{rule.explanation} Lista ma {items} pozycji (zalecane maksimum: {limit})."
            findings.append(_finding(rule, ctx, start, end, explanation=explanation))
    return findings


def _check_long_sentences(rule: Rule, ctx: TextContext) -> list[Finding]:
    limit = rule.params.get("max_words", 35)
    findings = []
    for start, end in ctx.sentences.spans:
        sentence = ctx.text[start:end]
        words = count_words(sentence)
        if words <= limit or any(rx(p).search(sentence) for p in rule.sentence_exceptions):
            continue
        explanation = f"{rule.explanation} Zdanie ma {words} słów (próg: {limit})."
        findings.append(_finding(rule, ctx, start, end, explanation=explanation))
    return findings


CHECKS: dict[str, Callable[[Rule, TextContext], list[Finding]]] = {
    "title_gender": _check_title_gender,
    "salary_details": _check_salary_details,
    "requirements_count": _check_requirements_count,
    "long_sentences": _check_long_sentences,
}


def run_rule(rule: Rule, text: str, ctx: TextContext | None = None) -> list[Finding]:
    """Uruchamia jedną regułę (przydatne w testach i do diagnostyki)."""
    ctx = ctx or TextContext(text)
    if rule.kind == "pattern":
        return _run_pattern(rule, ctx)
    if rule.kind == "missing":
        return _run_missing(rule, ctx)
    if rule.check not in CHECKS:
        raise ValueError(f"Nieznana funkcja sprawdzająca: {rule.check}")
    return CHECKS[rule.check](rule, ctx)


def run_rules(text: str, rules: Iterable[Rule] = RULES) -> list[Finding]:
    """Wszystkie znaleziska reguł, posortowane wg pozycji (znaleziska bez pozycji na końcu)."""
    ctx = TextContext(text)
    findings: list[Finding] = []
    for rule in rules:
        findings.extend(run_rule(rule, text, ctx))
    return sort_findings(findings)


def sort_findings(findings: list[Finding]) -> list[Finding]:
    order = {"error": 0, "warning": 1, "suggestion": 2}
    ordered = sorted(
        findings,
        key=lambda f: (f.start is None, f.start if f.start is not None else 0, order[f.severity], f.rule_id),
    )
    return [f.model_copy(update={"id": f"f{i}"}) for i, f in enumerate(ordered, start=1)]
