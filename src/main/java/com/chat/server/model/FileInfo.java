package com.chat.server.model;

import java.util.Date;
import java.util.UUID;

public class FileInfo {
    private int id;
    private String fileId;
    private String filename;
    private String sender;
    private String fileType;
    private long fileSize;
    private String savedPath;
    private String blurhash;
    private Date timestamp;
    
    public FileInfo() {
        this.fileId = UUID.randomUUID().toString();
        this.timestamp = new Date();
    }
    
    public FileInfo(String filename, String sender, String fileType, 
                    long fileSize, String savedPath, String blurhash) {
        this();
        this.filename = filename;
        this.sender = sender;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.savedPath = savedPath;
        this.blurhash = blurhash;
    }
    
    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    
    public String getSavedPath() { return savedPath; }
    public void setSavedPath(String savedPath) { this.savedPath = savedPath; }
    
    public String getBlurhash() { return blurhash; }
    public void setBlurhash(String blurhash) { this.blurhash = blurhash; }
    
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}
