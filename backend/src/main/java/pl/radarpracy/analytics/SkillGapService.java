package pl.radarpracy.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.analytics.SkillGapCalculator.OfferMatch;
import pl.radarpracy.analytics.SkillGapCalculator.SkillGap;
import pl.radarpracy.common.Text;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;
import pl.radarpracy.skills.Skill;
import pl.radarpracy.skills.SkillDictionary;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Luki kompetencyjne (pomysł #30) na podstawie ofert NEW/SAVED (bez oznaczonych duplikatów). */
@Service
public class SkillGapService {

    private final MatchService matching;
    private final OfferService offers;
    private final ProfileService profiles;
    private final SkillDictionary dictionary;

    public SkillGapService(MatchService matching, OfferService offers, ProfileService profiles,
                           SkillDictionary dictionary) {
        this.matching = matching;
        this.offers = offers;
        this.profiles = profiles;
        this.dictionary = dictionary;
    }

    /**
     * @param cvId           użyta wersja CV
     * @param cvName         jej nazwa
     * @param offersAnalyzed liczba przeanalizowanych ofert
     * @param threshold      próg „odblokowania” (%)
     * @param gaps           luki posortowane wg liczby odblokowanych ofert
     * @param note           wyjaśnienie metody lub powód braku wyników
     */
    public record SkillGapReport(Long cvId, String cvName, int offersAnalyzed, int threshold, List<SkillGap> gaps,
                                 String note) {
    }

    /** Oferty, które kandydat może jeszcze rozważać. */
    static boolean active(JobOffer o) {
        return (o.getStatus() == OfferStatus.NEW || o.getStatus() == OfferStatus.SAVED) && o.getDuplicateOfId() == null;
    }

    @Transactional(readOnly = true)
    public SkillGapReport compute(Long cvId) {
        CvVariant cv = profiles.cvOrActive(cvId).orElse(null);
        if (cv == null) {
            return new SkillGapReport(null, null, 0, SkillGapCalculator.UNLOCK_THRESHOLD, List.of(),
                    "Dodaj CV w profilu, aby zobaczyć luki kompetencyjne.");
        }
        CandidateProfile profile = profiles.profile();
        List<OfferMatch> matches = offers.all().stream()
                .filter(SkillGapService::active)
                .filter(o -> o.getRequirements() != null && !o.getRequirements().isEmpty())
                .map(o -> new OfferMatch(o, matching.match(o, cv, profile)))
                .toList();
        String cvText = Text.nullToEmpty(cv.getContent());
        Set<String> inCv = new HashSet<>();
        for (String s : dictionary.skillsIn(cvText)) {
            inCv.add(s);
            inCv.addAll(dictionary.impliedBy(s));
        }
        List<SkillGap> gaps = SkillGapCalculator.compute(matches,
                (m, skill) -> matching.matchRequirements(m.offer().getRequirements(), cvText + "\n" + skill, profile)
                        .score(),
                skill -> dictionary.get(skill).map(Skill::category).orElse(null), inCv::contains);
        String note = matches.isEmpty()
                ? "Brak aktywnych ofert (nowych lub zapisanych) z wymaganiami do analizy."
                : "Symulacja: każdą umiejętność dopisujemy do CV „" + cv.getName() + "” i ponownie liczymy dopasowanie "
                + "tych samych wymagań. Oferta jest „odblokowana”, gdy jej wynik wzrośnie z poniżej do co najmniej "
                + SkillGapCalculator.UNLOCK_THRESHOLD + "%. Dopisz umiejętność do CV dopiero, gdy naprawdę ją znasz.";
        return new SkillGapReport(cv.getId(), cv.getName(), matches.size(), SkillGapCalculator.UNLOCK_THRESHOLD, gaps,
                note);
    }
}
