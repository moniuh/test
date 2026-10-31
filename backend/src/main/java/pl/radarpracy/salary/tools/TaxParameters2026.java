package pl.radarpracy.salary.tools;

import java.util.List;
import java.util.Set;

/**
 * Parametry podatkowo-składkowe na rok 2026 — jedno miejsce do corocznej aktualizacji (pomysł #63).
 *
 * <p>Wartości sprawdzone w październiku 2026 r. w wyszukiwarce (portale księgowe: ifirma.pl, infakt.pl,
 * pitax.pl, podatki.biz, pit.pl, forsal.pl, zielonalinia.gov.pl; komunikaty ZUS i obwieszczenia MF/GUS,
 * na które te portale się powołują). Każda stała ma komentarz ze źródłem i statusem:
 * <b>[zweryfikowane]</b> — potwierdzone w co najmniej dwóch niezależnych źródłach,
 * <b>[ustawowe]</b> — stała wynika wprost z ustawy i nie zmieniła się od lat,
 * <b>[szacunek]</b> — wartość przyjęta przez nas (np. typowa stawka) i opisana w interfejsie jako szacunek.</p>
 *
 * <p>Kalkulator jest zawsze szacunkiem, a nie poradą podatkową.</p>
 */
public final class TaxParameters2026 {

    private TaxParameters2026() {
    }

    /** Rok, którego dotyczą parametry. */
    public static final int YEAR = 2026;

    // ------------------------------------------------------------------ PIT (skala, liniowy)

    /** Próg skali podatkowej: 120 000 zł dochodu rocznie [zweryfikowane: pitax.pl, infor.pl — bez zmian w 2026]. */
    public static final double PIT_THRESHOLD = 120_000;
    /** Stawka I progu skali: 12% [ustawowe, art. 27 ust. 1 ustawy o PIT; zweryfikowane]. */
    public static final double PIT_RATE_1 = 0.12;
    /** Stawka II progu skali: 32% [ustawowe; zweryfikowane]. */
    public static final double PIT_RATE_2 = 0.32;
    /** Kwota wolna od podatku: 30 000 zł [zweryfikowane — zapowiadana podwyżka do 60 000 zł nie weszła w 2026]. */
    public static final double TAX_FREE_AMOUNT = 30_000;
    /** Kwota zmniejszająca podatek rocznie: 12% × 30 000 = 3 600 zł (300 zł miesięcznie przy PIT-2) [zweryfikowane]. */
    public static final double TAX_REDUCING_AMOUNT_YEARLY = PIT_RATE_1 * TAX_FREE_AMOUNT;
    /** Miesięczna kwota zmniejszająca zaliczkę (przy złożonym PIT-2): 300 zł [zweryfikowane]. */
    public static final double TAX_REDUCING_AMOUNT_MONTHLY = TAX_REDUCING_AMOUNT_YEARLY / 12;
    /** Podatek liniowy dla działalności: 19% [ustawowe, art. 30c]. */
    public static final double LINEAR_RATE = 0.19;

    /**
     * Ulga dla młodych (do 26. roku życia): zwolnienie przychodów z UoP/zlecenia do 85 528 zł rocznie
     * [ustawowe, art. 21 ust. 1 pkt 148 — limit niezmieniony od 2019 r.; część portali myli go z progiem skali].
     * Nie obejmuje działalności gospodarczej (B2B).
     */
    public static final double YOUTH_RELIEF_LIMIT = 85_528;

    // ------------------------------------------------------------------ koszty uzyskania przychodu

    /** KUP pracownika — podstawowe: 250 zł miesięcznie (3 000 zł rocznie) [zweryfikowane: kadry.infor.pl, wyliczenia netto płacy minimalnej 2026]. */
    public static final double KUP_BASIC_MONTHLY = 250;
    /** KUP pracownika — podwyższone (dojazd z innej miejscowości): 300 zł miesięcznie [ustawowe od 2023 r., art. 22 ust. 2]. */
    public static final double KUP_RAISED_MONTHLY = 300;
    /** KUP przy umowie zlecenia: 20% przychodu pomniejszonego o składki społeczne [ustawowe, art. 22 ust. 9 pkt 4]. */
    public static final double KUP_CIVIL_RATE = 0.20;

