"""Narzędzia tekstowe: podział na zdania, weryfikacja cytatów, maskowanie danych osobowych."""
from __future__ import annotations

import bisect
import re
from dataclasses import dataclass, field

# Skróty, po których kropka nie kończy zdania.
ABBREVIATIONS = frozenset(
    "np ds ul tys min max maks mies godz ok tj tzw itd itp r nr m.in dr inż mgr prof al pl os ew wg jw tel "
    "św sp kat pkt str art ust lit poz dz u godz. ang j wyż ww zob przyp tzn cdn płk gen hab lic red".split()
)
_BULLET = re.compile(r"^[ \t]*(?:[-*•–·▪◦✓✔]|\d+[.)])[ \t]+")
_WS = re.compile(r"\s+")
# Ujednolicenie typograficzne 1:1 (długość tekstu się nie zmienia, więc offsety są zachowane).
_TYPO = str.maketrans({"„": '"', "”": '"', "“": '"', "«": '"', "»": '"', "‘": "'", "’": "'", "–": "-", "—": "-",
                       " ": " "})


def sentence_spans(text: str) -> list[tuple[int, int]]:
    """Zakresy zdań: granice to nowa linia oraz . ! ? (z pominięciem skrótów i liczb dziesiętnych)."""
    spans: list[tuple[int, int]] = []
    pos = 0
    for line in text.splitlines(keepends=True):
        line_start, content = pos, line.rstrip("\r\n")
        pos += len(line)
        start = 0
        for i, ch in enumerate(content):
            if ch not in ".!?":
                continue
            nxt = content[i + 1] if i + 1 < len(content) else ""
            if nxt and not nxt.isspace():
                continue
            if ch == ".":
                word = re.search(r"([\w.]+)$", content[start:i])
                if word and (word.group(1).lower() in ABBREVIATIONS or word.group(1).isdigit()):
                    continue
            _add_span(spans, content, line_start, start, i + 1)
            start = i + 1
        _add_span(spans, content, line_start, start, len(content))
    return spans


def _add_span(spans: list[tuple[int, int]], content: str, line_start: int, start: int, end: int) -> None:
    seg = content[start:end]
    stripped = seg.strip()
    if not stripped:
        return
    lead = len(seg) - len(seg.lstrip())
    spans.append((line_start + start + lead, line_start + start + lead + len(stripped)))


class SentenceIndex:
    """Szybkie wyszukiwanie zdania zawierającego daną pozycję."""

    def __init__(self, text: str) -> None:
        self.text = text
        self.spans = sentence_spans(text)
        self._starts = [s for s, _ in self.spans]

    def at(self, pos: int) -> tuple[int, int]:
        i = bisect.bisect_right(self._starts, pos) - 1
        if i >= 0 and self.spans[i][0] <= pos < max(self.spans[i][1], self.spans[i][0] + 1):
            return self.spans[i]
        line_start = self.text.rfind("\n", 0, pos) + 1
        line_end = self.text.find("\n", pos)
        return line_start, len(self.text) if line_end == -1 else line_end


def line_bounds(text: str, pos: int) -> tuple[int, int]:
    start = text.rfind("\n", 0, pos) + 1
    end = text.find("\n", pos)
    return start, len(text) if end == -1 else end


def is_bullet(line: str) -> bool:
    return bool(_BULLET.match(line))


def strip_bullet(line: str) -> str:
    return _BULLET.sub("", line, count=1)


def count_words(text: str) -> int:
    return len(re.findall(r"\w+", text))


def normalize_ws(text: str) -> str:
    return _WS.sub(" ", text.translate(_TYPO)).strip()


def _normalized_with_map(text: str) -> tuple[str, list[int]]:
    """Tekst po normalizacji białych znaków + mapa: indeks w znormalizowanym → indeks w oryginale."""
    out: list[str] = []
    index: list[int] = []
    prev_space = True
    for i, ch in enumerate(text.translate(_TYPO)):
        if ch.isspace():
            if not prev_space:
                out.append(" ")
                index.append(i)
            prev_space = True
        else:
            out.append(ch)
            index.append(i)
            prev_space = False
    if out and out[-1] == " ":
        out.pop()
        index.pop()
    return "".join(out), index


_QUOTE_TRIM = " \t\n\"'„”“«»…."


