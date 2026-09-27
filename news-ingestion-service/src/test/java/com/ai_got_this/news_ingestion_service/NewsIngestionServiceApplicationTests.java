package com.ai_got_this.news_ingestion_service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Deliberately does not start a Spring application context: the production
 * context auto-starts a real WebSocketConnectionManager from application.properties.
 * Boundary components are covered as isolated unit tests instead.
 */
class NewsIngestionServiceApplicationTests {

    @Test
    void applicationClassCanBeLoadedWithoutStartingExternalInfrastructure() {
        assertDoesNotThrow(() -> Class.forName(NewsIngestionServiceApplication.class.getName()));
    }
}
