from datetime import date, timedelta

import pytest

from conftest import T, add_rule, approve_generated, make_employee, make_training
from szkolenia.compliance import add_months
from szkolenia.rules import EmployeeSpec, RuleSpec, initial_due_date, required_trainings, rule_matches
from szkolenia.service import Conflict

EMP = EmployeeSpec(id=1, department="Magazyn", position="Magazynier", hire_date=date(2026, 3, 1))


@pytest.mark.parametrize(("department", "position", "expected"), [
    (None, None, True),                       # wszyscy
    ("Magazyn", None, True),                  # po dziale
    ("  magazyn ", None, True),               # bez wielkości liter i nadmiarowych spacji
    ("Sprzedaż", None, False),
    (None, "Magazynier", True),               # po stanowisku
    (None, "Kierownik magazynu", False),
    ("Magazyn", "Magazynier", True),          # oba kryteria muszą pasować
    ("Magazyn", "Operator wózka widłowego", False),
])
def test_rule_matches(department, position, expected):
    assert rule_matches(RuleSpec(1, 10, department, position, 30), EMP) is expected


def test_required_trainings_picks_shortest_due():
    rules = [RuleSpec(1, 10, None, None, 30), RuleSpec(2, 10, "Magazyn", None, 7), RuleSpec(3, 11, "Sprzedaż", None, 5)]
    chosen = required_trainings(rules, EMP)
    assert set(chosen) == {10}
    assert chosen[10].id == 2


def test_initial_due_date_new_hire_vs_existing_staff():
    hire = date(2020, 1, 10)
    assert initial_due_date(hire, 14, T, on_hire=True) == date(2020, 1, 24)
    # Reguła dodana później: termin liczony od dziś, nie wstecz od dawnej daty zatrudnienia
    assert initial_due_date(hire, 14, T, on_hire=False) == T + timedelta(days=14)
    future_hire = T + timedelta(days=10)
    assert initial_due_date(future_hire, 0, T, on_hire=False) == future_hire


def test_new_employee_gets_matching_trainings_with_due_from_hire_date(service):
    rodo = make_training(service, title="RODO", category="RODO")
    bhp = make_training(service, title="BHP robotnicze", category="BHP", validity_months=36)
    sales = make_training(service, title="Szkolenie handlowe", category="Inne")
    add_rule(service, rodo["id"], due_days=14)
    add_rule(service, bhp["id"], department="Magazyn", due_days=365)
    add_rule(service, sales["id"], department="Sprzedaż")
    hire = T - timedelta(days=3)
    employee, created = make_employee(service, hire_date=hire)
    due = {a["training_title"]: date.fromisoformat(a["due_date"]) for a in created}
    assert due == {"RODO": hire + timedelta(days=14), "BHP robotnicze": hire + timedelta(days=365)}
    assert all(a["reason"] == "new_hire" for a in created)


def test_new_rule_assigns_existing_employees_once(service):
    training = make_training(service)
    employee, created = make_employee(service, hire_date=date(2019, 5, 1))
    assert created == []
    _, result = service.create_rule({"training_id": training["id"], "department": "Magazyn", "position": None,
                                     "due_days": 21}, T)
    assert len(result["created"]) == 1
    assert result["created"][0]["due_date"] == (T + timedelta(days=21)).isoformat()
    assert service.sync_assignments(T)["created"] == []        # idempotentnie
    assert service.sync_assignments(T + timedelta(days=5))["created"] == []
    count = service.store.scalar("SELECT COUNT(*) FROM assignments WHERE employee_id=?", (employee["id"],))
    assert count == 1


def test_changing_department_assigns_new_and_cancels_unneeded(service):
    wh = make_training(service, title="Instruktaż magazyn", category="BHP", mode="classroom", validity_months=None)
    office = make_training(service, title="BHP biuro", category="BHP", validity_months=72)
    add_rule(service, wh["id"], department="Magazyn", due_days=0)
    add_rule(service, office["id"], department="Administracja", due_days=60)
    employee, created = make_employee(service)
    assert [a["training_title"] for a in created] == ["Instruktaż magazyn"]
    _, result = service.update_employee(employee["id"], {"department": "Administracja"}, T)
    assert [a["training_title"] for a in result["created"]] == ["BHP biuro"]
    assert [a["training_id"] for a in result["cancelled"]] == [wh["id"]]


