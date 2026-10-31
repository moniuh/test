package pl.radarpracy.aggregator;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.Seniority;
import pl.radarpracy.offer.WorkMode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimalne wykrywanie trybu pracy, formy umowy, poziomu stanowiska i daty ważności z tekstu ogłoszenia
 * (część pomysłu #49). Po scaleniu z modułem analizy ofert może zostać zastąpione wspólną implementacją.
 */
public final class OfferTextAnalyzer {

    private static final Pattern REMOTE = Pattern.compile(
            "(?<![a-z])(100\\s*%\\s*(zdaln|remote)|w\\s+pelni\\s+zdaln|fully\\s+remote|full\\s+remote|praca\\s+zdalna|pracy\\s+zdalnej"
                    + "|zdalnie|zdalna|remote|home\\s*office|z\\s+domu)");
    private static final Pattern FULLY_REMOTE = Pattern.compile(
            "(100\\s*%\\s*(zdaln|remote)|w\\s+pelni\\s+zdaln|fully\\s+remote|full\\s+remote|tylko\\s+zdaln|wylacznie\\s+zdaln)");
    private static final Pattern HYBRID = Pattern.compile("(?<![a-z])(hybryd|hybrid)");
    private static final Pattern ONSITE = Pattern.compile(
            "(?<![a-z])(stacjonarn|on-?site|w\\s+biurze|z\\s+biura|praca\\s+w\\s+siedzibie|in\\s+the\\s+office|office-based)");
    private static final Pattern UOP = Pattern.compile(
            "(?<![a-z])(umow\\w*\\s+o\\s+prace|uop|employment\\s+contract|permanent\\s+contract)(?![a-z])");
    private static final Pattern B2B = Pattern.compile("(?<![a-z])(b2b|faktur\\w*\\s+vat|contractor)(?![a-z])");
    /** „B2B” jako model biznesu, nie forma umowy: „projekty B2B”, „sprzedaż B2B”, „B2B SaaS”. */
    private static final Pattern B2B_BUSINESS = Pattern.compile(
            "(projekt\\w*|sprzedaz\\w*|sprzedawc\\w*|klient\\w*|rynk\\w*|rynek|sektor\\w*|segment\\w*|platform\\w*"
                    + "|e-?commerce|marketing\\w*|handl\\w*|produkt\\w*|aplikacj\\w*|uslug\\w*|obslug\\w*)\\s+b2b"
                    + "|b2b\\s+(saas|sales|marketing|e-?commerce|i\\s+b2c|/\\s*b2c)|b2c\\s*/\\s*b2b");
    private static final Pattern UZ = Pattern.compile("(?<![a-z])(umow\\w*\\s+zleceni\\w*|zlecenie)(?![a-z])");
    private static final Pattern UOD = Pattern.compile("(?<![a-z])(umow\\w*\\s+o\\s+dzielo)(?![a-z])");
    private static final Pattern INTERN = Pattern.compile("(?<![a-z])(oferta\\s+stazu|program\\w*\\s+stazow\\w*|umow\\w*\\s+stazow\\w*|staz\\s+(platny|letni|dla)|praktyki\\s+(studenckie|zawodowe|letnie)|internship|intern)(?![a-z])");
    private static final Pattern EXPIRES = Pattern.compile(
            "(wazn\\w*\\s+do|aplikuj\\s+do|zgloszenia\\s+(przyjmujemy\\s+)?do|termin\\s+(nadsylania|skladania)\\s+\\w+\\s+(up[lł]ywa\\s+)?|valid\\s+(until|through)|apply\\s+(by|before)|deadline)"
                    + "\\s*:?\\s*(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}\\.\\d{1,2}\\.\\d{4})");

    private OfferTextAnalyzer() {
    }

    /** Tryb pracy z tekstu (null, gdy nie wiadomo). */
    public static WorkMode workMode(String text) {
        String f = Text.fold(text);
        if (f.isBlank()) {
            return null;
        }
        boolean hybrid = HYBRID.matcher(f).find();
        boolean remote = REMOTE.matcher(f).find();
        boolean onsite = ONSITE.matcher(f).find();
        if (FULLY_REMOTE.matcher(f).find()) {
            return WorkMode.REMOTE;
        }
        if (hybrid) {
            return WorkMode.HYBRID;
        }
        if (remote && onsite) {
            return WorkMode.HYBRID;
        }
        if (remote) {
            return WorkMode.REMOTE;
        }
        return onsite ? WorkMode.ONSITE : null;
    }

    /** Formy umowy wspomniane w tekście (w stałej kolejności). */
    public static List<ContractType> contractTypes(String text) {
        String f = Text.fold(text);
        List<ContractType> out = new ArrayList<>();
        if (UOP.matcher(f).find()) {
            out.add(ContractType.UOP);
        }
        if (mentionsB2bContract(f)) {
            out.add(ContractType.B2B);
        }
        if (UZ.matcher(f).find()) {
            out.add(ContractType.UZ);
        }
        if (UOD.matcher(f).find()) {
            out.add(ContractType.UOD);
        }
        if (INTERN.matcher(f).find()) {
            out.add(ContractType.INTERNSHIP);
        }
        return out;
    }

    /** Czy któraś wzmianka o B2B dotyczy formy współpracy (a nie np. „projektów B2B”). */
    private static boolean mentionsB2bContract(String folded) {
        Matcher m = B2B.matcher(folded);
        while (m.find()) {
            if (!m.group(1).equals("b2b")) {
                return true;
            }
            int from = Math.max(0, m.start() - 25);
            int to = Math.min(folded.length(), m.end() + 15);
            Matcher business = B2B_BUSINESS.matcher(folded.substring(from, to));
            boolean businessSense = false;
            while (business.find()) {
                int bs = from + business.start();
                int be = from + business.end();
                if (bs <= m.start() && be >= m.end()) {
                    businessSense = true;
                }
            }
            if (!businessSense) {
                return true;
            }
        }
        return false;
    }

    /** Typy zatrudnienia schema.org → formy umowy (CONTRACTOR → B2B, INTERN → staż). */
    public static List<ContractType> fromEmploymentTypes(List<String> employmentTypes) {
        List<ContractType> out = new ArrayList<>();
        for (String t : employmentTypes) {
            switch (t) {
                case "CONTRACTOR" -> out.add(ContractType.B2B);
                case "INTERN", "INTERNSHIP" -> out.add(ContractType.INTERNSHIP);
                default -> {
                }
            }
        }
        return out;
    }

    /** Poziom stanowiska z tytułu (null, gdy tytuł go nie określa). */
    public static Seniority seniority(String title) {
        String f = " " + Text.fold(title).replaceAll("[^a-z0-9]+", " ") + " ";
        if (f.matches(".*\\s(intern|internship|stazysta|stazystka|staz|praktykant|praktykantka|trainee)\\s.*")) {
            return Seniority.INTERN;
        }
        if (f.matches(".*\\s(lead|leader|lider|liderka|principal|head|kierownik|kierowniczka|staff)\\s.*")) {
            return Seniority.LEAD;
        }
        if (f.matches(".*\\s(senior|starszy|starsza|sr)\\s.*")) {
            return Seniority.SENIOR;
        }
        if (f.matches(".*\\s(junior|mlodszy|mlodsza|jr|absolwent|absolwentka|entry)\\s.*")) {
            return Seniority.JUNIOR;
        }
        if (f.matches(".*\\s(mid|regular|middle)\\s.*")) {
            return Seniority.MID;
        }
        return null;
    }

    /** Data ważności wspomniana w treści („oferta ważna do 30.11.2026”). */
    public static LocalDate expiresAt(String text) {
        Matcher m = EXPIRES.matcher(Text.fold(text));
        if (m.find()) {
            return Dates.parse(m.group(m.groupCount()));
        }
        return null;
    }
}
