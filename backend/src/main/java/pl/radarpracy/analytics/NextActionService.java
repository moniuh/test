package pl.radarpracy.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.analytics.NextActionRules.NextAction;
import pl.radarpracy.analytics.NextActionRules.OfferCandidate;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.ProfileService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Zbiera dane dla reguł „Co dalej?” (pomysł #97). */
@Service
public class NextActionService {

    private final AnalyticsData data;
    private final ProfileService profiles;
    private final OfferService offers;
    private final MatchService matching;
    private final SkillGapService skillGaps;
    private final MoodService mood;

    public NextActionService(AnalyticsData data, ProfileService profiles, OfferService offers, MatchService matching,
                             SkillGapService skillGaps, MoodService mood) {
        this.data = data;
        this.profiles = profiles;
        this.offers = offers;
        this.matching = matching;
        this.skillGaps = skillGaps;
        this.mood = mood;
    }

    @Transactional(readOnly = true)
    public List<NextAction> compute(LocalDate today, LocalDateTime now) {
        CandidateProfile profile = profiles.profile();
        List<AppFacts> apps = data.applications();
        WeeklyGoal.Progress progress = DashboardService.progress(apps, today, profile);
        List<OfferCandidate> unapplied = DashboardService.topOffers(offers.all(), matching.matchAll(null), apps,
                        Integer.MAX_VALUE, NextActionRules.HIGH_MATCH_SCORE).stream()
                .map(o -> new OfferCandidate(o.offerId(), o.title(), o.company(), o.score(),
                        o.sameCompanyApplicationId()))
                .toList();
        List<SkillGapCalculator.SkillGap> gaps = skillGaps.compute(null).gaps();
        MoodRules.Warning warning = mood.currentWarning(today).orElse(null);
        return NextActionRules.compute(new NextActionRules.Inputs(today, now, data.zone(), apps, unapplied,
                progress.current(), gaps, warning, profile.getFollowUpDays()));
    }
}
