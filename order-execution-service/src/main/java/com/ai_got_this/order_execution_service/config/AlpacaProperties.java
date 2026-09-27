package com.ai_got_this.order_execution_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("alpaca.api")
@Data
public class AlpacaProperties {
    private String streamUrl;
    private String subscriptionMessage;
    private String orderEndpoint;
    private String paperTradingBaseUrl;
    private String key;
    private String secret;
}
