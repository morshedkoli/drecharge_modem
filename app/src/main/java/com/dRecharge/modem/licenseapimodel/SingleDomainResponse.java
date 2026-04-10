package com.dRecharge.modem.licenseapimodel;

import com.google.gson.annotations.SerializedName;

public class SingleDomainResponse {
    private boolean success;
    private String requestId;
    private String checkedAt;

    @SerializedName(value = "data", alternate = {"subscription"})
    private DomainSubscriptionStatus data;

    @SerializedName("domain")
    private String domain;

    @SerializedName(value = "tracked", alternate = {"isTracked", "is_tracked"})
    private Boolean tracked;

    @SerializedName(value = "available", alternate = {"isAvailable", "is_available"})
    private Boolean available;

    @SerializedName(value = "status", alternate = {"subscriptionStatus", "subscription_status"})
    private String status;

    @SerializedName(value = "subscribed", alternate = {"isSubscribed", "is_subscribed"})
    private Boolean subscribed;

    @SerializedName(value = "expired", alternate = {"isExpired", "is_expired"})
    private Boolean expired;

    @SerializedName(value = "expiresAt", alternate = {"expires_at", "expireAt", "expire_at", "expiryDate", "expiry_date"})
    private String expiresAt;

    @SerializedName(value = "daysUntilExpiry", alternate = {"days_until_expiry", "daysLeft", "days_left"})
    private Integer daysUntilExpiry;

    @SerializedName(value = "domainLogo", alternate = {"domain_logo", "logo", "logo_name", "logoUrl", "logo_url"})
    private String domainLogo;

    @SerializedName(value = "displayName", alternate = {
            "display_name",
            "appName",
            "app_name",
            "domainName",
            "domain_name",
            "brandName",
            "brand_name",
            "name",
            "title"
    })
    private String displayName;

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

    public DomainSubscriptionStatus getData() {
        return data;
    }

    public ApiError getError() {
        return error;
    }

    public DomainSubscriptionStatus resolveData() {
        if (data != null) {
            mergeTopLevelInto(data);
            return data;
        }

        if (domain == null
                && tracked == null
                && available == null
                && status == null
                && subscribed == null
                && expired == null
                && expiresAt == null
                && daysUntilExpiry == null
                && domainLogo == null
                && displayName == null) {
            return null;
        }

        DomainSubscriptionStatus fallback = new DomainSubscriptionStatus();
        fallback.setDomain(domain);
        fallback.setTracked(Boolean.TRUE.equals(tracked));
        fallback.setAvailable(Boolean.TRUE.equals(available));
        fallback.setStatus(status);
        fallback.setSubscribed(Boolean.TRUE.equals(subscribed));
        fallback.setExpired(Boolean.TRUE.equals(expired));
        fallback.setExpiresAt(expiresAt);
        fallback.setDaysUntilExpiry(daysUntilExpiry);
        fallback.setDomainLogo(domainLogo);
        fallback.setDisplayName(displayName);
        return fallback;
    }

    private void mergeTopLevelInto(DomainSubscriptionStatus target) {
        if (target == null) {
            return;
        }

        if ((target.getDomain() == null || target.getDomain().trim().isEmpty()) && domain != null) {
            target.setDomain(domain);
        }
        if ((target.getStatus() == null || target.getStatus().trim().isEmpty()) && status != null) {
            target.setStatus(status);
        }
        if ((target.getExpiresAt() == null || target.getExpiresAt().trim().isEmpty()) && expiresAt != null) {
            target.setExpiresAt(expiresAt);
        }
        if (target.getDaysUntilExpiry() == null && daysUntilExpiry != null) {
            target.setDaysUntilExpiry(daysUntilExpiry);
        }
        if ((target.getDomainLogo() == null || target.getDomainLogo().trim().isEmpty()) && domainLogo != null) {
            target.setDomainLogo(domainLogo);
        }
        if ((target.getDisplayName() == null || target.getDisplayName().trim().isEmpty()) && displayName != null) {
            target.setDisplayName(displayName);
        }
        if (!target.isTracked() && tracked != null) {
            target.setTracked(Boolean.TRUE.equals(tracked));
        }
        if (!target.isAvailable() && available != null) {
            target.setAvailable(Boolean.TRUE.equals(available));
        }
        if (!target.isSubscribed() && subscribed != null) {
            target.setSubscribed(Boolean.TRUE.equals(subscribed));
        }
        if (!target.isExpired() && expired != null) {
            target.setExpired(Boolean.TRUE.equals(expired));
        }
    }
}
