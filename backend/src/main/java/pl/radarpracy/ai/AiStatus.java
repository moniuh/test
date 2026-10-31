package pl.radarpracy.ai;

/** Stan integracji AI dla interfejsu. */
public record AiStatus(boolean available, String model, String reason) {
}
