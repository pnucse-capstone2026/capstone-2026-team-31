package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.service.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    record Error(String code, String message, UUID correlationId, Instant timestamp) {}

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<Void> notAcceptable(HttpMediaTypeNotAcceptableException exception, HttpServletRequest request) {
        log.warn(
                "No acceptable response representation: method={}, path={}, accept={}, userAgent={}, "
                        + "remoteAddress={}, forwardedFor={}, requestId={}, supportedMediaTypes={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getHeader(HttpHeaders.ACCEPT),
                request.getHeader(HttpHeaders.USER_AGENT),
                request.getRemoteAddr(),
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Request-ID"),
                exception.getSupportedMediaTypes()
        );
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Error> api(ApiException e) {
        return ResponseEntity
                .status(e.status)
                .body(new Error(e.code, e.getMessage(), UUID.randomUUID(), Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> other(Exception e) {
        return ResponseEntity
                .status(500)
                .body(new Error("INTERNAL_ERROR", "Unexpected service error.", UUID.randomUUID(), Instant.now()));
    }
}
