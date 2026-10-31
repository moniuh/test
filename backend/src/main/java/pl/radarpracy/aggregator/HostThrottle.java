package pl.radarpracy.aggregator;

import java.util.HashMap;
import java.util.Map;

/**
 * Limit zapytań na domenę: kolejne zapytanie do tego samego hosta czeka, aż minie
 * {@code delayMs} od poprzedniego (rezerwacje terminów — bezpieczne przy wielu wątkach).
 */
final class HostThrottle {

    private final Map<String, Long> nextAllowedNanos = new HashMap<>();

    /** Czeka na swoją kolej dla hosta; zwraca czas oczekiwania w ms. */
    long acquire(String host, long delayMs) {
        if (delayMs <= 0) {
            return 0;
        }
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            long allowedAt = nextAllowedNanos.getOrDefault(host, now);
            long start = Math.max(now, allowedAt);
            nextAllowedNanos.put(host, start + delayMs * 1_000_000L);
            waitNanos = start - now;
        }
        if (waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SourceException("Pobieranie przerwane");
            }
        }
        return waitNanos / 1_000_000L;
    }
}
