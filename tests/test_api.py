"""API: ścieżki szczęśliwe, walidacja z komunikatami po polsku, AI z atrapą i fallback."""
from __future__ import annotations

import pytest
from conftest import EXAMPLES, FakeLLM
from fastapi.testclient import TestClient

from ogloszenia.api import app, create_app
from ogloszenia.audit import AIIssue, AIReview
from ogloszenia.llm import LLMUnavailable
from ogloszenia.rewrite import AIRewrite

AD = "Kierownik sklepu\nSzukamy osoby do 35 lat z miłą aparycją. Wynagrodzenie 6000 zł brutto/mies."


@pytest.fixture
def client():
    return TestClient(app)


def test_health_offline(client):
    data = client.get("/api/health").json()
    assert data["status"] == "ok" and data["ai_enabled"] is False and data["rules"] >= 25
    assert data["formula"].startswith("wynik = max(0, 100")


def test_rules_catalog(client):
    rules = client.get("/api/rules").json()
    assert {"id", "category_label", "severity_label", "legal_basis", "explanation", "exceptions"} <= rules[0].keys()
    assert {r["category"] for r in rules} == {
        "pay_transparency", "gender_neutrality", "discrimination", "excessive_data", "clarity"}


def test_examples_list_and_get(client):
    listed = client.get("/api/examples").json()
    assert len(listed) == 6 and {e["kind"] for e in listed} == {"dobre", "złe", "do poprawy"}
    example = client.get(f"/api/examples/{listed[0]['id']}").json()
    assert example["text"] == (EXAMPLES / "magazynier-zle.txt").read_text(encoding="utf-8")


@pytest.mark.parametrize("bad_id", ["nie-ma-takiego", "..%2F..%2Fetc", "Magazynier"])
def test_unknown_example_404(client, bad_id):
    response = client.get(f"/api/examples/{bad_id}")
    assert response.status_code == 404
    expected = {"Nie znaleziono przykładu o podanym identyfikatorze.", "Nie znaleziono zasobu."}
    assert response.json()["detail"] in expected


def test_unknown_route_and_method_messages_are_polish(client):
    assert client.get("/api/nie-ma").json() == {"detail": "Nie znaleziono zasobu."}
    response = client.get("/api/audit")
    assert response.status_code == 405 and response.json()["detail"].startswith("Niedozwolona metoda")


def test_audit_happy_path_offsets(client):
    response = client.post("/api/audit", json={"text": AD})
    assert response.status_code == 200
    data = response.json()
    assert data["source"] == "offline" and 0 <= data["score"] <= 100
    fragments = {f["rule_id"]: f for f in data["findings"]}
    for f in data["findings"]:
        if f["start"] is not None:
            assert AD[f["start"]:f["end"]] == f["fragment"]
    assert fragments["disc.age"]["fragment"] == "do 35 lat"
    assert fragments["gender.title"]["fix"]["text"] == " (K/M)"
    assert len(data["categories"]) == 5 and data["title"] == "Kierownik sklepu"


@pytest.mark.parametrize(("payload", "field", "message"), [
    ({}, "text", "To pole jest wymagane."),
    ({"text": ""}, "text", "Tekst jest za krótki (min. 1 znaków)."),
    ({"text": "   \n "}, "text", "Treść ogłoszenia jest pusta."),
    ({"text": "a" * 20_001}, "text", "Tekst jest za długi (maks. 20000 znaków)."),
    ({"text": 5}, "text", "Oczekiwano tekstu."),
    ({"text": "ok", "use_ai": "może"}, "use_ai", "Oczekiwano wartości true lub false."),
])
def test_audit_validation_errors_in_polish(client, payload, field, message):
    response = client.post("/api/audit", json=payload)
    assert response.status_code == 422
    body = response.json()
    assert body["detail"] == "Nieprawidłowe dane wejściowe."
    assert {"pole": field, "komunikat": message} in body["errors"]


def test_invalid_json_body(client):
    response = client.post("/api/audit", content="{nie json", headers={"Content-Type": "application/json"})
    assert response.status_code == 422
    assert response.json()["errors"][0]["komunikat"] == "Niepoprawny JSON w treści żądania."


