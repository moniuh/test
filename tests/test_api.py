from datetime import timedelta

import pytest
from fastapi.testclient import TestClient

from conftest import MATERIAL, FakeLLM, T
from szkolenia.api import create_app
from szkolenia.llm import LLMUnavailable
from szkolenia.quizgen import AIQuestion, AIQuestionSet


@pytest.fixture
def client():
    return TestClient(create_app(db_path=":memory:", llm_factory=lambda: None, clock=lambda: T, seed=False))


def setup_training(client, **overrides) -> dict:
    body = {"title": "Ochrona sygnalistów", "category": "Sygnaliści", "validity_months": 12, "pass_threshold": 80,
            "mode": "elearning", "material": MATERIAL, "questions_per_test": 5}
    body.update(overrides)
    r = client.post("/api/trainings", json=body)
    assert r.status_code == 201, r.text
    return r.json()


def add_employee(client, **overrides) -> dict:
    body = {"full_name": "Anna Nowak", "email": "anna.nowak@firma-demo.example", "department": "Magazyn",
            "position": "Magazynier", "hire_date": T.isoformat()}
    body.update(overrides)
    r = client.post("/api/employees", json=body)
    assert r.status_code == 201, r.text
    return r.json()


def test_health_and_index(client):
    r = client.get("/api/health")
    assert r.json()["ai_mode"] == "offline" and r.json()["today"] == T.isoformat()
    page = client.get("/")
    assert page.status_code == 200 and "Szkolenia obowiązkowe" in page.text
    assert client.get("/static/app.js").status_code == 200


def test_employee_validation_errors_in_polish(client):
    r = client.post("/api/employees", json={"full_name": "A", "email": "zly-adres", "department": "Magazyn",
                                            "position": "Magazynier"})
    assert r.status_code == 422
    body = r.json()
    assert body["detail"] == "Nieprawidłowe dane wejściowe."
    messages = {e["field"]: e["message"] for e in body["errors"]}
    assert messages["email"] == "Nieprawidłowy adres e-mail."
    assert messages["hire_date"] == "To pole jest wymagane."
    assert "za krótki" in messages["full_name"]


def test_create_employee_auto_assigns(client):
    training = setup_training(client)
    r = client.post("/api/rules", json={"training_id": training["id"], "department": "Magazyn", "due_days": 14})
    assert r.status_code == 201 and r.json()["sync"]["created"] == []
    created = add_employee(client)
    assert [(a["training_title"], a["due_date"]) for a in created["assignments_created"]] == \
           [("Ochrona sygnalistów", (T + timedelta(days=14)).isoformat())]
    dup = client.post("/api/employees", json={"full_name": "Anna Druga", "email": "anna.nowak@firma-demo.example",
                                              "department": "Magazyn", "position": "Magazynier",
                                              "hire_date": T.isoformat()})
    assert dup.status_code == 409 and "e-mail" in dup.json()["detail"]
    employees = client.get("/api/employees").json()
    assert employees[0]["compliance_percent"] == 0


def test_not_found_messages_in_polish(client):
    assert client.get("/api/employees/999").json()["detail"] == "Nie znaleziono pracownika."
    assert client.get("/api/trainings/999").status_code == 404
    assert client.get("/api/nie-ma-takiej-sciezki").json()["detail"] == "Nie znaleziono zasobu."
    assert client.get("/api/verify/AAAA-BBBB-CCCC").status_code == 404
    assert client.get("/verify/AAAA-BBBB-CCCC").status_code == 404


def test_training_validation(client):
    r = client.post("/api/trainings", json={"title": "X", "category": "BHP", "mode": "zdalne", "pass_threshold": 150})
    assert r.status_code == 422
    fields = {e["field"] for e in r.json()["errors"]}
    assert {"title", "mode", "pass_threshold"} <= fields


