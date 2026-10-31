import base64

import pytest
from fastapi.testclient import TestClient

from asystent.api import create_app
from asystent.config import Settings
from conftest import FakeLLM, ai_response, fragment_with

NEW_DOC = """---
tytul: Polityka podróży służbowych
kod: polityka-podrozy
wersja: 1.0
obowiazuje_od: 2026-10-01
---
# Polityka podróży służbowych

## § 1. Delegacje krajowe

1. Za każdą dobę delegacji krajowej przysługuje dieta w wysokości 45 zł.

## § 2. Delegacje zagraniczne

1. Dieta za delegację zagraniczną wynosi tyle, ile przewidują przepisy dla danego kraju.
"""


def _b64(text: str) -> str:
    return base64.b64encode(text.encode()).decode()


def test_index_serves_ui(client):
    response = client.get("/")
    assert response.status_code == 200
    assert "Asystent kadrowy" in response.text
    assert client.get("/static/app.js").status_code == 200


def test_health(client):
    data = client.get("/api/health").json()
    assert data["status"] == "ok"
    assert data["mode"] == "offline"
    assert data["documents_current"] == 8
    assert data["chunks_indexed"] > 50


def test_session_and_ask(client):
    session = client.post("/api/sessions").json()
    assert len(session["session_token"]) >= 16
    body = {"question": "Ile mam dni urlopu?", "session_token": session["session_token"]}
    response = client.post("/api/ask", json=body)
    assert response.status_code == 200
    data = response.json()
    assert data["session_token"] == session["session_token"]
    assert data["status"] == "answered"
    assert data["source"] == "offline"
    assert data["citations"][0]["section_path"][0] == "Polityka urlopowa"
    assert data["citations"][0]["fragment"]


def test_ask_without_session_creates_one(client):
    data = client.post("/api/ask", json={"question": "Kiedy wypłata?"}).json()
    assert data["session_token"]


@pytest.mark.parametrize(
    ("payload", "message"),
    [
        ({}, "Pole jest wymagane"),
        ({"question": ""}, "Tekst jest za krótki"),
        ({"question": "x" * 1001}, "Tekst jest za długi"),
        ({"question": 123}, "Oczekiwano tekstu"),
    ],
)
def test_ask_validation_errors_in_polish(client, payload, message):
    response = client.post("/api/ask", json=payload)
    assert response.status_code == 422
    body = response.json()
    assert body["detail"] == "Nieprawidłowe dane wejściowe"
    assert body["bledy"][0]["pole"] == "question"
    assert body["bledy"][0]["komunikat"] == message


def test_ask_whitespace_question_is_domain_error(client):
    response = client.post("/api/ask", json={"question": "   "})
    assert response.status_code == 400
    assert response.json()["detail"] == "Pytanie nie może być puste"


def test_feedback_endpoint(client):
    answer = client.post("/api/ask", json={"question": "Kiedy wypłata?"}).json()
    url = f"/api/interactions/{answer['interaction_id']}/feedback"
    assert client.post(url, json={"session_token": answer["session_token"], "vote": "up"}).json() == {"ok": True}
    assert client.post(url, json={"session_token": answer["session_token"], "vote": "meh"}).status_code == 422
    other = client.post("/api/sessions").json()["session_token"]
    forbidden = client.post(url, json={"session_token": other, "vote": "down"})
    assert forbidden.status_code == 403
    assert "innej sesji" in forbidden.json()["detail"]
    missing = client.post("/api/interactions/9999/feedback", json={"session_token": other, "vote": "up"})
    assert missing.status_code == 404


def test_escalation_flow(client):
    answer = client.post("/api/ask", json={"question": "Czy firma refunduje kryptowaluty?"}).json()
    created = client.post("/api/escalations", json={
        "session_token": answer["session_token"], "interaction_id": answer["interaction_id"], "note": "Proszę o info",
    })
    assert created.status_code == 201
    esc = created.json()["escalation"]
    assert esc["status"] == "nowe"

    hr_list = client.get("/api/hr/escalations", params={"status": "nowe"}).json()
    assert [e["id"] for e in hr_list] == [esc["id"]]
    replied = client.post(f"/api/hr/escalations/{esc['id']}/reply", json={"reply": "Nie refundujemy."}).json()
    assert replied["status"] == "odpowiedziane"

    mine = client.get("/api/me/escalations", headers={"X-Session-Token": answer["session_token"]}).json()
    assert mine[0]["hr_reply"] == "Nie refundujemy."
    assert client.get("/api/me/escalations").json() == []

    closed = client.post(f"/api/hr/escalations/{esc['id']}/close").json()
    assert closed["status"] == "zamknięte"
    assert client.post(f"/api/hr/escalations/{esc['id']}/reply", json={"reply": "x"}).status_code == 400
    assert client.post("/api/hr/escalations/999/close").status_code == 404
    assert client.get("/api/hr/escalations", params={"status": "dziwny"}).status_code == 400


def test_escalation_without_content_is_rejected(client):
    response = client.post("/api/escalations", json={})
    assert response.status_code == 400


