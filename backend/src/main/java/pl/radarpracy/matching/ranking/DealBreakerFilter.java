package pl.radarpracy.matching.ranking;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.salary.NormalizedSalary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Twarde filtry deal-breakerów (pomysł #32). Oferta jest wykluczona, gdy:
 * <ul>
 *   <li>fraza z {@code dealBreakers} występuje w treści (porównanie po {@link Text#fold}, odstępy dowolne,
 *       fraza musi zaczynać się na początku słowa — „nadgodzin” pasuje do „nadgodzinach”);</li>
 *   <li>tryb pracy jest spoza akceptowanych;</li>
 *   <li>praca wymaga obecności w biurze w mieście spoza preferowanych, a kandydat nie rozważa przeprowadzki;</li>
 *   <li>oferta proponuje wyłącznie nieakceptowane formy umowy;</li>
 *   <li>górna granica widełek (równoważnik UoP brutto) jest poniżej minimum z profilu.</li>
 * </ul>
 * Każde wykluczenie ma czytelny powód; dla fraz — także dosłowny cytat z ogłoszenia (sprawdzany w kodzie).
 */
final class DealBreakerFilter {

    private static final int QUOTE_MAX = 160;

    private DealBreakerFilter() {
    }

    static List<Exclusion> check(JobOffer offer, CandidateProfile profile, NormalizedSalary salary) {
        List<Exclusion> out = new ArrayList<>();
        if (profile == null) {
            return out;
        }
        String content = OfferTexts.fullContent(offer);
        for (String phrase : OfferTexts.nonNull(profile.getDealBreakers())) {
            findPhrase(content, phrase).ifPresent(quote -> out.add(new Exclusion(Exclusion.Type.DEAL_BREAKER,
                    "Zawiera wykluczającą frazę „" + phrase.strip() + "”.", quote)));
        }

        List<WorkMode> modes = OfferTexts.nonNull(profile.getWorkModes());
        if (offer.getWorkMode() != null && !modes.isEmpty() && !modes.contains(offer.getWorkMode())) {
            out.add(new Exclusion(Exclusion.Type.WORK_MODE, "Tryb pracy: " + RankingLabels.workMode(offer.getWorkMode())
                    + " — akceptujesz tylko: " + RankingLabels.workModes(modes) + ".", null));
        }

        if (!profile.isWillingToRelocate() && OfferTexts.requiresPresence(offer) && !Text.isBlank(offer.getLocation())
                && !OfferTexts.mentionsRemote(offer.getLocation())) {
            List<String> cities = OfferTexts.preferredCities(profile);
            if (!cities.isEmpty() && OfferTexts.matchingCity(offer.getLocation(), cities).isEmpty()) {
                out.add(new Exclusion(Exclusion.Type.LOCATION, "Praca " + presenceLabel(offer.getWorkMode()) + " w: "
                        + offer.getLocation().strip() + " — poza Twoimi lokalizacjami (" + String.join(", ", cities)
                        + "), a nie rozważasz przeprowadzki.", null));
            }
        }

        List<ContractType> accepted = OfferTexts.nonNull(profile.getContractTypes());
        List<ContractType> offered = OfferTexts.nonNull(offer.getContractTypes());
        if (!accepted.isEmpty() && !offered.isEmpty() && offered.stream().noneMatch(accepted::contains)) {
            out.add(new Exclusion(Exclusion.Type.CONTRACT, "Forma umowy: " + RankingLabels.contracts(offered)
                    + " — akceptujesz tylko: " + RankingLabels.contracts(accepted) + ".", null));
        }

        Integer min = profile.getMinMonthlySalary();
        if (min != null && min > 0 && salary != null && salary.present() && salary.comparableMax() != null
                && salary.comparableMax() < min) {
            out.add(new Exclusion(Exclusion.Type.SALARY, "Górna granica widełek ≈ "
                    + RankingLabels.money(salary.comparableMax()) + " (równoważnik UoP brutto) jest poniżej Twojego minimum "
                    + RankingLabels.money(min) + ".", null));
        }
        return out;
    }

    /**
     * Szuka frazy w tekście złożonym; zwraca dosłowny fragment oryginału (zdanie/linię) z trafieniem.
     * {@link Text#fold} zachowuje długość, więc indeksy w tekście złożonym wskazują oryginał.
     */
    static Optional<String> findPhrase(String content, String phrase) {
        if (Text.isBlank(phrase) || Text.isBlank(content)) {
            return Optional.empty();
        }
        String[] words = Text.fold(phrase).strip().split("\\s+");
        StringBuilder regex = new StringBuilder("(?<![\\p{L}\\p{N}])");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                regex.append("\\s+");
            }
            regex.append(Pattern.quote(words[i]));
        }
        Matcher m = Pattern.compile(regex.toString()).matcher(Text.fold(content));
        if (!m.find()) {
            return Optional.empty();
        }
        String quote = Text.quoteAround(content, m.start(), m.end(), QUOTE_MAX);
        // zasada: cytat musi być dosłownym fragmentem źródła
        return Optional.of(content.contains(quote) && !quote.isBlank() ? quote : content.substring(m.start(), m.end()));
    }

    private static String presenceLabel(WorkMode mode) {
        return mode == WorkMode.HYBRID ? "hybrydowa" : "stacjonarna";
    }
}
