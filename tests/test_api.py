from symulator.candidate import CandidateReplyAI
from symulator.coach import CoachAIVerdict
from symulator.llm import LLMUnavailable
from symulator.report import ReportNoteAI, ReportNotesAI


def _start(client, scenario_id="obsluga-klienta"):
    res = client.post("/api/sessions", json={"scenario_id": scenario_id})
    assert res.status_code == 201
    return res.json()["session"]["id"]


def test_index_and_static_served(client):
    res = client.get("/")
    assert res.status_code == 200 and "Symulator rozmów rekrutacyjnych" in res.text
    assert "symulowany" in res.text  # informacja o AI w UI
    assert client.get("/static/app.js").status_code == 200
    assert client.get("/static/style.css").status_code == 200


def test_status_offline(client):
    assert client.get("/api/status").json()["mode"] == "offline"


def test_scenarios_list_does_not_leak_hidden_facts(client):
    res = client.get("/api/scenarios")
    assert res.status_code == 200
    data = res.json()
    assert len(data) >= 3
    assert "facts" not in data[0] and "traps" not in data[0]
    assert "podwójne opłaty" not in res.text
    assert client.get("/api/scenarios/junior-developer").json()["title"] == "Junior Python Developer"
    assert client.get("/api/scenarios/brak").status_code == 404


def test_full_session_from_start_to_report_and_export(client):
    sid = _start(client)
    view = client.get(f"/api/sessions/{sid}").json()
    assert view["session"]["status"] == "w_toku" and view["turns"] == []
    assert "Zacznij od pytania behawioralnego" in view["star_hint"]

    questions = [
        "Dzień dobry, proszę opowiedzieć o sobie.",
        "Proszę opowiedzieć o sytuacji, w której rozmawiała Pani z bardzo zdenerwowanym klientem.",
        "Co konkretnie Pani wtedy zrobiła?",
        "Jaki był efekt?",
        "Czy ma Pani dzieci?",
        "Lubi Pani pracę w zespole, prawda?",
    ]
    results = []
    for q in questions:
        res = client.post(f"/api/sessions/{sid}/turns", json={"question": q})
        assert res.status_code == 200, res.text
        results.append(res.json())

    second = results[1]
    assert second["turn"]["revealed_fact_ids"] == ["kom_s"]
    assert second["turn"]["coach"]["question_type"] == "behawioralne"
    assert second["turn"]["candidate_source"] == "offline"
    assert results[3]["progress"][0]["elements"] == {"S": True, "T": False, "A": True, "R": True}
    flagged = results[4]["turn"]["coach"]
    assert flagged["flags"][0]["category"] == "rodzina"
    assert "18³ᵃ" in flagged["flags"][0]["legal_basis"]
    assert results[5]["turn"]["coach"]["question_type"] == "sugerujace"
    assert results[5]["stats"]["questions"] == 6

    rep = client.post(f"/api/sessions/{sid}/finish")
    assert rep.status_code == 200
    report = rep.json()
    assert report["turns"] == 6
    assert len(report["recommendations"]) == 3
    assert report["incidents"][0]["category"] == "rodzina"
    assert report["questions"]["leading"] == 1
    assert report["talk"]["trainee_words"] > 0 and report["talk"]["candidate_words"] > 0
    comm = next(c for c in report["coverage"] if c["competency_id"] == "komunikacja")
    assert comm["elements_revealed"] == ["S", "A", "R"]

    assert client.get(f"/api/sessions/{sid}/report").json()["session_id"] == sid
    # Ponowne zakończenie zwraca ten sam raport, a zakończona sesja nie przyjmuje pytań
    assert client.post(f"/api/sessions/{sid}/finish").json()["generated_at"] == report["generated_at"]
    closed = client.post(f"/api/sessions/{sid}/turns", json={"question": "Jeszcze jedno?"})
    assert closed.status_code == 409 and "zakończona" in closed.json()["detail"]

    md = client.get(f"/api/sessions/{sid}/export.md")
    assert md.status_code == 200
    assert md.headers["content-type"].startswith("text/markdown")
    assert "attachment" in md.headers["content-disposition"]
    assert "## Transkrypt" in md.text and "Czy ma Pani dzieci?" in md.text

    sessions = client.get("/api/sessions").json()
    assert sessions[0]["id"] == sid and sessions[0]["status"] == "zakonczona"
    assert sessions[0]["summary"]["incidents"] == 1


