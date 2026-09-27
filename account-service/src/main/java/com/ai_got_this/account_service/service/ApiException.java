package com.ai_got_this.account_service.service;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    public final String code;
    public final HttpStatus status;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
