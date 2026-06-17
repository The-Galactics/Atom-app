package com.atom.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Message {
    private String id;
    private String chatId;
    private String userId;
    private String content;
    private LocalDateTime createdAt;

    public Message(String chatId, String userId, String content) {
        this.id = UUID.randomUUID().toString();
        this.chatId = chatId;
        this.userId = userId;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public Message(String id, String chatId, String userId, String content, LocalDateTime createdAt) {
        this.id = id;
        this.chatId = chatId;
        this.userId = userId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public boolean isOwnedBy(String userId) {
        return this.userId.equals(userId);
    }

    public String getId() {
        return id;
    }

    public String getChatId() {
        return chatId;
    }

    public String getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
