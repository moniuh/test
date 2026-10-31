"""Narzędzia tekstowe: normalizacja, tokenizacja z prostą fleksją polską, zdania, weryfikacja cytatów."""
from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from functools import lru_cache

_FOLD = str.maketrans("ąćęłńóśźż", "acelnoszz")
_TYPO = str.maketrans(
    {
        "‘": "'", "’": "'", "‚": "'", "‛": "'",
        "“": '"', "”": '"', "„": '"', "«": '"', "»": '"',
        "–": "-", "—": "-", "−": "-", "‐": "-", "‑": "-",
        "‹": "<", "›": ">",
        " ": " ", " ": " ", " ": " ",
    }
)
_ZERO_WIDTH = dict.fromkeys(map(ord, "​‌‍⁠﻿­"), None)

# Końcówki fleksyjne (po złożeniu znaków diakrytycznych), od najdłuższych.
_SUFFIXES = sorted(
    {
        "iego", "iemu", "ach", "ami", "ego", "emu", "iej", "iem", "ich", "imi", "ych", "ymi",
        "om", "ow", "em", "ie", "ia", "iu", "im", "ym", "ej",
        "a", "e", "i", "o", "u", "y",
    },
    key=len,
    reverse=True,
)
# Oboczności spółgłoskowe na końcu tematu (Dockerze → docker, obsłudze → obsług, Polsce → polsk).
_ALTERNATIONS = (("rz", "r"), ("dz", "g"), ("sc", "sk"), ("cz", "k"))

TOKEN_RE = re.compile(r"[0-9A-Za-zÀ-ÖØ-öø-ž+#]+(?:[.\-'][0-9A-Za-zÀ-ÖØ-öø-ž+#]+)*")


def fold(text: str) -> str:
    """Małe litery bez polskich znaków — do porównań odpornych na brak diakrytyków."""
    return text.lower().translate(_FOLD)


def strip_invisible(text: str) -> str:
    return text.translate(_ZERO_WIDTH)


@lru_cache(maxsize=50_000)
def stem_candidates(token: str) -> frozenset[str]:
    """Zbiór możliwych tematów słowa. Dwa słowa pasują, gdy ich zbiory mają część wspólną.

    Krótkie tokeny (≤ 3 znaki, np. SQL, ZUS, C#) porównujemy dokładnie. Dzięki zbiorom
    „Java” i „Javie” pasują do siebie, a „Java” i „JavaScript” — nie.
    """
    word = fold(token)
    if len(word) <= 3:
        return frozenset({word})
    out = {word}
    for suffix in _SUFFIXES:
        if word.endswith(suffix) and len(word) - len(suffix) >= 3:
            out.add(word[: -len(suffix)])
    for stem in list(out):
        for src, dst in _ALTERNATIONS:
            if stem.endswith(src) and len(stem) - len(src) + len(dst) >= 3:
                out.add(stem[: -len(src)] + dst)
    return frozenset(out)


@dataclass(frozen=True)
class Token:
    text: str
    start: int
    end: int

    @property
    def stems(self) -> frozenset[str]:
        return stem_candidates(self.text)


def tokenize(text: str, offset: int = 0) -> list[Token]:
    return [Token(m.group(0), m.start() + offset, m.end() + offset) for m in TOKEN_RE.finditer(text)]


def _short_form(word: str) -> str | None:
    """Krótkie słowa (≤ 3 znaki, np. ZUS, SQL, C#) porównujemy dokładnie; „ZUS-u” → „zus”."""
    word = fold(word)
    if len(word) <= 3:
        return word
    head, sep, tail = word.partition("-")
    if sep and len(head) <= 4 and len(tail) <= 3 and tail.isalpha():
        return head
    return None


def tokens_match(a: Token | str, b: Token | str) -> bool:
    ta = a.text if isinstance(a, Token) else a
    tb = b.text if isinstance(b, Token) else b
    short_a, short_b = _short_form(ta), _short_form(tb)
    if short_a is not None or short_b is not None:
        return (short_a or fold(ta)) == (short_b or fold(tb))
    return not stem_candidates(ta).isdisjoint(stem_candidates(tb))


