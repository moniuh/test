package pl.radarpracy.application.messages;

import pl.radarpracy.application.messages.MessageTemplate.InterviewContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static pl.radarpracy.application.ApplicationStatus.APPLIED;
import static pl.radarpracy.application.ApplicationStatus.INTERVIEW;
import static pl.radarpracy.application.ApplicationStatus.NO_RESPONSE;
import static pl.radarpracy.application.ApplicationStatus.OFFER;
import static pl.radarpracy.application.ApplicationStatus.REJECTED;
import static pl.radarpracy.application.ApplicationStatus.SCREENING;

/**
 * Katalog szablonów wiadomości (pomysł #43) i ich wypełnianie — czysta logika.
 * Teksty są neutralne płciowo (bez form typu „wysłałam/wysłałem”).
 */
public final class MessageTemplates {

    /** Znaczniki i ich opisy (do pokazania w interfejsie i oznaczenia braków). */
    public static final Map<String, String> PLACEHOLDERS = new LinkedHashMap<>();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");

    static {
        PLACEHOLDERS.put("firma", "nazwa firmy");
        PLACEHOLDERS.put("stanowisko", "stanowisko");
        PLACEHOLDERS.put("kontakt", "osoba kontaktowa");
        PLACEHOLDERS.put("data_wyslania", "data wysłania aplikacji");
        PLACEHOLDERS.put("data_rozmowy", "termin rozmowy");
        PLACEHOLDERS.put("imie_nazwisko", "imię i nazwisko z profilu");
    }

    private static final String SIGNATURE = "\n\nZ pozdrowieniami\n{imie_nazwisko}";

