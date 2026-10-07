package com.gametrust.backend.controller;

import com.gametrust.backend.dto.auth.*;
import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.service.AuthService;
import com.gametrust.backend.exception.BadRequestException;
import com.gametrust.backend.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for registration, login, token refresh, logout, and profile")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new gamer account with OTP", description = "Verifies 6-digit OTP code, creates account with role MEMBER and sets HttpOnly JWT cookies")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody VerifyOtpRegisterRequest request) {
        AuthResponse response = authService.verifyAndRegister(request);
        ResponseCookie accessCookie = jwtService.generateAccessTokenCookie(response.getAccessToken());
        ResponseCookie refreshCookie = jwtService.generateRefreshTokenCookie(response.getRefreshToken());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success("Account registered successfully", response));
    }

    @PostMapping("/send-registration-otp")
    @Operation(summary = "Send OTP code to email for registration", description = "Validates username and email uniqueness, generates 6-digit OTP and sends it via email")
    public ResponseEntity<ApiResponse<Void>> sendRegistrationOtp(@Valid @RequestBody SendOtpRequest request) {
        authService.sendRegistrationOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Mã xác thực OTP đã được gửi đến email của bạn", null));
    }

    @PostMapping("/verify-registration-otp")
    @Operation(summary = "Verify OTP code and create operative account", description = "Verifies 6-digit OTP, creates new account and sets HttpOnly JWT cookies")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyRegistrationOtp(@Valid @RequestBody VerifyOtpRegisterRequest request) {
        AuthResponse response = authService.verifyAndRegister(request);

        ResponseCookie accessCookie = jwtService.generateAccessTokenCookie(response.getAccessToken());
        ResponseCookie refreshCookie = jwtService.generateRefreshTokenCookie(response.getRefreshToken());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success("Đăng ký tài khoản thành công!", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with credentials", description = "Authenticates by email/username & password, returning access and refresh JWTs and setting HttpOnly cookies")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        ResponseCookie accessCookie = jwtService.generateAccessTokenCookie(response.getAccessToken());
        ResponseCookie refreshCookie = jwtService.generateRefreshTokenCookie(response.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success("Login successful", response));
    }

    @PostMapping({"/refresh-token", "/refresh"})
    @Operation(summary = "Refresh expired access token", description = "Rotates refresh token and returns a new access/refresh token pair in body and HttpOnly cookies")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = JwtService.REFRESH_TOKEN_COOKIE_NAME, required = false) String cookieRefreshToken) {
        String tokenStr = (request != null && StringUtils.hasText(request.getRefreshToken()))
                ? request.getRefreshToken()
                : cookieRefreshToken;

        if (!StringUtils.hasText(tokenStr)) {
            throw new BadRequestException("Refresh token is required in request body or cookie");
        }

        AuthResponse response = authService.refreshToken(new RefreshTokenRequest(tokenStr));
        ResponseCookie accessCookie = jwtService.generateAccessTokenCookie(response.getAccessToken());
        ResponseCookie refreshCookie = jwtService.generateRefreshTokenCookie(response.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out user", description = "Revokes the provided refresh token session and clears HttpOnly cookies")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = JwtService.REFRESH_TOKEN_COOKIE_NAME, required = false) String cookieRefreshToken) {
        String tokenToRevoke = (request != null && StringUtils.hasText(request.getRefreshToken()))
                ? request.getRefreshToken()
                : cookieRefreshToken;

        if (StringUtils.hasText(tokenToRevoke)) {
            authService.logout(tokenToRevoke);
        }

        ResponseCookie cleanAccess = jwtService.cleanAccessTokenCookie();
        ResponseCookie cleanRefresh = jwtService.cleanRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanAccess.toString())
                .header(HttpHeaders.SET_COOKIE, cleanRefresh.toString())
                .body(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get current authenticated gamer profile", description = "Retrieves profile info for currently logged in user based on Bearer token")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        UserResponse response = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved", response));
    }

    @PutMapping("/profile")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update gamer profile avatar", description = "Updates avatarUrl or profile info for current authenticated user")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        String avatarUrl = body != null ? body.get("avatarUrl") : null;
        UserResponse response = authService.updateProfile(userDetails.getUsername(), avatarUrl);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }
}
