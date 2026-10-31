"""Wyszukiwanie BM25 (Okapi) zaimplementowane w czystym Pythonie.

Oprócz wyniku BM25 liczymy „pokrycie” — jaka część pojęć z pytania (ważona IDF)
występuje we fragmencie. Wynik BM25 nie ma skali bezwzględnej, więc o tym, czy
fragment w ogóle odpowiada na pytanie, decyduje łącznie próg wyniku i próg pokrycia.
"""
from __future__ import annotations

import math
from collections import Counter
from collections.abc import Iterable, Sequence
from dataclasses import dataclass

from .text import analyze, query_concepts, query_hints


@dataclass(frozen=True)
class Passage:
    """Fragment dokumentu w indeksie (niezależny od bazy danych)."""

    chunk_id: str
    document_id: int
    doc_key: str
    title: str
    version: str
    effective_from: str
    section_path: tuple[str, ...]
    text: str

    @property
    def path_label(self) -> str:
        return " › ".join(self.section_path)

    @property
    def section(self) -> str:
        """Ostatni element ścieżki (zwykle paragraf), a gdy go brak — tytuł."""
        return self.section_path[-1] if len(self.section_path) > 1 else self.title


@dataclass(frozen=True)
class Hit:
    passage: Passage
    score: float
    coverage: float
    matched: tuple[str, ...]


class BM25:
    """Indeks BM25 nad listą dokumentów-tokenów."""

    def __init__(self, documents: Sequence[Sequence[str]], k1: float = 1.2, b: float = 0.6) -> None:
        self.k1 = k1
        self.b = b
        self.tf: list[Counter[str]] = [Counter(doc) for doc in documents]
        self.lengths = [len(doc) for doc in documents]
        self.n = len(documents)
        self.avgdl = (sum(self.lengths) / self.n) if self.n else 0.0
        self.df: Counter[str] = Counter()
        for tf in self.tf:
            self.df.update(tf.keys())

    def idf(self, term: str) -> float:
        """IDF z wygładzeniem (zawsze dodatnie); termin spoza słownika dostaje maksimum."""
        df = self.df.get(term, 0)
        return math.log(1.0 + (self.n - df + 0.5) / (df + 0.5))

    def score(self, index: int, terms: Iterable[str]) -> float:
        tf = self.tf[index]
        length_norm = 1.0 - self.b + self.b * (self.lengths[index] / self.avgdl if self.avgdl else 0.0)
        total = 0.0
        for term in terms:
            freq = tf.get(term, 0)
            if freq:
                total += self.idf(term) * freq * (self.k1 + 1.0) / (freq + self.k1 * length_norm)
        return total


class SearchEngine:
    """Wyszukiwarka fragmentów: BM25 + pokrycie pojęć z pytania."""

    # Nagłówek sekcji opisuje jej temat zwięźlej niż treść, więc liczy się podwójnie.
    HEADING_WEIGHT = 2

    def __init__(self, passages: Sequence[Passage]) -> None:
        self.passages = list(passages)
        self._tokens = [self._passage_tokens(p) for p in self.passages]
        self._sets = [set(tokens) for tokens in self._tokens]
        self.bm25 = BM25(self._tokens)

    @classmethod
    def _passage_tokens(cls, passage: Passage) -> list[str]:
        tokens = analyze(passage.title)
        for heading in passage.section_path[1:-1]:
            tokens += analyze(heading)
        if len(passage.section_path) > 1:
            tokens += analyze(passage.section_path[-1]) * cls.HEADING_WEIGHT
        return tokens + analyze(passage.text)

    def concept_weight(self, concept: frozenset[str]) -> float:
        return max(self.bm25.idf(term) for term in concept)

    def search(self, query: str, k: int = 5, doc_keys: Iterable[str] | None = None) -> list[Hit]:
        """Najlepsze fragmenty dla pytania (tylko z wynikiem > 0)."""
        concepts = query_concepts(query)
        if not concepts or not self.passages:
            return []
        terms = sorted({term for concept in concepts for term in concept} | set(query_hints(query)))
        weights = [self.concept_weight(c) for c in concepts]
        total_weight = sum(weights) or 1.0
        allowed = set(doc_keys) if doc_keys is not None else None
        hits: list[Hit] = []
        for i, passage in enumerate(self.passages):
            if allowed is not None and passage.doc_key not in allowed:
                continue
            score = self.bm25.score(i, terms)
            if score <= 0:
                continue
            present = self._sets[i]
            matched_weight = 0.0
            matched: list[str] = []
            for concept, weight in zip(concepts, weights, strict=True):
                hit_terms = concept & present
                if hit_terms:
                    matched_weight += weight
                    matched.extend(sorted(hit_terms))
            hits.append(Hit(passage, score, matched_weight / total_weight, tuple(matched)))
        # Ranking łączy BM25 z pokryciem: fragment z kompletem pojęć z pytania wygrywa z takim,
        # który wiele razy powtarza tylko część z nich.
        hits.sort(key=lambda h: (-h.score * (0.5 + 0.5 * h.coverage), -h.coverage, h.passage.chunk_id))
        return hits[:k]


def is_relevant(hit: Hit, min_score: float, min_coverage: float) -> bool:
    return hit.score >= min_score and hit.coverage >= min_coverage
