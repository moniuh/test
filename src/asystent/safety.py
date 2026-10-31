"""Bezpieczniki: reguły wykrywające tematy wrażliwe i próby prompt injection.

Reguły działają przed wywołaniem modelu (model dodatkowo klasyfikuje temat). Celowo
wolimy fałszywy alarm niż przeoczenie: skutkiem alarmu jest przekierowanie do HR
albo dodatkowe zastrzeżenie, a nie odmowa pomocy.
"""
from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass, field
from typing import Literal

from .text import fold

Category = Literal["mobbing_dyskryminacja", "zdrowie", "sprawa_indywidualna", "porada_prawna"]
HARASSMENT: Category = "mobbing_dyskryminacja"
HEALTH: Category = "zdrowie"
INDIVIDUAL: Category = "sprawa_indywidualna"
LEGAL: Category = "porada_prawna"

HEALTH_MARK = "[informacja o zdrowiu]"

CATEGORY_LABELS: dict[str, str] = {
    HARASSMENT: "mobbing, molestowanie, dyskryminacja lub odwet",
    HEALTH: "zdrowie i dane medyczne",
    INDIVIDUAL: "sprawa indywidualna lub dane innej osoby",
    LEGAL: "porada prawna",
}


def _rx(*patterns: str) -> re.Pattern[str]:
    return re.compile("|".join(f"(?:{p})" for p in patterns))


