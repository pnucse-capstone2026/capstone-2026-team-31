package com.ai_got_this.order_execution_persister.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties("alpaca.api")
@Validated
@Data
public class AlpacaProperties {
    @NotBlank
    private String paperTradingBaseUrl;
    @NotBlank
    private String key;
    @NotBlank
    private String secret;
}
