package com.ai_got_this.account_service.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AlpacaPropertiesTest {

    @Test
    void bindsAllConfiguredAlpacaProperties() throws IOException {
        Binder binder = new Binder(new MapConfigurationPropertySource(applicationProperties()));
        AlpacaProperties alpacaProperties = binder.bind("alpaca", AlpacaProperties.class)
                .orElseThrow(() -> new IllegalStateException("alpaca properties were not bound"));
        AlpacaAPIProperties alpacaAPIProperties = binder.bind("alpaca.api", AlpacaAPIProperties.class)
                .orElseThrow(() -> new IllegalStateException("alpaca.api properties were not bound"));

        assertNotNull(alpacaProperties.reconciliationInterval());
        assertNotNull(alpacaAPIProperties.key());
        assertNotNull(alpacaAPIProperties.secret());
        assertNotNull(alpacaAPIProperties.paperTradingBaseUrl());
    }

    private Map<String, String> applicationProperties() throws IOException {
        InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties");
        assertNotNull(input);

        Properties properties = new Properties();
        try (input) {
            properties.load(input);
        }

        Map<String, String> values = new HashMap<>();
        properties.forEach((key, value) -> values.put(key.toString(), value.toString()));
        return values;
    }
}
