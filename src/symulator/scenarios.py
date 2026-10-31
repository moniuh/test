"""Scenariusze rozmów jako dane (JSON w `symulator/data/`) i ich walidacja."""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

from pydantic import BaseModel, Field, model_validator

from .models import STAR_ORDER, IllegalCategory, PersonaStyle, StarElement

DATA_DIR = Path(__file__).parent / "data"

TOPIC_KEYS = (
    "motywacja",
    "mocne_strony",
    "slabe_strony",
    "oczekiwania_finansowe",
    "dyspozycyjnosc",
    "termin_rozpoczecia",
    "pytania_do_rekrutera",
)


class Competency(BaseModel):
    id: str
    name: str
    description: str
    keywords: list[str]
    sample_question: str


class HiddenFact(BaseModel):
    """Fakt, który kandydat ujawnia dopiero po dobrym dopytaniu."""

    id: str
    competency: str
    star: StarElement
    keywords: list[str]
    content: str
    hint: str = ""
    requires: list[str] = Field(default_factory=list)


class Trap(BaseModel):
    """Wątek prywatny, o którym kandydat wspomina sam. Trenujący nie powinien w niego wchodzić."""

    id: str
    category: IllegalCategory
    mention: str
    trigger_keywords: list[str] = Field(default_factory=list)
    trigger_turn: int = 4
    followup_keywords: list[str] = Field(default_factory=list)
    note: str


class Persona(BaseModel):
    name: str
    gender: str  # "K" albo "M" — do form grzecznościowych
    background: str
    style: PersonaStyle
    style_description: str
    intro: str
    topic_answers: dict[str, str]
    fallback_answers: list[str]
    closed_answers: list[str]
    fillers: list[str] = Field(default_factory=list)
    digressions: list[str] = Field(default_factory=list)
    awkward_answers: list[str] = Field(default_factory=list)


class Scenario(BaseModel):
    id: str
    title: str
    position: str
    company: str
    description: str
    difficulty: str
    competencies: list[Competency]
    persona: Persona
    facts: list[HiddenFact]
    traps: list[Trap] = Field(default_factory=list)

    @model_validator(mode="after")
    def _check_consistency(self) -> "Scenario":
        comp_ids = {c.id for c in self.competencies}
        fact_ids = [f.id for f in self.facts]
        if len(set(fact_ids)) != len(fact_ids):
            raise ValueError(f"Scenariusz {self.id}: zduplikowane id faktów")
        for fact in self.facts:
            if fact.competency not in comp_ids:
                raise ValueError(f"Scenariusz {self.id}: fakt {fact.id} wskazuje nieznaną kompetencję")
            missing = set(fact.requires) - set(fact_ids)
            if missing:
                raise ValueError(f"Scenariusz {self.id}: fakt {fact.id} wymaga nieznanych faktów {missing}")
        trap_ids = [t.id for t in self.traps]
        if len(set(trap_ids)) != len(trap_ids):
            raise ValueError(f"Scenariusz {self.id}: zduplikowane id pułapek")
        unknown_topics = set(self.persona.topic_answers) - set(TOPIC_KEYS)
        if unknown_topics:
            raise ValueError(f"Scenariusz {self.id}: nieznane tematy {unknown_topics}")
        return self

    @property
    def fact_ids(self) -> set[str]:
        return {f.id for f in self.facts}

    def fact(self, fact_id: str) -> HiddenFact | None:
        return next((f for f in self.facts if f.id == fact_id), None)

    def facts_for(self, competency_id: str) -> list[HiddenFact]:
        order = {e: i for i, e in enumerate(STAR_ORDER)}
        return sorted((f for f in self.facts if f.competency == competency_id), key=lambda f: order[f.star])

    def competency(self, competency_id: str) -> Competency | None:
        return next((c for c in self.competencies if c.id == competency_id), None)

    def trap(self, trap_id: str) -> Trap | None:
        return next((t for t in self.traps if t.id == trap_id), None)

    def public_view(self) -> dict:
        """Widok dla UI — bez ukrytych faktów i treści pułapek (trenujący ich nie zna)."""
        return {
            "id": self.id,
            "title": self.title,
            "position": self.position,
            "company": self.company,
            "description": self.description,
            "difficulty": self.difficulty,
            "competencies": [{"id": c.id, "name": c.name, "description": c.description} for c in self.competencies],
            "candidate": {
                "name": self.persona.name,
                "gender": self.persona.gender,
                "background": self.persona.background,
                "style": self.persona.style,
            },
            "facts_total": len(self.facts),
            "traps_total": len(self.traps),
        }


class ScenarioNotFound(KeyError):
    pass


_DIFFICULTY_ORDER = {"łatwy": 0, "średni": 1, "trudny": 2}


def load_scenarios(directory: Path = DATA_DIR) -> dict[str, Scenario]:
    """Wczytuje scenariusze z JSON, uporządkowane od najłatwiejszego."""
    loaded = [Scenario.model_validate(json.loads(p.read_text(encoding="utf-8"))) for p in sorted(directory.glob("*.json"))]
    loaded.sort(key=lambda s: (_DIFFICULTY_ORDER.get(s.difficulty, 9), s.id))
    return {s.id: s for s in loaded}


@lru_cache(maxsize=1)
def default_scenarios() -> dict[str, Scenario]:
    return load_scenarios()


def get_scenario(scenario_id: str) -> Scenario:
    try:
        return default_scenarios()[scenario_id]
    except KeyError as exc:
        raise ScenarioNotFound(scenario_id) from exc
