"""Anonimizacja CV (blind screening): maskowanie danych identyfikujących przed oceną i przed modelem.

Podejście regułowe, deterministyczne i testowalne. Maskujemy kategorie, które nie są potrzebne
do oceny kwalifikacji, a mogą prowadzić do dyskryminacji lub identyfikacji osoby:
imię i nazwisko, e-mail, telefon, adres, PESEL, data urodzenia, wiek, zdjęcie, stan cywilny
i dane rodzinne, linki do profili, narodowość/wyznanie. Następnie neutralizujemy najczęstsze
formy rodzajowe (moduł `gender`).
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

from .gender import neutralize_gender

PLACEHOLDERS = {
    "name": "[IMIĘ I NAZWISKO]",
    "email": "[E-MAIL]",
    "phone": "[TELEFON]",
    "address": "[ADRES]",
    "pesel": "[PESEL]",
    "birth_date": "[DATA URODZENIA]",
    "age": "[WIEK]",
    "photo": "[ZDJĘCIE]",
    "marital": "[STAN CYWILNY]",
    "family": "[DANE RODZINNE]",
    "profile_link": "[LINK DO PROFILU]",
    "sensitive": "[DANE WRAŻLIWE]",
}
CATEGORY_LABELS = {
    "name": "imię i nazwisko",
    "email": "e-mail",
    "phone": "telefon",
    "address": "adres",
    "pesel": "PESEL",
    "birth_date": "data urodzenia",
    "age": "wiek",
    "photo": "zdjęcie",
    "marital": "stan cywilny",
    "family": "dane rodzinne",
    "profile_link": "link do profilu",
    "sensitive": "narodowość / wyznanie",
}

_UP = "A-ZĄĆĘŁŃÓŚŹŻ"
_LO = "a-ząćęłńóśźż"
_CAP_WORD = rf"[{_UP}][{_LO}]+(?:-[{_UP}][{_LO}]+)?"

FIRST_NAMES = frozenset(
    """
    Anna Maria Katarzyna Małgorzata Agnieszka Barbara Ewa Krystyna Elżbieta Zofia Magdalena Joanna
    Aleksandra Monika Teresa Danuta Natalia Karolina Julia Marta Beata Dorota Halina Jadwiga Jolanta
    Iwona Grażyna Paulina Justyna Agata Bożena Urszula Renata Sylwia Patrycja Weronika Alicja Hanna
    Kinga Dominika Emilia Oliwia Zuzanna Wiktoria Martyna Izabela Edyta Kamila Klaudia Gabriela Lena
    Piotr Krzysztof Andrzej Tomasz Paweł Jan Michał Marcin Stanisław Jakub Adam Marek Łukasz Grzegorz
    Mateusz Wojciech Mariusz Dariusz Zbigniew Jerzy Maciej Rafał Robert Kamil Józef Ryszard Szymon
    Kacper Dawid Bartosz Daniel Sebastian Artur Damian Janusz Patryk Przemysław Filip Karol Hubert
    Wiktor Igor Oskar Antoni Leon Konrad Norbert Radosław Sławomir Henryk Tadeusz Witold Arkadiusz
    """.split()
)
_FIRST_NAME_ALT = "|".join(sorted(FIRST_NAMES, key=len, reverse=True))

_TITLE_WORDS = {
    "specjalista", "specjalistka", "programista", "programistka", "kierownik", "kierowniczka", "menedżer",
    "manager", "księgowa", "księgowy", "analityk", "analityczka", "inżynier", "asystent", "asystentka",
    "konsultant", "konsultantka", "junior", "senior", "starszy", "starsza", "młodszy", "młodsza", "kadr",
    "płac", "ds.", "dyrektor", "koordynator", "koordynatorka", "referent", "referentka", "praktykant",
    "praktykantka", "doradca", "handlowiec", "sprzedawca", "magazynier", "kierowca", "rekruter",
}
_HEADER_WORDS = {
    "cv", "curriculum", "vitae", "życiorys", "zyciorys", "dane", "osobowe", "kontakt", "profil",
    "doświadczenie", "wykształcenie", "umiejętności", "o", "mnie", "list", "motywacyjny", "resume",
}

EMAIL_RE = re.compile(r"[\w.+\-]+@[\w\-]+(?:\.[\w\-]+)+")
URL_RE = re.compile(
    r"(?:https?://)?(?:www\.)?(?:linkedin\.com|github\.com|gitlab\.com|facebook\.com|fb\.com|"
    r"instagram\.com|twitter\.com|x\.com|goldenline\.pl)/[\w\-./%?=&]*",
    re.IGNORECASE,
)
PESEL_RE = re.compile(r"(?<![\d+])\d{11}(?!\d)")
PESEL_LABEL_RE = re.compile(r"(PESEL\s*[:\-–]?\s*)(\d[\d\s]{9,14}\d)", re.IGNORECASE)
PHONE_RE = re.compile(
    r"(?<![\d+])(?:(?:\+|00)\s?48[\s\-]?)?"
    r"(?:\(\d{2}\)\s?\d{3}[\s\-]?\d{2}[\s\-]?\d{2}"
    r"|\d{3}[\s\-]?\d{3}[\s\-]?\d{3}"
    r"|\d{2}[\s\-]\d{3}[\s\-]\d{2}[\s\-]\d{2})"
    r"(?!\d)"
)
_STREET_WORD = rf"(?:[{_UP}0-9][\w.\-]*|im\.|i|w|nad|pod)"
STREET_RE = re.compile(
    rf"\b(?:ul\.|ulica|al\.|aleja|aleje|os\.|osiedle|pl\.|plac)\s+"
    rf"{_STREET_WORD}(?:\s+{_STREET_WORD}){{0,4}}?\s+"
    rf"\d+[A-Za-z]?(?:\s*/\s*\d+[A-Za-z]?)?(?:\s*(?:m\.|lok\.)\s*\d+)?",
)
POSTAL_RE = re.compile(rf"(?<!\d)\d{{2}}-\d{{3}}(?!\d)[ \t]+{_CAP_WORD}(?:[ \t]+{_CAP_WORD})?")
ADDRESS_LABEL_RE = re.compile(
    r"^([ \t]*[-•*]?[ \t]*(?:adres(?: zamieszkania| do korespondencji)?|miejsce zamieszkania|zamieszkał[ay]?)"
    r"[ \t]*[:\-–][ \t]*)(\S[^\n]*)$",
    re.IGNORECASE | re.MULTILINE,
)
_MONTHS = (
    r"stycznia|lutego|marca|kwietnia|maja|czerwca|lipca|sierpnia|września|wrzesnia|października|"
    r"pazdziernika|listopada|grudnia"
)
_DATE = rf"(?:\d{{1,2}}[./\-]\d{{1,2}}[./\-]\d{{2,4}}|\d{{4}}-\d{{2}}-\d{{2}}|\d{{1,2}}\s+(?:{_MONTHS})\s+\d{{4}}|(?:19|20)\d{{2}})"
BIRTH_RE = re.compile(
    rf"(\b(?:data\s+urodzenia|data\s+ur\.|urodzon[ya]|urodził[ae]m|ur\.)\s*(?:[:\-–]|dnia|w\s+dniu)?\s*(?:r\.\s*)?)({_DATE})(\s*r\.)?",
    re.IGNORECASE,
)
YEAR_OF_BIRTH_RE = re.compile(r"\brocznik\s*[:\-–]?\s*(?:19|20)\d{2}\b", re.IGNORECASE)
AGE_RES = (
    re.compile(r"\bwiek\s*[:\-–]?\s*\d{1,2}(?:\s*(?:lat|lata|l\.))?", re.IGNORECASE),
    re.compile(r"\b(?:mam|liczę|skończył[ae]m)\s+\d{2}\s+(?:lat|lata)\b", re.IGNORECASE),
    re.compile(
        r"\b\d{2}\s*-\s*letni(?:a|ą|e|ego|ej|emu|m|ch)?\b"
        r"(?!\s+(?:doświadczeni|staż|praktyk|okres|kontrakt|współprac|pracy|projekt|stażem))",
        re.IGNORECASE,
    ),
)
PHOTO_RES = (
    re.compile(r"\[\s*(?:zdjęcie|zdjecie|fotografia|foto|photo)[^\]\n]*\]", re.IGNORECASE),
    re.compile(r"^[ \t]*(?:zdjęcie|zdjecie|fotografia|foto|photo)[ \t]*[:\-–][^\n]*$", re.IGNORECASE | re.MULTILINE),
    re.compile(
        r"\b(?:w załączeniu|w załączniku|załączam|dołączam|dołączone)\s+(?:(?:moje|aktualne)\s+)?"
        r"(?:zdjęcie|zdjęcia|fotografię|fotografia)\b[^\n.]*",
        re.IGNORECASE,
    ),
)
MARITAL_RES = (
    re.compile(r"(stan\s+cywilny\s*[:\-–]\s*)([^\n,;]+)", re.IGNORECASE),
    re.compile(
        r"\b(?:żonaty|zamężna|mężatka|kawaler|panna|rozwiedzion[ya]|wdowa|wdowiec|"
        r"w\s+związku\s+małżeńskim|w\s+separacji)\b",
        re.IGNORECASE,
    ),
)
FAMILY_RES = (
    re.compile(r"((?:liczba\s+)?dzieci\s*[:\-–]\s*)([^\n,;]+)", re.IGNORECASE),
    re.compile(
        r"\b(?:mam|posiadam|wychowuję|jestem\s+(?:matką|ojcem|mamą|tatą))\s+(?:\w+\s+){0,2}?"
        r"(?:dzieci|dziecko|dzieckiem|córkę|córki|syna|synów|synami|córkami)\b",
        re.IGNORECASE,
    ),
)
SENSITIVE_RE = re.compile(r"((?:narodowość|wyznanie|religia)\s*[:\-–]\s*)([^\n,;]+)", re.IGNORECASE)
NAME_LABEL_RE = re.compile(
    rf"((?:imię\s+i\s+nazwisko|imie\s+i\s+nazwisko|nazwisko\s+i\s+imię|nazwisko|imię)\s*[:\-–]\s*)"
    rf"({_CAP_WORD}(?:[ \t]+{_CAP_WORD}){{0,2}})",
    re.IGNORECASE,
)
FULL_NAME_RE = re.compile(rf"\b(?:{_FIRST_NAME_ALT})[ \t]+{_CAP_WORD}\b")


_NAME_ENDINGS = (
    r"(?:iego|iemu|owej|owie|owi|ową|owa|iej|iem|ego|emu|ich|imi|ie|em|om|ej|im|a|e|i|y|u|ą|ę)?"
)
_PLACEHOLDER_RE = re.compile(r"\[[A-ZĄĆĘŁŃÓŚŹŻ /\-]+\]")


@dataclass
class AnonymizationResult:
    text: str
    counts: dict[str, int] = field(default_factory=dict)
    identity: dict[str, list[str]] = field(default_factory=dict)
    gender_neutralized: int = 0


class _Masker:
    def __init__(self, text: str) -> None:
        self.text = text
        self.counts: dict[str, int] = {}
        self.identity: dict[str, list[str]] = {}

    def _record(self, category: str, value: str) -> None:
        self.counts[category] = self.counts.get(category, 0) + 1
        values = self.identity.setdefault(category, [])
        value = " ".join(value.split())
        if value and value not in values:
            values.append(value)

    def mask(self, category: str, pattern: re.Pattern[str], value_group: int = 0) -> None:
        placeholder = PLACEHOLDERS[category]

        def repl(m: re.Match[str]) -> str:
            value = m.group(value_group)
            if not value or _PLACEHOLDER_RE.fullmatch(value.strip()):
                return m.group(0)
            self._record(category, value)
            if value_group == 0:
                return placeholder
            start, end = m.span(value_group)
            whole_start = m.start()
            return m.group(0)[: start - whole_start] + placeholder + m.group(0)[end - whole_start :]

        self.text = pattern.sub(repl, self.text)


def _name_from_header(text: str) -> list[str]:
    """Imię i nazwisko z pierwszych linii CV (typowy nagłówek): 2–4 słowa wielką literą, bez cyfr."""
    checked = 0
    for line in text.splitlines():
        line = line.strip().strip("•-*|").strip()
        if not line:
            continue
        checked += 1
        if checked > 3:
            break
        words = line.split()
        if any(w.lower().strip(":") in _HEADER_WORDS | _TITLE_WORDS for w in words):
            continue
        if 2 <= len(words) <= 4 and all(
            re.fullmatch(rf"{_CAP_WORD}|[{_UP}]{{2,}}(?:-[{_UP}]{{2,}})?", w) for w in words
        ):
            return words
    return []


def anonymize(text: str, neutralize: bool = True) -> AnonymizationResult:
    """Maskuje dane identyfikujące. Zwraca tekst zanonimizowany, liczniki i wykryte dane (do odkrycia)."""
    header_name = _name_from_header(text)
    m = _Masker(text)
    m.mask("email", EMAIL_RE)
    m.mask("profile_link", URL_RE)
    m.mask("pesel", PESEL_LABEL_RE, 2)
    m.mask("pesel", PESEL_RE)
    m.mask("birth_date", BIRTH_RE, 2)
    m.mask("birth_date", YEAR_OF_BIRTH_RE)
    m.mask("address", ADDRESS_LABEL_RE, 2)
    m.mask("address", STREET_RE)
    m.mask("address", POSTAL_RE)
    m.mask("phone", PHONE_RE)
    for pattern in AGE_RES:
        m.mask("age", pattern)
    for pattern in PHOTO_RES:
        m.mask("photo", pattern)
    m.mask("marital", MARITAL_RES[0], 2)
    m.mask("marital", MARITAL_RES[1])
    m.mask("family", FAMILY_RES[0], 2)
    m.mask("family", FAMILY_RES[1])
    m.mask("sensitive", SENSITIVE_RE, 2)

    # Imię i nazwisko: z etykiety, z nagłówka i z listy imion; potem wszystkie wystąpienia tych słów.
    name_tokens: set[str] = set(header_name)
    for match in NAME_LABEL_RE.finditer(m.text):
        name_tokens.update(match.group(2).split())
    m.mask("name", NAME_LABEL_RE, 2)
    for match in FULL_NAME_RE.finditer(m.text):
        name_tokens.update(match.group(0).split())
    if header_name:
        header_pattern = re.compile(r"\b" + r"\s+".join(map(re.escape, header_name)) + r"\b")
        m.mask("name", header_pattern)
    m.mask("name", FULL_NAME_RE)
    for token in sorted(name_tokens, key=len, reverse=True):
        if len(token) < 3 or token.startswith("["):
            continue
        token_cap = token[:1].upper() + token[1:].lower()
        # Nazwiska i imiona się odmieniają (Kowalska → Kowalskiej, Nowak → Nowakiem): obcinamy końcową
        # samogłoskę i dopuszczamy tylko typowe końcówki fleksyjne (żeby „Lis” nie maskował „Lista”).
        if len(token_cap) >= 4 and token_cap[-1] in "aeiouy":
            stem = re.escape(token_cap[:-1])
        else:
            stem = re.escape(token_cap)
        pattern = re.compile(rf"\b(?:{stem}{_NAME_ENDINGS}|{re.escape(token.upper())})\b")
        m.mask("name", pattern)
    # Sklejenie sąsiednich znaczników („[IMIĘ I NAZWISKO] [IMIĘ I NAZWISKO]” → jeden).
    m.text = re.sub(r"(\[IMIĘ I NAZWISKO\])(?:[ \t]+\[IMIĘ I NAZWISKO\])+", r"\1", m.text)
    m.text = re.sub(r"(\[ADRES\])(?:[ \t]*,?[ \t]*\[ADRES\])+", r"\1", m.text)

    neutralized = 0
    text_out = m.text
    if neutralize:
        text_out, neutralized = neutralize_gender(text_out)
    return AnonymizationResult(text=text_out, counts=m.counts, identity=m.identity, gender_neutralized=neutralized)
