package com.ai_got_this.news_ingestion_service.service;

import com.ai_got_this.news_ingestion_service.dto.NewsHeadline;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewsPublisherTest {
    @Test
    void publishesTheOriginalHeadlineToTheRequestedExchangeAndRoutingKey() {
        RecordingRabbitTemplate rabbitTemplate = new RecordingRabbitTemplate();
        NewsPublisher publisher = new NewsPublisher(rabbitTemplate);
        NewsHeadline headline = NewsHeadline.builder().ticker("NVDA").headline("Earnings rise").build();
        publisher.publishNews("financial-news-exchange", headline, "financial-news");
        assertEquals("financial-news-exchange", rabbitTemplate.exchange);
        assertEquals("financial-news", rabbitTemplate.routingKey);
        assertEquals(headline, rabbitTemplate.message);
    }

    private static final class RecordingRabbitTemplate extends RabbitTemplate {
        private String exchange;
        private String routingKey;
        private Object message;
        @Override public void convertAndSend(String exchange, String routingKey, Object message) {
            this.exchange = exchange;
            this.routingKey = routingKey;
            this.message = message;
        }
    }
}
