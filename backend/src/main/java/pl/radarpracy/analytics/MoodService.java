package pl.radarpracy.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.profile.ProfileService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Dziennik samopoczucia (pomysł #76): jeden wpis na dzień, ostrzeżenie wg {@link MoodRules}. */
@Service
public class MoodService {

    static final int MAX_NOTE = 1000;
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    private final MoodEntryRepository entries;
    private final ProfileService profiles;

    public MoodService(MoodEntryRepository entries, ProfileService profiles) {
        this.entries = entries;
        this.profiles = profiles;
    }

    /** Dane wejściowe wpisu; brak daty = dziś. */
    public record MoodRequest(LocalDate date, Integer mood, Integer energy, String note) {
    }

    /**
     * Historia i ostrzeżenie.
     *
     * @param days    zakres historii w dniach
     * @param entries wpisy od najnowszego
     * @param today   dzisiejszy wpis (null, gdy jeszcze nie ma)
     * @param warning ostrzeżenie (null, gdy brak)
     */
    public record MoodOverview(int days, List<MoodEntry> entries, MoodEntry today, MoodRules.Warning warning) {
    }

    /** Zapisuje wpis; jeśli na ten dzień już istnieje — nadpisuje go. */
    @Transactional
    public MoodEntry save(MoodRequest req, LocalDate today) {
        if (req == null) {
            throw new BadRequestException("Brak danych wpisu");
        }
        int mood = scale(req.mood(), "Nastrój");
        int energy = scale(req.energy(), "Energia");
        LocalDate date = req.date() == null ? today : req.date();
        if (date.isAfter(today)) {
            throw new BadRequestException("Nie można dodać wpisu z przyszłą datą");
        }
        String note = req.note() == null || req.note().isBlank() ? null : req.note().strip();
        if (note != null && note.length() > MAX_NOTE) {
            throw new BadRequestException("Notatka może mieć najwyżej " + MAX_NOTE + " znaków");
        }
        MoodEntry e = entries.findByDate(date).orElseGet(() -> new MoodEntry(date, mood, energy, note));
        e.setMood(mood);
        e.setEnergy(energy);
        e.setNote(note);
        return entries.save(e);
    }

    private static int scale(Integer v, String what) {
        if (v == null || v < 1 || v > 5) {
            throw new BadRequestException(what + " musi być w skali 1–5");
        }
        return v;
    }

    @Transactional(readOnly = true)
    public MoodOverview overview(Integer daysParam, LocalDate today) {
        int days = daysParam == null ? DEFAULT_DAYS : daysParam;
        if (days < 1 || days > MAX_DAYS) {
            throw new BadRequestException("Zakres historii musi mieć od 1 do " + MAX_DAYS + " dni");
        }
        // do reguł potrzebujemy co najmniej ostatnich 14 dni, niezależnie od zakresu historii
        int lookback = Math.max(days, MoodRules.AVERAGE_WINDOW_DAYS + MoodRules.DROP_WINDOW_DAYS);
        List<MoodEntry> recent = entries.findByDateGreaterThanEqualOrderByDateDesc(today.minusDays(lookback - 1L));
        LocalDate from = today.minusDays(days - 1L);
        List<MoodEntry> history = recent.stream().filter(e -> !e.getDate().isBefore(from)).toList();
        MoodEntry todays = recent.stream().filter(e -> e.getDate().equals(today)).findFirst().orElse(null);
        return new MoodOverview(days, history, todays, warning(recent, today).orElse(null));
    }

    Optional<MoodRules.Warning> warning(List<MoodEntry> recent, LocalDate today) {
        return MoodRules.evaluate(recent, today, profiles.profile().getWeeklyApplicationGoal());
    }

    /** Ostrzeżenie na dziś (dla „Co dalej?”). */
    @Transactional(readOnly = true)
    public Optional<MoodRules.Warning> currentWarning(LocalDate today) {
        int lookback = MoodRules.AVERAGE_WINDOW_DAYS + MoodRules.DROP_WINDOW_DAYS;
        return warning(entries.findByDateGreaterThanEqualOrderByDateDesc(today.minusDays(lookback)), today);
    }
}
