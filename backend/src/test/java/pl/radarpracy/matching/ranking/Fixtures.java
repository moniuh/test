package pl.radarpracy.matching.ranking;

import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.RejectReason;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.salary.SalaryNormalizer;
import pl.radarpracy.salary.SalaryPeriod;
import pl.radarpracy.salary.SalaryProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Dane testowe modułu rankingu. */
final class Fixtures {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 31);
    static final SalaryNormalizer SALARY = new SalaryNormalizer(SalaryProperties.defaults());
    private static final AtomicLong IDS = new AtomicLong(1000);

    private Fixtures() {
    }

    /** Profil jak w danych demo: zdalnie/hybrydowo, UoP/B2B, min. 15 000 zł, Warszawa + zdalnie. */
    static CandidateProfile profile() {
        CandidateProfile p = new CandidateProfile();
        p.setLocation("Warszawa");
        p.setPreferredLocations(List.of("Warszawa", "zdalnie"));
        p.setWorkModes(List.of(WorkMode.REMOTE, WorkMode.HYBRID));
        p.setContractTypes(List.of(ContractType.UOP, ContractType.B2B));
        p.setMinMonthlySalary(15000);
        p.setWillingToRelocate(false);
        p.setDealBreakers(List.of("praca zmianowa", "dyspozycyjność 24/7"));
        return p;
    }

    static OfferBuilder offer(String title) {
        return new OfferBuilder(title);
    }

    static MatchResult match(long offerId, int score) {
        return new MatchResult(offerId, 1L, "Backend Java", score, 3, 4, 1, 2, List.of(), List.of("Java"),
                List.of("Kubernetes"), "Spełniasz 3 z 4 wymagań obowiązkowych.", List.of());
    }

    static final class OfferBuilder {
        private final JobOffer o = new JobOffer();

        OfferBuilder(String title) {
            o.setId(IDS.incrementAndGet());
            o.setTitle(title);
            o.setStatus(OfferStatus.NEW);
            o.setPublishedAt(TODAY);
            o.setCreatedAt(Instant.parse("2026-10-31T08:00:00Z"));
        }

        OfferBuilder id(long id) {
            o.setId(id);
            return this;
        }

        OfferBuilder company(String c) {
            o.setCompany(c);
            return this;
        }

        OfferBuilder location(String l) {
            o.setLocation(l);
            return this;
        }

        OfferBuilder description(String d) {
            o.setDescription(d);
            return this;
        }

        OfferBuilder mode(WorkMode m) {
            o.setWorkMode(m);
            return this;
        }

        OfferBuilder contracts(ContractType... c) {
            o.setContractTypes(List.of(c));
            return this;
        }

        OfferBuilder seniority(Seniority s) {
            o.setSeniority(s);
            return this;
        }

        OfferBuilder salary(Integer min, Integer max) {
            o.setSalaryMin(min);
            o.setSalaryMax(max);
            o.setSalaryCurrency("PLN");
            o.setSalaryPeriod(SalaryPeriod.MONTH);
            o.setSalaryGross(true);
            return this;
        }

        OfferBuilder published(LocalDate d) {
            o.setPublishedAt(d);
            return this;
        }

        OfferBuilder expires(LocalDate d) {
            o.setExpiresAt(d);
            return this;
        }

        OfferBuilder created(Instant i) {
            o.setCreatedAt(i);
            return this;
        }

        OfferBuilder status(OfferStatus s) {
            o.setStatus(s);
            return this;
        }

        OfferBuilder rejected(RejectReason r) {
            o.setStatus(OfferStatus.REJECTED);
            o.setRejectReason(r);
            return this;
        }

        OfferBuilder requirements(Requirement... r) {
            o.setRequirements(List.of(r));
            return this;
        }

        OfferBuilder duplicateOf(Long id) {
            o.setDuplicateOfId(id);
            return this;
        }

        OfferBuilder benefits(String... b) {
            o.setBenefits(List.of(b));
            return this;
        }

        JobOffer build() {
            return o;
        }
    }
}
