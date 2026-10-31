"""Przepisanie offline (poprawki z reguł + szablony) i przez AI (z kontrolą faktów i fallbackiem)."""
from __future__ import annotations

import pytest
from conftest import FakeLLM

from ogloszenia.engine import run_rules
from ogloszenia.llm import LLMUnavailable
from ogloszenia.rewrite import AIChange, AIRewrite, amount_values, cleanup, rewrite_offline, rewrite_text


@pytest.mark.parametrize(("source", "expected"), [
    ("W CV prosimy podać datę urodzenia, PESEL, stan cywilny oraz załączyć zdjęcie.",
     "W CV prosimy podać datę urodzenia."),
    ("Szukamy osoby do 35 lat z doświadczeniem.", "Szukamy osoby z doświadczeniem."),
    ("Młody, dynamiczny zespół czeka.", "Dynamiczny zespół czeka."),
    ("Dołącz do młodego zespołu.", "Dołącz do zgranego zespołu."),
    ("Prosimy o podanie obecnego wynagrodzenia.", "Prosimy o podanie oczekiwanego wynagrodzenia."),
    ("Prosimy o podanie obecnego wynagrodzenia i oczekiwań finansowych.", "Prosimy o podanie oczekiwań finansowych."),
    ("Szukamy pani do sprzątania.", "Szukamy osoby do sprzątania."),
    ("Jesteś ambitny, samodzielny i zmotywowany?", "Jesteś ambitny/a, samodzielny/a i zmotywowany/a?"),
    ("- native speaker języka niemieckiego", "- biegła znajomość języka niemieckiego (poziom C2)"),
    ("CV ze zdjęciem prosimy przesyłać na adres.", "CV prosimy przesyłać na adres."),
    ("Oferujemy rodzinną atmosferę.", "Oferujemy przyjazną atmosferę."),
    ("Wymagamy doświadczenia i dobrego stanu zdrowia.", "Wymagamy doświadczenia."),
    ("Szukamy osoby dyspozycyjnej, bez zobowiązań rodzinnych.", "Szukamy osoby dyspozycyjnej."),
    ("Wymagania:\n- miła aparycja\n- prawo jazdy kat. B", "Wymagania:\n- prawo jazdy kat. B"),
])
def test_offline_fix_applies_rule_replacement(source, expected):
    result = rewrite_offline(source)
    assert expected in result.text, result.text


def test_offline_rewrite_of_bad_example(example_text):
    original = example_text("magazynier-zle.txt")
    result = rewrite_offline(original)
    assert result.source == "offline"
    text = result.text
    assert text.startswith("Magazynier (K/M)")
    banned_phrases = (
        "do 35 lat", "mężczyznę", "stan zdrowia", "bez zobowiązań", "ze zdjęciem", "atrakcyjne wynagrodzenie",
    )
    for banned in banned_phrases:
        assert banned not in text, banned
    assert "[UZUPEŁNIJ: widełki wynagrodzenia" in text
    assert text.count("[UZUPEŁNIJ: widełki wynagrodzenia") == 1  # szablon nie dubluje zamiany z pay.vague
    assert "Forma zatrudnienia: [UZUPEŁNIJ:" in text and "Zakres obowiązków: [UZUPEŁNIJ:" in text
    assert "rekrutacja@magazyny-polnoc.example" in text  # fakty zachowane
    assert result.score_after > result.score_before
    kinds = {c.kind for c in result.changes}
    assert {"replaced", "removed", "added", "manual"} <= kinds
    assert any("Uzupełnij pola" in w for w in result.warnings)


def test_templates_go_under_title_and_rodo_at_end():
    result = rewrite_offline("Kasjer\nPraca w sklepie w Toruniu.")
    lines = result.text.splitlines()
    assert lines[0] == "Kasjer (K/M)" and lines[1] == ""
    assert lines[2].startswith("Wynagrodzenie: [UZUPEŁNIJ:")
    assert lines[-1].startswith("Klauzula informacyjna (RODO): [UZUPEŁNIJ:")
    assert "Praca w sklepie w Toruniu." in result.text


def test_good_ad_is_left_unchanged(example_text):
    original = example_text("programista-dobre.txt")
    result = rewrite_offline(original)
    assert result.changes == [] and result.text == original.strip("\n")
    assert result.score_before == result.score_after == 100


def test_manual_items_are_listed_for_findings_without_fix():
    result = rewrite_offline("Programista (K/M)\nSzukamy ninja JavaScriptu. Wynagrodzenie 8000–9500 brutto.")
    manual = [c for c in result.changes if c.kind == "manual"]
    assert any(c.before == "ninja" for c in manual)
    assert any(c.rule_id == "pay.details" for c in manual)


