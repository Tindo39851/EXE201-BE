package com.gametrust.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        boolean isProd = environment.acceptsProfiles(Profiles.of("prod", "production"));

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

        // 3. Rate Limiting: Prevent spamming pending orders (only enforce on production)
        String effectiveUserId = userId;
        if (isProd) {
            Instant fifteenMinutesAgo = Instant.now().minus(15, ChronoUnit.MINUTES);
            long pendingCount = transactionRepository.countByUserIdAndStatusAndCreatedAtAfter(effectiveUserId, "PENDING", fifteenMinutesAgo);
            if (pendingCount >= MAX_PENDING_ORDERS_LIMIT) {
                throw new BadRequestException("Bạn đang có " + pendingCount + " giao dịch đang chờ xử lý. Vui lòng hoàn thành giao dịch hiện có hoặc đợi 15 phút để tạo lệnh mới.");
            }
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
            log.error("[PAYMENT_ERROR] Failed to communicate with PayOS gateway: {}", ex.getMessage(), ex);
            throw new BadRequestException("Không thể tạo liên kết thanh toán qua cổng PayOS. Vui lòng thử lại sau: " + ex.getMessage());
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

            // 2. Verify banking transaction response code: "00" = SUCCESS
            if (!"00".equals(webhookData.getCode())) {
                log.warn("[PAYMENT_WARNING] PayOS webhook received non-success code {} for orderCode {}",
                        webhookData.getCode(), webhookData.getOrderCode());
                return false;
            }

            long orderCode = webhookData.getOrderCode();
            long paidAmount = webhookData.getAmount() != null ? webhookData.getAmount() : 0L;

            // 3. Locate transaction in database
            Optional<WalletTransaction> optionalTx = transactionRepository.findByOrderCode(orderCode);
            if (optionalTx.isEmpty()) {
                log.warn("[PAYMENT_WARNING] OrderCode {} not found in local system", orderCode);
                return false;
            }

            WalletTransaction tx = optionalTx.get();

            // 4. AMOUNT TAMPERING CHECK
            if (paidAmount < tx.getAmountVnd()) {
                log.error("[CRITICAL_FRAUD_ALERT] Amount mismatch for orderCode {}: expected {} VND, but actual paid was {} VND! Transaction marked FAILED.",
                        orderCode, tx.getAmountVnd(), paidAmount);
                tx.setStatus("FAILED");
                transactionRepository.save(tx);
                return false;
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

    public boolean simulateSuccess(long orderCode) {
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new BadRequestException("Cửa sau mô phỏng bị vô hiệu hóa hoàn toàn trên môi trường Production vì lý do an toàn ngân hàng.");
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
        if (!isAdmin && callerUserId != null && !callerUserId.equals(tx.getUserId())) {
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

        // 5. PayOS still PENDING — in DEV mode, credit optimistically for testing
        boolean isProd = environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (!isProd) {
            log.info("[DEV_MANUAL_CREDIT] PayOS unconfirmed but DEV mode — crediting {} VND to user {} for ref '{}'",
                    tx.getAmountVnd(), tx.getUserId(), normalizedRef);

            Query query = new Query(Criteria.where("orderCode").is(tx.getOrderCode()).and("status").is("PENDING"));
            Update update = new Update()
                    .set("status", "SUCCESS")
                    .set("paidAt", Instant.now())
                    .set("reference", "MANUAL_UNCONFIRMED_" + normalizedRef);

            WalletTransaction updatedTx = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true), WalletTransaction.class);

            if (updatedTx != null) {
                ensureUserBalanceCredited(updatedTx);
            }

            double newBalance = getBalance(tx.getUserId());
            return new PaymentStatusResponse(tx.getOrderCode(), "SUCCESS", tx.getAmountVnd(), true,
                    newBalance, "[DEV] Giao dịch được xác nhận thủ công thành công.");
        }

        return new PaymentStatusResponse(tx.getOrderCode(), "PENDING", tx.getAmountVnd(), false,
                getBalance(tx.getUserId()),
                "Giao dịch đang chờ xác nhận từ ngân hàng. Vui lòng thử lại sau ít phút.");
    }

    public List<WalletTransaction> getTransactions(String userId) {
        if (userId != null && !userId.isBlank() && !"guest".equalsIgnoreCase(userId)) {
            return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            return Collections.emptyList();
        }
        return transactionRepository.findTop10ByOrderByCreatedAtDesc();
    }

    public double getBalance(String userId) {
        if (userId != null && !userId.isBlank() && !"guest".equalsIgnoreCase(userId)) {
            return userRepository.findById(userId).map(User::getWalletBalance).orElse(150000.0);
        }
        if (!environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            return userRepository.findByUsername("toàn").map(User::getWalletBalance).orElse(170000.0);
        }
        return 150000.0;
    }
}
