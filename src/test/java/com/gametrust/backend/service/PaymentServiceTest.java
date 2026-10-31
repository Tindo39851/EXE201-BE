package com.gametrust.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gametrust.backend.dto.payment.CreatePaymentRequest;
import com.gametrust.backend.entity.WalletTransaction;
import com.gametrust.backend.exception.BadRequestException;
import com.gametrust.backend.exception.UnauthorizedException;
import com.gametrust.backend.repository.UserRepository;
import com.gametrust.backend.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import vn.payos.PayOS;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PayOS payOS;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private Environment environment;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                payOS,
                transactionRepository,
                userRepository,
                mongoTemplate,
                new ObjectMapper(),
                environment
        );
    }

    @Test
    @DisplayName("Should reject deposit amount less than 10,000 VND (10k)")
    void shouldRejectDepositAmountLessThanTenThousandVnd() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmountVnd(5000L); // 5k < 10k

        assertThrows(BadRequestException.class, () ->
                paymentService.createPaymentLink(request, "user123", "User")
        );
    }

    @Test
    @DisplayName("Should reject deposit amount exceeding 50,000,000 VND limit")
    void shouldRejectDepositAmountExceedingLimit() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmountVnd(60000000L);

        assertThrows(BadRequestException.class, () ->
                paymentService.createPaymentLink(request, "user123", "User")
        );
    }

    @Test
    @DisplayName("Should reject creation when pending orders limit reached (Anti-Spam)")
    void shouldRejectWhenPendingOrdersLimitReached() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setAmountVnd(50000L);

        when(transactionRepository.countByUserIdAndStatusAndCreatedAtAfter(anyString(), eq("PENDING"), any(Instant.class)))
                .thenReturn(5L);

        assertThrows(BadRequestException.class, () ->
                paymentService.createPaymentLink(request, "user123", "User")
        );
    }

    @Test
    @DisplayName("Should not expose another user's payment status")
    void shouldRejectPaymentStatusForDifferentUser() {
        WalletTransaction transaction = new WalletTransaction(123456L, "owner-id", "Owner", 1.97, 50000, "GT123456");
        when(transactionRepository.findByOrderCode(123456L)).thenReturn(Optional.of(transaction));

        assertThrows(UnauthorizedException.class, () ->
                paymentService.checkPaymentStatus(123456L, "attacker-id", false)
        );
    }

    @Test
    @DisplayName("Should not let a user simulate success for another user's payment")
    void shouldRejectSimulationForDifferentUser() {
        WalletTransaction transaction = new WalletTransaction(654321L, "owner-id", "Owner", 1.97, 50000, "GT654321");
        when(environment.acceptsProfiles(any(org.springframework.core.env.Profiles.class))).thenReturn(false);
        when(environment.getProperty("PAYMENT_DEV_SIMULATION_ENABLED", Boolean.class)).thenReturn(true);
        when(transactionRepository.findByOrderCode(654321L)).thenReturn(Optional.of(transaction));

        assertThrows(UnauthorizedException.class, () ->
                paymentService.simulateSuccess(654321L, "attacker-id", false)
        );
    }

    @Test
    @DisplayName("Should reject payment simulation unless explicitly enabled")
    void shouldRejectSimulationWhenDisabled() {
        when(environment.acceptsProfiles(any(org.springframework.core.env.Profiles.class))).thenReturn(false);
        when(environment.getProperty("PAYMENT_DEV_SIMULATION_ENABLED", Boolean.class)).thenReturn(false);
        assertThrows(BadRequestException.class, () ->
                paymentService.simulateSuccess(654321L, "owner-id", false)
        );
    }
}