def test_deactivated_employee_has_open_assignments_cancelled(service):
    training = make_training(service)
    add_rule(service, training["id"])
    employee, _ = make_employee(service)
    service.update_employee(employee["id"], {"active": False}, T)
    assert service.store.scalar("SELECT COUNT(*) FROM assignments WHERE status='open'") == 0
    assert service.run_daily(T)["created"] == 0


def test_classroom_training_completed_by_trainer_confirmation(service):
    training = make_training(service, title="Instruktaż stanowiskowy", mode="classroom", validity_months=None,
                             material="Instruktaż prowadzi przełożony na stanowisku pracy.")
    add_rule(service, training["id"], department="Magazyn", due_days=0)
    _, created = make_employee(service)
    assignment_id = created[0]["id"]
    with pytest.raises(Conflict):
        service.start_attempt(assignment_id)  # bez testu online
    cert = service.confirm_attendance(assignment_id, "Ryszard Bąk", T, today=T)
    assert cert["method"] == "confirmation" and cert["confirmed_by"] == "Ryszard Bąk"
    assert cert["valid_until"] is None
    assert service.get_assignment(assignment_id)["status"] == "completed"


def test_confirmation_rejected_for_elearning(service):
    training = make_training(service)
    add_rule(service, training["id"])
    _, created = make_employee(service)
    with pytest.raises(Conflict):
        service.confirm_attendance(created[0]["id"], "Ryszard Bąk", T, today=T)


def test_renewal_created_in_expiry_window_and_only_once(service):
    training = make_training(service, validity_months=12)
    add_rule(service, training["id"], due_days=14)
    employee, created = make_employee(service, hire_date=T)
    completed_on = T + timedelta(days=5)
    service.record_external_completion(created[0]["id"], completed_on, "papier", today=completed_on)
    valid_until = add_months(completed_on, 12)

    before_window = valid_until - timedelta(days=31)
    assert service.sync_assignments(before_window)["renewals"] == []

    in_window = valid_until - timedelta(days=30)
    renewals = service.sync_assignments(in_window)["renewals"]
    assert len(renewals) == 1
    assert renewals[0]["reason"] == "renewal" and renewals[0]["due_date"] == valid_until.isoformat()
    assert service.sync_assignments(in_window)["renewals"] == []

    after_expiry = valid_until + timedelta(days=3)
    assert service.sync_assignments(after_expiry)["renewals"] == []
    open_count = service.store.scalar(
        "SELECT COUNT(*) FROM assignments WHERE employee_id=? AND status='open'", (employee["id"],))
    assert open_count == 1
    cell = service.employee_trainings(employee["id"], after_expiry)["items"][0]
    assert cell["status"] == "overdue"


def test_renewal_after_expiry_when_daily_job_was_not_running(service):
    training = make_training(service, validity_months=12)
    add_rule(service, training["id"])
    employee, created = make_employee(service)
    service.record_external_completion(created[0]["id"], T, "papier", today=T)
    late = add_months(T, 12) + timedelta(days=40)
    renewals = service.sync_assignments(late)["renewals"]
    assert len(renewals) == 1 and renewals[0]["due_date"] == add_months(T, 12).isoformat()


def test_manual_assignment_and_duplicate_conflict(service):
    training = make_training(service)
    employee, _ = make_employee(service)
    a = service.create_manual_assignment(employee["id"], training["id"], T + timedelta(days=10), T)
    assert a["reason"] == "manual"
    with pytest.raises(Conflict):
        service.create_manual_assignment(employee["id"], training["id"], T + timedelta(days=10), T)
    # ręczne przypisanie nie jest anulowane przez synchronizację, mimo braku reguły
    assert service.sync_assignments(T)["cancelled"] == []


def test_employee_view_shows_test_availability(service):
    training = make_training(service)
    add_rule(service, training["id"])
    employee, _ = make_employee(service)
    item = service.employee_trainings(employee["id"], T)["items"][0]
    assert item["can_take_test"] is False and item["approved_questions"] == 0
    approve_generated(service, training["id"], 3)
    item = service.employee_trainings(employee["id"], T)["items"][0]
    assert item["can_take_test"] is True and item["status"] == "incomplete"