def test_cleanup_removes_orphans():
    assert cleanup("Ala ,  ma kota , .\n-  \n\n\n\nKoniec i .") == "Ala, ma kota.\n\nKoniec."


@pytest.mark.parametrize(("text", "values"), [
    ("Wynagrodzenie: 8–10 tys. zł brutto", {8000, 10000}),
    ("12 000 – 15 000 PLN", {12000, 15000}),
    ("Widełki: 12-15k netto", {12000, 15000}),
    ("Stawka 120 zł/h", {120}),
    ("8.000 zł", {8000}),
    ("Bez kwot", set()),
])
def test_amount_values(text, values):
    assert amount_values(text) == values


# --- przepisanie przez AI ------------------------------------------------------------------------------

AD = (
    "Księgowa\n"
    "Biuro w Gdańsku zatrudni księgową do 40 lat. Wynagrodzenie 8000–9500 zł brutto/mies., umowa o pracę.\n"
    "Obowiązki: prowadzenie ksiąg.\nCV: kadry@biuro.example, tel. 600 100 200. Klauzula RODO w formularzu."
)


def ai_rewrite(text: str, changes=None, injection=False) -> AIRewrite:
    return AIRewrite(rewritten_text=text, changes=changes or [], injection_attempt=injection)


def good_ai_version(prompt: str) -> AIRewrite:
    assert "kadry@biuro.example" not in prompt and "[EMAIL_1]" in prompt
    return ai_rewrite(
        "Księgowy / Księgowa (K/M)\nBiuro w Gdańsku zatrudni osobę na stanowisko księgowej/księgowego. "
        "Wynagrodzenie 8000–9500 zł brutto/mies., umowa o pracę.\nObowiązki: prowadzenie ksiąg.\n"
        "CV: [EMAIL_1], tel. [TELEFON_1]. Klauzula RODO w formularzu.",
        changes=[
            AIChange(before="księgową do 40 lat", after="osobę na stanowisko księgowej/księgowego",
                     reason="usunięto limit wieku"),
            AIChange(before="fragment, którego nie było", after="x", reason="test"),
        ],
    )


def test_ai_rewrite_unmasks_contacts_and_verifies_changes():
    fake = FakeLLM({AIRewrite: good_ai_version})
    result = rewrite_text(AD, fake)
    assert result.source == "ai" and result.ai_note is None
    assert "kadry@biuro.example" in result.text and "600 100 200" in result.text
    assert [c.verified for c in result.changes] == [True, False]
    assert any("nie odpowiada dokładnie" in w for w in result.warnings)
    assert result.score_after > result.score_before
    assert "<ogloszenie>" in fake.calls[0]["prompt"]


def test_ai_rewrite_with_invented_salary_falls_back_to_offline():
    invented = ai_rewrite(AD.replace("8000–9500 zł", "9000–12000 zł"))
    result = rewrite_text(AD, FakeLLM({AIRewrite: invented}))
    assert result.source == "offline"
    assert "kwoty, których nie ma w oryginale" in result.ai_note
    assert "do 40 lat" not in result.text


def test_ai_rewrite_dropping_contact_produces_warning():
    dropped = ai_rewrite(AD.replace("tel. 600 100 200", "").replace("Księgowa\n", "Księgowa (K/M)\n"))
    result = rewrite_text(AD, FakeLLM({AIRewrite: dropped}))
    assert result.source == "ai"
    assert any("telefon" in w for w in result.warnings)


def test_ai_rewrite_failure_falls_back_to_offline():
    result = rewrite_text(AD, FakeLLM(error=LLMUnavailable("Model odmówił odpowiedzi")))
    assert result.source == "offline" and "Model odmówił odpowiedzi" in result.ai_note
    assert result.text.startswith("Księgowa (K/M)")


def test_ai_rewrite_empty_output_falls_back():
    result = rewrite_text(AD, FakeLLM({AIRewrite: ai_rewrite("   ")}))
    assert result.source == "offline"


def test_rewrite_without_llm_is_offline():
    result = rewrite_text(AD, None)
    assert result.source == "offline" and result.ai_note.startswith("Tryb offline")
    assert all(f.source == "rule" for f in run_rules(result.text))


def test_score_after_does_not_count_unfilled_placeholders():
    result = rewrite_offline("Kasjer\nPraca w sklepie w Toruniu.")
    # tytuł poprawiony, ale brak kwoty (15), umowy (6), obowiązków (6) i klauzuli (2) zostaje, dopóki pola są puste
    assert result.score_before == 100 - 15 - 15 - 6 - 6 - 2
    assert result.score_after == 100 - 15 - 6 - 6 - 2
