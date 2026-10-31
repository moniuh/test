import pytest

from symulator.question_types import classify_question, star_cues

CASES = [
    ("Proszę opowiedzieć o sytuacji, w której miała Pani trudnego klienta.", False, "behawioralne"),
    ("Jak Pan rozwiązał ostatni konflikt w zespole?", False, "behawioralne"),
    ("Opowie Pan o ostatnim konflikcie w zespole, który Pan rozwiązał?", False, "behawioralne"),
    ("Czy pracował Pan w zespole?", False, "zamkniete"),
    ("Ma Pan doświadczenie w obsłudze klienta?", False, "zamkniete"),
    ("Czy wie Pan, jak działa git?", False, "zamkniete"),
    ("Dobrze, czy lubi Pan pracę z ludźmi?", True, "zamkniete"),
    ("Lubi Pan pracę z ludźmi, prawda?", False, "sugerujace"),
    ("Na pewno świetnie radzi Pan sobie ze stresem?", False, "sugerujace"),
    ("Nie uważa Pan, że klient ma zawsze rację?", False, "sugerujace"),
    ("Zakładam, że zna Pan Excela?", False, "sugerujace"),
    ("Jak Pan to zrobił i dlaczego akurat tak?", True, "wielokrotne"),
    ("Co Pan wtedy zrobił? Jaki był efekt?", True, "wielokrotne"),
    ("Czy lubi Pan pracę z ludźmi i czy dobrze znosi Pan stres?", False, "wielokrotne"),
    ("Co Pan wtedy konkretnie zrobił?", True, "dopytujace"),
    ("Jaki był efekt?", True, "dopytujace"),
    ("Rozumiem, a co Pan wtedy zrobił?", True, "dopytujace"),
    ("A jak zareagował klient?", True, "dopytujace"),
    ("Jak radzi Pan sobie ze stresem?", False, "otwarte"),
    ("W jaki sposób motywuje Pan ludzi?", False, "otwarte"),
    ("Czy mógłby Pan opowiedzieć o projekcie, z którego jest Pan dumny?", False, "otwarte"),
    ("Co by Pan zrobił, gdyby klient krzyczał?", False, "otwarte"),
    ("Proszę opowiedzieć o sobie.", False, "otwarte"),
]


@pytest.mark.parametrize(("question", "history", "expected"), CASES)
def test_classifies_question_types(question, history, expected):
    assert classify_question(question, has_history=history).question_type == expected


def test_followup_requires_history():
    assert classify_question("Jaki był efekt?", has_history=False).question_type == "otwarte"


def test_open_form_flags():
    assert classify_question("Jak radzi Pan sobie ze stresem?").open_form
    assert not classify_question("Czy pracował Pan w zespole?").open_form
    assert not classify_question("Lubi Pan ludzi, prawda?").open_form


def test_sub_question_count():
    assert classify_question("Co Pan zrobił? Jak zareagował klient? Jaki był efekt?").sub_questions == 3
    assert classify_question("Jak Pan to zrobił?").sub_questions == 1


@pytest.mark.parametrize(
    ("question", "cue"),
    [
        ("Proszę opisać sytuację, w której…", "S"),
        ("Jaka była Pana rola w tym projekcie?", "T"),
        ("Co konkretnie Pani zrobiła?", "A"),
        ("Jak to się skończyło?", "R"),
        ("Jaki był rezultat?", "R"),
    ],
)
def test_star_cues(question, cue):
    assert cue in star_cues(question)
