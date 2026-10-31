package pl.radarpracy.matching.ranking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.NotFoundException;
import pl.radarpracy.common.Text;
import pl.radarpracy.matching.ranking.SavedSearchDtos.SavedSearchRequest;
import pl.radarpracy.matching.ranking.SavedSearchDtos.SavedSearchResults;
import pl.radarpracy.matching.ranking.SavedSearchDtos.SavedSearchView;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Zapisane wyszukiwania z licznikiem nowych ofert (pomysł #33). */
@Service
public class SavedSearchService {

    static final int NAME_MAX = 120;
    static final int QUERY_MAX = 500;

    private final SavedSearchRepository searches;
    private final RankingService ranking;
    private final ProfileService profiles;

    public SavedSearchService(SavedSearchRepository searches, RankingService ranking, ProfileService profiles) {
        this.searches = searches;
        this.ranking = ranking;
        this.profiles = profiles;
    }

    /** Wszystkie wyszukiwania z licznikami (ranking liczony raz na wersję CV). */
    public List<SavedSearchView> list() {
        Set<Long> cvIds = existingCvIds();
        Map<Long, RankingSnapshot> snapshots = new HashMap<>();
        return searches.findAllByOrderByIdAsc().stream()
                .map(s -> {
                    Long cvId = effectiveCvId(s, cvIds);
                    RankingSnapshot snap = snapshots.computeIfAbsent(cvId, ranking::snapshot);
                    return view(s, snap.select(query(s, cvId)));
                })
                .toList();
    }

    public SavedSearchView get(Long id) {
        SavedSearch s = find(id);
        return view(s, results(s));
    }

    @Transactional
    public SavedSearchView create(SavedSearchRequest request) {
        SavedSearch s = new SavedSearch();
        apply(request, s);
        SavedSearch saved = searches.save(s);
        return view(saved, results(saved));
    }

    @Transactional
    public SavedSearchView update(Long id, SavedSearchRequest request) {
        SavedSearch s = find(id);
        apply(request, s);
        SavedSearch saved = searches.save(s);
        return view(saved, results(saved));
    }

    @Transactional
    public void delete(Long id) {
        searches.delete(find(id));
    }

    /** Wyniki wyszukiwania i id ofert nowych od ostatniego przejrzenia (bez oznaczania jako przejrzane). */
    public SavedSearchResults results(Long id) {
        SavedSearch s = find(id);
        List<RankedOffer> items = results(s);
        List<Long> fresh = items.stream().filter(r -> isNew(r, s.getLastCheckedAt())).map(r -> r.offer().id()).toList();
        return new SavedSearchResults(view(s, items), items, fresh);
    }

    /** Oznacza wyniki jako przejrzane — licznik nowych ofert wraca do zera. */
    @Transactional
    public SavedSearchView markSeen(Long id) {
        SavedSearch s = find(id);
        s.setLastCheckedAt(Instant.now());
        SavedSearch saved = searches.save(s);
        return view(saved, results(saved));
    }

    // ------------------------------------------------------------------

    private SavedSearch find(Long id) {
        return searches.findById(id).orElseThrow(() -> NotFoundException.of("Zapisane wyszukiwanie", id));
    }

    private List<RankedOffer> results(SavedSearch s) {
        Long cvId = effectiveCvId(s, existingCvIds());
        return ranking.rank(query(s, cvId));
    }

    static RankingQuery query(SavedSearch s, Long cvId) {
        return new RankingQuery(cvId, new HashSet<>(s.getStatuses()), s.getQuery(), s.isIncludeExcluded(),
                s.getMinScore(), new HashSet<>(s.getWorkModes()), s.getMinSalary(), RankingSort.RANK);
    }

    /** „Nowa” oferta: dodana po ostatnim przejrzeniu wyników. */
    static boolean isNew(RankedOffer r, Instant lastCheckedAt) {
        Instant created = r.offer().createdAt();
        return created != null && lastCheckedAt != null && created.isAfter(lastCheckedAt);
    }

    static SavedSearchView view(SavedSearch s, List<RankedOffer> items) {
        int fresh = (int) items.stream().filter(r -> isNew(r, s.getLastCheckedAt())).count();
        return new SavedSearchView(s.getId(), s.getName(), s.getQuery(), s.getMinScore(), s.getMinSalary(),
                List.copyOf(s.getWorkModes()), List.copyOf(s.getStatuses()), s.isIncludeExcluded(), s.getCvId(),
                s.getLastCheckedAt(), s.getCreatedAt(), items.size(), fresh);
    }

    private Set<Long> existingCvIds() {
        return profiles.cvVariants().stream().map(CvVariant::getId).collect(Collectors.toSet());
    }

    /** Usunięta wersja CV nie psuje wyszukiwania — wtedy używamy aktywnej. */
    private static Long effectiveCvId(SavedSearch s, Set<Long> existing) {
        return s.getCvId() != null && existing.contains(s.getCvId()) ? s.getCvId() : null;
    }

    private void apply(SavedSearchRequest r, SavedSearch s) {
        if (r == null || Text.isBlank(r.name())) {
            throw new BadRequestException("Podaj nazwę wyszukiwania");
        }
        if (r.name().strip().length() > NAME_MAX) {
            throw new BadRequestException("Nazwa wyszukiwania może mieć najwyżej " + NAME_MAX + " znaków");
        }
        if (r.query() != null && r.query().strip().length() > QUERY_MAX) {
            throw new BadRequestException("Fraza wyszukiwania może mieć najwyżej " + QUERY_MAX + " znaków");
        }
        if (r.minScore() != null && (r.minScore() < 0 || r.minScore() > 100)) {
            throw new BadRequestException("Minimalne dopasowanie musi mieścić się w zakresie 0–100");
        }
        if (r.minSalary() != null && r.minSalary() < 0) {
            throw new BadRequestException("Minimalne wynagrodzenie nie może być ujemne");
        }
        if (r.cvId() != null) {
            profiles.cv(r.cvId());
        }
        s.setName(r.name().strip());
        s.setQuery(Text.isBlank(r.query()) ? null : r.query().strip());
        s.setMinScore(r.minScore());
        s.setMinSalary(r.minSalary() == null || r.minSalary() == 0 ? null : r.minSalary());
        s.setWorkModes(r.workModes() == null ? List.of()
                : r.workModes().stream().filter(Objects::nonNull).distinct().sorted().toList());
        s.setStatuses(r.statuses() == null ? List.of()
                : r.statuses().stream().filter(Objects::nonNull).distinct().sorted().toList());
        s.setIncludeExcluded(Boolean.TRUE.equals(r.includeExcluded()));
        s.setCvId(r.cvId());
    }
}
