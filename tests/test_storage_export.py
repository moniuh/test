from symulator.export import session_to_markdown
from symulator.report import build_report
from symulator.storage import SessionStore


def test_store_roundtrip_and_delete(tmp_path, cs_scenario, player):
    store = SessionStore(tmp_path / "db" / "s.db")
    rec = store.create_session("s1", cs_scenario.id, ai_enabled=False)
    turns = player(cs_scenario, ["Proszę opowiedzieć o sobie.", "Czy ma Pani dzieci?"])
    for t in turns:
        store.add_turn("s1", t)
    loaded = store.get_turns("s1")
    assert [t.question for t in loaded] == [t.question for t in turns]
    assert loaded[1].coach.flags[0].category == "rodzina"
    assert store.get_session("s1").turns_count == 2

    report = build_report(cs_scenario, "s1", loaded)
    store.finish_session("s1", report)
    assert store.get_session("s1").status == "zakonczona"
    assert store.get_report("s1").incidents[0].category == "rodzina"
    assert store.report_summaries()["s1"]["incidents"] == 1
    assert [r.id for r in store.list_sessions()] == [rec.id]

    assert store.delete_session("s1")
    assert store.get_session("s1") is None and store.get_turns("s1") == [] and store.get_report("s1") is None
    assert not store.delete_session("s1")


def test_purge_older_than(tmp_path):
    store = SessionStore(tmp_path / "p.db")
    store.create_session("old", "obsluga-klienta", ai_enabled=False)
    with store._conn() as conn:
        conn.execute("UPDATE sessions SET created_at = '2020-01-01T00:00:00+00:00' WHERE id = 'old'")
    store.create_session("new", "obsluga-klienta", ai_enabled=False)
    assert store.purge_older_than(30) == 1
    assert [r.id for r in store.list_sessions()] == ["new"]


def test_markdown_export_contains_report_and_transcript(tmp_path, cs_scenario, player):
    store = SessionStore(tmp_path / "e.db")
    store.create_session("e1", cs_scenario.id, ai_enabled=False)
    turns = player(cs_scenario, ["Proszę opowiedzieć o sytuacji, w której rozmawiała Pani z bardzo zdenerwowanym klientem.", "Ile Pani zarabia obecnie?"])
    for t in turns:
        store.add_turn("e1", t)
    report = build_report(cs_scenario, "e1", turns)
    store.finish_session("e1", report)
    md = session_to_markdown(store.get_session("e1"), cs_scenario, turns, report)
    assert md.startswith("# Sesja treningowa: ")
    for heading in ("## Raport", "### Kompletność STAR per kompetencja", "### Najważniejsze rekomendacje", "### Incydenty prawne", "## Transkrypt"):
        assert heading in md
    assert "Ile Pani zarabia obecnie?" in md
    assert "symulowany" in md
    assert "Proporcja mówienia" in md


def test_markdown_export_without_report(tmp_path, cs_scenario):
    store = SessionStore(tmp_path / "e2.db")
    rec = store.create_session("e2", cs_scenario.id, ai_enabled=False)
    md = session_to_markdown(rec, cs_scenario, [], None)
    assert "## Raport" not in md and "## Transkrypt" in md
