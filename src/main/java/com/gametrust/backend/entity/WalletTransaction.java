package com.gametrust.backend.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.Instant;

@Document(collection = "wallet_transactions")
public class WalletTransaction {

    @Id
    private String id;

    @Indexed(unique = true)
    private long orderCode;

    @Indexed
    private String userId;

    private String username;

    private double amountUsd;

    private int amountVnd;

    private String description;

    private String method = "QR";

    private String status = "PENDING"; // PENDING, SUCCESS, CANCELLED, FAILED

    private String paymentLinkId;

    private String qrCode;

    private String checkoutUrl;

    // Banking audit fields
    private String reference;
    private String counterAccountNumber;
    private String counterAccountName;
    private String counterAccountBankName;

    @Field("createdAt")
    private Instant createdAt = Instant.now();

    @Field("paidAt")
    private Instant paidAt;

    public WalletTransaction() {
    }

    public WalletTransaction(long orderCode, String userId, String username, double amountUsd, int amountVnd, String description) {
        this.orderCode = orderCode;
        this.userId = userId;
        this.username = username;
        this.amountUsd = amountUsd;
        this.amountVnd = amountVnd;
        this.description = description;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(long orderCode) {
        this.orderCode = orderCode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public double getAmountUsd() {
        return amountUsd;
    }

    public void setAmountUsd(double amountUsd) {
        this.amountUsd = amountUsd;
    }

    public int getAmountVnd() {
        return amountVnd;
    }

    public void setAmountVnd(int amountVnd) {
        this.amountVnd = amountVnd;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPaymentLinkId() {
        return paymentLinkId;
    }

    public void setPaymentLinkId(String paymentLinkId) {
        this.paymentLinkId = paymentLinkId;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getCounterAccountNumber() {
        return counterAccountNumber;
    }

    public void setCounterAccountNumber(String counterAccountNumber) {
        this.counterAccountNumber = counterAccountNumber;
    }

    public String getCounterAccountName() {
        return counterAccountName;
    }

    public void setCounterAccountName(String counterAccountName) {
        this.counterAccountName = counterAccountName;
    }

    public String getCounterAccountBankName() {
        return counterAccountBankName;
    }

    public void setCounterAccountBankName(String counterAccountBankName) {
        this.counterAccountBankName = counterAccountBankName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }
}
