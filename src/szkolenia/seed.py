"""Dane demonstracyjne: fikcyjna firma, historia szkoleń liczona względem dzisiejszej daty.

Historia powstaje przez prawdziwe operacje serwisu (testy, potwierdzenia, wpisy z ewidencji),
dzięki czemu demo pokazuje wszystkie statusy: aktualne, wygasa, przeterminowane, nieukończone.
"""
from __future__ import annotations

import argparse
import json
import os
import unicodedata
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from pathlib import Path

from .compliance import add_months
from .db import Store
from .service import Settings, TrainingService

DEFAULT_DATA_DIR = Path(__file__).resolve().parents[2] / "data"


def data_dir() -> Path:
    return Path(os.getenv("SZKOLENIA_DATA_DIR") or DEFAULT_DATA_DIR)


def load_company(path: Path | None = None) -> dict:
    base = path or data_dir()
    company = json.loads((base / "firma-demo.json").read_text(encoding="utf-8"))
    for t in company["trainings"]:
        t["material_text"] = (base / "materialy" / t["material"]).read_text(encoding="utf-8")
    return company


def email_for(full_name: str) -> str:
    ascii_name = unicodedata.normalize("NFKD", full_name.replace("ł", "l").replace("Ł", "L"))
    ascii_name = "".join(ch for ch in ascii_name if not unicodedata.combining(ch))
    return ".".join(ascii_name.lower().split()) + "@firma-demo.example"


@dataclass(frozen=True)
class Step:
    employee: str
    training: str
    action: str  # test | fail | external | confirm | open
    on: date  # data zdarzenia (dla „open”: data przypisania)
    due: date | None = None


def _history(T: date, hires: dict[str, date]) -> list[Step]:
    """Scenariusze dobrane tak, by każdy status wystąpił kilka razy."""
    def ago(days: int = 0, months: int = 0) -> date:
        return add_months(T, -months) - timedelta(days=days)

    def valid_until_in(days: int, validity: int) -> date:
        # data ukończenia, dla której ważność kończy się T + days
        return add_months(T + timedelta(days=days), -validity)

    h = hires
    return [
        # Agnieszka — dyrektor: wszystko aktualne (BHP kierujących ważne jeszcze 45 dni)
        Step("agnieszka", "rodo", "test", ago(months=5)),
        Step("agnieszka", "sygnalisci", "external", ago(months=13)),
        Step("agnieszka", "antymobbing", "test", ago(months=11)),
        Step("agnieszka", "bhp-kierujacy", "external", valid_until_in(45, 60)),
        # Tomasz — kierownik magazynu: antymobbing wygasa za 25 dni
        Step("tomasz", "instruktaz", "confirm", h["tomasz"]),
        Step("tomasz", "rodo", "test", ago(months=3)),
        Step("tomasz", "sygnalisci", "external", ago(months=12)),
        Step("tomasz", "antymobbing", "external", valid_until_in(25, 24)),
        Step("tomasz", "bhp-kierujacy", "external", ago(months=48)),
        # Marta — kierowniczka sprzedaży: BHP kierujących wygasło 5 dni temu
        Step("marta", "bhp-kierujacy", "external", valid_until_in(-5, 60)),
        Step("marta", "rodo", "test", ago(months=4)),
        Step("marta", "sygnalisci", "external", ago(months=12)),
        Step("marta", "antymobbing", "test", ago(months=8)),
        # Joanna — kadry: komplet aktualny
        Step("joanna", "bhp-administracyjne", "external", h["joanna"] + timedelta(days=60)),
        Step("joanna", "rodo", "test", ago(months=1)),
        Step("joanna", "sygnalisci", "external", ago(months=12)),
        Step("joanna", "antymobbing", "test", ago(months=6)),
        # Paweł — magazynier: RODO wygasło 20 dni temu, BHP okresowe wygasa za 12 dni
        Step("pawel", "instruktaz", "confirm", h["pawel"]),
        Step("pawel", "bhp-robotnicze", "external", valid_until_in(12, 36)),
        Step("pawel", "rodo", "test", valid_until_in(-20, 12)),
        Step("pawel", "sygnalisci", "external", ago(months=12)),
        Step("pawel", "antymobbing", "test", ago(months=10)),
        # Katarzyna — operatorka wózka: antymobbing po terminie mimo próby
        Step("katarzyna", "instruktaz", "confirm", h["katarzyna"]),
        Step("katarzyna", "bhp-robotnicze", "test", ago(months=14)),
        Step("katarzyna", "rodo", "test", ago(months=2)),
        Step("katarzyna", "sygnalisci", "external", ago(months=12)),
        Step("katarzyna", "antymobbing", "open", ago(days=40), due=ago(days=10)),
        Step("katarzyna", "antymobbing", "fail", ago(days=12)),
        # Bartosz — handlowiec: RODO wygasa za 5 dni
        Step("bartosz", "rodo", "test", valid_until_in(5, 12)),
        Step("bartosz", "bhp-administracyjne", "external", h["bartosz"] + timedelta(days=20)),
        Step("bartosz", "sygnalisci", "external", h["bartosz"] + timedelta(days=10)),
        Step("bartosz", "antymobbing", "test", h["bartosz"] + timedelta(days=20)),
        # Michał — nowy księgowy: sygnaliści po terminie (test jeszcze niezatwierdzony)
        Step("michal", "rodo", "test", ago(days=70)),
        Step("michal", "antymobbing", "test", ago(days=60)),
        # Natalia — nowa handlowczyni: RODO zaliczone, antymobbing niezaliczony za pierwszym razem
        Step("natalia", "rodo", "test", ago(days=15)),
        Step("natalia", "antymobbing", "fail", ago(days=2)),
        # Kamil — nowy magazynier: instruktaż w dniu zatrudnienia, reszta w toku
        Step("kamil", "instruktaz", "confirm", h["kamil"]),
    ]


