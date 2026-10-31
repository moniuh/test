package pl.radarpracy.application.letter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.radarpracy.ai.ClaudeClient;
import pl.radarpracy.ai.LlmUnavailableException;
import pl.radarpracy.ai.PiiMasker;
import pl.radarpracy.application.letter.CoverLetterResult.Evidence;
import pl.radarpracy.application.letter.CoverLetterTemplate.Language;
import pl.radarpracy.application.letter.CoverLetterTemplate.Tone;
import pl.radarpracy.common.BadRequestException;
import pl.radarpracy.common.Text;
import pl.radarpracy.matching.MatchResult;
import pl.radarpracy.matching.MatchService;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.OfferService;
import pl.radarpracy.profile.CandidateProfile;
import pl.radarpracy.profile.CvVariant;
import pl.radarpracy.profile.ProfileService;
import pl.radarpracy.skills.SkillDictionary;

import java.util.List;

/**
 * List motywacyjny oparty na dowodach (pomysł #42).
 *
 * <p>Dowody to dosłowne cytaty CV z {@link MatchService} (wymagania spełnione w pełni lub częściowo).
 * Offline list powstaje z szablonu ({@link CoverLetterTemplate}). Z AI: dane osobowe są maskowane
 * ({@link PiiMasker}), ogłoszenie i dowody trafiają do modelu jako dane w tagach XML, wynik jest
 * odmaskowywany; każdy błąd modelu albo „cytat”, którego nie ma w CV ani w ogłoszeniu, oznacza powrót
 * do wersji offline.</p>
 */
@Service
public class CoverLetterService {

    private static final Logger log = LoggerFactory.getLogger(CoverLetterService.class);
    static final int OFFER_MAX_CHARS = 6000;
    static final int MIN_AI_LENGTH = 120;

    private final MatchService matching;
    private final OfferService offers;
    private final ProfileService profiles;
    private final ClaudeClient claude;
    private final PiiMasker masker;
    private final SkillDictionary dictionary;

    public CoverLetterService(MatchService matching, OfferService offers, ProfileService profiles, ClaudeClient claude,
                              PiiMasker masker, SkillDictionary dictionary) {
        this.matching = matching;
        this.offers = offers;
        this.profiles = profiles;
        this.claude = claude;
        this.masker = masker;
        this.dictionary = dictionary;
    }

