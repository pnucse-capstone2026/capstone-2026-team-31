package com.ai_got_this.market_data_service.service;

import com.ai_got_this.market_data_service.dto.AlpacaBarMessage;
import com.ai_got_this.market_data_service.dto.MarketData;
import com.ai_got_this.market_data_service.dto.MarketTick;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MarketDataComputationServiceTest {
    @Test
    void publishesNothingUntilTheMacdLineAndEveryOtherFieldAreAvailable() {
        MarketDataPublisherService publisher = mock(MarketDataPublisherService.class);
        MarketDataComputationService service = new MarketDataComputationService(publisher);

        for (int close = 1; close <= 25; close++) {
            service.processBar(bar("AAPL", close));
        }

        verifyNoInteractions(publisher);
        Optional<MarketData> warmingUp = service.getLatestMarketData("AAPL");
        assertEquals(25.0, warmingUp.orElseThrow().getClose());
        assertNull(warmingUp.orElseThrow().getMacd());

        service.processBar(bar("AAPL", 26));

        ArgumentCaptor<MarketTick> tick = ArgumentCaptor.forClass(MarketTick.class);
        verify(publisher).publishMarketData(eq("market-data-exchange"), tick.capture(), eq("market-data"));

        MarketData data = tick.getValue().bar();
        assertEquals("AAPL", tick.getValue().ticker());
        assertEquals(26.0, data.getClose());
        assertEquals(0.04, data.getReturnPercentage(), 0.0000001);
        assertEquals(24.0, data.getMovingAverage5(), 0.0000001);
        assertEquals(16.5, data.getMovingAverage20(), 0.0000001);
        assertEquals(100.0, data.getRsi14(), 0.0000001);
        assertNotNull(data.getVolatility5());
        assertNotNull(data.getMacd());
        assertComplete(data);
    }

    @Test
    void maintainsIndependentRollingStateForEachTickerAndPublishesEachCompleteUpdate() {
        MarketDataPublisherService publisher = mock(MarketDataPublisherService.class);
        MarketDataComputationService service = new MarketDataComputationService(publisher);

        for (int close = 1; close <= 26; close++) {
            service.processBar(bar("AAPL", close));
        }
        service.processBar(bar("MSFT", 300));
        service.processBar(bar("AAPL", 27));

        verify(publisher, times(2)).publishMarketData(eq("market-data-exchange"), any(MarketTick.class), eq("market-data"));
        assertEquals(27.0, service.getLatestMarketData("AAPL").orElseThrow().getClose());
        assertEquals(300.0, service.getLatestMarketData("MSFT").orElseThrow().getClose());
        assertNull(service.getLatestMarketData("MSFT").orElseThrow().getMacd());
        assertFalse(service.getLatestMarketData("UNKNOWN").isPresent());
    }

    private static AlpacaBarMessage bar(String symbol, double close) {
        AlpacaBarMessage bar = new AlpacaBarMessage();
        bar.setSymbol(symbol);
        bar.setOpen(close - 0.5);
        bar.setHigh(close + 0.5);
        bar.setLow(close - 0.75);
        bar.setClose(close);
        bar.setVolume(100L);
        bar.setTimestamp(Instant.parse("2026-01-01T00:00:00Z").plusSeconds((long) close * 60).toString());
        return bar;
    }

    private static void assertComplete(MarketData data) {
        assertNotNull(data.getOpen());
        assertNotNull(data.getHigh());
        assertNotNull(data.getLow());
        assertNotNull(data.getClose());
        assertNotNull(data.getVolume());
        assertNotNull(data.getReturnPercentage());
        assertNotNull(data.getMovingAverage5());
        assertNotNull(data.getMovingAverage20());
        assertNotNull(data.getVolatility5());
        assertNotNull(data.getRsi14());
        assertNotNull(data.getMacd());
    }
}
