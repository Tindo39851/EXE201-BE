package com.gametrust.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gametrust.backend.dto.common.ApiResponse;
import com.gametrust.backend.dto.payment.CreatePaymentRequest;
import com.gametrust.backend.dto.payment.PaymentResponse;
import com.gametrust.backend.entity.WalletTransaction;
import com.gametrust.backend.security.UserPrincipal;
import com.gametrust.backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-payment-link")
    public ApiResponse<PaymentResponse> createPaymentLink(
            @Valid @RequestBody CreatePaymentRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        String userId = user != null ? user.getId() : "guest";
        String username = user != null ? user.getUsername() : "Operative";
        PaymentResponse response = paymentService.createPaymentLink(request, userId, username);
        return ApiResponse.success("Payment link created successfully", response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody JsonNode payload) {
        boolean success = paymentService.handleWebhook(payload);
        if (!success) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", 1,
                    "message", "Webhook processing failed — will retry",
                    "data", Map.of("success", false)
            ));
        }
        return ResponseEntity.ok(Map.of(
                "error", 0,
                "message", "Webhook processed",
                "data", Map.of("success", true)
        ));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<WalletTransaction>> getTransactions(@AuthenticationPrincipal UserPrincipal user) {
        String userId = user != null ? user.getId() : null;
        List<WalletTransaction> transactions = paymentService.getTransactions(userId);
        return ApiResponse.success("Transactions retrieved", transactions);
    }

    @GetMapping("/balance")
    public ApiResponse<Map<String, Object>> getBalance(@AuthenticationPrincipal UserPrincipal user) {
        String userId = user != null ? user.getId() : null;
        double balance = paymentService.getBalance(userId);
        return ApiResponse.success("Balance retrieved", Map.of("balance", balance));
    }

    @GetMapping("/check-status/{orderCode}")
    public ApiResponse<com.gametrust.backend.dto.payment.PaymentStatusResponse> checkStatus(@PathVariable long orderCode) {
        com.gametrust.backend.dto.payment.PaymentStatusResponse status = paymentService.checkPaymentStatus(orderCode);
        return ApiResponse.success("Payment status retrieved", status);
    }

    @PostMapping("/simulate-success/{orderCode}")
    public ApiResponse<Map<String, Object>> simulateSuccess(@PathVariable long orderCode) {
        boolean ok = paymentService.simulateSuccess(orderCode);
        return ApiResponse.success("Simulation completed", Map.of("success", ok, "orderCode", orderCode));
    }

    /**
     * Manual reconciliation endpoint: user enters their GT transfer code (e.g. "GT891725")
     * from their bank biên lai → system finds and credits the matching order.
     */
    @GetMapping("/check-by-ref")
    public ApiResponse<com.gametrust.backend.dto.payment.PaymentStatusResponse> checkByRef(
            @RequestParam String ref,
            @AuthenticationPrincipal UserPrincipal user) {
        String userId = user != null ? user.getId() : null;
        com.gametrust.backend.dto.payment.PaymentStatusResponse status = paymentService.checkByTransferRef(ref, userId);
        return ApiResponse.success("Transfer reference checked", status);
    }
}
