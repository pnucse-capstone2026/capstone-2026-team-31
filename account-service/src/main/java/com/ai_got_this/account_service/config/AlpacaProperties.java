package com.ai_got_this.account_service.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alpaca")
public record AlpacaProperties(Duration reconciliationInterval) {}
