package com.dRecharge.modem.subscription;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubscriptionStatusEvaluatorTest {
    @Test
    public void evaluate_marksPreviousDayAsExpiredEvenWhenServerFlagIsFalse() throws Exception {
        SubscriptionStatusEvaluator.Evaluation evaluation = SubscriptionStatusEvaluator.evaluate(
                false,
                "2026-04-01",
                day("2026-04-02"));

        assertTrue(evaluation.isExpired());
        assertEquals(-1, evaluation.getDaysLeft());
    }

    @Test
    public void evaluate_marksSameDayAsExpiresToday() throws Exception {
        SubscriptionStatusEvaluator.Evaluation evaluation = SubscriptionStatusEvaluator.evaluate(
                false,
                "2026-04-02T23:59:59",
                day("2026-04-02"));

        assertFalse(evaluation.isExpired());
        assertTrue(evaluation.expiresToday());
        assertEquals(0, evaluation.getDaysLeft());
        assertEquals("2026-04-02", evaluation.getDisplayDate());
    }

    @Test
    public void evaluate_parsesMicrosecondsAndTimezone() throws Exception {
        SubscriptionStatusEvaluator.Evaluation evaluation = SubscriptionStatusEvaluator.evaluate(
                false,
                "2026-04-04T10:15:30.123456Z",
                day("2026-04-02"));

        assertFalse(evaluation.isExpired());
        assertEquals(2, evaluation.getDaysLeft());
        assertEquals("2026-04-04", evaluation.getDisplayDate());
    }

    private Date day(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value);
    }
}
