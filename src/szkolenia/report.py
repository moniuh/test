"""Eksport raportu zgodności (CSV, drukowalny HTML) i strony zaświadczeń."""
from __future__ import annotations

import csv
import io
from datetime import date
from html import escape

CSV_COLUMNS = [
    "Pracownik", "E-mail", "Dział", "Stanowisko", "Szkolenie", "Kategoria", "Status",
    "Data ukończenia", "Ważne do", "Termin", "Nr zaświadczenia",
]


def fmt(d: str | None) -> str:
    """ISO → DD.MM.RRRR (format oczekiwany w polskiej dokumentacji)."""
    if not d:
        return ""
    return date.fromisoformat(d).strftime("%d.%m.%Y")


def matrix_to_csv(matrix: dict) -> str:
    """Jeden wiersz na parę pracownik × wymagane szkolenie. Średnik i BOM — Excel w polskiej wersji."""
    out = io.StringIO()
    out.write("﻿")
    writer = csv.writer(out, delimiter=";", lineterminator="\r\n")
    writer.writerow(CSV_COLUMNS)
    trainings = {str(t["id"]): t for t in matrix["trainings"]}
    for row in matrix["rows"]:
        e = row["employee"]
        for tid, cell in row["cells"].items():
            if not cell["required"]:
                continue
            t = trainings[tid]
            writer.writerow([
                e["full_name"], e["email"], e["department"], e["position"], t["title"], t["category"],
                cell["status_label"], fmt(cell["completed_on"]),
                fmt(cell["valid_until"]) if cell["completed_on"] else "",
                fmt(cell["due_date"]), cell["certificate_number"] or "",
            ])
    return out.getvalue()


_PRINT_CSS = """
:root { --ink:#1d2433; --muted:#5b6475; --line:#d5d9e2; --ok:#1f7a4d; --warn:#9a6200; --bad:#b42318; --todo:#3d5a99; }
* { box-sizing: border-box; }
body { font-family: "Segoe UI", Roboto, Arial, sans-serif; color: var(--ink); margin: 24px; font-size: 13px; background: #fff; }
h1 { font-size: 20px; margin: 0 0 4px; }
.muted { color: var(--muted); }
table { border-collapse: collapse; width: 100%; margin-top: 16px; }
th, td { border: 1px solid var(--line); padding: 6px 8px; text-align: left; vertical-align: top; }
th { background: #f3f5f9; font-weight: 600; }
.s-valid { color: var(--ok); } .s-expiring { color: var(--warn); } .s-overdue { color: var(--bad); font-weight: 600; }
.s-incomplete { color: var(--todo); } .s-not_applicable { color: var(--muted); }
.small { font-size: 11px; color: var(--muted); display: block; }
.toolbar { margin-bottom: 16px; }
button { font: inherit; padding: 6px 14px; border: 1px solid var(--line); border-radius: 6px; background: #fff; cursor: pointer; }
.sign { margin-top: 40px; display: flex; gap: 48px; }
.sign div { border-top: 1px solid var(--ink); padding-top: 4px; min-width: 220px; }
@media print { .toolbar { display: none; } body { margin: 10mm; } @page { size: A4 landscape; } }
"""


def matrix_to_html(matrix: dict, generated_at: str) -> str:
    trainings = matrix["trainings"]
    head = "".join(f"<th>{escape(t['title'])}</th>" for t in trainings)
    body = []
    for row in matrix["rows"]:
        e = row["employee"]
        cells = []
        for t in trainings:
            c = row["cells"][str(t["id"])]
            details = []
            if c["completed_on"]:
                details.append(f"ukończone {fmt(c['completed_on'])}")
                details.append(f"ważne do {fmt(c['valid_until'])}" if c["valid_until"] else "bezterminowo")
            if c["due_date"]:
                details.append(f"termin {fmt(c['due_date'])}")
            if c["certificate_number"]:
                details.append(f"nr {escape(c['certificate_number'])}")
            small = "".join(f"<span class='small'>{d}</span>" for d in details)
            cells.append(f"<td><span class='s-{c['status']}'>{escape(c['status_label'])}</span>{small}</td>")
        body.append(
            f"<tr><td><strong>{escape(e['full_name'])}</strong><span class='small'>{escape(e['position'])}</span></td>"
            f"<td>{escape(e['department'])}</td><td>{row['compliance_percent']}%</td>{''.join(cells)}</tr>"
        )
    scope = f"dział: {escape(matrix['department'])}" if matrix.get("department") else "wszystkie działy"
    return f"""<!doctype html>
<html lang="pl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Raport zgodności szkoleń</title><style>{_PRINT_CSS}</style></head>
<body>
<div class="toolbar"><button onclick="window.print()">Drukuj</button></div>
<h1>Raport zgodności szkoleń obowiązkowych</h1>
<div class="muted">{escape(matrix['company'])} · stan na {fmt(matrix['today'])} · {scope} · wygenerowano {escape(generated_at)}</div>
<table>
<thead><tr><th>Pracownik</th><th>Dział</th><th>Zgodność</th>{head}</tr></thead>
<tbody>{''.join(body)}</tbody>
</table>
<p class="muted">Legenda: aktualne — ważne dłużej niż 30 dni; wygasa ≤ 30 dni; przeterminowane — po terminie albo po upływie ważności;
nieukończone — w terminie, jeszcze bez zaliczenia; nie dotyczy — brak reguły przypisania.</p>
<div class="sign"><div>Sporządził(a)</div><div>Zatwierdził(a)</div></div>
</body></html>"""


