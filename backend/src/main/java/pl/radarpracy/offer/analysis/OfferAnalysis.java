package pl.radarpracy.offer.analysis;

import java.util.List;

/**
 * Zbiorcza analiza ogłoszenia: czerwone flagi (#18), ryzyko oszustwa (#19) i duplikaty (#16).
 *
 * @param source zawsze „offline” — analiza działa na regułach, bez AI
 */
public record OfferAnalysis(Long offerId, List<RedFlag> redFlags, ScamAssessment scam, DuplicateReport duplicates,
                            String source) {
}
