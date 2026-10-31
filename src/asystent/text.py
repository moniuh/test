"""Normalizacja tekstu po polsku na potrzeby wyszukiwania.

Potok: małe litery → zdjęcie ogonków (ą→a, ł→l…) → tokeny → odrzucenie słów
nieistotnych → lekki stemming (obcinanie końcówek fleksyjnych) → tokeny synonimów.
Stemming jest celowo prosty: ważniejsza jest spójność (te same formy dają ten sam
rdzeń w dokumencie i w pytaniu) niż poprawność językoznawcza.
"""
from __future__ import annotations

import re
import unicodedata
from collections.abc import Iterable
from dataclasses import dataclass

_FOLD = str.maketrans("ąćęłńóśźż", "acelnoszz")
_TOKEN_RE = re.compile(r"[a-z0-9]+")
# Łączymy wyrazy z łącznikiem („e-ZLA” → „ezla”, „home-office” → „homeoffice”).
_HYPHEN_RE = re.compile(r"(?<=[a-z0-9])[-‐‑](?=[a-z0-9])")
_WORD_RE = re.compile(r"[0-9A-Za-zĄĆĘŁŃÓŚŹŻąćęłńóśźż]+(?:[-‐‑][0-9A-Za-zĄĆĘŁŃÓŚŹŻąćęłńóśźż]+)*")

SYNONYM_PREFIX = "="

STOPWORDS = frozenset(
    """
    a aby ale albo ani az bo by byc byl byla bylo bym bys chce chcialbym chcialabym co czy czym
    dla do gdy gdzie i ich ile im ja jak jaka jaki jakie jakiej jakim jakich je jego jej jesli jest
    jestem juz ku lub ma mam mamy masz mi mnie moge mogl mogla mozna moj moja moje mojego mojej
    mu musze my na nad nam nas nie niz o od oraz po pod prosze przez przy sa sie sobie ta tak takze
    te tego tej ten to tu ty u w we wiec z za ze zeby kiedy ktory ktora ktore ktorych tez jeszcze
    trzeba czyli jako jakis jakas jakies jakos czego czemu the
    """.split()
)

# Końcówki fleksyjne (bez ogonków), sprawdzane od najdłuższej.
_SUFFIXES = tuple(
    sorted(
        {
            "owaniami", "owaniach", "owaniem", "owania", "owaniu", "owanie",
            "osciami", "osciach", "osciom", "oscia", "osci", "osc",
            "owymi", "owych", "owego", "owemu", "owej", "owym", "owa", "owe", "owy", "owi",
            "anych", "anymi", "anej", "anego", "any", "ana", "ane",
            "owujesz", "owuje", "owuja", "ujesz", "ujemy", "ujecie", "uja", "uje", "owac", "ywac", "iwac",
            "owo", "ami", "ach", "ych", "ymi", "imi", "ego", "emu", "iem", "ich", "iej", "ie", "ia", "iu", "ii",
            "ej", "ow", "om", "em", "ym", "im", "ac", "ic", "yc", "ec", "esz", "emy",
            "y", "i", "a", "e", "u", "o",
        },
        key=len,
        reverse=True,
    )
)
_MIN_STEM = 3

# Formy nieregularne, których obcinanie końcówek nie sprowadzi do wspólnego rdzenia.
_IRREGULAR = {
    "dzien": "dni", "dnia": "dni", "dniu": "dni", "dniem": "dni", "dni": "dni", "dniach": "dni",
    "dniami": "dni", "dniom": "dni",
    "dzieci": "dzieck", "dziecko": "dzieck", "dziecka": "dzieck", "dzieckiem": "dzieck",
    "dzieciom": "dzieck", "dziecmi": "dzieck", "dzieciach": "dzieck",
    "lat": "rok", "lata": "rok", "latach": "rok", "latami": "rok",
    "ludzie": "osob", "ludzi": "osob",
}

