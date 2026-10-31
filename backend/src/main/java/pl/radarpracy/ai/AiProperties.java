package pl.radarpracy.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguracja AI ({@code radar.ai.*}). Klucz API pochodzi ze zmiennej środowiskowej
 * {@code ANTHROPIC_API_KEY} (odczytywanej przez SDK).
 *
 * @param offline   wymusza tryb offline nawet z kluczem
 * @param model     identyfikator modelu Claude
 * @param maxTokens limit długości odpowiedzi
 */
@ConfigurationProperties("radar.ai")
public record AiProperties(boolean offline, String model, int maxTokens) {

    public AiProperties {
        model = model == null || model.isBlank() ? "claude-opus-5-5" : model;
        maxTokens = maxTokens <= 0 ? 8000 : maxTokens;
    }
}
