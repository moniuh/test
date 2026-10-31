import io
import zipfile

import pytest

from conftest import EXAMPLES
from docx_utils import build_docx
from preselekcja.extract import ExtractionError, extract_text, text_from_txt

CV_LINE = "Doświadczenie w kadrach i płacach od 2019 roku, znajomość prawa pracy."


def test_txt_utf8_and_cp1250():
    assert extract_text(CV_LINE.encode("utf-8"), "cv.txt") == (CV_LINE, "txt")
    assert extract_text(("﻿" + CV_LINE).encode("utf-8"), "CV.TXT")[0] == CV_LINE
    assert text_from_txt(CV_LINE.encode("cp1250")) == CV_LINE


def test_docx_body_header_and_tabs():
    data = build_docx([CV_LINE, "Excel\tzaawansowany", "", "Język angielski B2"], header_lines=["Anna Testowa, tel. 601 234 567"])
    text, fmt = extract_text(data, "cv.docx")
    assert fmt == "docx"
    assert text.splitlines()[0] == "Anna Testowa, tel. 601 234 567"  # nagłówek czytany jako pierwszy
    assert "Excel\tzaawansowany" in text and "Język angielski B2" in text


def test_example_docx_and_pdf_with_polish_characters():
    text, fmt = extract_text((EXAMPLES / "cv-02.docx").read_bytes(), "cv-02.docx")
    assert fmt == "docx" and "Młodszy specjalista ds. kadr i płac" in text
    text, fmt = extract_text((EXAMPLES / "cv-03.pdf").read_bytes(), "cv-03.pdf")
    assert fmt == "pdf" and "Ukończyłam kurs „Kadry i płace od podstaw”" in text
    assert "grafiki" in text  # ligatura „ﬁ” rozłożona przez NFKC


def test_minimal_ascii_pdf():
    content = b"BT /F1 12 Tf 72 720 Td (Doswiadczenie w kadrach i placach, prawo pracy, ZUS.) Tj ET"
    objs = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length %d >>\nstream\n" % len(content) + content + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    out = io.BytesIO()
    out.write(b"%PDF-1.4\n")
    offsets = []
    for i, obj in enumerate(objs, 1):
        offsets.append(out.tell())
        out.write(b"%d 0 obj\n" % i + obj + b"\nendobj\n")
    xref = out.tell()
    out.write(b"xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1))
    for off in offsets:
        out.write(b"%010d 00000 n \n" % off)
    out.write(b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs) + 1, xref))
    text, fmt = extract_text(out.getvalue(), "cv.pdf")
    assert fmt == "pdf" and "prawo pracy" in text


@pytest.mark.parametrize(
    "data,name,message",
    [
        (b"abc", "cv.odt", "Nieobsługiwany format"),
        (b"", "cv.txt", "pusty"),
        (b"za krotko", "cv.txt", "za krótki"),
        (b"to nie jest pdf" * 5, "cv.pdf", "PDF"),
        (b"to nie jest docx" * 5, "cv.docx", "DOCX"),
        (b"x" * (5 * 1024 * 1024 + 1), "cv.txt", "za duży"),
    ],
)
def test_errors_are_polish(data, name, message):
    with pytest.raises(ExtractionError, match=message):
        extract_text(data, name)


def test_docx_without_document_xml():
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as zf:
        zf.writestr("inny.xml", "<a/>")
    with pytest.raises(ExtractionError, match="word/document.xml"):
        extract_text(buf.getvalue(), "cv.docx")
