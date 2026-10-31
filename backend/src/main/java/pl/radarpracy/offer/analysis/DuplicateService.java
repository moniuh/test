package pl.radarpracy.offer.analysis;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.RejectReason;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Wyszukiwanie i oznaczanie duplikatów ofert (#16). */
@Service
public class DuplicateService {

    private final OfferService offers;

    public DuplicateService(OfferService offers) {
        this.offers = offers;
    }

    /** Oferty podobne do zapisanej oferty. */
    @Transactional(readOnly = true)
    public DuplicateReport forOffer(Long id) {
        JobOffer offer = offers.get(id);
        DuplicateDetector.Fingerprint fp = fingerprint(offer);
        return report(id, fp, offer, offers.all());
    }

    /** Sprawdzenie szkicu przed zapisem (ostrzeżenie o możliwym duplikacie). */
    @Transactional(readOnly = true)
    public DuplicateReport check(String title, String company, String description, String url, Long excludeId) {
        if (Text.isBlank(title) && Text.isBlank(description)) {
            throw new BadRequestException("Podaj tytuł lub treść ogłoszenia");
        }
        DuplicateDetector.Fingerprint fp = DuplicateDetector.fingerprint(excludeId, title, company, description, url);
        return report(excludeId, fp, null, offers.all());
    }

    DuplicateReport report(Long selfId, DuplicateDetector.Fingerprint fp, JobOffer self, List<JobOffer> all) {
        List<DuplicateReport.Candidate> out = new ArrayList<>();
        Instant selfCreated = self == null ? Instant.now() : self.getCreatedAt();
        for (JobOffer other : all) {
            if (Objects.equals(other.getId(), selfId)) {
                continue;
            }
            DuplicateDetector.Similarity s = DuplicateDetector.compare(fp, fingerprint(other));
            if (s.score() < DuplicateDetector.MIN_SCORE) {
                continue;
            }
            boolean marked = selfId != null && (Objects.equals(other.getDuplicateOfId(), selfId)
                    || self != null && Objects.equals(self.getDuplicateOfId(), other.getId()));
            boolean older = other.getCreatedAt() != null && selfCreated != null
                    ? other.getCreatedAt().isBefore(selfCreated)
                    : other.getId() != null && selfId != null && other.getId() < selfId;
            out.add(new DuplicateReport.Candidate(other.getId(), other.getTitle(), other.getCompany(), other.getSource(),
                    other.getStatus(), s.score(), s.company(), s.title(), s.content(), s.sameUrl(),
                    s.score() >= DuplicateDetector.THRESHOLD, marked, older, s.reasons()));
        }
        out.sort(Comparator.comparingDouble(DuplicateReport.Candidate::score).reversed()
                .thenComparing(DuplicateReport.Candidate::offerId));
        return new DuplicateReport(selfId, DuplicateDetector.THRESHOLD, DuplicateDetector.MIN_SCORE,
                new DuplicateReport.Weights(DuplicateDetector.W_COMPANY, DuplicateDetector.W_TITLE, DuplicateDetector.W_CONTENT),
                out.size() > 10 ? out.subList(0, 10) : out);
    }

    static DuplicateDetector.Fingerprint fingerprint(JobOffer o) {
        return DuplicateDetector.fingerprint(o.getId(), o.getTitle(), o.getCompany(), o.getDescription(), o.getUrl());
    }

    /**
     * Oznacza ofertę jako duplikat innej: {@code duplicateOfId} wskazuje oryginał (koniec łańcucha
     * duplikatów), status ARCHIVED, powód DUPLICATE.
     */
    @Transactional
    public JobOffer markDuplicate(Long id, Long originalId) {
        if (originalId == null) {
            throw new BadRequestException("Wskaż ofertę oryginalną");
        }
        if (originalId.equals(id)) {
            throw new BadRequestException("Oferta nie może być duplikatem samej siebie");
        }
        JobOffer offer = offers.get(id);
        JobOffer original = offers.get(originalId);
        Set<Long> seen = new HashSet<>();
        seen.add(original.getId());
        while (original.getDuplicateOfId() != null) {
            if (original.getDuplicateOfId().equals(id)) {
                throw new BadRequestException("Oferta #" + originalId + " jest już oznaczona jako duplikat tej oferty");
            }
            if (!seen.add(original.getDuplicateOfId())) {
                break; // cykl w danych — zatrzymaj się
            }
            original = offers.get(original.getDuplicateOfId());
        }
        offer.setDuplicateOfId(original.getId());
        offer.setStatus(OfferStatus.ARCHIVED);
        offer.setRejectReason(RejectReason.DUPLICATE);
        offer.setRejectNote(Text.truncate("Duplikat oferty „" + original.getTitle() + "”"
                + (original.getCompany() == null ? "" : " (" + original.getCompany() + ")") + " — #" + original.getId(), 1000));
        return offers.save(offer);
    }

    /** Cofnięcie oznaczenia duplikatu — oferta wraca jako nowa. */
    @Transactional
    public JobOffer unmarkDuplicate(Long id) {
        JobOffer offer = offers.get(id);
        if (offer.getDuplicateOfId() == null) {
            throw new BadRequestException("Oferta nie jest oznaczona jako duplikat");
        }
        offer.setDuplicateOfId(null);
        if (offer.getRejectReason() == RejectReason.DUPLICATE) {
            offer.setRejectReason(null);
            offer.setRejectNote(null);
            offer.setStatus(OfferStatus.NEW);
        }
        return offers.save(offer);
    }
}
