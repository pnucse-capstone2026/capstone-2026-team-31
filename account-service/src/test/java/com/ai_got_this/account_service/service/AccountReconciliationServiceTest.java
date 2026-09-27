package com.ai_got_this.account_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai_got_this.account_service.client.AlpacaTradingClient;
import com.ai_got_this.account_service.domain.Watchlist;
import com.ai_got_this.account_service.domain.WatchlistEntry;
import com.ai_got_this.account_service.repository.PositionProjectionRepository;
import com.ai_got_this.account_service.repository.TradingAccountProjectionRepository;
import com.ai_got_this.account_service.repository.WatchlistEntryRepository;
import com.ai_got_this.account_service.repository.WatchlistRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccountReconciliationServiceTest {

    @Test
    void createsLocalWatchlistAndEntriesFromBroker() {
        AlpacaTradingClient broker = mock(AlpacaTradingClient.class);
        TradingAccountProjectionRepository accounts = mock(TradingAccountProjectionRepository.class);
        PositionProjectionRepository positions = mock(PositionProjectionRepository.class);
        WatchlistRepository watchlists = mock(WatchlistRepository.class);
        WatchlistEntryRepository entries = mock(WatchlistEntryRepository.class);
        AccountReconciliationService service = new AccountReconciliationService(
                broker, accounts, positions, watchlists, entries
        );

        UUID brokerWatchlistId = UUID.randomUUID();
        when(broker.account()).thenReturn(account());
        when(broker.positions()).thenReturn(List.of());
        when(broker.watchlists()).thenReturn(List.of(new AlpacaTradingClient.BrokerWatchlist(
                brokerWatchlistId,
                "Technology",
                List.of(new AlpacaTradingClient.BrokerAsset("AAPL"))
        )));
        when(accounts.findAll()).thenReturn(List.of());
        when(watchlists.findAll()).thenReturn(List.of());
        when(entries.findByWatchlistId(any())).thenReturn(List.of());

        service.reconcile();

        ArgumentCaptor<Watchlist> watchlistCaptor = ArgumentCaptor.forClass(Watchlist.class);
        org.mockito.Mockito.verify(watchlists).save(watchlistCaptor.capture());
        Watchlist savedWatchlist = watchlistCaptor.getValue();
        assertEquals(brokerWatchlistId, savedWatchlist.getAlpacaWatchlistId());
        assertEquals("Technology", savedWatchlist.getName());
        assertEquals(Watchlist.SyncStatus.SYNCED, savedWatchlist.getSyncStatus());

        ArgumentCaptor<WatchlistEntry> entryCaptor = ArgumentCaptor.forClass(WatchlistEntry.class);
        org.mockito.Mockito.verify(entries).save(entryCaptor.capture());
        assertEquals(savedWatchlist.getWatchlistId(), entryCaptor.getValue().getWatchlistId());
        assertEquals("AAPL", entryCaptor.getValue().getTicker());
    }

    @Test
    void removesLocalWatchlistThatNoLongerExistsAtBroker() {
        AlpacaTradingClient broker = mock(AlpacaTradingClient.class);
        TradingAccountProjectionRepository accounts = mock(TradingAccountProjectionRepository.class);
        PositionProjectionRepository positions = mock(PositionProjectionRepository.class);
        WatchlistRepository watchlists = mock(WatchlistRepository.class);
        WatchlistEntryRepository entries = mock(WatchlistEntryRepository.class);
        AccountReconciliationService service = new AccountReconciliationService(
                broker, accounts, positions, watchlists, entries
        );
        Watchlist stale = new Watchlist();
        stale.setWatchlistId(UUID.randomUUID());
        stale.setAlpacaWatchlistId(UUID.randomUUID());
        stale.setName("Stale list");
        WatchlistEntry staleEntry = new WatchlistEntry();
        staleEntry.setEntryId(UUID.randomUUID());
        staleEntry.setWatchlistId(stale.getWatchlistId());
        staleEntry.setTicker("AAPL");

        when(broker.account()).thenReturn(account());
        when(broker.positions()).thenReturn(List.of());
        when(broker.watchlists()).thenReturn(List.of());
        when(accounts.findAll()).thenReturn(List.of());
        when(watchlists.findAll()).thenReturn(List.of(stale));
        when(entries.findByWatchlistId(stale.getWatchlistId())).thenReturn(List.of(staleEntry));

        service.reconcile();

        org.mockito.Mockito.verify(entries).deleteAll(List.of(staleEntry));
        org.mockito.Mockito.verify(watchlists).delete(stale);
    }

    private AlpacaTradingClient.BrokerAccount account() {
        return new AlpacaTradingClient.BrokerAccount(
                UUID.randomUUID(), "PA123", "ACTIVE", "USD", BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                false, false, false, false
        );
    }
}
