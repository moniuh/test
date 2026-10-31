package pl.radarpracy.application.messages;

import pl.radarpracy.application.ApplicationStatus;

import java.util.List;

/**
 * Szablon wiadomości do rekrutera (pomysł #43). Znaczniki w nawiasach klamrowych, np. {@code {firma}},
 * są podstawiane danymi aplikacji i profilu.
 *
 * @param interviewContext której rozmowy dotyczy {@code {data_rozmowy}}: ostatniej odbytej (PAST),
 *                         najbliższej zaplanowanej (UPCOMING) albo żadnej (NONE)
 * @param suggestedFor     statusy, przy których szablon jest podpowiadany
 */
public record MessageTemplate(String id, String name, String description, String subject, String body,
                              InterviewContext interviewContext, List<ApplicationStatus> suggestedFor) {

    public enum InterviewContext {
        NONE, PAST, UPCOMING
    }
}
