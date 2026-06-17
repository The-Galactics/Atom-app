package com.atom.application.port.out;

import com.atom.domain.model.User;

import java.util.Optional;

public interface UserPortOut {
    User save(User user);
    Optional<User> findById(String userId);
    Optional<User> findByEmail(String email);
    void delete(User user);
}
