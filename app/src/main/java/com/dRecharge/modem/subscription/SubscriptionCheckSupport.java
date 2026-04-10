package com.dRecharge.modem.subscription;

import com.dRecharge.modem.helper.Session;
import com.dRecharge.modem.licenseapimodel.DomainSubscriptionStatus;
import com.dRecharge.modem.licenseapimodel.SingleDomainResponse;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class SubscriptionCheckSupport {
    private static final int DAILY_CHECK_HOUR = 1;

    private SubscriptionCheckSupport() {
    }

    public static String getCurrentCheckSlotKey() {
        Calendar now = Calendar.getInstance();
        Calendar slot = (Calendar) now.clone();
        if (now.get(Calendar.HOUR_OF_DAY) < DAILY_CHECK_HOUR) {
            slot.add(Calendar.DAY_OF_YEAR, -1);
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(slot.getTime());
    }

    public static long getNextScheduledCheckTimeMillis() {
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, DAILY_CHECK_HOUR);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis();
    }

    public static boolean isSubscriptionCheckCurrent(Session session, String host) {
        if (session == null || host == null || host.trim().isEmpty()) {
            return false;
        }

        return host.equals(session.getData(Session.SUBSCRIPTION_LAST_DOMAIN))
                && getCurrentCheckSlotKey().equals(session.getData(Session.SUBSCRIPTION_LAST_CHECK_DATE))
                && hasStoredSubscriptionState(session);
    }

    public static boolean hasStoredSubscriptionState(Session session) {
        if (session == null) {
            return false;
        }

        return !session.getData(Session.SUBSCRIPTION_STATUS).isEmpty()
                || !session.getData(Session.SUBSCRIPTION_MESSAGE).isEmpty()
                || session.getBooleanData(Session.SUBSCRIPTION_TRACKED)
                || session.getBooleanData(Session.SUBSCRIPTION_AVAILABLE)
                || session.getBooleanData(Session.SUBSCRIPTION_SUBSCRIBED)
                || session.getBooleanData(Session.SUBSCRIPTION_EXPIRED)
                || !session.getData(Session.SUBSCRIPTION_EXPIRES_AT).isEmpty();
    }

    public static boolean storeSubscriptionState(Session session, String host, SingleDomainResponse response) {
        if (session == null || host == null || host.trim().isEmpty()) {
            return false;
        }

        DomainSubscriptionStatus resolved = response == null ? null : response.resolveData();
        if (resolved == null) {
            return false;
        }

        session.setData(Session.SUBSCRIPTION_LAST_DOMAIN, host);
        session.setData(Session.SUBSCRIPTION_LAST_CHECK_DATE, getCurrentCheckSlotKey());
        session.setData(Session.SUBSCRIPTION_CHECKED_AT, defaultIfEmpty(response.getCheckedAt(), ""));
        session.setData(Session.SUBSCRIPTION_STATUS, defaultIfEmpty(resolved.getStatus(), ""));
        session.setBooleanData(Session.SUBSCRIPTION_TRACKED, resolved.isTracked());
        session.setBooleanData(Session.SUBSCRIPTION_AVAILABLE, resolved.isAvailable());
        session.setBooleanData(Session.SUBSCRIPTION_SUBSCRIBED, resolved.isSubscribed());
        session.setBooleanData(Session.SUBSCRIPTION_EXPIRED, resolved.isExpired());
        session.setData(Session.SUBSCRIPTION_EXPIRES_AT, defaultIfEmpty(resolved.getExpiresAt(), ""));
        session.setData(Session.SUBSCRIPTION_DAYS_UNTIL_EXPIRY, resolved.getDaysUntilExpiry() == null
                ? ""
                : String.valueOf(resolved.getDaysUntilExpiry()));
        session.setData(Session.SUBSCRIPTION_MESSAGE, defaultIfEmpty(resolved.getMessage(), ""));
        session.setData(Session.SUBSCRIPTION_DOMAIN_LOGO, defaultIfEmpty(resolved.getDomainLogo(), ""));
        session.setData(Session.SUBSCRIPTION_DISPLAY_NAME, defaultIfEmpty(resolved.getDisplayName(), ""));
        return true;
    }

    private static String defaultIfEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
