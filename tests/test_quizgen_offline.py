from pathlib import Path

import pytest

from conftest import MATERIAL
from szkolenia.quizgen import BLANK, duration_context, generate_offline, inflect_duration
from szkolenia.textutil import normalize_for_match, verify_quote

MATERIALS = sorted((Path(__file__).resolve().parents[1] / "data" / "materialy").glob("*.txt"))
ELEARNING = [p for p in MATERIALS if p.stem != "instruktaz-stanowiskowy"]


@pytest.mark.parametrize("path", ELEARNING, ids=lambda p: p.stem)
def test_offline_questions_are_well_formed(path):
    material = path.read_text(encoding="utf-8")
    result = generate_offline(material, count=12)
    assert result.source == "offline"
    assert len(result.questions) >= 6
    texts = [normalize_for_match(q.text) for q in result.questions]
    assert len(texts) == len(set(texts)), "duplikaty pytań"
    for q in result.questions:
        assert len(q.options) == 4
        assert len({normalize_for_match(o) for o in q.options}) == 4, f"powtórzone opcje: {q.options}"
        assert 0 <= q.correct_index < 4
        assert q.text.count(BLANK) == 1
        assert verify_quote(q.source_quote, material) and q.quote_verified
        # luka wypełniona poprawną odpowiedzią odtwarza zdanie z materiału
        cloze = q.text.split("„", 1)[1].rstrip("”")
        assert cloze.replace(BLANK, q.correct_answer) == q.source_quote
        assert q.correct_answer not in cloze.replace(BLANK, "")  # odpowiedź nie wycieka w treści


def test_offline_is_deterministic():
    a = generate_offline(MATERIAL, count=10)
    b = generate_offline(MATERIAL, count=10)
    assert [(q.text, q.options, q.correct_index) for q in a.questions] == \
           [(q.text, q.options, q.correct_index) for q in b.questions]


def test_definition_questions_use_other_defined_terms_as_distractors():
    result = generate_offline(MATERIAL, count=10)
    definitions = [q for q in result.questions if q.kind == "definicja"]
    assert definitions
    terms = {"Sygnalista", "Działania odwetowe", "Zgłoszenie wewnętrzne", "Zgłoszenie zewnętrzne"}
    for q in definitions:
        assert set(q.options) == terms
        assert q.text.startswith("Uzupełnij lukę w definicji: „____ to")


def test_number_questions_have_grammatical_polish_distractors():
    result = generate_offline(MATERIAL, count=10)
    by_answer = {q.correct_answer: q for q in result.questions if q.kind == "liczba"}
    # „w terminie nie dłuższym niż 3 miesiące” — mianownik/biernik, więc „6 miesięcy”, ale „2 miesiące”
    months = by_answer["3 miesiące"]
    assert all(o.split()[1] in {"dni", "miesiące", "miesięcy", "lata", "lat"} for o in months.options)
    assert "7 dni" in months.options  # dystraktor z innego terminu w materiale
    days = by_answer["7 dni"]
    assert all(o.endswith(" dni") or o.endswith(" miesięcy") for o in days.options)


def test_date_question_keeps_genitive_month_names():
    result = generate_offline(MATERIAL, count=10)
    q = next(q for q in result.questions if q.kind == "data")
    assert q.correct_answer == "25 września 2024"
    months = {"stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia",
              "września", "października", "listopada", "grudnia"}
    assert all(o.split()[1] in months for o in q.options)


@pytest.mark.parametrize(("n", "lemma", "ctx", "expected"), [
    (3, "rok", "nom", "3 lata"), (5, "rok", "nom", "5 lat"), (22, "rok", "nom", "22 lata"),
    (12, "rok", "nom", "12 lat"), (3, "miesiąc", "gen", "3 miesięcy"), (2, "tydzień", "nom", "2 tygodnie"),
    (4, "godzina", "nom", "4 godziny"), (48, "godzina", "any", "48 godzin"), (3, "dzień", "any", "3 dni"),
    (3, "miesiąc", "any", None), (1, "rok", "nom", None),
])
def test_inflect_duration(n, lemma, ctx, expected):
    assert inflect_duration(n, lemma, ctx) == expected


@pytest.mark.parametrize(("n", "tag", "ctx"), [
    (3, "pl_nom", "nom"), (3, "pl_gen", "gen"), (1, "sg_gen", "gen"), (1, "sg_nom", "nom"),
    (72, "few_or_gen", "nom"), (5, "pl_gen", "any"), (3, "pl_any", "any"),
])
def test_duration_context(n, tag, ctx):
    assert duration_context(n, tag) == ctx


def test_respects_count_and_existing_questions():
    first = generate_offline(MATERIAL, count=3)
    assert len(first.questions) == 3
    again = generate_offline(MATERIAL, count=10, existing=[q.text for q in first.questions])
    assert not {q.text for q in first.questions} & {q.text for q in again.questions}


def test_material_without_terms_gives_no_questions_and_warning():
    result = generate_offline("Dbamy o porządek w biurze i uprzejmość wobec gości firmy.", count=5)
    assert result.questions == []
    assert result.warnings and "0 z 5" in result.warnings[0]


def test_headings_are_not_used_as_questions():
    material = "Szkolenie okresowe BHP — stanowiska robotnicze\n\n" + MATERIAL
    result = generate_offline(material, count=20)
    assert all("stanowiska robotnicze" not in q.text for q in result.questions)
