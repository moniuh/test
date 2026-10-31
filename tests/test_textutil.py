import pytest

from preselekcja.textutil import find_phrase, find_quote, split_sentences, tokenize, tokens_match


@pytest.mark.parametrize(
    "a,b",
    [("Python", "Pythonie"), ("Java", "Javie"), ("Excel", "Excelu"), ("obsługa", "obsłudze"), ("Docker", "Dockerze"),
     ("Kraków", "Krakowie"), ("zespół", "zespołem"), ("płace", "płac"), ("angielski", "angielskiego"),
     ("doświadczenie", "doświadczeniem"), ("obsluga", "obsługą")],
)
def test_polish_inflection_matches(a, b):
    assert tokens_match(a, b)


@pytest.mark.parametrize("a,b", [("Java", "JavaScript"), ("SQL", "SQLite"), ("ZUS", "zusa"), ("kurs", "kursor")])
def test_no_false_inflection_matches(a, b):
    assert not tokens_match(a, b)


def test_phrase_with_inflection_and_gap():
    tokens = tokenize("Znajomość przepisów prawa pracy oraz obsługa kluczowego klienta")
    assert find_phrase("prawo pracy", tokens) is not None
    assert find_phrase("obsługa klienta", tokens) is not None  # jedno wtrącone słowo
    assert find_phrase("prawo jazdy", tokens) is None


def test_split_sentences_keeps_offsets():
    text = "- Linia pierwsza.\n  Zdanie A. Zdanie B.\n"
    for s in split_sentences(text):
        assert text[s.start:s.end] == s.text
    assert [s.text for s in split_sentences(text)] == ["Linia pierwsza.", "Zdanie A.", "Zdanie B."]


SOURCE = "Prowadziłem/am akta osobowe\n  zgodnie z Kodeksem pracy – od 2019 r.\nExcel: „tabele przestawne”."


def test_quote_found_after_whitespace_and_typography_normalization():
    span = find_quote(SOURCE, 'akta osobowe zgodnie z kodeksem pracy - od 2019')
    assert span is not None
    assert SOURCE[span[0]:span[1]].startswith("akta osobowe")
    assert find_quote(SOURCE, 'Excel: "tabele przestawne"') is not None


def test_quote_with_ellipsis_requires_parts_in_order():
    assert find_quote(SOURCE, "Prowadziłem/am akta … Kodeksem pracy") is not None
    assert find_quote(SOURCE, "Kodeksem pracy … Prowadziłem/am akta") is None


@pytest.mark.parametrize("quote", ["", "   ", "[TELEFON]", "abc", "10 lat doświadczenia w SAP HR"])
def test_fabricated_or_empty_quote_is_rejected(quote):
    assert find_quote(SOURCE + " [TELEFON]", quote) is None


def test_short_terms_match_exactly_or_with_hyphen_suffix():
    assert tokens_match("ZUS", "ZUS")
    assert tokens_match("ZUS", "ZUS-u")
    assert tokens_match("SQL", "sql")
    assert not tokens_match("ZUS", "zusa")
    assert not tokens_match("PIT-11", "PIT-4R")
