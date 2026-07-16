package cn.superstocks;

import cn.superstocks.lang.LanguageManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.URI;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigValidator {
    private final JavaPlugin plugin;
    private final LanguageManager language;

    public ConfigValidator(JavaPlugin plugin, LanguageManager language) {
        this.plugin = plugin;
        this.language = language;
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        validateLanguage(errors);
        Set<String> marketIds = new HashSet<>();
        Set<String> symbols = new HashSet<>();
        validateRealMarkets(errors, marketIds, symbols);
        validateCustomMarkets(errors, marketIds, symbols);
        validateMaterials(errors);
        validateVolatility(errors);
        validatePenaltyTiers(errors);
        validateProvider(errors);
        validateMarketHours(errors);
        validateGui(errors);
        return errors;
    }

    private Map<String, String> v(String k, Object v) { return language.vars(k, String.valueOf(v)); }
    private Map<String, String> v(String k1, Object v1, String k2, Object v2) { return language.vars(k1, String.valueOf(v1), k2, String.valueOf(v2)); }
    private Map<String, String> v(String k1, Object v1, String k2, Object v2, String k3, Object v3) { return language.vars(k1, String.valueOf(v1), k2, String.valueOf(v2), k3, String.valueOf(v3)); }
    private String t(String key) { return language.text(key); }
    private String t(String key, Map<String, String> vars) { return language.text(key, vars); }

    private void validateLanguage(List<String> errors) {
        String lang = plugin.getConfig().getString("language", "zh_CN");
        File local = new File(plugin.getDataFolder(), "Language/" + lang + ".yml");
        if (plugin.getResource("Language/" + lang + ".yml") == null && !local.exists()) {
            errors.add(t("validators.errors.language-not-found", v("language", lang)));
        }
    }

    private void validateRealMarkets(List<String> errors, Set<String> marketIds, Set<String> symbols) {
        ConfigurationSection markets = plugin.getConfig().getConfigurationSection("markets");
        if (markets == null) return;
        for (String market : markets.getKeys(false)) {
            if (!marketIds.add(market)) {
                errors.add(t("validators.errors.duplicate-market", v("market", market)));
            }
            for (String symbol : markets.getStringList(market + ".symbols")) {
                if (symbol.isBlank()) {
                    errors.add(t("validators.errors.market-empty-symbol", v("market", market)));
                } else if (!symbols.add(symbol)) {
                    errors.add(t("validators.errors.duplicate-symbol", v("symbol", symbol)));
                }
            }
        }
    }

    private void validateCustomMarkets(List<String> errors, Set<String> marketIds, Set<String> symbols) {
        File folder = new File(plugin.getDataFolder(), "CustomMarkets");
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return;
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String id = yaml.getString("market.id", file.getName().replaceFirst("\\.ya?ml$", ""));
            if (id == null || id.isBlank()) {
                errors.add(t("validators.errors.custom-market-id-empty", v("file", file.getName())));
                continue;
            }
            if (!marketIds.add(id)) {
                errors.add(t("validators.errors.duplicate-market-id", v("id", id, "file", file.getName())));
            }
            String icon = yaml.getString("market.icon", "AMETHYST_SHARD");
            if (Material.matchMaterial(icon) == null) {
                errors.add(t("validators.errors.custom-market-invalid-material", v("file", file.getName(), "icon", icon)));
            }
            ConfigurationSection stocks = yaml.getConfigurationSection("stocks");
            if (stocks == null) {
                errors.add(t("validators.errors.custom-market-no-stocks", v("file", file.getName())));
                continue;
            }
            for (String key : stocks.getKeys(false)) {
                String path = key + ".";
                String symbol = stocks.getString(path + "symbol", key);
                if (symbol == null || symbol.isBlank()) {
                    errors.add(t("validators.errors.custom-market-empty-symbol", v("file", file.getName(), "key", key)));
                } else if (!symbols.add(symbol)) {
                    errors.add(t("validators.errors.duplicate-symbol-file", v("symbol", symbol, "file", file.getName())));
                }
                double initial = stocks.getDouble(path + "initial-price", 100.0D);
                double min = stocks.getDouble(path + "min-price", 1.0D);
                double max = stocks.getDouble(path + "max-price", 10000.0D);
                if (min <= 0.0D || max < min || initial < min || initial > max) {
                    errors.add(t("validators.errors.custom-market-invalid-price", v("file", file.getName(), "symbol", symbol)));
                }
                if (stocks.getDouble(path + "volatility-percent", 2.0D) < 0.0D) {
                    errors.add(t("validators.errors.custom-market-negative-volatility", v("file", file.getName(), "symbol", symbol)));
                }
            }
        }
    }

    private void validateMaterials(List<String> errors) {
        String[] keys = {"filler-material", "stats-material", "back-material", "portfolio-material",
                "winners-material", "losers-material", "quote-up-material", "quote-down-material",
                "quote-flat-material", "quote-missing-material", "holding-material", "buy-material", "sell-material"};
        for (String key : keys) {
            String value = plugin.getConfig().getString("gui." + key);
            if (value != null && Material.matchMaterial(value) == null) {
                errors.add(t("validators.errors.invalid-material", v("key", key, "value", value)));
            }
        }
    }

    private void validateVolatility(List<String> errors) {
        double min = plugin.getConfig().getDouble("gameplay-volatility.random-adjustment.min-percent", -3.0D);
        double max = plugin.getConfig().getDouble("gameplay-volatility.random-adjustment.max-percent", 3.0D);
        if (min > max) {
            errors.add(t("validators.errors.volatility-min-exceeds-max"));
        }
        double minPrice = plugin.getConfig().getDouble("gameplay-volatility.safety.min-price", 0.01D);
        double maxPrice = plugin.getConfig().getDouble("gameplay-volatility.safety.max-price", 1_000_000_000.0D);
        if (minPrice <= 0.0D || maxPrice < minPrice) {
            errors.add(t("validators.errors.invalid-safety-price"));
        }
    }

    private void validatePenaltyTiers(List<String> errors) {
        ConfigurationSection tiers = plugin.getConfig().getConfigurationSection("loss-penalty.tiers");
        if (tiers == null) return;
        List<TierRange> ranges = new ArrayList<>();
        for (String key : tiers.getKeys(false)) {
            if (!tiers.getBoolean(key + ".enabled", true)) continue;
            double min = tiers.getDouble(key + ".min-loss-percent", 0.0D);
            double max = tiers.getDouble(key + ".max-loss-percent", -1.0D);
            if (min < 0.0D || (max >= 0.0D && max <= min)) {
                errors.add(t("validators.errors.invalid-penalty-tier", v("key", key)));
                continue;
            }
            ranges.add(new TierRange(key, min, max));
        }
        ranges.sort(Comparator.comparingDouble(TierRange::min));
        for (int i = 1; i < ranges.size(); i++) {
            TierRange previous = ranges.get(i - 1);
            TierRange current = ranges.get(i);
            if (previous.max < 0.0D || current.min < previous.max) {
                errors.add(t("validators.errors.penalty-tier-overlap", v("previous", previous.id, "current", current.id)));
            }
        }
    }

    private void validateProvider(List<String> errors) {
        String active = plugin.getConfig().getString("stock-provider.active", "tencent");
        String endpoint = plugin.getConfig().getString("stock-provider.sources." + active + ".endpoint");
        if (endpoint == null || !endpoint.contains("{symbols}")) {
            errors.add(t("validators.errors.provider-missing-symbols", v("active", active)));
            return;
        }
        try {
            URI uri = URI.create(endpoint.replace("{symbols}", "test"));
            if (uri.getScheme() == null || (!uri.getScheme().equals("http") && !uri.getScheme().equals("https"))) {
                errors.add(t("validators.errors.provider-wrong-scheme", v("active", active)));
            }
        } catch (IllegalArgumentException ex) {
            errors.add(t("validators.errors.provider-invalid-uri", v("active", active)));
        }
    }

    private void validateMarketHours(List<String> errors) {
        ConfigurationSection hours = plugin.getConfig().getConfigurationSection("market-hours");
        if (hours == null) return;
        for (String market : hours.getKeys(false)) {
            if (!hours.getBoolean(market + ".enabled", false)) continue;
            try {
                ZoneId.of(hours.getString(market + ".timezone", "UTC"));
                LocalTime.parse(hours.getString(market + ".open", "09:30"));
                LocalTime.parse(hours.getString(market + ".close", "16:00"));
            } catch (Exception ex) {
                errors.add(t("validators.errors.invalid-market-hours", v("market", market)));
            }
            String action = hours.getString(market + ".closed-action", "DENY");
            if (!action.equalsIgnoreCase("DENY") && !action.equalsIgnoreCase("SELL_ONLY")) {
                errors.add(t("validators.errors.invalid-closed-action", v("market", market)));
            }
        }
    }

    private void validateGui(List<String> errors) {
        String[] sizeKeys = {"main-size", "market-size", "stock-size", "portfolio-size", "watchlist-size", "ranking-size"};
        for (String key : sizeKeys) {
            int size = plugin.getConfig().getInt("gui." + key, 54);
            if (size < 9 || size > 54 || size % 9 != 0) {
                errors.add(t("validators.errors.invalid-gui-size", v("key", key, "value", size)));
            }
        }
    }

    public void log() {
        List<String> errors = validate();
        plugin.getLogger().info(t("validators.log.banner"));
        String lang = plugin.getConfig().getString("language", "zh_CN");
        plugin.getLogger().info(t("validators.log.language-line", v("language", lang)));
        String provider = plugin.getConfig().getString("stock-provider.active", "tencent");
        plugin.getLogger().info(t("validators.log.provider-line", v("provider", provider, "seconds", plugin.getConfig().getInt("stock-provider.refresh-seconds", 300))));
        ConfigurationSection markets = plugin.getConfig().getConfigurationSection("markets");
        int totalStocks = 0;
        if (markets != null) {
            for (String m : markets.getKeys(false)) {
                int count = markets.getStringList(m + ".symbols").size();
                totalStocks += count;
                plugin.getLogger().info(t("validators.log.market-line", v("market", m, "count", count)));
            }
        }
        File cmFolder = new File(plugin.getDataFolder(), "CustomMarkets");
        File[] cmFiles = cmFolder.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        int cmCount = cmFiles != null ? cmFiles.length : 0;
        if (cmCount > 0) plugin.getLogger().info(t("validators.log.custom-market-line", v("count", cmCount)));
        plugin.getLogger().info(t("validators.log.feature-status"));
        logFeature(t("validators.features.quote-protection"), plugin.getConfig().getBoolean("quote-safety.reject-stale-quotes", true));
        logFeature(t("validators.features.circuit-breaker"), plugin.getConfig().getBoolean("quote-safety.circuit-breaker.enabled", true));
        logFeature(t("validators.features.short-selling"), plugin.getConfig().getBoolean("short-selling.enabled", true));
        logFeature(t("validators.features.competition"), plugin.getConfig().getBoolean("competition.enabled", true));
        logFeature(t("validators.features.auto-orders"), plugin.getConfig().getBoolean("orders.enabled", true));
        logFeature(t("validators.features.price-alerts"), plugin.getConfig().getBoolean("alerts.enabled", true));
        logFeature(t("validators.features.price-history"), plugin.getConfig().getBoolean("price-history.enabled", true));
        logFeature(t("validators.features.custom-markets"), plugin.getConfig().getBoolean("custom-markets.enabled", true));
        boolean risky = plugin.getConfig().getBoolean("loss-penalty.enabled", false)
                || plugin.getConfig().getBoolean("gameplay-volatility.price-multiplier.enabled", false)
                || plugin.getConfig().getBoolean("gameplay-volatility.random-adjustment.enabled", false)
                || plugin.getConfig().getBoolean("dividends.enabled", false)
                || plugin.getConfig().getBoolean("market-events.enabled", false);
        plugin.getLogger().info(t("validators.log.high-risk", v("state", risky ? t("validators.log.high-risk-on") : t("validators.log.high-risk-off"))));
        if (errors.isEmpty()) {
            plugin.getLogger().info(t("validators.log.all-passed"));
        } else {
            plugin.getLogger().warning(t("validators.log.errors-header", v("count", errors.size())));
            errors.forEach(e -> plugin.getLogger().warning(t("validators.log.error-line", v("error", e))));
        }
        plugin.getLogger().info(t("validators.log.footer"));
    }

    private void logFeature(String name, boolean enabled) {
        plugin.getLogger().info("    " + (enabled ? "✓" : "✗") + " " + name + (enabled ? "" : " (off)"));
    }

    private record TierRange(String id, double min, double max) {}
}
