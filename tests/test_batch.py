"""Tryb wsadowy: parsowanie CSV/JSON, raport zbiorczy, CLI."""
from __future__ import annotations

import json

import pytest
from conftest import EXAMPLES

from ogloszenia import batch as batch_module
from ogloszenia.batch import BatchFormatError, main, parse_batch, run_batch
from ogloszenia.models import BatchInput

BAD = "Magazynier\nZatrudnimy mężczyznę do 35 lat. Atrakcyjne wynagrodzenie."
GOOD = (EXAMPLES / "programista-dobre.txt").read_text(encoding="utf-8")


def test_parse_csv_with_semicolon_bom_and_multiline():
    content = '﻿id;tytul;tresc\n1;Magazynier;"Magazynier\nZatrudnimy mężczyznę; pilne"\n2;Pusty;\n'
    items, skipped = parse_batch(content, "csv")
    assert [i.id for i in items] == ["1"]
    assert items[0].title == "Magazynier" and "mężczyznę; pilne" in items[0].text
    assert skipped == ["Pozycja 2: pusta treść ogłoszenia — pominięto."]


def test_parse_csv_comma_and_english_headers():
    items, _ = parse_batch('id,title,text\na,"Kasjer","Kasjer, praca w sklepie"\n', "auto")
    assert items[0].id == "a" and items[0].text == "Kasjer, praca w sklepie"


def test_parse_json_variants():
    as_list, _ = parse_batch(json.dumps([{"tresc": BAD, "tytuł": "M"}, "Drugie ogłoszenie"]), "auto")
    assert [i.id for i in as_list] == ["1", "2"] and as_list[0].title == "M"
    wrapped, _ = parse_batch(json.dumps({"items": [{"id": 7, "text": BAD}]}), "json")
    assert wrapped[0].id == "7"


@pytest.mark.parametrize(("content", "fmt", "message"), [
    ("{nie json", "json", "Niepoprawny JSON"),
    ('{"cos": 1}', "json", "JSON musi być listą"),
    ("id;nazwa\n1;x\n", "csv", "Nie znaleziono kolumny z treścią"),
    ("", "csv", "Plik CSV jest pusty"),
])
def test_parse_errors_are_polish(content, fmt, message):
    with pytest.raises(BatchFormatError, match=message):
        parse_batch(content, fmt)


def test_too_many_items(monkeypatch):
    monkeypatch.setattr(batch_module, "MAX_ITEMS", 2)
    with pytest.raises(BatchFormatError, match="Za dużo"):
        parse_batch(json.dumps(["a", "b", "c"]), "json")


def test_empty_batch_rejected():
    with pytest.raises(BatchFormatError, match="Brak ogłoszeń"):
        run_batch([])


def test_report_aggregates():
    items = [BatchInput(id="1", text=BAD), BatchInput(id="2", text=BAD), BatchInput(id="3", text=GOOD)]
    report = run_batch(items)
    assert report.count == 3 and report.with_errors == 2
    assert report.max_score == 100 and report.items[0].score <= report.items[-1].score
    assert report.items[-1].id == "3"
    assert sum(report.verdicts.values()) == 3 and report.verdicts["ok"] == 1
    top = {s.rule_id: s for s in report.top_rules}
    assert top["disc.age"].ads == 2 and top["disc.age"].share == pytest.approx(0.667, abs=1e-3)
    assert report.top_rules[0].ads >= report.top_rules[-1].ads
    cats = {c.category: c for c in report.categories}
    assert cats["discrimination"].ads == 2
    assert report.average_score == pytest.approx(sum(i.score for i in report.items) / 3, abs=0.05)


def test_example_files_give_same_report_from_csv_and_json():
    csv_items, _ = parse_batch((EXAMPLES / "wsad-przyklad.csv").read_text(encoding="utf-8"))
    json_items, _ = parse_batch((EXAMPLES / "wsad-przyklad.json").read_text(encoding="utf-8"))
    assert len(csv_items) == len(json_items) == 10
    assert run_batch(csv_items) == run_batch(json_items)


def test_cli_prints_report(capsys):
    assert main([str(EXAMPLES / "wsad-przyklad.csv")]) == 0
    out = capsys.readouterr().out
    assert "Najczęstsze problemy" in out and "Ogłoszeń: 10" in out
    assert main([str(EXAMPLES / "wsad-przyklad.json"), "--json"]) == 0
    assert json.loads(capsys.readouterr().out)["count"] == 10


def test_cli_reports_missing_file(capsys, tmp_path):
    assert main([str(tmp_path / "brak.csv")]) == 1
    assert "Błąd" in capsys.readouterr().err
