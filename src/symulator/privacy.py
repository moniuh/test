"""Minimalizacja danych: maskowanie danych identyfikujących przed wysłaniem tekstu do modelu.

Trenujący pisze pytania swobodnie i może wkleić prawdziwy e-mail, telefon czy PESEL.
Model ich nie potrzebuje, więc zastępujemy je znacznikami. Oryginał zostaje tylko lokalnie.
"""
from __future__ import annotations

import re

_EMAIL = re.compile(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+")
_PESEL = re.compile(r"(?<!\d)\d{11}(?!\d)")
_PHONE = re.compile(r"(?<![\w])(?:\+?48[\s-]?)?(?:\d{3}[\s-]?\d{3}[\s-]?\d{3}|\d{2}[\s-]?\d{3}[\s-]?\d{2}[\s-]?\d{2})(?![\w])")
_IBAN = re.compile(r"\bPL\s?\d{2}(?:\s?\d{4}){6}\b", re.IGNORECASE)
_POSTAL_ADDRESS = re.compile(r"\bul\.\s+[A-ZĄĆĘŁŃÓŚŹŻ][\wąćęłńóśźż-]+(?:\s+\d+[a-zA-Z]?(?:/\d+)?)?")


def mask_pii(text: str) -> str:
    """Zastępuje e-maile, numery PESEL, IBAN, telefony i adresy ulic znacznikami."""
    text = _EMAIL.sub("[e-mail]", text)
    text = _IBAN.sub("[numer konta]", text)
    text = _PESEL.sub("[PESEL]", text)
    text = _PHONE.sub("[telefon]", text)
    text = _POSTAL_ADDRESS.sub("[adres]", text)
    return text