def seed_demo(service: TrainingService, today: date, company: dict | None = None) -> dict:
    """Wypełnia pustą bazę danymi demo. Zwraca podsumowanie."""
    company = company or load_company()
    store = service.store
    trainings: dict[str, int] = {}
    for t in company["trainings"]:
        created = service.create_training({
            "title": t["title"], "category": t["category"], "validity_months": t["validity_months"],
            "pass_threshold": t["pass_threshold"], "mode": t["mode"], "material": t["material_text"],
            "questions_per_test": t["questions_per_test"],
        })
        trainings[t["key"]] = created["id"]

    hires = {e["key"]: today - timedelta(days=e["hired_days_ago"]) for e in company["employees"]}
    employees: dict[str, int] = {}

    def add_employee(e: dict) -> None:
        emp, _ = service.create_employee({
            "full_name": e["full_name"], "email": email_for(e["full_name"]), "department": e["department"],
            "position": e["position"], "hire_date": hires[e["key"]],
            "manager_id": employees.get(e["manager"]) if e["manager"] else None,
        }, today=hires[e["key"]])
        employees[e["key"]] = emp["id"]

    # Dotychczasowi pracownicy trafiają do bazy przed regułami — ich historię odtwarzają kroki poniżej.
    for e in company["employees"]:
        if not e.get("new_hire"):
            add_employee(e)
    now = datetime.now().isoformat(timespec="seconds")
    for r in company["rules"]:
        store.insert(
            "INSERT INTO assignment_rules (training_id, department, position, due_days, created_at) VALUES (?, ?, ?, ?, ?)",
            (trainings[r["training"]], r["department"], r["position"], r["due_days"], now),
        )

    # Pytania: zawsze generator offline (powtarzalne demo, bez kosztów API przy starcie);
    # większość zatwierdzona, część zostaje do przeglądu przez HR.
    original_factory = service.llm_factory
    service.llm_factory = lambda: None
    try:
        for t in company["trainings"]:
            if t["mode"] != "elearning":
                continue
            generated = service.generate_questions(trainings[t["key"]], count=12)["created"]
            if t["key"] == "sygnalisci":
                continue  # demo: HR musi przejrzeć i zatwierdzić pytania, zanim test będzie dostępny
            keep_drafts = 2 if t["key"] == "antymobbing" else 0
            for q in generated[: len(generated) - keep_drafts]:
                service.approve_question(q["id"], reviewer=company["reviewer"])
    finally:
        service.llm_factory = original_factory

    # Nowi pracownicy: automatyczne przypisanie z reguł (termin = zatrudnienie + N dni).
    for e in company["employees"]:
        if e.get("new_hire"):
            add_employee(e)

    for index, step in enumerate(sorted(_history(today, hires), key=lambda s: s.on)):
        _apply(service, step, employees[step.employee], trainings[step.training], company, seed=1000 + index)

    daily = service.run_daily(today)
    return {"employees": len(employees), "trainings": len(trainings), **daily}


