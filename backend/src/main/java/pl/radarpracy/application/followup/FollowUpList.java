package pl.radarpracy.application.followup;

import java.time.LocalDate;
import java.util.List;

/**
 * Zaległe (termin dziś lub wcześniej) i nadchodzące follow-upy (pomysł #41).
 *
 * @param today        dzień, względem którego liczono dni
 * @param followUpDays liczba dni z profilu (po ilu dniach bez odpowiedzi przypominamy)
 * @param horizonDays  ile dni naprzód obejmują „nadchodzące”
 */
public record FollowUpList(LocalDate today, int followUpDays, int horizonDays, List<FollowUpItem> overdue,
                           List<FollowUpItem> upcoming) {
}
