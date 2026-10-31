package pl.radarpracy.offer.analysis;

import pl.radarpracy.offer.OfferStatus;

import java.util.List;

/**
 * Wynik wyszukiwania duplikatów (#16). Próg i składniki wyniku są jawne.
 *
 * @param offerId    oferta, dla której szukano (null przy sprawdzaniu szkicu przed zapisem)
 * @param threshold  od tego wyniku oferta jest „prawdopodobnym duplikatem”
 * @param minScore   od tego wyniku oferta jest pokazywana jako podobna
 * @param weights    wagi składników (firma, tytuł, treść)
 * @param candidates podobne oferty, od najbardziej podobnej
 */
public record DuplicateReport(Long offerId, double threshold, double minScore, Weights weights,
                              List<Candidate> candidates) {

    public record Weights(double company, double title, double content) {
    }

    /**
     * Podobna oferta.
     *
     * @param duplicate     wynik ≥ progu
     * @param alreadyMarked oferta jest już oznaczona jako duplikat tej oferty (lub odwrotnie)
     * @param older         czy kandydat został dodany wcześniej (sugerowany oryginał)
     */
    public record Candidate(Long offerId, String title, String company, String source, OfferStatus status,
                            double score, Double companyScore, Double titleScore, Double contentScore,
                            boolean sameUrl, boolean duplicate, boolean alreadyMarked, boolean older,
                            List<String> reasons) {
    }
}
