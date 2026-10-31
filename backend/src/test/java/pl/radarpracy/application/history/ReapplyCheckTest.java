package pl.radarpracy.application.history;

import org.junit.jupiter.api.Test;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.offer.JobOffer;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.radarpracy.application.TestData.application;
import static pl.radarpracy.application.TestData.offer;

class ReapplyCheckTest {

    @Test
    void normalizesCompanyNames() {
        assertThat(CompanyNames.normalize("LogiTech Solutions sp. z o.o.")).isEqualTo("logitech solutions");
        assertThat(CompanyNames.normalize("LogiTech Solutions")).isEqualTo("logitech solutions");
        assertThat(CompanyNames.normalize("BankPol S.A.")).isEqualTo("bankpol");
        assertThat(CompanyNames.normalize("Nordlys Software AB")).isEqualTo("nordlys software");
        assertThat(CompanyNames.normalize("Dynamiczny Zespół Sp. z o. o.")).isEqualTo("dynamiczny zespol");
        assertThat(CompanyNames.normalize("Firma-X, Sp.K.")).isEqualTo("firma x");
        assertThat(CompanyNames.normalize(null)).isEmpty();
        assertThat(CompanyNames.sameCompany("Sklepik.pl", "sklepik pl")).isTrue();
        assertThat(CompanyNames.sameCompany("Medikos", "Medikos Group")).isFalse();
        assertThat(CompanyNames.sameCompany(null, null)).isFalse();
    }

    @Test
    void comparesRolesIgnoringSeniorityAndNoise() {
        assertThat(CompanyNames.similarRole("Senior Java Developer", "Java Developer (Mid)")).isTrue();
        assertThat(CompanyNames.similarRole("Java/Spring Developer", "Java Developer")).isTrue();
        assertThat(CompanyNames.similarRole("Java Developer (remote, EU)", "Java Developer")).isTrue();
        assertThat(CompanyNames.similarRole("Tech Lead Java", "Java Developer")).isFalse();
        assertThat(CompanyNames.similarRole("Fullstack Developer (Java + React)", "Java Developer")).isFalse();
        assertThat(CompanyNames.similarRole("Frontend Developer (React)", "Java Developer")).isFalse();
    }

    @Test
    void findsSameCompanySimilarRoleAndSameOffer() {
        JobOffer target = offer(4, "Java Developer (Mid)", "LogiTech Solutions sp. z o.o.");
        JobApplication sameCompany = application(1, offer(2, "Java Developer", "LogiTech Solutions"),
                ApplicationStatus.NO_RESPONSE, LocalDate.of(2026, 10, 19));
        JobApplication similar = application(2, offer(13, "Java/Spring Developer", "InsureTech"),
                ApplicationStatus.REJECTED, LocalDate.of(2026, 9, 15));
        JobApplication unrelated = application(3, offer(5, "Frontend Developer (React)", "Sklepik.pl"),
                ApplicationStatus.REJECTED, LocalDate.of(2026, 10, 3));
        JobApplication same = application(4, target, ApplicationStatus.PLANNED, null);

        ReapplyCheck c = ReapplyCheckService.check(target, List.of(sameCompany, similar, unrelated, same));

        assertThat(c.normalizedCompany()).isEqualTo("logitech solutions");
        assertThat(c.sameCompany()).extracting(ReapplyCheck.Previous::applicationId).containsExactly(1L);
        assertThat(c.sameCompany().get(0).outcome()).isEqualTo("Brak odpowiedzi");
        assertThat(c.sameCompany().get(0).closed()).isTrue();
        assertThat(c.similarRole()).extracting(ReapplyCheck.Previous::applicationId).containsExactly(2L);
        assertThat(c.sameOffer()).extracting(ReapplyCheck.Previous::applicationId).containsExactly(4L);
        assertThat(c.sameOffer().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(c.warning()).isEqualTo("Już aplikowano do firmy LogiTech Solutions. Ostatnio: „Java Developer”, "
                + "19 października 2026 — Brak odpowiedzi.");
        // nazwa kończąca się kropką nie daje podwójnej kropki
        JobApplication withDot = application(9, offer(20, "QA", "Testy sp. z o.o."), ApplicationStatus.APPLIED,
                LocalDate.of(2026, 10, 5));
        assertThat(ReapplyCheckService.check(offer(21, "QA Engineer", "Testy"), List.of(withDot)).warning())
                .startsWith("Już aplikowano do firmy Testy sp. z o.o. Ostatnio:");
        assertThat(c.any()).isTrue();
    }

    @Test
    void noWarningWithoutHistory() {
        ReapplyCheck c = ReapplyCheckService.check(offer(1, "Data Engineer", "Nowa Firma"), List.of());
        assertThat(c.warning()).isNull();
        assertThat(c.any()).isFalse();
    }
}
