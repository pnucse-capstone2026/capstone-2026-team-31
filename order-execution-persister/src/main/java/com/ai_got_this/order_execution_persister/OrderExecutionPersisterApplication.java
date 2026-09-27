package com.ai_got_this.order_execution_persister;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrderExecutionPersisterApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderExecutionPersisterApplication.class, args);
    }

}
