package pl.radarpracy.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.InterviewKind;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.ProfileService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pulpit tygodniowy (pomysł #73). */
@Service
public class DashboardService {

    /** Ile dni do przodu pokazujemy rozmowy. */
    static final int UPCOMING_DAYS = 7;
    /** Ile najlepiej dopasowanych ofert pokazujemy. */
    static final int TOP_OFFERS = 3;
    /** Ile tygodni historii zwracamy do wykresu. */
    static final int HISTORY_WEEKS = 8;

    private final AnalyticsData data;
    private final ProfileService profiles;
    private final OfferService offers;
    private final MatchService matching;

    public DashboardService(AnalyticsData data, ProfileService profiles, OfferService offers, MatchService matching) {
        this.data = data;
        this.profiles = profiles;
        this.offers = offers;
        this.matching = matching;
    }

    public record UpcomingInterview(Long interviewId, Long applicationId, Long offerId, String company,
                                    String position, LocalDateTime scheduledAt, InterviewKind kind,
                                    String kindLabel) {
    }

    public record FollowUp(Long applicationId, Long offerId, String company, String position, LocalDate dueDate,
                           long daysOverdue, ApplicationStatus status, String statusLabel) {
    }

    /**
     * Dobrze dopasowana oferta bez aplikacji.
     *
     * @param sameCompanyApplicationId id istniejącej aplikacji do tej samej firmy (np. duplikat ogłoszenia
     *                                 z innego portalu) — null, gdy brak
     */
    public record TopOffer(Long offerId, String title, String company, int score, String summary,
                           OfferStatus status, List<String> missingSkills, Long sameCompanyApplicationId) {
    }

    /**
     * @param activeOffers       oferty nowe i zapisane
     * @param openApplications   aplikacje niezamknięte (także „do wysłania”)
     * @param upcomingInterviews rozmowy od teraz w przyszłość (w otwartych aplikacjach)
     */
    public record Counters(int activeOffers, int openApplications, int upcomingInterviews) {
    }

    public record Dashboard(String candidateName, LocalDate today, WeeklyGoal.Week week, int streak,
                            List<WeeklyGoal.WeekBar> recentWeeks, List<UpcomingInterview> upcomingInterviews,
                            List<FollowUp> overdueFollowUps, List<TopOffer> topOffers, Counters counters) {
    }

    @Transactional(readOnly = true)
    public Dashboard build(LocalDate today, LocalDateTime now) {
        CandidateProfile profile = profiles.profile();
        List<AppFacts> apps = data.applications();
        WeeklyGoal.Progress progress = progress(apps, today, profile);
        List<JobOffer> allOffers = offers.all();
        Map<Long, MatchResult> matches = matching.matchAll(null);
        int activeOffers = (int) allOffers.stream().filter(SkillGapService::active).count();
        int open = (int) apps.stream().filter(a -> !a.status().isClosed()).count();
        int upcomingAll = (int) apps.stream().filter(a -> !a.status().isClosed())
                .flatMap(a -> a.interviews().stream())
                .filter(i -> i.getScheduledAt() != null && !i.getScheduledAt().isBefore(now)).count();
        return new Dashboard(profile.getFullName(), today, progress.current(), progress.streak(),
                progress.recentWeeks(), upcomingInterviews(apps, now), overdueFollowUps(apps, today),
                topOffers(allOffers, matches, apps, TOP_OFFERS, 0), new Counters(activeOffers, open, upcomingAll));
    }

    static WeeklyGoal.Progress progress(List<AppFacts> apps, LocalDate today, CandidateProfile profile) {
        List<LocalDate> sentDates = apps.stream().filter(AppFacts::sent).map(AppFacts::sentDate)
                .filter(Objects::nonNull).toList();
        return WeeklyGoal.compute(sentDates, today, profile.getWeeklyApplicationGoal(), HISTORY_WEEKS);
    }

    /** Rozmowy w ciągu najbliższych {@link #UPCOMING_DAYS} dni (od teraz). */
    static List<UpcomingInterview> upcomingInterviews(List<AppFacts> apps, LocalDateTime now) {
        LocalDateTime until = now.plusDays(UPCOMING_DAYS);
        List<UpcomingInterview> out = new ArrayList<>();
        for (AppFacts a : apps) {
            if (a.status().isClosed()) {
                continue;
            }
            for (Interview i : a.interviews()) {
                LocalDateTime at = i.getScheduledAt();
                if (at != null && !at.isBefore(now) && !at.isAfter(until)) {
                    out.add(new UpcomingInterview(i.getId(), a.id(), a.offerId(), a.company(), a.position(), at,
                            i.getKind(), Labels.interviewKind(i.getKind())));
                }
            }
        }
        out.sort(Comparator.comparing(UpcomingInterview::scheduledAt));
        return out;
    }

    /** Follow-upy z terminem ≤ dziś w aplikacjach otwartych. */
    static List<FollowUp> overdueFollowUps(List<AppFacts> apps, LocalDate today) {
        List<FollowUp> out = new ArrayList<>();
        for (AppFacts a : apps) {
            LocalDate due = a.application().getNextFollowUpAt();
            if (due != null && !due.isAfter(today) && !a.status().isClosed()) {
                out.add(new FollowUp(a.id(), a.offerId(), a.company(), a.position(), due,
                        ChronoUnit.DAYS.between(due, today), a.status(), Labels.status(a.status())));
            }
        }
        out.sort(Comparator.comparing(FollowUp::dueDate).thenComparing(FollowUp::applicationId));
        return out;
    }

    /** Najlepiej dopasowane oferty NEW/SAVED, na które jeszcze nie aplikowano. */
    static List<TopOffer> topOffers(List<JobOffer> allOffers, Map<Long, MatchResult> matches, List<AppFacts> apps,
                                    int limit, int minScore) {
        Set<Long> applied = new HashSet<>();
        Map<String, Long> byCompany = new HashMap<>();
        for (AppFacts a : apps) {
            applied.add(a.offerId());
            String company = AppFacts.normalizeCompany(a.offer() == null ? null : a.offer().getCompany());
            if (!company.isEmpty()) {
                byCompany.putIfAbsent(company, a.id());
            }
        }
        return allOffers.stream()
                .filter(SkillGapService::active)
                .filter(o -> !applied.contains(o.getId()))
                .filter(o -> matches.containsKey(o.getId()))
                .filter(o -> matches.get(o.getId()).score() >= minScore)
                .sorted(Comparator.comparingInt((JobOffer o) -> matches.get(o.getId()).score()).reversed()
                        .thenComparing(JobOffer::getId))
                .limit(limit)
                .map(o -> {
                    MatchResult r = matches.get(o.getId());
                    return new TopOffer(o.getId(), o.getTitle(), o.getCompany(), r.score(), r.summary(),
                            o.getStatus(), r.missingSkills(), byCompany.get(AppFacts.normalizeCompany(o.getCompany())));
                })
                .toList();
    }
}
