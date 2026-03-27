package com.dRecharge.modem.apimodel;

public class SimModel {

    public String st;
    public String msg;
    public String operator;
    public String sim_number;
    public String sim_id;
    public String balance;
    public String service_code;
    public String status;


    public SimModel(String st, String msg, String operator, String sim_number, String sim_id, String balance, String service_code, String status) {
        this.st = st;
        this.msg = msg;
        this.operator = operator;
        this.sim_number = sim_number;
        this.sim_id = sim_id;
        this.balance = balance;
        this.service_code = service_code;
        this.status = status;
    }

    public String getSt() {
        return st;
    }

    public String getMsg() {
        return msg;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public void setSim_number(String sim_number) {
        this.sim_number = sim_number;
    }

    public void setSim_id(String sim_id) {
        this.sim_id = sim_id;
    }

    public void setBalance(String balance) {
        this.balance = balance;
    }

    public void setService_code(String service_code) {
        this.service_code = service_code;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
