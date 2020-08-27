package ru.mts.e2e.mtucheck;

public class eNB {
    private String id;
    private String ip;
    private String mmeIP;
    private long last_check;
    private EnodebStatus status;

    public eNB (String id, String ip, String mmeIP, long last_check, EnodebStatus status) {
        this.id = id;
        this.ip = ip;
        this.mmeIP = mmeIP;
        this.last_check = last_check;
        this.status = status;
    }

    public String getId() { return id; }

    public String getIp() {
        return ip;
    }

    public String getMmeIP() {
        return mmeIP;
    }

    public long getLast_check() {
        return last_check;
    }

    public EnodebStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "eNB{" +
                "id='" + id + '\'' +
                ", ip='" + ip + '\'' +
                ", mmeIP='" + mmeIP + '\'' +
                ", last_check=" + last_check +
                ", status=" + status +
                '}';
    }
}