import pytest

from preselekcja.bias import CandidateOutcome, GroupCounts, adverse_impact, bias_report


def by_group(results):
    return {r.group: r for r in results}


def test_four_fifths_rule_on_known_numbers():
    # Klasyczny przykład: 48/80 = 60% vs 12/40 = 30% → IR = 0,5 < 0,8
    results, reference = adverse_impact({"male": GroupCounts(48, 80), "female": GroupCounts(12, 40)})
    g = by_group(results)
    assert reference == "male"
    assert g["male"].rate == 0.6 and g["female"].rate == 0.3
    assert g["female"].impact_ratio == 0.5 and g["female"].flagged
    assert g["male"].impact_ratio == 1.0 and not g["male"].flagged


def test_ratio_exactly_at_four_fifths_is_not_flagged():
    results, _ = adverse_impact({"a": GroupCounts(50, 100), "b": GroupCounts(40, 100)})
    assert by_group(results)["b"].impact_ratio == 0.8
    assert not by_group(results)["b"].flagged


def test_just_below_four_fifths_is_flagged():
    results, _ = adverse_impact({"a": GroupCounts(50, 100), "b": GroupCounts(39, 100)})
    assert by_group(results)["b"].flagged


def test_undisclosed_group_is_excluded_from_comparison():
    results, reference = adverse_impact(
        {"female": GroupCounts(10, 20), "male": GroupCounts(5, 20), "undisclosed": GroupCounts(9, 9)}
    )
    g = by_group(results)
    assert reference == "female"
    assert g["undisclosed"].impact_ratio is None and g["undisclosed"].excluded_from_ratio
    assert g["male"].impact_ratio == 0.5


def test_no_selected_at_all_gives_no_ratio():
    results, reference = adverse_impact({"a": GroupCounts(0, 10), "b": GroupCounts(0, 5)})
    assert all(r.impact_ratio is None and not r.flagged for r in results)


def test_small_sample_flag():
    results, _ = adverse_impact({"a": GroupCounts(5, 29), "b": GroupCounts(20, 30)})
    g = by_group(results)
    assert g["a"].small_sample and not g["b"].small_sample


def test_report_stages_and_warnings():
    outcomes = [
        CandidateOutcome("invite", "invited", {"gender": "female", "age_group": "30_49", "disability": "no"}),
        CandidateOutcome("mismatch", "new", {"gender": "female", "age_group": "under_30", "disability": "no"}),
        CandidateOutcome("invite", "rejected", {"gender": "male", "age_group": "30_49", "disability": "no"}),
        CandidateOutcome("consider", "invited", {"gender": "male", "age_group": "50_plus", "disability": "no"}),
        CandidateOutcome("invite", "new", None),  # bez ankiety — nie wchodzi do raportu
    ]
    report = bias_report(outcomes)
    assert report.candidates_total == 5 and report.respondents == 4
    gender = next(d for d in report.dimensions if d.dimension == "gender")
    stages = {s.stage: by_group(s.groups) for s in gender.stages}
    assert (stages["ai_pass"]["female"].selected, stages["ai_pass"]["female"].total) == (1, 2)
    assert stages["ai_pass"]["male"].rate == 1.0
    assert stages["ai_pass"]["female"].impact_ratio == 0.5 and stages["ai_pass"]["female"].flagged
    assert stages["ai_invite"]["male"].selected == 1
    assert stages["human_invited"]["female"].selected == 1 and stages["human_invited"]["male"].selected == 1
    assert any("Mała próba" in w for w in report.warnings)


def test_report_without_respondents_warns():
    report = bias_report([CandidateOutcome("invite", "new", None)])
    assert report.respondents == 0
    assert any("Brak danych" in w for w in report.warnings)


@pytest.mark.parametrize("n", [30, 60])
def test_no_small_sample_warning_for_large_sample(n):
    outcomes = [
        CandidateOutcome("invite", "new", {"gender": "female" if i % 2 else "male", "age_group": "30_49", "disability": "no"})
        for i in range(n * 2)
    ]
    assert not any("Mała próba" in w for w in bias_report(outcomes).warnings)