    // ------------------------------------------------------------------ ZUS pracownika (UoP, zlecenie)

    /** Składka emerytalna — część pracownika: 9,76% [ustawowe]. */
    public static final double EMPLOYEE_PENSION_RATE = 0.0976;
    /** Składka rentowa — część pracownika: 1,5% [ustawowe]. */
    public static final double EMPLOYEE_DISABILITY_RATE = 0.015;
    /** Składka chorobowa (płaci pracownik): 2,45% [ustawowe]. */
    public static final double SICKNESS_RATE = 0.0245;
    /** Składka zdrowotna pracownika/zleceniobiorcy: 9% podstawy (brutto − składki społeczne), bez odliczenia od PIT [ustawowe od 2022 r.]. */
    public static final double HEALTH_RATE = 0.09;
    /**
     * Limit 30-krotności: roczna podstawa składek emerytalnej i rentowej nie może przekroczyć 30 × prognozowanego
     * przeciętnego wynagrodzenia (9 420 zł) = 282 600 zł [zweryfikowane: obwieszczenie MRPiPS, podatki.biz, pit.pl].
     */
    public static final double PENSION_BASE_LIMIT = 282_600;

    // ------------------------------------------------------------------ PPK

    /** PPK — wpłata podstawowa pracownika: 2% brutto, potrącana z wynagrodzenia [ustawowe, ustawa o PPK]. */
    public static final double PPK_EMPLOYEE_RATE = 0.02;
    /** PPK — wpłata podstawowa pracodawcy: 1,5% brutto; jest przychodem pracownika (zwiększa podstawę PIT) [ustawowe]. */
    public static final double PPK_EMPLOYER_RATE = 0.015;

    // ------------------------------------------------------------------ płace i podstawy na 2026

    /** Płaca minimalna od 1.01.2026: 4 806 zł brutto [zweryfikowane: rozporządzenie RM z 11.09.2025]. */
    public static final double MINIMUM_WAGE = 4_806;
    /** Prognozowane przeciętne wynagrodzenie na 2026: 9 420 zł [zweryfikowane: ustawa budżetowa na 2026]. */
    public static final double FORECAST_AVERAGE_WAGE = 9_420;

    // ------------------------------------------------------------------ ZUS przedsiębiorcy (B2B)

    /** Podstawa „dużego ZUS”: 60% × 9 420 zł = 5 652 zł [zweryfikowane: ZUS, ifirma.pl, zielonalinia.gov.pl]. */
    public static final double ZUS_FULL_BASE = 5_652;
    /** Podstawa preferencyjnego ZUS (pierwsze 24 miesiące): 30% × 4 806 zł = 1 441,80 zł [zweryfikowane]. */
    public static final double ZUS_PREFERENTIAL_BASE = 1_441.80;
    /** Składka emerytalna przedsiębiorcy: 19,52% [ustawowe]. */
    public static final double BUSINESS_PENSION_RATE = 0.1952;
    /** Składka rentowa przedsiębiorcy: 8% [ustawowe]. */
    public static final double BUSINESS_DISABILITY_RATE = 0.08;
    /**
     * Składka wypadkowa: 1,67% (płatnicy do 9 ubezpieczonych) [zweryfikowane pośrednio: suma „dużego ZUS”
     * 1 926,76 zł i preferencyjnego 456,18 zł z chorobowym zgadza się tylko przy 1,67%].
     */
    public static final double ACCIDENT_RATE = 0.0167;
    /** Fundusz Pracy: 2,45% — tylko gdy podstawa ≥ płaca minimalna (czyli nie przy ZUS preferencyjnym) [ustawowe]. */
    public static final double LABOUR_FUND_RATE = 0.0245;

    // ------------------------------------------------------------------ składka zdrowotna przedsiębiorcy

