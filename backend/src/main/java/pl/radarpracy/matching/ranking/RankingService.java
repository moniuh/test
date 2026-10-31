package pl.radarpracy.matching.ranking;

import org.springframework.stereotype.Service;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.ProfileService;
import pl.radarpracy.salary.SalaryNormalizer;
import pl.radarpracy.skills.SkillDictionary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ranking ofert: dopasowanie do CV × preferencje z profilu × wyuczone preferencje (pomysły #31, #32, #35).
 * Punkt wejścia dla innych modułów (np. pulpitu): {@link #rank(RankingQuery)}.
 */
@Service
public class RankingService {

    private final OfferService offers;
    private final ProfileService profiles;
    private final MatchService matcher;
    private final SalaryNormalizer salaries;
    private final PreferenceLearner learner;

    public RankingService(OfferService offers, ProfileService profiles, MatchService matcher, SalaryNormalizer salaries,
                          SkillDictionary dictionary) {
        this.offers = offers;
        this.profiles = profiles;
        this.matcher = matcher;
        this.salaries = salaries;
        this.learner = new PreferenceLearner(new OfferFeatures(dictionary));
    }

    /** Lista rankingu wg kryteriów. */
    public List<RankedOffer> rank(RankingQuery query) {
        return snapshot(query.cvId()).select(query);
    }

    /** Ranking wszystkich ofert względem wersji CV (null = aktywna), do wielokrotnego filtrowania. */
    public RankingSnapshot snapshot(Long cvId) {
        Map<Long, MatchResult> matches = matcher.matchAll(cvId);
        List<JobOffer> all = offers.all();
        CandidateProfile profile = profiles.profile();
        LearnedPreferences learned = learner.learn(all);
        LocalDate today = LocalDate.now();
        List<RankingSnapshot.Entry> entries = new ArrayList<>(all.size());
        for (JobOffer o : all) {
            RankedOffer ranked = RankingScorer.score(o, matches.get(o.getId()), salaries.normalize(o), profile, learned,
                    today);
            entries.add(new RankingSnapshot.Entry(o, ranked));
        }
        return new RankingSnapshot(entries);
    }

    /** Wyuczone preferencje do podglądu (#35). */
    public PreferencesView preferences() {
        return PreferencesView.of(learner.learn(offers.all()));
    }
}
