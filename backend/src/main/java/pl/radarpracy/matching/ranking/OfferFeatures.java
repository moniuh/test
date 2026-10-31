package pl.radarpracy.matching.ranking;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.offer.RejectReason;
import pl.radarpracy.skills.SkillDictionary;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Cechy oferty, z których uczymy się preferencji kandydata (pomysł #35). */
final class OfferFeatures {

    /** Rodzaj cechy: limit wkładu w punktach i powody odrzucenia, które jej dotyczą. */
    enum Type {
        SKILL(RankingWeights.LEARNING_CAP_SKILLS, EnumSet.of(RejectReason.TECH_STACK)),
        WORK_MODE(RankingWeights.LEARNING_CAP_OTHER, EnumSet.of(RejectReason.WORK_MODE, RejectReason.LOCATION)),
        CONTRACT(RankingWeights.LEARNING_CAP_OTHER, EnumSet.of(RejectReason.CONTRACT)),
        SENIORITY(RankingWeights.LEARNING_CAP_OTHER, EnumSet.of(RejectReason.SENIORITY)),
        COMPANY(RankingWeights.LEARNING_CAP_COMPANY, EnumSet.of(RejectReason.COMPANY, RejectReason.RED_FLAGS));

        private final double cap;
        private final Set<RejectReason> reasons;

        Type(double cap, Set<RejectReason> reasons) {
            this.cap = cap;
            this.reasons = reasons;
        }

        double cap() {
            return cap;
        }

        /**
         * Czy odrzucenie z danym powodem jest sygnałem negatywnym dla cech tego rodzaju. Odrzucenie
         * „za niskie wynagrodzenie” nie mówi nic o technologiach, więc ich nie obniża; brak powodu
         * albo „inny” — dotyczy wszystkich cech.
         */
        boolean affectedBy(RejectReason reason) {
            return reason == null || reason == RejectReason.OTHER || reasons.contains(reason);
        }
    }

    /** Cecha: rodzaj + klucz (np. SKILL/„Kafka”, COMPANY/„finpay systems”). */
    record Feature(Type type, String key) {
    }

    private static final Pattern LEGAL_FORM = Pattern.compile(
            "\\b(sp\\.?\\s*z\\s*o\\.?\\s*o\\.?|sp\\.?\\s*k\\.?|sp\\.?\\s*j\\.?|s\\.?\\s*a\\.?|spolka akcyjna"
                    + "|spolka z ograniczona odpowiedzialnoscia|gmbh|ltd\\.?|inc\\.?|llc|ab|s\\.?r\\.?o\\.?)(?=\\s|$|[,.])");

    private final SkillDictionary dictionary;

    OfferFeatures(SkillDictionary dictionary) {
        this.dictionary = dictionary;
    }

    /** Cechy oferty z etykietą do wyświetlenia (kolejność stała: umiejętności, tryb, umowa, poziom, firma). */
    Map<Feature, String> of(JobOffer o) {
        Map<Feature, String> out = new LinkedHashMap<>();
        for (String skill : dictionary.skillsIn(OfferTexts.content(o))) {
            out.put(new Feature(Type.SKILL, skill), skill);
        }
        if (o.getWorkMode() != null) {
            out.put(new Feature(Type.WORK_MODE, o.getWorkMode().name()), RankingLabels.workMode(o.getWorkMode()));
        }
        for (ContractType c : OfferTexts.nonNull(o.getContractTypes())) {
            out.put(new Feature(Type.CONTRACT, c.name()), RankingLabels.contract(c));
        }
        if (o.getSeniority() != null) {
            out.put(new Feature(Type.SENIORITY, o.getSeniority().name()), RankingLabels.seniority(o.getSeniority()));
        }
        String company = companyKey(o.getCompany());
        if (!company.isEmpty()) {
            out.put(new Feature(Type.COMPANY, company), o.getCompany().strip());
        }
        return out;
    }

    /** Klucz firmy: złożona nazwa bez formy prawnej („LogiTech Solutions sp. z o.o.” → „logitech solutions”). */
    static String companyKey(String company) {
        if (Text.isBlank(company)) {
            return "";
        }
        String f = LEGAL_FORM.matcher(Text.fold(company)).replaceAll(" ");
        return f.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
