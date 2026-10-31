"""Budowa testu (losowanie z ziarnem) i deterministyczne ocenianie."""
from __future__ import annotations

import random
from dataclasses import dataclass


@dataclass(frozen=True)
class QuestionSnapshot:
    """Treść pytania zamrożona w chwili rozpoczęcia podejścia (dowód, co widział pracownik)."""

    id: int
    text: str
    options: tuple[str, ...]
    correct_index: int
    explanation: str = ""
    source_quote: str = ""

    def to_dict(self) -> dict:
        return {
            "id": self.id, "text": self.text, "options": list(self.options),
            "correct_index": self.correct_index, "explanation": self.explanation,
            "source_quote": self.source_quote,
        }

    @classmethod
    def from_dict(cls, data: dict) -> "QuestionSnapshot":
        return cls(
            id=int(data["id"]), text=data["text"], options=tuple(data["options"]),
            correct_index=int(data["correct_index"]), explanation=data.get("explanation", ""),
            source_quote=data.get("source_quote", ""),
        )


@dataclass(frozen=True)
class QuizItem:
    question: QuestionSnapshot
    permutation: tuple[int, ...]  # pozycja wyświetlona → indeks oryginalnej odpowiedzi

    @property
    def options(self) -> list[str]:
        return [self.question.options[i] for i in self.permutation]

    @property
    def correct_display_index(self) -> int:
        return self.permutation.index(self.question.correct_index)


def select_questions(pool: list[QuestionSnapshot], seed: int, limit: int | None) -> list[QuestionSnapshot]:
    """Wybór pytań do podejścia; niezależny od kolejności, w jakiej przyszły z bazy."""
    canonical = sorted(pool, key=lambda q: q.id)
    if limit is None or limit >= len(canonical):
        return canonical
    rng = random.Random(f"{seed}:wybor")
    return sorted(rng.sample(canonical, limit), key=lambda q: q.id)


def build_quiz(selected: list[QuestionSnapshot], seed: int) -> list[QuizItem]:
    """Kolejność pytań i odpowiedzi wynika wyłącznie z ziarna i identyfikatorów pytań."""
    ordered = sorted(selected, key=lambda q: q.id)
    random.Random(f"{seed}:kolejnosc").shuffle(ordered)
    items = []
    for q in ordered:
        perm = list(range(len(q.options)))
        random.Random(f"{seed}:odpowiedzi:{q.id}").shuffle(perm)
        items.append(QuizItem(question=q, permutation=tuple(perm)))
    return items


@dataclass(frozen=True)
class GradedItem:
    question_id: int
    chosen_display_index: int | None
    correct_display_index: int
    correct: bool


@dataclass(frozen=True)
class GradeResult:
    score: int
    total: int
    percent: int
    passed: bool
    items: list[GradedItem]


class GradingError(ValueError):
    pass


def grade(items: list[QuizItem], answers: dict[int, int], pass_threshold: int) -> GradeResult:
    """Ocena: `answers` mapuje id pytania → indeks wyświetlonej odpowiedzi. Brak odpowiedzi = błąd."""
    known = {item.question.id for item in items}
    unknown = set(answers) - known
    if unknown:
        raise GradingError(f"Odpowiedzi dotyczą pytań spoza tego podejścia: {sorted(unknown)}")
    graded: list[GradedItem] = []
    for item in items:
        chosen = answers.get(item.question.id)
        if chosen is not None and not 0 <= chosen < len(item.permutation):
            raise GradingError(f"Nieprawidłowy numer odpowiedzi w pytaniu {item.question.id}")
        correct = chosen is not None and item.permutation[chosen] == item.question.correct_index
        graded.append(GradedItem(item.question.id, chosen, item.correct_display_index, correct))
    total = len(items)
    score = sum(g.correct for g in graded)
    # Porównanie na liczbach całkowitych — bez błędów zaokrągleń na progu.
    passed = total > 0 and score * 100 >= pass_threshold * total
    percent = round(score * 100 / total) if total else 0
    return GradeResult(score=score, total=total, percent=percent, passed=passed, items=graded)
