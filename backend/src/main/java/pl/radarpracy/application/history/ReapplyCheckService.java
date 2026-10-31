package pl.radarpracy.application.history;

import org.springframework.stereotype.Service;
import pl.radarpracy.application.ApplicationService;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.application.StatusLabels;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Ostrzeżenie o ponownej aplikacji do tej samej firmy lub na podobne stanowisko (pomysł #47). */
@Service
public class ReapplyCheckService {

    static final int SIMILAR_LIMIT = 5;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("pl"));

    private final ApplicationService applications;
    private final OfferService offers;

    public ReapplyCheckService(ApplicationService applications, OfferService offers) {
        this.applications = applications;
        this.offers = offers;
    }

    public ReapplyCheck check(Long offerId) {
        return check(offers.get(offerId), applications.all());
    }

    static ReapplyCheck check(JobOffer offer, Collection<JobApplication> all) {
        List<ReapplyCheck.Previous> sameOffer = new ArrayList<>();
        List<ReapplyCheck.Previous> sameCompany = new ArrayList<>();
        List<ReapplyCheck.Previous> similarRole = new ArrayList<>();
        for (JobApplication a : all) {
            JobOffer o = a.getOffer();
            double similarity = CompanyNames.roleSimilarity(offer.getTitle(), o.getTitle());
            if (Objects.equals(o.getId(), offer.getId())) {
                sameOffer.add(previous(a, similarity));
            } else if (CompanyNames.sameCompany(offer.getCompany(), o.getCompany())) {
                sameCompany.add(previous(a, similarity));
            } else if (similarity >= CompanyNames.ROLE_THRESHOLD) {
                similarRole.add(previous(a, similarity));
            }
        }
        Comparator<ReapplyCheck.Previous> newest = Comparator
                .comparing(ReapplyCheck.Previous::date, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ReapplyCheck.Previous::applicationId, Comparator.reverseOrder());
        sameOffer.sort(newest);
        sameCompany.sort(newest);
        similarRole.sort(Comparator.comparingDouble(ReapplyCheck.Previous::similarity).reversed().thenComparing(newest));
        List<ReapplyCheck.Previous> similarTop = similarRole.stream().limit(SIMILAR_LIMIT).toList();
        return new ReapplyCheck(offer.getId(), offer.getCompany(), CompanyNames.normalize(offer.getCompany()),
                sameOffer, sameCompany, similarTop, warning(offer, sameCompany));
    }

    private static ReapplyCheck.Previous previous(JobApplication a, double similarity) {
        LocalDate date = a.getAppliedAt() != null ? a.getAppliedAt()
                : a.getCreatedAt() == null ? null : LocalDate.ofInstant(a.getCreatedAt(), ZoneId.systemDefault());
        return new ReapplyCheck.Previous(a.getId(), a.getOffer().getId(), a.getOffer().getTitle(),
                a.getOffer().getCompany(), a.getStatus(), StatusLabels.status(a.getStatus()),
                a.getStatus().isClosed(), date, Math.round(similarity * 100) / 100.0);
    }

    private static String warning(JobOffer offer, List<ReapplyCheck.Previous> sameCompany) {
        if (sameCompany.isEmpty()) {
            return null;
        }
        ReapplyCheck.Previous last = sameCompany.get(0);
        String company = (Text.isBlank(last.company()) ? offer.getCompany() : last.company()).strip();
        StringBuilder sb = new StringBuilder("Już aplikowano do firmy ").append(company);
        if (sameCompany.size() > 1) {
            sb.append(" (").append(sameCompany.size()).append(" razy)");
        } else if (company.endsWith(".")) {
            sb.setLength(sb.length() - 1);
        }
        sb.append(". Ostatnio: „").append(last.offerTitle()).append("”");
        if (last.date() != null) {
            sb.append(", ").append(DATE.format(last.date()));
        }
        sb.append(" — ").append(last.outcome()).append('.');
        return sb.toString();
    }
}
