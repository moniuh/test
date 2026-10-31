"""Modele danych: reguły, znaleziska, wynik audytu, przepisanie i raport wsadowy."""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

Severity = Literal["error", "warning", "suggestion"]
Category = Literal["pay_transparency", "gender_neutrality", "discrimination", "excessive_data", "clarity"]
RuleKind = Literal["pattern", "missing", "check"]
# Sposób automatycznej poprawki w przepisaniu offline:
#   replace – szablon re (\g<nazwa>) z wzorca; word_map – podmiana słów wg słownika reguły;
#   remove – usunięcie dopasowania; remove_item – usunięcie pozycji z wyliczenia, a gdy to nie
#   wyliczenie – całego zdania; remove_sentence – usunięcie zdania/punktu listy;
#   title_marker – dopisanie „(K/M)” do tytułu; none – tylko do ręcznej poprawy.
FixMode = Literal["none", "replace", "word_map", "remove", "remove_item", "remove_sentence", "title_marker"]
Source = Literal["ai", "offline"]

SEVERITY_LABELS: dict[str, str] = {"error": "błąd", "warning": "ostrzeżenie", "suggestion": "sugestia"}
CATEGORY_LABELS: dict[str, str] = {
    "pay_transparency": "Jawność wynagrodzeń",
    "gender_neutrality": "Neutralność płciowa",
    "discrimination": "Dyskryminacja",
    "excessive_data": "Dane nadmiarowe i RODO",
    "clarity": "Przejrzystość",
}


class RulePattern(BaseModel):
    """Pojedynczy wzorzec reguły; `replace` i `fix` nadpisują ustawienia reguły."""

    model_config = ConfigDict(frozen=True)

    regex: str
    replace: str | None = None
    fix: FixMode | None = None


class Rule(BaseModel):
    """Reguła audytu opisana danymi (wzorce, wyjątki, wyjaśnienie, podstawa prawna, poprawka)."""

    model_config = ConfigDict(frozen=True)

    id: str
    category: Category
    severity: Severity
    title: str
    kind: RuleKind = "pattern"
    patterns: tuple[RulePattern, ...] = ()
    # Wyjątek, gdy jego dopasowanie nakłada się na znalezisko (np. „React Native”).
    exceptions: tuple[str, ...] = ()
    # Wyjątek, gdy występuje w tym samym zdaniu lub punkcie listy (np. „bez względu na wiek”).
    sentence_exceptions: tuple[str, ...] = ()
    # Reguła nie działa, gdy wzorzec występuje gdziekolwiek w ogłoszeniu.
    doc_exceptions: tuple[str, ...] = ()
    # Reguła działa tylko, gdy cecha wykrywana przez wskazaną regułę `missing` jest nieobecna.
    only_if_absent: str | None = None
    check: str | None = None
    params: dict[str, int] = Field(default_factory=dict)
    explanation: str
    legal_basis: str
    suggestion: str
    fix: FixMode = "none"
    word_map: dict[str, str] = Field(default_factory=dict)
    template: str | None = None
    template_position: Literal["top", "end"] = "top"


class Fix(BaseModel):
    """Proponowana zmiana tekstu: zakres [start, end) zastąpić `text`."""

    start: int
    end: int
    text: str
    mode: FixMode


class Finding(BaseModel):
    id: str = ""
    rule_id: str
    category: Category
    severity: Severity
    title: str
    explanation: str
    legal_basis: str
    suggestion: str
    start: int | None = None
    end: int | None = None
    fragment: str | None = None
    source: Literal["rule", "ai"] = "rule"
    fix: Fix | None = None


class RejectedQuote(BaseModel):
    quote: str
    reason: str


class CategorySummary(BaseModel):
    category: Category
    label: str
    errors: int = 0
    warnings: int = 0
    suggestions: int = 0
    penalty: int = 0
    score: int = 100


class SeverityCounts(BaseModel):
    error: int = 0
    warning: int = 0
    suggestion: int = 0


class AuditResult(BaseModel):
    score: int
    verdict: str
    verdict_label: str
    formula: str
    counts: SeverityCounts
    categories: list[CategorySummary]
    findings: list[Finding]
    source: Source
    ai_note: str | None = None
    ai_rejected: list[RejectedQuote] = Field(default_factory=list)
    injection_suspected: bool = False
    title: str | None = None
    words: int = 0


class Change(BaseModel):
    kind: Literal["replaced", "removed", "added", "manual"]
    before: str
    after: str
    reason: str
    rule_id: str | None = None
    verified: bool = True


class RewriteResult(BaseModel):
    source: Source
    text: str
    changes: list[Change]
    warnings: list[str] = Field(default_factory=list)
    score_before: int
    score_after: int
    ai_note: str | None = None


class BatchInput(BaseModel):
    id: str
    title: str | None = None
    text: str


class BatchItemResult(BaseModel):
    id: str
    title: str | None
    score: int
    verdict: str
    verdict_label: str
    errors: int
    warnings: int
    suggestions: int
    rules: list[str]


class RuleStat(BaseModel):
    rule_id: str
    title: str
    category: Category
    severity: Severity
    ads: int
    occurrences: int
    share: float


class CategoryStat(BaseModel):
    category: Category
    label: str
    findings: int
    ads: int


class BatchReport(BaseModel):
    count: int
    average_score: float
    median_score: float
    min_score: int
    max_score: int
    with_errors: int
    verdicts: dict[str, int]
    top_rules: list[RuleStat]
    categories: list[CategoryStat]
    items: list[BatchItemResult]
    skipped: list[str] = Field(default_factory=list)