# Wzorce działają na tekście po fold(): małe litery, bez polskich znaków.
_HARASSMENT_RE = _rx(
    r"\bmob+ing\w*", r"\bmobbuj\w*", r"\bnek(a|an|aj|al)\w*", r"\bmolest\w*", r"\bnapastow\w*",
    r"\bdyskrymin\w*", r"\bodwet\w*", r"\bszykan\w*", r"\bponiz(a|aj|an|ony|ona|eni)\w*",
    r"\bupokarz\w*", r"\bzastrasz\w*", r"\bgnebi\w*", r"\bwyzyw\w*", r"\bseksualn\w*",
    r"\bseksist\w*", r"\brasist\w*", r"\bhomofob\w*", r"\bobmacyw\w*", r"\bkrzycz\w* na mnie",
    r"\bzemst\w*", r"\bmsci\w*", r"\bwrogie srodowisko",
)
_HEALTH_RE = _rx(
    r"\bdiagnoz\w*", r"\bdepresj\w*", r"\bnowotw\w*", r"\bonkolog\w*", r"\brak(a|iem)?\b",
    r"\bcukrzyc\w*", r"\bw ciazy\b", r"\bciaz(a|y|e|ow)\b", r"\bporonien\w*", r"\bpsychiatr\w*",
    r"\bterapi\w*", r"\bterapeut\w*", r"\bleki\b", r"\blekow\b", r"\bleczeni\w*", r"\bszpital\w*",
    r"\bhiv\b", r"\buzalezni\w*", r"\bniepelnospraw\w*", r"\bchoruj\w*(?: na \w+)?", r"\blecz\w* sie na \w+",
    r"\bwynik\w* badan\w*",
    r"\bdolegliw\w*", r"\bzawal\w*", r"\budar\w*", r"\bnerwic\w*", r"\bstan\w* lekow\w*",
    r"\bzaburzen\w*", r"\bchorob\w* (przewlek|psych|nowotw)\w*", r"\bpanik\w*", r"\bzabieg\w*",
)
# Osoba trzecia: nazwisko o typowej końcówce, „pan/pani X” albo wskazanie współpracownika.
_SURNAME_SUFFIXES = r"(?:ski|cki|dzki|ska|cka|dzka|skiego|ckiego|skiej|ckiej|wicz|wicza|czyk|czyka|ak|aka)"
_SURNAME_WORD_RE = re.compile(r"^[A-ZĄĆĘŁŃÓŚŹŻ][a-ząćęłńóśźż]+" + _SURNAME_SUFFIXES + r"$")
_SURNAME_RE = re.compile(r"\b[A-ZĄĆĘŁŃÓŚŹŻ][a-ząćęłńóśźż]+" + _SURNAME_SUFFIXES + r"\b")
_OTHER_PERSON_WORD_RE = re.compile(
    r"^(koleg|kolezank|wspolpracownik|szef|kierownik|przelozon|ktos|innego|innej|inni|innych|tego|tej)\w*$"
)
_PRIVATE_TOPIC_WORD_RE = re.compile(
    r"^(zarabia|pensj|wynagrodzeni|stawk|podwyzk|premi|ocen|chor|l4$|zwolni|adres|telefon|pesel|dane$|danych$|prywat)\w*"
)
_FIRST_PERSON = frozenset({"mi", "mnie", "moje", "moj", "moja", "moim", "mojego", "mojej", "swoje", "swoja", "swoj"})
_TITLE_WORDS = frozenset({"pan", "pani", "pana", "panu", "panem", "pania"})
_PERSON_WINDOW = 4
_OWN_MATTER_RE = _rx(
    r"\bczy (mnie )?zwolni\w*", r"\bzwolni\w* mnie\b", r"\bzostan\w* zwolnion\w*", r"\bstrac\w* prac\w*",
    r"\bwyrzuc\w* mnie\b", r"\bwypowiedzeni\w* (mi|dla mnie)\b", r"\bdostane (podwyzk|premi|awans)\w*",
    r"\bdlaczego (nie )?dostal\w*", r"\bmoj\w* (ocen\w*|umow\w*|premi\w*|podwyzk\w*|awans\w*)\b",
    r"\b(przedluz\w*|przedluza\w*) (mi|moja|moje|moj)\w* umow\w*", r"\bredukcj\w* etat\w*",
    r"\bzwolnieni\w* grupow\w*", r"\bmoj\w* (sald\w*|wniosek|wniosku) .*(odrzuc|zatwierdz)\w*",
)
_LEGAL_RE = _rx(
    r"\b(nie)?legaln\w*", r"\bzgodn\w* z prawem\b", r"\bkodeks\w*", r"\bsad\w* pracy\b",
    r"\bpozw(ac|e|iesz|ie|al|ali|any|ana|ani|anie)\b",
    r"\bpozew\b", r"\bprawnik\w*", r"\bradc\w* prawn\w*", r"\badwokat\w*", r"\bodszkodowan\w*",
    r"\bpip\b", r"\binspekcj\w* pracy\b", r"\bart\.? ?\d+", r"\bartykul\w*", r"\bporad\w* prawn\w*",
    r"\bczy pracodawca moze\b", r"\bczy (firma|pracodawca) ma prawo\b", r"\bnaruszeni\w* praw\w* pracownik\w*",
    r"\bustaw\w*", r"\bprzepis\w* prawa\b",
)
_INJECTION_RE = _rx(
    r"zignoruj\w* (wszystk\w* |poprzedni\w* |wczesniejsz\w* |powyzsz\w* |swoje )*(instrukcj|polecen|zasad|regul)",
    r"ignore (all |any |the )?(previous |prior |above |earlier )?(instructions|prompts|rules)",
    r"\bdisregard\b", r"\bsystem prompt", r"\bprompt\w* systemow\w*", r"\bjestes teraz\b",
    r"\bod teraz jestes\b", r"\budawaj\w*\b", r"\bdeveloper mode\b", r"\bjailbreak\w*",
    r"\b(pokaz|wypisz|ujawnij)\w* (swoj\w* |twoj\w* )?(instrukcj|prompt|polecen)",
    r"</?\s*(fragment|fragmenty|pytanie|system|instrukcj\w*)\s*>", r"\bact as\b",
    r"\bzmien\w* (swoje |twoje )?(zasady|instrukcje)", r"\bnie (musisz|trzeba) cytowac\b",
    r"\bodpowiedz bez cytat\w*", r"\bnowe instrukcje\b", r"\bnadpisz\w*",
)

_EMAIL_RE = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
_PESEL_RE = re.compile(r"\b\d{11}\b")
_ACCOUNT_RE = re.compile(r"\b(?:PL)?\d{2}(?:[ ]?\d{4}){6}\b")
_PHONE_RE = re.compile(r"(?<!\w)(?:\+48[ -]?)?(?:\d{3}[ -]?\d{3}[ -]?\d{3}|\d{2}[ -]\d{3}[ -]\d{2}[ -]\d{2})(?!\w)")
_TITLE_NAME_RE = re.compile(
    r"\b(?:[Pp]an(?:i|a|u|em)?|[Pp]anią|[Kk]oleg[aię]|[Kk]oleżank[aię])\s+[A-ZĄĆĘŁŃÓŚŹŻ][a-ząćęłńóśźż]+"
)


