package com.dRecharge.modem.service;

import com.dRecharge.modem.apimodel.GetPendingModel;

public class ServiceRequest {
    private final String sid;
    private final String pcode;
    private final String phone;
    private final String amount;
    private final String type;
    private final String packageName;
    private final boolean powerLoad;
    private final String status;

    public ServiceRequest(String sid, String pcode, String phone, String amount, String type,
                          String packageName, boolean powerLoad, String status) {
        this.sid = safe(sid);
        this.pcode = safe(pcode);
        this.phone = safe(phone);
        this.amount = safe(amount);
        this.type = safe(type);
        this.packageName = safe(packageName);
        this.powerLoad = powerLoad;
        this.status = safe(status);
    }

    public static ServiceRequest from(GetPendingModel model) {
        if (model == null) {
            return new ServiceRequest("", "", "", "", "", "", false, "");
        }
        return new ServiceRequest(
                model.getSid(),
                model.getPcode(),
                model.getPhone(),
                model.getBalance(),
                model.getType(),
                model.getPackage_name(),
                model.isPowerLoad(),
                model.getStatus()
        );
    }

    public String getSid() {
        return sid;
    }

    public String getPcode() {
        return pcode;
    }

    public String getPhone() {
        return phone;
    }

    public String getAmount() {
        return amount;
    }

    public String getType() {
        return type;
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isPowerLoad() {
        return powerLoad;
    }

    public String getStatus() {
        return status;
    }

    public boolean hasPendingRequest() {
        return "1".equals(status);
    }

    public boolean shouldStopService() {
        return "4".equals(status);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
