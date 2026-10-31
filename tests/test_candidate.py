from symulator.candidate import (
    CandidateReplyAI,
    OfflineCandidate,
    candidate_reply,
    validate_ai_reply,
)
from symulator.coach import rule_analysis
from symulator.llm import LLMUnavailable


def _ask(scenario, question, history=()):
    history = list(history)
    analysis = rule_analysis(question, has_history=bool(history))
    return OfflineCandidate(scenario).respond(question, analysis, history)


def test_behavioral_question_reveals_situation_fact(cs_scenario):
    reply = _ask(cs_scenario, "Proszę opowiedzieć o sytuacji, w której rozmawiała Pani z bardzo zdenerwowanym klientem.")
    assert reply.revealed_fact_ids == ["kom_s"]
    assert "podwójne opłaty" in reply.answer
    assert reply.source == "offline"


def test_keyword_matching_handles_inflection(warehouse_scenario):
    reply = _ask(warehouse_scenario, "Proszę opowiedzieć o sytuacji, gdy na zmianie padł system i był przestój.")
    assert reply.revealed_fact_ids == ["kryz_s"]


def test_closed_question_is_evasive_and_reveals_nothing_but_hints(cs_scenario):
    reply = _ask(cs_scenario, "Czy miała Pani trudnych klientów?")
    assert reply.revealed_fact_ids == []
    assert "Miałam kiedyś takiego klienta" in reply.answer  # aluzja zachęcająca do pytania otwartego


def test_leading_question_gets_agreement_without_facts(cs_scenario):
    reply = _ask(cs_scenario, "Na pewno świetnie radzi sobie Pani z klientami, prawda?")
    assert reply.revealed_fact_ids == []


def test_illegal_question_causes_awkwardness(cs_scenario):
    reply = _ask(cs_scenario, "Czy ma Pani dzieci?")
    assert reply.emotion == "zaklopotana"
    assert reply.revealed_fact_ids == []
    assert reply.answer in cs_scenario.persona.awkward_answers


def test_followups_walk_through_star_and_respect_prerequisites(cs_scenario, player):
    turns = player(cs_scenario, [
        "Proszę opowiedzieć o sytuacji, w której rozmawiała Pani z bardzo zdenerwowanym klientem.",
        "Jaki był efekt?",  # R wymaga A — kandydat odpowiada ogólnikiem
        "Co konkretnie Pani wtedy zrobiła?",
        "Jaki był efekt?",
    ])
    assert turns[0].revealed_fact_ids == ["kom_s"]
    assert turns[1].revealed_fact_ids == []
    assert "Ogólnie skończyło się dobrze" in turns[1].answer
    assert turns[2].revealed_fact_ids == ["kom_a"]
    assert turns[3].revealed_fact_ids == ["kom_r"]


def test_multiple_question_answers_only_first_part(cs_scenario, player):
    turns = player(cs_scenario, [
        "Proszę opowiedzieć o sytuacji, w której rozmawiała Pani z bardzo zdenerwowanym klientem.",
        "Co Pani wtedy zrobiła i jaki był efekt?",
    ])
    assert turns[1].coach.question_type == "wielokrotne"
    assert turns[1].revealed_fact_ids == ["kom_a"]


def test_evasive_persona_needs_concrete_situation(warehouse_scenario):
    general = _ask(warehouse_scenario, "Jak prowadzi Pan trudną rozmowę z pracownikiem?")
    assert general.revealed_fact_ids == []
    concrete = _ask(warehouse_scenario, "Proszę opowiedzieć o sytuacji, w której musiał Pan przeprowadzić trudną rozmowę z pracownikiem.")
    assert concrete.revealed_fact_ids == ["lid_s"]


def test_topic_answers_and_intro(dev_scenario):
    assert _ask(dev_scenario, "Proszę opowiedzieć o sobie.").answer == dev_scenario.persona.intro
    salary = _ask(dev_scenario, "Jakie są Pana oczekiwania finansowe?")
    assert "6000 zł" in salary.answer


def test_fallback_answer_when_nothing_matches(cs_scenario):
    reply = _ask(cs_scenario, "Jak wygląda u Pani idealny poniedziałek?")
    assert reply.revealed_fact_ids == []
    assert any(reply.answer.startswith(f) for f in cs_scenario.persona.fallback_answers)


