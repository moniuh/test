import pytest

from asystent.text import (
    analyze,
    fold,
    normalize_for_match,
    query_concepts,
    query_hints,
    stem,
    stems,
    tokenize,
)


def test_fold_lowercases_and_strips_polish_diacritics():
    assert fold("Zażółć GĘŚLĄ jaźń") == "zazolc gesla jazn"


def test_tokenize_joins_hyphenated_words_and_keeps_digits():
    assert tokenize("Zaświadczenie e-ZLA (L4), § 12.") == ["zaswiadczenie", "ezla", "l4", "12"]


@pytest.mark.parametrize(
    "forms",
    [
        ["urlop", "urlopu", "urlopem", "urlopie", "urlopów", "urlopowa", "urlopowej", "urlopowy"],
        ["zwolnienie", "zwolnienia", "zwolnieniu", "zwolnieniem", "zwolnień"],
        ["wynagrodzenie", "wynagrodzenia", "wynagrodzeniu", "wynagrodzeń"],
        ["wypoczynek", "wypoczynku", "wypoczynkowy", "wypoczynkowego"],
        ["praca", "pracy", "pracę", "pracą", "pracować"],
        ["zdalna", "zdalnej", "zdalnie", "zdalną", "zdalnych"],
        ["wniosek", "wniosku", "wnioskiem", "wniosków"],
        ["nieobecność", "nieobecności", "nieobecny"],
        ["dzień", "dni", "dnia", "dniach"],
        ["dofinansowanie", "dofinansowuje"],
        ["sygnalista", "sygnaliści", "sygnalistów", "sygnalisty"],
        ["budżet", "budżetu", "budżecie"],
        ["zakład", "zakładu", "zakładzie"],
    ],
)
def test_stemming_maps_inflected_forms_to_one_stem(forms):
    assert len({stem(fold(f)) for f in forms}) == 1, {f: stem(fold(f)) for f in forms}


def test_stemming_keeps_short_words_and_numbers():
    assert stem("bhp") == "bhp"
    assert stem("l4") == "l4"
    assert stem("2026") == "2026"


def test_stems_drop_stopwords():
    assert stems("Ile mam dni urlopu?") == ["dni", "urlop"]
    assert stems("Jak i kiedy to jest?") == []


@pytest.mark.parametrize(
    ("variant", "canonical"),
    [
        ("urlop", "=urlop"),
        ("wypoczynek", "=urlop"),
        ("L4", "=l4"),
        ("zwolnienie lekarskie", "=l4"),
        ("e-ZLA", "=l4"),
        ("home office", "=praca_zdalna"),
        ("praca zdalna", "=praca_zdalna"),
        ("wypłata", "=wynagrodzenie"),
        ("wynagrodzenie", "=wynagrodzenie"),
        ("nadgodziny", "=nadgodziny"),
        ("praca w godzinach nadliczbowych", "=nadgodziny"),
    ],
)
def test_synonym_groups_add_canonical_token(variant, canonical):
    assert canonical in analyze(variant)


def test_multiword_synonym_counts_once():
    tokens = analyze("urlop wypoczynkowy")
    assert tokens.count("=urlop") == 1


def test_zwolnienie_alone_is_not_sick_leave():
    # „zwolnienie” bez „lekarskie” może oznaczać rozwiązanie umowy — bez synonimu L4
    assert "=l4" not in analyze("zwolnienie z pracy")


def test_query_concepts_link_l4_with_sick_leave():
    concepts = query_concepts("jak zgłosić L4")
    assert frozenset({"l4", "=l4"}) in concepts
    doc = set(analyze("Pracownik przekazuje zwolnienie lekarskie"))
    assert all(c & doc for c in concepts if "=l4" in c)


def test_question_hints_for_ile_and_kiedy():
    assert query_hints("Ile mam urlopu?") == ["=ilosc"]
    assert query_hints("Kiedy wypłata?") == ["=termin"]
    assert query_hints("Jak zgłosić L4?") == []


def test_normalize_for_match_unifies_whitespace_quotes_and_dashes():
    assert normalize_for_match("„Zgłoś\n  naruszenie”  —  formularz") == '"Zgłoś naruszenie" - formularz'
