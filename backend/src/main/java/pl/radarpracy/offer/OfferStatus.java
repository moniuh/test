package pl.radarpracy.offer;

/** Stan oferty z punktu widzenia kandydata (aplikacje są osobnym bytem). */
public enum OfferStatus {
    /** nowa, nieoceniona */
    NEW,
    /** zapisana / polubiona */
    SAVED,
    /** odrzucona przez kandydata (z powodem) */
    REJECTED,
    /** zarchiwizowana (np. wygasła lub zaaplikowano i zamknięto) */
    ARCHIVED
}
