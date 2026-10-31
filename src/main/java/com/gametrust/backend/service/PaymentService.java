package com.gametrust.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gametrust.backend.dto.auth.UserResponse;
import com.gametrust.backend.dto.payment.CreatePaymentRequest;
import com.gametrust.backend.dto.payment.PaymentResponse;
import com.gametrust.backend.entity.User;
import com.gametrust.backend.entity.WalletTransaction;
import com.gametrust.backend.exception.BadRequestException;
import com.gametrust.backend.exception.UnauthorizedException;
import com.gametrust.backend.repository.UserRepository;
import com.gametrust.backend.repository.WalletTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import com.gametrust.backend.dto.payment.PaymentStatusResponse;
import com.gametrust.backend.exception.ResourceNotFoundException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.v2.paymentRequests.Transaction;
import vn.payos.model.webhooks.WebhookData;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    public static final int USD_TO_VND_RATE = 25400;
    public static final long MIN_DEPOSIT_VND = 10000L;       // 10k VND
    public static final long MAX_DEPOSIT_VND = 50000000L;    // 50,000,000 VND (50 triệu)
    public static final int MAX_PENDING_ORDERS_LIMIT = 5;

    private final PayOS payOS;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public PaymentService(PayOS payOS,
                          WalletTransactionRepository transactionRepository,
                          UserRepository userRepository,
                          MongoTemplate mongoTemplate,
                          ObjectMapper objectMapper,
                          Environment environment) {
        this.payOS = payOS;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    public PaymentResponse createPaymentLink(CreatePaymentRequest request, String userId, String username) {
        // 1. Authentication Security: Block unauthenticated / guest orders on all environments
        if (userId == null || userId.isBlank() || "guest".equalsIgnoreCase(userId)) {
            throw new UnauthorizedException("Vui lòng đăng nhập tài khoản trước khi thực hiện giao dịch nạp tiền.");
        }

        // 2. Strict Input Validation on Amount (in VND)
        long amountVnd;
        if (request.getAmountVnd() != null && request.getAmountVnd() > 0) {
            amountVnd = request.getAmountVnd();
        } else if (request.getAmountUsd() != null && request.getAmountUsd() > 0) {
            amountVnd = (long) Math.round(request.getAmountUsd() * USD_TO_VND_RATE);
        } else {
            amountVnd = 50000L; // default 50k VND
        }

        if (amountVnd < MIN_DEPOSIT_VND || amountVnd > MAX_DEPOSIT_VND) {
            throw new BadRequestException("Số tiền nạp không hợp lệ. Số tiền phải từ 10,000 VNĐ (10k) đến 50,000,000 VNĐ.");
        }

        double amountUsd = Math.round((amountVnd / (double) USD_TO_VND_RATE) * 100.0) / 100.0;

        // 3. Rate Limiting: Prevent spamming pending orders in every environment.
        String effectiveUserId = userId;
        Instant fifteenMinutesAgo = Instant.now().minus(15, ChronoUnit.MINUTES);
        long pendingCount = transactionRepository.countByUserIdAndStatusAndCreatedAtAfter(effectiveUserId, "PENDING", fifteenMinutesAgo);
        if (pendingCount >= MAX_PENDING_ORDERS_LIMIT) {
            throw new BadRequestException("Bạn đang có " + pendingCount + " giao dịch đang chờ xử lý. Vui lòng hoàn thành giao dịch hiện có hoặc đợi 15 phút để tạo lệnh mới.");
        }

        // Ensure unique orderCode AND unique description (GT + orderCode suffix must not collide)
        long orderCode;
        String description;
        int attempts = 0;
        do {
            orderCode = (System.currentTimeMillis() % 800000000L) + (long) (java.util.concurrent.ThreadLocalRandom.current().nextInt(10000, 99999));
            description = "GT" + orderCode;
            if (description.length() > 25) description = description.substring(0, 25);
            attempts++;
        } while (attempts < 10 && (
                transactionRepository.findByOrderCode(orderCode).isPresent() ||
                transactionRepository.findByDescription(description).isPresent()
        ));

        // 4. Safe Return/Cancel URLs
        String frontendBaseUrl = environment.getProperty("app.frontend.url", "http://localhost:3000");
        String returnUrl = frontendBaseUrl + "/wallet?status=success&orderCode=" + orderCode;
        String cancelUrl = frontendBaseUrl + "/wallet?status=cancelled&orderCode=" + orderCode;

        PaymentLinkItem item = PaymentLinkItem.builder()
                .name("Nạp Ví GameTrust")
                .quantity(1)
                .price(amountVnd)
                .build();

        CreatePaymentLinkRequest paymentData = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amountVnd)
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .items(Collections.singletonList(item))
                .build();

        PaymentResponse response = new PaymentResponse();
        response.setOrderCode(orderCode);
        response.setAmountUsd(amountUsd);
        response.setAmountVnd((int) amountVnd);
        response.setTransferNote(description);
        response.setAccountNumber("0838939851");
        response.setAccountName("DO TRONG TIN");
        response.setBankName("MBBank");
        response.setStatus("PENDING");

        String vietQrUrl = "https://img.vietqr.io/image/970422-0838939851-compact2.png?amount=" + amountVnd
                + "&addInfo=" + description + "&accountName=DO%20TRONG%20TIN";

        String payosClientId = environment.getProperty("payos.client-id", "");
        String payosChecksum = environment.getProperty("payos.checksum-key", "");
        if (payosClientId.isBlank() || payosChecksum.isBlank() || payosClientId.startsWith("your_")) {
            log.warn("[PAYMENT_FALLBACK] PayOS keys not configured. Using direct VietQR for orderCode {}", orderCode);
            response.setQrImageUrl(vietQrUrl);
            WalletTransaction transaction = new WalletTransaction(
                    orderCode, effectiveUserId, username != null ? username : "Operative",
                    amountUsd, (int) amountVnd, description);
            transactionRepository.save(transaction);
            return response;
        }

        try {
            CreatePaymentLinkResponse checkoutData = payOS.paymentRequests().create(paymentData);
            response.setCheckoutUrl(checkoutData.getCheckoutUrl());
            response.setQrCode(checkoutData.getQrCode());
            response.setQrImageUrl(checkoutData.getQrCode() != null
                    ? "https://img.vietqr.io/image/970422-0838939851-compact2.png?amount=" + amountVnd + "&addInfo=" + description + "&accountName=DO%20TRONG%20TIN"
                    : null);

            WalletTransaction transaction = new WalletTransaction(
                    orderCode,
                    effectiveUserId,
                    username != null ? username : "Operative",
                    amountUsd,
                    (int) amountVnd,
                    description
            );
            transaction.setPaymentLinkId(checkoutData.getPaymentLinkId());
            transaction.setCheckoutUrl(checkoutData.getCheckoutUrl());
            transaction.setQrCode(checkoutData.getQrCode());
            transactionRepository.save(transaction);

            log.info("[PAYMENT_CREATED] Created PayOS orderCode {} for user {} ({} VND / ${} USD)",
                    orderCode, effectiveUserId, amountVnd, amountUsd);

        } catch (Exception ex) {
            log.error("[PAYMENT_ERROR] Failed to communicate with PayOS gateway: {}. Falling back to VietQR direct.", ex.getMessage(), ex);
            response.setQrImageUrl(vietQrUrl);
            WalletTransaction transaction = new WalletTransaction(
                    orderCode,
                    effectiveUserId,
                    username != null ? username : "Operative",
                    amountUsd,
                    (int) amountVnd,
                    description
            );
            transactionRepository.save(transaction);
            return response;
        }

        return response;
    }

    public boolean handleWebhook(JsonNode webhookPayload) {
        try {
            // 1. Verify cryptographic signature via PayOS Checksum Key
            WebhookData webhookData = payOS.webhooks().verify(webhookPayload);
            if (webhookData == null) {
                log.warn("[PAYMENT_SECURITY_ALERT] Invalid PayOS webhook signature or payload tampering detected!");
                return false;
            }

            // A validly signed non-success event is terminal; acknowledge it to prevent endless retries.
            if (!"00".equals(webhookData.getCode())) {
                log.info("[PAYMENT_IGNORED] PayOS webhook received non-success code {} for orderCode {}",
                        webhookData.getCode(), webhookData.getOrderCode());
                return true;
            }

            long orderCode = webhookData.getOrderCode();
            long paidAmount = webhookData.getAmount() != null ? webhookData.getAmount() : 0L;

            // 3. Locate transaction in database
            Optional<WalletTransaction> optionalTx = transactionRepository.findByOrderCode(orderCode);
            if (optionalTx.isEmpty()) {
                // PayOS uses orderCode 123 for its webhook URL verification probe. This code
                // cannot be generated by GameTrust; acknowledge it without crediting any user.
                if (orderCode == 123L) {
                    log.info("[PAYMENT_WEBHOOK_PROBE] Acknowledged PayOS verification event");
                    return true;
                }
                // Keep retrying unknown real orders in case the transaction save was delayed.
                log.error("[PAYMENT_ORPHAN_EVENT] Signed successful webhook has unknown orderCode {}", orderCode);
                return false;
            }

            WalletTransaction tx = optionalTx.get();

            // 4. AMOUNT TAMPERING CHECK
            if (paidAmount < tx.getAmountVnd()) {
                log.error("[CRITICAL_FRAUD_ALERT] Amount mismatch for orderCode {}: expected {} VND, but actual paid was {} VND! Transaction marked FAILED.",
                        orderCode, tx.getAmountVnd(), paidAmount);
                tx.setStatus("FAILED");
                transactionRepository.save(tx);
                // Authentic terminal rejection: acknowledge so PayOS does not retry indefinitely.
                return true;
            }

            // 5. ATOMIC IDEMPOTENT UPDATE
            Query query = new Query(Criteria.where("orderCode").is(orderCode).and("status").is("PENDING"));
            Update update = new Update()
                    .set("status", "SUCCESS")
                    .set("paidAt", Instant.now())
                    .set("reference", webhookData.getReference())
                    .set("counterAccountNumber", webhookData.getCounterAccountNumber())
                    .set("counterAccountName", webhookData.getCounterAccountName())
                    .set("counterAccountBankName", webhookData.getCounterAccountBankName());

            WalletTransaction updatedTx = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    WalletTransaction.class
            );

            if (updatedTx == null) {
                // Idempotent retry: if transaction was marked SUCCESS but credit was interrupted, complete it now
                optionalTx.ifPresent(this::ensureUserBalanceCredited);
                log.info("[PAYMENT_IDEMPOTENT] OrderCode {} was already processed. Verified credit status.", orderCode);
                return true;
            }

            // 6. RELIABLE BALANCE INCREMENT WITH RECOVERY STATE
            ensureUserBalanceCredited(updatedTx);
            return true;

        } catch (Exception ex) {
            log.error("[PAYMENT_SECURITY_ERROR] Error validating or processing PayOS webhook: {}", ex.getMessage(), ex);
            return false;
        }
    }

    public synchronized void ensureUserBalanceCredited(WalletTransaction tx) {
        if (tx == null || tx.isCredited() || tx.getUserId() == null || "guest".equalsIgnoreCase(tx.getUserId())) {
            return;
        }

        try {
            Query userQuery = new Query(Criteria.where("id").is(tx.getUserId()));
            Update balanceUpdate = new Update().inc("walletBalance", (double) tx.getAmountVnd());
            mongoTemplate.updateFirst(userQuery, balanceUpdate, User.class);

            Query txQuery = new Query(Criteria.where("orderCode").is(tx.getOrderCode()));
            Update creditUpdate = new Update().set("credited", true);
            mongoTemplate.updateFirst(txQuery, creditUpdate, WalletTransaction.class);

            tx.setCredited(true);
            log.info("[PAYMENT_CREDIT_COMPLETED] Reliably credited {} VND to user {} for orderCode {}",
                    tx.getAmountVnd(), tx.getUserId(), tx.getOrderCode());
        } catch (Exception ex) {
            log.error("[PAYMENT_CREDIT_ERROR] Failed to credit balance for user {} orderCode {}: {}",
                    tx.getUserId(), tx.getOrderCode(), ex.getMessage(), ex);
            throw ex;
        }
    }

    public boolean simulateSuccess(long orderCode, String callerUserId, boolean isAdmin) {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))
                || !Boolean.TRUE.equals(environment.getProperty("PAYMENT_DEV_SIMULATION_ENABLED", Boolean.class))) {
            throw new BadRequestException("Payment simulation is disabled. Enable it explicitly only in a local development environment.");
        }

        WalletTransaction existing = transactionRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Giao dịch không tồn tại với mã đơn " + orderCode));
        if (!isAdmin && (callerUserId == null || !callerUserId.equals(existing.getUserId()))) {
            throw new UnauthorizedException("Bạn không có quyền mô phỏng giao dịch của tài khoản khác.");
        }

        Query query = new Query(Criteria.where("orderCode").is(orderCode).and("status").is("PENDING"));
        Update update = new Update()
                .set("status", "SUCCESS")
                .set("paidAt", Instant.now())
                .set("reference", "DEV_SIMULATION_" + orderCode);

        WalletTransaction updatedTx = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                WalletTransaction.class
        );

        if (updatedTx != null) {
            ensureUserBalanceCredited(updatedTx);
            return true;
        }
        return false;
    }

    public PaymentStatusResponse checkPaymentStatus(long orderCode) {
        return checkPaymentStatus(orderCode, null, true);
    }

    public PaymentStatusResponse checkPaymentStatus(long orderCode, String callerUserId, boolean isAdmin) {
        Optional<WalletTransaction> optionalTx = transactionRepository.findByOrderCode(orderCode);
        if (optionalTx.isEmpty()) {
            throw new ResourceNotFoundException("Giao dịch không tồn tại với mã đơn " + orderCode);
        }
        WalletTransaction tx = optionalTx.get();

        // Ownership enforcement: Only owner or admin can inspect this transaction
        if (!isAdmin && (callerUserId == null || !callerUserId.equals(tx.getUserId()))) {
            throw new UnauthorizedException("Bạn không có quyền truy cập thông tin giao dịch này.");
        }

        // 1. If already marked SUCCESS, ensure balance was credited, then return immediately
        if ("SUCCESS".equalsIgnoreCase(tx.getStatus())) {
            ensureUserBalanceCredited(tx);
            double currentBalance = getBalance(tx.getUserId());
            return new PaymentStatusResponse(orderCode, "SUCCESS", tx.getAmountVnd(), true, currentBalance, "Giao dịch đã được thanh toán thành công!");
        }

        // 2. Query PayOS API directly to check if bank transfer was received
        try {
            PaymentLink paymentLink = payOS.paymentRequests().get(orderCode);
            if (paymentLink != null && paymentLink.getStatus() == PaymentLinkStatus.PAID) {
                long paidAmount = paymentLink.getAmountPaid() != null ? paymentLink.getAmountPaid() : 0L;

                // Amount tampering check
                if (paidAmount < tx.getAmountVnd()) {
                    log.error("[FRAUD_ALERT] Order {} paid amount {} is less than expected {} VND", orderCode, paidAmount, tx.getAmountVnd());
                    tx.setStatus("FAILED");
                    transactionRepository.save(tx);
                    return new PaymentStatusResponse(orderCode, "FAILED", tx.getAmountVnd(), false, getBalance(tx.getUserId()), "Số tiền chuyển chưa đủ.");
                }

                String ref = null;
                String counterAcc = null;
                String counterName = null;
                String counterBank = null;
                if (paymentLink.getTransactions() != null && !paymentLink.getTransactions().isEmpty()) {
                    Transaction t = paymentLink.getTransactions().get(0);
                    ref = t.getReference();
                    counterAcc = t.getCounterAccountNumber();
                    counterName = t.getCounterAccountName();
                    counterBank = t.getCounterAccountBankName();
                }

                // Atomic update
                Query query = new Query(Criteria.where("orderCode").is(orderCode).and("status").is("PENDING"));
                Update update = new Update()
                        .set("status", "SUCCESS")
                        .set("paidAt", Instant.now())
                        .set("reference", ref != null ? ref : "PAYOS_SYNC_" + orderCode)
                        .set("counterAccountNumber", counterAcc)
                        .set("counterAccountName", counterName)
                        .set("counterAccountBankName", counterBank);

                WalletTransaction updatedTx = mongoTemplate.findAndModify(
                        query,
                        update,
                        FindAndModifyOptions.options().returnNew(true),
                        WalletTransaction.class
                );

                if (updatedTx != null) {
                    ensureUserBalanceCredited(updatedTx);
                    log.info("[PAYMENT_SYNC_SUCCESS] Synchronized paid order {} for user {}: +{} VND",
                            orderCode, updatedTx.getUserId(), updatedTx.getAmountVnd());
                    double newBalance = getBalance(updatedTx.getUserId());
                    return new PaymentStatusResponse(orderCode, "SUCCESS", tx.getAmountVnd(), true, newBalance, "Thanh toán thành công!");
                } else {
                    // Concurrent request already processed → re-fetch actual status and ensure credited
                    WalletTransaction latest = transactionRepository.findByOrderCode(orderCode)
                            .orElse(tx);
                    ensureUserBalanceCredited(latest);
                    boolean isPaid = "SUCCESS".equalsIgnoreCase(latest.getStatus());
                    double newBalance = getBalance(tx.getUserId());
                    return new PaymentStatusResponse(orderCode, latest.getStatus(), tx.getAmountVnd(), isPaid, newBalance,
                            isPaid ? "Thanh toán thành công!" : "Giao dịch đang được xử lý.");
                }
            } else if (paymentLink != null && paymentLink.getStatus() == PaymentLinkStatus.CANCELLED) {
                tx.setStatus("CANCELLED");
                transactionRepository.save(tx);
                return new PaymentStatusResponse(orderCode, "CANCELLED", tx.getAmountVnd(), false, getBalance(tx.getUserId()), "Giao dịch đã bị huỷ.");
            }
        } catch (Exception ex) {
            log.warn("[PAYMENT_SYNC_WARNING] Could not query PayOS for order {}: {}", orderCode, ex.getMessage());
        }

        return new PaymentStatusResponse(orderCode, tx.getStatus(), tx.getAmountVnd(), false, getBalance(tx.getUserId()), "Đang chờ thanh toán");
    }

    /**
     * Manual reconciliation: user enters their bank transfer description (e.g. "GT891725")
     * → we find the matching PENDING order → query PayOS → if PAID, credit balance.
     * Fallback: if PayOS can't confirm yet but order exists, mark for review and credit optimistically in DEV.
     */
    public PaymentStatusResponse checkByTransferRef(String ref, String userId) {
        if (userId == null || userId.isBlank() || "guest".equalsIgnoreCase(userId)) {
            throw new UnauthorizedException("Vui lòng đăng nhập tài khoản trước khi kiểm tra mã chuyển khoản.");
        }
        if (ref == null || ref.isBlank()) {
            throw new BadRequestException("Vui lòng nhập mã giao dịch (ví dụ: GT891725).");
        }

        // Normalize: strip whitespace, uppercase
        String normalizedRef = ref.trim().toUpperCase();

        // Extract GT code if embedded in bank noise (e.g., "O5CH7KOBPRJ2-GT229693" -> "GT229693")
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("GT\\d+").matcher(normalizedRef);
        final String searchRef = matcher.find() ? matcher.group() : normalizedRef;

        // 1. Try exact description match (e.g. "GT891725" or "GT229693")
        Optional<WalletTransaction> optionalTx = transactionRepository.findByDescription(searchRef);

        // 2. If not found by exact match, try parsing the numeric suffix to reconstruct orderCode
        //    GT prefix + last 6 digits of orderCode → search by orderCode variants
        if (optionalTx.isEmpty() && searchRef.startsWith("GT")) {
            try {
                long suffix = Long.parseLong(searchRef.substring(2)); // e.g. 891725
                // The orderCode = some_prefix * 1000000 + suffix OR suffix itself
                // Try finding by description pattern in all user's PENDING transactions
                if (userId != null && !userId.isBlank() && !"guest".equalsIgnoreCase(userId)) {
                    List<WalletTransaction> userPending = transactionRepository
                            .findByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING");
                    optionalTx = userPending.stream()
                            .filter(t -> t.getDescription() != null
                                    && (t.getDescription().equalsIgnoreCase(searchRef)
                                    || (t.getOrderCode() % 1000000L) == suffix))
                            .findFirst();
                }
            } catch (NumberFormatException ignored) {
                // Not a valid GTxxxxxx format
            }
        }

        if (optionalTx.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy giao dịch với mã '" + normalizedRef + "'. " +
                    "Vui lòng kiểm tra lại mã nội dung chuyển khoản (bắt đầu bằng GT...).");
        }

        WalletTransaction tx = optionalTx.get();

        // Ownership enforcement: Cannot query transactions of other users
        if (!userId.equals(tx.getUserId())) {
            throw new UnauthorizedException("Bạn không có quyền tra cứu giao dịch của tài khoản khác.");
        }

        // 3. Already SUCCESS → ensure credited and return current status
        if ("SUCCESS".equalsIgnoreCase(tx.getStatus())) {
            ensureUserBalanceCredited(tx);
            double currentBalance = getBalance(tx.getUserId());
            return new PaymentStatusResponse(tx.getOrderCode(), "SUCCESS", tx.getAmountVnd(), true,
                    currentBalance, "Giao dịch này đã được xử lý thành công trước đó.");
        }

        // 4. Query PayOS to verify payment
        try {
            PaymentLink paymentLink = payOS.paymentRequests().get(tx.getOrderCode());
            if (paymentLink != null && paymentLink.getStatus() == PaymentLinkStatus.PAID) {
                long paidAmount = paymentLink.getAmountPaid() != null ? paymentLink.getAmountPaid() : 0L;

                if (paidAmount < tx.getAmountVnd()) {
                    log.error("[FRAUD_ALERT] Manual check: order {} paid {} VND < expected {} VND",
                            tx.getOrderCode(), paidAmount, tx.getAmountVnd());
                    return new PaymentStatusResponse(tx.getOrderCode(), "UNDERPAID", tx.getAmountVnd(), false,
                            getBalance(tx.getUserId()), "Số tiền chuyển khoản chưa đủ yêu cầu.");
                }

                String ref2 = null;
                String counterAcc = null, counterName = null, counterBank = null;
                if (paymentLink.getTransactions() != null && !paymentLink.getTransactions().isEmpty()) {
                    Transaction t = paymentLink.getTransactions().get(0);
                    ref2 = t.getReference();
                    counterAcc = t.getCounterAccountNumber();
                    counterName = t.getCounterAccountName();
                    counterBank = t.getCounterAccountBankName();
                }

                // Idempotent credit
                Query query = new Query(Criteria.where("orderCode").is(tx.getOrderCode()).and("status").is("PENDING"));
                Update update = new Update()
                        .set("status", "SUCCESS")
                        .set("paidAt", Instant.now())
                        .set("reference", ref2 != null ? ref2 : "MANUAL_REF_" + normalizedRef)
                        .set("counterAccountNumber", counterAcc)
                        .set("counterAccountName", counterName)
                        .set("counterAccountBankName", counterBank);

                WalletTransaction updatedTx = mongoTemplate.findAndModify(query, update,
                        FindAndModifyOptions.options().returnNew(true), WalletTransaction.class);

                if (updatedTx != null) {
                    ensureUserBalanceCredited(updatedTx);
                    log.info("[MANUAL_RECONCILE_SUCCESS] Credited {} VND to user {} via manual ref '{}'",
                            updatedTx.getAmountVnd(), updatedTx.getUserId(), normalizedRef);
                }

                double newBalance = getBalance(tx.getUserId());
                return new PaymentStatusResponse(tx.getOrderCode(), "SUCCESS", tx.getAmountVnd(), true,
                        newBalance, "Xác nhận thành công! Số dư đã được cập nhật.");
            }
        } catch (Exception ex) {
            log.warn("[MANUAL_RECONCILE_WARNING] PayOS query failed for order {}: {}", tx.getOrderCode(), ex.getMessage());
        }

        // Never credit a wallet based only on a user-entered bank transfer reference.
        // If PayOS has not confirmed the order, leave it pending for webhook/status retry.
        return new PaymentStatusResponse(tx.getOrderCode(), "PENDING", tx.getAmountVnd(), false,
                getBalance(tx.getUserId()),
                "Giao dịch đang chờ xác nhận từ ngân hàng. Vui lòng thử lại sau ít phút.");
    }

    public List<WalletTransaction> getTransactions(String userId) {
        if (userId == null || userId.isBlank() || "guest".equalsIgnoreCase(userId)) {
            throw new UnauthorizedException("Vui lòng đăng nhập để xem lịch sử giao dịch.");
        }
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public double getBalance(String userId) {
        if (userId == null || userId.isBlank() || "guest".equalsIgnoreCase(userId)) {
            throw new UnauthorizedException("Vui lòng đăng nhập để xem số dư ví.");
        }
        return userRepository.findById(userId)
                .map(User::getWalletBalance)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản ví."));
    }

    public synchronized UserResponse subscribePremium(String userId, String username) {
        if (userId == null || userId.isBlank() || "guest".equalsIgnoreCase(userId)) {
            throw new UnauthorizedException("Vui lòng đăng nhập để nâng cấp tài khoản Premium.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản: " + userId));

        double currentBal = user.getWalletBalance();
        long costVnd = 49000L;
        if (currentBal < costVnd) {
            throw new BadRequestException("Số dư ví không đủ 49.000 VNĐ. Vui lòng nạp thêm PayOS để kích hoạt gói Premium!");
        }

        user.setWalletBalance(currentBal - costVnd);
        user.setPremium(true);
        user.setPremiumTier("PREMIUM");
        Instant now = Instant.now();
        Instant expiresAt = (user.getPremiumExpiresAt() != null && user.getPremiumExpiresAt().isAfter(now))
                ? user.getPremiumExpiresAt().plus(30, ChronoUnit.DAYS)
                : now.plus(30, ChronoUnit.DAYS);
        user.setPremiumExpiresAt(expiresAt);
        user.touch();
        userRepository.save(user);

        long orderCode = Long.parseLong(String.valueOf(System.currentTimeMillis()).substring(5));
        WalletTransaction tx = new WalletTransaction(
                orderCode,
                user.getId(),
                user.getUsername(),
                Math.round((costVnd / (double) USD_TO_VND_RATE) * 100.0) / 100.0,
                (int) costVnd,
                "Đăng ký GameTrust Premium 30 ngày (Mở khóa AI Room Recommendation)"
        );
        tx.setStatus("SUCCESS");
        tx.setMethod("WALLET");
        tx.setCredited(true);
        tx.setPaidAt(now);
        transactionRepository.save(tx);

        log.info("[PREMIUM_ACTIVATED] User {} ({}) successfully subscribed to Premium 30 days. New balance: {}",
                user.getUsername(), user.getId(), user.getWalletBalance());

        return UserResponse.fromUser(user);
    }
}
