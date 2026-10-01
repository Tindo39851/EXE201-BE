package com.gametrust.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "Email or username is required")
    private String emailOrUsername;

    @NotBlank(message = "Password is required")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(String emailOrUsername, String password) {
        this.emailOrUsername = emailOrUsername;
        this.password = password;
    }

    public String getEmailOrUsername() {
        return emailOrUsername;
    }

    public void setEmailOrUsername(String emailOrUsername) {
        this.emailOrUsername = emailOrUsername;
    }

    // Convenience setters/getters in case clients send 'email' or 'username'
    public void setEmail(String email) {
        if (this.emailOrUsername == null || this.emailOrUsername.isBlank()) {
            this.emailOrUsername = email;
        }
    }

    public void setUsername(String username) {
        if (this.emailOrUsername == null || this.emailOrUsername.isBlank()) {
            this.emailOrUsername = username;
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
