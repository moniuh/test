package pl.radarpracy.aggregator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.application.JobApplicationRepository;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.OfferStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wygasanie ofert i archiwizacja (pomysł #22): oferty pobrane ze źródeł w stanie NEW lub SAVED,
 * których data ważności minęła, albo — bez daty — pobrane dawniej niż {@code archiveAfterDays} dni temu,
 * trafiają do archiwum. Oferty z aplikacją i oferty dodane ręcznie nie są ruszane.
 */
@Service
public class ArchiveService {

    /** Zarchiwizowana oferta i powód. */
    public record ArchivedOffer(Long offerId, String title, String company, String reason) {
    }

    /**
     * Raport archiwizacji.
     *
     * @param archived               liczba zarchiwizowanych ofert
     * @param expired                w tym: minęła data ważności
     * @param stale                  w tym: bez daty, pobrane dawno
     * @param skippedWithApplication pominięte, bo kandydat ma do nich aplikację
     * @param offers                 lista zarchiwizowanych
     * @param message                podsumowanie po polsku
     */
    public record ArchiveReport(int archived, int expired, int stale, int skippedWithApplication,
                                List<ArchivedOffer> offers, String message) {
    }

    private final AggregatedOfferRepository meta;
    private final OfferService offers;
    private final JobApplicationRepository applications;
    private final AggregatorProperties props;

    public ArchiveService(AggregatedOfferRepository meta, OfferService offers, JobApplicationRepository applications,
                          AggregatorProperties props) {
        this.meta = meta;
        this.offers = offers;
        this.applications = applications;
        this.props = props;
    }

    @Transactional
    public ArchiveReport archiveExpired(LocalDate today) {
        Map<Long, JobOffer> byId = offers.all().stream().collect(Collectors.toMap(JobOffer::getId, Function.identity()));
        Instant staleBefore = today.minusDays(props.archiveAfterDays()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        int expired = 0;
        int stale = 0;
        int skipped = 0;
        List<ArchivedOffer> archived = new ArrayList<>();
        for (AggregatedOffer m : meta.findBySourceIdIsNotNull()) {
            JobOffer o = byId.get(m.getOfferId());
            if (o == null || (o.getStatus() != OfferStatus.NEW && o.getStatus() != OfferStatus.SAVED)) {
                continue;
            }
            String reason = null;
            boolean isExpired = false;
            if (o.getExpiresAt() != null) {
                if (o.getExpiresAt().isBefore(today)) {
                    reason = "minęła data ważności (" + o.getExpiresAt() + ")";
                    isExpired = true;
                }
            } else if (m.getFetchedAt() != null && m.getFetchedAt().isBefore(staleBefore)) {
                reason = "brak daty ważności, pobrana ponad " + props.archiveAfterDays() + " dni temu";
            }
            if (reason == null) {
                continue;
            }
            if (!applications.findByOfferId(o.getId()).isEmpty()) {
                skipped++;
                continue;
            }
            offers.setStatus(o.getId(), OfferStatus.ARCHIVED);
            archived.add(new ArchivedOffer(o.getId(), o.getTitle(), o.getCompany(), reason));
            if (isExpired) {
                expired++;
            } else {
                stale++;
            }
        }
        String message = archived.isEmpty() ? "Brak ofert do archiwizacji."
                : "Zarchiwizowano " + archived.size() + " (wygasłe: " + expired + ", bez daty i stare: " + stale + ").";
        if (skipped > 0) {
            message += " Pominięto " + skipped + " z aplikacją.";
        }
        return new ArchiveReport(archived.size(), expired, stale, skipped, archived, message);
    }
}
