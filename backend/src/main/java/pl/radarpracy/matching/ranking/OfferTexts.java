package pl.radarpracy.matching.ranking;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.offer.WorkMode;
import pl.radarpracy.profile.CandidateProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Teksty oferty i porównywanie lokalizacji — wspólne dla rankingu, filtrów i podobieństwa. */
final class OfferTexts {

    /** Długość rdzenia przy porównywaniu nazw miast (odporne na odmianę: Kraków / Krakowie). */
    private static final int CITY_STEM = 5;

    private OfferTexts() {
    }

    /** Treść merytoryczna: tytuł, opis i wymagania, których nie ma już w opisie. */
    static String content(JobOffer o) {
        StringBuilder sb = new StringBuilder(Text.nullToEmpty(o.getTitle()));
        String description = Text.nullToEmpty(o.getDescription());
        if (!description.isBlank()) {
            sb.append('\n').append(description);
        }
        if (o.getRequirements() != null) {
            for (Requirement r : o.getRequirements()) {
                if (r != null && !Text.isBlank(r.text()) && !description.contains(r.text())) {
                    sb.append('\n').append(r.text());
                }
            }
        }
        return sb.toString();
    }

    /** Cała treść ogłoszenia (z benefitami i tagami) — tu szukamy fraz wykluczających. */
    static String fullContent(JobOffer o) {
        StringBuilder sb = new StringBuilder(content(o));
        for (String b : nonNull(o.getBenefits())) {
            sb.append('\n').append(b);
        }
        for (String t : nonNull(o.getTags())) {
            sb.append('\n').append(t);
        }
        return sb.toString();
    }

    /** Tekst przeszukiwany przez wyszukiwarkę (w postaci złożonej). */
    static String searchable(JobOffer o) {
        return Text.fold(fullContent(o) + '\n' + Text.nullToEmpty(o.getCompany()) + '\n'
                + Text.nullToEmpty(o.getLocation()));
    }

    // --- lokalizacje ---

    /** Czy tekst oznacza pracę zdalną („zdalnie”, „praca zdalna”, „remote”). */
    static boolean mentionsRemote(String text) {
        for (String t : Text.tokens(text, 3)) {
            if (t.startsWith("zdaln") || t.equals("remote") || t.equals("remotely")) {
                return true;
            }
        }
        return false;
    }

    /** Praca zdalna: tryb REMOTE albo — gdy tryb nieznany — lokalizacja „zdalnie”. */
    static boolean isRemote(JobOffer o) {
        return o.getWorkMode() == WorkMode.REMOTE || (o.getWorkMode() == null && mentionsRemote(o.getLocation()));
    }

    /** Czy oferta wymaga obecności w biurze (tryb stacjonarny lub hybrydowy). */
    static boolean requiresPresence(JobOffer o) {
        return o.getWorkMode() == WorkMode.ONSITE || o.getWorkMode() == WorkMode.HYBRID;
    }

    /** Miasta kandydata: preferowane lokalizacje (bez „zdalnie”) i miasto zamieszkania. */
    static List<String> preferredCities(CandidateProfile p) {
        List<String> out = new ArrayList<>();
        for (String loc : nonNull(p.getPreferredLocations())) {
            if (!Text.isBlank(loc) && !mentionsRemote(loc)) {
                out.add(loc.strip());
            }
        }
        if (!Text.isBlank(p.getLocation()) && !mentionsRemote(p.getLocation())
                && out.stream().noneMatch(c -> Text.fold(c).equals(Text.fold(p.getLocation().strip())))) {
            out.add(p.getLocation().strip());
        }
        return out;
    }

    /** Pierwsze miasto z listy, które występuje w lokalizacji oferty (porównanie rdzeni słów). */
    static Optional<String> matchingCity(String offerLocation, List<String> cities) {
        List<String> offerStems = Text.tokens(offerLocation, 2).stream().map(t -> Text.stem(t, CITY_STEM)).toList();
        for (String city : cities) {
            List<String> cityStems = Text.tokens(city, 2).stream().map(t -> Text.stem(t, CITY_STEM)).toList();
            if (!cityStems.isEmpty() && offerStems.containsAll(cityStems)) {
                return Optional.of(city);
            }
        }
        return Optional.empty();
    }

    static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
