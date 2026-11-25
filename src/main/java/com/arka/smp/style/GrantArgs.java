package com.arka.smp.style;

import java.util.UUID;

/** Reads the arguments of /style give and /style remove. Plain Java, so it can be tested without a server. */
final class GrantArgs {
    private GrantArgs() {
    }

    /** A UUID with or without dashes (Tebex can send either), or null when the text is not one. */
    static UUID uuid(String s) {
        if (s == null) return null;
        try {
            if (s.length() == 36) return UUID.fromString(s);
            if (s.length() == 32 && s.matches("[0-9a-fA-F]{32}")) {
                return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-"
                        + s.substring(16, 20) + "-" + s.substring(20));
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    /** Whole days from 1 to {@code max}, or -1 when the text is not a number in that range. */
    static int days(String s, int max) {
        try {
            int d = Integer.parseInt(s.trim());
            return d >= 1 && d <= max ? d : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
