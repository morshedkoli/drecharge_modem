package com.dRecharge.modem.licenseapimodel;

import com.google.gson.annotations.SerializedName;

public class DomainSubscriptionStatus {
    @SerializedName("domain")
    private String domain;

    @SerializedName(value = "tracked", alternate = {"isTracked", "is_tracked"})
    private boolean tracked;

    @SerializedName(value = "available", alternate = {"isAvailable", "is_available"})
    private boolean available;

    @SerializedName(value = "status", alternate = {"subscriptionStatus", "subscription_status"})
    private String status;

    @SerializedName(value = "subscribed", alternate = {"isSubscribed", "is_subscribed"})
    private boolean subscribed;

    @SerializedName(value = "expired", alternate = {"isExpired", "is_expired"})
    private boolean expired;

    @SerializedName(value = "expiresAt", alternate = {"expires_at", "expireAt", "expire_at", "expiryDate", "expiry_date"})
    private String expiresAt;

    @SerializedName(value = "daysUntilExpiry", alternate = {"days_until_expiry", "daysLeft", "days_left"})
    private Integer daysUntilExpiry;

    @SerializedName(value = "message", alternate = {"detail", "error", "description"})
    private String message;

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

    public String getDomain() {
        return domain;
    }

    public boolean isTracked() {
        return tracked;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getStatus() {
        return status;
    }

    public boolean isSubscribed() {
        return subscribed;
    }

    public boolean isExpired() {
        return expired;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public Integer getDaysUntilExpiry() {
        return daysUntilExpiry;
    }

    public String getMessage() {
        return message;
    }

    public String getDomainLogo() {
        return domainLogo;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setDomainLogo(String domainLogo) {
        this.domainLogo = domainLogo;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setTracked(boolean tracked) {
        this.tracked = tracked;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setDaysUntilExpiry(Integer daysUntilExpiry) {
        this.daysUntilExpiry = daysUntilExpiry;
    }
}
