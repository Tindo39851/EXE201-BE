package com.gametrust.backend.dto.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class CreatePaymentRequest {

    // Số tiền nạp bằng VNĐ (tối thiểu 10,000 VNĐ = 10k, tối đa 50,000,000 VNĐ)
    @Min(value = 10000, message = "Số tiền nạp tối thiểu là 10,000 VNĐ (10k)")
    @Max(value = 50000000, message = "Số tiền nạp tối đa là 50,000,000 VNĐ (50 triệu)")
    private Long amountVnd;

    // Tùy chọn số tiền USD (legacy support)
    private Double amountUsd;

    private String description;
    private String returnUrl;
    private String cancelUrl;

    public CreatePaymentRequest() {
    }

    public Long getAmountVnd() {
        return amountVnd;
    }

    public void setAmountVnd(Long amountVnd) {
        this.amountVnd = amountVnd;
    }

    public Double getAmountUsd() {
        return amountUsd;
    }

    public void setAmountUsd(Double amountUsd) {
        this.amountUsd = amountUsd;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }
}
