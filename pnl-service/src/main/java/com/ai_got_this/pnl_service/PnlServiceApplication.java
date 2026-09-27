package com.ai_got_this.pnl_service;

import com.ai_got_this.pnl_service.config.PnlProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PnlProperties.class)
public class PnlServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PnlServiceApplication.class, args);
    }
}
