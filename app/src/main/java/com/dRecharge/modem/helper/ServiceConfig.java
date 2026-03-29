package com.dRecharge.modem.helper;

public class ServiceConfig {
    public final String name;
    public String pin = "";
    public int sim = 1;
    public String number = "";
    public boolean active = false;

    public ServiceConfig(String name) {
        this.name = name;
    }
}
