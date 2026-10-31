package pl.radarpracy.analytics;

import pl.radarpracy.application.ApplicationStatus;
import pl.radarpracy.application.InterviewKind;

import java.util.Locale;

/** Polskie etykiety i formatowanie liczb używane w odpowiedziach i raporcie. */
final class Labels {

    private Labels() {
    }

    static String status(ApplicationStatus s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case PLANNED -> "Do wysłania";
            case APPLIED -> "Wysłana";
            case SCREENING -> "Pierwszy kontakt";
            case INTERVIEW -> "Rozmowy";
            case OFFER -> "Oferta";
            case ACCEPTED -> "Przyjęta";
            case REJECTED -> "Odrzucona";
            case WITHDRAWN -> "Wycofana";
            case NO_RESPONSE -> "Brak odpowiedzi";
        };
    }

    static String interviewKind(InterviewKind k) {
        if (k == null) {
            return "Rozmowa";
        }
        return switch (k) {
            case PHONE -> "Telefoniczna";
            case VIDEO -> "Wideo";
            case ONSITE -> "W biurze";
            case TECHNICAL -> "Techniczna";
            case HR -> "HR";
            case FINAL -> "Finałowa";
        };
    }

    /** Procent z jednym miejscem po przecinku; null, gdy mianownik to 0. */
    static Double percent(long part, long whole) {
        if (whole <= 0) {
            return null;
        }
        return Math.round(1000.0 * part / whole) / 10.0;
    }

    /** Procent jako tekst, np. „42%” lub „42,5%”. */
    static String pct(Double value) {
        if (value == null) {
            return "—";
        }
        if (value == Math.rint(value)) {
            return String.format(Locale.ROOT, "%.0f%%", value);
        }
        return String.format(Locale.ROOT, "%.1f%%", value).replace('.', ',');
    }

    /** Odmiana: 1 aplikacja, 2 aplikacje, 5 aplikacji. */
    static String plural(long n, String one, String few, String many) {
        if (n == 1) {
            return one;
        }
        long mod10 = n % 10;
        long mod100 = n % 100;
        return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many;
    }

    static String count(long n, String one, String few, String many) {
        return n + " " + plural(n, one, few, many);
    }
}
