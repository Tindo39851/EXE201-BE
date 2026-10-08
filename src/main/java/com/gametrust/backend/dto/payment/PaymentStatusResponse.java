package com.gametrust.backend.dto.payment;

public class PaymentStatusResponse {
    private long orderCode;
    private String status;
    private int amountVnd;
    private boolean isPaid;
    private double balance;
    private String message;

    public PaymentStatusResponse() {
    }

    public PaymentStatusResponse(long orderCode, String status, int amountVnd, boolean isPaid, double balance, String message) {
        this.orderCode = orderCode;
        this.status = status;
        this.amountVnd = amountVnd;
        this.isPaid = isPaid;
        this.balance = balance;
        this.message = message;
    }

    public long getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(long orderCode) {
        this.orderCode = orderCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAmountVnd() {
        return amountVnd;
    }

    public void setAmountVnd(int amountVnd) {
        this.amountVnd = amountVnd;
    }

    public boolean isPaid() {
        return isPaid;
    }

    public void setPaid(boolean paid) {
        isPaid = paid;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
