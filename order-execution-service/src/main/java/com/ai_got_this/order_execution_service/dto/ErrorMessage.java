package com.ai_got_this.order_execution_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class ErrorMessage {
    private String action;
    private ErrorData data;

    @Data
    public static class ErrorData {
        private String errorMessage;
    }
}
