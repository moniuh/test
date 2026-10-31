package pl.radarpracy.aggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.radarpracy.aggregator.AggregatorDtos.SourceRequest;

import java.util.List;

/**
 * Źródła demonstracyjne agregatora (po danych demo rdzenia): kanał RSS i strona kariery z JSON-LD czytane
 * z {@code classpath:aggregator/} (bez sieci) oraz wyłączone źródło RSS z prawdziwym adresem jako wzór.
 * Przy starcie wykonuje pierwsze pobranie źródeł DEMO, żeby skrzynka nie była pusta.
 */
@Component
@Order(10)
public class AggregatorDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AggregatorDemoSeeder.class);

    static final String DEMO_FEED_NAME = "Demo: kanał RSS „Praca Dziś”";
    static final String DEMO_CAREERS_NAME = "Demo: strona kariery Zielony Kod";

    private final boolean enabled;
    private final AggregatorSourceRepository sources;
    private final AggregatorService aggregator;

    public AggregatorDemoSeeder(@Value("${radar.demo-data:true}") boolean enabled, AggregatorSourceRepository sources,
                                AggregatorService aggregator) {
        this.enabled = enabled;
        this.sources = sources;
        this.aggregator = aggregator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || sources.count() > 0) {
            return;
        }
        log.info("Tworzę źródła demonstracyjne agregatora");
        AggregatorSource feed = aggregator.create(new SourceRequest(DEMO_FEED_NAME, SourceType.DEMO, "demo-feed.xml",
                true, List.of(), List.of("praca zmianowa"), List.of(), null));
        AggregatorSource careers = aggregator.create(new SourceRequest(DEMO_CAREERS_NAME, SourceType.DEMO,
                "demo-careers.html", true, List.of(), List.of(), List.of(), null));
        aggregator.create(new SourceRequest("Przykład: We Work Remotely — programowanie (RSS)", SourceType.RSS,
                "https://weworkremotely.com/categories/remote-programming-jobs.rss", false,
                List.of("java", "kotlin", "spring", "backend"), List.of(), List.of(), null));
        for (AggregatorSource s : List.of(feed, careers)) {
            try {
                AggregatorRun run = aggregator.run(s.getId(), RunTrigger.STARTUP);
                log.info("Źródło demo „{}”: {}", s.getName(), run.getMessage());
            } catch (RuntimeException e) {
                log.warn("Nie udało się pobrać źródła demo „{}”: {}", s.getName(), e.getMessage());
            }
        }
    }
}