def test_full_flow_generate_approve_test_certificate_verify(client):
    training = setup_training(client)
    client.post("/api/rules", json={"training_id": training["id"], "due_days": 30})
    employee = add_employee(client)["employee"]

    gen = client.post(f"/api/trainings/{training['id']}/questions/generate", json={"count": 5}).json()
    assert gen["source"] == "offline" and len(gen["created"]) == 5
    assert all(q["status"] == "draft" for q in gen["created"])

    my = client.get(f"/api/employees/{employee['id']}/trainings").json()["items"][0]
    assert my["can_take_test"] is False
    assignment_id = my["assignment_id"]
    assert client.post(f"/api/assignments/{assignment_id}/attempts").status_code == 409

    for q in gen["created"]:
        r = client.post(f"/api/questions/{q['id']}/approve", json={"reviewer": "Joanna Pawlak"})
        assert r.json()["status"] == "approved" and r.json()["reviewed_by"] == "Joanna Pawlak"

    quiz = client.post(f"/api/assignments/{assignment_id}/attempts")
    assert quiz.status_code == 201
    quiz = quiz.json()
    assert len(quiz["questions"]) == 5 and "correct_index" not in quiz["questions"][0]

    attempt = client.get(f"/api/attempts/{quiz['attempt_id']}").json()
    snap = {q["id"]: q for q in attempt["questions"]}
    answers = []
    for q in quiz["questions"]:
        correct_text = snap[q["id"]]["options"][snap[q["id"]]["correct_index"]]
        answers.append({"question_id": q["id"], "option_index": q["options"].index(correct_text)})
    result = client.post(f"/api/attempts/{quiz['attempt_id']}/submit", json={"answers": answers}).json()
    assert result["passed"] and result["percent"] == 100
    cert = result["certificate"]

    page = client.get(f"/certificates/{cert['id']}")
    assert page.status_code == 200 and cert["number"] in page.text and f"/verify/{cert['verification_code']}" in page.text
    verified = client.get(f"/api/verify/{cert['verification_code']}").json()
    assert verified["is_valid"] and verified["full_name"] == "Anna Nowak"
    assert "Zaświadczenie istnieje" in client.get(f"/verify/{cert['verification_code']}").text

    again = client.post(f"/api/attempts/{quiz['attempt_id']}/submit", json={"answers": answers})
    assert again.status_code == 409


def test_submit_validation(client):
    r = client.post("/api/attempts/1/submit", json={"answers": [{"question_id": 1, "option_index": 0},
                                                                {"question_id": 1, "option_index": 1}]})
    assert r.status_code == 422 and "tylko jedną" in r.json()["errors"][0]["message"]
    r = client.post("/api/attempts/1/submit", json={"answers": [{"question_id": 1, "option_index": 7}]})
    assert r.status_code == 422


def test_question_edit_and_quote_check(client):
    training = setup_training(client)
    q = client.post(f"/api/trainings/{training['id']}/questions/generate", json={"count": 1}).json()["created"][0]
    body = {"text": q["text"], "options": q["options"], "correct_index": q["correct_index"],
            "explanation": q["explanation"], "source_quote": "Zmyślony cytat, którego nie ma w materiale szkolenia."}
    edited = client.put(f"/api/questions/{q['id']}", json=body).json()
    assert edited["quote_verified"] is False and edited["status"] == "draft"
    r = client.post(f"/api/questions/{q['id']}/approve")
    assert r.status_code == 409 and "Cytat" in r.json()["detail"]
    bad = client.put(f"/api/questions/{q['id']}", json={**body, "options": ["a", "b", "c"]})
    assert bad.status_code == 422
    assert client.post(f"/api/questions/{q['id']}/reject").json()["status"] == "rejected"
    assert client.delete(f"/api/questions/{q['id']}").status_code == 204
    assert client.get(f"/api/trainings/{training['id']}/questions").json() == []


def test_classroom_confirmation_endpoint(client):
    training = setup_training(client, title="Instruktaż stanowiskowy", mode="classroom", validity_months=None)
    client.post("/api/rules", json={"training_id": training["id"], "department": "Magazyn", "due_days": 0})
    created = add_employee(client)["assignments_created"][0]
    future = client.post(f"/api/assignments/{created['id']}/confirm",
                         json={"trainer": "Ryszard Bąk", "session_date": (T + timedelta(days=1)).isoformat()})
    assert future.status_code == 422 and "przyszłości" in future.json()["detail"]
    ok = client.post(f"/api/assignments/{created['id']}/confirm", json={"trainer": "Ryszard Bąk", "session_date": T.isoformat()})
    assert ok.status_code == 200 and ok.json()["method"] == "confirmation"
    assert client.post(f"/api/assignments/{created['id']}/attempts").status_code == 409


