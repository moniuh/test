import pytest

from asystent.assistant import DomainError, ForbiddenError, NotFoundError
from asystent.safety import mask_pii


def test_escalation_lifecycle_from_interaction(assistant):
    answer = assistant.ask("Czy firma refunduje kryptowaluty?")
    token = answer.session_token
    returned_token, esc = assistant.escalate(token, interaction_id=answer.interaction_id, note="Pilne, proszę o info")
    assert returned_token == token
    assert esc.status == "nowe"
    assert esc.question == "Czy firma refunduje kryptowaluty?"
    assert esc.context["status"] == "no_basis"
    assert esc.note == "Pilne, proszę o info"

    replied = assistant.reply(esc.id, "Nie refundujemy kryptowalut.")
    assert replied.status == "odpowiedziane" and replied.hr_reply == "Nie refundujemy kryptowalut."
    mine = assistant.my_escalations(token)
    assert [e.hr_reply for e in mine] == ["Nie refundujemy kryptowalut."]

    closed = assistant.close(esc.id)
    assert closed.status == "zamknięte" and closed.closed_at
    with pytest.raises(DomainError, match="zamknięte"):
        assistant.reply(esc.id, "Jeszcze raz")
    assert [e.id for e in assistant.escalations("zamknięte")] == [esc.id]
    assert assistant.escalations("nowe") == []


def test_reply_and_close_in_one_step(assistant):
    _, esc = assistant.escalate(None, question="Czy mogę przywieźć psa do biura?")
    closed = assistant.reply(esc.id, "Tak, w piątki.", close=True)
    assert closed.status == "zamknięte" and closed.closed_at and closed.replied_at


def test_escalation_from_free_text_masks_personal_and_health_data(assistant):
    _, esc = assistant.escalate(None, question="Mam depresję, kontakt jan@firma.pl", contact="tel. wewn. 123")
    assert "depresj" not in esc.question and "jan@firma.pl" not in esc.question
    assert esc.contact == "tel. wewn. 123"  # kontakt podaje się dobrowolnie i świadomie


def test_escalation_requires_question_or_interaction(assistant):
    with pytest.raises(DomainError):
        assistant.escalate(None)


def test_cannot_escalate_or_rate_someone_elses_interaction(assistant):
    answer = assistant.ask("Kiedy wypłata?")
    other = assistant.ask("Kiedy wypłata?").session_token
    with pytest.raises(ForbiddenError):
        assistant.escalate(other, interaction_id=answer.interaction_id)
    with pytest.raises(ForbiddenError):
        assistant.feedback(answer.interaction_id, other, "down")
    with pytest.raises(NotFoundError):
        assistant.feedback(9999, other, "up")


def test_feedback_is_stored(assistant):
    answer = assistant.ask("Kiedy wypłata?")
    assistant.feedback(answer.interaction_id, answer.session_token, "down", "Za mało szczegółów, pisz na a@b.pl")
    row = assistant.store.get_interaction(answer.interaction_id)
    assert row["feedback"] == "down"
    assert "a@b.pl" not in row["feedback_comment"]
    with pytest.raises(DomainError):
        assistant.feedback(answer.interaction_id, answer.session_token, "meh")


def test_my_escalations_with_invalid_token_is_empty(assistant):
    assert assistant.my_escalations("krótki") == []
    assert assistant.my_escalations(None) == []


def test_knowledge_gaps_group_by_keyword_and_suggest_document(assistant):
    for q in ["Czy firma refunduje kryptowaluty?", "Czy mogę dostać wypłatę w kryptowalucie?",
              "Jaka dieta przysługuje na delegacji zagranicznej?", "Kto zwraca koszty delegacji?",
              "Czy delegacja w weekend jest płatna?"]:
        assistant.ask(q)
    good = assistant.ask("Jakie są godziny wspólnej dostępności?")
    assistant.feedback(good.interaction_id, good.session_token, "down")
    assistant.ask("Ile zarabia Kowalski?")  # przekierowanie wrażliwe — to nie luka w wiedzy
    assistant.ask("Kiedy wypłata?")  # dobra odpowiedź — nie luka

    report = assistant.knowledge_gaps(days=30)
    assert report.total_questions == 8
    assert report.gap_questions == 6
    by_keyword = {g.keyword: g for g in report.groups}
    delegation = next(g for k, g in by_keyword.items() if k.startswith("delegac"))
    assert delegation.count == 3
    assert delegation.no_basis == 3
    assert "Brak dokumentu" in delegation.suggestion
    crypto = next(g for k, g in by_keyword.items() if k.startswith("kryptowalut"))
    assert crypto.count == 2
    thumbs = next(g for g in report.groups if g.thumbs_down)
    assert thumbs.suggested_document  # temat istnieje w dokumentach — podpowiedź uzupełnienia
    assert "Uzupełnij dokument" in thumbs.suggestion


def test_gap_examples_are_masked(assistant):
    assistant.ask("Czy pani Anna Nowak dostaje dietę za delegację, jej mail anna@firma.pl?")
    report = assistant.knowledge_gaps()
    text = " ".join(e for g in report.groups for e in g.examples)
    assert "anna@firma.pl" not in text and "Nowak" not in text
    assert mask_pii("anna@firma.pl") == "[e-mail]"


def test_stats(assistant):
    a = assistant.ask("Kiedy wypłata?")
    assistant.ask("Czy firma refunduje kryptowaluty?")
    assistant.ask("Mój szef mnie mobbinguje")
    assistant.feedback(a.interaction_id, a.session_token, "up")
    assistant.escalate(a.session_token, interaction_id=a.interaction_id)
    stats = assistant.stats()
    assert stats.total_questions == 3
    assert stats.by_status == {"answered": 1, "no_basis": 1, "sensitive": 1}
    assert stats.by_source == {"offline": 3}
    assert stats.feedback == {"up": 1, "down": 0}
    assert stats.escalations == {"nowe": 1}
    assert stats.answered_rate == pytest.approx(1 / 3, abs=0.001)
    assert stats.top_documents[0]["count"] >= 1
    assert stats.per_day[-1] == {"date": "2026-10-31", "count": 3}
    assert stats.documents_current == 8 and stats.documents_total == 9
