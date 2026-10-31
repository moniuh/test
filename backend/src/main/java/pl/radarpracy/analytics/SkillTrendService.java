package pl.radarpracy.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.analytics.SkillTrendCalculator.OfferSkills;
import pl.radarpracy.analytics.SkillTrendCalculator.SkillTrends;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;
import pl.radarpracy.profile.SkillEntry;
import pl.radarpracy.skills.Skill;
import pl.radarpracy.skills.SkillDictionary;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Trendy umiejętności (pomysł #75) — wszystkie zebrane oferty poza oznaczonymi duplikatami. */
@Service
public class SkillTrendService {

    static final int DEFAULT_DAYS = 30;
    static final int MIN_DAYS = 7;
    static final int MAX_DAYS = 365;
    static final int DEFAULT_LIMIT = 15;
    static final int MAX_LIMIT = 100;

    private final OfferService offers;
    private final ProfileService profiles;
    private final SkillDictionary dictionary;

    public SkillTrendService(OfferService offers, ProfileService profiles, SkillDictionary dictionary) {
        this.offers = offers;
        this.profiles = profiles;
        this.dictionary = dictionary;
    }

    @Transactional(readOnly = true)
    public SkillTrends compute(Integer daysParam, Integer limitParam, LocalDate today) {
        int days = daysParam == null ? DEFAULT_DAYS : daysParam;
        int limit = limitParam == null ? DEFAULT_LIMIT : limitParam;
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BadRequestException("Okno czasu musi mieć od " + MIN_DAYS + " do " + MAX_DAYS + " dni");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BadRequestException("Limit musi być w zakresie 1–" + MAX_LIMIT);
        }
        ZoneId zone = ZoneId.systemDefault();
        List<OfferSkills> offerSkills = offers.all().stream()
                .filter(o -> o.getDuplicateOfId() == null)
                .map(o -> skillsOf(o, zone))
                .toList();
        return SkillTrendCalculator.compute(offerSkills, today, days, limit, candidateSkills(),
                skill -> dictionary.get(skill).map(Skill::category).orElse(null));
    }

    OfferSkills skillsOf(JobOffer o, ZoneId zone) {
        Set<String> must = new LinkedHashSet<>();
        Set<String> nice = new LinkedHashSet<>();
        for (Requirement r : o.getRequirements() == null ? List.<Requirement>of() : o.getRequirements()) {
            Set<String> skills = dictionary.skillsIn(r.text());
            (r.mustHave() ? must : nice).addAll(skills);
        }
        nice.removeAll(must);
        LocalDate date = o.getPublishedAt() != null ? o.getPublishedAt()
                : o.getCreatedAt() == null ? null : o.getCreatedAt().atZone(zone).toLocalDate();
        return new OfferSkills(date, must, nice);
    }

    /** Umiejętności kandydata: z aktywnego CV (z relacją „obejmuje”) i z profilu. */
    Map<String, String> candidateSkills() {
        Map<String, String> out = new LinkedHashMap<>();
        Optional<CvVariant> cv = profiles.activeCv();
        if (cv.isPresent()) {
            for (String s : dictionary.skillsIn(cv.get().getContent())) {
                out.putIfAbsent(s, "CV");
                for (String implied : dictionary.impliedBy(s)) {
                    out.putIfAbsent(implied, "CV");
                }
            }
        }
        CandidateProfile p = profiles.profile();
        for (SkillEntry s : p.getSkills()) {
            if (s != null && s.name() != null) {
                out.putIfAbsent(dictionary.canonicalName(s.name()), "profil");
            }
        }
        return out;
    }
}
