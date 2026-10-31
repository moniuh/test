"""Wykrywanie prób prompt injection w CV (polecenia skierowane do systemu oceniającego).

Detekcja regułowa na tekście znormalizowanym (bez polskich znaków, bez znaków niewidocznych),
więc działa także dla „zignoruj poprzednie instrukcje” pisanego bez diakrytyków
lub przemieszanego znakami zerowej szerokości. Wynik to flaga dla rekrutera i lista fragmentów;
zdania z wykrytą próbą są wyłączane z oceny (nie mogą być „dowodem”).
"""
from __future__ import annotations

import re

from .models import InjectionFinding, InjectionReport
from .textutil import Sentence, split_sentences

_FOLD_MAP = str.maketrans("ąćęłńóśźżĄĆĘŁŃÓŚŹŻ", "acelnoszzACELNOSZZ")
_INVISIBLE = "​‌‍⁠﻿­"

# (identyfikator, etykieta dla rekrutera, wzorzec na tekście złożonym: małe litery, bez diakrytyków)
_RULES: list[tuple[str, str, re.Pattern[str]]] = [
    (
        "ignore_instructions_pl",
        "polecenie zignorowania instrukcji",
        re.compile(
            r"\b(?:zignoruj|ignoruj|pomin|zapomnij|odrzuc|nie\s+stosuj|nie\s+bierz\s+pod\s+uwage)(?:cie)?\b"
            r"\s+(?:\w+\s+){0,3}?(?:instrukcj|polecen|zasad|regul|wytyczn|prompt)\w*"
        ),
    ),
    (
        "ignore_instructions_en",
        "polecenie zignorowania instrukcji (EN)",
        re.compile(
            r"\b(?:ignore|disregard|forget|override)\s+(?:\w+\s+){0,3}?"
            r"(?:instructions?|prompts?|rules|guidelines|directions)\b"
        ),
    ),
    (
        "score_manipulation_pl",
        "żądanie najwyższej oceny",
        re.compile(
            r"\b(?:ocen|przyznaj|daj|wystaw|nadaj|przypisz)(?:cie)?\b\s+(?:\S+\s+){0,4}?"
            r"(?:najwyz\w*|maksymaln\w*|10\s*/\s*10|100\s*%|100\s*pkt|idealn\w*|najlepsz\w*)"
        ),
    ),
    (
        "score_manipulation_en",
        "żądanie najwyższej oceny (EN)",
        re.compile(
            r"\b(?:rate|score|rank|grade|give|assign)\s+(?:me|this\s+(?:cv|resume|candidate|applicant|profile)|"
            r"the\s+candidate|him|her|them)\b\s+(?:\S+\s+){0,3}?"
            r"(?:highest|maximum|max|10\s*/\s*10|100\s*%|top|perfect|best)\b"
        ),
    ),
    (
        "verdict_claim",
        "polecenie dla oceniającego o werdykcie",
        re.compile(
            r"\b(?:ten|ta|this)\s+(?:kandydat\w*|osoba|candidate|applicant)\s+(?:\w+\s+){0,2}?"
            r"(?:spelnia\s+wszystkie|meets\s+all|is\s+(?:a\s+)?perfect)"
            r"|\b(?:rekomenduj|polec|zapros|zarekomenduj)(?:cie)?\b\s+(?:\w+\s+){0,1}?"
            r"(?:tego\s+kandydat\w*|te\s+kandydatk\w*|mnie|mnie\s+na\s+rozmow\w*)\b"
            r"|\b(?:recommend|hire|shortlist|select)\s+(?:me|this\s+(?:candidate|applicant))\b"
        ),
    ),
    (
        "role_override",
        "próba zmiany roli modelu",
        re.compile(
            r"\b(?:jestes\s+teraz|od\s+teraz\s+jestes|you\s+are\s+now|udawaj|zachowuj\s+sie\s+jak)\b"
            r"|\b(?:system\s+prompt|prompt\s+systemow\w*|jako\s+model\s+jezykow\w*|"
            r"as\s+an?\s+(?:ai|language\s+model)|developer\s+mode)\b"
            r"|\b(?:(?:oto|twoje|nastepujace)\s+nowe\s+instrukcje|nowe\s+instrukcje\s*:|"
            r"(?:here\s+are\s+|your\s+)?new\s+instructions\s*:|your\s+new\s+instructions)"
        ),
    ),
    (
        "tag_escape",
        "próba wyjścia ze znaczników danych",
        re.compile(r"</?\s*(?:cv|system|instructions?|wymagania|wymaganie|stanowisko|user|assistant)\s*>"),
    ),
]


