import pytest

from preselekcja.models import RequirementResult
from preselekcja.scoring import THRESHOLDS, Thresholds, compute_score


def rr(verdict, weight=1, kind="nice", name="W"):
    return RequirementResult(requirement_id=name.lower(), name=name, kind=kind, weight=weight, verdict=verdict)


def test_weighted_score_from_verdicts():
    results = [rr("meets", 5, "must"), rr("partial", 4, "must"), rr("fails", 1), rr("no_data", 2)]
    s = compute_score(results)
    # (5*1 + 4*0.5 + 0 + 0) / 12 * 100
    assert s.score == pytest.approx(58.3)
    assert s.weighted_points == 7.0 and s.max_points == 12.0
    assert [r.points for r in results] == [5.0, 2.0, 0.0, 0.0]


@pytest.mark.parametrize(
    "meets_weight,other_weight,expected",
    [
        (70, 30, "invite"),      # dokładnie 70 → zaproś
        (699, 301, "consider"),  # 69.9 → do rozważenia
        (40, 60, "consider"),    # dokładnie 40
        (399, 601, "mismatch"),  # 39.9
    ],
)
def test_threshold_boundaries(meets_weight, other_weight, expected):
    results = [rr("meets", meets_weight), rr("no_data", other_weight)]
    assert compute_score(results).recommendation == expected


def test_thresholds_are_explicit_constants():
    assert THRESHOLDS.invite_min == 70 and THRESHOLDS.consider_min == 40
    custom = compute_score([rr("meets", 1), rr("no_data", 1)], Thresholds(invite_min=50, consider_min=20))
    assert custom.recommendation == "invite"


def test_failed_mandatory_caps_to_mismatch():
    s = compute_score([rr("fails", 1, "must", "Prawo jazdy"), rr("meets", 9)])
    assert s.score == 90.0
    assert s.recommendation == "mismatch"
    assert "Prawo jazdy" in s.gates[0]


def test_missing_mandatory_caps_to_consider():
    s = compute_score([rr("no_data", 1, "must", "ZUS"), rr("meets", 9)])
    assert s.recommendation == "consider"
    assert "ZUS" in s.gates[0]


def test_gates_never_raise_recommendation():
    s = compute_score([rr("no_data", 5, "must"), rr("meets", 1)])
    assert s.recommendation == "mismatch" and s.gates == []


def test_empty_requirements_rejected():
    with pytest.raises(ValueError):
        compute_score([])
