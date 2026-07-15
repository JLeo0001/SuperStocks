package cn.superstocks;

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
import java.util.Set;

public final class ConfigValidator {
    private final JavaPlugin plugin;

    public ConfigValidator(JavaPlugin plugin) {
        this.plugin = plugin;
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

    private void validateLanguage(List<String> errors) {
        String language = plugin.getConfig().getString("language", "zh_CN");
        File local = new File(plugin.getDataFolder(), "Language/" + language + ".yml");
        if (plugin.getResource("Language/" + language + ".yml") == null && !local.exists()) {
            errors.add("语言文件不存在: " + language);
        }
    }

    private void validateRealMarkets(List<String> errors, Set<String> marketIds, Set<String> symbols) {
        ConfigurationSection markets = plugin.getConfig().getConfigurationSection("markets");
        if (markets == null) {
            return;
        }
        for (String market : markets.getKeys(false)) {
            if (!marketIds.add(market)) {
                errors.add("重复市场 ID: " + market);
            }
            for (String symbol : markets.getStringList(market + ".symbols")) {
                if (symbol.isBlank()) {
                    errors.add("市场 " + market + " 包含空股票代码");
                } else if (!symbols.add(symbol)) {
                    errors.add("重复股票代码: " + symbol);
                }
            }
        }
    }

    private void validateCustomMarkets(List<String> errors, Set<String> marketIds, Set<String> symbols) {
        File folder = new File(plugin.getDataFolder(), "CustomMarkets");
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String id = yaml.getString("market.id", file.getName().replaceFirst("\\.ya?ml$", ""));
            if (id == null || id.isBlank()) {
                errors.add(file.getName() + " 的市场 ID 为空");
                continue;
            }
            if (!marketIds.add(id)) {
                errors.add("重复市场 ID: " + id + " (" + file.getName() + ")");
            }
            String icon = yaml.getString("market.icon", "AMETHYST_SHARD");
            if (Material.matchMaterial(icon) == null) {
                errors.add(file.getName() + " 使用无效 Material: " + icon);
            }
            ConfigurationSection stocks = yaml.getConfigurationSection("stocks");
            if (stocks == null) {
                errors.add(file.getName() + " 没有 stocks 配置");
                continue;
            }
            for (String key : stocks.getKeys(false)) {
                String path = key + ".";
                String symbol = stocks.getString(path + "symbol", key);
                if (symbol == null || symbol.isBlank()) {
                    errors.add(file.getName() + " 包含空股票代码: " + key);
                } else if (!symbols.add(symbol)) {
                    errors.add("重复股票代码: " + symbol + " (" + file.getName() + ")");
                }
                double initial = stocks.getDouble(path + "initial-price", 100.0D);
                double min = stocks.getDouble(path + "min-price", 1.0D);
                double max = stocks.getDouble(path + "max-price", 10000.0D);
                if (min <= 0.0D || max < min || initial < min || initial > max) {
                    errors.add(file.getName() + " 的价格范围无效: " + symbol);
                }
                if (stocks.getDouble(path + "volatility-percent", 2.0D) < 0.0D) {
                    errors.add(file.getName() + " 的波动率不能小于 0: " + symbol);
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
                errors.add("无效 Material: gui." + key + "=" + value);
            }
        }
    }

    private void validateVolatility(List<String> errors) {
        double min = plugin.getConfig().getDouble("gameplay-volatility.random-adjustment.min-percent", -3.0D);
        double max = plugin.getConfig().getDouble("gameplay-volatility.random-adjustment.max-percent", 3.0D);
        if (min > max) {
            errors.add("随机波动下限大于上限");
        }
        double minPrice = plugin.getConfig().getDouble("gameplay-volatility.safety.min-price", 0.01D);
        double maxPrice = plugin.getConfig().getDouble("gameplay-volatility.safety.max-price", 1_000_000_000.0D);
        if (minPrice <= 0.0D || maxPrice < minPrice) {
            errors.add("游戏内价格安全范围无效");
        }
    }

    private void validatePenaltyTiers(List<String> errors) {
        ConfigurationSection tiers = plugin.getConfig().getConfigurationSection("loss-penalty.tiers");
        if (tiers == null) {
            return;
        }
        List<TierRange> ranges = new ArrayList<>();
        for (String key : tiers.getKeys(false)) {
            if (!tiers.getBoolean(key + ".enabled", true)) {
                continue;
            }
            double min = tiers.getDouble(key + ".min-loss-percent", 0.0D);
            double max = tiers.getDouble(key + ".max-loss-percent", -1.0D);
            if (min < 0.0D || (max >= 0.0D && max <= min)) {
                errors.add("亏损档位范围无效: " + key);
                continue;
            }
            ranges.add(new TierRange(key, min, max));
        }
        ranges.sort(Comparator.comparingDouble(TierRange::min));
        for (int i = 1; i < ranges.size(); i++) {
            TierRange previous = ranges.get(i - 1);
            TierRange current = ranges.get(i);
            if (previous.max < 0.0D || current.min < previous.max) {
                errors.add("亏损档位重叠: " + previous.id + " / " + current.id);
            }
        }
    }

    private void validateProvider(List<String> errors) {
        String active = plugin.getConfig().getString("stock-provider.active", "tencent");
        String endpoint = plugin.getConfig().getString("stock-provider.sources." + active + ".endpoint");
        if (endpoint == null || !endpoint.contains("{symbols}")) {
            errors.add("行情源 endpoint 必须包含 {symbols}: " + active);
            return;
        }
        try {
            URI uri = URI.create(endpoint.replace("{symbols}", "test"));
            if (uri.getScheme() == null || (!uri.getScheme().equals("http") && !uri.getScheme().equals("https"))) {
                errors.add("行情源 endpoint 必须使用 HTTP/HTTPS: " + active);
            }
        } catch (IllegalArgumentException ex) {
            errors.add("行情源 endpoint 无效: " + active);
        }
    }

    private void validateMarketHours(List<String> errors) {
        ConfigurationSection hours = plugin.getConfig().getConfigurationSection("market-hours");
        if (hours == null) {
            return;
        }
        for (String market : hours.getKeys(false)) {
            if (!hours.getBoolean(market + ".enabled", false)) {
                continue;
            }
            try {
                ZoneId.of(hours.getString(market + ".timezone", "UTC"));
                LocalTime.parse(hours.getString(market + ".open", "09:30"));
                LocalTime.parse(hours.getString(market + ".close", "16:00"));
            } catch (Exception ex) {
                errors.add("市场交易时段配置无效: " + market);
            }
            String action = hours.getString(market + ".closed-action", "DENY");
            if (!action.equalsIgnoreCase("DENY") && !action.equalsIgnoreCase("SELL_ONLY")) {
                errors.add("closed-action 只能是 DENY 或 SELL_ONLY: " + market);
            }
        }
    }

    private void validateGui(List<String> errors) {
        String[] sizeKeys = {"main-size", "market-size", "stock-size", "portfolio-size", "watchlist-size", "ranking-size"};
        for (String key : sizeKeys) {
            int size = plugin.getConfig().getInt("gui." + key, 54);
            if (size < 9 || size > 54 || size % 9 != 0) {
                errors.add("GUI 尺寸无效: gui." + key + "=" + size);
            }
        }
    }

    public void log() {
        List<String> errors = validate();
        plugin.getLogger().info("========== SuperStocks 配置自检 ==========");
        // Summary
        String lang = plugin.getConfig().getString("language", "zh_CN");
        plugin.getLogger().info("  语言: " + lang);
        String provider = plugin.getConfig().getString("stock-provider.active", "tencent");
        plugin.getLogger().info("  行情源: " + provider + " (刷新间隔 " + plugin.getConfig().getInt("stock-provider.refresh-seconds", 300) + "s)");
        // Market stats
        ConfigurationSection markets = plugin.getConfig().getConfigurationSection("markets");
        int totalStocks = 0;
        if (markets != null) {
            for (String m : markets.getKeys(false)) {
                int count = markets.getStringList(m + ".symbols").size();
                totalStocks += count;
                plugin.getLogger().info("  市场 " + m + ": " + count + " 只股票");
            }
        }
        // Custom markets
        File cmFolder = new File(plugin.getDataFolder(), "CustomMarkets");
        File[] cmFiles = cmFolder.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        int cmCount = cmFiles != null ? cmFiles.length : 0;
        if (cmCount > 0) plugin.getLogger().info("  本地市场: " + cmCount + " 个文件");
        // Feature status
        plugin.getLogger().info("  功能状态:");
        logFeature("行情保护", plugin.getConfig().getBoolean("quote-safety.reject-stale-quotes", true));
        logFeature("熔断机制", plugin.getConfig().getBoolean("quote-safety.circuit-breaker.enabled", true));
        logFeature("做空交易", plugin.getConfig().getBoolean("short-selling.enabled", true));
        logFeature("投资大赛", plugin.getConfig().getBoolean("competition.enabled", true));
        logFeature("自动订单", plugin.getConfig().getBoolean("orders.enabled", true));
        logFeature("价格提醒", plugin.getConfig().getBoolean("alerts.enabled", true));
        logFeature("价格历史", plugin.getConfig().getBoolean("price-history.enabled", true));
        logFeature("本地市场", plugin.getConfig().getBoolean("custom-markets.enabled", true));
        boolean risky = plugin.getConfig().getBoolean("loss-penalty.enabled", false)
                || plugin.getConfig().getBoolean("gameplay-volatility.price-multiplier.enabled", false)
                || plugin.getConfig().getBoolean("gameplay-volatility.random-adjustment.enabled", false)
                || plugin.getConfig().getBoolean("dividends.enabled", false)
                || plugin.getConfig().getBoolean("market-events.enabled", false);
        plugin.getLogger().info("  高风险功能: " + (risky ? "已启用 ⚠" : "全部关闭 ✓"));
        // Errors
        if (errors.isEmpty()) {
            plugin.getLogger().info("  配置检查: 通过 ✓");
        } else {
            plugin.getLogger().warning("  配置问题 (" + errors.size() + "):");
            errors.forEach(e -> plugin.getLogger().warning("    ✗ " + e));
        }
        plugin.getLogger().info("===========================================");
    }

    private void logFeature(String name, boolean enabled) {
        plugin.getLogger().info("    " + (enabled ? "✓" : "✗") + " " + name + (enabled ? "" : " (关闭)"));
    }

    private record TierRange(String id, double min, double max) {
    }
}
