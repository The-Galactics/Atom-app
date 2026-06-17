package com.atom.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Chat {
    private String id;
    private String userId;
    private List<String> messageIds;
    private LocalDateTime createdAt;
    private ChatStatus status;

    public enum ChatStatus {
        ACTIVE, ARCHIVED, DELETED
    }

    public Chat(String userId) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.messageIds = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.status = ChatStatus.ACTIVE;
    }

    public Chat(String id, String userId, List<String> messageIds,
                LocalDateTime createdAt, ChatStatus status) {
        this.id = id;
        this.userId = userId;
        this.messageIds = new ArrayList<>(messageIds);
        this.createdAt = createdAt;
        this.status = status;
    }

    public void addMessage(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Message ID cannot be empty");
        }
        if (!this.status.equals(ChatStatus.ACTIVE)) {
            throw new IllegalStateException("Cannot add messages to inactive chat");
        }
        this.messageIds.add(messageId);
    }

    public void removeMessage(String messageId) {
        this.messageIds.remove(messageId);
    }

    public void archive() {
        this.status = ChatStatus.ARCHIVED;
    }

    public void delete() {
        this.status = ChatStatus.DELETED;
        this.messageIds.clear();
    }

    public boolean isOwnedBy(String userId) {
        return this.userId.equals(userId);
    }

    public boolean isActive() {
        return this.status.equals(ChatStatus.ACTIVE);
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public List<String> getMessageIds() {
        return new ArrayList<>(messageIds);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ChatStatus getStatus() {
        return status;
    }

    public int getMessageCount() {
        return messageIds.size();
    }
}
