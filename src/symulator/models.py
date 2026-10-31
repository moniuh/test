"""Wspólne typy domenowe: kategorie pytań niedozwolonych, typy pytań, emocje, elementy STAR."""
from __future__ import annotations

from typing import Literal

Source = Literal["ai", "offline"]

StarElement = Literal["S", "T", "A", "R"]
STAR_ORDER: tuple[StarElement, ...] = ("S", "T", "A", "R")
STAR_LABELS: dict[str, str] = {
    "S": "Sytuacja",
    "T": "Zadanie",
    "A": "Działanie",
    "R": "Rezultat",
}

IllegalCategory = Literal[
    "rodzina",
    "wiek",
    "zdrowie",
    "religia",
    "narodowosc",
    "zwiazki_zawodowe",
    "orientacja",
    "poglady",
    "wynagrodzenie_historia",
    "inne",
]
ILLEGAL_CATEGORIES: tuple[str, ...] = (
    "rodzina",
    "wiek",
    "zdrowie",
    "religia",
    "narodowosc",
    "zwiazki_zawodowe",
    "orientacja",
    "poglady",
    "wynagrodzenie_historia",
    "inne",
)

QuestionType = Literal["otwarte", "zamkniete", "sugerujace", "wielokrotne", "behawioralne", "dopytujace"]
QUESTION_TYPES: tuple[str, ...] = ("otwarte", "zamkniete", "sugerujace", "wielokrotne", "behawioralne", "dopytujace")
QUESTION_TYPE_LABELS: dict[str, str] = {
    "otwarte": "otwarte",
    "zamkniete": "zamknięte",
    "sugerujace": "sugerujące",
    "wielokrotne": "wielokrotne",
    "behawioralne": "behawioralne (STAR)",
    "dopytujace": "dopytujące",
}
# Typy, w których kandydat może swobodnie opowiadać — liczą się do „% pytań otwartych”.
OPEN_TYPES: frozenset[str] = frozenset({"otwarte", "behawioralne", "dopytujace"})

Emotion = Literal["neutralna", "pewna_siebie", "zestresowana", "zaklopotana", "entuzjastyczna", "zdystansowana"]
EMOTIONS: tuple[str, ...] = ("neutralna", "pewna_siebie", "zestresowana", "zaklopotana", "entuzjastyczna", "zdystansowana")
EMOTION_LABELS: dict[str, str] = {
    "neutralna": "neutralnie",
    "pewna_siebie": "pewnie",
    "zestresowana": "ze stresem",
    "zaklopotana": "z zakłopotaniem",
    "entuzjastyczna": "z entuzjazmem",
    "zdystansowana": "z dystansem",
}

PersonaStyle = Literal["gadatliwy", "zestresowany", "unikajacy_konkretow"]
STYLE_LABELS: dict[str, str] = {
    "gadatliwy": "gadatliwy",
    "zestresowany": "zestresowany",
    "unikajacy_konkretow": "unikający konkretów",
}
