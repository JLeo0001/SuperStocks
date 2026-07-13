package cn.superstocks.stock;

import cn.superstocks.model.StockQuote;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class StockService {
    private final JavaPlugin plugin;
    private final StockProvider provider;
    private final Map<String, String> marketNames = new LinkedHashMap<>();
    private final Map<String, List<String>> marketSymbols = new LinkedHashMap<>();
    private final Map<String, String> symbolMarkets = new HashMap<>();
    private final Map<String, StockQuote> cache = new ConcurrentHashMap<>();

    public StockService(JavaPlugin plugin) {
        this.plugin = plugin;
        int timeout = plugin.getConfig().getInt("stock-provider.timeout-seconds", 8);
        this.provider = new TencentStockProvider(Duration.ofSeconds(timeout));
        reloadMarkets();
    }

    public void reloadMarkets() {
        marketNames.clear();
        marketSymbols.clear();
        symbolMarkets.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("markets");
        if (root == null) {
            return;
        }
        for (String market : root.getKeys(false)) {
            String name = root.getString(market + ".display-name", market);
            List<String> symbols = new ArrayList<>(root.getStringList(market + ".symbols"));
            marketNames.put(market, name);
            marketSymbols.put(market, Collections.unmodifiableList(symbols));
            for (String symbol : symbols) {
                symbolMarkets.put(symbol, market);
            }
        }
    }

    public void start() {
        long refreshTicks = Math.max(60L, plugin.getConfig().getLong("stock-provider.refresh-seconds", 300L) * 20L);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::refreshNow, 20L, refreshTicks);
    }

    public void refreshNow() {
        try {
            Map<String, StockQuote> fetched = provider.fetch(symbolMarkets.keySet(), symbolMarkets);
            cache.putAll(fetched);
            plugin.getLogger().info("已同步 " + fetched.size() + " 条股票行情");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "同步腾讯财经行情失败，将继续使用旧缓存", ex);
        }
    }

    public Map<String, String> marketNames() {
        return Collections.unmodifiableMap(marketNames);
    }

    public List<String> symbolsForMarket(String market) {
        return marketSymbols.getOrDefault(market, List.of());
    }

    public Optional<StockQuote> quote(String symbol) {
        return Optional.ofNullable(cache.get(symbol));
    }

    public Collection<StockQuote> cachedQuotes() {
        return Collections.unmodifiableCollection(cache.values());
    }

    public String marketName(String market) {
        return marketNames.getOrDefault(market, market);
    }
}
