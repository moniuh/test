"""Wyciąganie tekstu z CV: wklejony tekst, .txt, .pdf (pypdf), .docx (zipfile + XML ze stdlib)."""
from __future__ import annotations

import io
import re
import unicodedata
import zipfile
from xml.etree import ElementTree

MAX_UPLOAD_BYTES = 5 * 1024 * 1024
MAX_TEXT_CHARS = 100_000
_MAX_DOCX_UNCOMPRESSED = 30 * 1024 * 1024
_W = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"


class ExtractionError(ValueError):
    """Nie udało się odczytać pliku — komunikat jest gotowy do pokazania użytkownikowi."""


def _clean(text: str) -> str:
    # NFKC rozkłada ligatury z PDF („ﬁ” → „fi”) i ujednolica znaki zgodności.
    text = unicodedata.normalize("NFKC", text)
    text = text.replace("\r\n", "\n").replace("\r", "\n").replace("\x00", "")
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def text_from_txt(data: bytes) -> str:
    for encoding in ("utf-8-sig", "cp1250", "iso-8859-2"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    raise ExtractionError("Nie rozpoznano kodowania pliku tekstowego (obsługiwane: UTF-8, Windows-1250, ISO-8859-2).")


def text_from_pdf(data: bytes) -> str:
    from pypdf import PdfReader
    from pypdf.errors import PdfReadError

    try:
        reader = PdfReader(io.BytesIO(data))
        if reader.is_encrypted:
            raise ExtractionError("PDF jest zaszyfrowany — zapisz go bez hasła albo wklej tekst.")
        pages = [page.extract_text() or "" for page in reader.pages[:30]]
    except ExtractionError:
        raise
    except (PdfReadError, ValueError, KeyError, TypeError) as exc:
        raise ExtractionError("Nie udało się odczytać pliku PDF (plik uszkodzony lub nieobsługiwany).") from exc
    text = "\n".join(pages)
    if not text.strip():
        raise ExtractionError("PDF nie zawiera warstwy tekstowej (to prawdopodobnie skan) — wklej tekst CV ręcznie.")
    return text


def _docx_part_text(xml: bytes) -> str:
    root = ElementTree.fromstring(xml)
    paragraphs: list[str] = []
    for p in root.iter(f"{_W}p"):
        chunks: list[str] = []
        for node in p.iter():
            if node.tag == f"{_W}t" and node.text:
                chunks.append(node.text)
            elif node.tag == f"{_W}tab":
                chunks.append("\t")
            elif node.tag in (f"{_W}br", f"{_W}cr"):
                chunks.append("\n")
        paragraphs.append("".join(chunks))
    return "\n".join(paragraphs)


def text_from_docx(data: bytes) -> str:
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as zf:
            names = zf.namelist()
            if "word/document.xml" not in names:
                raise ExtractionError("Plik nie wygląda na dokument DOCX (brak word/document.xml).")
            if sum(i.file_size for i in zf.infolist()) > _MAX_DOCX_UNCOMPRESSED:
                raise ExtractionError("Dokument DOCX jest zbyt duży po rozpakowaniu.")
            # Nagłówki często zawierają dane kontaktowe — czytamy je, by anonimizacja je zamaskowała.
            parts = sorted(n for n in names if re.fullmatch(r"word/header\d*\.xml", n))
            parts.append("word/document.xml")
            parts += sorted(n for n in names if re.fullmatch(r"word/footer\d*\.xml", n))
            texts = [_docx_part_text(zf.read(n)) for n in parts]
    except ExtractionError:
        raise
    except (zipfile.BadZipFile, ElementTree.ParseError, KeyError) as exc:
        raise ExtractionError("Nie udało się odczytać pliku DOCX (plik uszkodzony?).") from exc
    return "\n".join(t for t in texts if t.strip())


SUPPORTED = {".txt": "txt", ".pdf": "pdf", ".docx": "docx"}


def detect_format(filename: str) -> str:
    lower = (filename or "").lower()
    for ext, fmt in SUPPORTED.items():
        if lower.endswith(ext):
            return fmt
    raise ExtractionError("Nieobsługiwany format pliku. Dozwolone: .txt, .pdf, .docx (albo wklej tekst).")


def extract_text(data: bytes, filename: str) -> tuple[str, str]:
    """Zwraca (tekst, format). Rzuca ExtractionError z polskim komunikatem."""
    if len(data) > MAX_UPLOAD_BYTES:
        raise ExtractionError(f"Plik jest za duży (maks. {MAX_UPLOAD_BYTES // (1024 * 1024)} MB).")
    if not data:
        raise ExtractionError("Plik jest pusty.")
    fmt = detect_format(filename)
    if fmt == "txt":
        text = text_from_txt(data)
    elif fmt == "pdf":
        text = text_from_pdf(data)
    else:
        text = text_from_docx(data)
    return normalize_input_text(text), fmt


def normalize_input_text(text: str) -> str:
    text = _clean(text)
    if len(text) < 30:
        raise ExtractionError("Tekst CV jest za krótki (min. 30 znaków).")
    if len(text) > MAX_TEXT_CHARS:
        raise ExtractionError(f"Tekst CV jest za długi (maks. {MAX_TEXT_CHARS} znaków).")
    return text
