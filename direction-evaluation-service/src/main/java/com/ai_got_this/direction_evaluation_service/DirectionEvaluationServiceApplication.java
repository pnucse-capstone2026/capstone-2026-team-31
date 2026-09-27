package com.ai_got_this.direction_evaluation_service;

import com.ai_got_this.direction_evaluation_service.config.DirectionEvaluationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DirectionEvaluationProperties.class)
public class DirectionEvaluationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DirectionEvaluationServiceApplication.class, args);
	}

}
