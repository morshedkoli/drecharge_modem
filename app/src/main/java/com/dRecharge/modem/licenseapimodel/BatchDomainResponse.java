package com.dRecharge.modem.licenseapimodel;

public class BatchDomainResponse {
    private boolean success;
    private String requestId;
    private String checkedAt;
    private BatchData data;
    private ApiError error;

    public boolean isSuccess() {
        return success;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getCheckedAt() {
        return checkedAt;
    }

    public BatchData getData() {
        return data;
    }

    public ApiError getError() {
        return error;
    }
}