# Grupy synonimów: każda fraza (po normalizacji) dokłada token kanoniczny „=nazwa”.
SYNONYM_GROUPS: dict[str, tuple[str, ...]] = {
    "urlop": ("urlop", "wypoczynek", "urlop wypoczynkowy", "wakacje"),
    "l4": (
        "L4", "zwolnienie lekarskie", "zwolnienie chorobowe", "e-ZLA", "zaświadczenie lekarskie",
        "niezdolność do pracy", "chorobowe",
    ),
    "praca_zdalna": ("home office", "homeoffice", "praca zdalna", "zdalnie", "praca z domu", "hybrydowy"),
    "wynagrodzenie": (
        "wypłata", "wypłacać", "wynagrodzenie", "pensja", "pobory", "płaca", "płatny", "pasek płacowy",
    ),
    "nadgodziny": ("nadgodziny", "praca w godzinach nadliczbowych", "godziny nadliczbowe", "nadliczbowe"),
    "zgloszenie": (
        "zgłosić", "zgłaszać", "zgłoszenie", "zawiadomić", "zawiadamiać", "zawiadamia", "zawiadomienie",
    ),
    "mobbing": ("mobbing", "nękanie", "zastraszanie"),
    "sygnalista": ("sygnalista", "whistleblower", "zgłoszenie wewnętrzne", "naruszenie prawa"),
    "benefit": ("benefit", "świadczenie dodatkowe", "dodatki pozapłacowe", "pakiet socjalny"),
    "sprzet": ("sprzęt", "laptop", "komputer"),
    "opieka_medyczna": ("opieka medyczna", "pakiet medyczny", "abonament medyczny", "prywatna przychodnia"),
    "szkolenie": ("szkolenie", "kurs", "konferencja", "certyfikat"),
    "zagranica": ("zagranica", "zagraniczny", "granica"),
    "podwyzka": ("podwyżka", "zmiana wynagrodzenia", "przegląd wynagrodzeń", "podwyższenie wynagrodzenia"),
    "noc": ("noc", "nocny"),
    # Grupy docelowe dla słów pytających (patrz QUESTION_HINTS).
    "ilosc": ("wymiar", "liczba", "wysokość", "ilość", "limit"),
    "termin": ("termin", "najpóźniej"),
}

# Słowa pytające są na liście nieistotnych, ale niosą informację o typie odpowiedzi:
# „ile” szuka wymiaru/liczby, „kiedy” — terminu. Dokładamy wtedy token kanoniczny
# do zapytania BM25 (bez wpływu na pokrycie pojęć).
QUESTION_HINTS: dict[str, str] = {"ile": "=ilosc", "ilu": "=ilosc", "kiedy": "=termin"}


def fold(text: str) -> str:
    """Małe litery i zdjęte polskie znaki diakrytyczne."""
    return unicodedata.normalize("NFC", text).lower().translate(_FOLD)


def tokenize(text: str) -> list[str]:
    """Tokeny po złożeniu znaków (bez stemmingu i bez odrzucania słów)."""
    return _TOKEN_RE.findall(_HYPHEN_RE.sub("", fold(text)))


def stem(token: str) -> str:
    """Lekki stemming: obcina najdłuższą pasującą końcówkę, zostawiając rdzeń ≥ 3 znaki."""
    if token in _IRREGULAR:
        return _IRREGULAR[token]
    if len(token) <= 3 or not token.isalpha():
        return token
    original = token
    for suffix in _SUFFIXES:
        if token.endswith(suffix) and len(token) - len(suffix) >= _MIN_STEM:
            token = token[: -len(suffix)]
            break
    # Miejscownik z obocznością: budżecie/budżet, kontakcie/kontakt, zakładzie/zakład.
    if original.endswith("cie") and token.endswith("c"):
        token = token[:-1] + "t"
    elif original.endswith("dzie") and token.endswith("dz"):
        token = token[:-1]
    # „e” ruchome: wypoczynek/wypoczynku, dodatek/dodatku, wniosek/wniosku.
    if token.endswith("ek") and len(token) >= 5:
        token = token[:-2] + "k"
    # Oboczność ść/st: sygnaliści/sygnalista, specjaliści/specjalista.
    if token.endswith("sc") and len(token) >= 5:
        token = token[:-2] + "st"
    return token


