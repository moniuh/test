package pl.radarpracy.analytics;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.application.ApplicationEvent;
import pl.radarpracy.application.ApplicationEventRepository;
import pl.radarpracy.application.ApplicationService;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.InterviewRepository;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Odczyt danych rdzenia (aplikacje, zdarzenia, rozmowy) w postaci {@link AppFacts} — tylko do odczytu. */
@Component
public class AnalyticsData {

    private final ApplicationService applications;
    private final ApplicationEventRepository events;
    private final InterviewRepository interviews;

    public AnalyticsData(ApplicationService applications, ApplicationEventRepository events,
                         InterviewRepository interviews) {
        this.applications = applications;
        this.events = events;
        this.interviews = interviews;
    }

    /** Strefa czasowa używana do zamiany znaczników czasu na daty. */
    public ZoneId zone() {
        return ZoneId.systemDefault();
    }

    @Transactional(readOnly = true)
    public List<AppFacts> applications() {
        Map<Long, List<ApplicationEvent>> byApp = events.findAll().stream()
                .collect(Collectors.groupingBy(ApplicationEvent::getApplicationId));
        Map<Long, List<Interview>> interviewsByApp = interviews.findAllByOrderByScheduledAtAsc().stream()
                .collect(Collectors.groupingBy(Interview::getApplicationId));
        ZoneId zone = zone();
        return applications.all().stream()
                .map(a -> AppFacts.of(a, byApp.getOrDefault(a.getId(), List.of()),
                        interviewsByApp.getOrDefault(a.getId(), List.of()), zone))
                .toList();
    }
}
