package com.ai_got_this.pnl_service.domain;

public class InsufficientLongPositionException extends RuntimeException {
    public InsufficientLongPositionException(String message) {
        super(message);
    }
}
