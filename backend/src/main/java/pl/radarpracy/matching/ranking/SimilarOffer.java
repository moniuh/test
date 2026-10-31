package pl.radarpracy.matching.ranking;

import pl.radarpracy.offer.OfferStatus;

import java.util.List;

/**
 * Oferta podobna do wskazanej (pomysł #36).
 *
 * @param offerId      id oferty
 * @param title        stanowisko
 * @param company      firma
 * @param similarity   podobieństwo cosinusowe wektorów TF-IDF, 0–1
 * @param sharedSkills wspólne umiejętności ze słownika
 * @param status       status oferty
 */
public record SimilarOffer(Long offerId, String title, String company, double similarity, List<String> sharedSkills,
                           OfferStatus status) {
}
