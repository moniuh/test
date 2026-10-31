"""Neutralizacja najczęstszych polskich form rodzajowych w CV.

Cel: po anonimizacji oba warianty („pracowałam” i „pracowałem”) wyglądają tak samo
(„pracowałem/am”), więc forma gramatyczna nie zdradza płci, a dopasowanie słów kluczowych
działa jednakowo dla obu wersji nazw stanowisk („programistka” → „programista/ka”).

Świadomie wąski zakres (opisany w README jako ograniczenie):
- 1. os. lp. czasu przeszłego regularnych czasowników (-łam/-łem) i tryb przypuszczający (-łabym/-łbym),
- czasowniki na -ąć (podjęłam/podjąłem),
- wybrane przymiotniki z sekcji „O mnie” i popularne nazwy stanowisk (tylko mianownik).
Rzeczowniki w narzędniku na -łem („działem”, „materiałem”, „tytułem”) nie są zmieniane.
Formy nieregularne („poszłam/poszedłem”) i odmienione rzeczowniki zostają bez zmian.
"""
from __future__ import annotations

import re

_L = "A-Za-zĄĆĘŁŃÓŚŹŻąćęłńóśźż"

# Rzeczowniki (i imiona), których narzędnik kończy się na -ałem, a nie są czasownikami.
_NOUN_FORMS = tuple(
    s + "em"
    for s in (
        "dział", "materiał", "kapitał", "potencjał", "zapał", "kanał", "sygnał", "terminał", "ideał",
        "generał", "personał", "upał", "szał", "kwartał", "arsenał", "kryształ", "piedestał",
        "michał", "rafał",
    )
)

# Temat zakończony samogłoską a/e/i/y albo g/k (mogłam, piekłam) + łam/łem.
_PAST_RE = re.compile(rf"\b([{_L}]+?[aeiy]|[{_L}]+[gk])ł(am|em)\b(?!/)", re.IGNORECASE)
_COND_RE = re.compile(rf"\b([{_L}]+?[aeiy])ł(abym|bym)\b(?!/)", re.IGNORECASE)
_AC_RE = re.compile(rf"\b([{_L}]{{2,}}?)(ęłam|ąłem)\b(?!/)", re.IGNORECASE)

_ADJECTIVES = (
    "komunikatywn", "odpowiedzialn", "zmotywowan", "samodzieln", "dyspozycyjn", "zorganizowan",
    "sumienn", "kreatywn", "rzeteln", "uczciw", "punktualn", "ambitn", "systematyczn", "skrupulatn",
    "cierpliw", "elastyczn", "zaangażowan", "pracowit", "lojaln", "asertywn", "empatyczn",
    "nastawion", "skoncentrowan", "zainteresowan", "gotow", "zdecydowan", "przygotowan",
    "doświadczon", "wykwalifikowan",
)
_ADJ_RE = re.compile(r"\b(" + "|".join(_ADJECTIVES) + r")(a|y)\b(?!/)", re.IGNORECASE)

# Forma żeńska → zapis neutralny; forma męska (przed „/”) dostaje ten sam zapis.
_TITLES = {
    "absolwentka": "absolwent/ka",
    "studentka": "student/ka",
    "specjalistka": "specjalista/ka",
    "programistka": "programista/ka",
    "analityczka": "analityk/czka",
    "asystentka": "asystent/ka",
    "konsultantka": "konsultant/ka",
    "koordynatorka": "koordynator/ka",
    "kierowniczka": "kierownik/czka",
    "menedżerka": "menedżer/ka",
    "menadżerka": "menadżer/ka",
    "sprzedawczyni": "sprzedawca/czyni",
    "przedstawicielka": "przedstawiciel/ka",
    "doradczyni": "doradca/czyni",
    "praktykantka": "praktykant/ka",
    "stażystka": "stażysta/ka",
    "inżynierka": "inżynier/ka",
    "referentka": "referent/ka",
    "kasjerka": "kasjer/ka",
    "recepcjonistka": "recepcjonista/ka",
    "dyrektorka": "dyrektor/ka",
    "trenerka": "trener/ka",
    "nauczycielka": "nauczyciel/ka",
    "rekruterka": "rekruter/ka",
}
_NEUTRAL_BY_WORD = {**_TITLES, **{v.split("/")[0]: v for v in _TITLES.values()}}
_TITLE_RE = re.compile(
    r"(?<![\w/])(" + "|".join(sorted(map(re.escape, _NEUTRAL_BY_WORD), key=len, reverse=True)) + r")(?![\w/])",
    re.IGNORECASE,
)


def _match_case(template: str, word: str) -> str:
    if word.isupper() and len(word) > 1:
        return template.upper()
    if word[:1].isupper():
        return template[:1].upper() + template[1:]
    return template


def _inside_proper_name(text: str, start: int, end: int) -> bool:
    """Przymiotnik wielką literą obok innego słowa wielką literą to zwykle nazwa własna („Agencja Kreatywna”)."""
    if not text[start:start + 1].isupper():
        return False
    before = re.search(r"([^\W\d_]+)[ \t]+$", text[:start])
    after = re.match(r"[ \t]+([^\W\d_]+)", text[end:])
    return bool((before and before.group(1)[0].isupper()) or (after and after.group(1)[0].isupper()))


def neutralize_gender(text: str) -> tuple[str, int]:
    """Zwraca tekst z zneutralizowanymi formami rodzajowymi oraz liczbę zamian."""
    count = 0

    def replace(template_fn):
        def inner(m: re.Match[str]) -> str:
            nonlocal count
            result = template_fn(m)
            if result is None:
                return m.group(0)
            count += 1
            return _match_case(result, m.group(0))

        return inner

    def past(m: re.Match[str]) -> str | None:
        if m.group(0).lower().endswith(_NOUN_FORMS):
            return None
        return m.group(1).lower() + "łem/am"

    text = _COND_RE.sub(replace(lambda m: m.group(1).lower() + "łbym/abym"), text)
    text = _AC_RE.sub(replace(lambda m: m.group(1).lower() + "ąłem/ęłam"), text)
    text = _PAST_RE.sub(replace(past), text)
    text = _ADJ_RE.sub(
        replace(
            lambda m: None if _inside_proper_name(m.string, m.start(), m.end()) else m.group(1).lower() + "y/a"
        ),
        text,
    )
    text = _TITLE_RE.sub(replace(lambda m: _NEUTRAL_BY_WORD.get(m.group(0).lower())), text)
    return text, count
