package pl.radarpracy.tailoring;

import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.offer.OfferStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Obiekty żądań i odpowiedzi API tailoringu. */
public final class TailoringDtos {

    private TailoringDtos() {
    }

    /** Żądanie podglądu: oferta, CV bazowe (domyślnie aktywne) i opcje. */
    public record PreviewRequest(Long offerId, Long baseCvId, TailoringOptions options) {
    }

    /**
     * Zapis nowej wersji CV.
     *
     * @param content          treść do zapisu (z podglądu, ewentualnie po edycji); null = wygeneruj offline
     * @param name             nazwa wersji (domyślnie z tytułu oferty)
     * @param activate         ustaw jako aktywną wersję CV
     * @param acceptViolations jawne potwierdzenie zapisu mimo naruszeń strażnika
     * @param source           „ai” albo „offline” (z podglądu)
     * @param edited           czy kandydat edytował tekst
     */
    public record SaveRequest(Long offerId, Long baseCvId, TailoringOptions options, String content, String name,
                              Boolean activate, Boolean acceptViolations, String source, Boolean edited) {
    }

    /** Podgląd dopasowanego CV (bez zapisu). */
    public record Preview(Long offerId, String offerTitle, String offerCompany, Long baseCvId, String baseCvName,
                          String suggestedName, String content, String source, boolean aiAvailable, String aiNote,
                          List<AiTailor.Rejection> aiRejected, GuardReport guard, ScoreComparison comparison,
                          List<LineDiff.Entry> diff, List<String> competences, List<String> addedFromProfile,
                          List<TailorDraft.RoleChange> roles, TailoringOptions options) {
    }

    /** Kontrola dowolnego tekstu (np. po ręcznej edycji) przed zapisem. */
    public record CheckRequest(Long offerId, Long baseCvId, String content) {
    }

    /** Wynik kontroli: raport strażnika, wynik przed/po i różnice względem CV bazowego. */
    public record CheckResult(GuardReport guard, ScoreComparison comparison, List<LineDiff.Entry> diff) {
    }

    /** Oferta powiązana z dopasowaną wersją. */
    public record OfferRef(Long id, String title, String company, OfferStatus status, boolean exists) {
    }

    /** Aplikacja na ofertę z informacją, czy użyto w niej tej wersji CV. */
    public record ApplicationRef(Long id, ApplicationStatus status, LocalDate appliedAt, Long cvVariantId,
                                 String cvVariantName, boolean usesThisVersion) {
    }

    /** Pozycja biblioteki dopasowanych CV (#94). */
    public record LibraryItem(Long id, Long cvVariantId, String name, boolean cvExists, boolean active, Long baseCvId,
                              String baseCvName, OfferRef offer, int scoreBefore, int scoreAfter, String source,
                              boolean edited, boolean acceptedViolations, int violations, Instant createdAt,
                              List<ApplicationRef> applications) {
    }

    /** Szczegóły dopasowanej wersji: treść, raport strażnika, aktualne porównanie i różnice. */
    public record Detail(LibraryItem item, String content, GuardReport guard, ScoreComparison comparison,
                         List<LineDiff.Entry> diff, TailoringOptions options) {
    }

    /** Wynik usunięcia z biblioteki. */
    public record DeleteResult(boolean cvDeleted, String message) {
    }

    /** Oferta do wyboru na stronie biblioteki (NEW/SAVED, posortowane wg dopasowania). */
    public record OfferOption(Long id, String title, String company, OfferStatus status, int score,
                              int tailoredCount) {
    }
}
