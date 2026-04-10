package com.dRecharge.modem.server;

import java.net.URI;
import java.util.Locale;

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

    public static String normalizeSubscriptionDomain(String value) {
        String normalized = sanitizeDomain(value);
        if (normalized.isEmpty()) {
            return "";
        }

        try {
            URI uri = URI.create(normalized);
            return normalizeHost(uri.getHost());
        } catch (Exception ignored) {
            return normalizeHost(normalized);
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }

        String normalizedHost = host.trim().toLowerCase(Locale.US);
        while (normalizedHost.startsWith("www.")) {
            normalizedHost = normalizedHost.substring(4);
        }
        return normalizedHost;
    }
}
