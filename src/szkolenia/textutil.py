"""Narzędzia tekstowe: normalizacja, weryfikacja cytatów, podział na zdania, maskowanie danych."""
from __future__ import annotations

import re
from dataclasses import dataclass

_WS = re.compile(r"\s+")
_QUOTES = str.maketrans({
    "„": '"', "”": '"', "“": '"', "«": '"', "»": '"', "‚": "'", "’": "'", "‘": "'",
    "–": "-", "—": "-", "‑": "-", " ": " ",
})

# Skróty, po których kropka nie kończy zdania, nawet gdy dalej stoi wielka litera.
_NON_TERMINAL_ABBR = {
    "np", "m.in", "tzw", "tj", "dr", "mgr", "prof", "inż", "ul", "al", "św", "nr", "art",
    "ust", "pkt", "poz", "lit", "godz", "por", "zob", "ww", "jw", "wg", "ok", "ds", "kpt",
}


def normalize_ws(text: str) -> str:
    return _WS.sub(" ", text).strip()


def normalize_for_match(text: str) -> str:
    """Postać do porównań: ujednolicone cudzysłowy i myślniki, białe znaki, wielkość liter."""
    return normalize_ws(text.translate(_QUOTES)).casefold()


MIN_QUOTE_CHARS = 15


def verify_quote(quote: str, source: str) -> bool:
    """Cytat jest zweryfikowany, gdy po normalizacji jest podciągiem materiału i nie jest trywialny."""
    q = normalize_for_match(quote).strip(" .\"'")
    if len(q) < MIN_QUOTE_CHARS:
        return False
    return q in normalize_for_match(source)


@dataclass(frozen=True)
class Sentence:
    text: str
    start: int  # pozycja w materiale (pozwala zachować dosłowny cytat)


_BULLET = re.compile(r"^\s*(?:[-•*▪–]|\d{1,2}[.)])\s+")
_SPLIT_CANDIDATE = re.compile(r"[.!?…]+[\"”»)]?\s+(?=[\"„«(]?[A-ZĄĆĘŁŃÓŚŹŻ0-9])")


def _ends_with_non_terminal_abbr(chunk: str) -> bool:
    match = re.search(r"([\wąćęłńóśźżĄĆĘŁŃÓŚŹŻ.]+)\.\s*$", chunk)
    if not match:
        return False
    token = match.group(1).casefold()
    return token in _NON_TERMINAL_ABBR or bool(re.fullmatch(r"\d{1,2}", token))


def split_sentences(text: str) -> list[Sentence]:
    """Dzieli materiał na zdania (linie list, akapity, kropki z obsługą polskich skrótów)."""
    sentences: list[Sentence] = []
    offset = 0
    for line in text.splitlines(keepends=True):
        line_start = offset
        offset += len(line)
        body = line.rstrip("\n")
        bullet = _BULLET.match(body)
        cursor = bullet.end() if bullet else 0
        last = cursor
        for m in _SPLIT_CANDIDATE.finditer(body, cursor):
            piece = body[last:m.end()]
            if _ends_with_non_terminal_abbr(piece.rstrip()):
                continue
            _push(sentences, body, last, m.end(), line_start)
            last = m.end()
        _push(sentences, body, last, len(body), line_start)
    return sentences


def _push(out: list[Sentence], body: str, start: int, end: int, line_start: int) -> None:
    raw = body[start:end]
    stripped = raw.strip()
    if not stripped:
        return
    lead = len(raw) - len(raw.lstrip())
    out.append(Sentence(text=stripped, start=line_start + start + lead))


_EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
_PHONE = re.compile(r"(?<!\d)(?:\+48[\s-]?)?(?:\d{3}[\s-]?\d{3}[\s-]?\d{3})(?!\d)")
_PESEL = re.compile(r"(?<!\d)\d{11}(?!\d)")


def mask_pii(text: str) -> str:
    """Maskuje e-maile, telefony i numery PESEL przed wysłaniem tekstu do modelu."""
    text = _EMAIL.sub("[e-mail]", text)
    text = _PESEL.sub("[PESEL]", text)
    return _PHONE.sub("[telefon]", text)


_INJECTION = re.compile(
    r"(zignoruj|pomiń|zapomnij)\s+(wszystkie\s+|wcześniejsze\s+|poprzednie\s+)*(instrukcje|polecenia)"
    r"|ignore\s+(all\s+|previous\s+|prior\s+)*instructions"
    r"|system\s*prompt|prompt\s+systemow"
    r"|jesteś\s+teraz|you\s+are\s+now",
    re.IGNORECASE,
)


def detect_injection(text: str) -> bool:
    """Heurystyka: czy materiał zawiera próbę sterowania modelem (prompt injection)."""
    return bool(_INJECTION.search(text))


def polish_plural(n: int, one: str, few: str, many: str) -> str:
    """Odmiana rzeczownika przy liczebniku: 1 dzień, 2 dni, 5 dni; 1 pytanie, 3 pytania, 5 pytań."""
    n = abs(n)
    if n == 1:
        return one
    if n % 10 in (2, 3, 4) and n % 100 not in (12, 13, 14):
        return few
    return many
