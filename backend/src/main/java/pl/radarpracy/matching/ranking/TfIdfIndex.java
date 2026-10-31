package pl.radarpracy.matching.ranking;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.skills.SkillDictionary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Indeks TF-IDF ofert i podobieństwo cosinusowe (pomysł #36).
 *
 * <p>Termy to słowa z {@link Text#tokens} (tytuł liczony podwójnie) oraz umiejętności ze słownika
 * jako osobne termy „skill:…” z wagą {@link #SKILL_BOOST} — dzięki temu „Spring Boot” i „springboot”
 * to ta sama cecha, a wspólne technologie ważą więcej niż wspólne słowa. TF jest logarytmiczne
 * (1 + ln n), IDF wygładzone: ln((1 + N) / (1 + df)) + 1. Pomijamy słowa typowe dla każdego ogłoszenia
 * („wymagania”, „oferujemy”…), żeby nie zawyżały podobieństwa niepowiązanych ofert.</p>
 */
final class TfIdfIndex {

    static final double SKILL_BOOST = 3.0;
    static final double MIN_SIMILARITY = 0.1;
    private static final int MIN_TOKEN = 3;
    private static final String SKILL_PREFIX = "skill:";
    private static final Set<String> JOB_AD_STOPWORDS = Set.of(
            "wymagania", "wymagamy", "oczekujemy", "oferujemy", "zapewniamy", "oferta", "oferty", "ofercie",
            "doswiadczenie", "doswiadczenia", "doswiadczeniem", "komercyjnego", "komercyjne", "minimum", "requirements",
            "nice", "plus", "benefity", "benefits", "zadania", "obowiazki", "twoje", "naszego", "nasz", "nasza",
            "about", "offer", "team", "zespol", "zespole", "zespolu", "firma", "firmy", "mies", "brutto", "netto",
            "vat", "pln", "zl");

    private final Map<Long, Map<String, Double>> vectors = new LinkedHashMap<>();
    private final Map<Long, Set<String>> skills = new HashMap<>();

    TfIdfIndex(List<JobOffer> offers, SkillDictionary dictionary) {
        Map<Long, Map<String, Double>> tfs = new LinkedHashMap<>();
        Map<String, Integer> df = new HashMap<>();
        for (JobOffer o : offers) {
            if (o.getId() == null) {
                continue;
            }
            String content = OfferTexts.content(o);
            Map<String, Integer> counts = new HashMap<>();
            for (String t : Text.tokens(Text.nullToEmpty(o.getTitle()) + "\n" + content, MIN_TOKEN)) {
                if (!JOB_AD_STOPWORDS.contains(t)) {
                    counts.merge(t, 1, Integer::sum);
                }
            }
            Map<String, Double> tf = new HashMap<>();
            counts.forEach((t, n) -> tf.put(t, 1 + Math.log(n)));
            Set<String> offerSkills = dictionary.skillsIn(content);
            for (String s : offerSkills) {
                tf.put(SKILL_PREFIX + s, SKILL_BOOST);
            }
            skills.put(o.getId(), offerSkills);
            tfs.put(o.getId(), tf);
            for (String term : tf.keySet()) {
                df.merge(term, 1, Integer::sum);
            }
        }
        int n = tfs.size();
        for (Map.Entry<Long, Map<String, Double>> e : tfs.entrySet()) {
            Map<String, Double> v = new HashMap<>();
            double norm = 0;
            for (Map.Entry<String, Double> t : e.getValue().entrySet()) {
                double w = t.getValue() * (Math.log((1.0 + n) / (1.0 + df.get(t.getKey()))) + 1);
                v.put(t.getKey(), w);
                norm += w * w;
            }
            double len = Math.sqrt(norm);
            if (len > 0) {
                v.replaceAll((k, w) -> w / len);
            }
            vectors.put(e.getKey(), v);
        }
    }

    /** Podobieństwo cosinusowe dwóch ofert z indeksu (0, gdy którejś nie ma). */
    double similarity(Long a, Long b) {
        Map<String, Double> va = vectors.get(a);
        Map<String, Double> vb = vectors.get(b);
        if (va == null || vb == null) {
            return 0;
        }
        Map<String, Double> small = va.size() <= vb.size() ? va : vb;
        Map<String, Double> big = small == va ? vb : va;
        double dot = 0;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double w = big.get(e.getKey());
            if (w != null) {
                dot += e.getValue() * w;
            }
        }
        return Math.min(1.0, Math.max(0.0, dot));
    }

    /**
     * Najbardziej podobne oferty — bez samej oferty i jej duplikatów (oryginał, kopie, „rodzeństwo”
     * wskazujące na ten sam oryginał). Kolejność: podobieństwo malejąco, potem id.
     */
    List<SimilarOffer> similarTo(JobOffer target, List<JobOffer> candidates, int limit) {
        List<SimilarOffer> out = new ArrayList<>();
        Set<String> targetSkills = skills.getOrDefault(target.getId(), Set.of());
        for (JobOffer o : candidates) {
            if (o.getId() == null || o.getId().equals(target.getId()) || duplicates(target, o)) {
                continue;
            }
            double sim = similarity(target.getId(), o.getId());
            if (sim < MIN_SIMILARITY) {
                continue;
            }
            Set<String> shared = new LinkedHashSet<>(targetSkills);
            shared.retainAll(skills.getOrDefault(o.getId(), Set.of()));
            out.add(new SimilarOffer(o.getId(), o.getTitle(), o.getCompany(), RankComponent.round(sim, 3),
                    List.copyOf(shared), o.getStatus()));
        }
        out.sort(Comparator.comparingDouble((SimilarOffer s) -> -s.similarity()).thenComparing(SimilarOffer::offerId));
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : out;
    }

    static boolean duplicates(JobOffer a, JobOffer b) {
        return Objects.equals(a.getDuplicateOfId(), b.getId()) || Objects.equals(b.getDuplicateOfId(), a.getId())
                || (a.getDuplicateOfId() != null && a.getDuplicateOfId().equals(b.getDuplicateOfId()));
    }
}
