package com.ai_got_this.market_data_service.service;

import com.ai_got_this.market_data_service.dto.AlpacaBarMessage;
import com.ai_got_this.market_data_service.dto.MarketData;
import com.ai_got_this.market_data_service.dto.MarketTick;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Maintains the latest market-data feature vector and rolling indicator state for each ticker.
 * A vector is published only after every field in {@link MarketData} has a value.
 */
@Service
@Slf4j
public class MarketDataComputationService {
    private static final String EXCHANGE = "market-data-exchange";
    private static final String ROUTING_KEY = "market-data";
    private static final int MA_5_PERIOD = 5;
    private static final int MA_20_PERIOD = 20;
    private static final int VOLATILITY_PERIOD = 5;
    private static final int RSI_PERIOD = 14;
    private static final int MACD_FAST_PERIOD = 12;
    private static final int MACD_SLOW_PERIOD = 26;

    private final MarketDataPublisherService marketDataPublisherService;
    private final Map<String, SymbolState> statesByTicker = new HashMap<>();

    public MarketDataComputationService(MarketDataPublisherService marketDataPublisherService) {
        this.marketDataPublisherService = marketDataPublisherService;
    }

    /**
     * Incorporates one closed Alpaca bar. The method is synchronized because each new value
     * depends on the state produced by the previous bar for the same ticker.
     */
    public synchronized void processBar(AlpacaBarMessage bar) {
        if (!isUsable(bar)) {
            log.warn("Ignoring incomplete or invalid bar: {}", bar);
            return;
        }

        SymbolState state = statesByTicker.computeIfAbsent(bar.getSymbol(), ignored -> new SymbolState());
        MarketData marketData = state.marketData;
        marketData.setOpen(bar.getOpen());
        marketData.setHigh(bar.getHigh());
        marketData.setLow(bar.getLow());
        marketData.setClose(bar.getClose());
        marketData.setVolume(bar.getVolume());

        updateIndicators(state, bar.getClose());

        if (isComplete(marketData)) {
            Instant observedAt = parseTimestamp(bar.getTimestamp());
            if (observedAt == null) {
                log.warn("Ignoring bar without a valid source timestamp for ticker {}", bar.getSymbol());
                return;
            }
            marketDataPublisherService.publishMarketData(
                    EXCHANGE,
                    new MarketTick(
                            bar.getSymbol(),
                            copyOf(marketData),
                            stableEventId(bar.getSymbol(), observedAt),
                            observedAt.toString()),
                    ROUTING_KEY);
        }
    }

    /** Returns the latest in-memory vector for a ticker, including null warm-up fields if any. */
    public synchronized Optional<MarketData> getLatestMarketData(String ticker) {
        SymbolState state = statesByTicker.get(ticker);
        return state == null ? Optional.empty() : Optional.of(copyOf(state.marketData));
    }

    private void updateIndicators(SymbolState state, double close) {
        MarketData marketData = state.marketData;
        state.barCount++;

        addRollingValue(state.closes, close, MA_20_PERIOD);
        marketData.setMovingAverage5(averageOfLast(state.closes, MA_5_PERIOD));
        marketData.setMovingAverage20(averageOfLast(state.closes, MA_20_PERIOD));

        if (state.previousClose != null) {
            double fractionalReturn = (close - state.previousClose) / state.previousClose;
            marketData.setReturnPercentage(fractionalReturn);
            addRollingValue(state.returns, fractionalReturn, VOLATILITY_PERIOD);
            marketData.setVolatility5(sampleStandardDeviation(state.returns, VOLATILITY_PERIOD));
            updateRsi(state, close - state.previousClose);
        }
        state.previousClose = close;

        updateMacd(state, close);
        marketData.setMacd(state.fastEma == null || state.slowEma == null
                ? null
                : state.fastEma - state.slowEma);
    }

    private void updateRsi(SymbolState state, double priceChange) {
        double gain = Math.max(priceChange, 0.0);
        double loss = Math.max(-priceChange, 0.0);
        state.rsiChangeCount++;

        if (state.rsiChangeCount <= RSI_PERIOD) {
            state.initialGainTotal += gain;
            state.initialLossTotal += loss;
            if (state.rsiChangeCount == RSI_PERIOD) {
                state.averageGain = state.initialGainTotal / RSI_PERIOD;
                state.averageLoss = state.initialLossTotal / RSI_PERIOD;
            }
        }
        else {
            state.averageGain = ((state.averageGain * (RSI_PERIOD - 1)) + gain) / RSI_PERIOD;
            state.averageLoss = ((state.averageLoss * (RSI_PERIOD - 1)) + loss) / RSI_PERIOD;
        }

        if (state.rsiChangeCount >= RSI_PERIOD) {
            state.marketData.setRsi14(rsi(state.averageGain, state.averageLoss));
        }
    }