def _assignment_for(service: TrainingService, employee_id: int, training_id: int, on: date,
                    due: date | None = None) -> int:
    row = service.store.one(
        "SELECT id FROM assignments WHERE employee_id=? AND training_id=? AND status='open'", (employee_id, training_id)
    )
    if row:
        return row["id"]
    return service._insert_assignment(
        employee_id, training_id, None, "rule", on - timedelta(days=14), due or on + timedelta(days=14)
    )["id"]


def _apply(service: TrainingService, step: Step, employee_id: int, training_id: int, company: dict, seed: int) -> None:
    if step.action == "open":
        _assignment_for(service, employee_id, training_id, step.on + timedelta(days=14), step.due)
        return
    assignment_id = _assignment_for(service, employee_id, training_id, step.on)
    if step.action == "confirm":
        service.confirm_attendance(assignment_id, company["trainer"], step.on, today=step.on)
    elif step.action == "external":
        service.record_external_completion(assignment_id, step.on, "przeniesione z papierowej ewidencji", today=step.on)
    elif step.action in ("test", "fail"):
        start = datetime.combine(step.on, time(10, 0))
        quiz = service.start_attempt(assignment_id, now=start, seed=seed)
        attempt = service.store.one("SELECT * FROM attempts WHERE id=?", (quiz["attempt_id"],))
        items = service._attempt_items(attempt)
        if step.action == "test":
            answers = {it.question.id: it.correct_display_index for it in items}
        else:  # dwie poprawne, reszta błędna — wynik poniżej progu
            answers = {it.question.id: (it.correct_display_index if i < 2 else (it.correct_display_index + 1) % 4)
                       for i, it in enumerate(items)}
        service.submit_attempt(quiz["attempt_id"], answers, today=step.on, now=start + timedelta(minutes=9))


def main() -> None:
    parser = argparse.ArgumentParser(description="Wypełnia bazę danymi demonstracyjnymi.")
    parser.add_argument("--db", default=os.getenv("SZKOLENIA_DB", "szkolenia.db"))
    parser.add_argument("--today", help="data „dziś” (RRRR-MM-DD), domyślnie bieżąca")
    parser.add_argument("--reset", action="store_true", help="usuń istniejący plik bazy")
    args = parser.parse_args()
    if args.reset and args.db != ":memory:" and Path(args.db).exists():
        Path(args.db).unlink()
    store = Store(args.db)
    service = TrainingService(store, settings=Settings(company_name=load_company()["company"]),
                              llm_factory=lambda: None)
    if service.stats()["employees"]:
        raise SystemExit("Baza nie jest pusta — użyj --reset.")
    today = date.fromisoformat(args.today) if args.today else date.today()
    print(json.dumps(seed_demo(service, today), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
