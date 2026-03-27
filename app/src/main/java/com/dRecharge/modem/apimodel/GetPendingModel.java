package com.dRecharge.modem.apimodel;

import com.google.gson.annotations.SerializedName;

public class GetPendingModel {
    @SerializedName("id")
    private String id = "";

    @SerializedName("sid")
    private String sid = "";

    @SerializedName("userid")
    private String userid = "";

    @SerializedName("admin_id")
    private String adminId = "";

    @SerializedName("p_id")
    private String productId = "";

    @SerializedName("phone")
    private String phone = "";

    @SerializedName("type")
    private String type = "";

    @SerializedName("balance")
    private String balance = "";

    @SerializedName("status")
    private String status = "";

    @SerializedName("pcode")
    private String pcode = "";

    @SerializedName(value = "package_name", alternate = {"packageName"})
    private String packageName = "";

    @SerializedName("sender")
    private String sender = "";

    @SerializedName(value = "isPowerLoad", alternate = {"is_power_load", "power_load", "powerLoad"})
    private String powerLoad = "";

    public String getId() {
        return safe(id);
    }

    public String getSid() {
        return safe(sid);
    }

    public String getUserid() {
        return safe(userid);
    }

    public String getAdmin_id() {
        return safe(adminId);
    }

    public String getP_id() {
        return safe(productId);
    }

    public String getPhone() {
        return safe(phone);
    }

    public String getType() {
        return safe(type);
    }

    public String getBalance() {
        return safe(balance);
    }

    public String getStatus() {
        return safe(status);
    }

    public String getPcode() {
        return safe(pcode);
    }

    public String getPackage_name() {
        return safe(packageName);
    }

    public String getSender() {
        return safe(sender);
    }

    public boolean isPowerLoad() {
        String value = safe(powerLoad);
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    public boolean hasStatus(String expectedStatus) {
        return expectedStatus != null && expectedStatus.equals(getStatus());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
