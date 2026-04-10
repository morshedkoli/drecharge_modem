package com.dRecharge.modem.subscription;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubscriptionAccessPolicyTest {
    @Test
    public void canProcessRequests_allowsCurrentNonExpiredSubscriptions() throws Exception {
        SubscriptionStatusEvaluator.Evaluation evaluation = SubscriptionStatusEvaluator.evaluate(
                false,
                "2026-04-10",
                day("2026-04-03"));

        assertTrue(SubscriptionAccessPolicy.canProcessRequests(
                true,
                evaluation,
                "active",
                ""));
    }

    @Test
    public void canProcessRequests_blocksExpiredSubscriptions() throws Exception {
        SubscriptionStatusEvaluator.Evaluation evaluation = SubscriptionStatusEvaluator.evaluate(
                false,
                "2026-04-01",
                day("2026-04-03"));

        assertFalse(SubscriptionAccessPolicy.canProcessRequests(
                true,
                evaluation,
                "active",
                ""));
    }

    @Test
    public void isExpired_blocksServerExpiredStatusWithoutDate() {
        SubscriptionStatusEvaluator.Evaluation evaluation = SubscriptionStatusEvaluator.evaluate(false, "");

        assertTrue(SubscriptionAccessPolicy.isExpired(
                evaluation,
                "expired",
                "subscription expired"));
    }

    private Date day(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value);
    }
}