@dataclass
class SafetyCheck:
    """Wynik reguł dla pytania."""

    categories: list[str] = field(default_factory=list)
    injection: bool = False
    others_data: bool = False  # pytanie o dane innej osoby

    def has(self, category: str) -> bool:
        return category in self.categories


def detect_injection(text: str) -> bool:
    """Czy tekst zawiera polecenia skierowane do modelu (prompt injection)."""
    return bool(_INJECTION_RE.search(fold(text)))


def asks_about_others(text: str) -> bool:
    """Czy pytanie dotyczy wynagrodzenia, zdrowia, ocen lub danych konkretnej innej osoby.

    Heurystyka: słowo „prywatne” (zarabia, pensja, L4, adres…) w pobliżu wskazania osoby
    (nazwisko, „pan/pani X”, kolega, szef…), chyba że temat jest wprost „mój”.
    """
    words = re.findall(r"\w+", text)
    folded = [fold(w) for w in words]
    persons: list[int] = []
    for i, (word, low) in enumerate(zip(words, folded, strict=True)):
        if _OTHER_PERSON_WORD_RE.match(low) or _SURNAME_WORD_RE.match(word):
            persons.append(i)
        elif low in _TITLE_WORDS and i + 1 < len(words) and words[i + 1][:1].isupper():
            persons.append(i)
    if not persons:
        return False
    for t, low in enumerate(folded):
        if not _PRIVATE_TOPIC_WORD_RE.match(low):
            continue
        neighbours = {folded[j] for j in (t - 1, t + 1) if 0 <= j < len(folded)}
        if neighbours & _FIRST_PERSON:
            continue
        if any(abs(p - t) <= _PERSON_WINDOW for p in persons if p != t):
            return True
    return False


def check_question(text: str) -> SafetyCheck:
    """Klasyfikacja pytania regułami (może zwrócić kilka kategorii)."""
    folded = fold(text)
    check = SafetyCheck(injection=detect_injection(text))
    if _HARASSMENT_RE.search(folded):
        check.categories.append(HARASSMENT)
    if _HEALTH_RE.search(folded):
        check.categories.append(HEALTH)
    check.others_data = asks_about_others(text)
    if check.others_data or _OWN_MATTER_RE.search(folded):
        check.categories.append(INDIVIDUAL)
    if _LEGAL_RE.search(folded):
        check.categories.append(LEGAL)
    return check


def mask_pii(text: str) -> str:
    """Maskuje dane identyfikujące: e-mail, PESEL, numer konta, telefon, „pan/pani Nazwisko”, nazwiska."""
    text = _EMAIL_RE.sub("[e-mail]", text)
    text = _ACCOUNT_RE.sub("[numer konta]", text)
    text = _PESEL_RE.sub("[PESEL]", text)
    text = _PHONE_RE.sub("[telefon]", text)
    text = _TITLE_NAME_RE.sub(lambda m: m.group(0).split()[0] + " [osoba]", text)
    return _SURNAME_RE.sub("[osoba]", text)


def mask_health(text: str) -> str:
    """Zastępuje wzmianki o stanie zdrowia znacznikiem — szczegółów nie zapisujemy ani nie wysyłamy."""
    text = unicodedata.normalize("NFC", text)
    folded = fold(text)
    if len(folded) != len(text):  # nietypowe znaki zmieniły długość — maskujemy po słowach
        return re.sub(r"\w+", lambda m: HEALTH_MARK if _HEALTH_RE.search(fold(m.group(0))) else m.group(0), text)
    spans: list[list[int]] = []
    for match in _HEALTH_RE.finditer(folded):
        start, end = match.span()
        if spans and start <= spans[-1][1] + 1:
            spans[-1][1] = max(spans[-1][1], end)
        else:
            spans.append([start, end])
    for start, end in reversed(spans):
        text = text[:start] + HEALTH_MARK + text[end:]
    return text


def escape_for_prompt(text: str) -> str:
    """Neutralizuje znaczniki, by dane nie mogły „zamknąć” tagu w prompcie."""
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