def find_phrase(term: str, tokens: list[Token], max_gap: int = 1) -> tuple[int, int] | None:
    """Szuka frazy (z fleksją) w tokenach; między słowami frazy dopuszcza `max_gap` wtrąconych słów.

    Zwraca indeksy (pierwszy, ostatni) tokenu dopasowania albo None.
    """
    term_tokens = tokenize(term)
    if not term_tokens:
        return None
    for i, tok in enumerate(tokens):
        if not tokens_match(term_tokens[0], tok):
            continue
        pos, ok = i, True
        for t in term_tokens[1:]:
            for j in range(pos + 1, min(pos + 2 + max_gap, len(tokens))):
                if tokens_match(t, tokens[j]):
                    pos = j
                    break
            else:
                ok = False
                break
        if ok:
            return i, pos
    return None


@dataclass(frozen=True)
class Sentence:
    text: str
    start: int
    end: int


_SENT_SPLIT = re.compile(r"(?<=[.!?])\s+(?=[A-ZĄĆĘŁŃÓŚŹŻ0-9\[\"„(])")
_BULLET = re.compile(r"^(?:[-•*–·▪●]|\d{1,2}[.)])\s+")


def split_sentences(text: str) -> list[Sentence]:
    """Dzieli tekst na zdania/linie (CV to głównie wypunktowania), zachowując offsety.

    Znacznik wypunktowania („- ”, „• ”, „1. ”) jest pomijany, więc dowód zaczyna się od treści.
    """
    out: list[Sentence] = []
    pos = 0
    for line in text.splitlines(keepends=True):
        line_start = pos
        pos += len(line)
        body = line.rstrip("\r\n")
        cursor = 0
        for m in list(_SENT_SPLIT.finditer(body)) + [None]:
            end = m.start() if m else len(body)
            chunk = body[cursor:end]
            lead = len(chunk) - len(chunk.lstrip())
            stripped = chunk.strip()
            bullet = _BULLET.match(stripped)
            if bullet:  # znacznik wypunktowania nie jest częścią dowodu
                lead += bullet.end()
                stripped = stripped[bullet.end():]
            if stripped:
                s = line_start + cursor + lead
                out.append(Sentence(stripped, s, s + len(stripped)))
            if m:
                cursor = m.end()
    return out


def _normalize_with_map(text: str) -> tuple[str, list[int]]:
    """Normalizuje (typografia, wielkość liter, białe znaki) i zwraca mapę indeksów do oryginału."""
    chars: list[str] = []
    index: list[int] = []
    prev_space = True
    for i, ch in enumerate(text):
        ch = unicodedata.normalize("NFKC", ch).translate(_TYPO).translate(_ZERO_WIDTH)
        if not ch:
            continue
        for c in ch:
            if c.isspace():
                if prev_space:
                    continue
                chars.append(" ")
                index.append(i)
                prev_space = True
            else:
                chars.append(c.lower())
                index.append(i)
                prev_space = False
    while chars and chars[-1] == " ":
        chars.pop()
        index.pop()
    return "".join(chars), index


def normalize_for_match(text: str) -> str:
    return _normalize_with_map(text)[0].strip()


_PLACEHOLDER_RE = re.compile(r"\[[A-ZĄĆĘŁŃÓŚŹŻ \-/]+\]")
_ELLIPSIS_RE = re.compile(r"\s*(?:\.\.\.|…|\[\.\.\.\]|\(\.\.\.\))\s*")


def find_quote(source: str, quote: str, min_chars: int = 4) -> tuple[int, int] | None:
    """Weryfikuje, czy cytat jest podciągiem źródła po normalizacji białych znaków i typografii.

    Wielokropek dzieli cytat na części, które muszą wystąpić w źródle w tej kolejności.
    Zwraca (start, koniec) w oryginalnym źródle albo None, gdy cytatu nie ma (zmyślony dowód).
    """
    if not quote:
        return None
    meaningful = _PLACEHOLDER_RE.sub("", quote)
    if len(re.sub(r"\W", "", meaningful)) < min_chars:
        return None
    norm_src, index = _normalize_with_map(source)
    parts = [normalize_for_match(p) for p in _ELLIPSIS_RE.split(quote)]
    parts = [p for p in parts if p]
    if not parts:
        return None
    cursor = 0
    first_start: int | None = None
    last_end = 0
    for part in parts:
        found = norm_src.find(part, cursor)
        if found < 0:
            return None
        if first_start is None:
            first_start = found
        last_end = found + len(part)
        cursor = last_end
    return index[first_start], index[last_end - 1] + 1
