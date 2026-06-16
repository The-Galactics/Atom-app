package com.atom.application.port.in;

import com.atom.domain.model.User;

import java.util.Optional;

public interface UserPortIn {
    User createUser(String email, String name);
    Optional<User> getUserById(String userId);
    Optional<User> getUserByEmail(String email);
    User updateUserProfile(String userId, String name);
    void deactivateUser(String userId);
    void activateUser(String userId);
}
