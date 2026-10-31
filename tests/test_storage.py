import sqlite3

import pytest

from preselekcja.storage import DemographicsStore, Store


@pytest.fixture
def store(tmp_path):
    return Store(tmp_path / "s.db")


def test_audit_chain_and_append_only(store, tmp_path):
    for i in range(3):
        store.append_audit(actor="r1", action="test", source="human", entity_type="candidate", entity_id=i,
                           input_hash="ab" * 32, details={"n": i, "tekst": "zażółć"})
    entries = list(store.iter_audit())
    assert entries[0]["prev_hash"] == "0" * 64
    assert entries[1]["prev_hash"] == entries[0]["entry_hash"]
    assert store.verify_audit_chain()["ok"]

    with pytest.raises(sqlite3.DatabaseError, match="tylko do dopisywania"):
        store.db.execute("UPDATE audit_log SET actor = 'ktoś' WHERE id = 1")
    with pytest.raises(sqlite3.DatabaseError, match="tylko do dopisywania"):
        store.db.execute("DELETE FROM audit_log WHERE id = 1")


def test_tampering_is_detected_by_hash_chain(store):
    for i in range(3):
        store.append_audit(actor="r1", action="decision_recorded", source="human", details={"status": "rejected"})
    # Ktoś z dostępem do pliku omija wyzwalacz i zmienia wpis.
    store.db.execute("DROP TRIGGER audit_no_update")
    store.db.execute("UPDATE audit_log SET details_json = '{\"status\": \"invited\"}' WHERE id = 2")
    result = store.verify_audit_chain()
    assert not result["ok"] and result["broken_at"] == 2


def test_decisions_are_append_only(store, job):
    with store.db.transaction() as tx:
        j = store.create_job(tx, job, "r1")
        cid = store.create_candidate(tx, job_id=j.id, source_format="text", input_hash="x", original_text="a",
                                     anonymized_text="a", identity={}, anonymization={})
        store.add_decision(tx, candidate_id=cid, status="rejected", reason="brak kwalifikacji",
                           actor="r1", ai_recommendation="mismatch", evaluation_id=None, is_override=False,
                           viewed_before=True)
    with pytest.raises(sqlite3.DatabaseError):
        store.db.execute("UPDATE decisions SET status = 'invited'")


def test_demographics_in_separate_database(tmp_path):
    demo = DemographicsStore(tmp_path / "demo.db")
    main = Store(tmp_path / "main.db")
    demo.upsert(1, "female", "30_49", "no")
    demo.upsert(1, "female", "50_plus", "no")
    assert demo.all() == {1: {"gender": "female", "age_group": "50_plus", "disability": "no"}}
    tables = {r["name"] for r in main.db.query("SELECT name FROM sqlite_master WHERE type='table'")}
    assert "demographics" not in tables
    assert demo.delete(1) and demo.all() == {}
