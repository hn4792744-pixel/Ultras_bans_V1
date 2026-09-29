package com.ultras.bans.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses compact duration tokens like "10m", "6h", "7d", "1w", "30d" into
 * milliseconds. Also used to validate/clamp durations chosen via the
 * Temporary Punishment GUI (spec section 7), which uses the exact same
 * increments: 1m 5m 10m 15m 30m 1h 6h 16h 1d 7d 1w 30d.
 */
public final class DurationParser {

    private static final Pattern TOKEN = Pattern.compile("^(\\d+)([smhdw])$", Pattern.CASE_INSENSITIVE);

    public static final long MINUTE = 60_000L;
    public static final long HOUR = 60 * MINUTE;
    public static final long DAY = 24 * HOUR;
    public static final long WEEK = 7 * DAY;

    private DurationParser() {}

    /**
     * @return millis, or -1 if the input is unparseable, or null-equivalent
     *         handled by caller for "permanent" keywords.
     */
    public static long parseMillis(String input) {
        if (input == null || input.isBlank()) return -1;
        String trimmed = input.trim();
        if (isPermanentKeyword(trimmed)) return -1;

        Matcher m = TOKEN.matcher(trimmed);
        if (!m.matches()) return -1;

        long amount;
        try {
            amount = Long.parseLong(m.group(1));
        } catch (NumberFormatException ex) {
            return -1;
        }
        if (amount <= 0) return -1;

        char unit = Character.toLowerCase(m.group(2).charAt(0));
        return switch (unit) {
            case 's' -> amount * 1000L;
            case 'm' -> amount * MINUTE;
            case 'h' -> amount * HOUR;
            case 'd' -> amount * DAY;
            case 'w' -> amount * WEEK;
            default -> -1;
        };
    }

    public static boolean isPermanentKeyword(String input) {
        String lower = input.toLowerCase();
        return lower.equals("permanent") || lower.equals("perm") || lower.equals("-1") || lower.equals("forever");
    }

    public static boolean isValid(String input) {
        return isPermanentKeyword(input) || parseMillis(input) > 0;
    }

    /** Clamps a duration to the configured maximum (spec section 7: 30 days). */
    public static long clamp(long millis, int maxDays) {
        long max = maxDays * DAY;
        return Math.min(millis, max);
    }

    /** The fixed increment ladder used by the Temporary Punishment GUI's +/- buttons. */
    public static final long[] GUI_STEPS_MILLIS = {
            MINUTE, 5 * MINUTE, 10 * MINUTE, 15 * MINUTE, 30 * MINUTE,
            HOUR, 6 * HOUR, 16 * HOUR, DAY, 7 * DAY, WEEK, 30 * DAY
    };

    public static final String[] GUI_STEP_LABELS = {
            "+1m", "+5m", "+10m", "+15m", "+30m", "+1h", "+6h", "+16h", "+1d", "+7d", "+1w", "+30d"
    };
}
