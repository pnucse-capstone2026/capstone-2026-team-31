package com.ai_got_this.account_service.web;

import com.ai_got_this.account_service.client.AlpacaTradingClient;
import com.ai_got_this.account_service.domain.Watchlist;
import com.ai_got_this.account_service.domain.WatchlistEntry;
import com.ai_got_this.account_service.repository.WatchlistEntryRepository;
import com.ai_got_this.account_service.repository.WatchlistRepository;
import com.ai_got_this.account_service.service.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {
    private static final Pattern TICKER = Pattern.compile("[A-Z][A-Z.]{0,15}");
    private final WatchlistRepository watchlists;
    private final WatchlistEntryRepository entries;
    private final AlpacaTradingClient broker;

    public WatchlistController(
            WatchlistRepository watchlists,
            WatchlistEntryRepository entries,
            AlpacaTradingClient broker
    ) {
        this.watchlists = watchlists;
        this.entries = entries;
        this.broker = broker;
    }

    @GetMapping
    public List<View> list() {
        return watchlists.findAllByOrderByNameAsc()
                .stream()
                .map(this::view)
                .toList();
    }

    @GetMapping("/active-tickers")
    public Set<String> active() {
        return new TreeSet<>(entries.findByWatchlistIdInAndIsActiveTrue(
                watchlists.findAll()
                        .stream()
                        .map(Watchlist::getWatchlistId)
                        .collect(java.util.stream.Collectors.toSet())
        ).stream().map(WatchlistEntry::getTicker).toList());
    }

    @GetMapping("/{id}")
    public View get(@PathVariable UUID id) {
        return view(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public View create(@RequestBody Write input) {
        String name = name(input.name());
        List<String> symbols = symbols(input.symbols());

        if (watchlists.findByName(name).isPresent())
            throw conflict("WATCHLIST_NAME_CONFLICT", "A watchlist with this name already exists.");

        AlpacaTradingClient.BrokerWatchlist remote = broker.createWatchlist(name, symbols);

        if (remote == null || remote.id() == null)
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ALPACA_INVALID_RESPONSE",
                    "Alpaca did not return a watchlist."
            );

        Watchlist item = new Watchlist();
        item.setWatchlistId(UUID.randomUUID());
        item.setAlpacaWatchlistId(remote.id());
        item.setName(name);
        watchlists.save(item);

        return new View(item, save(item, symbols));
    }

    @PutMapping("/{id}")
    @Transactional
    public View replace(@PathVariable UUID id, @RequestBody Write input) {
        Watchlist item = find(id);
        String name = name(input.name());
        List<String> symbols = symbols(input.symbols());

        watchlists.findByName(name)
                .filter(other -> !other.getWatchlistId().equals(id))
                .ifPresent(x -> {
                    throw conflict("WATCHLIST_NAME_CONFLICT", "A watchlist with this name already exists.");
                });

        broker.replaceWatchlist(item.getAlpacaWatchlistId(), name, symbols);
        item.setName(name);
        entries.deleteAll(entries.findByWatchlistId(id));

        return new View(item, save(item, symbols));
    }

    @PostMapping("/{id}/entries")
    @Transactional
    public View add(@PathVariable UUID id, @RequestBody EntryWrite body) {
        Watchlist item = find(id);
        String ticker = ticker(body.ticker());

        if (entries.findByWatchlistId(id).stream().anyMatch(x -> x.getTicker().equals(ticker)))
            throw conflict("WATCHLIST_ENTRY_EXISTS", "Ticker already exists.");

        broker.addWatchlistAsset(item.getAlpacaWatchlistId(), ticker);

        WatchlistEntry entry = new WatchlistEntry();
        entry.setEntryId(UUID.randomUUID());
        entry.setWatchlistId(id);
        entry.setTicker(ticker);
        entries.save(entry);

        return view(item);
    }

    @DeleteMapping("/{id}/entries/{ticker}")
    @Transactional
    public View remove(@PathVariable UUID id, @PathVariable String ticker) {
        Watchlist item = find(id);
        WatchlistEntry entry = entry(item, ticker(ticker));
        broker.removeWatchlistAsset(item.getAlpacaWatchlistId(), entry.getTicker());
        entries.delete(entry);

        return view(item);
    }

    @PatchMapping("/{id}/entries/{ticker}")
    public View active(@PathVariable UUID id, @PathVariable String ticker, @RequestBody ActiveWrite input) {
        Watchlist item = find(id);
        WatchlistEntry entry = entry(item, ticker(ticker));
        entry.setActive(input.isActive());
        entries.save(entry);

        return view(item);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable UUID id) {
        Watchlist item = find(id);
        broker.deleteWatchlist(item.getAlpacaWatchlistId());
        entries.deleteAll(entries.findByWatchlistId(id));
        watchlists.delete(item);
    }

    private View view(Watchlist item) {
        return new View(item, entries.findByWatchlistId(item.getWatchlistId()));
    }

    private List<WatchlistEntry> save(Watchlist item, List<String> symbols) {
        List<WatchlistEntry> saved = new ArrayList<>();

        for (String symbol : symbols) {
            WatchlistEntry entry = new WatchlistEntry();
            entry.setEntryId(UUID.randomUUID());
            entry.setWatchlistId(item.getWatchlistId());
            entry.setTicker(symbol);
            saved.add(entries.save(entry));
        }

        return saved;
    }

    private Watchlist find(UUID id) {
        return watchlists.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "WATCHLIST_NOT_FOUND",
                        "Watchlist not found."
                ));
    }

    private WatchlistEntry entry(Watchlist item, String ticker) {
        return entries.findByWatchlistId(item.getWatchlistId())
                .stream()
                .filter(x -> x.getTicker().equals(ticker))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "WATCHLIST_ENTRY_NOT_FOUND",
                        "Ticker not found."
                ));
    }

    private String name(String value) {
        if (value == null || value.isBlank())
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_WATCHLIST_NAME",
                    "Watchlist name is required."
            );

        return value.trim();
    }

    private List<String> symbols(List<String> values) {
        List<String> result = (values == null ? List.<String>of() : values)
                .stream()
                .map(this::ticker)
                .toList();

        if (new java.util.HashSet<>(result).size() != result.size())
            throw conflict("WATCHLIST_ENTRY_EXISTS", "Ticker already exists.");

        return result;
    }

    private String ticker(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);

        if (!TICKER.matcher(result).matches())
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TICKER",
                    "Ticker is invalid."
            );

        return result;
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public record Write(String name, List<String> symbols) {}

    public record EntryWrite(String ticker) {}

    public record ActiveWrite(boolean isActive) {}

    public record View(Watchlist watchlist, List<WatchlistEntry> entries) {}
}
