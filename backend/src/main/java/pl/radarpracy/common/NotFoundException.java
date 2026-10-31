package pl.radarpracy.common;

/** Zasób nie istnieje — mapowany na HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String what, Object id) {
        return new NotFoundException(what + " o id " + id + " nie istnieje");
    }
}
