package com.ai_got_this.market_data_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlpacaErrorMessage extends AlpacaMessage {
    private String msg;
    private Integer code;
}
