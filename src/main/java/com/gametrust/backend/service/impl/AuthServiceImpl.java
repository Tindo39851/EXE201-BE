package com.gametrust.backend.service.impl;

import com.gametrust.backend.dto.auth.*;
import com.gametrust.backend.entity.RefreshToken;
import com.gametrust.backend.entity.Role;
import com.gametrust.backend.entity.User;
import com.gametrust.backend.exception.BadRequestException;
import com.gametrust.backend.exception.ResourceNotFoundException;
import com.gametrust.backend.exception.UnauthorizedException;
import com.gametrust.backend.repository.RefreshTokenRepository;
import com.gametrust.backend.repository.UserRepository;
import com.gametrust.backend.security.JwtService;
import com.gametrust.backend.security.UserPrincipal;
import com.gametrust.backend.service.AuthService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        String cleanUsername = request.getUsername().trim();

        if (userRepository.existsByEmail(cleanEmail)) {
            throw new BadRequestException("An account with this email already exists");
        }

        if (userRepository.existsByUsername(cleanUsername)) {
            throw new BadRequestException("Username is already taken");
        }

        User user = new User(
                cleanUsername,
                cleanEmail,
                passwordEncoder.encode(request.getPassword()),
                Role.MEMBER
        );

        User savedUser = userRepository.save(user);

        return generateAuthResponse(savedUser);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getEmailOrUsername().trim();

        User user = userRepository.findByEmailOrUsername(identifier.toLowerCase(), identifier)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("Your account has been deactivated. Please contact support.");
        }

        return generateAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenStr = request.getRefreshToken();

        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (!refreshToken.isValid()) {
            refreshTokenRepository.delete(refreshToken);
            throw new UnauthorizedException("Refresh token has expired or been revoked");
        }

        User user = refreshToken.getUser();
        if (!user.isActive()) {
            throw new UnauthorizedException("Account is inactive");
        }

        // Token rotation: revoke old refresh token and issue a fresh pair
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return generateAuthResponse(user);
    }

    @Override
    @Transactional
    public void logout(String refreshTokenStr) {
        if (refreshTokenStr != null && !refreshTokenStr.isBlank()) {
            refreshTokenRepository.findByToken(refreshTokenStr)
                    .ifPresent(token -> {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                    });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return UserResponse.fromUser(user);
    }

    private AuthResponse generateAuthResponse(User user) {
        UserPrincipal principal = UserPrincipal.create(user);

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", user.getId().toString());
        extraClaims.put("role", user.getRole().name());
        extraClaims.put("email", user.getEmail());

        String accessToken = jwtService.generateAccessToken(principal, extraClaims);
        String refreshTokenString = jwtService.generateRefreshToken(user.getUsername());

        // Store refresh token with expiry
        Instant expiresAt = Instant.now().plusMillis(jwtService.getRefreshExpiration());
        RefreshToken refreshToken = new RefreshToken(user, refreshTokenString, expiresAt);
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                refreshTokenString,
                jwtService.getExpiration(),
                UserResponse.fromUser(user)
        );
    }
}
