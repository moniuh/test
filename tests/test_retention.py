import pytest

from asystent.assistant import DomainError
from asystent.config import Settings


def test_purge_removes_old_logs_and_closed_escalations(make_assistant, clock):
    assistant = make_assistant(settings=Settings(db_path=":memory:", retention_days=90))
    old = assistant.ask("Kiedy wypłata?")
    _, closed = assistant.escalate(old.session_token, interaction_id=old.interaction_id)
    assistant.close(closed.id)
    _, still_open = assistant.escalate(None, question="Czy mogę pracować z Hiszpanii?")

    clock.advance(days=100)
    recent = assistant.ask("Ile mam dni urlopu?")  # pierwsze pytanie danego dnia uruchamia czyszczenie

    assert assistant.store.get_interaction(old.interaction_id) is None
    assert assistant.store.get_interaction(recent.interaction_id) is not None
    remaining = {e.id for e in assistant.escalations()}
    assert closed.id not in remaining
    assert still_open.id in remaining  # otwarte zgłoszenie czeka na odpowiedź HR
    assert assistant.retention_info()["last_purge"].startswith("2027-02-08")


def test_manual_purge_with_custom_days(assistant, clock):
    assistant.ask("Kiedy wypłata?")
    clock.advance(days=10)
    assert assistant.purge(days=30)["interactions"] == 0
    result = assistant.purge(days=7)
    assert result["interactions"] == 1
    assert result["days"] == 7
    with pytest.raises(DomainError):
        assistant.purge(days=0)


def test_retention_days_from_settings(make_assistant, clock):
    assistant = make_assistant(settings=Settings(db_path=":memory:", retention_days=5))
    assistant.ask("Kiedy wypłata?")
    clock.advance(days=6)
    assert assistant.purge()["interactions"] == 1


def test_settings_from_env(monkeypatch):
    monkeypatch.setenv("HR_RETENTION_DAYS", "30")
    monkeypatch.setenv("HR_PANEL_TOKEN", "tajny")
    monkeypatch.setenv("HR_MIN_COVERAGE", "abc")  # zła wartość → domyślna
    settings = Settings.from_env()
    assert settings.retention_days == 30
    assert settings.hr_token == "tajny"
    assert settings.min_coverage == 0.5


def test_session_is_pseudonymous(assistant):
    answer = assistant.ask("Kiedy wypłata?")
    row = assistant.store.get_interaction(answer.interaction_id)
    assert row["session_ref"] != answer.session_token  # w bazie tylko skrót tokenu
    assert len(row["session_ref"]) == 16
    again = assistant.ask("Ile mam urlopu?", answer.session_token)
    assert again.session_token == answer.session_token
    assert assistant.store.get_interaction(again.interaction_id)["session_ref"] == row["session_ref"]
