package pl.radarpracy.aggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Harmonogram (pomysł #15): co {@code radar.aggregator.interval-minutes} pobiera wszystkie włączone
 * źródła i archiwizuje wygasłe oferty (pomysł #22). Włączany flagą {@code radar.aggregator.scheduler-enabled}.
 */
@Component
@ConditionalOnProperty(prefix = "radar.aggregator", name = "scheduler-enabled", havingValue = "true")
public class AggregatorScheduler {

    private static final Logger log = LoggerFactory.getLogger(AggregatorScheduler.class);

    private final AggregatorService aggregator;
    private final ArchiveService archive;

    public AggregatorScheduler(AggregatorService aggregator, ArchiveService archive) {
        this.aggregator = aggregator;
        this.archive = archive;
    }

    @Scheduled(initialDelay = 2, fixedDelayString = "${radar.aggregator.interval-minutes:60}", timeUnit = TimeUnit.MINUTES)
    public void tick() {
        try {
            List<AggregatorRun> runs = aggregator.runAll(RunTrigger.SCHEDULED);
            int created = runs.stream().mapToInt(AggregatorRun::getCreated).sum();
            long failed = runs.stream().filter(r -> r.getStatus() != RunStatus.OK).count();
            ArchiveService.ArchiveReport report = archive.archiveExpired(LocalDate.now());
            log.info("Agregator: {} źródeł, nowe oferty: {}, źródła z problemem: {}. {}", runs.size(), created, failed,
                    report.message());
        } catch (RuntimeException e) {
            log.error("Błąd zaplanowanego pobierania ofert", e);
        }
    }
}
