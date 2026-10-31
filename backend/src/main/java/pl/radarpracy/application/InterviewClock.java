package pl.radarpracy.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Strefa czasowa terminów rozmów. {@link Interview#getScheduledAt()} to czas lokalny wpisany przez
 * kandydata — interpretujemy go w strefie {@code radar.calendar.zone} (domyślnie Europe/Warsaw),
 * np. przy eksporcie .ics (DTSTART w UTC) i przy sortowaniu osi czasu.
 */
@Component
public class InterviewClock {

    private final ZoneId zone;
    private final Clock clock;

    @Autowired
    public InterviewClock(@Value("${radar.calendar.zone:Europe/Warsaw}") String zone) {
        this(ZoneId.of(zone), Clock.systemUTC());
    }

    public InterviewClock(ZoneId zone, Clock clock) {
        this.zone = zone;
        this.clock = clock;
    }

    public ZoneId zone() {
        return zone;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDateTime localNow() {
        return LocalDateTime.ofInstant(clock.instant(), zone);
    }

    public Instant toInstant(LocalDateTime local) {
        return local.atZone(zone).toInstant();
    }

    /** Dzień w strefie kandydata. */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }
}
