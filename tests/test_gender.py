import pytest

from preselekcja.gender import neutralize_gender


@pytest.mark.parametrize(
    "src,expected",
    [
        ("Pracowałam w kadrach.", "Pracowałem/am w kadrach."),
        ("Pracowałem w kadrach.", "Pracowałem/am w kadrach."),
        ("byłam", "byłem/am"),
        ("Mogłam pomóc.", "Mogłem/am pomóc."),
        ("Podjęłam pracę.", "Podjąłem/ęłam pracę."),
        ("Osiągnąłem cel.", "Osiągnąłem/ęłam cel."),
        ("Chciałabym", "Chciałbym/abym"),
        ("Jestem komunikatywna i sumienny.", "Jestem komunikatywny/a i sumienny/a."),
        ("Specjalistka ds. płac", "Specjalista/ka ds. płac"),
        ("programista Python", "programista/ka Python"),
        ("KIEROWAŁAM ZESPOŁEM", "KIEROWAŁEM/AM ZESPOŁEM"),
    ],
)
def test_neutralizes_common_forms(src, expected):
    assert neutralize_gender(src)[0] == expected


@pytest.mark.parametrize(
    "text",
    [
        "Kierowałem działem płac.",  # „działem” — rzeczownik, zostaje
        "z materiałem i tytułem magistra",
        "kwartałem i kanałem",
        "Agencja Kreatywna Przykład",  # nazwa własna
        "kłam",
    ],
)
def test_leaves_nouns_and_proper_names(text):
    out, _ = neutralize_gender(text)
    for word in ("działem", "materiałem", "tytułem", "kwartałem", "kanałem", "Kreatywna", "kłam"):
        if word in text:
            assert word in out


def test_feminine_and_masculine_versions_become_identical():
    fem = "Pracowałam jako specjalistka. Byłam odpowiedzialna za listy płac. Podjęłam studia."
    masc = "Pracowałem jako specjalista. Byłem odpowiedzialny za listy płac. Podjąłem studia."
    assert neutralize_gender(fem)[0] == neutralize_gender(masc)[0]


def test_is_idempotent():
    once, _ = neutralize_gender("Pracowałam, komunikatywna specjalistka")
    twice, count = neutralize_gender(once)
    assert twice == once and count == 0
