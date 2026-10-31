from symulator.llm import LLMUnavailable
from symulator.report import (
    ReportNoteAI,
    ReportNotesAI,
    build_report,
    competency_coverage,
    question_stats,
    talk_ratio,
    trap_outcomes,
)


def test_star_completeness_from_revealed_facts(cs_scenario, turn_factory):
    turns = [
        turn_factory(0, "Proszę opowiedzieć o sytuacji…", revealed=["kom_s"]),
        turn_factory(1, "Co Pani zrobiła?", revealed=["kom_a"]),
        turn_factory(2, "A w zespole?", revealed=["zesp_s"]),
    ]
    coverage = {c.competency_id: c for c in competency_coverage(cs_scenario, turns)}
    assert coverage["komunikacja"].elements_revealed == ["S", "A"]
    assert coverage["komunikacja"].elements_missing == ["T", "R"]
    assert coverage["komunikacja"].star_completeness == 0.5
    assert coverage["praca_zespolowa"].star_completeness == 0.25
    assert not coverage["rozwiazywanie_problemow"].covered


def test_talk_ratio_counts_words(turn_factory):
    turns = [
        turn_factory(0, "Jak Pan to zrobił?", answer="Najpierw wszystko dokładnie sprawdziłem i potem naprawiłem błąd w kodzie."),
        turn_factory(1, "Jaki był efekt?", answer="Raport działa trzy razy szybciej."),
    ]
    talk = talk_ratio(turns)
    assert talk.trainee_words == 7
    assert talk.candidate_words == 15
    assert talk.ratio == round(7 / 15, 2)
    assert talk.trainee_share == round(7 / 22, 2)
    assert talk.assessment.startswith("do poprawy")  # 32% > cel 30%


def test_talk_ratio_good_when_candidate_talks_most(turn_factory):
    talk = talk_ratio([turn_factory(0, "Jak Pan to zrobił?", answer=" ".join(["słowo"] * 20))])
    assert talk.trainee_share == round(4 / 24, 2)
    assert talk.assessment.startswith("dobra")


def test_talk_ratio_when_trainee_dominates(turn_factory):
    talk = talk_ratio([turn_factory(0, "To jest bardzo długie pytanie z wieloma słowami, które nic nie wnosi do rozmowy?", answer="Tak.")])
    assert talk.trainee_share > 0.45
    assert "zdominował" in talk.assessment


def test_question_stats(turn_factory):
    turns = [
        turn_factory(0, "Proszę opowiedzieć o sytuacji, w której miała Pani trudnego klienta."),
        turn_factory(1, "Lubi Pani klientów, prawda?"),
        turn_factory(2, "Co Pani zrobiła i jaki był efekt?"),
        turn_factory(3, "Czy pracowała Pani w zespole?"),
    ]
    stats = question_stats(turns)
    assert stats.total == 4
    assert stats.behavioral == 1 and stats.leading == 1 and stats.multiple == 1 and stats.closed == 1
    assert stats.open_count == 2  # behawioralne + wielokrotne o otwartej formie
    assert stats.open_share == 0.5


def test_trap_outcomes(cs_scenario, turn_factory):
    avoided = [turn_factory(0, "Dyspozycyjność?", traps=["pulapka_dzieci"]), turn_factory(1, "Jak radzi sobie Pani ze stresem?")]
    assert trap_outcomes(cs_scenario, avoided)[0].outcome == "uniknieta"
    entered = [turn_factory(0, "Dyspozycyjność?", traps=["pulapka_dzieci"]), turn_factory(1, "A ile lat mają dzieci?")]
    outcome = trap_outcomes(cs_scenario, entered)[0]
    assert outcome.outcome == "wejscie" and outcome.entered_turn == 2
    assert trap_outcomes(cs_scenario, [turn_factory(0, "Pytanie?")])[0].outcome == "nie_wystapila"


def test_report_offline_has_three_rule_recommendations(cs_scenario, player):
    turns = player(cs_scenario, ["Czy ma Pani dzieci?", "Lubi Pani klientów, prawda?", "Czy pracowała Pani w zespole?"])
    report = build_report(cs_scenario, "abc", turns)
    assert len(report.recommendations) == 3
    assert report.recommendations[0].title == "Wyeliminuj pytania niedozwolone"
    assert len(report.incidents) == 1 and report.incidents[0].category == "rodzina"
    assert report.notes_source == "offline"
    assert all(n.quote in {t.question for t in turns} for n in report.notes)


def test_good_session_gets_positive_recommendation_fill(cs_scenario, player):
    comp_questions = []
    for comp in cs_scenario.competencies:
        comp_questions += [comp.sample_question, "Jakie było Pani zadanie w tej sytuacji?", "Co konkretnie Pani wtedy zrobiła?", "Jaki był efekt?"]
    report = build_report(cs_scenario, "ok", player(cs_scenario, comp_questions))
    assert report.coverage_share == 1.0 and report.star_average == 1.0
    assert report.incidents == []
    assert len(report.recommendations) == 3


def _notes(*pairs):
    return ReportNotesAI(notes=[ReportNoteAI(kind=k, observation="Obserwacja", quote=q, suggestion="Wskazówka") for k, q in pairs])


def test_ai_notes_verified_quotes_kept_and_fabricated_rejected(cs_scenario, player, fake_llm_cls):
    turns = player(cs_scenario, ["Proszę opowiedzieć o sytuacji, w której rozmawiała Pani z bardzo zdenerwowanym klientem.", "Lubi Pani klientów, prawda?"])
    fake = fake_llm_cls({ReportNotesAI: _notes(
        ("mocna_strona", "rozmawiała Pani z bardzo   zdenerwowanym klientem"),
        ("do_poprawy", "Lubi Pani klientów, prawda?"),
        ("do_poprawy", "Tego zdania nie było w rozmowie."),
    )})
    report = build_report(cs_scenario, "s1", turns, fake)
    assert report.notes_source == "ai"
    assert len(report.notes) == 2
    assert report.rejected_notes == 1
    assert all(n.source == "ai" for n in report.notes)
    assert "<transkrypt>" in fake.calls[0]["prompt"]


def test_ai_notes_all_rejected_keeps_offline_notes(cs_scenario, player, fake_llm_cls):
    turns = player(cs_scenario, ["Lubi Pani klientów, prawda?"])
    fake = fake_llm_cls({ReportNotesAI: _notes(("do_poprawy", "Zmyślony cytat"))})
    report = build_report(cs_scenario, "s2", turns, fake)
    assert report.notes_source == "offline" and report.rejected_notes == 1 and report.notes


def test_report_falls_back_when_ai_unavailable(cs_scenario, player, fake_llm_cls):
    turns = player(cs_scenario, ["Czy pracowała Pani w zespole?"])
    fake = fake_llm_cls({ReportNotesAI: LLMUnavailable("Model odmówił odpowiedzi")})
    report = build_report(cs_scenario, "s3", turns, fake)
    assert report.notes_source == "offline"
    assert report.ai_error == "Model odmówił odpowiedzi"
    assert len(report.recommendations) == 3
