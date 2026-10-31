import csv
import io
from datetime import timedelta

from conftest import T, add_rule, make_employee, make_training
from szkolenia.report import CSV_COLUMNS, certificate_html, matrix_to_csv, matrix_to_html


def build(service):
    rodo = make_training(service, title="RODO", category="RODO", validity_months=12)
    bhp = make_training(service, title="BHP biuro", category="BHP", validity_months=72)
    add_rule(service, rodo["id"], due_days=14)
    add_rule(service, bhp["id"], department="Administracja", due_days=365)
    anna, a_created = make_employee(service, full_name="Anna Nowak", department="Administracja", position="Księgowa",
                                    hire_date=T - timedelta(days=30))
    piotr, p_created = make_employee(service, full_name="Piotr <script>alert(1)</script>", department="Magazyn",
                                     hire_date=T - timedelta(days=30))
    rodo_a = next(a for a in a_created if a["training_title"] == "RODO")
    service.record_external_completion(rodo_a["id"], T - timedelta(days=20), "papier", today=T)
    return rodo, bhp, anna, piotr


def test_matrix_statuses_and_department_filter(service):
    rodo, bhp, anna, piotr = build(service)
    matrix = service.compliance_matrix(T)
    rows = {r["employee"]["full_name"]: r for r in matrix["rows"]}
    anna_cells = rows["Anna Nowak"]["cells"]
    assert anna_cells[str(rodo["id"])]["status"] == "valid"
    assert anna_cells[str(rodo["id"])]["certificate_number"].startswith("SZK/")
    assert anna_cells[str(bhp["id"])]["status"] == "incomplete"
    piotr_cells = rows["Piotr <script>alert(1)</script>"]["cells"]
    assert piotr_cells[str(rodo["id"])]["status"] == "overdue"   # termin minął 16 dni temu
    assert piotr_cells[str(bhp["id"])]["status"] == "not_applicable"
    assert rows["Anna Nowak"]["compliance_percent"] == 50
    only_admin = service.compliance_matrix(T, "Administracja")
    assert [r["employee"]["full_name"] for r in only_admin["rows"]] == ["Anna Nowak"]


def test_csv_export(service):
    build(service)
    text = matrix_to_csv(service.compliance_matrix(T))
    assert text.startswith("﻿")
    rows = list(csv.reader(io.StringIO(text.lstrip("﻿")), delimiter=";"))
    assert rows[0] == CSV_COLUMNS
    assert len(rows) == 1 + 3          # Anna: RODO + BHP, Piotr: RODO (BHP nie dotyczy)
    anna_rodo = next(r for r in rows if r[0] == "Anna Nowak" and r[4] == "RODO")
    assert anna_rodo[6] == "aktualne" and anna_rodo[7] == (T - timedelta(days=20)).strftime("%d.%m.%Y")
    assert anna_rodo[10].startswith("SZK/")
    statuses = {r[6] for r in rows[1:]}
    assert statuses == {"aktualne", "nieukończone", "przeterminowane"}


def test_printable_html_escapes_and_contains_legend(service):
    build(service)
    html = matrix_to_html(service.compliance_matrix(T), "16.03.2026 12:00")
    assert "<script>alert(1)</script>" not in html and "&lt;script&gt;" in html
    assert "Raport zgodności szkoleń obowiązkowych" in html and "Legenda" in html and "window.print()" in html


def test_certificate_html(service):
    training = make_training(service, mode="classroom", validity_months=None, title="Instruktaż stanowiskowy")
    add_rule(service, training["id"], due_days=0)
    _, created = make_employee(service, full_name="Ewa Zielińska")
    cert = service.confirm_attendance(created[0]["id"], "Ryszard Bąk", T, today=T)
    html = certificate_html(cert, "Firma Demo sp. z o.o.", "http://test/verify/" + cert["verification_code"])
    for fragment in ("ZAŚWIADCZENIE", cert["number"], "Ewa Zielińska", "bezterminowo", cert["verification_code"],
                     "Ryszard Bąk", "16.03.2026"):
        assert fragment in html
