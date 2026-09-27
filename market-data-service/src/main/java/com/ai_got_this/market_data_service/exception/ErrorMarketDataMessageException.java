package com.ai_got_this.market_data_service.exception;

public class ErrorMarketDataMessageException extends RuntimeException {
    public ErrorMarketDataMessageException(String message) {
        super(message);
    }
}
