package com.ai_got_this.account_service.service;

import com.ai_got_this.account_service.client.AlpacaTradingClient;
import com.ai_got_this.account_service.domain.PositionProjection;
import com.ai_got_this.account_service.domain.TradingAccountProjection;
import com.ai_got_this.account_service.domain.Watchlist;
import com.ai_got_this.account_service.domain.WatchlistEntry;
import com.ai_got_this.account_service.repository.PositionProjectionRepository;
import com.ai_got_this.account_service.repository.TradingAccountProjectionRepository;
import com.ai_got_this.account_service.repository.WatchlistEntryRepository;
import com.ai_got_this.account_service.repository.WatchlistRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountReconciliationService {
    private final AlpacaTradingClient broker;
    private final TradingAccountProjectionRepository accounts;
    private final PositionProjectionRepository positions;
    private final WatchlistRepository watchlists;
    private final WatchlistEntryRepository watchlistEntries;

    public AccountReconciliationService(
            AlpacaTradingClient broker,
            TradingAccountProjectionRepository accounts,
            PositionProjectionRepository positions,
            WatchlistRepository watchlists,
            WatchlistEntryRepository watchlistEntries
    ) {
        this.broker = broker;
        this.accounts = accounts;
        this.positions = positions;
        this.watchlists = watchlists;
        this.watchlistEntries = watchlistEntries;
    }

    @Transactional
    public TradingAccountProjection reconcile() {
        AlpacaTradingClient.BrokerAccount source = broker.account();
        if (source == null || source.id() == null)
            throw new IllegalStateException("Alpaca did not return an account.");

        TradingAccountProjection target = accounts.findAll()
                .stream()
                .findFirst()
                .orElseGet(TradingAccountProjection::new);

        target.setProjectionId(target.getProjectionId() == null ? UUID.randomUUID() : target.getProjectionId());
        target.setAlpacaAccountId(source.id());
        target.setAccountNumber(source.account_number());
        target.setStatus(source.status());
        target.setCurrency(source.currency());
        target.setCash(source.cash());
        target.setBuyingPower(source.buying_power());
        target.setNonMarginableBuyingPower(source.non_marginable_buying_power());
        target.setPortfolioValue(source.portfolio_value());
        target.setEquity(source.equity());
        target.setLastEquity(source.last_equity());
        target.setLongMarketValue(source.long_market_value());
        target.setAccountBlocked(source.account_blocked());
        target.setTradingBlocked(source.trading_blocked());
        target.setTradeSuspendedByUser(source.trade_suspended_by_user());
        target.setShortingEnabled(source.shorting_enabled());
        target.setLastSyncedAt(Instant.now());
        accounts.save(target);

        reconcilePositionsBySymbolUpsert(broker.positions());
        reconcileWatchlistsByAlpacaId(broker.watchlists());
        return target;
    }

    /**
     * The broker is authoritative for its watchlists. Local entry activity is
     * intentionally retained because it is application-only state and is not
     * represented by the broker API.
     */
    void reconcileWatchlistsByAlpacaId(List<AlpacaTradingClient.BrokerWatchlist> brokerWatchlists) {
        Map<UUID, AlpacaTradingClient.BrokerWatchlist> remoteById = new HashMap<>();
        for (AlpacaTradingClient.BrokerWatchlist remote : brokerWatchlists) {
            if (remote != null && remote.id() != null) {
                remoteById.put(remote.id(), remote);
            }
        }

        Map<UUID, Watchlist> localByAlpacaId = new HashMap<>();
        for (Watchlist local : watchlists.findAll()) {
            localByAlpacaId.put(local.getAlpacaWatchlistId(), local);
        }

        for (Watchlist local : localByAlpacaId.values()) {
            if (!remoteById.containsKey(local.getAlpacaWatchlistId())) {
                watchlistEntries.deleteAll(watchlistEntries.findByWatchlistId(local.getWatchlistId()));
                watchlists.delete(local);
            }
        }

        for (AlpacaTradingClient.BrokerWatchlist remote : remoteById.values()) {
            Watchlist local = localByAlpacaId.get(remote.id());
            if (local == null) {
                local = new Watchlist();
                local.setWatchlistId(UUID.randomUUID());
                local.setAlpacaWatchlistId(remote.id());
            }
            local.setName(remote.name());
            local.setSyncStatus(Watchlist.SyncStatus.SYNCED);
            local.setLastSyncErrorCode(null);
            watchlists.save(local);

            reconcileWatchlistEntries(local, remote.assets());
        }
    }

    private void reconcileWatchlistEntries(
            Watchlist watchlist,
            List<AlpacaTradingClient.BrokerAsset> brokerAssets
    ) {
        Set<String> desiredTickers = new HashSet<>();
        if (brokerAssets != null) {
            for (AlpacaTradingClient.BrokerAsset asset : brokerAssets) {
                if (asset != null && asset.symbol() != null && !asset.symbol().isBlank()) {
                    desiredTickers.add(asset.symbol().trim().toUpperCase(java.util.Locale.ROOT));
                }
            }
        }

        List<WatchlistEntry> localEntries = watchlistEntries.findByWatchlistId(watchlist.getWatchlistId());
        Set<String> localTickers = new HashSet<>();
        for (WatchlistEntry localEntry : localEntries) {
            localTickers.add(localEntry.getTicker());
            if (!desiredTickers.contains(localEntry.getTicker())) {
                watchlistEntries.delete(localEntry);
            }
        }

        for (String ticker : desiredTickers) {
            if (!localTickers.contains(ticker)) {
                WatchlistEntry entry = new WatchlistEntry();
                entry.setEntryId(UUID.randomUUID());
                entry.setWatchlistId(watchlist.getWatchlistId());
                entry.setTicker(ticker);
                watchlistEntries.save(entry);
            }
        }
    }

    void reconcilePositionsBySymbolUpsert(List<AlpacaTradingClient.BrokerPosition> brokerPositions) {
        Set<String> activeSymbols = new HashSet<>();

        for (AlpacaTradingClient.BrokerPosition brokerPosition : brokerPositions) {
            activeSymbols.add(brokerPosition.symbol());

            PositionProjection projection = positions.findBySymbol(brokerPosition.symbol())
                    .orElseGet(PositionProjection::new);

            projection.setPositionId(projection.getPositionId() == null ? UUID.randomUUID() : projection.getPositionId());
            projection.setAlpacaAssetId(brokerPosition.asset_id());
            projection.setSymbol(brokerPosition.symbol());
            projection.setQuantity(brokerPosition.qty());
            projection.setAverageEntryPrice(brokerPosition.avg_entry_price());
            projection.setMarketValue(brokerPosition.market_value());
            projection.setCostBasis(brokerPosition.cost_basis());
            projection.setCurrentPrice(brokerPosition.current_price());
            projection.setUnrealizedPnl(brokerPosition.unrealized_pl());
            projection.setSide(brokerPosition.side());
            projection.setUpdatedAt(Instant.now());
            positions.save(projection);
        }

        if (activeSymbols.isEmpty()) {
            positions.deleteAll();
            return;
        }

        positions.deleteBySymbolNotIn(activeSymbols);
    }

    void reconcilePositionsByReplacement(List<AlpacaTradingClient.BrokerPosition> brokerPositions) {
        positions.deleteAll();
        for (AlpacaTradingClient.BrokerPosition position : brokerPositions) {
            PositionProjection projection = new PositionProjection();
            projection.setPositionId(UUID.randomUUID());
            projection.setAlpacaAssetId(position.asset_id());
            projection.setSymbol(position.symbol());
            projection.setQuantity(position.qty());
            projection.setAverageEntryPrice(position.avg_entry_price());
            projection.setMarketValue(position.market_value());
            projection.setCostBasis(position.cost_basis());
            projection.setCurrentPrice(position.current_price());
            projection.setUnrealizedPnl(position.unrealized_pl());
            projection.setSide(position.side());
            projection.setUpdatedAt(Instant.now());
            positions.save(projection);
        }
    }
}
