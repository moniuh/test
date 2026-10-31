package pl.radarpracy.analytics;

import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.RequirementMatch;
import pl.radarpracy.offer.JobOffer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;

/**
 * Luki kompetencyjne na tle rynku (pomysł #30).
 *
 * <p>Dla każdej brakującej umiejętności (z {@code missingSkills} wyników dopasowania) liczymy, w ilu ofertach
 * jej brakuje (osobno jako wymaganie obowiązkowe), ile punktów dopasowania średnio przez nią tracisz oraz ile ofert
 * przekroczyłoby próg {@link #UNLOCK_THRESHOLD}% po jej opanowaniu. „Po opanowaniu” to symulacja: ponowne
 * dopasowanie tych samych wymagań do CV z dopisaną umiejętnością — liczone przez kod, nie zgadywane.</p>
 */
public final class SkillGapCalculator {

    /** Próg dopasowania, od którego oferta jest „odblokowana”. */
    static final int UNLOCK_THRESHOLD = 70;
    /** Kategoria słownika z kompetencjami miękkimi. */
    static final String SOFT_CATEGORY = "miekkie";

    /** Rodzaj luki. */
    public enum GapType {
        /** umiejętności nie ma w CV — można się jej nauczyć */
        MISSING,
        /** kompetencja miękka — zwykle wystarczy pokazać ją w CV na przykładzie */
        SOFT_SKILL,
        /** umiejętność jest w CV, ale nie spełnia wymagania (lata, poziom) */
        LEVEL
    }

    private SkillGapCalculator() {
    }

    /** Oferta z wynikiem dopasowania do bieżącego CV. */
    public record OfferMatch(JobOffer offer, MatchResult result) {
    }

    public record UnlockedOffer(Long offerId, String title, String company, int scoreBefore, int scoreAfter) {
    }

    /**
     * Luka kompetencyjna.
     *
     * @param skill           nazwa kanoniczna
     * @param category        kategoria ze słownika (może być null)
     * @param offersMissing   w ilu ofertach jej brakuje
     * @param mustHaveMissing w ilu z nich jest w wymaganiu obowiązkowym
     * @param avgPointsLost   średnia strata punktów dopasowania (= średni zysk w symulacji)
     * @param unlockedOffers  ile ofert przekroczyłoby próg po jej opanowaniu
     * @param unlocked        te oferty (z wynikiem przed i po)
     * @param type            rodzaj luki
     * @param hint            wskazówka dla kandydata (null dla zwykłego braku)
     */
    public record SkillGap(String skill, String category, int offersMissing, int mustHaveMissing,
                           double avgPointsLost, int unlockedOffers, List<UnlockedOffer> unlocked, GapType type,
                           String hint) {
    }

    static GapType type(String category, boolean inCv) {
        if (SOFT_CATEGORY.equals(category)) {
            return GapType.SOFT_SKILL;
        }
        return inCv ? GapType.LEVEL : GapType.MISSING;
    }

    static String hint(GapType type) {
        return switch (type) {
            case SOFT_SKILL -> "Kompetencja miękka — zwykle wystarczy pokazać ją w CV na konkretnym przykładzie "
                    + "(tylko jeśli to prawda).";
            case LEVEL -> "Masz ją w CV, ale nie spełnia wymagania (lata doświadczenia lub poziom) — doprecyzuj w CV "
                    + "lub profilu, jak długo i na jakim poziomie z nią pracujesz.";
            case MISSING -> null;
        };
    }

    /**
     * @param matches   oferty z wynikami dopasowania
     * @param simulate  wynik oferty po dopisaniu umiejętności do CV
     * @param categoryOf kategoria umiejętności ze słownika
     * @param inCv       czy umiejętność jest już w CV (wtedy luka dotyczy poziomu lub lat)
     */
    public static List<SkillGap> compute(List<OfferMatch> matches, ToIntBiFunction<OfferMatch, String> simulate,
                                         Function<String, String> categoryOf, Predicate<String> inCv) {
        Map<String, List<OfferMatch>> bySkill = new LinkedHashMap<>();
        for (OfferMatch m : matches) {
            for (String s : m.result().missingSkills()) {
                bySkill.computeIfAbsent(s, k -> new ArrayList<>()).add(m);
            }
        }
        List<SkillGap> out = new ArrayList<>();
        bySkill.forEach((skill, offers) -> {
            int must = 0;
            double lost = 0;
            List<UnlockedOffer> unlocked = new ArrayList<>();
            for (OfferMatch m : offers) {
                if (missingInMustHave(m.result(), skill)) {
                    must++;
                }
                int before = m.result().score();
                int after = simulate.applyAsInt(m, skill);
                lost += Math.max(0, after - before);
                if (before < UNLOCK_THRESHOLD && after >= UNLOCK_THRESHOLD) {
                    unlocked.add(new UnlockedOffer(m.offer().getId(), m.offer().getTitle(), m.offer().getCompany(),
                            before, after));
                }
            }
            unlocked.sort(Comparator.comparingInt(UnlockedOffer::scoreAfter).reversed());
            String category = categoryOf.apply(skill);
            GapType type = type(category, inCv.test(skill));
            out.add(new SkillGap(skill, category, offers.size(), must, Math.round(10.0 * lost / offers.size()) / 10.0,
                    unlocked.size(), List.copyOf(unlocked), type, hint(type)));
        });
        out.sort(Comparator.comparingInt(SkillGap::unlockedOffers).reversed()
                .thenComparing(Comparator.comparingInt(SkillGap::mustHaveMissing).reversed())
                .thenComparing(Comparator.comparingInt(SkillGap::offersMissing).reversed())
                .thenComparing(Comparator.comparingDouble(SkillGap::avgPointsLost).reversed())
                .thenComparing(SkillGap::skill));
        return out;
    }

    /** Czy umiejętność występuje w niespełnionym wymaganiu obowiązkowym. */
    static boolean missingInMustHave(MatchResult r, String skill) {
        for (RequirementMatch rm : r.requirements()) {
            if (rm.mustHave() && !rm.met() && rm.skills() != null && rm.skills().contains(skill)) {
                return true;
            }
        }
        return false;
    }
}