_CERT_CSS = """
* { box-sizing: border-box; }
body { font-family: Georgia, "Times New Roman", serif; color: #1d2433; background: #eef1f6; margin: 0; padding: 24px; }
.sheet { background: #fff; max-width: 800px; margin: 0 auto; padding: 48px 56px; border: 1px solid #d5d9e2; }
.frame { border: 3px double #3d5a99; padding: 32px; }
.company { font-family: Arial, sans-serif; font-size: 13px; color: #5b6475; text-transform: uppercase; letter-spacing: .08em; }
h1 { font-size: 30px; letter-spacing: .12em; margin: 24px 0 4px; text-align: center; }
.number { text-align: center; font-family: Arial, sans-serif; color: #5b6475; }
.lead { text-align: center; margin-top: 28px; font-size: 16px; }
.name { text-align: center; font-size: 26px; margin: 8px 0 4px; }
.training { text-align: center; font-size: 20px; font-weight: bold; margin: 8px 0 24px; }
dl { display: grid; grid-template-columns: max-content 1fr; gap: 6px 16px; font-family: Arial, sans-serif; font-size: 14px; }
dt { color: #5b6475; }
.verify { margin-top: 28px; padding: 12px 16px; background: #f3f5f9; font-family: Arial, sans-serif; font-size: 13px; }
.code { font-family: "Courier New", monospace; font-size: 18px; font-weight: bold; letter-spacing: .1em; }
.sign { margin-top: 48px; display: flex; justify-content: flex-end; font-family: Arial, sans-serif; font-size: 12px; }
.sign div { border-top: 1px solid #1d2433; padding-top: 4px; min-width: 240px; text-align: center; }
.toolbar { max-width: 800px; margin: 0 auto 12px; font-family: Arial, sans-serif; }
button { font: inherit; padding: 6px 14px; border: 1px solid #d5d9e2; border-radius: 6px; background: #fff; cursor: pointer; }
@media print { body { background: #fff; padding: 0; } .toolbar { display: none; } .sheet { border: 0; padding: 0; } }
@media (max-width: 600px) { .sheet { padding: 20px; } .frame { padding: 16px; } h1 { font-size: 22px; } }
"""


def certificate_html(cert: dict, company: str, verify_url: str) -> str:
    if cert["method"] == "test" and cert.get("attempt_percent") is not None:
        result = f"test online zaliczony: {cert['attempt_percent']}% (próg {cert['pass_threshold']}%)"
    elif cert["method"] == "confirmation":
        result = f"udział potwierdzony przez prowadzącego: {escape(cert['confirmed_by'] or '')}"
    else:
        result = f"wpis z ewidencji{': ' + escape(cert['note']) if cert.get('note') else ''}"
    valid = fmt(cert["valid_until"]) if cert["valid_until"] else "bezterminowo"
    return f"""<!doctype html>
<html lang="pl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Zaświadczenie {escape(cert['number'])}</title><style>{_CERT_CSS}</style></head>
<body>
<div class="toolbar"><button onclick="window.print()">Drukuj</button></div>
<div class="sheet"><div class="frame">
<div class="company">{escape(company)}</div>
<h1>ZAŚWIADCZENIE</h1>
<div class="number">nr {escape(cert['number'])}</div>
<p class="lead">o ukończeniu szkolenia. Niniejszym potwierdza się, że</p>
<p class="name">{escape(cert['full_name'])}</p>
<p class="lead">{escape(cert['position'])}, dział {escape(cert['department'])}, ukończył(a) szkolenie</p>
<p class="training">{escape(cert['training_title'])}</p>
<dl>
<dt>Kategoria</dt><dd>{escape(cert['category'])}</dd>
<dt>Data ukończenia</dt><dd>{fmt(cert['issued_on'])}</dd>
<dt>Ważne do</dt><dd>{valid}</dd>
<dt>Sposób zaliczenia</dt><dd>{result}</dd>
</dl>
<div class="verify">Autentyczność sprawdzisz pod adresem <strong>{escape(verify_url)}</strong><br>
Kod weryfikacyjny: <span class="code">{escape(cert['verification_code'])}</span></div>
<div class="sign"><div>podpis osoby upoważnionej</div></div>
</div></div>
</body></html>"""


def verification_html(result: dict | None, code: str) -> str:
    if result is None:
        body = (f"<p class='bad'>Nie znaleziono zaświadczenia o kodzie <strong>{escape(code)}</strong>.</p>"
                "<p>Sprawdź, czy kod został przepisany bez błędów.</p>")
    else:
        state = "ok" if result["is_valid"] else "bad"
        valid = fmt(result["valid_until"]) if result["valid_until"] else "bezterminowo"
        body = f"""<p class="{state}">Zaświadczenie istnieje — status: <strong>{escape(result['status_label'])}</strong></p>
<dl><dt>Numer</dt><dd>{escape(result['number'])}</dd>
<dt>Osoba</dt><dd>{escape(result['full_name'])}</dd>
<dt>Szkolenie</dt><dd>{escape(result['training_title'])}</dd>
<dt>Data ukończenia</dt><dd>{fmt(result['issued_on'])}</dd>
<dt>Ważne do</dt><dd>{valid}</dd></dl>"""
    return f"""<!doctype html>
<html lang="pl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Weryfikacja zaświadczenia</title>
<style>body{{font-family:Arial,sans-serif;background:#eef1f6;color:#1d2433;margin:0;padding:24px}}
main{{max-width:560px;margin:0 auto;background:#fff;border:1px solid #d5d9e2;border-radius:10px;padding:24px}}
h1{{font-size:20px;margin-top:0}}.ok{{color:#1f7a4d}}.bad{{color:#b42318}}
dl{{display:grid;grid-template-columns:max-content 1fr;gap:6px 16px}}dt{{color:#5b6475}}</style></head>
<body><main><h1>Weryfikacja zaświadczenia</h1>{body}</main></body></html>"""
