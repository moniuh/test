import json
import re
from datetime import datetime, timedelta

import pytest

from conftest import T, add_rule, approve_generated, make_employee, make_training
from szkolenia.compliance import add_months
from szkolenia.quiz import GradingError, QuestionSnapshot, build_quiz, grade, select_questions
from szkolenia.service import Conflict, InvalidInput, NotFound

POOL = [QuestionSnapshot(id=i, text=f"Pytanie {i}", options=("A", "B", "C", "D"), correct_index=i % 4)
        for i in range(1, 11)]


def test_build_quiz_is_deterministic_for_seed():
    a = build_quiz(POOL, seed=42)
    b = build_quiz(list(reversed(POOL)), seed=42)  # kolejność z bazy nie ma znaczenia
    assert [(i.question.id, i.permutation) for i in a] == [(i.question.id, i.permutation) for i in b]
    c = build_quiz(POOL, seed=43)
    assert [i.question.id for i in a] != [i.question.id for i in c]


def test_options_shuffled_and_correct_index_tracked():
    for item in build_quiz(POOL, seed=7):
        assert sorted(item.options) == ["A", "B", "C", "D"]
        assert item.options[item.correct_display_index] == item.question.options[item.question.correct_index]


def test_select_questions_limits_and_is_stable():
    picked = select_questions(POOL, seed=5, limit=4)
    assert len(picked) == 4 and picked == select_questions(list(reversed(POOL)), seed=5, limit=4)
    assert select_questions(POOL, seed=5, limit=None) == sorted(POOL, key=lambda q: q.id)


def test_grade_threshold_boundary():
    items = build_quiz(POOL[:5], seed=1)
    correct = {i.question.id: i.correct_display_index for i in items}
    wrong_one = dict(correct)
    first = items[0]
    wrong_one[first.question.id] = (first.correct_display_index + 1) % 4
    assert grade(items, correct, 80).passed and grade(items, correct, 80).percent == 100
    r = grade(items, wrong_one, 80)
    assert (r.score, r.percent, r.passed) == (4, 80, True)       # 4/5 = 80% przy progu 80% → zaliczone
    wrong_two = dict(wrong_one)
    second = items[1]
    wrong_two[second.question.id] = (second.correct_display_index + 1) % 4
    assert not grade(items, wrong_two, 80).passed                # 3/5 = 60%


def test_grade_missing_answer_counts_as_wrong_and_unknown_rejected():
    items = build_quiz(POOL[:3], seed=1)
    r = grade(items, {}, 50)
    assert r.score == 0 and not r.passed
    with pytest.raises(GradingError):
        grade(items, {999: 0}, 50)
    with pytest.raises(GradingError):
        grade(items, {items[0].question.id: 4}, 50)


@pytest.fixture
def ready(service):
    training = make_training(service, questions_per_test=4)
    add_rule(service, training["id"])
    approved = approve_generated(service, training["id"], 6)
    drafts = service.generate_questions(training["id"], 3)["created"]
    employee, created = make_employee(service)
    return service, training, approved, drafts, employee, created[0]


def correct_answers(service, attempt_id):
    attempt = service.store.one("SELECT * FROM attempts WHERE id=?", (attempt_id,))
    return {it.question.id: it.correct_display_index for it in service._attempt_items(attempt)}


def test_only_approved_questions_in_test(ready):
    service, training, approved, drafts, _, assignment = ready
    approved_ids = {q["id"] for q in approved}
    for seed in range(5):
        quiz = service.start_attempt(assignment["id"], seed=seed)
        ids = {q["id"] for q in quiz["questions"]}
        assert ids <= approved_ids and len(ids) == 4
        assert not ids & {q["id"] for q in drafts}
        assert all("correct_index" not in q for q in quiz["questions"])  # klient nie dostaje odpowiedzi


def test_no_approved_questions_blocks_test(service):
    training = make_training(service)
    add_rule(service, training["id"])
    service.generate_questions(training["id"], 3)  # same szkice
    _, created = make_employee(service)
    with pytest.raises(Conflict, match="niedostępny"):
        service.start_attempt(created[0]["id"])


def test_attempt_stores_seed_and_snapshot(ready):
    service, *_, assignment = ready
    quiz = service.start_attempt(assignment["id"], seed=1234)
    attempt = service.get_attempt(quiz["attempt_id"])
    assert attempt["seed"] == 1234
    assert [q["id"] for q in attempt["questions"]] == sorted(q["id"] for q in quiz["questions"])
    # ta sama kolejność odtworzona z ziarna
    order = [it.question.id for it in service._attempt_items(service.store.one(
        "SELECT * FROM attempts WHERE id=?", (quiz["attempt_id"],)))]
    assert order == [q["id"] for q in quiz["questions"]]


def test_passing_issues_certificate(ready):
    service, training, *_, employee, assignment = ready
    start = datetime(2026, 3, 16, 10, 0)
    quiz = service.start_attempt(assignment["id"], now=start, seed=99)
    result = service.submit_attempt(quiz["attempt_id"], correct_answers(service, quiz["attempt_id"]), today=T,
                                    now=start + timedelta(minutes=7))
    assert result["passed"] and result["percent"] == 100 and result["duration_seconds"] == 420
    cert = result["certificate"]
    assert re.fullmatch(r"SZK/2026/\d{4}", cert["number"])
    assert re.fullmatch(r"[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}", cert["verification_code"])
    assert cert["valid_until"] == add_months(T, 12).isoformat()
    assert cert["attempt_percent"] == 100 and cert["method"] == "test"
    assert all("correct_index" in item and item["explanation"] for item in result["items"])
    assert service.get_assignment(assignment["id"])["status"] == "completed"
    item = service.employee_trainings(employee["id"], T)["items"][0]
    assert item["status"] == "valid" and item["certificate_number"] == cert["number"]


