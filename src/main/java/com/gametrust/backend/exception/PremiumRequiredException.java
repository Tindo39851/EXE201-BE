package com.gametrust.backend.exception;

public class PremiumRequiredException extends RuntimeException {

    public PremiumRequiredException(String message) {
        super(message);
    }
}
