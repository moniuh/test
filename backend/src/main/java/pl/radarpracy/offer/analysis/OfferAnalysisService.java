package pl.radarpracy.offer.analysis;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.RejectReason;

/** Analiza zapisanej oferty i odrzucanie ofert z powodem (#24). */
@Service
public class OfferAnalysisService {

    static final int MAX_NOTE = 1000;

    private final OfferService offers;
    private final DuplicateService duplicates;

    public OfferAnalysisService(OfferService offers, DuplicateService duplicates) {
        this.offers = offers;
        this.duplicates = duplicates;
    }

    @Transactional(readOnly = true)
    public OfferAnalysis analyze(Long id) {
        JobOffer offer = offers.get(id);
        return new OfferAnalysis(id, RedFlagDetector.detect(offer), ScamDetector.assess(offer), duplicates.forOffer(id),
                "offline");
    }

    /** Odrzucenie oferty z powodem (zasila uczenie preferencji) i opcjonalną notatką. */
    @Transactional
    public JobOffer reject(Long id, RejectReason reason, String note) {
        if (reason == null) {
            throw new BadRequestException("Wybierz powód odrzucenia");
        }
        String n = note == null ? null : note.strip();
        if (n != null && n.length() > MAX_NOTE) {
            throw new BadRequestException("Notatka może mieć najwyżej " + MAX_NOTE + " znaków");
        }
        JobOffer offer = offers.get(id);
        offer.setStatus(OfferStatus.REJECTED);
        offer.setRejectReason(reason);
        offer.setRejectNote(Text.isBlank(n) ? null : n);
        return offers.save(offer);
    }
}
