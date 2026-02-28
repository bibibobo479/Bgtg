package com.chat.server.model;

import java.util.Date;

public class UserConnection {
    private int id;
    private int userId;
    private String ipAddress;
    private Date connectedAt;
    private Date disconnectedAt;
    
    public UserConnection() {}
    
    public UserConnection(int userId, String ipAddress) {
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.connectedAt = new Date();
    }
    
    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public Date getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Date connectedAt) { this.connectedAt = connectedAt; }
    
    public Date getDisconnectedAt() { return disconnectedAt; }
    public void setDisconnectedAt(Date disconnectedAt) { this.disconnectedAt = disconnectedAt; }
}
