package pl.radarpracy.tailoring;

/** Zapis odrzucony: strażnik zgłosił naruszenia, a kandydat ich jawnie nie potwierdził. */
public class GuardRejectedException extends RuntimeException {

    private final GuardReport report;

    public GuardRejectedException(GuardReport report) {
        super("Strażnik prawdomówności zgłosił " + report.violations().size()
                + " naruszeń — popraw tekst albo jawnie potwierdź zapis (acceptViolations).");
        this.report = report;
    }

    public GuardReport report() {
        return report;
    }
}
