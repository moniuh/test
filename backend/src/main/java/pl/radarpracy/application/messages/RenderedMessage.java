package pl.radarpracy.application.messages;

import java.util.List;

/**
 * Wypełniony szablon wiadomości (pomysł #43).
 *
 * @param to       adresat („Imię Nazwisko &lt;e-mail&gt;”) albo null, gdy brak kontaktu w aplikacji
 * @param text     całość do skopiowania: adresat, temat i treść
 * @param missing  znaczniki bez danych (w tekście zastąpione opisem w nawiasach kwadratowych)
 * @param eventId  id zdarzenia MESSAGE, gdy wiadomość zapisano w historii aplikacji
 */
public record RenderedMessage(String templateId, String templateName, String to, String subject, String body,
                              String text, List<String> missing, Long eventId) {
}