def stems(text: str) -> list[str]:
    """Rdzenie słów istotnych (bez słów nieistotnych)."""
    return [stem(t) for t in tokenize(text) if t not in STOPWORDS]


@dataclass(frozen=True)
class _Phrase:
    tokens: tuple[str, ...]
    canonical: str


def _build_phrases() -> dict[str, list[_Phrase]]:
    index: dict[str, list[_Phrase]] = {}
    for name, members in SYNONYM_GROUPS.items():
        for member in members:
            seq = tuple(stems(member))
            if seq:
                index.setdefault(seq[0], []).append(_Phrase(seq, SYNONYM_PREFIX + name))
    for phrases in index.values():
        phrases.sort(key=lambda p: len(p.tokens), reverse=True)
    return index


_PHRASES = _build_phrases()


def synonym_spans(seq: list[str]) -> list[tuple[int, int, str]]:
    """Wystąpienia fraz synonimów w ciągu rdzeni: (początek, koniec, token kanoniczny).

    Po dopasowaniu frazy jej dalsze pozycje nie dają drugi raz tego samego tokenu,
    więc „urlop wypoczynkowy” liczy się jako jedno wystąpienie grupy „urlop”.
    """
    spans: list[tuple[int, int, str]] = []
    blocked: dict[str, int] = {}
    for i, token in enumerate(seq):
        for phrase in _PHRASES.get(token, ()):
            n = len(phrase.tokens)
            if tuple(seq[i : i + n]) != phrase.tokens:
                continue
            if blocked.get(phrase.canonical, -1) > i:
                continue
            spans.append((i, i + n, phrase.canonical))
            blocked[phrase.canonical] = i + n
    return spans


def analyze(text: str) -> list[str]:
    """Tokeny do indeksu: rdzenie + tokeny kanoniczne synonimów."""
    seq = stems(text)
    return seq + [canonical for _, _, canonical in synonym_spans(seq)]


def query_concepts(text: str) -> list[frozenset[str]]:
    """Pojęcia z pytania: każdy rdzeń z alternatywami (tokenami kanonicznymi synonimów).

    Pojęcie uznaje się za pokryte przez fragment, gdy ten zawiera dowolną alternatywę —
    dzięki temu „L4” w pytaniu pokrywa „zwolnienie lekarskie” w dokumencie.
    """
    seq = stems(text)
    alternatives: list[set[str]] = [{t} for t in seq]
    for start, end, canonical in synonym_spans(seq):
        for pos in range(start, end):
            alternatives[pos].add(canonical)
    concepts: list[frozenset[str]] = []
    for alts in alternatives:
        concept = frozenset(alts)
        if concept not in concepts:
            concepts.append(concept)
    return concepts


def query_hints(text: str) -> list[str]:
    """Tokeny kanoniczne wynikające ze słów pytających („ile”, „kiedy”)."""
    hints: list[str] = []
    for token in tokenize(text):
        hint = QUESTION_HINTS.get(token)
        if hint and hint not in hints:
            hints.append(hint)
    return hints


def surface_words(text: str) -> Iterable[tuple[str, str]]:
    """Pary (rdzeń, forma z tekstu małymi literami) dla słów istotnych — do raportów."""
    for match in _WORD_RE.finditer(text):
        word = match.group(0).lower()
        folded = _HYPHEN_RE.sub("", fold(word))
        if folded in STOPWORDS or not folded:
            continue
        yield stem(folded), word


_WS_RE = re.compile(r"\s+")
_QUOTE_MAP = str.maketrans({"„": '"', "”": '"', "“": '"', "«": '"', "»": '"', "’": "'", "‘": "'",
                            "–": "-", "—": "-", "‑": "-", "‐": "-", " ": " "})


def collapse_ws(text: str) -> str:
    """Jednolite białe znaki (cytat do wyświetlenia zostaje dosłowny)."""
    return _WS_RE.sub(" ", text).strip()


def normalize_for_match(text: str) -> str:
    """Normalizacja do weryfikacji cytatów: jednolite białe znaki, cudzysłowy i myślniki."""
    return _WS_RE.sub(" ", text.translate(_QUOTE_MAP)).strip()
