"""Teksty pokazywane pracownikowi — w jednym miejscu, żeby HR mógł je łatwo dostosować."""

NO_BASIS = (
    "Nie znalazłem podstawy w dokumentach firmy, żeby odpowiedzieć na to pytanie. "
    "Nie będę zgadywać — przekaż pytanie do Działu HR, a odpowiedź otrzymasz tutaj."
)
LEGAL_NO_BASIS = (
    "Regulaminy i polityki firmy nie regulują tej kwestii, więc nie udzielę odpowiedzi. "
    "Nie jestem źródłem porad prawnych — skontaktuj się z Działem HR."
)
LEGAL_DISCLAIMER = (
    "Odpowiedź opiera się wyłącznie na regulaminach i politykach firmy, a nie na analizie przepisów prawa — "
    "nie jest poradą prawną."
)
EXTRACT_NOTE = (
    "To wyciąg z dokumentów (tryb offline), a nie interpretacja. Przeczytaj pełny fragment, "
    "a w razie wątpliwości zapytaj HR."
)
HARASSMENT = (
    "Nie oceniam indywidualnych sytuacji i nie zbieram ich szczegółów — takie sprawy wyjaśnia się poufnie "
    "według oficjalnej procedury firmy. Zgłoszenia możesz dokonać kanałami wskazanymi poniżej, także jako "
    "świadek. Procedura zakazuje działań odwetowych wobec osoby zgłaszającej. Nie opisuj tutaj szczegółów "
    "sprawy — czat asystenta nie jest kanałem zgłoszeń."
)
HARASSMENT_NO_PROCEDURE = (
    "Nie oceniam indywidualnych sytuacji i nie zbieram ich szczegółów. Skontaktuj się poufnie z Działem HR "
    "lub HR Business Partnerem — wskażą oficjalną procedurę zgłoszenia."
)
INDIVIDUAL_OTHERS = (
    "Nie udzielam informacji o innych pracownikach — ich wynagrodzenie, zdrowie, oceny i dane osobowe są poufne. "
    "Jeśli potrzebujesz takiej informacji służbowo, zwróć się do Działu HR."
)
INDIVIDUAL_OWN = (
    "To sprawa indywidualna, której nie rozstrzygnę na podstawie regulaminów — nie mam dostępu do Twoich "
    "danych kadrowych ani do decyzji, które Ciebie dotyczą. Porozmawiaj z przełożonym lub Działem HR; możesz "
    "też przekazać pytanie do HR poniżej."
)
INDIVIDUAL_RELATED = "Poniżej zapisy regulaminów, które mogą być pomocne — nie są odpowiedzią w Twojej sprawie."
INDIVIDUAL_PARTIAL = "Część pytania dotyczy Twojej indywidualnej sytuacji — potwierdź ją w Dziale HR."
HEALTH_MASKED = (
    "Nie podawaj w czacie informacji o swoim zdrowiu — nie są potrzebne do odpowiedzi. "
    "Usunąłem je z pytania, zanim zostało zapisane lub przekazane dalej."
)
HEALTH_SOFT = "Nie podawaj w czacie informacji o swoim zdrowiu — nie są potrzebne do odpowiedzi."
INJECTION = "Pytanie zawierało polecenia skierowane do asystenta — potraktowałem je jako zwykły tekst."
REJECTED_CITATIONS = "Odrzucono {n} cytat(y) z odpowiedzi modelu, których nie udało się potwierdzić w dokumentach."
