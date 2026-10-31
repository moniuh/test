"""Minimalny generator plików DOCX (stdlib) — do testów i przykładów."""
from __future__ import annotations

import io
import zipfile
from xml.sax.saxutils import escape

_NS = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" ' \
      'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'


def _paragraphs(lines: list[str]) -> str:
    out = []
    for line in lines:
        if not line:
            out.append("<w:p/>")
            continue
        runs = []
        for i, part in enumerate(line.split("\t")):
            if i:
                runs.append("<w:r><w:tab/></w:r>")
            runs.append(f'<w:r><w:t xml:space="preserve">{escape(part)}</w:t></w:r>')
        out.append("<w:p>" + "".join(runs) + "</w:p>")
    return "".join(out)


def build_docx(lines: list[str], header_lines: list[str] | None = None) -> bytes:
    content_types = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
        '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
        '<Default Extension="xml" ContentType="application/xml"/>'
        '<Override PartName="/word/document.xml" '
        'ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
        + (
            '<Override PartName="/word/header1.xml" '
            'ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>'
            if header_lines else ""
        )
        + "</Types>"
    )
    rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        '<Relationship Id="rId1" '
        'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" '
        'Target="word/document.xml"/></Relationships>'
    )
    doc_rels = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        + (
            '<Relationship Id="rIdH1" '
            'Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>'
            if header_lines else ""
        )
        + "</Relationships>"
    )
    sect = '<w:sectPr><w:headerReference w:type="default" r:id="rIdH1"/></w:sectPr>' if header_lines else ""
    document = (
        f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document {_NS}><w:body>'
        f"{_paragraphs(lines)}{sect}</w:body></w:document>"
    )
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("[Content_Types].xml", content_types)
        zf.writestr("_rels/.rels", rels)
        zf.writestr("word/_rels/document.xml.rels", doc_rels)
        zf.writestr("word/document.xml", document)
        if header_lines:
            zf.writestr(
                "word/header1.xml",
                f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:hdr {_NS}>{_paragraphs(header_lines)}</w:hdr>',
            )
    return buf.getvalue()