    private void updateMacd(SymbolState state, double close) {
        if (state.barCount <= MACD_FAST_PERIOD) {
            state.fastSeedTotal += close;
            if (state.barCount == MACD_FAST_PERIOD) {
                state.fastEma = state.fastSeedTotal / MACD_FAST_PERIOD;
            }
        }
        else {
            state.fastEma = ema(close, state.fastEma, MACD_FAST_PERIOD);
        }

        if (state.barCount <= MACD_SLOW_PERIOD) {
            state.slowSeedTotal += close;
            if (state.barCount == MACD_SLOW_PERIOD) {
                state.slowEma = state.slowSeedTotal / MACD_SLOW_PERIOD;
            }
        }
        else {
            state.slowEma = ema(close, state.slowEma, MACD_SLOW_PERIOD);
        }
    }

    private static double ema(double value, double previousEma, int period) {
        double multiplier = 2.0 / (period + 1.0);
        return (value * multiplier) + (previousEma * (1.0 - multiplier));
    }

    private static Double averageOfLast(Deque<Double> values, int period) {
        if (values.size() < period) {
            return null;
        }
        return values.stream().skip(values.size() - period).mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private static Double sampleStandardDeviation(Deque<Double> values, int period) {
        if (values.size() < period) {
            return null;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double squaredDifferenceTotal = values.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .sum();
        return Math.sqrt(squaredDifferenceTotal / (period - 1));
    }

    private static double rsi(double averageGain, double averageLoss) {
        if (averageLoss == 0.0 && averageGain == 0.0) {
            return 50.0;
        }
        if (averageLoss == 0.0) {
            return 100.0;
        }
        if (averageGain == 0.0) {
            return 0.0;
        }
        double relativeStrength = averageGain / averageLoss;
        return 100.0 - (100.0 / (1.0 + relativeStrength));
    }

    private static void addRollingValue(Deque<Double> values, double value, int capacity) {
        values.addLast(value);
        if (values.size() > capacity) {
            values.removeFirst();
        }
    }

    private static boolean isUsable(AlpacaBarMessage bar) {
        return bar != null
                && bar.getSymbol() != null && !bar.getSymbol().isBlank()
                && isFinitePositive(bar.getOpen())
                && isFinitePositive(bar.getHigh())
                && isFinitePositive(bar.getLow())
                && isFinitePositive(bar.getClose())
                && bar.getVolume() != null && bar.getVolume() >= 0
                && parseTimestamp(bar.getTimestamp()) != null;
    }

    private static Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String stableEventId(String ticker, Instant observedAt) {
        return UUID.nameUUIDFromBytes((ticker + "|" + observedAt).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static boolean isFinitePositive(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0;
    }

    private static boolean isComplete(MarketData marketData) {
        return marketData.getOpen() != null
                && marketData.getHigh() != null
                && marketData.getLow() != null
                && marketData.getClose() != null
                && marketData.getVolume() != null
                && marketData.getReturnPercentage() != null
                && marketData.getMovingAverage5() != null
                && marketData.getMovingAverage20() != null
                && marketData.getVolatility5() != null
                && marketData.getRsi14() != null
                && marketData.getMacd() != null;
    }

    private static MarketData copyOf(MarketData source) {
        return MarketData.builder()
                .open(source.getOpen())
                .high(source.getHigh())
                .low(source.getLow())
                .close(source.getClose())
                .volume(source.getVolume())
                .returnPercentage(source.getReturnPercentage())
                .movingAverage5(source.getMovingAverage5())
                .movingAverage20(source.getMovingAverage20())
                .volatility5(source.getVolatility5())
                .rsi14(source.getRsi14())
                .macd(source.getMacd())
                .build();
    }

    private static class SymbolState {
        private final MarketData marketData = MarketData.builder().build();
        private final Deque<Double> closes = new ArrayDeque<>();
        private final Deque<Double> returns = new ArrayDeque<>();
        private Double previousClose;
        private int barCount;
        private int rsiChangeCount;
        private double initialGainTotal;
        private double initialLossTotal;
        private double averageGain;
        private double averageLoss;
        private double fastSeedTotal;
        private double slowSeedTotal;
        private Double fastEma;
        private Double slowEma;
    }
}
