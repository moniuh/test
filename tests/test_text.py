import pytest

from symulator.privacy import mask_pii
from symulator.text import count_keyword_hits, is_verified_quote, normalize, stem, word_count, words_match


def test_normalize_strips_polish_diacritics_and_punctuation():
    assert normalize("Źdźbło, ŁĄKA i Żółć!") == "zdzblo laka i zolc"


@pytest.mark.parametrize(
    ("token", "keyword"),
    [
        ("reklamacji", "reklamacja"),
        ("reklamacyjny", "reklamacja"),
        ("zespołem", "zespół"),
        ("zespołowo", "zespół"),
        ("klientami", "klient"),
        ("zdenerwowanym", "zdenerwowany"),
        ("błędami", "błąd"),
        ("błędy", "błąd"),
        ("kodu", "kod"),
        ("dockerem", "docker"),
    ],
)
def test_words_match_handles_inflection(token, keyword):
    assert words_match(token, keyword)


@pytest.mark.parametrize(("token", "keyword"), [("praca", "pracownik"), ("kot", "kod"), ("stres", "strefa"), ("prawo", "praca")])
def test_words_match_rejects_unrelated_words(token, keyword):
    assert not words_match(token, keyword)


def test_multiword_keyword_requires_all_parts():
    assert count_keyword_hits("Miałam trudnego klienta wczoraj", ["trudny klient"]) == 1
    assert count_keyword_hits("Miałam klienta wczoraj", ["trudny klient"]) == 0


def test_stem_keeps_minimum_length():
    assert stem("ma") == "ma"
    assert stem("zespołami") == "zespol"


def test_word_count_counts_polish_words():
    assert word_count("Jak Pan to zrobił? Proszę o szczegóły.") == 7


def test_quote_verification_normalizes_whitespace_only():
    source = "Rekruter: Czy ma Pan\n doświadczenie?\nKandydat: Tak."
    assert is_verified_quote("Czy ma Pan doświadczenie?", source)
    assert is_verified_quote("„Czy ma Pan   doświadczenie?”", source)
    assert not is_verified_quote("Czy ma Pani doświadczenie?", source)
    assert not is_verified_quote("", source)


def test_mask_pii_hides_contact_data():
    text = "Proszę pisać na jan.kowalski@example.com lub dzwonić 600 700 800, PESEL 90010112345, ul. Długa 5"
    masked = mask_pii(text)
    assert "example.com" not in masked
    assert "600 700 800" not in masked
    assert "90010112345" not in masked
    assert "Długa" not in masked
    assert "[e-mail]" in masked and "[telefon]" in masked and "[PESEL]" in masked and "[adres]" in masked


def test_polish_plural():
    from symulator.text import plural

    assert [plural(n, "pytanie", "pytania", "pytań") for n in (1, 2, 5, 12, 22, 25)] == [
        "1 pytanie", "2 pytania", "5 pytań", "12 pytań", "22 pytania", "25 pytań",
    ]
