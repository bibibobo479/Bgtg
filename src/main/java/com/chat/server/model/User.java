package com.chat.server.model;

import java.util.Date;
import java.util.UUID;

public class User {
    private int id;
    private String nickname;
    private String deviceName;
    private String ipAddress;
    private Date firstSeen;
    private Date lastSeen;
    private int totalMessages;
    private int totalFiles;
    
    // Конструкторы, геттеры и сеттеры
    public User() {}
    
    public User(String nickname, String deviceName, String ipAddress) {
        this.nickname = nickname;
        this.deviceName = deviceName;
        this.ipAddress = ipAddress;
        this.firstSeen = new Date();
        this.lastSeen = new Date();
        this.totalMessages = 0;
        this.totalFiles = 0;
    }
    
    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public Date getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Date firstSeen) { this.firstSeen = firstSeen; }
    
    public Date getLastSeen() { return lastSeen; }
    public void setLastSeen(Date lastSeen) { this.lastSeen = lastSeen; }
    
    public int getTotalMessages() { return totalMessages; }
    public void setTotalMessages(int totalMessages) { this.totalMessages = totalMessages; }
    
    public int getTotalFiles() { return totalFiles; }
    public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
}
