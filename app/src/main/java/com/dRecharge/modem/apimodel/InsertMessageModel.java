package com.dRecharge.modem.apimodel;

import com.google.gson.annotations.SerializedName;

public class InsertMessageModel {
    @SerializedName("msg")
    private String msg = "";

    @SerializedName("status")
    private String status = "";

    @SerializedName("insert")
    private String insert = "";

    @SerializedName("position")
    private String position = "";

    @SerializedName("simam")
    private String simam = "";

    @SerializedName("check")
    private String check = "";

    public String getMsg() {
        return safe(msg);
    }

    public String getStatus() {
        return safe(status);
    }

    public String getInsert() {
        return safe(insert);
    }

    public String getPosition() {
        return safe(position);
    }

    public String getSimam() {
        return safe(simam);
    }

    public String getCheck() {
        return safe(check);
    }

    public boolean hasStatus(String expectedStatus) {
        return expectedStatus != null && expectedStatus.equals(getStatus());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