def test_trap_is_mentioned_once_on_trigger(cs_scenario, player):
    turns = player(cs_scenario, ["Jak wygląda Pani dyspozycyjność w weekendy?", "A grafik zmianowy?"])
    assert turns[0].trap_ids == ["pulapka_dzieci"]
    assert "dwójkę dzieci" in turns[0].answer
    assert turns[1].trap_ids == []


def test_offline_candidate_is_deterministic(cs_scenario, player):
    questions = ["Proszę opowiedzieć o sobie.", "Jak radzi sobie Pani z trudnymi klientami?", "Co wtedy Pani zrobiła?"]
    assert [t.answer for t in player(cs_scenario, questions)] == [t.answer for t in player(cs_scenario, questions)]


def test_ai_question_gets_transparent_answer(cs_scenario):
    reply = _ask(cs_scenario, "Czy jesteś sztuczną inteligencją?")
    assert "symulowanym kandydatem" in reply.answer


# --- ścieżka AI ---
def test_ai_reply_unknown_fact_ids_are_dropped(cs_scenario):
    reply = CandidateReplyAI(answer="Opowiem o klientce…", revealed_fact_ids=["kom_s", "zmyslony_fakt", "kom_s"], mentioned_trap_ids=["nie_ma"], emotion="pewna_siebie")
    validated = validate_ai_reply(reply, cs_scenario, [])
    assert validated.revealed_fact_ids == ["kom_s"]
    assert validated.trap_ids == []
    assert validated.source == "ai"
    assert "zmyslony_fakt" in (validated.note or "")


def test_ai_reply_already_revealed_ids_not_counted_twice(cs_scenario, turn_factory):
    history = [turn_factory(0, "Pytanie", revealed=["kom_s"])]
    reply = CandidateReplyAI(answer="Jeszcze raz…", revealed_fact_ids=["kom_s", "kom_t"], mentioned_trap_ids=[], emotion="neutralna")
    assert validate_ai_reply(reply, cs_scenario, history).revealed_fact_ids == ["kom_t"]


def test_ai_candidate_used_when_available(cs_scenario, fake_llm_cls):
    fake = fake_llm_cls({CandidateReplyAI: CandidateReplyAI(answer="Dzień dobry, jestem Joanna.", revealed_fact_ids=[], mentioned_trap_ids=["pulapka_dzieci"], emotion="entuzjastyczna")})
    q = "Proszę się przedstawić."
    reply = candidate_reply(cs_scenario, q, rule_analysis(q, has_history=False), [], fake)
    assert reply.source == "ai" and reply.trap_ids == ["pulapka_dzieci"]
    call = fake.calls[0]
    assert "<transkrypt>" in call["prompt"] and "<pytanie_rekrutera>" in call["prompt"]
    assert "kom_s" in call["system"]  # ukryte fakty idą w prompt systemowy
    assert "DANE" in call["system"]  # obrona przed prompt injection


def test_ai_prompt_masks_pii(cs_scenario, fake_llm_cls):
    fake = fake_llm_cls({CandidateReplyAI: CandidateReplyAI(answer="Dobrze.", revealed_fact_ids=[], mentioned_trap_ids=[], emotion="neutralna")})
    q = "Proszę przesłać CV na anna.nowak@example.com"
    candidate_reply(cs_scenario, q, rule_analysis(q, has_history=False), [], fake)
    assert "anna.nowak@example.com" not in fake.calls[0]["prompt"]


def test_fallback_to_offline_when_ai_unavailable(cs_scenario, fake_llm_cls):
    fake = fake_llm_cls({CandidateReplyAI: LLMUnavailable("Brak połączenia z API modelu")})
    q = "Proszę opowiedzieć o sytuacji, w której rozmawiała Pani z bardzo zdenerwowanym klientem."
    reply = candidate_reply(cs_scenario, q, rule_analysis(q, has_history=False), [], fake)
    assert reply.source == "offline"
    assert reply.revealed_fact_ids == ["kom_s"]
    assert "Model niedostępny" in reply.note


def test_empty_ai_answer_falls_back(cs_scenario, fake_llm_cls):
    fake = fake_llm_cls({CandidateReplyAI: CandidateReplyAI(answer="   ", revealed_fact_ids=[], mentioned_trap_ids=[], emotion="neutralna")})
    q = "Proszę opowiedzieć o sobie."
    reply = candidate_reply(cs_scenario, q, rule_analysis(q, has_history=False), [], fake)
    assert reply.source == "offline" and reply.answer == cs_scenario.persona.intro
