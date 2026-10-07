package com.gametrust.backend.service.impl;

import com.gametrust.backend.dto.auth.*;
import com.gametrust.backend.entity.EmailVerification;
import com.gametrust.backend.entity.RefreshToken;
import com.gametrust.backend.entity.Role;
import com.gametrust.backend.entity.User;
import com.gametrust.backend.exception.BadRequestException;
import com.gametrust.backend.exception.ResourceNotFoundException;
import com.gametrust.backend.exception.UnauthorizedException;
import com.gametrust.backend.repository.EmailVerificationRepository;
import com.gametrust.backend.repository.RefreshTokenRepository;
import com.gametrust.backend.repository.UserRepository;
import com.gametrust.backend.security.JwtService;
import com.gametrust.backend.security.UserPrincipal;
import com.gametrust.backend.service.AuthService;
import com.gametrust.backend.service.EmailService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            EmailVerificationRepository emailVerificationRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
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
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenStr = request.getRefreshToken();

        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (!refreshToken.isValid()) {
            refreshTokenRepository.delete(refreshToken);
            throw new UnauthorizedException("Refresh token has expired or been revoked");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Refresh token user no longer exists"));
        if (!user.isActive()) {
            throw new UnauthorizedException("Account is inactive");
        }

        // Token rotation: revoke old refresh token and issue a fresh pair
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return generateAuthResponse(user);
    }

    @Override
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
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return UserResponse.fromUser(user);
    }

    @Override
    public UserResponse updateProfile(String username, String avatarUrl) {
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (avatarUrl != null && !avatarUrl.isBlank()) {
            user.setAvatarUrl(avatarUrl.trim());
        }
        user.touch();
        userRepository.save(user);
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
        RefreshToken refreshToken = new RefreshToken(user.getId(), refreshTokenString, expiresAt);
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                refreshTokenString,
                jwtService.getExpiration(),
                UserResponse.fromUser(user)
        );
    }

    @Override
    public void sendRegistrationOtp(SendOtpRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        String cleanUsername = request.getUsername().trim();

        if (userRepository.existsByEmail(cleanEmail) || userRepository.existsByUsername(cleanUsername)) {
            throw new BadRequestException("Thông tin đăng ký (email hoặc username) đã được sử dụng. Vui lòng kiểm tra lại.");
        }

        // Rate limit: Kiểm tra xem mã OTP gần nhất có được gửi trong vòng 60 giây qua không
        java.util.Optional<EmailVerification> existingVerification = emailVerificationRepository
                .findTopByEmailOrderByCreatedAtDesc(cleanEmail);
        if (existingVerification.isPresent()) {
            EmailVerification prev = existingVerification.get();
            if (prev.getLastSentAt() != null && Instant.now().isBefore(prev.getLastSentAt().plusSeconds(60))) {
                long waitSeconds = 60 - java.time.Duration.between(prev.getLastSentAt(), Instant.now()).toSeconds();
                throw new BadRequestException("Vui lòng đợi " + Math.max(1, waitSeconds) + " giây trước khi yêu cầu mã OTP mới.");
            }
        }

        // Sinh mã OTP 6 chữ số ngẫu nhiên
        String otp = String.format("%06d", new Random().nextInt(1_000_000));
        Instant expiresAt = Instant.now().plusSeconds(300); // 5 phút

        // Xóa mã OTP cũ của email này nếu có
        emailVerificationRepository.deleteByEmail(cleanEmail);

        // Lưu mã OTP mới vào MongoDB
        emailVerificationRepository.save(new EmailVerification(cleanEmail, otp, expiresAt));

        // Gửi email chứa OTP
        emailService.sendOtpEmail(cleanEmail, cleanUsername, otp);
    }

    @Override
    public AuthResponse verifyAndRegister(VerifyOtpRegisterRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        String cleanUsername = request.getUsername().trim();

        if (userRepository.existsByEmail(cleanEmail)) {
            throw new BadRequestException("Email đã được sử dụng bởi một tài khoản khác");
        }

        if (userRepository.existsByUsername(cleanUsername)) {
            throw new BadRequestException("Username đã tồn tại, vui lòng chọn tên khác");
        }

        EmailVerification verification = emailVerificationRepository
                .findTopByEmailOrderByCreatedAtDesc(cleanEmail)
                .orElseThrow(() -> new BadRequestException("Mã OTP không tồn tại hoặc đã hết hạn. Vui lòng bấm gửi lại mã."));

        if (verification.getExpiresAt().isBefore(Instant.now())) {
            emailVerificationRepository.deleteByEmail(cleanEmail);
            throw new BadRequestException("Mã OTP đã hết hạn (chỉ có hiệu lực trong 5 phút). Vui lòng gửi lại mã mới.");
        }

        // Brute-force protection: giới hạn tối đa 5 lần thử sai
        if (!verification.getOtp().equals(request.getOtp().trim())) {
            int attempts = verification.getFailedAttempts() + 1;
            verification.setFailedAttempts(attempts);
            if (attempts >= 5) {
                emailVerificationRepository.deleteByEmail(cleanEmail);
                throw new BadRequestException("Bạn đã nhập sai mã OTP quá 5 lần. Mã OTP đã bị hủy vì lý do bảo mật. Vui lòng bấm gửi lại mã mới.");
            }
            emailVerificationRepository.save(verification);
            throw new BadRequestException("Mã OTP không chính xác. Bạn còn " + (5 - attempts) + " lần thử.");
        }

        // OTP hợp lệ -> Xóa OTP
        emailVerificationRepository.deleteByEmail(cleanEmail);

        // Tạo tài khoản User chính thức
        User user = new User(
                cleanUsername,
                cleanEmail,
                passwordEncoder.encode(request.getPassword()),
                Role.MEMBER
        );

        User savedUser = userRepository.save(user);
        return generateAuthResponse(savedUser);
    }
}
