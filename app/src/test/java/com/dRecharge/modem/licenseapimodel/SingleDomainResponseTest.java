package com.dRecharge.modem.licenseapimodel;

import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SingleDomainResponseTest {
    private final Gson gson = new Gson();

    @Test
    public void resolveData_readsSnakeCaseTopLevelFields() {
        SingleDomainResponse response = gson.fromJson(
                "{"
                        + "\"is_tracked\":true,"
                        + "\"is_available\":true,"
                        + "\"status\":\"active\","
                        + "\"is_subscribed\":true,"
                        + "\"is_expired\":false,"
                        + "\"expires_at\":\"2026-04-10\","
                        + "\"days_until_expiry\":7,"
                        + "\"logoUrl\":\"/subscription/domain_logo/brand-wordmark.png\","
                        + "\"display_name\":\"IkramTel Modem Service\""
                        + "}",
                SingleDomainResponse.class);

        DomainSubscriptionStatus resolved = response.resolveData();

        assertNotNull(resolved);
        assertTrue(resolved.isTracked());
        assertTrue(resolved.isAvailable());
        assertTrue(resolved.isSubscribed());
        assertEquals("active", resolved.getStatus());
        assertEquals("2026-04-10", resolved.getExpiresAt());
        assertEquals(Integer.valueOf(7), resolved.getDaysUntilExpiry());
        assertEquals("/subscription/domain_logo/brand-wordmark.png", resolved.getDomainLogo());
        assertEquals("IkramTel Modem Service", resolved.getDisplayName());
    }

    @Test
    public void resolveData_mergesTopLevelBrandingIntoNestedData() {
        SingleDomainResponse response = gson.fromJson(
                "{"
                        + "\"logoUrl\":\"/subscription/domain_logo/brand-wordmark.png\","
                        + "\"display_name\":\"IkramTel Modem Service\","
                        + "\"data\":{\"status\":\"active\"}"
                        + "}",
                SingleDomainResponse.class);

        DomainSubscriptionStatus resolved = response.resolveData();

        assertNotNull(resolved);
        assertEquals("/subscription/domain_logo/brand-wordmark.png", resolved.getDomainLogo());
        assertEquals("IkramTel Modem Service", resolved.getDisplayName());
    }
}
