package com.ai_got_this.account_service;

import com.ai_got_this.account_service.config.AlpacaAPIProperties;
import com.ai_got_this.account_service.config.AlpacaProperties;
import com.ai_got_this.account_service.config.OrderExecutionPersisterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
        AlpacaProperties.class,
        AlpacaAPIProperties.class,
        OrderExecutionPersisterProperties.class
})
@EnableScheduling
public class AccountServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
