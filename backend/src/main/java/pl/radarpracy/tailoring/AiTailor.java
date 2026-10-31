package pl.radarpracy.tailoring;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.stereotype.Component;
import pl.radarpracy.ai.ClaudeClient;
import pl.radarpracy.ai.LlmUnavailableException;
import pl.radarpracy.ai.PiiMasker;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.Requirement;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.tailoring.GuardReport.Violation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Opcjonalna redakcja AI dopasowanego CV (pomysł #67).
 *
 * <p>Model dostaje tylko podsumowanie i punkty doświadczenia (bez bloku danych osobowych), po
 * maskowaniu PII, w tagach danych ({@link ClaudeClient#asData}). Ma wyłącznie przeredagować tekst —
 * bez nowych faktów, umiejętności i liczb. Każde zdanie z odpowiedzi przechodzi strażnika
 * prawdomówności; odrzucone wracają do wersji offline.</p>
 */
@Component
public class AiTailor {

    private static final String SPLIT = "\n@@@KONIEC-CV@@@\n";
    private static final Pattern LEFTOVER_TAG = Pattern.compile("\\[[A-ZĄĆĘŁŃÓŚŹŻ ]+(?:-\\d+)?]");

    static final String SYSTEM = """
            Jesteś redaktorem CV. Przeredaguj podsumowanie i punkty doświadczenia kandydata tak, aby \
            lepiej odpowiadały wymaganiom oferty: wysuń na początek to, co dotyczy wymagań, używaj słów z oferty \
            tylko wtedy, gdy opisują dokładnie to samo, co już jest w CV, pisz zwięźle i konkretnie.
            Twarde zasady: tylko przeredaguj. Nie dodawaj faktów, umiejętności, technologii, liczb, procentów, \
            dat, nazw firm ani obowiązków, których nie ma w CV. Nie zmieniaj znaczenia ani skali osiągnięć. \
            Każdy punkt redaguj osobno i zachowaj jego identyfikator. Jeśli punktu nie da się poprawić, \
            zwróć go bez zmian.
            """ + ClaudeClient.DATA_RULES;

    /** Przeredagowany punkt. */
    public record AiBullet(
            @JsonPropertyDescription("identyfikator punktu z CV, np. r0b2") String id,
            @JsonPropertyDescription("przeredagowana treść punktu, bez punktora") String text) {
    }

    /** Odpowiedź modelu. */
    public record AiRewrite(
            @JsonPropertyDescription("przeredagowane podsumowanie, jedno zdanie lub akapit na element") List<String> summary,
            @JsonPropertyDescription("przeredagowane punkty doświadczenia") List<AiBullet> bullets) {
    }

    /** Zdanie zaproponowane przez AI i odrzucone przez strażnika. */
    public record Rejection(String original, String proposed, String reason) {
    }

    /**
     * Wynik redakcji.
     *
     * @param draft    wersja po redakcji (lub offline)
     * @param source   „ai”, gdy przyjęto co najmniej jedną zmianę modelu, w przeciwnym razie „offline”
     * @param accepted liczba przyjętych zmian
     * @param rejected zdania odrzucone przez strażnika (wróciły do wersji offline)
     * @param note     informacja dla kandydata (np. dlaczego AI nie zadziałało)
     */
    public record Result(TailorDraft draft, String source, int accepted, List<Rejection> rejected, String note) {
    }

    private final ClaudeClient claude;
    private final PiiMasker masker;
    private final TruthGuard guard;

    public AiTailor(ClaudeClient claude, PiiMasker masker, TruthGuard guard) {
        this.claude = claude;
        this.masker = masker;
        this.guard = guard;
    }

    public boolean available() {
        return claude.available();
    }

    public Result improve(TailorDraft draft, JobOffer offer, CandidateProfile profile, TruthGuard.Facts facts) {
        if (!claude.available()) {
            return new Result(draft, "offline", 0, List.of(),
                    "AI niedostępne (" + claude.status().reason() + ") — pokazujemy wersję offline.");
        }
        List<String> summary = draft.summaryLines();
        Map<String, String> originals = new LinkedHashMap<>();
        for (TailorDraft.Line b : draft.bullets()) {
            originals.put(b.id(), b.content());
        }
        StringBuilder cv = new StringBuilder("PODSUMOWANIE:\n");
        summary.forEach(l -> cv.append(l).append('\n'));
        cv.append("\nPUNKTY DOŚWIADCZENIA:\n");
        originals.forEach((id, text) -> cv.append('[').append(id).append("] ").append(text).append('\n'));
        String offerText = offerText(offer);

        PiiMasker.Masked masked = masker.mask(cv + SPLIT + offerText, profile);
        int cut = masked.text().indexOf(SPLIT.strip());
        String maskedCv = cut < 0 ? masked.text() : masked.text().substring(0, cut);
        String maskedOffer = cut < 0 ? "" : masked.text().substring(cut + SPLIT.strip().length());
        String user = ClaudeClient.asData("oferta", maskedOffer.strip()) + "\n\n"
                + ClaudeClient.asData("cv", maskedCv.strip()) + "\n\n"
                + "Zwróć przeredagowane podsumowanie (summary) i wszystkie punkty (bullets) z ich identyfikatorami.";

        AiRewrite rewrite;
        try {
            rewrite = claude.completeStructured(SYSTEM, user, AiRewrite.class);
        } catch (LlmUnavailableException e) {
            return new Result(draft, "offline", 0, List.of(),
                    "AI niedostępne (" + e.getMessage() + ") — pokazujemy wersję offline.");
        }
        if (rewrite == null) {
            return new Result(draft, "offline", 0, List.of(), "Model nie zwrócił danych — wersja offline.");
        }

        List<Rejection> rejected = new ArrayList<>();
        int accepted = 0;

        // podsumowanie: przyjmujemy w całości tylko wtedy, gdy każde zdanie przejdzie kontrolę
        List<String> newSummary = null;
        if (rewrite.summary() != null && !rewrite.summary().isEmpty()) {
            String original = String.join(" ", summary);
            List<String> candidate = new ArrayList<>();
            boolean ok = true;
            for (String s : rewrite.summary()) {
                String text = clean(masked.unmask(s));
                if (text.isEmpty()) {
                    continue;
                }
                List<Violation> v = check(facts, original, text, 0.0);
                if (!v.isEmpty()) {
                    ok = false;
                    rejected.add(new Rejection(original, text, reasons(v)));
                }
                candidate.add(text);
            }
            if (ok && !candidate.isEmpty() && !candidate.equals(summary)) {
                newSummary = candidate;
                accepted++;
            }
        }

        Map<String, String> bulletRewrites = new LinkedHashMap<>();
        if (rewrite.bullets() != null) {
            for (AiBullet b : rewrite.bullets()) {
                if (b == null || b.id() == null || !originals.containsKey(b.id().strip())) {
                    continue;
                }
                String id = b.id().strip();
                String original = originals.get(id);
                String text = clean(masked.unmask(b.text()));
                if (text.isEmpty() || TruthGuard.norm(text).equals(TruthGuard.norm(original))) {
                    continue;
                }
                List<Violation> v = check(facts, original, text, 0.4);
                if (v.isEmpty()) {
                    bulletRewrites.put(id, text);
                    accepted++;
                } else {
                    rejected.add(new Rejection(original, text, reasons(v)));
                }
            }
        }
        TailorDraft result = draft.withRewrites(newSummary, bulletRewrites);
        String note = accepted == 0
                ? "AI nie zaproponowało zmian, które przeszłyby kontrolę — pokazujemy wersję offline."
                : rejected.isEmpty() ? "AI przeredagowało " + accepted + " fragm. — wszystkie przeszły kontrolę."
                : "AI przeredagowało " + accepted + " fragm.; " + rejected.size()
                + " odrzucił strażnik (wróciły do wersji offline).";
        return new Result(result, accepted > 0 ? "ai" : "offline", accepted, List.copyOf(rejected), note);
    }

    private List<Violation> check(TruthGuard.Facts facts, String original, String text, double minFromOriginal) {
        List<Violation> v = new ArrayList<>(guard.checkRewrite(facts, original, text, minFromOriginal));
        if (LEFTOVER_TAG.matcher(text).find()) {
            v.add(new Violation(0, GuardReport.Kind.UNVERIFIED_LINE, "Zawiera znacznik zamiast danych.", text));
        }
        return v;
    }

    private static String reasons(List<Violation> v) {
        return v.stream().map(Violation::detail).distinct().collect(Collectors.joining(" "));
    }

    /** Usuwa punktor i identyfikator, jeśli model je powtórzył. */
    static String clean(String s) {
        if (s == null) {
            return "";
        }
        String t = s.strip().replaceAll("^\\[r\\d+b\\d+]\\s*", "");
        return CvStructureParser.stripBullet(t).strip();
    }

    private static String offerText(JobOffer offer) {
        StringBuilder sb = new StringBuilder();
        sb.append("Stanowisko: ").append(offer.getTitle()).append('\n');
        sb.append("Wymagania:\n");
        for (Requirement r : offer.getRequirements()) {
            sb.append("- ").append(r.text()).append(r.mustHave() ? " (obowiązkowe)" : " (mile widziane)").append('\n');
        }
        return sb.toString();
    }
}
