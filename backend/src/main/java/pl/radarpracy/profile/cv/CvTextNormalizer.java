package pl.radarpracy.profile.cv;

import java.util.regex.Pattern;

/**
 * Normalizacja białych znaków w tekście wyodrębnionym z PDF/DOCX (pomysł #1).
 *
 * <p>Ujednolica końce linii, zamienia twarde spacje i tabulatory na zwykłe spacje, usuwa znaki
 * niewidoczne (miękki dywiz, zero-width), zamienia „prywatne” punktory czcionek Symbol/Wingdings
 * (np. U+F0B7 z Worda) na „•”, scala wielokrotne spacje i ogranicza puste linie do jednej.</p>
 */
public final class CvTextNormalizer {

    private static final Pattern MULTI_SPACE = Pattern.compile(" {2,}");
    private static final Pattern MULTI_BLANK = Pattern.compile("\n{3,}");

    private CvTextNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                sb.append('\n');
            } else if (c == '\t' || c == '\f' || c == '\u000B' || c == ' ' || c == ' ' || c == ' '
                    || Character.getType(c) == Character.SPACE_SEPARATOR) {
                sb.append(' ');
            } else if (c == '­' || c == '​' || c == '‌' || c == '‍' || c == '⁠'
                    || c == '﻿') {
                // znaki niewidoczne — pomijamy
                continue;
            } else if (c >= '' && c <= '') {
                // obszar prywatny (punktory z czcionek Symbol/Wingdings)
                sb.append('•');
            } else if (Character.isISOControl(c)) {
                continue;
            } else {
                sb.append(c);
            }
        }
        StringBuilder out = new StringBuilder(sb.length());
        for (String line : sb.toString().split("\n", -1)) {
            out.append(MULTI_SPACE.matcher(line).replaceAll(" ").strip()).append('\n');
        }
        return MULTI_BLANK.matcher(out.toString()).replaceAll("\n\n").strip();
    }

    /** Liczba słów (ciągów liter/cyfr). */
    public static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        var m = CvStructure.WORD.matcher(text);
        while (m.find()) {
            count++;
        }
        return count;
    }
}
