package com.dRecharge.modem.subscription;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SubscriptionStatusEvaluator {
    private static final long DAY_IN_MILLIS = 24L * 60L * 60L * 1000L;
    private static final Pattern MICROSECONDS_PATTERN = Pattern.compile("\\.(\\d{3})\\d+(?=(Z|[+-]\\d{2}:?\\d{2})?$)");
    private static final String[] DATE_PATTERNS = new String[]{
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
    };

    private SubscriptionStatusEvaluator() {
    }

    public static Evaluation evaluate(boolean serverExpired, String expiresAt) {
        return evaluate(serverExpired, expiresAt, new Date());
    }

    static Evaluation evaluate(boolean serverExpired, String expiresAt, Date now) {
        Date parsedExpiry = parseExpiry(expiresAt);
        if (parsedExpiry == null) {
            return new Evaluation(serverExpired, false, false, -1, fallbackDisplayValue(expiresAt));
        }

        Date today = truncateToDay(now);
        Date expiryDay = truncateToDay(parsedExpiry);
        long daysBetween = (expiryDay.getTime() - today.getTime()) / DAY_IN_MILLIS;
        boolean locallyExpired = daysBetween < 0;
        boolean expired = serverExpired || locallyExpired;
        boolean expiresToday = !expired && daysBetween == 0;
        long daysLeft = expired ? -1 : Math.max(0, daysBetween);

        return new Evaluation(expired, true, expiresToday, daysLeft, formatDay(parsedExpiry));
    }

    private static Date parseExpiry(String rawValue) {
        String normalized = normalize(rawValue);
        if (normalized.isEmpty()) {
            return null;
        }

        for (String pattern : DATE_PATTERNS) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setLenient(false);
                Date parsed = parser.parse(normalized);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String normalize(String rawValue) {
        if (rawValue == null) {
            return "";
        }

        String normalized = rawValue.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        if (normalized.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:.*")) {
            normalized = normalized.replaceFirst(" ", "T");
        }

        Matcher matcher = MICROSECONDS_PATTERN.matcher(normalized);
        if (matcher.find()) {
            normalized = matcher.replaceFirst(".$1");
        }

        return normalized;
    }

    private static Date truncateToDay(Date value) {
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            formatter.setLenient(false);
            return formatter.parse(formatter.format(value));
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String formatDay(Date value) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(value);
    }

    private static String fallbackDisplayValue(String expiresAt) {
        return expiresAt == null ? "" : expiresAt.trim();
    }

    public static final class Evaluation {
        private final boolean expired;
        private final boolean hasKnownExpiry;
        private final boolean expiresToday;
        private final long daysLeft;
        private final String displayDate;

        private Evaluation(boolean expired, boolean hasKnownExpiry, boolean expiresToday, long daysLeft, String displayDate) {
            this.expired = expired;
            this.hasKnownExpiry = hasKnownExpiry;
            this.expiresToday = expiresToday;
            this.daysLeft = daysLeft;
            this.displayDate = displayDate;
        }

        public boolean isExpired() {
            return expired;
        }

        public boolean hasKnownExpiry() {
            return hasKnownExpiry;
        }

        public boolean expiresToday() {
            return expiresToday;
        }

        public long getDaysLeft() {
            return daysLeft;
        }

        public String getDisplayDate() {
            return displayDate;
        }
    }
}
