package com.dRecharge.modem.licenseapimodel;

import java.util.List;

public class BatchData {
    private int count;
    private List<DomainSubscriptionStatus> domains;

    public int getCount() {
        return count;
    }

    public List<DomainSubscriptionStatus> getDomains() {
        return domains;
    }
}
