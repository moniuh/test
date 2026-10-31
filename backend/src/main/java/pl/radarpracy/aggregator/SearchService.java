package pl.radarpracy.aggregator;

import org.springframework.stereotype.Service;
import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.offer.OfferStatus;
import pl.radarpracy.skills.Skill;
import pl.radarpracy.skills.SkillDictionary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wyszukiwarka pełnotekstowa po wszystkich ofertach (pomysł #61) — ranking BM25F z {@link Bm25Search}.
 * Słowa zapytania będące aliasami umiejętności (np. „k8s”, „postgres”) są rozszerzane o nazwę kanoniczną.
 */
@Service
public class SearchService {

    /** Wynik wyszukiwania. */
    public record Result(Long offerId, String title, String company, String location, OfferStatus status,
                         String sourceName, double score, List<String> matchedFields, String snippet,
                         List<int[]> highlights) {
    }

    /** Odpowiedź z rozbiorem zapytania (do pokazania „szukano: …”). */
    public record Response(String query, int total, List<Result> results, List<String> terms, List<String> phrases,
                           List<String> excluded) {
    }

    private final OfferService offers;
    private final SkillDictionary dictionary;
    private final AggregatedOfferRepository meta;

    public SearchService(OfferService offers, SkillDictionary dictionary, AggregatedOfferRepository meta) {
        this.offers = offers;
        this.dictionary = dictionary;
        this.meta = meta;
    }

    public Response search(String q, OfferStatus status, int limit) {
        Bm25Search.Query query = expand(Bm25Search.parse(q), q);
        if (query.isEmpty()) {
            return new Response(Text.nullToEmpty(q), 0, List.of(), query.terms(), query.phrases(),
                    excludedLabels(query));
        }
        List<JobOffer> all = offers.all().stream().filter(o -> status == null || o.getStatus() == status).toList();
        Map<Long, JobOffer> byId = all.stream().collect(Collectors.toMap(JobOffer::getId, Function.identity()));
        List<Bm25Search.Doc> docs = new ArrayList<>(all.size());
        for (JobOffer o : all) {
            String skills = String.join(" ", dictionary.skillsIn(o.getTitle() + "\n" + Text.nullToEmpty(o.getDescription())));
            docs.add(new Bm25Search.Doc(o.getId(), Text.nullToEmpty(o.getTitle()), Text.nullToEmpty(o.getCompany()),
                    skills, Text.nullToEmpty(o.getDescription())));
        }
        List<Bm25Search.Hit> hits = Bm25Search.search(docs, query);
        Map<Long, String> sourceNames = meta.findBySourceIdIsNotNull().stream()
                .collect(Collectors.toMap(AggregatedOffer::getOfferId, AggregatedOffer::getSourceName, (a, b) -> a));
        List<Result> results = new ArrayList<>();
        for (Bm25Search.Hit h : hits.subList(0, Math.min(hits.size(), Math.max(1, limit)))) {
            JobOffer o = byId.get(h.id());
            Bm25Search.Snippet sn = Bm25Search.snippet(o.getDescription(), query, 220);
            results.add(new Result(o.getId(), o.getTitle(), o.getCompany(), o.getLocation(), o.getStatus(),
                    sourceNames.getOrDefault(o.getId(), o.getSource()), Math.round(h.score() * 100) / 100.0,
                    h.fields().stream().map(Bm25Search.Field::label).toList(), sn.snippet(), sn.highlights()));
        }
        return new Response(Text.nullToEmpty(q), hits.size(), results, query.terms(), query.phrases(),
                excludedLabels(query));
    }

    /** Rozszerzenie słów o nazwy kanoniczne umiejętności („k8s” → „kubernetes”). */
    private Bm25Search.Query expand(Bm25Search.Query query, String raw) {
        Set<String> terms = new LinkedHashSet<>(query.terms());
        for (String word : Text.nullToEmpty(raw).split("\\s+")) {
            if (word.isBlank() || word.startsWith("-") || word.contains("\"")) {
                continue;
            }
            dictionary.resolve(word).map(Skill::name).ifPresent(name -> {
                for (String t : Bm25Search.analyze(name)) {
                    if (!query.excludedTerms().contains(t)) {
                        terms.add(t);
                    }
                }
            });
        }
        return new Bm25Search.Query(List.copyOf(terms), query.phrases(), query.excludedTerms(), query.excludedPhrases());
    }

    private static List<String> excludedLabels(Bm25Search.Query q) {
        List<String> out = new ArrayList<>(q.excludedTerms());
        out.addAll(q.excludedPhrases());
        return out;
    }
}
