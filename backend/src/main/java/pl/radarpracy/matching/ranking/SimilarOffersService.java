package pl.radarpracy.matching.ranking;

import org.springframework.stereotype.Service;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;

/** Podobne oferty — TF-IDF i podobieństwo cosinusowe (pomysł #36). */
@Service
public class SimilarOffersService {

    public static final int DEFAULT_LIMIT = 5;
    public static final int MAX_LIMIT = 20;

    private final OfferService offers;
    private final SkillDictionary dictionary;

    public SimilarOffersService(OfferService offers, SkillDictionary dictionary) {
        this.offers = offers;
        this.dictionary = dictionary;
    }

    /** Do {@code limit} ofert najbardziej podobnych do wskazanej (404, gdy oferta nie istnieje). */
    public List<SimilarOffer> similar(Long offerId, Integer limit) {
        JobOffer target = offers.get(offerId);
        int n = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
        List<JobOffer> all = offers.all();
        return new TfIdfIndex(all, dictionary).similarTo(target, all, n);
    }
}
