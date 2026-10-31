import pytest

from preselekcja.anonymize import anonymize


@pytest.mark.parametrize(
    "phone",
    [
        "+48 601 234 567",
        "+48601234567",
        "0048 601 234 567",
        "601 234 567",
        "601-234-567",
        "601234567",
        "(12) 345 67 89",
        "+48 12 345 67 89",
        "12 345 67 89",
    ],
)
def test_masks_polish_phone_formats(phone):
    result = anonymize(f"Doświadczenie w kadrach.\nKontakt: {phone}, w godzinach 9-17.")
    assert phone not in result.text
    assert "[TELEFON]" in result.text
    assert result.counts["phone"] == 1


@pytest.mark.parametrize(
    "text",
    [
        "Pracowałem w latach 2015-2020 oraz 2021–2023.",
        "Mam 5 lat doświadczenia i zespół 250 pracowników.",
        "Wynagrodzenie 7 500 zł, budżet 1 200 000 zł.",
        "NIP firmy: 123-456-32-18.",
    ],
)
def test_does_not_mask_years_counts_or_amounts_as_phone(text):
    assert "[TELEFON]" not in anonymize(text).text


def test_masks_pesel_with_and_without_label():
    result = anonymize("PESEL: 90010112345\nInny numer 85022854321 w tekście.")
    assert "90010112345" not in result.text and "85022854321" not in result.text
    assert result.text.count("[PESEL]") == 2


@pytest.mark.parametrize(
    "address",
    [
        "ul. Lipowa 7/12",
        "ul. 3 Maja 5",
        "al. Jana Pawła II 15",
        "os. Słoneczne 14 m. 3",
        "pl. Bohaterów Getta 2a",
        "31-512 Kraków",
        "00-950 Warszawa",
    ],
)
def test_masks_addresses(address):
    result = anonymize(f"Doświadczony specjalista.\nMieszkam: {address}\nExcel.")
    assert address not in result.text
    assert "[ADRES]" in result.text


def test_masks_labeled_address_line_and_merges_placeholders():
    result = anonymize("Adres: ul. Polna 3, 33-100 Tarnów\nSkills")
    assert "Polna" not in result.text and "Tarnów" not in result.text
    assert result.text.splitlines()[0] == "Adres: [ADRES]"
    plain = anonymize("Kontakt\nul. Polna 3, 33-100 Tarnów")
    assert plain.text.splitlines()[1] == "[ADRES]"


def test_masks_email_and_profile_links():
    result = anonymize("anna.nowak@poczta.example | linkedin.com/in/anna-nowak | https://github.com/annanowak")
    assert "@" not in result.text and "linkedin" not in result.text and "github" not in result.text
    assert result.identity["email"] == ["anna.nowak@poczta.example"]


def test_masks_name_from_header_and_its_inflected_forms():
    text = "ANNA KOWALCZYK\nSpecjalistka ds. kadr\nRekomendacja dla Pani Kowalczyk i Kowalczykowej."
    result = anonymize(text)
    assert "KOWALCZYK" not in result.text and "Kowalczyk" not in result.text
    assert result.text.startswith("[IMIĘ I NAZWISKO]")
    assert "ANNA KOWALCZYK" in result.identity["name"]


def test_masks_inflected_feminine_surname():
    result = anonymize("Joanna Wiśniewska\nList od Wiśniewskiej.")
    assert "Wiśniewsk" not in result.text


def test_masks_labeled_name_and_names_from_first_name_list():
    result = anonymize("Curriculum Vitae\nImię i nazwisko: Zenon Kwiatkowski\nReferencje: Tomasz Zieliński, kierownik.")
    assert "Zenon" not in result.text and "Kwiatkowski" not in result.text
    assert "Tomasz Zieliński" not in result.text


def test_job_title_header_is_not_treated_as_name():
    result = anonymize("Specjalista Kadr Płac\nDoświadczenie w kadrach.")
    assert "Specjalista" in result.text or "Specjalista/ka" in result.text
    assert "[IMIĘ I NAZWISKO]" not in result.text


@pytest.mark.parametrize(
    "line,placeholder,leaked",
    [
        ("Data urodzenia: 14.05.1990 r.", "[DATA URODZENIA]", "1990"),
        ("ur. 3 marca 1985", "[DATA URODZENIA]", "1985"),
        ("Urodzona 1992-07-01", "[DATA URODZENIA]", "1992"),
        ("Rocznik 1979", "[DATA URODZENIA]", "1979"),
        ("Wiek: 34 lata", "[WIEK]", "34"),
        ("Mam 52 lata i dużo energii.", "[WIEK]", "52"),
        ("Jestem 45-letnią specjalistką.", "[WIEK]", "45"),
        ("Zdjęcie: w załączeniu", "[ZDJĘCIE]", "załączeniu"),
        ("[zdjęcie kandydata]", "[ZDJĘCIE]", "kandydata"),
        ("Stan cywilny: zamężna", "[STAN CYWILNY]", "zamężna"),
        ("Jestem żonaty, mam dwoje dzieci.", "[STAN CYWILNY]", "żonaty"),
        ("Narodowość: polska", "[DANE WRAŻLIWE]", "polska"),
    ],
)
def test_masks_birth_date_age_photo_marital_status(line, placeholder, leaked):
    result = anonymize("Doświadczenie w kadrach.\n" + line)
    assert placeholder in result.text
    assert leaked not in result.text


def test_children_are_masked_as_family_data():
    result = anonymize("Mam dwoje dzieci i prawo jazdy.")
    assert "[DANE RODZINNE]" in result.text and "dzieci" not in result.text


def test_experience_length_is_not_masked_as_age():
    text = "10-letnie doświadczenie w kadrach. 5 lat pracy z Excelem."
    result = anonymize(text)
    assert "[WIEK]" not in result.text
    assert "10-letnie doświadczenie" in result.text


def test_identity_is_kept_for_reveal_but_not_in_text():
    result = anonymize("Piotr Nowicki\ntel. 512-345-678")
    assert result.identity["phone"] == ["512-345-678"]
    assert "Nowicki" not in result.text


def test_gender_forms_are_neutralized_after_masking():
    result = anonymize("Pracowałam w kadrach i byłam odpowiedzialna za ZUS.")
    assert "Pracowałem/am" in result.text and "odpowiedzialny/a" in result.text
    assert result.gender_neutralized == 3
    assert anonymize("Pracowałam", neutralize=False).text == "Pracowałam"
