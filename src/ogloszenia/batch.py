"""Tryb wsadowy: wiele ogłoszeń (JSON lub CSV) → raport zbiorczy z najczęstszymi problemami.

Uruchomienie z linii poleceń: `ogloszenia-wsad plik.csv` albo `python -m ogloszenia.batch plik.json --json`.
Tryb wsadowy korzysta wyłącznie z silnika reguł (deterministycznie, bez kosztów API).
"""
from __future__ import annotations

import argparse
import csv
import io
import json
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Literal

from .engine import run_rules
from .models import CATEGORY_LABELS, BatchInput, BatchItemResult, BatchReport, CategoryStat, RuleStat
from .rules import RULES_BY_ID
from .scoring import VERDICTS, compute_score, count_severities, verdict

MAX_ITEMS = 500
MAX_TEXT = 20_000
TEXT_KEYS = ("text", "tresc", "treść", "ogloszenie", "ogłoszenie", "opis", "content", "body")
TITLE_KEYS = ("title", "tytul", "tytuł", "stanowisko", "nazwa")
ID_KEYS = ("id", "nr", "numer", "lp")


class BatchFormatError(ValueError):
    """Nieprawidłowy plik wsadowy (komunikat po polsku dla użytkownika)."""


def _pick(row: dict[str, Any], keys: tuple[str, ...]) -> Any:
    lowered = {str(k).strip().lower(): v for k, v in row.items() if k is not None}
    for key in keys:
        if key in lowered and lowered[key] not in (None, ""):
            return lowered[key]
    return None


def _to_items(rows: list[Any]) -> tuple[list[BatchInput], list[str]]:
    items: list[BatchInput] = []
    skipped: list[str] = []
    for index, row in enumerate(rows, start=1):
        if isinstance(row, str):
            text, title, item_id = row, None, None
        elif isinstance(row, dict):
            text, title, item_id = _pick(row, TEXT_KEYS), _pick(row, TITLE_KEYS), _pick(row, ID_KEYS)
        else:
            skipped.append(f"Pozycja {index}: nieobsługiwany format — pominięto.")
            continue
        text = str(text or "").strip()
        if not text:
            skipped.append(f"Pozycja {index}: pusta treść ogłoszenia — pominięto.")
            continue
        if len(text) > MAX_TEXT:
            skipped.append(f"Pozycja {index}: treść dłuższa niż {MAX_TEXT} znaków — pominięto.")
            continue
        items.append(BatchInput(id=str(item_id or index), title=str(title) if title else None, text=text))
    return items, skipped


def parse_json(content: str) -> tuple[list[BatchInput], list[str]]:
    try:
        data = json.loads(content)
    except json.JSONDecodeError as exc:
        raise BatchFormatError(f"Niepoprawny JSON (linia {exc.lineno}, kolumna {exc.colno}).") from exc
    if isinstance(data, dict):
        data = data.get("items") or data.get("ogloszenia") or data.get("ogłoszenia")
    if not isinstance(data, list):
        raise BatchFormatError("JSON musi być listą ogłoszeń albo obiektem z kluczem „items”.")
    return _to_items(data)


def parse_csv(content: str) -> tuple[list[BatchInput], list[str]]:
    content = content.lstrip("﻿")
    if not content.strip():
        raise BatchFormatError("Plik CSV jest pusty.")
    try:
        dialect = csv.Sniffer().sniff(content[:4096], delimiters=",;\t")
        delimiter = dialect.delimiter
    except csv.Error:
        delimiter = ","
    reader = csv.DictReader(io.StringIO(content), delimiter=delimiter)
    headers = [str(h).strip().lower() for h in (reader.fieldnames or [])]
    if not any(h in TEXT_KEYS for h in headers):
        raise BatchFormatError(
            "Nie znaleziono kolumny z treścią ogłoszenia (oczekiwana nazwa: „tresc”, „text” lub „ogloszenie”)."
        )
    try:
        rows = list(reader)
    except csv.Error as exc:
        raise BatchFormatError(f"Niepoprawny CSV: {exc}.") from exc
    return _to_items(rows)


