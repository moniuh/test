import json

import pytest

from symulator.models import ILLEGAL_CATEGORIES, STAR_ORDER
from symulator.scenarios import DATA_DIR, Scenario, ScenarioNotFound, default_scenarios, get_scenario


def test_at_least_three_scenarios_load():
    scenarios = default_scenarios()
    assert len(scenarios) >= 3
    assert {"obsluga-klienta", "junior-developer", "kierownik-zmiany"} <= set(scenarios)


@pytest.mark.parametrize("scenario_id", ["obsluga-klienta", "junior-developer", "kierownik-zmiany"])
def test_every_competency_has_full_star_set(scenario_id):
    scenario = get_scenario(scenario_id)
    for comp in scenario.competencies:
        assert [f.star for f in scenario.facts_for(comp.id)] == list(STAR_ORDER)
    for fact in scenario.facts:
        assert fact.keywords or fact.star != "S", fact.id
    for trap in scenario.traps:
        assert trap.category in ILLEGAL_CATEGORIES
        assert trap.followup_keywords


def test_public_view_hides_hidden_facts_and_trap_content():
    scenario = get_scenario("obsluga-klienta")
    view = json.dumps(scenario.public_view(), ensure_ascii=False)
    for fact in scenario.facts:
        assert fact.content not in view
    for trap in scenario.traps:
        assert trap.mention not in view


def test_unknown_scenario_raises():
    with pytest.raises(ScenarioNotFound):
        get_scenario("nie-ma")


def test_validation_rejects_unknown_requires():
    raw = json.loads((DATA_DIR / "obsluga-klienta.json").read_text(encoding="utf-8"))
    raw["facts"][1]["requires"] = ["nie_istnieje"]
    with pytest.raises(ValueError):
        Scenario.model_validate(raw)


def test_validation_rejects_duplicate_fact_ids():
    raw = json.loads((DATA_DIR / "obsluga-klienta.json").read_text(encoding="utf-8"))
    raw["facts"][1]["id"] = raw["facts"][0]["id"]
    with pytest.raises(ValueError):
        Scenario.model_validate(raw)


@pytest.mark.parametrize("scenario_id", ["obsluga-klienta", "junior-developer", "kierownik-zmiany"])
def test_offline_demo_is_playable_ideal_interviewer_reveals_every_fact(scenario_id, player):
    """Rekruter zadający pytanie behawioralne i dopytujący o T, A, R wydobywa wszystkie fakty."""
    scenario = get_scenario(scenario_id)
    pan = "Pani" if scenario.persona.gender == "K" else "Pan"
    zrobil = "zrobiła" if scenario.persona.gender == "K" else "zrobił"
    questions = []
    for comp in scenario.competencies:
        questions += [
            comp.sample_question,
            f"Jakie było {pan} zadanie w tej sytuacji?",
            f"Co konkretnie {pan} wtedy {zrobil}?",
            "Jaki był efekt?",
        ]
    turns = player(scenario, questions)
    revealed = {fid for t in turns for fid in t.revealed_fact_ids}
    assert revealed == scenario.fact_ids
