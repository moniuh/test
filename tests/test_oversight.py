from preselekcja.models import Decision
from preselekcja.oversight import is_override, oversight_metrics


def d(i, cid, status, rec, viewed=True, ts=None):
    return Decision(
        id=i, candidate_id=cid, status=status, reason="uzasadnienie decyzji", actor="r1", ai_recommendation=rec,
        is_override=is_override(rec, status), viewed_before=viewed, created_at=ts or f"2026-10-31T10:00:{i:02d}",
    )


def test_override_definition():
    assert is_override("invite", "rejected")
    assert is_override("mismatch", "invited")
    assert not is_override("invite", "invited")
    assert not is_override("consider", "rejected")
    assert not is_override("mismatch", "on_hold")


def test_override_rate_counts_latest_final_decision_with_directional_recommendation():
    decisions = [
        d(1, 1, "rejected", "invite"),      # override
        d(2, 2, "invited", "invite"),       # zgodna
        d(3, 3, "invited", "mismatch"),     # override
        d(4, 4, "rejected", "mismatch"),    # zgodna
        d(5, 5, "rejected", "consider"),    # bez kierunku — poza mianownikiem
        d(6, 6, "on_hold", "invite"),       # nie jest decyzją końcową
        d(7, 2, "on_hold", "invite"),       # późniejsze wstrzymanie nie kasuje decyzji końcowej
        d(8, 4, "invited", "mismatch"),     # zmiana decyzji: ostatnia końcowa → override
    ]
    r = oversight_metrics(decisions)
    assert r.final_decisions == 5
    assert r.directional_decisions == 4
    assert r.overrides == 3
    assert r.override_rate == 0.75 and r.agreement_rate == 0.25
    assert r.on_hold == 2


def test_no_decisions():
    r = oversight_metrics([])
    assert r.override_rate is None and r.final_decisions == 0
    assert "Za mało decyzji" in r.interpretation[0]


def test_low_override_rate_warns_about_automation_bias():
    decisions = [d(i, i, "invited", "invite") for i in range(1, 21)]
    r = oversight_metrics(decisions)
    assert r.override_rate == 0.0
    assert "automation bias" in r.interpretation[0]


def test_decisions_without_review_are_counted():
    r = oversight_metrics([d(1, 1, "invited", "invite", viewed=False), d(2, 2, "rejected", "mismatch")])
    assert r.decisions_without_review == 1
    assert any("bez otwarcia" in n for n in r.interpretation)