def test_document_upload_and_versions(client):
    response = client.post("/api/hr/documents", json={"filename": "podroze.md", "content_base64": _b64(NEW_DOC)})
    assert response.status_code == 201
    doc = response.json()
    assert doc["status"] == "aktualna" and doc["chunk_count"] == 2

    detail = client.get(f"/api/hr/documents/{doc['id']}").json()
    assert detail["chunks"][0]["section_path"] == ["Polityka podróży służbowych", "§ 1. Delegacje krajowe"]

    answer = client.post("/api/ask", json={"question": "Ile wynosi dieta za delegację krajową?"}).json()
    assert answer["status"] == "answered"
    assert answer["citations"][0]["doc_key"] == "polityka-podrozy"

    duplicate = client.post("/api/hr/documents", json={"filename": "podroze.md", "content_base64": _b64(NEW_DOC)})
    assert duplicate.status_code == 422 and "już istnieje" in duplicate.json()["detail"]

    v2 = client.post("/api/hr/documents", json={
        "filename": "podroze.md", "content_base64": _b64(NEW_DOC.replace("45 zł", "50 zł")),
        "version": "2.0", "effective_from": "2026-10-15",
    }).json()
    statuses = {d["version"]: d["status"] for d in client.get("/api/hr/documents").json()
                if d["doc_key"] == "polityka-podrozy"}
    assert statuses == {"1.0": "archiwalna", "2.0": "aktualna"}
    answer = client.post("/api/ask", json={"question": "Ile wynosi dieta za delegację krajową?"}).json()
    assert "50 zł" in answer["answer"]

    withdrawn = client.post(f"/api/hr/documents/{v2['id']}/withdraw", json={"withdrawn": True}).json()
    assert withdrawn["status"] == "wycofana"
    assert client.post("/api/hr/documents/999/withdraw", json={}).status_code == 404
    assert client.get("/api/hr/documents/999").status_code == 404


@pytest.mark.parametrize(
    ("payload", "status", "fragment"),
    [
        ({"filename": "a.md", "content_base64": "@@@"}, 400, "Base64"),
        ({"filename": "a.xlsx", "content_base64": _b64("x")}, 422, "Nieobsługiwany format"),
        ({"filename": "a.md", "content_base64": _b64("tekst"), "effective_from": "jutro"}, 422, "Nieprawidłowe dane"),
        ({"filename": "a.md"}, 422, "Nieprawidłowe dane"),
    ],
)
def test_document_upload_errors(client, payload, status, fragment):
    response = client.post("/api/hr/documents", json=payload)
    assert response.status_code == status
    assert fragment in response.json()["detail"]


def test_hr_reports(client):
    client.post("/api/ask", json={"question": "Czy firma refunduje kryptowaluty?"})
    client.post("/api/ask", json={"question": "Kiedy wypłata?"})
    gaps = client.get("/api/hr/gaps", params={"days": 30}).json()
    assert gaps["gap_questions"] == 1
    assert gaps["groups"][0]["keyword"]
    assert client.get("/api/hr/gaps", params={"days": 0}).status_code == 422
    stats = client.get("/api/hr/stats").json()
    assert stats["total_questions"] == 2
    hits = client.get("/api/hr/search", params={"q": "urlop na żądanie"}).json()
    assert hits[0]["path"].startswith("Polityka urlopowa")


def test_retention_endpoints(client):
    client.post("/api/ask", json={"question": "Kiedy wypłata?"})
    info = client.get("/api/hr/retention").json()
    assert info["days"] == 90 and info["last_purge"]
    result = client.post("/api/hr/retention/purge", json={"days": 30}).json()
    assert result == {**result, "days": 30, "interactions": 0}
    assert client.post("/api/hr/retention/purge", json={"days": 0}).status_code == 422


def test_hr_token_protects_panel(make_assistant):
    settings = Settings(db_path=":memory:", hr_token="sekret")
    client = TestClient(create_app(assistant=make_assistant(settings=settings)))
    denied = client.get("/api/hr/documents")
    assert denied.status_code == 401
    assert "token" in denied.json()["detail"]
    assert client.get("/api/hr/documents", headers={"X-HR-Token": "zly"}).status_code == 401
    assert client.get("/api/hr/documents", headers={"X-HR-Token": "sekret"}).status_code == 200
    # czat pracownika nie wymaga tokenu
    assert client.post("/api/ask", json={"question": "Kiedy wypłata?"}).status_code == 200


def test_ai_mode_through_api(make_assistant):
    def respond(prompt):
        fid = fragment_with(prompt, "28. dnia")
        return ai_response(answer="Wypłata jest do 28. dnia miesiąca.",
                           citations=[{"fragment_id": fid, "quote": "najpóźniej 28. dnia miesiąca"}])

    client = TestClient(create_app(assistant=make_assistant(llm=FakeLLM(respond))))
    data = client.post("/api/ask", json={"question": "Kiedy wypłata?"}).json()
    assert data["source"] == "ai"
    assert data["answer"] == "Wypłata jest do 28. dnia miesiąca."
    assert data["citations"][0]["quote"] == "najpóźniej 28. dnia miesiąca"
