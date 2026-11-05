package com.gametrust.backend.service;

import com.gametrust.backend.entity.Role;
import com.gametrust.backend.entity.User;
import com.gametrust.backend.exception.PremiumRequiredException;
import com.gametrust.backend.repository.UserRepository;
import com.gametrust.backend.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PremiumAccessServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PremiumAccessService premiumAccessService;

    @Test
    void activePremiumMemberCanUseAi() {
        User user = user("premium", Role.MEMBER);
        user.setPremium(true);
        user.setPremiumExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        UserPrincipal principal = UserPrincipal.create(user);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> premiumAccessService.requireAiAccess(principal));
    }

    @Test
    void standardMemberCannotUseAi() {
        User user = user("member", Role.MEMBER);
        UserPrincipal principal = UserPrincipal.create(user);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThrows(PremiumRequiredException.class,
                () -> premiumAccessService.requireAiAccess(principal));
    }

    @Test
    void expiredPremiumMemberCannotUseAi() {
        User user = user("expired", Role.MEMBER);
        user.setPremium(true);
        user.setPremiumExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        UserPrincipal principal = UserPrincipal.create(user);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThrows(PremiumRequiredException.class,
                () -> premiumAccessService.requireAiAccess(principal));
    }

    @Test
    void adminKeepsOperationalAiAccess() {
        User user = user("admin", Role.ADMIN);
        UserPrincipal principal = UserPrincipal.create(user);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> premiumAccessService.requireAiAccess(principal));
    }

    private User user(String username, Role role) {
        User user = new User(username, username + "@gametrust.local", "encoded", role);
        user.setId(username + "-id");
        return user;
    }
}
