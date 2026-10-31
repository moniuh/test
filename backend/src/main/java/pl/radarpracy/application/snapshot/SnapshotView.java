package pl.radarpracy.application.snapshot;

import java.time.Instant;

/**
 * „Co wysłano” — snapshot dokumentów z chwili aplikowania i porównanie z bieżącą wersją CV (pomysł #46).
 *
 * @param hasSnapshot         czy snapshot istnieje
 * @param snapshotAt          kiedy go zapisano (null dla starszych aplikacji bez znacznika czasu)
 * @param cvSnapshot          treść CV wysłana do firmy
 * @param coverLetterSnapshot list motywacyjny w chwili wysłania (null = nie dołączono listu)
 * @param currentCvExists     czy wersja CV nadal istnieje w profilu
 * @param currentCvName       bieżąca nazwa wersji CV
 * @param cvChanged           czy bieżąca treść tej wersji CV różni się od wysłanej (null = nie da się porównać)
 * @param linesAdded          linie dodane w CV od wysłania
 * @param linesRemoved        linie usunięte z CV od wysłania
 * @param coverLetterChanged  czy list w aplikacji edytowano po wysłaniu (null = nie da się porównać)
 */
public record SnapshotView(Long applicationId, boolean hasSnapshot, Instant snapshotAt, Long cvVariantId,
                           String cvVariantName, String cvSnapshot, String coverLetterSnapshot,
                           boolean currentCvExists, String currentCvName, Boolean cvChanged, int linesAdded,
                           int linesRemoved, Boolean coverLetterChanged) {
}