def test_report_csv_print_and_dashboard(client):
    training = setup_training(client)
    client.post("/api/rules", json={"training_id": training["id"], "due_days": 7})
    add_employee(client, hire_date=(T - timedelta(days=30)).isoformat())
    add_employee(client, full_name="Jan Kowalczyk", email="jan@firma-demo.example", department="Sprzedaż",
                 hire_date=T.isoformat())
    dash = client.get("/api/dashboard").json()
    assert dash["counts"]["overdue"] == 1 and dash["counts"]["incomplete"] == 1 and dash["compliance_percent"] == 0
    assert dash["overdue"][0]["full_name"] == "Anna Nowak" and dash["overdue"][0]["days"] == 23
    csv = client.get("/api/report.csv?department=Sprzedaż")
    assert csv.headers["content-type"].startswith("text/csv")
    assert "attachment" in csv.headers["content-disposition"]
    lines = csv.content.decode("utf-8-sig").strip().splitlines()
    assert len(lines) == 2 and "Jan Kowalczyk" in lines[1] and "Anna" not in csv.text
    printable = client.get("/report/print?department=Magazyn")
    assert printable.status_code == 200 and "Anna Nowak" in printable.text and "dział: Magazyn" in printable.text
    report = client.get("/api/report").json()
    assert len(report["rows"]) == 2 and report["trainings"][0]["id"] == training["id"]


def test_outbox_and_daily_job_idempotent(client):
    training = setup_training(client)
    client.post("/api/rules", json={"training_id": training["id"], "due_days": 7})
    add_employee(client, hire_date=(T - timedelta(days=30)).isoformat())
    first = client.post("/api/jobs/daily").json()
    assert first["reminders_queued"] == 2   # po terminie + eskalacja (do HR — brak przełożonego)
    assert client.post("/api/jobs/daily").json()["reminders_queued"] == 0
    msgs = client.get("/api/outbox?status=pending").json()
    assert {m["kind"] for m in msgs} == {"overdue", "escalation"}
    assert client.post("/api/outbox/send").json() == {"sent": 2}
    assert client.get("/api/outbox?status=pending").json() == []


def test_employee_update_and_rule_delete(client):
    training = setup_training(client)
    rule = client.post("/api/rules", json={"training_id": training["id"], "department": "Sprzedaż", "due_days": 7}).json()
    emp = add_employee(client)["employee"]
    moved = client.patch(f"/api/employees/{emp['id']}", json={"department": "Sprzedaż"}).json()
    assert len(moved["sync"]["created"]) == 1
    removed = client.delete(f"/api/rules/{rule['rule']['id']}").json()
    assert removed["sync"]["cancelled"] == 1
    assert client.delete("/api/rules/999").status_code == 404
    bad = client.patch(f"/api/employees/{emp['id']}", json={"manager_id": emp["id"]})
    assert bad.status_code == 422


def test_generate_with_fake_ai_and_fallback():
    good = AIQuestion(question="W jakim terminie potwierdzamy przyjęcie zgłoszenia?",
                      options=["3 dni", "7 dni", "14 dni", "30 dni"], correct_answer="7 dni",
                      explanation="Materiał podaje 7 dni.",
                      source_quote="przekazujemy sygnaliście w terminie 7 dni od jego otrzymania")
    llm = FakeLLM(AIQuestionSet(questions=[good], injection_detected=False))
    ai_client = TestClient(create_app(db_path=":memory:", llm_factory=lambda: llm, clock=lambda: T, seed=False))
    assert ai_client.get("/api/health").json()["ai_mode"] == "ai"
    training = setup_training(ai_client)
    out = ai_client.post(f"/api/trainings/{training['id']}/questions/generate", json={"count": 3}).json()
    assert out["source"] == "ai" and out["created"][0]["origin"] == "ai"

    llm.error = LLMUnavailable("Brak połączenia z API modelu")
    out = ai_client.post(f"/api/trainings/{training['id']}/questions/generate", json={"count": 2}).json()
    assert out["source"] == "offline" and len(out["created"]) == 2 and "AI niedostępne" in out["warnings"][0]


def test_generate_validation_and_empty_material(client):
    training = setup_training(client, material="")
    assert client.post(f"/api/trainings/{training['id']}/questions/generate", json={"count": 0}).status_code == 422
    r = client.post(f"/api/trainings/{training['id']}/questions/generate", json={"count": 3})
    assert r.status_code == 409 and "materiału" in r.json()["detail"]
