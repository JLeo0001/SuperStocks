package cn.superstocks.placeholder;

import cn.superstocks.SuperStocksPlugin;
import cn.superstocks.model.Holding;
import cn.superstocks.model.StockQuote;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class SuperStocksExpansion extends PlaceholderExpansion {
    private final SuperStocksPlugin plugin;

    public SuperStocksExpansion(SuperStocksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "superstocks";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SuperStocks";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || player.getUniqueId() == null) {
            return null;
        }
        try {
            return switch (params.toLowerCase()) {
                case "cash" -> plugin.economy().available() ? plugin.economy().format(plugin.economy().balance(player)) : "0.00";
                case "portfolio_value" -> format(portfolioValue(player));
                case "total_value" -> format(totalValue(player));
                case "profit" -> format(profit(player));
                case "profit_percent" -> format(profitPercent(player));
                default -> stockShares(player, params);
            };
        } catch (SQLException ex) {
            plugin.getLogger().warning("PlaceholderAPI query failed: " + ex.getMessage());
            return "0";
        }
    }

    private String stockShares(OfflinePlayer player, String params) throws SQLException {
        String lower = params.toLowerCase();
        if (!lower.startsWith("shares_")) {
            return null;
        }
        String symbol = params.substring("shares_".length());
        return plugin.tradeService().holding(player.getUniqueId(), symbol)
                .map(holding -> format(holding.shares()))
                .orElse("0");
    }

    private double portfolioValue(OfflinePlayer player) throws SQLException {
        double value = 0.0D;
        for (Holding holding : plugin.tradeService().holdings(player.getUniqueId())) {
            Optional<StockQuote> quote = plugin.stockService().quote(holding.symbol());
            if (quote.isPresent()) {
                value += holding.marketValue(quote.get().price());
            }
        }
        return value;
    }

    private double totalValue(OfflinePlayer player) throws SQLException {
        double cash = plugin.economy().available() ? plugin.economy().balance(player) : 0.0D;
        return cash + portfolioValue(player);
    }

    private double profit(OfflinePlayer player) throws SQLException {
        double value = 0.0D;
        for (Holding holding : plugin.tradeService().holdings(player.getUniqueId())) {
            Optional<StockQuote> quote = plugin.stockService().quote(holding.symbol());
            if (quote.isPresent()) {
                value += holding.profit(quote.get().price());
            }
        }
        return value;
    }

    private double profitPercent(OfflinePlayer player) throws SQLException {
        List<Holding> holdings = plugin.tradeService().holdings(player.getUniqueId());
        double cost = 0.0D;
        double current = 0.0D;
        for (Holding holding : holdings) {
            cost += holding.shares() * holding.averageCost();
            Optional<StockQuote> quote = plugin.stockService().quote(holding.symbol());
            if (quote.isPresent()) {
                current += holding.marketValue(quote.get().price());
            }
        }
        if (cost <= 0.0D) {
            return 0.0D;
        }
        return (current - cost) / cost * 100.0D;
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }
}
