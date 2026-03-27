package com.dRecharge.modem.server;

public final class ServerConfig {
    private ServerConfig() {
    }

    public static String sanitizeDomain(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return "";
        }

        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    public static String buildBaseUrl(String value) {
        String normalized = sanitizeDomain(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Server domain is empty");
        }
        return normalized + "/";
    }
}
