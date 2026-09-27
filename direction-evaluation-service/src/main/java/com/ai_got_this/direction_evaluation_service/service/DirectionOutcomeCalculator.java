package com.ai_got_this.direction_evaluation_service.service;

import com.ai_got_this.direction_evaluation_service.domain.Direction;
import com.ai_got_this.direction_evaluation_service.domain.MarketTickRecord;
import com.ai_got_this.direction_evaluation_service.domain.PredictionInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class DirectionOutcomeCalculator {
    public Outcome calculate(PredictionInput prediction, MarketTickRecord evaluationTick) {
        BigDecimal actualReturn = evaluationTick.getPrice().subtract(prediction.getLatestPrice())
                .divide(prediction.getLatestPrice(), 12, RoundingMode.HALF_UP);
        Direction actualDirection = directionFor(actualReturn);
        return new Outcome(actualReturn, actualDirection, actualDirection == prediction.getPredictedDirection());
    }

    private static Direction directionFor(BigDecimal actualReturn) {
        int sign = actualReturn.signum();
        return sign > 0 ? Direction.UP : sign < 0 ? Direction.DOWN : Direction.FLAT;
    }

    public record Outcome(BigDecimal actualReturn, Direction actualDirection, boolean correct) {
    }
}
