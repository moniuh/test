"""Wczytywanie dokumentów (Markdown, TXT, DOCX, opcjonalnie PDF) i podział na fragmenty.

Fragment odpowiada sekcji dokumentu wyznaczonej przez nagłówki Markdown lub wiersze
zaczynające się od „Rozdział …” / „§ …”. Każdy fragment niesie ścieżkę sekcji,
np. „Regulamin pracy › Rozdział III. Czas pracy › § 6. Norma i system czasu pracy”.
"""
from __future__ import annotations

import io
import re
import zipfile
from dataclasses import dataclass, field
from datetime import date
from xml.etree import ElementTree

PATH_SEPARATOR = " › "
SUPPORTED_EXTENSIONS = (".md", ".markdown", ".txt", ".docx", ".pdf")
MAX_CHUNK_CHARS = 1400


class DocumentError(ValueError):
    """Dokumentu nie da się wczytać; komunikat jest gotowy do pokazania użytkownikowi."""


@dataclass
class ParsedDocument:
    title: str | None
    doc_key: str | None
    version: str | None
    effective_from: date | None
    body: str
    metadata: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class ChunkDraft:
    ordinal: int
    section_path: tuple[str, ...]
    text: str

    @property
    def path_label(self) -> str:
        return PATH_SEPARATOR.join(self.section_path)


_FRONT_KEYS = {
    "tytul": "title", "tytuł": "title", "title": "title",
    "kod": "doc_key", "code": "doc_key", "doc_key": "doc_key",
    "wersja": "version", "version": "version",
    "obowiazuje_od": "effective_from", "obowiązuje_od": "effective_from", "effective_from": "effective_from",
}


def parse_date(value: str) -> date:
    """Data w formacie RRRR-MM-DD; błąd z czytelnym komunikatem."""
    try:
        return date.fromisoformat(value.strip())
    except ValueError as exc:
        raise DocumentError(f"Nieprawidłowa data „{value}” — oczekiwany format RRRR-MM-DD") from exc


def parse_front_matter(text: str) -> ParsedDocument:
    """Odczytuje opcjonalny nagłówek `---` z metadanymi (tytul, kod, wersja, obowiazuje_od)."""
    text = text.lstrip("﻿")
    meta: dict[str, str] = {}
    body = text
    if text.startswith("---"):
        lines = text.splitlines()
        for end in range(1, len(lines)):
            if lines[end].strip() == "---":
                for line in lines[1:end]:
                    if ":" in line:
                        key, value = line.split(":", 1)
                        meta[key.strip().lower()] = value.strip()
                body = "\n".join(lines[end + 1 :])
                break
    fields = {_FRONT_KEYS[k]: v for k, v in meta.items() if k in _FRONT_KEYS and v}
    effective = parse_date(fields["effective_from"]) if "effective_from" in fields else None
    return ParsedDocument(
        title=fields.get("title"),
        doc_key=fields.get("doc_key"),
        version=fields.get("version"),
        effective_from=effective,
        body=body.strip("\n"),
        metadata=meta,
    )


def decode_text(data: bytes) -> str:
    """UTF-8, a gdy się nie da — Windows-1250 (częste w polskich plikach TXT)."""
    try:
        return data.decode("utf-8-sig")
    except UnicodeDecodeError:
        return data.decode("cp1250", errors="replace")


_W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
_HEADING_STYLE_RE = re.compile(r"(heading|nag[łl]?[óo]?wek)\s*(\d)", re.IGNORECASE)


def docx_to_markdown(data: bytes) -> str:
    """Tekst z DOCX (zipfile + XML), style nagłówków zamienione na nagłówki Markdown."""
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            xml = archive.read("word/document.xml")
    except (zipfile.BadZipFile, KeyError) as exc:
        raise DocumentError("Plik DOCX jest uszkodzony lub nie zawiera treści dokumentu") from exc
    root = ElementTree.fromstring(xml)
    lines: list[str] = []
    for para in root.iter(f"{_W}p"):
        text = "".join(node.text or "" for node in para.iter(f"{_W}t")).strip()
        if not text:
            continue
        style = para.find(f"{_W}pPr/{_W}pStyle")
        style_name = style.get(f"{_W}val", "") if style is not None else ""
        match = _HEADING_STYLE_RE.search(style_name)
        if style_name.lower() == "title":
            lines.append(f"# {text}")
        elif match:
            lines.append(f"{'#' * min(int(match.group(2)) + 1, 6)} {text}")
        else:
            lines.append(text)
        lines.append("")
    return "\n".join(lines)


