"""Przykładowe (fikcyjne) ogłoszenia z katalogu `examples/`."""
from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import Any

_SAFE_ID = re.compile(r"^[a-z0-9-]{1,64}$")


def examples_dir() -> Path:
    """Katalog przykładów: `OGLOSZENIA_EXAMPLES_DIR` albo `examples/` w katalogu repozytorium."""
    override = os.getenv("OGLOSZENIA_EXAMPLES_DIR")
    return Path(override) if override else Path(__file__).resolve().parents[2] / "examples"


def _index(base: Path | None = None) -> dict[str, Any]:
    path = (base or examples_dir()) / "index.json"
    if not path.is_file():
        return {"examples": [], "batch": {}}
    return json.loads(path.read_text(encoding="utf-8"))


def list_examples(base: Path | None = None) -> list[dict[str, str]]:
    return [
        {k: e[k] for k in ("id", "title", "kind", "description")}
        for e in _index(base).get("examples", [])
    ]


def load_example(example_id: str, base: Path | None = None) -> dict[str, str] | None:
    if not _SAFE_ID.match(example_id):
        return None
    base = base or examples_dir()
    for entry in _index(base).get("examples", []):
        if entry["id"] == example_id:
            text = (base / entry["file"]).read_text(encoding="utf-8")
            return {"id": entry["id"], "title": entry["title"], "kind": entry["kind"], "text": text}
    return None


def batch_sample(fmt: str, base: Path | None = None) -> str | None:
    base = base or examples_dir()
    name = _index(base).get("batch", {}).get(fmt)
    if not name:
        return None
    return (base / name).read_text(encoding="utf-8")
