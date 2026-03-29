package com.dRecharge.modem.helper;

public class ServiceConfig {
    public final String name;
    public String pin = "";
    public int sim = 1;
    public String number = "";
    public boolean active = false;
    /** USSD dial template for type "1" (commission/postpaid). Placeholders: {PHONE} {AMOUNT} {PIN} */
    public String dialCode1 = "";
    /** USSD dial template for type "0" (prepaid). Empty means same as dialCode1. */
    public String dialCode0 = "";
    /** Whether auto-enable/disable schedule is active for this service. */
    public boolean scheduleEnabled = false;
    /** Daily enable time in "HH:mm" format (24-hour). Empty = not set. */
    public String scheduleStart = "";
    /** Daily disable time in "HH:mm" format (24-hour). Empty = not set. */
    public String scheduleEnd = "";

    public ServiceConfig(String name) {
        this.name = name;
    }
}
