package com.dRecharge.modem.helper;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class Session {
    SharedPreferences pref;
    SharedPreferences.Editor editor;
    Context _context;
    int PRIVATE_MODE = 0;

    public static final String PREFER_NAME = "ReloadMobileModule";
    public static final String IS_DOMAIN_VALIED = "is_domain_valied";
    public static final String API_DOMAIN_LINK = "api_url";

    public static final String SIM1_INFO = "sim1Info";
    public static final String SIM2_INFO = "sim2Info";

    // SIM 1 SESSION DATA
    public static final String SIM1_ID = "sim1id";
    public static final String SIM1_NUMBER = "sim1Number";
    public static final String SIM1_PIN = "sim1Pin";
    public static final String SIM1_TIME = "sim1Time";
    public static final String SIM1_MIN_BAL = "sim1MinBal";
    public static final String SIM1_SERVICE_CODE = "sim1ServiceCode";
    public static final String SIM1_SERVICE = "sim1service";
    public static final String SIM1_SERVICE_NAME = "sim1servicename";
    public static final String SIM1_ENABLED = "sim1Enabled";

    // SIM 2 SESSION DATA
    public static final String SIM2_ID = "sim2id";
    public static final String SIM2_NUMBER = "sim2Number";
    public static final String SIM2_PIN = "sim2Pin";
    public static final String SIM2_TIME = "sim2Time";
    public static final String SIM2_MIN_BAL = "sim2MinBal";
    public static final String SIM2_SERVICE_CODE = "sim2ServiceCode";
    public static final String SIM2_SERVICE = "sim2service";
    public static final String SIM2_SERVICE_NAME = "sim2serviceame";
    public static final String SIM2_ENABLED = "sim2Enabled";

    //Common
    public static final String TIME_INTERVAL = "timeInterval";

    public Session(Context context) {
        this._context = context;
        pref = _context.getSharedPreferences(PREFER_NAME, PRIVATE_MODE);
        editor = pref.edit();
    }

    public String getData(String id) {
        return pref.getString(id, "");
    }

    public int getIntData(String id) {
        return pref.getInt(id, -1);
    }

    public boolean getBooleanData(String id) {
        return pref.getBoolean(id, false);
    }

    public void setData(String id, String val) {
        editor.putString(id, val);
        editor.apply();
    }

    public void setIntData(String id, int val) {
        editor.putInt(id, val);
        editor.apply();
    }

    public void SetSim1Info(Boolean sim1Info, String sim1Num, String sim1Pin, String sim1MinBal, String sim1Time) {
        editor.putBoolean(SIM1_INFO, sim1Info);
        editor.putString(SIM1_NUMBER, sim1Num);
        editor.putString(SIM1_PIN, sim1Pin);
        editor.putString(SIM1_MIN_BAL, sim1MinBal);
        editor.putString(SIM1_TIME, sim1Time);
        editor.apply();
    }

    public void SetSim2Info(Boolean sim2Info, String sim2Num, String sim2Pin, String sim2MinBal, String sim2Time) {
        editor.putBoolean(SIM2_INFO, sim2Info);
        editor.putString(SIM2_NUMBER, sim2Num);
        editor.putString(SIM2_PIN, sim2Pin);
        editor.putString(SIM2_MIN_BAL, sim2MinBal);
        editor.putString(SIM2_TIME, sim2Time);
        editor.apply();
    }

    public void setBooleanData(String id, Boolean val) {
        editor.putBoolean(id, val);
        editor.apply();
    }

    public boolean isDomainValid() {
        return pref.getBoolean(IS_DOMAIN_VALIED, false);
    }

    public boolean isSim1Valid() {
        return pref.getBoolean(SIM1_INFO, false);
    }

    public boolean isSim2Valid() {
        return pref.getBoolean(SIM2_INFO, false);
    }

    // ── Per-service configuration ────────────────────────────────────────────

    private static String svcKey(String name, String field) {
        // e.g. "svc_bKash_Agent_SIM_pin"
        return "svc_" + name.replaceAll("[^a-zA-Z0-9]", "_") + "_" + field;
    }

    public ServiceConfig getServiceConfig(String name) {
        ServiceConfig cfg = new ServiceConfig(name);
        cfg.pin             = pref.getString(svcKey(name, "pin"), "");
        cfg.sim             = pref.getInt(svcKey(name, "sim"), 1);
        cfg.number          = pref.getString(svcKey(name, "number"), "");
        cfg.active          = pref.getBoolean(svcKey(name, "active"), false);
        cfg.dialCode1       = pref.getString(svcKey(name, "dialCode1"), "");
        cfg.dialCode0       = pref.getString(svcKey(name, "dialCode0"), "");
        cfg.scheduleEnabled = pref.getBoolean(svcKey(name, "scheduleEnabled"), false);
        cfg.scheduleStart   = pref.getString(svcKey(name, "scheduleStart"), "");
        cfg.scheduleEnd     = pref.getString(svcKey(name, "scheduleEnd"), "");
        return cfg;
    }

    public void saveServiceConfig(ServiceConfig cfg) {
        editor.putString(svcKey(cfg.name, "pin"), cfg.pin);
        editor.putInt(svcKey(cfg.name, "sim"), cfg.sim);
        editor.putString(svcKey(cfg.name, "number"), cfg.number);
        editor.putBoolean(svcKey(cfg.name, "active"), cfg.active);
        editor.putString(svcKey(cfg.name, "dialCode1"), cfg.dialCode1);
        editor.putString(svcKey(cfg.name, "dialCode0"), cfg.dialCode0);
        editor.putBoolean(svcKey(cfg.name, "scheduleEnabled"), cfg.scheduleEnabled);
        editor.putString(svcKey(cfg.name, "scheduleStart"), cfg.scheduleStart);
        editor.putString(svcKey(cfg.name, "scheduleEnd"), cfg.scheduleEnd);
        editor.apply();
    }

    /**
     * Returns true if the service should be treated as active right now,
     * considering its schedule. If schedule is disabled or times are not set,
     * falls back to cfg.active.
     */
    public static boolean isScheduledActiveNow(ServiceConfig cfg) {
        if (!cfg.scheduleEnabled || cfg.scheduleStart.isEmpty() || cfg.scheduleEnd.isEmpty()) {
            return cfg.active;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
            Calendar now = Calendar.getInstance();
            int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

            Calendar startCal = Calendar.getInstance();
            startCal.setTime(sdf.parse(cfg.scheduleStart));
            int startMin = startCal.get(Calendar.HOUR_OF_DAY) * 60 + startCal.get(Calendar.MINUTE);

            Calendar endCal = Calendar.getInstance();
            endCal.setTime(sdf.parse(cfg.scheduleEnd));
            int endMin = endCal.get(Calendar.HOUR_OF_DAY) * 60 + endCal.get(Calendar.MINUTE);

            if (startMin <= endMin) {
                return nowMin >= startMin && nowMin < endMin;
            } else {
                // overnight window e.g. 10:00 PM – 06:00 AM
                return nowMin >= startMin || nowMin < endMin;
            }
        } catch (Exception ex) {
            return cfg.active;
        }
    }

    /** Returns all service configs that are currently active for the given SIM slot (1 or 2),
     *  respecting each service's schedule if one is configured. */
    public List<ServiceConfig> getActiveServicesForSim(int sim) {
        List<String> all = ServiceCatalog.getServices();
        List<ServiceConfig> result = new ArrayList<>();
        for (String name : all) {
            if (ServiceCatalog.SELECT_ONE.equals(name)) continue;
            ServiceConfig cfg = getServiceConfig(name);
            if (cfg.sim == sim && isScheduledActiveNow(cfg)) {
                result.add(cfg);
            }
        }
        return result;
    }

    /** Returns all currently active service configs (both SIMs),
     *  respecting each service's schedule if one is configured. */
    public List<ServiceConfig> getAllActiveServices() {
        List<String> all = ServiceCatalog.getServices();
        List<ServiceConfig> result = new ArrayList<>();
        for (String name : all) {
            if (ServiceCatalog.SELECT_ONE.equals(name)) continue;
            ServiceConfig cfg = getServiceConfig(name);
            if (isScheduledActiveNow(cfg)) result.add(cfg);
        }
        return result;
    }
}
