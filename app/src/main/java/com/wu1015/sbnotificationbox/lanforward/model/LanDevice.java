package com.wu1015.sbnotificationbox.lanforward.model;

/**
 * 局域网设备信息
 */
public class LanDevice {
    private String deviceName;
    private String ipAddress;
    private int port;
    private long lastSeen;
    private boolean isOnline;

    public LanDevice(String deviceName, String ipAddress, int port) {
        this.deviceName = deviceName;
        this.ipAddress = ipAddress;
        this.port = port;
        this.lastSeen = System.currentTimeMillis();
        this.isOnline = true;
    }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    /** 唯一标识 = ip:port */
    public String getKey() {
        return ipAddress + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LanDevice device = (LanDevice) o;
        return port == device.port && ipAddress.equals(device.ipAddress);
    }

    @Override
    public int hashCode() {
        return (ipAddress + ":" + port).hashCode();
    }

    @Override
    public String toString() {
        return deviceName + " (" + ipAddress + ":" + port + ")";
    }
}
