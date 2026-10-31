package pl.radarpracy.matching.ranking;

import pl.radarpracy.common.Text;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.ranking.RankingWeights.Factor;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.salary.NormalizedSalary;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ranking jednej oferty: dopasowanie × preferencje (pomysł #31). Czysta funkcja — ten sam zestaw
 * danych zawsze daje ten sam wynik; wagi są w {@link RankingWeights}.
 */
final class RankingScorer {

    private RankingScorer() {
    }

    static RankedOffer score(JobOffer offer, MatchResult match, NormalizedSalary salary, CandidateProfile profile,
                             LearnedPreferences learned, LocalDate today) {
        CandidateProfile p = profile == null ? new CandidateProfile() : profile;
        NormalizedSalary s = salary == null ? NormalizedSalary.absent() : salary;
        List<RankComponent> components = new ArrayList<>();
        components.add(cv(match));
        components.add(salary(s, p.getMinMonthlySalary()));
        components.add(workMode(offer, p));
        components.add(location(offer, p));
        components.add(freshness(offer, today));
        components.add(contract(offer, p));
        components.add(learned(offer, learned));

        double sum = 0;
        for (RankComponent c : components) {
            sum += c.value() * c.weight() * 100;
        }
        int rankScore = (int) Math.max(0, Math.min(100, Math.round(sum)));
        List<Exclusion> exclusions = DealBreakerFilter.check(offer, p, s);
        return new RankedOffer(OfferBrief.of(offer), match == null ? 0 : match.score(),
                match == null ? null : match.summary(), match == null ? List.of() : match.missingSkills(), rankScore,
                s.comparableMin(), s.comparableMax(), s.present() ? s.comparableMid() : null, List.copyOf(components),
                !exclusions.isEmpty(), List.copyOf(exclusions));
    }

    // ------------------------------------------------------------------

    static RankComponent cv(MatchResult m) {
        if (m == null || m.cvId() == null) {
            return RankComponent.neutral(Factor.CV_MATCH, "Brak CV — dodaj CV w profilu, aby uwzględnić dopasowanie.");
        }
        if (m.mustHaveTotal() + m.niceTotal() == 0) {
            return RankComponent.neutral(Factor.CV_MATCH,
                    "Oferta nie ma wymagań do porównania — składnik neutralny (dodaj wymagania w edycji oferty).");
        }
        String cv = m.cvName() == null ? "" : " (CV „" + m.cvName() + "”)";
        return RankComponent.of(Factor.CV_MATCH, m.score() / 100.0, false,
                "Dopasowanie " + m.score() + "/100" + cv + ". " + m.summary());
    }

    static RankComponent salary(NormalizedSalary s, Integer min) {
        if (min == null || min <= 0) {
            return RankComponent.neutral(Factor.SALARY,
                    "Nie ustawiono minimalnego wynagrodzenia w profilu — składnik neutralny.");
        }
        if (!s.present()) {
            return RankComponent.neutral(Factor.SALARY,
                    "Oferta nie podaje widełek — składnik neutralny. Zapytaj o budżet przed rozmową.");
        }
        Integer mid = s.comparableMid();
        if (mid == null) {
            return RankComponent.neutral(Factor.SALARY, s.explanation() + " — składnik neutralny.");
        }
        String how = "kwota miesięczna brutto w PLN".equals(s.explanation()) ? "" : " (" + s.explanation() + ")";
        if (s.comparableMax() != null && s.comparableMax() < min) {
            return RankComponent.of(Factor.SALARY, 0, false, "Górna granica widełek ≈ "
                    + RankingLabels.money(s.comparableMax()) + " równoważnika UoP brutto" + how
                    + " — poniżej Twojego minimum " + RankingLabels.money(min) + ".");
        }
        double ratio = (double) mid / min;
        double value = salaryCurve(ratio);
        long diff = Math.round((ratio - 1) * 100);
        String rel = diff == 0 ? "na poziomie Twojego minimum" : diff > 0 ? "o " + diff + "% powyżej Twojego minimum"
                : "o " + Math.abs(diff) + "% poniżej Twojego minimum";
        String what = s.comparableMin() != null && s.comparableMax() != null ? "Środek widełek"
                : s.comparableMin() != null ? "Dolna granica („od”)" : "Górna granica („do”)";
        return RankComponent.of(Factor.SALARY, value, false, what + " ≈ " + RankingLabels.money(mid)
                + " równoważnika UoP brutto" + how + " — " + rel + " " + RankingLabels.money(min) + ".");
    }

    /** Krzywa odcinkowo liniowa: (0,8 → 0), (1,0 → 0,6), (1,2 → 1,0). */
    static double salaryCurve(double ratio) {
        double zero = RankingWeights.SALARY_ZERO_RATIO;
        double atMin = RankingWeights.SALARY_AT_MIN_VALUE;
        double full = RankingWeights.SALARY_FULL_RATIO;
        if (ratio <= zero) {
            return 0;
        }
        if (ratio < 1) {
            return atMin * (ratio - zero) / (1 - zero);
        }
        if (ratio < full) {
            return atMin + (1 - atMin) * (ratio - 1) / (full - 1);
        }
        return 1;
    }

    static RankComponent workMode(JobOffer o, CandidateProfile p) {
        List<WorkMode> accepted = OfferTexts.nonNull(p.getWorkModes());
        if (accepted.isEmpty()) {
            return RankComponent.neutral(Factor.WORK_MODE, "Nie określono akceptowanych trybów pracy w profilu.");
        }
        if (o.getWorkMode() == null) {
            return RankComponent.neutral(Factor.WORK_MODE, "Oferta nie podaje trybu pracy — składnik neutralny.");
        }
        String mode = Text.capitalize(RankingLabels.workMode(o.getWorkMode()));
        if (accepted.contains(o.getWorkMode())) {
            return RankComponent.of(Factor.WORK_MODE, 1, false, mode + " — akceptujesz ten tryb.");
        }
        return RankComponent.of(Factor.WORK_MODE, 0, false,
                mode + " — poza akceptowanymi trybami (" + RankingLabels.workModes(accepted) + ").");
    }

    static RankComponent location(JobOffer o, CandidateProfile p) {
        if (OfferTexts.isRemote(o)) {
            return RankComponent.of(Factor.LOCATION, 1, false, "Praca zdalna — miejsce zamieszkania nie ma znaczenia.");
        }
        if (Text.isBlank(o.getLocation())) {
            return RankComponent.neutral(Factor.LOCATION, "Oferta nie podaje lokalizacji — składnik neutralny.");
        }
        String loc = o.getLocation().strip();
        if (OfferTexts.mentionsRemote(loc) && o.getWorkMode() == null) {
            return RankComponent.of(Factor.LOCATION, 1, false, "Możliwa praca zdalna — lokalizacja nie ma znaczenia.");
        }
        List<String> cities = OfferTexts.preferredCities(p);
        if (cities.isEmpty()) {
            return RankComponent.neutral(Factor.LOCATION, "Nie podano preferowanych lokalizacji w profilu.");
        }
        Optional<String> hit = OfferTexts.matchingCity(loc, cities);
        if (hit.isPresent()) {
            return RankComponent.of(Factor.LOCATION, 1, false, loc + " — w Twoich preferowanych lokalizacjach.");
        }
        if (p.isWillingToRelocate()) {
            return RankComponent.of(Factor.LOCATION, 0.5, false,
                    loc + " — poza preferowanymi lokalizacjami, ale rozważasz przeprowadzkę.");
        }
        return RankComponent.of(Factor.LOCATION, 0, false, loc + " — poza preferowanymi lokalizacjami ("
                + String.join(", ", cities) + ") i nie rozważasz przeprowadzki.");
    }

    static RankComponent contract(JobOffer o, CandidateProfile p) {
        List<ContractType> accepted = OfferTexts.nonNull(p.getContractTypes());
        List<ContractType> offered = OfferTexts.nonNull(o.getContractTypes());
        if (accepted.isEmpty()) {
            return RankComponent.neutral(Factor.CONTRACT, "Nie określono akceptowanych form umowy w profilu.");
        }
        if (offered.isEmpty()) {
            return RankComponent.neutral(Factor.CONTRACT, "Oferta nie podaje formy umowy — składnik neutralny.");
        }
        List<ContractType> ok = offered.stream().filter(accepted::contains).toList();
        if (!ok.isEmpty()) {
            return RankComponent.of(Factor.CONTRACT, 1, false,
                    Text.capitalize(RankingLabels.contracts(ok)) + " — akceptujesz tę formę umowy.");
        }
        return RankComponent.of(Factor.CONTRACT, 0, false, Text.capitalize(RankingLabels.contracts(offered))
                + " — poza akceptowanymi formami (" + RankingLabels.contracts(accepted) + ").");
    }

    static RankComponent freshness(JobOffer o, LocalDate today) {
        LocalDate date = o.getPublishedAt() != null ? o.getPublishedAt()
                : o.getCreatedAt() != null ? LocalDate.ofInstant(o.getCreatedAt(), ZoneId.systemDefault()) : null;
        if (o.getExpiresAt() != null && o.getExpiresAt().isBefore(today)) {
            return RankComponent.of(Factor.FRESHNESS, 0, false, "Ogłoszenie wygasło " + o.getExpiresAt() + ".");
        }
        if (date == null) {
            return RankComponent.neutral(Factor.FRESHNESS, "Brak daty publikacji — składnik neutralny.");
        }
        long age = Math.max(0, ChronoUnit.DAYS.between(date, today));
        int fresh = RankingWeights.FRESH_DAYS;
        int stale = RankingWeights.STALE_DAYS;
        double value = age <= fresh ? 1 : Math.max(0, 1 - (double) (age - fresh) / (stale - fresh));
        String when = age == 0 ? "dziś" : age == 1 ? "wczoraj" : age + " dni temu";
        String verb = o.getPublishedAt() != null ? "Opublikowano " : "Dodano ";
        String tail = value == 0 ? " — ogłoszenie prawdopodobnie nieaktualne." : ".";
        return RankComponent.of(Factor.FRESHNESS, value, false, verb + when + tail);
    }

    static RankComponent learned(JobOffer o, LearnedPreferences learned) {
        if (learned == null || !learned.active()) {
            int have = learned == null ? 0 : learned.ratings();
            return new RankComponent(Factor.LEARNED.key(), Factor.LEARNED.label(), 0, Factor.LEARNED.weight(), 0, true,
                    "Uczenie nieaktywne — potrzeba min. " + RankingWeights.LEARNING_MIN_RATINGS
                            + " ocen (zapisanych lub odrzuconych ofert), masz " + have + ".");
        }
        LearnedPreferences.Adjustment adj = learned.adjust(o);
        double value = adj.points() / RankingWeights.LEARNING_MAX_POINTS;
        String explanation = adj.reasons().isEmpty()
                ? "Ta oferta nie ma cech z Twoich wcześniejszych ocen — bez korekty."
                : String.join("; ", adj.reasons()) + ".";
        return RankComponent.of(Factor.LEARNED, value, false, explanation);
    }
}
