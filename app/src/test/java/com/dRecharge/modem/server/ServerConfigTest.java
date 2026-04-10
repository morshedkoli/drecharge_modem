package com.dRecharge.modem.server;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ServerConfigTest {
    @Test
    public void normalizeSubscriptionDomain_removesProtocolAndWwwPrefix() {
        assertEquals("ikramtel.com", ServerConfig.normalizeSubscriptionDomain("https://ikramtel.com"));
        assertEquals("ikramtel.com", ServerConfig.normalizeSubscriptionDomain("www.ikramtel.com"));
        assertEquals("ikramtel.com", ServerConfig.normalizeSubscriptionDomain("https://www.ikramtel.com"));
    }
}
