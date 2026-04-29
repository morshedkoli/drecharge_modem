package com.dRecharge.modem.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves built-in carrier USSD codes for background processing.
 * <p>
 * This mirrors the hardcoded carrier logic that lives in MainActivity's
 * processRequest() switch-case so that KeepAliveService can execute the
 * same USSD sequences without requiring the activity to be alive.
 * <p>
 * For simple carrier load transfers (Grameen, Robi, Airtel, Banglalink,
 * Taletalk) the USSD is a single dial code.  For mobile banking services
 * (bKash, Nagad, Roket) the USSD is a multi-step interactive flow returned
 * as an ordered list of steps.
 */
public final class BuiltInUssdResolver {

    private BuiltInUssdResolver() {
    }

    /**
     * Attempts to resolve the USSD steps for a built-in service.
     *
     * @param service    Service name (e.g. "Grameen", "bKash-Agent-SIM")
     * @param pcode      Product code from server (e.g. "GP", "SK", "BK", "BKS")
     * @param phone      Normalized phone number
     * @param amount     Normalized amount
     * @param pin        SIM / service PIN
     * @param type       Request type ("0" or "1")
     * @param isPowerLoad Whether this is a power/package load
     * @param packageName Package code (for power loads)
     * @return Ordered list of USSD steps (first = dial code, rest = dialog inputs).
     *         Empty if the service/pcode combination is unknown.
     */
    public static List<String> resolve(String service, String pcode, String phone,
                                        String amount, String pin, String type,
                                        boolean isPowerLoad, String packageName) {
        if (service == null || service.isEmpty()) return Collections.emptyList();

        switch (service) {
            case "Grameen":
            case "Skitto": {
                if (isPowerLoad && !"SK".equals(pcode)) {
                    return resolvePackageLoad("*444*", phone, amount, pin, type, packageName);
                }
                if ("SK".equals(pcode)) {
                    return single("*666*" + phone + "*" + amount + "*" + pin + "#");
                }
                return single("*444*" + phone + "*" + amount + "*0*" + pin + "#");
            }

            case "Robi": {
                if (isPowerLoad) {
                    return resolvePackageLoad("*888*", phone, amount, pin, type, packageName);
                }
                return single("*888*" + phone + "*" + amount + "*" + pin + "#");
            }

            case "Airtel": {
                if (isPowerLoad) {
                    return resolvePackageLoad("*888*", phone, amount, pin, type, packageName);
                }
                return single("*888*" + phone + "*" + amount + "*" + pin + "#");
            }

            case "Banglalink": {
                if (isPowerLoad) {
                    return resolvePackageLoad("*555*", phone, amount, pin, type, packageName);
                }
                return single("*555*" + phone + "*" + amount + "*" + pin + "#");
            }

            case "Taletalk": {
                return single("*250*" + phone + "*" + amount + "*" + pin + "#");
            }

            // ── Mobile banking: bKash ────────────────────────────────────────

            case "bKash-Agent-SIM": {
                if ("BK".equals(pcode)) {
                    // bKash Cash In: *247# → 2 → phone → amount → pin
                    return steps("*247#", "2", phone, amount, pin);
                }
                return Collections.emptyList();
            }

            case "bKash-Personal-SIM": {
                if ("BKS".equals(pcode)) {
                    // bKash Send Money: *247# → 1 → phone → amount → flexiId(sid) → pin
                    // Note: flexiId requires the SID which we receive as pcode context.
                    // The personal SIM send-money flow in MainActivity passes flexiId=sid,
                    // but we don't have sid here. Use the multi-step template approach instead.
                    return steps("*247#", "1", phone, amount, pin);
                }
                if ("BKA".equals(pcode)) {
                    // bKash Cash Out: *247# → 4 → phone → amount → pin
                    return steps("*247#", "4", phone, amount, pin);
                }
                return Collections.emptyList();
            }

            // ── Mobile banking: Nagad ────────────────────────────────────────

            case "Nagad-Agent-SIM": {
                if ("NG".equals(pcode)) {
                    // Nagad Cash In: *167# → 3 → phone → amount → pin
                    return steps("*167#", "3", phone, amount, pin);
                }
                return Collections.emptyList();
            }

            case "Nagad-Personal-SIM": {
                if ("NGA".equals(pcode)) {
                    // Nagad Cash Out: *167# → 4 → phone → amount → pin
                    return steps("*167#", "4", phone, amount, pin);
                }
                if ("NGS".equals(pcode)) {
                    // Nagad Send Money: *167# → 1 → phone → amount → pin
                    return steps("*167#", "1", phone, amount, pin);
                }
                return Collections.emptyList();
            }

            // ── Mobile banking: Roket ────────────────────────────────────────

            case "Roket-Agent-SIM": {
                if ("RK".equals(pcode)) {
                    // Roket Cash In: *322# → 2 → phone → amount → pin
                    return steps("*322#", "2", phone, amount, pin);
                }
                return Collections.emptyList();
            }

            case "Roket-Personal-SIM": {
                if ("RKS".equals(pcode)) {
                    // Roket Send Money: *322# → 1 → phone → amount → pin
                    return steps("*322#", "1", phone, amount, pin);
                }
                if ("RKA".equals(pcode)) {
                    // Roket Cash Out: *322# → 4 → phone → amount → pin
                    return steps("*322#", "4", phone, amount, pin);
                }
                return Collections.emptyList();
            }

            // ── bKash Load / Nagad Load (same as carrier load with specific code) ──

            case "bKash-Load": {
                // bKash Load uses the same flow as bKash Personal Send Money
                return steps("*247#", "1", phone, amount, pin);
            }

            case "Nagad-Load": {
                // Nagad Load uses the same flow as Nagad Personal Send Money
                return steps("*167#", "1", phone, amount, pin);
            }

            default:
                return Collections.emptyList();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Single-step USSD — the entire code is dialed in one shot. */
    private static List<String> single(String code) {
        return Collections.singletonList(code);
    }

    /** Multi-step USSD — first element is dialed, remaining elements are sent into the dialog. */
    private static List<String> steps(String... parts) {
        List<String> list = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (p != null && !p.isEmpty()) {
                list.add(p);
            }
        }
        return list.isEmpty() ? Collections.emptyList() : list;
    }

    /**
     * Resolves a package/power load USSD code.
     * Package loads use: prefix + packageName + "*" + phone + "*" + pin + "#"
     * or fall back to: prefix + phone + "*" + amount + "*" + type + "*" + pin + "#"
     */
    private static List<String> resolvePackageLoad(String prefix, String phone,
                                                    String amount, String pin,
                                                    String type, String packageName) {
        if (packageName != null && !packageName.trim().isEmpty()) {
            return single(prefix + packageName.trim() + "*" + phone + "*" + pin + "#");
        }
        return single(prefix + phone + "*" + amount + "*" + type + "*" + pin + "#");
    }
}
