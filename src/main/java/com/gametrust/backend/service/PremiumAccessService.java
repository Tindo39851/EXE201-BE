package com.gametrust.backend.service;

import com.gametrust.backend.entity.Role;
import com.gametrust.backend.entity.User;
import com.gametrust.backend.exception.PremiumRequiredException;
import com.gametrust.backend.exception.UnauthorizedException;
import com.gametrust.backend.repository.UserRepository;
import com.gametrust.backend.security.UserPrincipal;
import org.springframework.stereotype.Service;

@Service
public class PremiumAccessService {

    private final UserRepository userRepository;

    public PremiumAccessService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireAiAccess(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để sử dụng tính năng AI.");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("Không tìm thấy tài khoản đang đăng nhập."));

        if (user.getRole() == Role.ADMIN || user.hasActivePremium()) {
            return user;
        }

        throw new PremiumRequiredException(
                "Tính năng AI chỉ dành cho tài khoản Premium. Vui lòng nâng cấp để tiếp tục."
        );
    }
}
