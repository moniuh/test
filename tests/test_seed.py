from datetime import date

import pytest
from fastapi.testclient import TestClient

from szkolenia.api import create_app
from szkolenia.seed import email_for

TODAY = date(2026, 10, 31)


@pytest.fixture(scope="module")
def demo():
    return TestClient(create_app(db_path=":memory:", llm_factory=lambda: None, clock=lambda: TODAY, seed=True))


def test_demo_has_ten_employees_in_three_departments(demo):
    employees = demo.get("/api/employees").json()
    assert len(employees) == 10
    assert demo.get("/api/departments").json() == ["Administracja", "Magazyn", "Sprzedaż"]
    assert all(e["email"].endswith("@firma-demo.example") for e in employees)


def test_demo_shows_every_status(demo):
    matrix = demo.get("/api/report").json()
    statuses = {c["status"] for row in matrix["rows"] for c in row["cells"].values()}
    assert statuses == {"valid", "expiring", "overdue", "incomplete", "not_applicable"}
    dash = demo.get("/api/dashboard").json()
    assert dash["counts"]["overdue"] >= 3 and dash["counts"]["expiring"] >= 2 and dash["counts"]["incomplete"] >= 3
    assert 50 < dash["compliance_percent"] < 95
    reasons = {o["reason"] for o in dash["overdue"]}
    assert reasons == {"ważność minęła", "termin minął"}


def test_demo_trainings_and_questions(demo):
    trainings = {t["title"]: t for t in demo.get("/api/trainings").json()}
    assert len(trainings) == 7
    assert trainings["BHP okresowe — stanowiska robotnicze"]["validity_months"] == 36
    assert trainings["BHP okresowe — stanowiska administracyjno-biurowe"]["validity_months"] == 72
    assert trainings["Ochrona danych osobowych (RODO)"]["validity_months"] == 12
    assert trainings["Instruktaż stanowiskowy BHP — magazyn"]["mode"] == "classroom"
    whistle = trainings["Ochrona sygnalistów — procedura zgłoszeń wewnętrznych"]
    assert whistle["question_counts"]["approved"] == 0 and whistle["question_counts"]["draft"] > 0
    assert trainings["Ochrona danych osobowych (RODO)"]["question_counts"]["approved"] >= 6


def test_demo_outbox_contains_escalations(demo):
    kinds = {m["kind"] for m in demo.get("/api/outbox").json()}
    assert kinds == {"reminder", "overdue", "escalation"}
    assert demo.post("/api/jobs/daily").json()["reminders_queued"] == 0


def test_demo_certificates_verify(demo):
    certs = demo.get("/api/certificates").json()
    assert len(certs) > 20
    assert demo.get(f"/api/verify/{certs[0]['verification_code']}").status_code == 200


def test_email_transliteration():
    assert email_for("Paweł Zając") == "pawel.zajac@firma-demo.example"
    assert email_for("Michał Sikora") == "michal.sikora@firma-demo.example"
