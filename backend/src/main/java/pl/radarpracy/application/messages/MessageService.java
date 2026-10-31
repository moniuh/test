package pl.radarpracy.application.messages;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.application.ApplicationEvent;
import pl.radarpracy.application.ApplicationService;
import pl.radarpracy.application.EventType;
import pl.radarpracy.application.Interview;
import pl.radarpracy.application.InterviewClock;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.common.Text;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.ProfileService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Wypełnianie szablonów wiadomości danymi aplikacji i profilu (pomysł #43). */
@Service
public class MessageService {

    private static final Locale PL = Locale.forLanguageTag("pl");
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", PL);
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy, 'godz.' HH:mm", PL);

    private final ApplicationService applications;
    private final ProfileService profiles;
    private final InterviewClock clock;

    public MessageService(ApplicationService applications, ProfileService profiles, InterviewClock clock) {
        this.applications = applications;
        this.profiles = profiles;
        this.clock = clock;
    }

    public List<MessageTemplate> templates() {
        return MessageTemplates.ALL;
    }

    /**
     * Wypełnia szablon; gdy {@code save}, zapisuje wiadomość jako zdarzenie MESSAGE na osi czasu
     * (z treścią przekazaną przez użytkownika po edycji albo z wypełnionym szablonem).
     */
    @Transactional
    public RenderedMessage render(Long applicationId, String templateId, boolean save, String editedSubject,
                                  String editedBody) {
        if (Text.isBlank(templateId)) {
            throw new BadRequestException("Wybierz szablon wiadomości");
        }
        MessageTemplate t = MessageTemplates.find(templateId)
                .orElseThrow(() -> new NotFoundException("Szablon „" + templateId + "” nie istnieje"));
        JobApplication a = applications.get(applicationId);
        RenderedMessage r = render(t, a, profiles.profile(), applications.interviews(applicationId), clock.localNow());
        if (!save) {
            return r;
        }
        String subject = Text.isBlank(editedSubject) ? r.subject() : editedSubject.strip();
        String body = Text.isBlank(editedBody) ? r.body() : editedBody.strip();
        ApplicationEvent e = applications.addEvent(applicationId, EventType.MESSAGE,
                t.name() + " — temat: " + subject + "\n\n" + body);
        return new RenderedMessage(r.templateId(), r.templateName(), r.to(), subject, body,
                compose(r.to(), subject, body), r.missing(), e.getId());
    }

    /** Czysta logika wypełniania (testowana jednostkowo). */
    static RenderedMessage render(MessageTemplate t, JobApplication a, CandidateProfile profile,
                                  List<Interview> interviews, LocalDateTime now) {
        Map<String, String> values = new HashMap<>();
        values.put("firma", a.getOffer().getCompany());
        values.put("stanowisko", a.getOffer().getTitle());
        String to = recipient(a.getContactName(), a.getContactEmail());
        values.put("kontakt", to);
        values.put("data_wyslania", a.getAppliedAt() == null ? null : DATE.format(a.getAppliedAt()));
        values.put("imie_nazwisko", profile == null ? null : profile.getFullName());
        Interview interview = pickInterview(t.interviewContext(), interviews, now);
        values.put("data_rozmowy", interview == null ? null : DATE_TIME.format(interview.getScheduledAt()));
        MessageTemplates.Filled subject = MessageTemplates.fill(t.subject(), values);
        MessageTemplates.Filled body = MessageTemplates.fill(t.body(), values);
        Set<String> missing = new LinkedHashSet<>(subject.missing());
        missing.addAll(body.missing());
        if (to == null) {
            missing.add("kontakt");
        }
        return new RenderedMessage(t.id(), t.name(), to, subject.text(), body.text(),
                compose(to, subject.text(), body.text()), new ArrayList<>(missing), null);
    }

    /** Ostatnia odbyta (PAST) lub najbliższa zaplanowana (UPCOMING) rozmowa. */
    static Interview pickInterview(MessageTemplate.InterviewContext context, List<Interview> interviews,
                                   LocalDateTime now) {
        if (context == MessageTemplate.InterviewContext.NONE || interviews == null) {
            return null;
        }
        List<Interview> withDate = interviews.stream().filter(i -> i.getScheduledAt() != null)
                .sorted(Comparator.comparing(Interview::getScheduledAt)).toList();
        if (context == MessageTemplate.InterviewContext.PAST) {
            return withDate.stream().filter(i -> !i.getScheduledAt().isAfter(now))
                    .reduce((x, y) -> y).orElse(null);
        }
        return withDate.stream().filter(i -> i.getScheduledAt().isAfter(now)).findFirst().orElse(null);
    }

    /** „Imię Nazwisko &lt;e-mail&gt;” bez dopisków w nawiasach (np. roli rekrutera). */
    static String recipient(String name, String email) {
        String n = name == null ? null : name.replaceAll("\\s*\\([^)]*\\)", "").strip();
        boolean hasName = n != null && !n.isEmpty();
        boolean hasMail = !Text.isBlank(email);
        if (hasName && hasMail) {
            return n + " <" + email.strip() + ">";
        }
        return hasName ? n : hasMail ? email.strip() : null;
    }

    static String compose(String to, String subject, String body) {
        return (to == null ? "" : "Do: " + to + "\n") + "Temat: " + subject + "\n\n" + body;
    }
}
