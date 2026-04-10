package com.dRecharge.modem.subscription;

import java.util.Locale;

public final class SubscriptionAccessPolicy {
    private SubscriptionAccessPolicy() {
    }

    public static boolean isExpired(SubscriptionStatusEvaluator.Evaluation evaluation,
                                    String status,
                                    String message) {
        if (evaluation != null && evaluation.isExpired()) {
            return true;
        }

        return hasExpiredFlag(status) || hasExpiredFlag(message);
    }

    public static boolean canProcessRequests(boolean hasCurrentCheck,
                                             SubscriptionStatusEvaluator.Evaluation evaluation,
                                             String status,
                                             String message) {
        return hasCurrentCheck && !isExpired(evaluation, status, message);
    }

    private static boolean hasExpiredFlag(String value) {
        String normalized = normalize(value);
        return normalized.contains("expired")
                || normalized.contains("subscription expired")
                || normalized.contains("expiry passed");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
