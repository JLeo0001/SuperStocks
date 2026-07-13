package cn.superstocks.stock;

import cn.superstocks.model.StockQuote;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayList;
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
    private StockProvider provider;
    private String providerName;
    private final Map<String, String> marketNames = new LinkedHashMap<>();
    private final Map<String, List<String>> marketSymbols = new LinkedHashMap<>();
    private final Map<String, String> marketIcons = new LinkedHashMap<>();
    private final Map<String, String> symbolMarkets = new HashMap<>();
    private final Map<String, StockQuote> cache = new ConcurrentHashMap<>();
    private volatile Instant lastSync;
    private volatile int lastFetchedCount;

    public StockService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        reloadProvider();
        reloadMarkets();
    }

    private void reloadProvider() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("stock-provider");
        if (section == null) {
            throw new IllegalStateException("Missing stock-provider config section");
        }
        String active = section.getString("active", section.getString("type", "tencent")).toLowerCase();
        ConfigurationSection source = section.getConfigurationSection("sources." + active);
        if (source == null) {
            plugin.getLogger().warning("未找到行情源配置 " + active + "，已回退到 tencent");
            active = "tencent";
            source = section.getConfigurationSection("sources.tencent");
        }
        if (source == null) {
            source = section;
        }
        providerName = source.getString("display-name", active);
        provider = new TencentStockProvider(source, section.getInt("timeout-seconds", 8));
    }

    public void reloadMarkets() {
        marketNames.clear();
        marketSymbols.clear();
        marketIcons.clear();
        symbolMarkets.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("markets");
        if (root == null) {
            return;
        }
        for (String market : root.getKeys(false)) {
            String name = root.getString(market + ".display-name", market);
            String icon = root.getString(market + ".icon", "EMERALD");
            List<String> symbols = new ArrayList<>(root.getStringList(market + ".symbols"));
            marketNames.put(market, name);
            marketIcons.put(market, icon);
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
            lastFetchedCount = fetched.size();
            lastSync = Instant.now();
            if (plugin.getConfig().getBoolean("stock-provider.log-success", true)) {
                plugin.getLogger().info("已同步 " + fetched.size() + " 条股票行情，当前缓存 " + loadedTotal() + "/" + configuredTotal());
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "同步股票行情失败，将继续使用旧缓存", ex);
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

    public String marketName(String market) {
        return marketNames.getOrDefault(market, market);
    }

    public String marketIcon(String market) {
        return marketIcons.getOrDefault(market, "EMERALD");
    }

    public int configuredTotal() {
        return symbolMarkets.size();
    }

    public int configuredCount(String market) {
        return symbolsForMarket(market).size();
    }

    public int loadedTotal() {
        int count = 0;
        for (String symbol : symbolMarkets.keySet()) {
            if (cache.containsKey(symbol)) {
                count++;
            }
        }
        return count;
    }

    public int loadedCount(String market) {
        int count = 0;
        for (String symbol : symbolsForMarket(market)) {
            if (cache.containsKey(symbol)) {
                count++;
            }
        }
        return count;
    }

    public int lastFetchedCount() {
        return lastFetchedCount;
    }

    public Instant lastSync() {
        return lastSync;
    }

    public Map<String, Double> priceSnapshot() {
        Map<String, Double> prices = new HashMap<>();
        for (Map.Entry<String, StockQuote> entry : cache.entrySet()) {
            prices.put(entry.getKey(), entry.getValue().price());
        }
        return prices;
    }

    public String providerName() {
        return providerName;
    }
}
