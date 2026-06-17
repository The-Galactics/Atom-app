package com.atom.application.usecase;

import com.atom.application.port.out.security.PasswordEncoderPortOut;
import com.atom.application.port.out.UserPortOut;
import com.atom.domain.model.User;

import java.util.Optional;

public class UserUseCase {

    private final UserPortOut userPortOut;
    private final PasswordEncoderPortOut passwordEncoder;

    public UserUseCase(UserPortOut userPortOut, PasswordEncoderPortOut passwordEncoder) {
        this.userPortOut = userPortOut;
        this.passwordEncoder = passwordEncoder;
    }


    public User registerUser(String email, String name, String rawPassword) {
        String hashedPassword = passwordEncoder.hashPassword(rawPassword);
        User user = new User(email, name, hashedPassword);
        return userPortOut.save(user);
    }

    public boolean authenticateUser(String email, String rawPassword) {
        Optional<User> userOptional = userPortOut.findByEmail(email);
        return userOptional.map(user -> passwordEncoder.verifyPassword(rawPassword, user.getPassword()))
                .orElse(false);
    }
}
