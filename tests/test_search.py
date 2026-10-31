import math

import pytest

from asystent.search import BM25, Passage, SearchEngine, is_relevant


def _passage(cid: str, title: str, section: str, text: str, key: str | None = None) -> Passage:
    return Passage(cid, 1, key or title.lower(), title, "1", "2026-01-01", (title, section), text)


def test_bm25_idf_and_length_normalization():
    index = BM25([["urlop", "dni"], ["urlop"] + ["inne"] * 20, ["premia"]])
    assert index.idf("premia") > index.idf("urlop")  # rzadszy termin waży więcej
    assert index.idf("nieznane") == pytest.approx(math.log(1 + 3.5 / 0.5))
    # Krótszy dokument z tym samym tf wygrywa
    assert index.score(0, ["urlop"]) > index.score(1, ["urlop"])
    assert index.score(2, ["urlop"]) == 0


def test_synonym_l4_beats_dismissal_distractor():
    engine = SearchEngine([
        _passage("a", "Regulamin", "§ 5. Rozwiązanie umowy",
                 "Zwolnienie z pracy następuje z zachowaniem okresu wypowiedzenia. Zwolnienie wymaga formy pisemnej."),
        _passage("b", "Regulamin", "§ 6. Choroba",
                 "W razie choroby pracownik przekazuje L4 w pierwszym dniu nieobecności."),
    ])
    hits = engine.search("Jak przekazać zwolnienie lekarskie?")
    assert hits[0].passage.chunk_id == "b"
    assert hits[0].coverage > hits[1].coverage


def test_synonym_home_office_finds_remote_work():
    engine = SearchEngine([
        _passage("a", "Polityka", "§ 1. Biuro", "Biuro jest otwarte od 7:00 do 19:00."),
        _passage("b", "Polityka", "§ 2. Praca zdalna", "Pracownik może pracować zdalnie do 3 dni w tygodniu."),
    ])
    assert engine.search("home office")[0].passage.chunk_id == "b"


def test_empty_or_stopword_query_returns_nothing(assistant):
    engine = assistant.engine()
    assert engine.search("") == []
    assert engine.search("jak i kiedy?") == []


def test_doc_keys_filter(assistant):
    hits = assistant.engine().search("zgłoszenie", k=10, doc_keys=["procedura-sygnalistow"])
    assert hits and all(h.passage.doc_key == "procedura-sygnalistow" for h in hits)


@pytest.mark.parametrize(
    ("question", "doc_key", "section_prefix"),
    [
        ("ile mam urlopu", "polityka-urlopowa", "§ 2."),
        ("Ile mam dni urlopu?", "polityka-urlopowa", "§ 2."),
        ("jak zgłosić L4", "regulamin-pracy", "§ 13."),
        ("jak zgłosić zwolnienie lekarskie", "regulamin-pracy", "§ 13."),
        ("ile dni pracy zdalnej", "polityka-pracy-zdalnej", "§ 3."),
        ("home office ile dni w tygodniu", "polityka-pracy-zdalnej", "§ 3."),
        ("kiedy wypłata", "regulamin-wynagradzania", "§ 4."),
        ("jak są płatne nadgodziny", "regulamin-wynagradzania", "§ 6."),
        ("urlop na żądanie", "polityka-urlopowa", "§ 5."),
        ("budżet szkoleniowy", "benefity", "§ 4."),
        ("czy mogę zgłosić naruszenie anonimowo jako sygnalista", "procedura-sygnalistow", "§ 5."),
        ("ile dni wolnego na ślub", "polityka-urlopowa", "§ 8."),
    ],
)
def test_ranking_on_sample_documents(assistant, question, doc_key, section_prefix):
    top = assistant.engine().search(question, k=1)[0]
    assert top.passage.doc_key == doc_key, top.passage.path_label
    assert top.passage.section.startswith(section_prefix), top.passage.path_label
    assert is_relevant(top, assistant.settings.min_score, assistant.settings.min_coverage)


@pytest.mark.parametrize("question", ["Czy firma refunduje kryptowaluty?", "samochód służbowy dla handlowca",
                                      "delegacja zagraniczna dieta"])
def test_off_topic_questions_are_below_threshold(assistant, question):
    hits = assistant.engine().search(question)
    s = assistant.settings
    assert not any(is_relevant(h, s.min_score, s.min_coverage) for h in hits)
