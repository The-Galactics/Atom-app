package com.atom.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class User {
    private String id;
    private String email;
    private String name;
    private String password;
    private LocalDateTime createdAt;
    private boolean active;

    public User(String email, String name, String password) {
        this.id = UUID.randomUUID().toString();
        this.email = email;
        this.name = name;
        this.password = password;
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    public User(String id, String email, String name, String password,
                LocalDateTime createdAt, boolean active) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.password = password;
        this.createdAt = createdAt;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void updateProfile(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return active;
    }
}
