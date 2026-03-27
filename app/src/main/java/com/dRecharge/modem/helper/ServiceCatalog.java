package com.dRecharge.modem.helper;

import java.util.Arrays;
import java.util.List;

public final class ServiceCatalog {
    public static final String SELECT_ONE = "Select One";

    private ServiceCatalog() {
    }

    public static List<String> getServices() {
        return Arrays.asList(
                SELECT_ONE,
                "Grameen",
                "Robi",
                "Airtel",
                "bLink",
                "Taletalk",
                "bKash-Load",
                "Nagad-Load",
                "bKash-Agent-SIM",
                "bKash-Personal-SIM",
                "Roket-Agent-SIM",
                "Roket-Personal-SIM",
                "Nagad-Agent-SIM",
                "Nagad-Personal-SIM"
        );
    }

    public static String getCodeForService(String serviceName) {
        if (serviceName == null) {
            return "";
        }

        switch (serviceName) {
            case "Grameen":
                return "GP";
            case "Robi":
                return "RB";
            case "Airtel":
                return "AT";
            case "bLink":
                return "BL";
            case "Taletalk":
                return "TT";
            case "bKash-Load":
            case "Nagad-Load":
                return "GP,RB,AT,BL,TT";
            case "bKash-Agent-SIM":
                return "BK";
            case "bKash-Personal-SIM":
                return "BKA,BKS";
            case "Roket-Agent-SIM":
                return "RK";
            case "Roket-Personal-SIM":
                return "RKA,RKS";
            case "Nagad-Agent-SIM":
                return "NG";
            case "Nagad-Personal-SIM":
                return "NGA,NGS";
            default:
                return "";
        }
    }

    public static int indexOf(String serviceName) {
        List<String> services = getServices();
        int index = services.indexOf(serviceName);
        return index >= 0 ? index : 0;
    }
}
