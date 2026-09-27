package com.ai_got_this.market_data_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("alpaca.api")
@Data
public class AlpacaProperties {
    private String key;
    private String secret;
    private String marketStreamUrl;
    private String subscriptionMessage;
}
