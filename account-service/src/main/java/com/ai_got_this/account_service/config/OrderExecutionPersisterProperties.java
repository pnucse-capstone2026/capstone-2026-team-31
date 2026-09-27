package com.ai_got_this.account_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "order-execution-persister.internal")
public record OrderExecutionPersisterProperties(String baseUrl) {}
