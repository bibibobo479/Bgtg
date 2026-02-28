package com.chat.server.model;

import java.util.Date;
import java.util.UUID;	

public class Message {
    private String messageId;
    private String sender;
    private String recipient;
    private String text;
    private String fileId;
    private String fileName;
    private String fileType;
    private Date timestamp;
    
    public Message() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = new Date();
    }
    
    public Message(String sender, String recipient, String text, 
                   String fileId, String fileName, String fileType) {
        this();
        this.sender = sender;
        this.recipient = recipient;
        this.text = text;
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileType = fileType;
    }
    
    // Геттеры и сеттеры
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}
