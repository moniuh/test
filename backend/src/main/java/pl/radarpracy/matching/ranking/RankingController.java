package pl.radarpracy.matching.ranking;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.offer.WorkMode;

import java.util.HashSet;
import java.util.List;

@RestController
public class RankingController {

    private final RankingService ranking;
    private final SimilarOffersService similar;

    public RankingController(RankingService ranking, SimilarOffersService similar) {
        this.ranking = ranking;
        this.similar = similar;
    }

    /**
     * Ranking ofert (#31) z twardymi filtrami deal-breakerów (#32) i korektą z uczenia (#35).
     * Statusy i tryby pracy można podać po przecinku lub wielokrotnie ({@code status=NEW,SAVED}).
     */
    @GetMapping("/api/ranking")
    List<RankedOffer> ranking(@RequestParam(required = false) Long cvId,
                              @RequestParam(required = false) List<OfferStatus> status,
                              @RequestParam(required = false) String q,
                              @RequestParam(defaultValue = "false") boolean includeExcluded,
                              @RequestParam(required = false) Integer minScore,
                              @RequestParam(required = false) List<WorkMode> workModes,
                              @RequestParam(required = false) Integer minSalary,
                              @RequestParam(required = false) String sort) {
        if (minScore != null && (minScore < 0 || minScore > 100)) {
            throw new BadRequestException("minScore musi mieścić się w zakresie 0–100");
        }
        if (minSalary != null && minSalary < 0) {
            throw new BadRequestException("minSalary nie może być ujemne");
        }
        return ranking.rank(new RankingQuery(cvId, status == null ? null : new HashSet<>(status), q, includeExcluded,
                minScore, workModes == null ? null : new HashSet<>(workModes), minSalary, RankingSort.parse(sort)));
    }

    /** Wyuczone wagi cech do podglądu (#35). */
    @GetMapping("/api/ranking/preferences")
    PreferencesView preferences() {
        return ranking.preferences();
    }

    /** Podobne oferty (#36): bez samej oferty i jej duplikatów. */
    @GetMapping("/api/offers/{id}/similar")
    List<SimilarOffer> similar(@PathVariable Long id, @RequestParam(required = false) Integer limit) {
        return similar.similar(id, limit);
    }
}
