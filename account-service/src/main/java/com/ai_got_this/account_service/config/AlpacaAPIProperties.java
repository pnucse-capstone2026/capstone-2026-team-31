package com.ai_got_this.account_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alpaca.api")
public record AlpacaAPIProperties(String key, String secret, String paperTradingBaseUrl) {}
