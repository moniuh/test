package pl.radarpracy.offer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.common.Text;

import java.util.List;

/** Operacje na ofertach — punkt wejścia dla innych modułów. */
@Service
public class OfferService {

    private final JobOfferRepository offers;
    private final RequirementExtractor extractor;

    public OfferService(JobOfferRepository offers, RequirementExtractor extractor) {
        this.offers = offers;
        this.extractor = extractor;
    }

    public List<JobOffer> all() {
        return offers.findAllByOrderByCreatedAtDesc();
    }

    public List<JobOffer> byStatus(OfferStatus status) {
        return offers.findByStatusOrderByCreatedAtDesc(status);
    }

    public JobOffer get(Long id) {
        return offers.findById(id).orElseThrow(() -> NotFoundException.of("Oferta", id));
    }

    @Transactional
    public JobOffer create(JobOffer incoming) {
        if (Text.isBlank(incoming.getTitle())) {
            throw new BadRequestException("Podaj tytuł stanowiska");
        }
        JobOffer o = new JobOffer();
        copy(incoming, o);
        o.setStatus(incoming.getStatus() == null ? OfferStatus.NEW : incoming.getStatus());
        if (o.getRequirements().isEmpty()) {
            o.setRequirements(extractor.extract(o.getDescription()));
        }
        return offers.save(o);
    }

    @Transactional
    public JobOffer update(Long id, JobOffer incoming) {
        JobOffer o = get(id);
        if (Text.isBlank(incoming.getTitle())) {
            throw new BadRequestException("Podaj tytuł stanowiska");
        }
        copy(incoming, o);
        if (incoming.getStatus() != null) {
            o.setStatus(incoming.getStatus());
        }
        o.setRejectReason(incoming.getRejectReason());
        o.setRejectNote(incoming.getRejectNote());
        o.setDuplicateOfId(incoming.getDuplicateOfId());
        return offers.save(o);
    }

    @Transactional
    public JobOffer setStatus(Long id, OfferStatus status) {
        JobOffer o = get(id);
        o.setStatus(status);
        if (status != OfferStatus.REJECTED) {
            o.setRejectReason(null);
            o.setRejectNote(null);
        }
        return offers.save(o);
    }

    @Transactional
    public JobOffer save(JobOffer offer) {
        return offers.save(offer);
    }

    @Transactional
    public void delete(Long id) {
        offers.delete(get(id));
    }

    /** Ponowne wyodrębnienie wymagań z treści (nadpisuje bieżące). */
    @Transactional
    public JobOffer reextractRequirements(Long id) {
        JobOffer o = get(id);
        o.setRequirements(extractor.extract(o.getDescription()));
        return offers.save(o);
    }

    private static void copy(JobOffer from, JobOffer to) {
        to.setTitle(from.getTitle().strip());
        to.setCompany(from.getCompany());
        to.setLocation(from.getLocation());
        to.setUrl(from.getUrl());
        to.setSource(from.getSource());
        to.setDescription(from.getDescription());
        to.setWorkMode(from.getWorkMode());
        to.setContractTypes(from.getContractTypes());
        to.setSeniority(from.getSeniority());
        to.setSalaryMin(from.getSalaryMin());
        to.setSalaryMax(from.getSalaryMax());
        to.setSalaryCurrency(Text.isBlank(from.getSalaryCurrency()) ? "PLN" : from.getSalaryCurrency().toUpperCase());
        to.setSalaryPeriod(from.getSalaryPeriod());
        to.setSalaryGross(from.getSalaryGross());
        to.setRequirements(from.getRequirements());
        to.setBenefits(from.getBenefits());
        to.setTags(from.getTags());
        to.setNotes(from.getNotes());
        to.setPublishedAt(from.getPublishedAt());
        to.setExpiresAt(from.getExpiresAt());
    }
}
