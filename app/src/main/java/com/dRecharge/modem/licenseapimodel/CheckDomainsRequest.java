package com.dRecharge.modem.licenseapimodel;

import java.util.List;

public class CheckDomainsRequest {
    private final List<String> domains;

    public CheckDomainsRequest(List<String> domains) {
        this.domains = domains;
    }

    public List<String> getDomains() {
        return domains;
    }
}
