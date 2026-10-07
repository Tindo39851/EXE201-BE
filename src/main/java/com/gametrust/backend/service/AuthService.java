package com.gametrust.backend.service;

import com.gametrust.backend.dto.auth.*;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String refreshToken);

    UserResponse getCurrentUser(String username);

    UserResponse updateProfile(String username, String avatarUrl);

    void sendRegistrationOtp(SendOtpRequest request);

    AuthResponse verifyAndRegister(VerifyOtpRegisterRequest request);
}