def locate_quote(source: str, quote: str, limit: int = 20) -> list[tuple[int, int]]:
    """Zakresy [start, end) w `source`, w których występuje `quote` (po normalizacji białych znaków).

    Najpierw dopasowanie dokładne, potem bez rozróżniania wielkości liter. Pusta lista = cytatu nie ma.
    """
    needle = normalize_ws(quote).strip(_QUOTE_TRIM)
    if len(needle) < 3:
        return []
    hay, index = _normalized_with_map(source)
    spans = _find_all(hay, needle, index, limit)
    if not spans:
        low_hay, low_needle = hay.lower(), needle.lower()
        if len(low_hay) == len(hay) and len(low_needle) == len(needle):
            spans = _find_all(low_hay, low_needle, index, limit)
    return spans


def _find_all(hay: str, needle: str, index: list[int], limit: int) -> list[tuple[int, int]]:
    spans: list[tuple[int, int]] = []
    start = hay.find(needle)
    while start != -1 and len(spans) < limit:
        end = start + len(needle)
        spans.append((index[start], index[end - 1] + 1))
        start = hay.find(needle, start + 1)
    return spans


def contains_quote(source: str, quote: str) -> bool:
    return bool(locate_quote(source, quote, limit=1))


# --- Maskowanie danych osobowych przed wysłaniem do modelu ------------------------------------
_PII_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("ZNACZNIK", re.compile(r"</?\s*(?:ogloszenie|ogłoszenie|system|instructions?)\b[^>]*>", re.IGNORECASE)),
    ("EMAIL", re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")),
    ("KONTO", re.compile(r"\b(?:PL)?\d{2}(?:[ ]?\d{4}){6}\b")),
    ("PESEL", re.compile(r"(?<!\d)\d{11}(?!\d)")),
    (
        "TELEFON",
        re.compile(
            r"(?<![\d\w])(?:\+48[\s-]?)?(?:\d{3}[\s-]\d{3}[\s-]\d{3}|\d{9}|\(?\d{2}\)?[\s-]\d{3}[\s-]\d{2}[\s-]\d{2})"
            r"(?![\d\w])"
        ),
    ),
)


@dataclass
class MaskedText:
    """Tekst z danymi osobowymi zastąpionymi znacznikami [EMAIL_1] itd. oraz mapą offsetów."""

    original: str
    masked: str
    placeholders: dict[str, str] = field(default_factory=dict)
    # (start_w_masked, end_w_masked, start_w_oryginale, end_w_oryginale) dla każdego znacznika
    segments: list[tuple[int, int, int, int]] = field(default_factory=list)

    def to_original(self, start: int, end: int) -> tuple[int, int]:
        return self._map(start, is_end=False), self._map(end, is_end=True)

    def _map(self, pos: int, *, is_end: bool) -> int:
        shift = 0
        for m_start, m_end, o_start, o_end in self.segments:
            if is_end:
                if pos <= m_start:
                    break
                if pos <= m_end:
                    return o_end
            else:
                if pos < m_start:
                    break
                if pos < m_end:
                    return o_start
            shift += (o_end - o_start) - (m_end - m_start)
        return pos + shift

    def unmask(self, text: str) -> str:
        for token, value in self.placeholders.items():
            text = text.replace(token, value)
        return text


def mask_pii(text: str) -> MaskedText:
    """Zastępuje e-maile, telefony, PESEL i numery kont znacznikami; zwraca mapę do odtworzenia."""
    hits: list[tuple[int, int, str]] = []
    for kind, pattern in _PII_PATTERNS:
        for m in pattern.finditer(text):
            if any(m.start() < e and s < m.end() for s, e, _ in hits):
                continue
            hits.append((m.start(), m.end(), kind))
    hits.sort()
    counters: dict[str, int] = {}
    seen: dict[tuple[str, str], str] = {}
    out: list[str] = []
    segments: list[tuple[int, int, int, int]] = []
    placeholders: dict[str, str] = {}
    cursor = 0
    length = 0
    for start, end, kind in hits:
        out.append(text[cursor:start])
        length += start - cursor
        value = text[start:end]
        token = seen.get((kind, value))
        if token is None:
            counters[kind] = counters.get(kind, 0) + 1
            token = f"[{kind}_{counters[kind]}]"
            seen[(kind, value)] = token
            if kind != "ZNACZNIK":
                placeholders[token] = value
        segments.append((length, length + len(token), start, end))
        out.append(token)
        length += len(token)
        cursor = end
    out.append(text[cursor:])
    return MaskedText(original=text, masked="".join(out), placeholders=placeholders, segments=segments)