def test_rewrite_offline(client):
    data = client.post("/api/rewrite", json={"text": AD}).json()
    assert data["source"] == "offline" and data["text"].startswith("Kierownik sklepu (K/M)")
    assert "do 35 lat" not in data["text"] and data["score_after"] > data["score_before"]


def test_batch_json_items(client):
    good = (EXAMPLES / "programista-dobre.txt").read_text(encoding="utf-8")
    payload = {"items": [{"id": "a", "text": AD}, {"title": "dobre", "text": good}]}
    data = client.post("/api/batch", json=payload).json()
    assert data["count"] == 2 and data["items"][-1]["title"] == "dobre"
    assert client.post("/api/batch", json={"items": []}).status_code == 422
    blank = client.post("/api/batch", json={"items": [{"id": "x", "text": "  "}]})
    assert blank.status_code == 422 and "Pusta treść" in blank.json()["detail"]


def test_batch_import_and_samples(client):
    sample = client.get("/api/batch/sample", params={"format": "csv"}).json()
    report = client.post("/api/batch/import", json={"content": sample["content"], "format": "csv"}).json()
    assert report["count"] == 10 and report["top_rules"]
    sample_json = client.get("/api/batch/sample", params={"format": "json"}).json()
    assert client.post("/api/batch/import", json={"content": sample_json["content"]}).json()["count"] == 10
    assert client.get("/api/batch/sample", params={"format": "xml"}).status_code == 422


def test_batch_import_error_is_polish(client):
    response = client.post("/api/batch/import", json={"content": "a;b\n1;2", "format": "csv"})
    assert response.status_code == 422
    assert response.json()["detail"].startswith("Nie znaleziono kolumny z treścią ogłoszenia")


def test_ui_and_static_served(client):
    page = client.get("/")
    assert page.status_code == 200 and "Audytor ogłoszeń o pracę" in page.text
    assert client.get("/static/app.js").status_code == 200
    assert client.get("/static/style.css").status_code == 200


# --- AI przez wstrzykniętego dostawcę --------------------------------------------------------------

def ai_client(fake: FakeLLM) -> TestClient:
    return TestClient(create_app(llm_provider=lambda: fake))


def test_audit_uses_ai_when_available():
    issue = AIIssue(quote="miłą aparycją", category="discrimination", severity="error", title="x",
                    explanation="y", legal_basis="z", suggestion="w")
    extra = AIIssue(quote="Wynagrodzenie 6000", category="pay_transparency", severity="suggestion",
                    title="Podaj też premie", explanation="y", legal_basis="Dobra praktyka", suggestion="w")
    fake = FakeLLM({AIReview: AIReview(issues=[issue, extra], injection_attempt=False)})
    client = ai_client(fake)
    assert client.get("/api/health").json()["ai_enabled"] is True
    data = client.post("/api/audit", json={"text": AD}).json()
    assert data["source"] == "ai"
    assert [f["fragment"] for f in data["findings"] if f["source"] == "ai"] == ["Wynagrodzenie 6000"]
    assert data["ai_rejected"][0]["reason"] == "pokrywa się z już wykrytym znaleziskiem"


def test_use_ai_false_skips_model():
    fake = FakeLLM({AIReview: AIReview(issues=[], injection_attempt=False)})
    data = ai_client(fake).post("/api/audit", json={"text": AD, "use_ai": False}).json()
    assert data["source"] == "offline" and fake.calls == []


def test_api_falls_back_when_model_unavailable():
    client = ai_client(FakeLLM(error=LLMUnavailable("Przekroczony limit zapytań do modelu")))
    audit = client.post("/api/audit", json={"text": AD}).json()
    assert audit["source"] == "offline" and "Przekroczony limit" in audit["ai_note"]
    rewrite = client.post("/api/rewrite", json={"text": AD}).json()
    assert rewrite["source"] == "offline" and "Przekroczony limit" in rewrite["ai_note"]


def test_rewrite_uses_ai_when_available():
    version = AIRewrite(rewritten_text="Kierownik / Kierowniczka sklepu (K/M)\nWynagrodzenie 6000 zł brutto/mies.",
                        changes=[], injection_attempt=False)
    data = ai_client(FakeLLM({AIRewrite: version})).post("/api/rewrite", json={"text": AD}).json()
    assert data["source"] == "ai" and data["text"].startswith("Kierownik / Kierowniczka")
