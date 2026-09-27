package com.quizsphere.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Text {

    public static final Pattern REGISTER_NUMBER = Pattern.compile("^[A-Z0-9][A-Z0-9/_-]{2,39}$");
    public static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Academic years run from I to IV. */
    public static final int MAX_ACADEMIC_YEAR = 4;

    private static final String[] ROMAN = {"", "I", "II", "III", "IV"};

    private Text() {
    }

    public static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static String upperOrNull(String s) {
        String t = trimToNull(s);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    /** Register numbers are case-insensitive and stored uppercase without whitespace. */
    public static String normalizeRegisterNumber(String s) {
        if (s == null) return null;
        String t = s.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    public static String roman(Integer year) {
        if (year == null || year < 1 || year >= ROMAN.length) return year == null ? "" : String.valueOf(year);
        return ROMAN[year];
    }

    /** Parses "3", "III", "III Year", "3rd" into 1..4; returns null when invalid. */
    public static Integer parseAcademicYear(String s) {
        String t = trimToNull(s);
        if (t == null) return null;
        t = t.toUpperCase(Locale.ROOT).replace("YEAR", "").replaceAll("(ST|ND|RD|TH)$", "").trim();
        try {
            int v = (int) Double.parseDouble(t);
            return v >= 1 && v <= MAX_ACADEMIC_YEAR ? v : null;
        } catch (NumberFormatException ignored) {
            // try roman numerals
        }
        for (int i = 1; i < ROMAN.length; i++) {
            if (ROMAN[i].equals(t)) return i;
        }
        return null;
    }

    /** Parses an integer cell value such as "5" or "5.0"; returns null when invalid. */
    public static Integer parseInt(String s) {
        String t = trimToNull(s);
        if (t == null) return null;
        try {
            double d = Double.parseDouble(t);
            if (d != Math.rint(d)) return null;
            return (int) d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max);
    }
}
