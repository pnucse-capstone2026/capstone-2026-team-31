package com.ai_got_this.market_data_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class AlpacaWebSocketReconnectConfig {
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService alpacaReconnectExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "alpaca-reconnect-");
            thread.setDaemon(false);
            return thread;
        });
    }
}