def test_validation_errors_are_in_polish(client):
    sid = _start(client)
    empty = client.post(f"/api/sessions/{sid}/turns", json={"question": "   "})
    assert empty.status_code == 422
    body = empty.json()
    assert body["detail"] == "Nieprawidłowe dane wejściowe."
    assert body["bledy"][0]["komunikat"] == "Pytanie nie może być puste."

    too_long = client.post(f"/api/sessions/{sid}/turns", json={"question": "a" * 1001})
    assert too_long.status_code == 422 and "za długi" in too_long.json()["bledy"][0]["komunikat"]

    missing = client.post(f"/api/sessions/{sid}/turns", json={})
    assert missing.json()["bledy"][0] == {"pole": "question", "komunikat": "Pole jest wymagane."}

    assert client.post("/api/sessions", json={}).status_code == 422


def test_not_found_and_conflict_errors(client):
    assert client.post("/api/sessions", json={"scenario_id": "nie-ma"}).status_code == 404
    assert client.get("/api/sessions/brak").status_code == 404
    assert "Nie znaleziono sesji" in client.get("/api/sessions/brak").json()["detail"]
    assert client.post("/api/sessions/brak/turns", json={"question": "Jak?"}).status_code == 404
    sid = _start(client)
    assert client.post(f"/api/sessions/{sid}/finish").status_code == 409  # brak pytań
    assert client.get(f"/api/sessions/{sid}/report").status_code == 404


def test_delete_session(client):
    sid = _start(client)
    client.post(f"/api/sessions/{sid}/turns", json={"question": "Proszę opowiedzieć o sobie."})
    assert client.delete(f"/api/sessions/{sid}").status_code == 204
    assert client.get(f"/api/sessions/{sid}").status_code == 404
    assert client.delete(f"/api/sessions/{sid}").status_code == 404


def test_trap_notice_returned_when_candidate_mentions_private_matter(client):
    sid = _start(client)
    res = client.post(f"/api/sessions/{sid}/turns", json={"question": "Jak wygląda Pani dyspozycyjność w weekendy?"}).json()
    assert res["turn"]["trap_ids"] == ["pulapka_dzieci"]
    assert "Nie dopytuj" in res["trap_notice"]
    follow = client.post(f"/api/sessions/{sid}/turns", json={"question": "A kto zajmuje się wtedy maluchami, mąż?"}).json()
    assert follow["turn"]["coach"]["flags"][0]["category"] == "rodzina"


def test_ai_mode_session_with_fake_llm(client_factory, fake_llm_cls):
    fake = fake_llm_cls({
        CandidateReplyAI: CandidateReplyAI(answer="Zadzwonił klient z podwójnymi opłatami i był wściekły.", revealed_fact_ids=["kom_s", "wymyslony"], mentioned_trap_ids=[], emotion="pewna_siebie"),
        CoachAIVerdict: CoachAIVerdict(question_type="behawioralne", illegal=False, category="brak", explanation="", suggestion="", tip="Teraz zapytaj o działania."),
        ReportNotesAI: ReportNotesAI(notes=[ReportNoteAI(kind="mocna_strona", observation="Dobre otwarcie.", quote="Proszę opowiedzieć o trudnym kliencie.", suggestion="Dopytaj o rezultat.")]),
    })
    client = client_factory(fake)
    assert client.get("/api/status").json()["mode"] == "ai"
    sid = _start(client)
    turn = client.post(f"/api/sessions/{sid}/turns", json={"question": "Proszę opowiedzieć o trudnym kliencie."}).json()["turn"]
    assert turn["candidate_source"] == "ai"
    assert turn["revealed_fact_ids"] == ["kom_s"]  # nieznane id odrzucone
    assert "wymyslony" in turn["candidate_note"]
    assert turn["coach"]["source"] == "ai" and turn["coach"]["tip"] == "Teraz zapytaj o działania."
    report = client.post(f"/api/sessions/{sid}/finish").json()
    assert report["notes_source"] == "ai" and report["notes"][0]["quote"] == "Proszę opowiedzieć o trudnym kliencie."


def test_ai_mode_falls_back_to_offline_on_llm_unavailable(client_factory, fake_llm_cls):
    fake = fake_llm_cls({
        CandidateReplyAI: LLMUnavailable("Brak połączenia z API modelu"),
        CoachAIVerdict: LLMUnavailable("Brak połączenia z API modelu"),
        ReportNotesAI: LLMUnavailable("Brak połączenia z API modelu"),
    })
    client = client_factory(fake)
    sid = _start(client)
    turn = client.post(f"/api/sessions/{sid}/turns", json={"question": "Proszę opowiedzieć o sytuacji, w której rozmawiała Pani z bardzo zdenerwowanym klientem."}).json()["turn"]
    assert turn["candidate_source"] == "offline" and turn["revealed_fact_ids"] == ["kom_s"]
    assert turn["coach"]["source"] == "offline" and turn["coach"]["ai_error"]
    report = client.post(f"/api/sessions/{sid}/finish").json()
    assert report["notes_source"] == "offline" and report["ai_error"]
