package com.example.privacy_lab.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Єдина точка санітизації: маскує PII і повністю редагує секрети. */
public final class PiiSanitizer {

    public static final String REDACTED = "[REDACTED]";

    private static final Pattern BEARER = Pattern.compile("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]{8,}");

    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");

    /** key=value, key: value, "key":"value" для секретних ключів */
    private static final Pattern SECRET_KV = Pattern.compile(
            "(?i)(password|passwd|pwd|token|api[_-]?key|secret|authorization)"
                    + "([\"']?\\s*[:=]\\s*[\"']?)"
                    + "([^\"'\\s,;&}\\)\\]]+)");

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /**
     * +380... (з роздільниками) або 10-15 цифр підряд. Дати й час не зачіпаються.
     */
    private static final Pattern PHONE = Pattern.compile(
            "\\+\\d(?:[ ().-]?\\d){8,14}|(?<![\\w.-])\\d{10,15}(?![\\w-])");

    private PiiSanitizer() {
    }

    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String s = BEARER.matcher(input).replaceAll("$1 " + REDACTED);
        s = JWT.matcher(s).replaceAll(REDACTED);
        s = SECRET_KV.matcher(s).replaceAll("$1$2" + REDACTED);
        s = EMAIL.matcher(s).replaceAll(m -> Matcher.quoteReplacement(maskEmail(m.group())));
        s = PHONE.matcher(s).replaceAll(m -> Matcher.quoteReplacement(maskPhone(m.group())));
        return s;
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }

    static String maskPhone(String phone) {
        int digits = (int) phone.chars().filter(Character::isDigit).count();
        StringBuilder sb = new StringBuilder();
        int seen = 0;
        for (char c : phone.toCharArray()) {
            if (Character.isDigit(c)) {
                seen++;
                sb.append(seen > digits - 2 ? c : '*');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}