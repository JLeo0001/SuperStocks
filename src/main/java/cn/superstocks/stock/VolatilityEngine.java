package cn.superstocks.stock;

import cn.superstocks.model.StockQuote;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class VolatilityEngine {
    private final JavaPlugin plugin;
    private final Map<String, RandomState> randomStates = new ConcurrentHashMap<>();

    private boolean priceMultiplierEnabled;
    private double priceMultiplier;
    private boolean randomEnabled;
    private double randomMinPercent;
    private double randomMaxPercent;
    private long randomRefreshMillis;
    private double minPrice;
    private double maxPrice;

    public VolatilityEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("gameplay-volatility");
        if (section == null) {
            priceMultiplierEnabled = false;
            randomEnabled = false;
            priceMultiplier = 1.0D;
            randomMinPercent = -3.0D;
            randomMaxPercent = 3.0D;
            randomRefreshMillis = 300_000L;
            minPrice = 0.01D;
            maxPrice = 1_000_000_000D;
            return;
        }
        priceMultiplierEnabled = section.getBoolean("price-multiplier.enabled", false);
        priceMultiplier = Math.max(0.0001D, section.getDouble("price-multiplier.multiplier", 1.0D));
        randomEnabled = section.getBoolean("random-adjustment.enabled", false);
        randomMinPercent = section.getDouble("random-adjustment.min-percent", -3.0D);
        randomMaxPercent = section.getDouble("random-adjustment.max-percent", 3.0D);
        if (randomMinPercent > randomMaxPercent) {
            double temp = randomMinPercent;
            randomMinPercent = randomMaxPercent;
            randomMaxPercent = temp;
        }
        randomRefreshMillis = Math.max(10_000L, section.getLong("random-adjustment.refresh-seconds", 300L) * 1000L);
        minPrice = Math.max(0.0001D, section.getDouble("safety.min-price", 0.01D));
        maxPrice = Math.max(minPrice, section.getDouble("safety.max-price", 1_000_000_000D));
    }

    public StockQuote apply(StockQuote quote) {
        double basePrice = quote.price();
        double multiplier = priceMultiplierEnabled ? priceMultiplier : 1.0D;
        double randomFactor = randomEnabled ? 1.0D + randomPercent(quote.symbol()) / 100.0D : 1.0D;
        double adjustedPrice = clamp(basePrice * multiplier * randomFactor);

        double priceRatio = basePrice <= 0.0D ? 1.0D : adjustedPrice / basePrice;
        double adjustedChange = quote.change() * priceRatio;
        double adjustedPercent = quote.changePercent();
        if (randomEnabled) {
            adjustedPercent += (randomFactor - 1.0D) * 100.0D;
        }

        return new StockQuote(
                quote.symbol(),
                quote.name(),
                quote.market(),
                adjustedPrice,
                adjustedChange,
                adjustedPercent,
                quote.updatedAt()
        );
    }

    public double randomPercent(String symbol) {
        long now = System.currentTimeMillis();
        RandomState state = randomStates.compute(symbol, (ignored, old) -> {
            if (old == null || now - old.generatedAt >= randomRefreshMillis) {
                double value = Math.abs(randomMaxPercent - randomMinPercent) < 0.000001D
                        ? randomMinPercent
                        : ThreadLocalRandom.current().nextDouble(randomMinPercent, randomMaxPercent);
                return new RandomState(value, now);
            }
            return old;
        });
        return state.percent;
    }

    public boolean anyEnabled() {
        return priceMultiplierEnabled || randomEnabled;
    }

    public boolean randomEnabled() {
        return randomEnabled;
    }

    public long randomRefreshSeconds() {
        return randomRefreshMillis / 1000L;
    }

    public String summary() {
        return "multiplier=" + (priceMultiplierEnabled ? priceMultiplier : 1.0D)
                + ", random=" + (randomEnabled ? randomMinPercent + "%.." + randomMaxPercent + "%/" + (randomRefreshMillis / 1000L) + "s" : "off");
    }

    private double clamp(double value) {
        return Math.max(minPrice, Math.min(maxPrice, value));
    }

    private record RandomState(double percent, long generatedAt) {
    }
}
