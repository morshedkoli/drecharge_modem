package com.dRecharge.modem.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UssdDialTemplateResolver {
    private UssdDialTemplateResolver() {
    }

    public static String applyTemplate(String template, String phone, String amount, String pin) {
        if (template == null) {
            return "";
        }

        return template
                .replace("{PHONE}", safe(phone))
                .replace("{AMOUNT}", safe(amount))
                .replace("{PIN}", safe(pin));
    }

    public static String resolveCustomDialCode(ServiceConfig config,
                                               String type,
                                               String phone,
                                               String amount,
                                               String pin) {
        if (config == null || !config.customUssdEnabled) {
            return "";
        }

        String template = "0".equals(type) && hasValue(config.dialCode0)
                ? config.dialCode0
                : config.dialCode1;
        if (!hasValue(template)) {
            return "";
        }

        // Only treat the template as a full custom flow if it actually contains variable
        // content — i.e. at least one placeholder ({PHONE}/{AMOUNT}/{PIN}) or a multi-step
        // separator (-). A plain code like "*247#" with none of these means the user only
        // wants to override the initial dial code for services that use the initialCode
        // parameter (bKash, Nagad, Roket). Those services have their own step logic and
        // must not be short-circuited by the custom template path.
        String trimmed = template.trim();
        boolean hasVariableContent = trimmed.contains("{PHONE}")
                || trimmed.contains("{AMOUNT}")
                || trimmed.contains("{PIN}")
                || trimmed.contains("-");
        if (!hasVariableContent) {
            return "";
        }

        return applyTemplate(trimmed, phone, amount, pin);
    }

    /**
     * Resolves the custom USSD template and splits it into ordered execution steps using
     * "-" as the delimiter. The first element is the USSD code to dial (e.g. "*247#") and
     * each subsequent element is a value to send into the USSD dialog (e.g. "1", phone,
     * amount, PIN). Returns an empty list when custom USSD is disabled or unconfigured.
     *
     * <p>Example template: {@code *247#-1-{PHONE}-{AMOUNT}-{PIN}}
     * <br>Resolved steps:   {@code ["*247#", "1", "0171xxxxxxx", "500", "1234"]}
     */
    public static List<String> resolveSteps(ServiceConfig config,
                                            String type,
                                            String phone,
                                            String amount,
                                            String pin) {
        String resolved = resolveCustomDialCode(config, type, phone, amount, pin);
        if (resolved.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = resolved.split("-");
        List<String> steps = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                steps.add(trimmed);
            }
        }
        return steps.isEmpty() ? Collections.emptyList() : steps;
    }

    private static boolean hasValue(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
