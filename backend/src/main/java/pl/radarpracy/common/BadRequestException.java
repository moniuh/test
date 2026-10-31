package pl.radarpracy.common;

/** Błędne dane wejściowe — mapowany na HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
