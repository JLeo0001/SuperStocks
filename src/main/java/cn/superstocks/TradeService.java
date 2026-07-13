package cn.superstocks;

import cn.superstocks.economy.VaultEconomyHook;
import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.Holding;
import cn.superstocks.model.RankingEntry;
import cn.superstocks.model.StockQuote;
import cn.superstocks.storage.StockStorage;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TradeService {
    private final VaultEconomyHook economy;
    private final StockStorage storage;
    private final LanguageManager language;
    private final double taxPercent;
    private final double minShares;

    public TradeService(VaultEconomyHook economy, StockStorage storage, LanguageManager language, double taxPercent, double minShares) {
        this.economy = economy;
        this.storage = storage;
        this.language = language;
        this.taxPercent = Math.max(0.0D, taxPercent);
        this.minShares = Math.max(1.0D, minShares);
    }

    public TradeResult buy(Player player, StockQuote quote, double shares) throws SQLException {
        if (!economy.available()) {
            return TradeResult.fail(language.text("messages.economy-unavailable"));
        }
        if (shares < minShares) {
            return TradeResult.fail(language.text("messages.min-shares", language.vars("shares", formatNumber(minShares))));
        }
        double gross = quote.price() * shares;
        double tax = gross * taxPercent / 100.0D;
        double total = gross + tax;
        if (economy.balance(player) + 0.000001D < total) {
            return TradeResult.fail(language.text("messages.not-enough-money", language.vars("amount", economy.format(total))));
        }
        if (!economy.withdraw(player, total)) {
            return TradeResult.fail(language.text("messages.withdraw-failed"));
        }
        try {
            storage.buy(player.getUniqueId(), quote.symbol(), shares, quote.price());
            return TradeResult.ok(language.text("messages.buy-success", language.vars(
                    "name", quote.name(),
                    "shares", formatNumber(shares),
                    "amount", economy.format(total)
            )));
        } catch (SQLException ex) {
            economy.deposit(player, total);
            throw ex;
        }
    }

    public TradeResult sell(Player player, StockQuote quote, double shares) throws SQLException {
        if (!economy.available()) {
            return TradeResult.fail(language.text("messages.economy-unavailable"));
        }
        if (shares < minShares) {
            return TradeResult.fail(language.text("messages.min-shares", language.vars("shares", formatNumber(minShares))));
        }
        boolean sold = storage.sell(player.getUniqueId(), quote.symbol(), shares, quote.price());
        if (!sold) {
            return TradeResult.fail(language.text("messages.not-enough-shares"));
        }
        double gross = quote.price() * shares;
        double tax = gross * taxPercent / 100.0D;
        double total = Math.max(0.0D, gross - tax);
        if (!economy.deposit(player, total)) {
            return TradeResult.fail(language.text("messages.deposit-failed"));
        }
        return TradeResult.ok(language.text("messages.sell-success", language.vars(
                "name", quote.name(),
                "shares", formatNumber(shares),
                "amount", economy.format(total)
        )));
    }

    public List<Holding> holdings(UUID playerId) throws SQLException {
        return storage.holdings(playerId);
    }

    public Optional<Holding> holding(UUID playerId, String symbol) throws SQLException {
        return storage.holding(playerId, symbol);
    }

    public List<RankingEntry> rankings(Map<String, Double> prices, boolean winners, int limit) throws SQLException {
        Map<UUID, double[]> totals = new HashMap<>();
        for (Holding holding : storage.allHoldings()) {
            double price = prices.getOrDefault(holding.symbol(), 0.0D);
            if (price <= 0.0D) {
                continue;
            }
            double[] total = totals.computeIfAbsent(holding.playerId(), ignored -> new double[2]);
            total[0] += holding.marketValue(price);
            total[1] += holding.shares() * holding.averageCost();
        }
        List<RankingEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, double[]> entry : totals.entrySet()) {
            double value = entry.getValue()[0];
            double cost = entry.getValue()[1];
            double profit = value - cost;
            double percent = cost <= 0.0D ? 0.0D : profit / cost * 100.0D;
            entries.add(new RankingEntry(entry.getKey(), value, profit, percent));
        }
        Comparator<RankingEntry> comparator = Comparator.comparingDouble(RankingEntry::profit);
        entries.sort(winners ? comparator.reversed() : comparator);
        if (entries.size() > limit) {
            return new ArrayList<>(entries.subList(0, limit));
        }
        return entries;
    }

    public static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format("%.2f", value);
    }

    public record TradeResult(boolean success, String message) {
        public static TradeResult ok(String message) {
            return new TradeResult(true, message);
        }

        public static TradeResult fail(String message) {
            return new TradeResult(false, message);
        }
    }
}
