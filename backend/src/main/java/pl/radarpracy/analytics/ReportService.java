package pl.radarpracy.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.analytics.AppFacts.Stage;
import pl.radarpracy.application.ApplicationEvent;
import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.InterviewKind;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.profile.ProfileService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Raport aktywności poszukiwania pracy (pomysł #79) — np. dla doradcy kariery lub urzędu pracy. */
@Service
public class ReportService {

    /** Domyślny zakres: ostatnie 30 dni. */
    static final int DEFAULT_DAYS = 30;
    /** Najdłuższy dopuszczalny zakres. */
    static final int MAX_DAYS = 731;

    private final AnalyticsData data;
    private final ProfileService profiles;

    public ReportService(AnalyticsData data, ProfileService profiles) {
        this.data = data;
        this.profiles = profiles;
    }

    public record ApplicationRow(Long applicationId, LocalDate date, String company, String position, String source,
                                 ApplicationStatus status, String statusLabel) {
    }

    public record InterviewRow(Long interviewId, Long applicationId, LocalDateTime date, String company,
                               String position, InterviewKind kind, String kindLabel) {
    }

    /**
     * @param applicationsSent  aplikacje wysłane w zakresie
     * @param distinctCompanies liczba różnych firm, do których wysłano aplikacje
     * @param responses         ile z tych aplikacji dostało odpowiedź
     * @param interviews        rozmowy w zakresie
     * @param offers            ile z aplikacji doszło do oferty
     * @param followUpsSent     wysłane follow-upy (zdarzenia) w zakresie
     */
    public record Summary(int applicationsSent, int distinctCompanies, int responses, int interviews, int offers,
                          int followUpsSent) {
    }

    public record Report(String candidateName, LocalDate from, LocalDate to, LocalDateTime generatedAt,
                         List<ApplicationRow> applications, List<InterviewRow> interviews, Summary summary) {
    }

    /** Uzupełnia domyślny zakres i sprawdza poprawność. */
    static LocalDate[] range(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate t = to == null ? today : to;
        LocalDate f = from == null ? t.minusDays(DEFAULT_DAYS - 1L) : from;
        if (f.isAfter(t)) {
            throw new BadRequestException("Data początkowa nie może być późniejsza niż końcowa");
        }
        if (ChronoUnit.DAYS.between(f, t) >= MAX_DAYS) {
            throw new BadRequestException("Zakres raportu może mieć najwyżej " + MAX_DAYS + " dni");
        }
        return new LocalDate[]{f, t};
    }

    @Transactional(readOnly = true)
    public Report build(LocalDate fromParam, LocalDate toParam, LocalDate today, LocalDateTime now) {
        LocalDate[] r = range(fromParam, toParam, today);
        return build(data.applications(), profiles.profile().getFullName(), r[0], r[1], now, data.zone());
    }

    static Report build(List<AppFacts> apps, String candidateName, LocalDate from, LocalDate to, LocalDateTime now,
                        ZoneId zone) {
        List<AppFacts> sent = apps.stream().filter(AppFacts::sent)
                .filter(a -> FunnelCalculator.inRange(a.sentDate(), from, to)).toList();
        List<ApplicationRow> appRows = sent.stream()
                .map(a -> new ApplicationRow(a.id(), a.sentDate(), a.company(), a.position(), a.source(), a.status(),
                        Labels.status(a.status())))
                .sorted(Comparator.comparing(ApplicationRow::date).thenComparing(ApplicationRow::applicationId))
                .toList();
        List<InterviewRow> interviewRows = new ArrayList<>();
        int followUps = 0;
        for (AppFacts a : apps) {
            for (Interview i : a.interviews()) {
                if (i.getScheduledAt() != null && FunnelCalculator.inRange(i.getScheduledAt().toLocalDate(), from, to)) {
                    interviewRows.add(new InterviewRow(i.getId(), a.id(), i.getScheduledAt(), a.company(),
                            a.position(), i.getKind(), Labels.interviewKind(i.getKind())));
                }
            }
            for (ApplicationEvent e : a.events()) {
                if (e.getType() == EventType.FOLLOW_UP_SENT && e.getOccurredAt() != null
                        && FunnelCalculator.inRange(AppFacts.toDate(e.getOccurredAt(), zone), from, to)) {
                    followUps++;
                }
            }
        }
        interviewRows.sort(Comparator.comparing(InterviewRow::date));
        int companies = (int) sent.stream().map(a -> a.company().strip().toLowerCase()).distinct().count();
        Summary summary = new Summary(sent.size(), companies, (int) sent.stream().filter(a -> a.reached(Stage.RESPONSE))
                .count(), interviewRows.size(), (int) sent.stream().filter(a -> a.reached(Stage.OFFER)).count(),
                followUps);
        return new Report(candidateName, from, to, now, appRows, List.copyOf(interviewRows), summary);
    }
}
