package com.atom.application.port.out.security;


public interface PasswordEncoderPortOut {
    
    String hashPassword(String rawPassword);
    boolean verifyPassword(String rawPassword, String encodedPassword);
}