def parse_batch(content: str, fmt: Literal["json", "csv", "auto"] = "auto") -> tuple[list[BatchInput], list[str]]:
    if fmt == "auto":
        fmt = "json" if content.lstrip("﻿ \n\t").startswith(("[", "{")) else "csv"
    items, skipped = parse_json(content) if fmt == "json" else parse_csv(content)
    if len(items) > MAX_ITEMS:
        raise BatchFormatError(f"Za dużo ogłoszeń w jednym wsadzie (maks. {MAX_ITEMS}).")
    return items, skipped


def run_batch(items: list[BatchInput], skipped: list[str] | None = None) -> BatchReport:
    if not items:
        raise BatchFormatError("Brak ogłoszeń do analizy.")
    results: list[BatchItemResult] = []
    rule_ads: Counter[str] = Counter()
    rule_hits: Counter[str] = Counter()
    cat_ads: Counter[str] = Counter()
    cat_hits: Counter[str] = Counter()
    for item in items:
        findings = run_rules(item.text)
        score = compute_score(findings)
        counts = count_severities(findings)
        code = verdict(score, counts)
        rules_here: dict[str, int] = defaultdict(int)
        for f in findings:
            rules_here[f.rule_id] += 1
            cat_hits[f.category] += 1
        for rule_id, n in rules_here.items():
            rule_ads[rule_id] += 1
            rule_hits[rule_id] += n
        for category in {f.category for f in findings}:
            cat_ads[category] += 1
        results.append(BatchItemResult(
            id=item.id, title=item.title, score=score, verdict=code, verdict_label=VERDICTS[code],
            errors=counts.error, warnings=counts.warning, suggestions=counts.suggestion,
            rules=sorted(rules_here),
        ))
    severity_rank = {"error": 0, "warning": 1, "suggestion": 2}
    top = sorted(
        rule_ads,
        key=lambda rid: (-rule_ads[rid], severity_rank[RULES_BY_ID[rid].severity], rid),
    )[:10]
    scores = [r.score for r in results]
    return BatchReport(
        count=len(results),
        average_score=round(statistics.fmean(scores), 1),
        median_score=float(statistics.median(scores)),
        min_score=min(scores),
        max_score=max(scores),
        with_errors=sum(1 for r in results if r.errors),
        verdicts={code: sum(1 for r in results if r.verdict == code) for code in VERDICTS},
        top_rules=[
            RuleStat(
                rule_id=rid, title=RULES_BY_ID[rid].title, category=RULES_BY_ID[rid].category,
                severity=RULES_BY_ID[rid].severity, ads=rule_ads[rid], occurrences=rule_hits[rid],
                share=round(rule_ads[rid] / len(results), 3),
            )
            for rid in top
        ],
        categories=[
            CategoryStat(category=cat, label=label, findings=cat_hits[cat], ads=cat_ads[cat])
            for cat, label in CATEGORY_LABELS.items()
        ],
        items=sorted(results, key=lambda r: (r.score, r.id)),
        skipped=skipped or [],
    )


def format_report(report: BatchReport) -> str:
    lines = [
        f"Ogłoszeń: {report.count} | średni wynik: {report.average_score} | mediana: {report.median_score} "
        f"| z błędami: {report.with_errors}",
        "",
        "Najczęstsze problemy:",
    ]
    for stat in report.top_rules:
        lines.append(f"  {stat.share:>6.0%}  {stat.title} ({stat.ads} ogł.)")
    lines += ["", "Ogłoszenia (od najsłabszych):"]
    for item in report.items:
        label = item.title or item.id
        lines.append(f"  {item.score:>3}  {item.verdict_label:<28} {label}")
    for note in report.skipped:
        lines.append(f"  ! {note}")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Audyt wielu ogłoszeń o pracę (CSV lub JSON).")
    parser.add_argument("plik", type=Path, help="plik .csv lub .json z ogłoszeniami")
    parser.add_argument("--json", action="store_true", help="wypisz raport jako JSON")
    args = parser.parse_args(argv)
    try:
        content = args.plik.read_text(encoding="utf-8")
        fmt: Literal["json", "csv", "auto"] = {".json": "json", ".csv": "csv"}.get(args.plik.suffix.lower(), "auto")
        items, skipped = parse_batch(content, fmt)
        report = run_batch(items, skipped)
    except (OSError, BatchFormatError) as exc:
        print(f"Błąd: {exc}", file=sys.stderr)
        return 1
    print(report.model_dump_json(indent=2) if args.json else format_report(report))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
