"""Narzędzia tekstowe: normalizacja polskiego tekstu, lekki stemming i dopasowanie słów kluczowych.

Polska fleksja (dziecko/dzieci/dziećmi, reklamacja/reklamacyjny) jest obsługiwana przez
usunięcie znaków diakrytycznych, odcięcie typowych końcówek i porównanie wspólnego prefiksu.
To celowo prosta heurystyka — przewidywalna i testowalna, bez zewnętrznych słowników.
"""
from __future__ import annotations

import re
import unicodedata

_WORD_RE = re.compile(r"[0-9a-z]+")
_RAW_WORD_RE = re.compile(r"\w+", re.UNICODE)

# Końcówki fleksyjne i słowotwórcze po usunięciu diakrytyków, od najdłuższych.
_SUFFIXES = sorted(
    {
        "owego", "owemu", "owych", "owymi", "owej", "owym", "owie", "owa", "owe", "owi", "owy", "owo",
        "iego", "iemu", "ich", "imi", "iej", "ym", "ymi", "ych", "ego", "emu", "ej",
        "ami", "ach", "iami", "iach", "om", "iom", "ow", "iow",
        "iem", "em", "ie", "ia", "ii", "io", "iu",
        "a", "e", "i", "o", "u", "y",
    },
    key=len,
    reverse=True,
)

_MIN_STEM = 3


def strip_diacritics(text: str) -> str:
    """Usuwa polskie znaki diakrytyczne (ł nie rozkłada się w NFKD, więc mapujemy ręcznie)."""
    text = text.replace("ł", "l").replace("Ł", "L")
    decomposed = unicodedata.normalize("NFKD", text)
    return "".join(ch for ch in decomposed if not unicodedata.combining(ch))


def normalize(text: str) -> str:
    """Małe litery, bez diakrytyków, tylko litery i cyfry rozdzielone pojedynczą spacją."""
    flat = strip_diacritics(text.lower())
    return " ".join(_WORD_RE.findall(flat))


def fold(text: str) -> str:
    """Normalizacja do dopasowań: ę i ą zrównane, by objąć oboczności (błąd/błędy, mąż/męża)."""
    return normalize(text.replace("ę", "ą").replace("Ę", "Ą"))


def tokens(text: str) -> list[str]:
    """Tokeny do dopasowania słów kluczowych (z zrównaniem ę/ą)."""
    return fold(text).split()


def plural(n: int, one: str, few: str, many: str) -> str:
    """Polska odmiana liczebnika: 1 pytanie, 2 pytania, 5 pytań, 22 pytania, 12 pytań."""
    if n == 1:
        return f"{n} {one}"
    if n % 10 in (2, 3, 4) and n % 100 not in (12, 13, 14):
        return f"{n} {few}"
    return f"{n} {many}"


def word_count(text: str) -> int:
    """Liczba słów w oryginalnym tekście (do proporcji mówienia)."""
    return len(_RAW_WORD_RE.findall(text))


def stem(word: str) -> str:
    """Odcina jedną najdłuższą pasującą końcówkę, zostawiając rdzeń o długości co najmniej 3."""
    word = normalize(word).replace(" ", "")
    for suffix in _SUFFIXES:
        if word.endswith(suffix) and len(word) - len(suffix) >= _MIN_STEM:
            return word[: -len(suffix)]
    return word


def _common_prefix(a: str, b: str) -> int:
    n = 0
    for x, y in zip(a, b):
        if x != y:
            break
        n += 1
    return n


def words_match(token: str, keyword: str) -> bool:
    """Czy słowo z tekstu to forma fleksyjna/pochodna słowa kluczowego."""
    t, k = fold(token), fold(keyword)
    if not t or not k:
        return False
    if t == k:
        return True
    if len(k) <= 4:
        # Krótkie słowa (kod, git, bhp, zysk): tylko ta sama forma lub krótka końcówka.
        return t.startswith(k) and len(t) - len(k) <= 3
    st, sk = stem(t), stem(k)
    if st == sk:
        return True
    prefix = _common_prefix(st, sk)
    return prefix >= 5 and prefix >= min(len(st), len(sk)) - 1


def phrase_in(text_tokens: list[str], keyword: str) -> bool:
    """Słowo kluczowe (także wielowyrazowe) występuje w tokenach tekstu — każdy człon osobno."""
    parts = fold(keyword).split()
    if not parts:
        return False
    return all(any(words_match(tok, part) for tok in text_tokens) for part in parts)


def count_keyword_hits(text: str | list[str], keywords: list[str]) -> int:
    toks = tokens(text) if isinstance(text, str) else text
    return sum(1 for kw in keywords if phrase_in(toks, kw))


def collapse_ws(text: str) -> str:
    return " ".join(text.split())


_QUOTE_CHARS = "\"'„”“«»‚‘’ "


def is_verified_quote(quote: str, source: str) -> bool:
    """Cytat jest prawdziwy, gdy po normalizacji białych znaków jest podciągiem źródła."""
    q = collapse_ws(quote).strip(_QUOTE_CHARS)
    if len(q) < 3:
        return False
    return q in collapse_ws(source)