def pdf_to_text(data: bytes) -> str:
    """Tekst z PDF — wymaga opcjonalnego pakietu pypdf."""
    try:
        from pypdf import PdfReader  # noqa: PLC0415 — zależność opcjonalna
    except ImportError as exc:
        raise DocumentError(
            "Obsługa PDF wymaga pakietu pypdf (pip install -e '.[pdf]'). Prześlij dokument jako Markdown lub TXT."
        ) from exc
    try:
        reader = PdfReader(io.BytesIO(data))
        return "\n".join(page.extract_text() or "" for page in reader.pages)
    except Exception as exc:  # pypdf rzuca wiele typów błędów
        raise DocumentError("Nie udało się odczytać tekstu z pliku PDF") from exc


def load_document(filename: str, data: bytes) -> ParsedDocument:
    """Zamienia plik na ParsedDocument (treść w Markdown/tekście + metadane z nagłówka)."""
    name = filename.lower()
    if not name.endswith(SUPPORTED_EXTENSIONS):
        raise DocumentError("Nieobsługiwany format pliku — dozwolone: .md, .txt, .docx, .pdf")
    if name.endswith(".docx"):
        text = docx_to_markdown(data)
    elif name.endswith(".pdf"):
        text = pdf_to_text(data)
    else:
        text = decode_text(data)
    if not text.strip():
        raise DocumentError("Dokument jest pusty")
    return parse_front_matter(text)


_MD_HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$")
_CHAPTER_RE = re.compile(r"^(rozdział|rozdzial|dział)\s+[0-9IVXLC]+\b.*$", re.IGNORECASE)
_PARAGRAPH_RE = re.compile(r"^§\s*\d+[a-z]?\b.*$")
_PARAGRAPH_LEVEL = 6
_CHAPTER_LEVEL = 2


def _heading(line: str) -> tuple[int, str] | None:
    stripped = line.strip()
    match = _MD_HEADING_RE.match(stripped)
    if match:
        return len(match.group(1)), match.group(2).strip()
    if _PARAGRAPH_RE.match(stripped) and len(stripped) <= 120:
        return _PARAGRAPH_LEVEL, stripped
    if _CHAPTER_RE.match(stripped) and len(stripped) <= 120:
        return _CHAPTER_LEVEL, stripped
    return None


def guess_title(body: str) -> str | None:
    """Tytuł z nagłówka H1 albo z pierwszego niepustego wiersza."""
    for line in body.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        heading = _heading(stripped)
        if heading and heading[0] == 1:
            return heading[1]
        if heading is None:
            return stripped[:120]
        return None
    return None


def _split_long(text: str, limit: int) -> list[str]:
    """Dzieli zbyt długą sekcję na części po akapitach/punktach, nie rozcinając punktów."""
    if len(text) <= limit:
        return [text]
    blocks = [b for b in re.split(r"\n\s*\n|\n(?=\s*(?:\d+[.)]|[-*•])\s)", text) if b.strip()]
    parts: list[str] = []
    current = ""
    for block in blocks:
        block = block.strip()
        if current and len(current) + len(block) + 1 > limit:
            parts.append(current)
            current = block
        else:
            current = f"{current}\n{block}" if current else block
    if current:
        parts.append(current)
    return parts


def chunk_document(title: str, body: str, max_chars: int = MAX_CHUNK_CHARS) -> list[ChunkDraft]:
    """Dzieli treść na fragmenty według nagłówków i paragrafów.

    Nagłówek H1 to tytuł dokumentu — nie trafia do ścieżki (zastępuje go `title`).
    Sekcje bez treści (np. sam nagłówek rozdziału) nie tworzą fragmentów.
    """
    stack: list[tuple[int, str]] = []
    sections: list[tuple[tuple[str, ...], list[str]]] = []
    current: list[str] = []

    def flush() -> None:
        text = "\n".join(current).strip()
        if not stack and text.split("\n", 1)[0].strip() == title.strip():
            text = text[len(text.split("\n", 1)[0]):].strip()  # tytuł z pierwszego wiersza TXT to nie treść
        if text:
            path = (title, *[h for level, h in stack if level > 1])
            sections.append((path, [text]))
        current.clear()

    for line in body.splitlines():
        heading = _heading(line)
        if heading is None:
            current.append(line.rstrip())
            continue
        flush()
        level, text = heading
        if level == 1:
            stack.clear()
            continue
        while stack and stack[-1][0] >= level:
            stack.pop()
        stack.append((level, text))
    flush()

    drafts: list[ChunkDraft] = []
    for path, (text,) in sections:
        for part in _split_long(text, max_chars):
            drafts.append(ChunkDraft(ordinal=len(drafts), section_path=path, text=part))
    return drafts


_SLUG_FOLD = str.maketrans("ąćęłńóśźż", "acelnoszz")


def slugify(value: str) -> str:
    """Klucz dokumentu z tytułu: „Polityka pracy zdalnej” → „polityka-pracy-zdalnej”."""
    folded = value.lower().translate(_SLUG_FOLD)
    slug = re.sub(r"[^a-z0-9]+", "-", folded).strip("-")
    return slug[:80] or "dokument"
