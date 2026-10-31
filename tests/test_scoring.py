"""Formuła wyniku 0–100, werdykt i podsumowanie kategorii."""
from __future__ import annotations

import pytest

from ogloszenia.audit import audit_text
from ogloszenia.models import Finding
from ogloszenia.scoring import FORMULA, WEIGHTS, category_summary, compute_score, count_severities, verdict


def make(rule_id="r", severity="error", category="discrimination", source="rule", start=0, end=1) -> Finding:
    return Finding(rule_id=rule_id, category=category, severity=severity, title="t", explanation="e",
                   legal_basis="l", suggestion="s", start=start, end=end, fragment="x", source=source)


def test_no_findings_gives_100_and_ok():
    assert compute_score([]) == 100
    assert verdict(100, count_severities([])) == "ok"


@pytest.mark.parametrize(("severity", "expected"), [("error", 85), ("warning", 94), ("suggestion", 98)])
def test_single_finding_weights(severity, expected):
    assert compute_score([make(severity=severity)]) == expected == 100 - WEIGHTS[severity]


def test_repeats_of_same_rule_are_capped_at_two():
    findings = [make(rule_id="disc.age", severity="warning", start=i, end=i + 1) for i in range(5)]
    assert compute_score(findings) == 100 - 2 * WEIGHTS["warning"]


def test_different_rules_add_up():
    findings = [make(rule_id="a", severity="error"), make(rule_id="b", severity="warning"),
                make(rule_id="c", severity="suggestion")]
    assert compute_score(findings) == 100 - 15 - 6 - 2


def test_ai_findings_are_counted_individually():
    findings = [make(rule_id="ai", severity="warning", source="ai", start=i * 10, end=i * 10 + 5) for i in range(3)]
    assert compute_score(findings) == 100 - 3 * WEIGHTS["warning"]


def test_score_never_below_zero():
    findings = [make(rule_id=f"r{i}") for i in range(12)]
    assert compute_score(findings) == 0
    assert verdict(0, count_severities(findings)) == "high_risk"


@pytest.mark.parametrize(("findings", "expected"), [
    ([make(severity="suggestion")], "ok"),
    ([make(severity="warning")], "minor"),
    ([make(rule_id=f"w{i}", severity="warning") for i in range(6)], "needs_work"),  # 64 pkt, bez błędów
    ([make(severity="error")], "needs_work"),
    ([make(rule_id=f"e{i}") for i in range(5)], "high_risk"),  # 25 pkt
])
def test_verdict_thresholds(findings, expected):
    assert verdict(compute_score(findings), count_severities(findings)) == expected


def test_category_summary_counts_and_scores():
    findings = [
        make(rule_id="disc.age", severity="error", category="discrimination"),
        make(rule_id="disc.age", severity="error", category="discrimination", start=5, end=6),
        make(rule_id="pay.vague", severity="warning", category="pay_transparency"),
        make(rule_id="clarity.jargon", severity="suggestion", category="clarity"),
    ]
    summary = {c.category: c for c in category_summary(findings)}
    assert len(summary) == 5
    assert summary["discrimination"].errors == 2 and summary["discrimination"].penalty == 30
    assert summary["discrimination"].score == 70
    assert summary["pay_transparency"].warnings == 1 and summary["pay_transparency"].score == 94
    assert summary["clarity"].suggestions == 1 and summary["clarity"].score == 98
    assert summary["excessive_data"].score == 100 and summary["excessive_data"].label == "Dane nadmiarowe i RODO"


def test_formula_is_explicit():
    for fragment in ("100", "15", "6", "2", "min(liczba wystąpień, 2)"):
        assert fragment in FORMULA


def test_audit_score_matches_formula(example_text):
    result = audit_text(example_text("specjalista-plac-do-poprawy.txt"))
    assert result.score == compute_score(result.findings)
    assert result.counts.error == sum(f.severity == "error" for f in result.findings)
    assert result.verdict == "needs_work" and result.verdict_label == "wymaga poprawy"
    assert result.source == "offline" and result.formula == FORMULA
    assert result.title == "Specjalista ds. płac"


def test_good_example_scores_100(example_text):
    result = audit_text(example_text("programista-dobre.txt"))
    assert result.score == 100 and result.verdict == "ok" and result.findings == []