def test_failed_attempt_keeps_assignment_open_and_hides_answers(ready):
    service, *_, assignment = ready
    quiz = service.start_attempt(assignment["id"], seed=5)
    answers = {qid: (idx + 1) % 4 for qid, idx in correct_answers(service, quiz["attempt_id"]).items()}
    result = service.submit_attempt(quiz["attempt_id"], answers, today=T)
    assert not result["passed"] and result["certificate"] is None and result["percent"] == 0
    assert all("correct_index" not in item for item in result["items"])
    assert service.get_assignment(assignment["id"])["status"] == "open"
    assert service.store.scalar("SELECT COUNT(*) FROM certificates") == 0


def test_double_submit_and_foreign_answers(ready):
    service, *_, assignment = ready
    quiz = service.start_attempt(assignment["id"], seed=5)
    with pytest.raises(InvalidInput):
        service.submit_attempt(quiz["attempt_id"], {123456: 0}, today=T)
    service.submit_attempt(quiz["attempt_id"], correct_answers(service, quiz["attempt_id"]), today=T)
    with pytest.raises(Conflict):
        service.submit_attempt(quiz["attempt_id"], {}, today=T)
    with pytest.raises(Conflict):
        service.start_attempt(assignment["id"])  # przypisanie zamknięte


def test_editing_question_returns_it_to_draft(ready):
    service, training, approved, *_ = ready
    q = approved[0]
    edited = service.update_question(q["id"], {**q, "text": q["text"] + " (poprawione)"})
    assert edited["status"] == "draft" and edited["reviewed_by"] is None


def test_approval_requires_verified_quote(ready):
    service, training, approved, drafts, *_ = ready
    q = drafts[0]
    edited = service.update_question(q["id"], {**q, "source_quote": "Tego zdania nie ma w żadnym materiale firmy."})
    assert edited["quote_verified"] is False
    with pytest.raises(Conflict, match="Cytat"):
        service.approve_question(q["id"])


def test_manual_question_validation(ready):
    service, training, *_ = ready
    with pytest.raises(InvalidInput, match="różnić"):
        service.create_question(training["id"], {"text": "Ile dni?", "options": ["7 dni", "7 dni", "3 dni", "1 dzień"],
                                                 "correct_index": 0})
    q = service.create_question(training["id"], {
        "text": "W jakim terminie potwierdzamy przyjęcie zgłoszenia?", "options": ["7 dni", "14 dni", "3 dni", "1 dzień"],
        "correct_index": 0, "explanation": "Termin z procedury.",
        "source_quote": "przekazujemy sygnaliście w terminie 7 dni od jego otrzymania"})
    assert q["origin"] == "manual" and q["quote_verified"]
    assert service.approve_question(q["id"], "Joanna")["status"] == "approved"


def test_material_change_reverts_questions_with_missing_quote(ready):
    service, training, approved, *_ = ready
    new_material = "\n".join(line for line in training["material"].splitlines() if "7 dni" not in line)
    affected = [q for q in approved if "7 dni" in q["source_quote"]]
    _, reverted = service.update_training(training["id"], {"material": new_material})
    assert reverted == len(affected)
    for q in affected:
        assert service.get_question(q["id"])["status"] == "draft"


def test_certificate_verification(ready):
    service, *_, assignment = ready
    quiz = service.start_attempt(assignment["id"], seed=1)
    cert = service.submit_attempt(quiz["attempt_id"], correct_answers(service, quiz["attempt_id"]), today=T)["certificate"]
    code = cert["verification_code"]
    ok = service.verify_certificate(code.lower().replace("-", " "), today=T)
    assert ok["is_valid"] and ok["number"] == cert["number"] and ok["status_label"] == "ważne"
    assert "email" not in ok  # weryfikacja ujawnia minimum danych
    expired = service.verify_certificate(code, today=add_months(T, 12) + timedelta(days=1))
    assert not expired["is_valid"] and expired["status_label"] == "wygasło"
    with pytest.raises(NotFound):
        service.verify_certificate("AAAA-BBBB-CCCC", today=T)


def test_certificate_numbers_are_sequential_per_year(service):
    training = make_training(service, mode="classroom", validity_months=None, title="Instruktaż")
    add_rule(service, training["id"], due_days=0)
    numbers = []
    for _ in range(3):
        _, created = make_employee(service)
        numbers.append(service.confirm_attendance(created[0]["id"], "Ryszard Bąk", T, today=T)["number"])
    assert numbers == ["SZK/2026/0001", "SZK/2026/0002", "SZK/2026/0003"]


def test_answers_snapshot_survives_question_edit(ready):
    service, training, approved, *_, assignment = ready
    quiz = service.start_attempt(assignment["id"], seed=3)
    answers = correct_answers(service, quiz["attempt_id"])
    for q in approved:  # HR zmienia pytania w trakcie testu
        service.update_question(q["id"], {**q, "correct_index": (q["correct_index"] + 1) % 4})
    assert service.submit_attempt(quiz["attempt_id"], answers, today=T)["passed"]
    stored = service.get_attempt(quiz["attempt_id"])
    assert json.loads(json.dumps(stored["answers"]))
