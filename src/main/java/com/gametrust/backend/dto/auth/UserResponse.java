package com.gametrust.backend.dto.auth;

import com.gametrust.backend.entity.Role;
import com.gametrust.backend.entity.User;

import java.time.Instant;

public class UserResponse {

    private String id;
    private String username;
    private String email;
    private Role role;
    private int reputationScore;
    private String avatarUrl;
    private boolean active = true;
    private Instant createdAt;
    private Double walletBalance;

    public UserResponse() {
    }

    public UserResponse(String id, String username, String email, Role role, int reputationScore, String avatarUrl, boolean active, Instant createdAt, Double walletBalance) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;
        this.reputationScore = reputationScore;
        this.avatarUrl = avatarUrl;
        this.active = active;
        this.createdAt = createdAt;
        this.walletBalance = walletBalance;
    }

    public static UserResponse fromUser(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getReputationScore(),
                user.getAvatarUrl(),
                user.isActive(),
                user.getCreatedAt(),
                user.getWalletBalance()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public int getReputationScore() {
        return reputationScore;
    }

    public void setReputationScore(int reputationScore) {
        this.reputationScore = reputationScore;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Double getWalletBalance() {
        return walletBalance;
    }

    public void setWalletBalance(Double walletBalance) {
        this.walletBalance = walletBalance;
    }
}