    public static final List<MessageTemplate> ALL = List.of(
            new MessageTemplate("follow-up", "Follow-up po aplikacji",
                    "Uprzejme pytanie o status, gdy firma nie odpowiada.",
                    "Aplikacja na stanowisko {stanowisko} — pytanie o dalsze kroki",
                    "Dzień dobry,\n\n"
                            + "dnia {data_wyslania} przesłana została moja aplikacja na stanowisko {stanowisko} "
                            + "w firmie {firma}. Uprzejmie pytam, czy proces rekrutacji jest w toku i czego mogę "
                            + "się spodziewać w kolejnych krokach.\n\n"
                            + "Stanowisko nadal bardzo mnie interesuje — w razie potrzeby chętnie prześlę dodatkowe "
                            + "informacje lub odpowiem na pytania." + SIGNATURE,
                    InterviewContext.NONE, List.of(APPLIED, SCREENING, NO_RESPONSE)),
            new MessageTemplate("thank-you", "Podziękowanie po rozmowie",
                    "Krótkie podziękowanie wysłane w ciągu doby po rozmowie.",
                    "Dziękuję za rozmowę — {stanowisko}",
                    "Dzień dobry,\n\n"
                            + "dziękuję za rozmowę w sprawie stanowiska {stanowisko} ({data_rozmowy}) "
                            + "i za poświęcony czas.\n\n"
                            + "Rozmowa utwierdziła mnie w przekonaniu, że to stanowisko i zespół firmy {firma} "
                            + "dobrze pasują do moich doświadczeń i planów zawodowych. Jeśli potrzebują Państwo "
                            + "dodatkowych informacji, chętnie je przekażę." + SIGNATURE,
                    InterviewContext.PAST, List.of(SCREENING, INTERVIEW)),
            new MessageTemplate("feedback-request", "Prośba o informację zwrotną po odmowie",
                    "Pytanie o powody decyzji — pomaga lepiej przygotować się do kolejnych rekrutacji.",
                    "Prośba o informację zwrotną — {stanowisko}",
                    "Dzień dobry,\n\n"
                            + "dziękuję za informację o decyzji w sprawie stanowiska {stanowisko} w firmie {firma} "
                            + "oraz za czas poświęcony mojej kandydaturze.\n\n"
                            + "Czy mogę prosić o krótką informację zwrotną: co przesądziło o decyzji i co warto "
                            + "rozwinąć, aby w przyszłości lepiej odpowiadać na Państwa potrzeby? Każda wskazówka "
                            + "będzie dla mnie cenna.\n\n"
                            + "Mam nadzieję, że nasze drogi jeszcze się skrzyżują." + SIGNATURE,
                    InterviewContext.NONE, List.of(REJECTED)),
            new MessageTemplate("decline-offer", "Odmowa przyjęcia oferty",
                    "Uprzejma odmowa, która nie zamyka drogi na przyszłość.",
                    "Odpowiedź na ofertę pracy — {stanowisko}",
                    "Dzień dobry,\n\n"
                            + "bardzo dziękuję za ofertę pracy na stanowisku {stanowisko} oraz za cały proces "
                            + "rekrutacji w firmie {firma}.\n\n"
                            + "Po namyśle z przykrością informuję, że nie przyjmę tej oferty. Decyzja nie była "
                            + "łatwa — wysoko oceniam rozmowy z Państwa zespołem i chętnie pozostanę w kontakcie."
                            + "\n\nZ wyrazami szacunku\n{imie_nazwisko}",
                    InterviewContext.NONE, List.of(OFFER)),
            new MessageTemplate("accept-offer", "Przyjęcie oferty",
                    "Potwierdzenie przyjęcia oferty z pytaniem o kolejne kroki.",
                    "Przyjęcie oferty pracy — {stanowisko}",
                    "Dzień dobry,\n\n"
                            + "dziękuję za ofertę pracy na stanowisku {stanowisko} w firmie {firma}. "
                            + "Z przyjemnością ją przyjmuję.\n\n"
                            + "Proszę o informację o kolejnych krokach: umowie, dacie rozpoczęcia pracy "
                            + "i dokumentach, które należy przygotować.\n\n"
                            + "Do zobaczenia wkrótce!\n{imie_nazwisko}",
                    InterviewContext.NONE, List.of(OFFER)),
            new MessageTemplate("reschedule", "Prośba o przełożenie rozmowy",
                    "Prośba o nowy termin, gdy zaplanowana rozmowa koliduje z innymi obowiązkami.",
                    "Prośba o zmianę terminu rozmowy — {stanowisko}",
                    "Dzień dobry,\n\n"
                            + "niestety nie mogę uczestniczyć w rozmowie w sprawie stanowiska {stanowisko} "
                            + "w firmie {firma}, zaplanowanej na {data_rozmowy}. Przepraszam za kłopot.\n\n"
                            + "Czy byłaby możliwość przełożenia jej na inny termin? Mogę zaproponować: "
                            + "[wpisz 2–3 terminy]." + SIGNATURE,
                    InterviewContext.UPCOMING, List.of(SCREENING, INTERVIEW)));

    private MessageTemplates() {
    }

    public static Optional<MessageTemplate> find(String id) {
        return ALL.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    /** Wynik podstawienia: tekst i lista znaczników bez danych (zastąpionych np. „[firma]”). */
    public record Filled(String text, Set<String> missing) {
    }

    /** Podstawia wartości; brakujące zastępuje opisem w nawiasach kwadratowych i zgłasza w {@code missing}. */
    public static Filled fill(String template, Map<String, String> values) {
        Set<String> missing = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = values.get(key);
            String replacement;
            if (value == null || value.isBlank()) {
                missing.add(key);
                replacement = "[" + PLACEHOLDERS.getOrDefault(key, key) + "]";
            } else {
                replacement = value.strip();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return new Filled(sb.toString(), missing);
    }

    /** Znaczniki użyte w szablonie (temat + treść). */
    static List<String> placeholdersIn(MessageTemplate t) {
        List<String> out = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(t.subject() + "\n" + t.body());
        while (m.find()) {
            if (!out.contains(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }
}
