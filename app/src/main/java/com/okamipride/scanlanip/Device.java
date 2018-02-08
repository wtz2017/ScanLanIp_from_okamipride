package com.okamipride.scanlanip;

/**
 * Created by WTZ on 2018/2/8.
 */

public class Device {

    private String ip;
    private String mac;

    public Device(String ip, String mac) {
        this.ip = ip;
        this.mac = mac;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

}
