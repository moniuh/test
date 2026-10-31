package pl.radarpracy.matching.ranking;

import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;

import java.util.Collection;
import java.util.stream.Collectors;

/** Polskie etykiety i formatowanie używane w wyjaśnieniach rankingu. */
final class RankingLabels {

    private RankingLabels() {
    }

    static String workMode(WorkMode m) {
        return switch (m) {
            case REMOTE -> "zdalnie";
            case HYBRID -> "hybrydowo";
            case ONSITE -> "stacjonarnie";
        };
    }

    static String contract(ContractType c) {
        return switch (c) {
            case UOP -> "umowa o pracę";
            case B2B -> "B2B";
            case UZ -> "umowa zlecenie";
            case UOD -> "umowa o dzieło";
            case INTERNSHIP -> "staż";
        };
    }

    static String seniority(Seniority s) {
        return switch (s) {
            case INTERN -> "Stażysta";
            case JUNIOR -> "Junior";
            case MID -> "Mid";
            case SENIOR -> "Senior";
            case LEAD -> "Lead";
        };
    }

    static String workModes(Collection<WorkMode> modes) {
        return modes.stream().map(RankingLabels::workMode).collect(Collectors.joining(", "));
    }

    static String contracts(Collection<ContractType> types) {
        return types.stream().map(RankingLabels::contract).collect(Collectors.joining(", "));
    }

    /** Kwota z separatorem tysięcy (twarda spacja), np. „15 000 zł”. */
    static String money(long amount) {
        String digits = Long.toString(Math.abs(amount));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(digits.charAt(i));
        }
        return (amount < 0 ? "−" : "") + sb + " zł";
    }

    /** Odmiana rzeczownika po liczebniku: 1 oferta, 2 oferty, 5 ofert. */
    static String plural(long n, String one, String few, String many) {
        if (n == 1) {
            return one;
        }
        long mod10 = n % 10;
        long mod100 = n % 100;
        return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? few : many;
    }

    /** Punkty ze znakiem, np. „+4”, „−3”. */
    static String signedPoints(long points) {
        return points < 0 ? "−" + Math.abs(points) : "+" + points;
    }
}
