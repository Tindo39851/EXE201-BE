package com.gametrust.backend.service;

import com.gametrust.backend.dto.auth.AuthResponse;
import com.gametrust.backend.dto.auth.LoginRequest;
import com.gametrust.backend.dto.auth.RegisterRequest;
import com.gametrust.backend.entity.Role;
import com.gametrust.backend.entity.User;
import com.gametrust.backend.exception.BadRequestException;
import com.gametrust.backend.repository.RefreshTokenRepository;
import com.gametrust.backend.repository.UserRepository;
import com.gametrust.backend.security.JwtService;
import com.gametrust.backend.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User("pro_gamer", "gamer@gametrust.gg", "encoded_password", Role.MEMBER);
        sampleUser.setId("66fdb9c110b4e62a8a11f001");
    }

    @Test
    void register_Success() {
        RegisterRequest request = new RegisterRequest("pro_gamer", "gamer@gametrust.gg", "secret123");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtService.generateAccessToken(any(), any())).thenReturn("mock_access_token");
        when(jwtService.generateRefreshToken(anyString())).thenReturn("mock_refresh_token");
        when(jwtService.getExpiration()).thenReturn(900000L);
        when(jwtService.getRefreshExpiration()).thenReturn(604800000L);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock_access_token", response.getAccessToken());
        assertEquals("mock_refresh_token", response.getRefreshToken());
        assertEquals("pro_gamer", response.getUser().getUsername());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_EmailAlreadyExists_ThrowsBadRequestException() {
        RegisterRequest request = new RegisterRequest("pro_gamer", "existing@gametrust.gg", "secret123");

        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest("gamer@gametrust.gg", "secret123");

        when(userRepository.findByEmailOrUsername(anyString(), anyString())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("secret123", "encoded_password")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), any())).thenReturn("mock_access_token");
        when(jwtService.generateRefreshToken(anyString())).thenReturn("mock_refresh_token");
        when(jwtService.getExpiration()).thenReturn(900000L);
        when(jwtService.getRefreshExpiration()).thenReturn(604800000L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock_access_token", response.getAccessToken());
        assertEquals("pro_gamer", response.getUser().getUsername());
    }

    @Test
    void login_InvalidPassword_ThrowsBadCredentialsException() {
        LoginRequest request = new LoginRequest("gamer@gametrust.gg", "wrong_password");

        when(userRepository.findByEmailOrUsername(anyString(), anyString())).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong_password", "encoded_password")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }
}