def _fold_with_map(text: str) -> tuple[str, list[int]]:
    out: list[str] = []
    index: list[int] = []
    prev_space = False
    for i, ch in enumerate(text):
        if ch in _INVISIBLE:
            continue
        if ch.isspace():
            if prev_space:
                continue
            out.append(" ")
            index.append(i)
            prev_space = True
            continue
        out.append(ch.translate(_FOLD_MAP).lower())
        index.append(i)
        prev_space = False
    return "".join(out), index


def detect_injection(text: str) -> InjectionReport:
    """Zwraca raport z flagą i fragmentami (offsety w oryginalnym tekście)."""
    folded, index = _fold_with_map(text)
    findings: list[InjectionFinding] = []
    for rule_id, label, pattern in _RULES:
        for m in pattern.finditer(folded):
            start, end = index[m.start()], index[m.end() - 1] + 1
            findings.append(
                InjectionFinding(rule=rule_id, label=label, fragment=text[start:end], start=start, end=end)
            )
    invisible = sum(text.count(c) for c in _INVISIBLE)
    if invisible >= 3:
        pos = next(i for i, c in enumerate(text) if c in _INVISIBLE)
        findings.append(
            InjectionFinding(
                rule="invisible_chars",
                label=f"ukryte znaki niewidoczne ({invisible})",
                fragment="",
                start=pos,
                end=pos,
            )
        )
    findings.sort(key=lambda f: f.start)
    return InjectionReport(flagged=bool(findings), findings=findings)


MAX_LINE_TO_DROP = 400


def suspicious_sentences(text: str, report: InjectionReport) -> list[Sentence]:
    """Fragmenty wyłączane z oceny: cała linia z próbą manipulacji (ukryty blok tekstu zwykle
    jest jedną linią), a w bardzo długich liniach — tylko zdanie z próbą."""
    spans = [(f.start, f.end) for f in report.findings if f.end > f.start]
    if not spans:
        return []
    out: list[Sentence] = []
    pos = 0
    for line in text.splitlines(keepends=True):
        start, pos = pos, pos + len(line)
        body = line.rstrip("\r\n")
        stripped = body.strip()
        if not stripped or not any(a < start + len(body) and b > start for a, b in spans):
            continue
        if len(stripped) <= MAX_LINE_TO_DROP:
            lead = len(body) - len(body.lstrip())
            out.append(Sentence(stripped, start + lead, start + lead + len(stripped)))
        else:
            for sent in split_sentences(body):
                s_start, s_end = start + sent.start, start + sent.end
                if any(a < s_end and b > s_start for a, b in spans):
                    out.append(Sentence(sent.text, s_start, s_end))
    return out


OMITTED_PLACEHOLDER = "[POMINIĘTO FRAGMENT: podejrzenie manipulacji oceną]"


def scoring_text(text: str, report: InjectionReport) -> str:
    """Tekst do oceny: zdania z próbą manipulacji zastąpione znacznikiem."""
    out = text
    for s in sorted(suspicious_sentences(text, report), key=lambda s: s.start, reverse=True):
        out = out[: s.start] + OMITTED_PLACEHOLDER + out[s.end :]
    return out
