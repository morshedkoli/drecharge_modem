package com.dRecharge.modem.licenseapimodel;

public class CheckDomainLicense {
    public int status;
    public boolean success;
    public String message;

    public CheckDomainLicense(int status, boolean success, String message) {
        this.status = status;
        this.success = success;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}

