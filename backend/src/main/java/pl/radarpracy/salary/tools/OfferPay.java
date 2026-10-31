package pl.radarpracy.salary.tools;

import pl.radarpracy.common.Text;
import pl.radarpracy.offer.ContractType;
import pl.radarpracy.offer.JobOffer;
import pl.radarpracy.salary.NormalizedSalary;

import java.util.List;
import java.util.regex.Pattern;

/** Wspólne reguły „jak liczyć netto dla tej oferty” — używane przez porównywarkę, ostrzeżenie i negocjacje. */
final class OfferPay {

    /** „26 dni płatnej przerwy”, „płatny urlop”, „paid time off”, „paid vacation”, „PTO”. */
    private static final Pattern PAID_LEAVE = Pattern.compile(
            "platn\\p{L}*\\s+(?:\\p{L}+\\s+)?(?:przerw|urlop|wolne|dni)|"
                    + "(?:przerw|urlop|dni wolne)\\p{L}*\\s+(?:\\p{L}+\\s+)?platn|"
                    + "paid\\s+(?:time\\s+off|leave|vacation|holiday)|\\bpto\\b");

    private OfferPay() {
    }

    /**
     * Forma rozliczenia do szacunku netto: B2B, gdy kwoty oferty są „netto + VAT”; UoP, gdy oferta ją przewiduje
     * (lub nie podaje umowy); zlecenie, gdy to jedyna forma. Umowa o dzieło — jak zlecenie bez składek
     * (zaznaczone w uwagach porównywarki).
     */
    static ContractKind contractFor(JobOffer offer, NormalizedSalary salary, ContractKind b2bVariant) {
        ContractKind b2b = b2bVariant != null && b2bVariant.isB2b() ? b2bVariant : ContractKind.B2B_LINIOWY;
        List<ContractType> types = offer.getContractTypes() == null ? List.of() : offer.getContractTypes();
        if ("B2B_NET".equals(salary.basis())) {
            return b2b;
        }
        if (types.isEmpty() || types.contains(ContractType.UOP) || types.contains(ContractType.INTERNSHIP)) {
            return ContractKind.UOP;
        }
        if (types.contains(ContractType.UZ) || types.contains(ContractType.UOD)) {
            return ContractKind.UZ;
        }
        return types.contains(ContractType.B2B) ? b2b : ContractKind.UOP;
    }

    /** Czy jedyną formą jest umowa o dzieło (bez składek ZUS i zdrowotnej). */
    static boolean onlyWorkContract(JobOffer offer) {
        List<ContractType> types = offer.getContractTypes() == null ? List.of() : offer.getContractTypes();
        return types.contains(ContractType.UOD) && !types.contains(ContractType.UZ)
                && !types.contains(ContractType.UOP) && !types.contains(ContractType.B2B);
    }

    /** Środek widełek miesięcznie w PLN w oryginalnej formie (brutto / netto + VAT / netto). */
    static Integer monthlyMid(NormalizedSalary n) {
        if (!n.present()) {
            return null;
        }
        if (n.monthlyMin() != null && n.monthlyMax() != null) {
            return (n.monthlyMin() + n.monthlyMax()) / 2;
        }
        return n.monthlyMin() != null ? n.monthlyMin() : n.monthlyMax();
    }

    /** Czy oferta (benefity, opis, notatki) wspomina płatny urlop / płatną przerwę. */
    static boolean mentionsPaidLeave(JobOffer offer) {
        StringBuilder sb = new StringBuilder();
        if (offer.getBenefits() != null) {
            offer.getBenefits().forEach(b -> sb.append(b).append('\n'));
        }
        sb.append(Text.nullToEmpty(offer.getDescription())).append('\n').append(Text.nullToEmpty(offer.getNotes()));
        return PAID_LEAVE.matcher(Text.fold(sb.toString())).find();
    }

    /** Etykieta podstawy kwot w ofercie. */
    static String basisLabel(String basis) {
        return switch (basis == null ? "UNKNOWN" : basis) {
            case "UOP_GROSS" -> "brutto";
            case "B2B_NET" -> "netto + VAT (B2B)";
            case "NET" -> "netto (na rękę)";
            default -> "brutto (przyjęte — oferta nie podaje)";
        };
    }
}
