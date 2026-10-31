import io
import zipfile
from datetime import date

import pytest

from asystent.chunking import (
    DocumentError,
    chunk_document,
    docx_to_markdown,
    guess_title,
    load_document,
    parse_front_matter,
    slugify,
)
from asystent.config import default_docs_dir

MARKDOWN = """---
tytul: Regulamin pracy
kod: regulamin-pracy
wersja: 3.0
obowiazuje_od: 2026-01-01
---
# Regulamin pracy Przykład sp. z o.o.

## Rozdział III. Czas pracy

### § 12. Czas pracy

1. Czas pracy nie może przekraczać 8 godzin na dobę.
2. Okres rozliczeniowy wynosi 1 miesiąc.

### § 13. Przerwy

1. Przysługuje 15 minut przerwy.

## Rozdział IV. Urlopy

### § 14. Urlopy

Zasady określa Polityka urlopowa.
"""


def test_front_matter_metadata():
    doc = parse_front_matter(MARKDOWN)
    assert doc.title == "Regulamin pracy"
    assert doc.doc_key == "regulamin-pracy"
    assert doc.version == "3.0"
    assert doc.effective_from == date(2026, 1, 1)
    assert doc.body.startswith("# Regulamin pracy")


def test_front_matter_invalid_date_has_polish_message():
    with pytest.raises(DocumentError, match="RRRR-MM-DD"):
        parse_front_matter("---\nobowiazuje_od: 1 stycznia\n---\ntekst")


def test_chunks_follow_headings_with_section_paths():
    doc = parse_front_matter(MARKDOWN)
    chunks = chunk_document(doc.title, doc.body)
    assert [c.path_label for c in chunks] == [
        "Regulamin pracy › Rozdział III. Czas pracy › § 12. Czas pracy",
        "Regulamin pracy › Rozdział III. Czas pracy › § 13. Przerwy",
        "Regulamin pracy › Rozdział IV. Urlopy › § 14. Urlopy",
    ]
    assert chunks[0].text.startswith("1. Czas pracy nie może")
    assert "Okres rozliczeniowy" in chunks[0].text
    # Nagłówek rozdziału bez treści nie tworzy osobnego fragmentu
    assert all(c.section_path[-1].startswith("§") for c in chunks)


def test_plain_text_uses_paragraph_and_chapter_lines():
    text = "Regulamin nagród\n\nRozdział I Zasady\n§ 1. Cel\nNagrody przyznaje zarząd.\n§ 2. Kwota\nDo 500 zł.\n"
    title = guess_title(text)
    assert title == "Regulamin nagród"
    chunks = chunk_document(title, text)
    paths = [c.path_label for c in chunks]
    assert "Regulamin nagród › Rozdział I Zasady › § 1. Cel" in paths
    assert "Regulamin nagród › Rozdział I Zasady › § 2. Kwota" in paths


def test_long_section_is_split_without_cutting_points():
    points = "\n".join(f"{i}. " + "Punkt regulaminu o istotnej treści. " * 6 for i in range(1, 15))
    chunks = chunk_document("Dok", f"## § 1. Długi\n{points}", max_chars=600)
    assert len(chunks) > 1
    assert all(c.section_path == ("Dok", "§ 1. Długi") for c in chunks)
    assert all(len(c.text) <= 600 for c in chunks)
    assert all(c.text.lstrip()[0].isdigit() for c in chunks)


def _docx(paragraphs: list[tuple[str | None, str]]) -> bytes:
    ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    body = ""
    for style, text in paragraphs:
        ppr = f'<w:pPr><w:pStyle w:val="{style}"/></w:pPr>' if style else ""
        body += f"<w:p>{ppr}<w:r><w:t>{text}</w:t></w:r></w:p>"
    xml = f'<?xml version="1.0"?><w:document xmlns:w="{ns}"><w:body>{body}</w:body></w:document>'
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("word/document.xml", xml)
    return buffer.getvalue()


def test_docx_headings_become_sections():
    data = _docx([("Title", "Polityka parkingowa"), ("Heading1", "§ 1. Miejsca"), (None, "Miejsc jest 20.")])
    markdown = docx_to_markdown(data)
    assert "# Polityka parkingowa" in markdown
    doc = load_document("parking.docx", data)
    chunks = chunk_document(guess_title(doc.body), doc.body)
    assert chunks[0].path_label == "Polityka parkingowa › § 1. Miejsca"


def test_broken_docx_and_unsupported_format():
    with pytest.raises(DocumentError, match="DOCX"):
        load_document("zly.docx", b"to nie jest zip")
    with pytest.raises(DocumentError, match="Nieobsługiwany format"):
        load_document("arkusz.xlsx", b"x")
    with pytest.raises(DocumentError, match="pusty"):
        load_document("pusty.txt", b"   ")


def test_pdf_without_pypdf_gives_clear_message(monkeypatch):
    import builtins

    real_import = builtins.__import__

    def fake_import(name, *args, **kwargs):
        if name == "pypdf":
            raise ImportError("brak")
        return real_import(name, *args, **kwargs)

    monkeypatch.setattr(builtins, "__import__", fake_import)
    with pytest.raises(DocumentError, match="pypdf"):
        load_document("regulamin.pdf", b"%PDF-1.4")


def test_cp1250_text_is_decoded():
    doc = load_document("stary.txt", "§ 1. Zażółć\nTreść".encode("cp1250"))
    assert "Zażółć" in doc.body


def test_slugify():
    assert slugify("Polityka pracy zdalnej") == "polityka-pracy-zdalnej"
    assert slugify("Świadczenia / ZFŚS 2026") == "swiadczenia-zfss-2026"


def test_all_sample_documents_parse_into_paragraph_chunks():
    files = sorted(default_docs_dir().glob("*.md"))
    assert len(files) >= 8
    for path in files:
        doc = load_document(path.name, path.read_bytes())
        assert doc.title and doc.doc_key and doc.version and doc.effective_from
        chunks = chunk_document(doc.title, doc.body)
        assert len(chunks) >= 5, path.name
        assert all("§" in c.section_path[-1] for c in chunks), path.name
