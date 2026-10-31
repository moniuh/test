from datetime import date, timedelta

import pytest

from conftest import T, add_rule, make_employee, make_training
from szkolenia.reminders import ReminderContext, ReminderSettings, plan_messages, reminder_stage

SETTINGS = ReminderSettings()


def ctx(due: date, manager: bool = True) -> ReminderContext:
    return ReminderContext(
        assignment_id=7, employee_name="Anna Nowak", employee_email="anna@firma-demo.example",
        department="Magazyn", training_title="RODO", due_date=due,
        manager_name="Jan Kierownik" if manager else None, manager_email="jan@firma-demo.example" if manager else None,
    )


@pytest.mark.parametrize(("days_left", "stage"), [
    (31, None), (30, 30), (8, 30), (7, 7), (2, 7), (1, 1), (0, 1), (-1, None),
])
def test_reminder_stage(days_left, stage):
    assert reminder_stage(days_left, SETTINGS.offsets) == stage


def test_plan_before_due_only_employee_reminder():
    msgs = plan_messages(ctx(T + timedelta(days=5)), T, SETTINGS)
    assert [(m.kind, m.dedup_key, m.recipient_email) for m in msgs] == [("reminder", "a7:przed:7", "anna@firma-demo.example")]
    assert "za 5 dni" in msgs[0].body and "„RODO”" in msgs[0].subject


def test_plan_on_due_day_says_today():
    msgs = plan_messages(ctx(T), T, SETTINGS)
    assert msgs[0].dedup_key == "a7:przed:1" and "dziś" in msgs[0].body


def test_plan_after_due_escalates_to_manager():
    msgs = plan_messages(ctx(T - timedelta(days=1)), T, SETTINGS)
    kinds = {m.kind: m for m in msgs}
    assert set(kinds) == {"overdue", "escalation"}
    assert kinds["escalation"].recipient_email == "jan@firma-demo.example"
    assert "1 dzień" in kinds["escalation"].body and "Anna Nowak" in kinds["escalation"].subject


def test_escalation_without_manager_goes_to_hr():
    msgs = plan_messages(ctx(T - timedelta(days=3), manager=False), T, SETTINGS)
    escalation = next(m for m in msgs if m.kind == "escalation")
    assert escalation.recipient_email == SETTINGS.hr_email


def test_escalation_delay_is_configurable():
    settings = ReminderSettings(escalate_after_days=7)
    kinds = [m.kind for m in plan_messages(ctx(T - timedelta(days=3)), T, settings)]
    assert kinds == ["overdue"]


@pytest.fixture
def overdue_setup(service):
    training = make_training(service, title="RODO", category="RODO")
    add_rule(service, training["id"], due_days=14)
    manager, _ = make_employee(service, full_name="Jan Kierownik", email="jan@firma-demo.example",
                               department="Zarząd", position="Prezes")
    employee, created = make_employee(service, full_name="Anna Nowak", email="anna@firma-demo.example",
                                      manager_id=manager["id"], hire_date=T)
    return service, created[0]


def test_queue_reminders_is_idempotent(overdue_setup):
    service, assignment = overdue_setup
    due = date.fromisoformat(assignment["due_date"])
    day = due - timedelta(days=5)
    first = service.queue_reminders(day)
    assert first >= 1
    assert service.queue_reminders(day) == 0                     # drugi przebieg tego samego dnia
    assert service.queue_reminders(day + timedelta(days=1)) == 0  # ten sam etap następnego dnia
    keys = [r["dedup_key"] for r in service.store.all("SELECT dedup_key FROM outbox")]
    assert len(keys) == len(set(keys))


def test_escalation_after_due_date_queued_once(overdue_setup):
    service, assignment = overdue_setup
    due = date.fromisoformat(assignment["due_date"])
    service.queue_reminders(due + timedelta(days=2))
    service.queue_reminders(due + timedelta(days=3))
    escalations = service.list_outbox()
    esc = [m for m in escalations if m["kind"] == "escalation" and m["assignment_id"] == assignment["id"]]
    assert len(esc) == 1 and esc[0]["recipient_email"] == "jan@firma-demo.example"
    overdue = [m for m in escalations if m["kind"] == "overdue" and m["assignment_id"] == assignment["id"]]
    assert len(overdue) == 1


def test_missed_stages_are_not_sent_retroactively(overdue_setup):
    service, assignment = overdue_setup
    due = date.fromisoformat(assignment["due_date"])
    service.queue_reminders(due)  # pierwszy przebieg dopiero w dniu terminu
    keys = {m["dedup_key"] for m in service.list_outbox() if m["assignment_id"] == assignment["id"]}
    assert keys == {f"a{assignment['id']}:przed:1"}


def test_completed_assignment_gets_no_reminders(overdue_setup):
    service, assignment = overdue_setup
    service.record_external_completion(assignment["id"], T, "papier", today=T)
    due = date.fromisoformat(assignment["due_date"])
    service.queue_reminders(due + timedelta(days=5))
    assert [m for m in service.list_outbox() if m["assignment_id"] == assignment["id"]] == []


def test_send_stub_marks_messages_sent(overdue_setup):
    service, assignment = overdue_setup
    service.queue_reminders(date.fromisoformat(assignment["due_date"]) + timedelta(days=1))
    pending = len(service.list_outbox("pending"))
    assert pending >= 2
    assert service.send_pending() == pending
    assert service.list_outbox("pending") == []
    assert all(m["sent_at"] for m in service.list_outbox("sent"))
    assert service.send_pending() == 0