    /** Minimalna składka zdrowotna (skala, liniowy): 9% × 4 806 zł = 432,54 zł od lutego 2026 [zweryfikowane: infakt.pl, ifirma.pl, podatnik.info]. */
    public static final double HEALTH_MIN_MONTHLY = 432.54;
    /** Składka zdrowotna na podatku liniowym: 4,9% dochodu [ustawowe]. */
    public static final double HEALTH_LINEAR_RATE = 0.049;
    /** Roczny limit odliczenia zdrowotnej na liniowym: 14 100 zł [zweryfikowane: obwieszczenie MF, ifirma.pl, infakt.pl]. */
    public static final double HEALTH_LINEAR_DEDUCTION_LIMIT = 14_100;
    /** Ryczałt: próg przychodu rocznego, do którego obowiązuje najniższa zdrowotna [ustawowe]. */
    public static final double RYCZALT_THRESHOLD_1 = 60_000;
    /** Ryczałt: drugi próg przychodu rocznego [ustawowe]. */
    public static final double RYCZALT_THRESHOLD_2 = 300_000;
    /**
     * Ryczałt — miesięczna zdrowotna w 2026: 9% × 60/100/180% przeciętnego wynagrodzenia w sektorze
     * przedsiębiorstw w IV kw. 2025 (9 228,64 zł) [zweryfikowane: vatax.pl, fakturaxl.pl, sum.money].
     */
    public static final double RYCZALT_HEALTH_LOW = 498.35;
    /** Ryczałt — zdrowotna przy przychodzie 60–300 tys. zł rocznie [zweryfikowane]. */
    public static final double RYCZALT_HEALTH_MID = 830.58;
    /** Ryczałt — zdrowotna przy przychodzie powyżej 300 tys. zł rocznie [zweryfikowane]. */
    public static final double RYCZALT_HEALTH_HIGH = 1_495.04;
    /** Ryczałt: od przychodu odlicza się 50% zapłaconej składki zdrowotnej [ustawowe, art. 11 ust. 1c ustawy o ryczałcie]. */
    public static final double RYCZALT_HEALTH_DEDUCTIBLE_SHARE = 0.5;
    /** Dopuszczalne stawki ryczałtu w % (np. 12% — usługi programistyczne, 8,5% — wiele usług, 15% — doradztwo) [ustawowe]. */
    public static final Set<Double> RYCZALT_RATES = Set.of(2.0, 3.0, 5.5, 8.5, 10.0, 12.0, 14.0, 15.0, 17.0);
    /** Domyślna stawka ryczałtu: 12% (programowanie, usługi IT) [ustawowe dla PKWiU 62.01]. */
    public static final double RYCZALT_DEFAULT_RATE = 12.0;

    // ------------------------------------------------------------------ porównania ofert

    /** Dni płatnego urlopu przyjmowane przy UoP (≥10 lat stażu z nauką) i przy korekcie B2B bez płatnej przerwy [ustawowe 20/26 dni; przyjęte 26]. */
    public static final int PAID_LEAVE_DAYS = 26;
    /** Dni robocze w roku do korekty urlopowej (21 × 12) [szacunek — zgodny z radar.salary.days-per-month]. */
    public static final int WORKING_DAYS_PER_YEAR = 252;

    /** Elementy kalkulatora, które nie są przepisem, tylko przyjętym przez nas szacunkiem — pokazujemy je w UI. */
    public static final List<String> ESTIMATES = List.of(
            "Wycena benefitów pozapłacowych to typowe kwoty rynkowe, nie dane z oferty.",
            "Korekta B2B bez płatnej przerwy zakłada " + PAID_LEAVE_DAYS + " dni wolnych na " + WORKING_DAYS_PER_YEAR
                    + " dni roboczych w roku.",
            "Składka zdrowotna przedsiębiorcy za styczeń 2026 liczona była jeszcze według stawek z 2025 r. "
                    + "(rok składkowy luty–styczeń) — kalkulator przyjmuje stawki 2026 dla wszystkich miesięcy.");

    /** Źródła pokazywane w interfejsie. */
    public static final String SOURCES = "Parametry 2026 sprawdzone w październiku 2026 r.: ustawa o PIT, obwieszczenia "
            + "ZUS, MRPiPS i MF, komunikat GUS o przeciętnym wynagrodzeniu w IV kw. 2025 (za portalami ifirma.pl, "
            + "infakt.pl, pitax.pl, podatki.biz).";
}
