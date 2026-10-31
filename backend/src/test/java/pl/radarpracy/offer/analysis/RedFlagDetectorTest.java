package pl.radarpracy.offer.analysis;

import org.junit.jupiter.api.Test;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.analysis.RedFlag.Severity;
import pl.radarpracy.salary.SalaryPeriod;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RedFlagDetectorTest {

    static final String RED_FLAGS = """
            Młodszy specjalista ds. obsługi klienta
            Dynamiczny Zespół sp. z o.o., Warszawa

            Dołącz do młodego, dynamicznego zespołu, w którym panuje rodzinna atmosfera!

            Wymagania:
            - Dyspozycyjność 24/7, gotowość do pracy w nadgodzinach i w weekendy
            - Odporność na stres i pracę pod presją czasu
            - Wysoka kultura osobista
            - Komunikatywność

            Oferujemy:
            - Umowę o dzieło
            - Bezpłatny okres próbny (2 tygodnie)
            - Atrakcyjne wynagrodzenie uzależnione od wyników
            - Możliwość szybkiego awansu

            Prosimy o CV ze zdjęciem, datą urodzenia i informacją o stanie cywilnym.
            """;

    static final String CLEAN = """
            Java Developer — LogiTech Solutions
            Kraków / 100% zdalnie. Umowa o pracę, 14 000 – 18 000 zł brutto miesięcznie.

            Wymagania:
            - 3 lata doświadczenia w Java
            - Spring, SQL

            Oferujemy: karta sportowa, opieka medyczna, PPK.

            Administratorem Twoich danych osobowych jest LogiTech. Przetwarzamy imię, nazwisko, datę urodzenia
            i dane kontaktowe zgodnie z art. 22¹ Kodeksu pracy.
            """;

    private static JobOffer offer(String title, String description) {
        JobOffer o = new JobOffer();
        o.setTitle(title);
        o.setDescription(description);
        return o;
    }

    private static Map<String, RedFlag> byCode(List<RedFlag> flags) {
        return flags.stream().collect(Collectors.toMap(RedFlag::code, Function.identity()));
    }

    @Test
    void detectsAllSignalsInRedFlagOfferWithVerbatimQuotes() {
        JobOffer o = offer("Młodszy specjalista ds. obsługi klienta", RED_FLAGS);
        o.setContractTypes(List.of(ContractType.UOD));
        o.setSeniority(Seniority.JUNIOR);
        List<RedFlag> flags = RedFlagDetector.detect(o);
        Map<String, RedFlag> f = byCode(flags);

        assertThat(f).containsKeys("NO_SALARY", "UOD_FOR_JOB", "UNPAID_TRIAL", "FAMILY_ATMOSPHERE", "YOUNG_TEAM",
                "AVAILABILITY_24_7", "OVERTIME", "STRESS", "RESULTS_ONLY_PAY", "PHOTO_REQUESTED", "BIRTH_DATE_REQUESTED",
                "PRIVATE_DATA_REQUESTED");
        assertThat(f.get("UOD_FOR_JOB").severity()).isEqualTo(Severity.HIGH);
        assertThat(f.get("UNPAID_TRIAL").quote()).isEqualTo("Bezpłatny okres próbny (2 tygodnie)");
        assertThat(f.get("AVAILABILITY_24_7").quote()).startsWith("Dyspozycyjność 24/7");
        assertThat(f.get("FAMILY_ATMOSPHERE").severity()).isEqualTo(Severity.INFO);
        assertThat(f.get("RESULTS_ONLY_PAY").severity()).isEqualTo(Severity.HIGH);
        assertThat(f.get("PRIVATE_DATA_REQUESTED").why()).contains("art. 22¹");
        // „atrakcyjne wynagrodzenie” bez kwot jest cytatem przy braku widełek
        assertThat(f.get("NO_SALARY").quote()).contains("Atrakcyjne wynagrodzenie");

        // zasada nr 2: każdy cytat to dosłowny fragment ogłoszenia
        for (RedFlag flag : flags) {
            assertThat(flag.title()).isNotBlank();
            assertThat(flag.why()).isNotBlank();
            if (flag.quote() != null) {
                assertThat(RED_FLAGS).as(flag.code()).contains(flag.quote());
            }
        }
        // najpierw flagi o wysokiej wadze
        assertThat(flags.get(0).severity()).isEqualTo(Severity.HIGH);
        assertThat(flags.get(flags.size() - 1).severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void cleanOfferHasNoFlagsAndClauseIsNotARequest() {
        JobOffer o = offer("Java Developer", CLEAN);
        o.setSalaryMin(14000);
        o.setSalaryMax(18000);
        assertThat(RedFlagDetector.detect(o)).isEmpty();
    }

    @Test
    void missingSalaryWithoutVagueWordingHasNoQuote() {
        RedFlag f = byCode(RedFlagDetector.detect(offer("Tester", "Testy manualne i automatyczne."))).get("NO_SALARY");
        assertThat(f.severity()).isEqualTo(Severity.WARNING);
        assertThat(f.quote()).isNull();
    }

    @Test
    void wideSalaryRange() {
        JobOffer o = offer("Programista", "Wynagrodzenie: 8 000 – 25 000 zł brutto");
        Map<String, RedFlag> f = byCode(RedFlagDetector.detect(o));
        assertThat(f.get("WIDE_SALARY_RANGE").quote()).isEqualTo("Wynagrodzenie: 8 000 – 25 000 zł brutto");
        assertThat(f.get("WIDE_SALARY_RANGE").why()).contains("3,1×");

        JobOffer narrow = offer("Programista", "x");
        narrow.setSalaryMin(10000);
        narrow.setSalaryMax(14000);
        narrow.setSalaryPeriod(SalaryPeriod.MONTH);
        assertThat(byCode(RedFlagDetector.detect(narrow))).doesNotContainKey("WIDE_SALARY_RANGE");
    }

    @Test
    void juniorWithSeniorRequirements() {
        String text = "Wymagania:\n- min. 4 lata doświadczenia w React\n- TypeScript";
        JobOffer o = offer("Junior Frontend Developer", text);
        o.setSalaryMin(6000);
        o.setRequirements(List.of(new Requirement("min. 4 lata doświadczenia w React", true, 4), Requirement.must("TypeScript")));
        RedFlag f = byCode(RedFlagDetector.detect(o)).get("JUNIOR_OVERREQUIREMENTS");
        assertThat(f.severity()).isEqualTo(Severity.WARNING);
        assertThat(f.quote()).isEqualTo("min. 4 lata doświadczenia w React");
        assertThat(f.why()).contains("4 lata");
    }

    @Test
    void paidOvertimeIsOnlyInfoAndNoOvertimeIsNotFlagged() {
        JobOffer paid = offer("Magazynier", "Wynagrodzenie 6 000 zł brutto. Nadgodziny płatne 150%.");
        assertThat(byCode(RedFlagDetector.detect(paid)).get("OVERTIME").severity()).isEqualTo(Severity.INFO);
        JobOffer none = offer("Magazynier", "Wynagrodzenie 6 000 zł brutto. Wolne weekendy, bez nadgodzin.");
        assertThat(byCode(RedFlagDetector.detect(none))).doesNotContainKey("OVERTIME");
    }

    @Test
    void trialDaysUnpaidInternshipNoContractAndAgeLimit() {
        String text = """
                Wynagrodzenie 5 000 zł brutto.
                Zapraszamy na dni próbne.
                Umowa po okresie próbnym.
                Szukamy osób w wieku 20-35 lat.
                Oferujemy bezpłatny staż.
                """;
        Map<String, RedFlag> f = byCode(RedFlagDetector.detect(offer("Sprzedawca", text)));
        assertThat(f.get("TRIAL_DAYS").severity()).isEqualTo(Severity.WARNING);
        assertThat(f.get("NO_CONTRACT").quote()).isEqualTo("Umowa po okresie próbnym.");
        assertThat(f.get("NO_CONTRACT").why()).contains("art. 29 § 2 KP");
        assertThat(f.get("AGE_LIMIT").severity()).isEqualTo(Severity.HIGH);
        assertThat(f.get("UNPAID_INTERNSHIP").severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void uodWithoutContinuousWorkIsWarning() {
        JobOffer o = offer("Logo dla kawiarni", "Projekt logo, umowa o dzieło, 2 000 zł.");
        assertThat(byCode(RedFlagDetector.detect(o)).get("UOD_FOR_JOB").severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void severityIsSerializedLowercase() {
        assertThat(Severity.HIGH.json()).isEqualTo("high");
        assertThat(Severity.INFO.json()).isEqualTo("info");
    }
}
