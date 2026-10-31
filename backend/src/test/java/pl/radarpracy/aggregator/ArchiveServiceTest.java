package pl.radarpracy.aggregator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import pl.radarpracy.application.JobApplication;
import pl.radarpracy.application.JobApplicationRepository;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.OfferStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Kontekst jak w testach MockMvc — współdzielony (jedna baza H2 w pamięci). */
@SpringBootTest
@AutoConfigureMockMvc
class ArchiveServiceTest {

    @Autowired
    ArchiveService archive;
    @Autowired
    OfferService offers;
    @Autowired
    AggregatedOfferRepository meta;
    @Autowired
    JobApplicationRepository applications;

    private JobOffer offer(String title, LocalDate expiresAt, OfferStatus status, Instant fetchedAt, boolean fromSource) {
        JobOffer o = new JobOffer();
        o.setTitle(title);
        o.setCompany("Archiwum Test");
        o.setExpiresAt(expiresAt);
        o.setStatus(status);
        JobOffer saved = offers.create(o);
        AggregatedOffer m = new AggregatedOffer();
        m.setOfferId(saved.getId());
        m.setSourceId(fromSource ? 987_654L : null);
        m.setSourceName(fromSource ? "Źródło testowe" : null);
        m.setExternalId("arch-" + saved.getId());
        m.setFetchedAt(fetchedAt);
        meta.save(m);
        return saved;
    }

    @Test
    void archivesExpiredAndStaleOffersFromSourcesOnly() {
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();
        Instant longAgo = now.minus(60, ChronoUnit.DAYS);
        JobOffer expired = offer("Archiwum: wygasła", today.minusDays(1), OfferStatus.NEW, now, true);
        JobOffer expiredSaved = offer("Archiwum: wygasła zapisana", today.minusDays(3), OfferStatus.SAVED, now, true);
        JobOffer validToday = offer("Archiwum: ważna do dziś", today, OfferStatus.NEW, longAgo, true);
        JobOffer stale = offer("Archiwum: stara bez daty", null, OfferStatus.NEW, longAgo, true);
        JobOffer fresh = offer("Archiwum: świeża bez daty", null, OfferStatus.NEW, now, true);
        JobOffer rejected = offer("Archiwum: odrzucona", today.minusDays(5), OfferStatus.REJECTED, now, true);
        JobOffer manual = offer("Archiwum: ręczna wygasła", today.minusDays(5), OfferStatus.NEW, longAgo, false);
        JobOffer withApplication = offer("Archiwum: z aplikacją", today.minusDays(2), OfferStatus.SAVED, now, true);
        JobApplication app = new JobApplication();
        app.setOffer(withApplication);
        applications.save(app);

        ArchiveService.ArchiveReport report = archive.archiveExpired(today);

        assertThat(report.offers()).extracting(ArchiveService.ArchivedOffer::offerId)
                .contains(expired.getId(), expiredSaved.getId(), stale.getId())
                .doesNotContain(validToday.getId(), fresh.getId(), rejected.getId(), manual.getId(), withApplication.getId());
        assertThat(report.skippedWithApplication()).isGreaterThanOrEqualTo(1);
        assertThat(report.message()).startsWith("Zarchiwizowano");
        assertThat(offers.get(expired.getId()).getStatus()).isEqualTo(OfferStatus.ARCHIVED);
        assertThat(offers.get(expiredSaved.getId()).getStatus()).isEqualTo(OfferStatus.ARCHIVED);
        assertThat(offers.get(stale.getId()).getStatus()).isEqualTo(OfferStatus.ARCHIVED);
        assertThat(offers.get(validToday.getId()).getStatus()).isEqualTo(OfferStatus.NEW);
        assertThat(offers.get(fresh.getId()).getStatus()).isEqualTo(OfferStatus.NEW);
        assertThat(offers.get(rejected.getId()).getStatus()).isEqualTo(OfferStatus.REJECTED);
        assertThat(offers.get(manual.getId()).getStatus()).isEqualTo(OfferStatus.NEW);
        assertThat(offers.get(withApplication.getId()).getStatus()).isEqualTo(OfferStatus.SAVED);
        assertThat(report.offers()).filteredOn(a -> a.offerId().equals(stale.getId()))
                .extracting(ArchiveService.ArchivedOffer::reason).first().asString().contains("brak daty");

        // drugi przebieg nie archiwizuje ponownie
        ArchiveService.ArchiveReport again = archive.archiveExpired(today);
        assertThat(again.offers()).extracting(ArchiveService.ArchivedOffer::offerId)
                .doesNotContain(expired.getId(), stale.getId());
    }
}