    public CoverLetterResult generate(CoverLetterRequest req) {
        if (req == null || req.offerId() == null) {
            throw new BadRequestException("Wybierz ofertę");
        }
        Tone tone;
        Language language;
        try {
            tone = Tone.parse(req.tone());
            language = Language.parse(req.language());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        JobOffer offer = offers.get(req.offerId());
        CvVariant cv = profiles.cvOrActive(req.cvId()).orElse(null);
        CandidateProfile profile = profiles.profile();
        MatchResult match = matching.match(offer.getId(), cv == null ? null : cv.getId());
        String cvText = cv == null ? "" : Text.nullToEmpty(cv.getContent());
        List<Evidence> evidence = CoverLetterTemplate.selectEvidence(match, cvText, dictionary);

        String notice = cv == null ? "Brak CV w profilu — dodaj CV, aby list zawierał cytaty-dowody." : null;
        if (claude.available()) {
            try {
                String text = aiLetter(offer, profile, evidence, cvText, tone, language);
                return new CoverLetterResult(text, "ai", evidence, cv == null ? null : cv.getId(),
                        cv == null ? null : cv.getName(), tone.label(), language.code(), notice);
            } catch (LlmUnavailableException e) {
                log.info("List motywacyjny: AI niedostępne ({}), używam szablonu offline", e.getMessage());
                notice = join(notice, "Nie udało się użyć AI (" + e.getMessage() + ") — pokazano list z szablonu.");
            } catch (RuntimeException e) {
                log.warn("List motywacyjny: błąd ścieżki AI, używam szablonu offline", e);
                notice = join(notice, "Nie udało się użyć AI — pokazano list z szablonu.");
            }
        }
        if (language == Language.EN && !evidence.isEmpty()) {
            notice = join(notice, "Cytaty z CV pozostają w oryginalnym języku CV.");
        }
        String text = CoverLetterTemplate.compose(offer.getTitle(), offer.getCompany(), profile.getFullName(),
                evidence, tone, language);
        return new CoverLetterResult(text, "offline", evidence, cv == null ? null : cv.getId(),
                cv == null ? null : cv.getName(), tone.label(), language.code(), notice);
    }

    /** Ścieżka AI: maskowanie → dane w XML → model → odmaskowanie → kontrola cytatów. */
    String aiLetter(JobOffer offer, CandidateProfile profile, List<Evidence> evidence, String cvText, Tone tone,
                    Language language) throws LlmUnavailableException {
        String system = systemPrompt(tone, language);
        String offerText = "Stanowisko: " + offer.getTitle() + "\n"
                + (Text.isBlank(offer.getCompany()) ? "" : "Firma: " + offer.getCompany() + "\n") + "\n"
                + Text.nullToEmpty(Text.truncate(offer.getDescription(), OFFER_MAX_CHARS));
        String signature = Text.isBlank(profile.getFullName()) ? "[Imię i nazwisko]" : profile.getFullName().strip();
        String user = "Napisz list motywacyjny na stanowisko z ogłoszenia.\n\n"
                + ClaudeClient.asData("oferta", offerText) + "\n\n"
                + ClaudeClient.asData("dowody", evidenceXml(evidence)) + "\n\n"
                + ClaudeClient.asData("podpis", signature);
        PiiMasker.Masked masked = masker.mask(user, profile);
        String answer = claude.complete(system, masked.text());
        String text = masked.unmask(answer).strip();
        if (text.length() < MIN_AI_LENGTH) {
            throw new LlmUnavailableException("Model zwrócił zbyt krótki list");
        }
        List<String> unverified = CoverLetterTemplate.unverifiedQuotes(text, cvText, offer.getDescription(),
                offer.getTitle(), offer.getCompany());
        if (!unverified.isEmpty()) {
            throw new LlmUnavailableException("model zacytował fragment, którego nie ma w CV ani w ogłoszeniu");
        }
        return text;
    }

    static String systemPrompt(Tone tone, Language language) {
        return """
                Jesteś asystentem osoby szukającej pracy. Piszesz list motywacyjny.
                Zasady:
                - Opieraj się WYŁĄCZNIE na dowodach z CV w <dowody> oraz na ogłoszeniu w <oferta>. \
                Nie dodawaj umiejętności, lat doświadczenia, firm, liczb ani osiągnięć, których nie ma w dowodach.
                - Jeśli cytujesz CV, cytuj dosłownie, w cudzysłowie „…”, bez zmiany ani jednego znaku.
                - Długość: 150–250 słów, 3–4 akapity, zwykły tekst bez Markdown i bez komentarzy przed listem i po nim.
                - Używaj form neutralnych płciowo (np. „zgłaszam swoją kandydaturę”, „chętnie opowiem”).
                - Ton: %s.
                - Język listu: %s (to polecenie ma pierwszeństwo przed regułą o języku danych).
                - Podpisz list dokładnie treścią z <podpis>.
                """.formatted(tone == Tone.FORMAL ? "formalny, uprzejmy" : "swobodny, ale profesjonalny",
                language == Language.PL ? "polski" : "angielski")
                + ClaudeClient.DATA_RULES;
    }

    /** Dowody jako XML (treść escapowana, więc nie może zamknąć tagu ani dodać nowego). */
    static String evidenceXml(List<Evidence> evidence) {
        if (evidence.isEmpty()) {
            return "(brak cytatów z CV pasujących do wymagań — napisz list ogólny, bez wymyślania kompetencji)";
        }
        StringBuilder sb = new StringBuilder();
        for (Evidence e : evidence) {
            sb.append("<dowod obowiazkowe=\"").append(e.mustHave() ? "tak" : "nie").append("\">\n")
                    .append("  <wymaganie>").append(xml(e.requirement())).append("</wymaganie>\n")
                    .append("  <cytat>").append(xml(e.quote())).append("</cytat>\n")
                    .append("</dowod>\n");
        }
        return sb.toString().strip();
    }

    static String xml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String join(String a, String b) {
        return a == null ? b : a + " " + b;
    }
}
