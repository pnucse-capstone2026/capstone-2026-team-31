package com.ai_got_this.direction_evaluation_service.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("measurement.direction-evaluation")
public class DirectionEvaluationProperties {
    private Duration horizon = Duration.ofHours(1);

    public Duration getHorizon() {
        return horizon;
    }

    public void setHorizon(Duration horizon) {
        this.horizon = horizon;
    }
}
