package pl.radarpracy.aggregator;

import pl.radarpracy.common.BadRequestException;

/**
 * Błąd pobrania lub odczytu źródła (komunikat po polsku, do pokazania w interfejsie). Przy podglądzie
 * źródła mapowany na HTTP 400; w przebiegu harmonogramu zapisywany w historii i stanie źródła.
 */
public class SourceException extends BadRequestException {

    private final boolean blockedByRobots;

    public SourceException(String message) {
        this(message, false);
    }

    public SourceException(String message, boolean blockedByRobots) {
        super(message);
        this.blockedByRobots = blockedByRobots;
    }

    public boolean blockedByRobots() {
        return blockedByRobots;
    }
}
