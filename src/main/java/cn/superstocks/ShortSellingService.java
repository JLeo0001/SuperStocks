package cn.superstocks;

import cn.superstocks.economy.VaultEconomyHook;
import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.Holding;
import cn.superstocks.model.ShortPosition;
import cn.superstocks.model.StockQuote;
import cn.superstocks.stock.StockService;
import cn.superstocks.storage.StockStorage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public final class ShortSellingService {
    private final JavaPlugin plugin; private final StockStorage storage; private final StockService stocks;
    private final VaultEconomyHook economy; private final LanguageManager language; private BukkitTask task;

    public ShortSellingService(JavaPlugin plugin, StockStorage storage, StockService stocks,
                               VaultEconomyHook economy, LanguageManager language) {
        this.plugin = plugin; this.storage = storage; this.stocks = stocks;
        this.economy = economy; this.language = language;
    }

    public void start() {
        stop();
        if (!enabled()) return;
        long ticks = Math.max(200L, plugin.getConfig().getLong("short-selling.check-seconds", 120L) * 20L);
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::checkLiquidations, ticks, ticks);
    }

    public void stop() { if (task != null) { task.cancel(); task = null; } }
    private boolean enabled() { return plugin.getConfig().getBoolean("short-selling.enabled", true); }

    private void checkLiquidations() {
        try {
            long now = System.currentTimeMillis();
            double liqPct = plugin.getConfig().getDouble("short-selling.margin.liquidation-percent", 50);
            for (ShortPosition pos : storage.allOpenShorts()) {
                Optional<StockQuote> q = stocks.quote(pos.symbol());
                if (q.isEmpty()) continue;
                double loss = -pos.unrealizedPnl(q.get().price()); // unrealizedPnl < 0 means loss
                double marginRatio = pos.margin() > 0 ? loss / pos.margin() * 100 : 0;
                if (marginRatio >= liqPct) {
                    Bukkit.getScheduler().runTask(plugin, () -> forceLiquidate(pos, q.get()));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "检查做空强平失败", e);
        }
    }

    private void forceLiquidate(ShortPosition pos, StockQuote quote) {
        try {
            double cost = quote.price() * pos.shares();
            OfflinePlayer player = Bukkit.getOfflinePlayer(pos.playerId());
            storage.closeShort(pos.id(), pos.playerId());
            double remaining = pos.margin() - Math.max(0, cost - pos.liability(quote.price()));
            if (remaining > 0 && economy.available()) economy.deposit(player, remaining);
            double lost = pos.margin() - Math.max(0, remaining);
            storage.audit("SYSTEM", "SHORT_LIQUIDATED", player.getName() + " " + pos.symbol() + " x" + pos.shares());
            Player online = player.getPlayer();
            if (online != null) {
                online.sendMessage(language.text("messages.short-liquidated", language.vars(
                        "symbol", pos.symbol(), "shares", TradeService.formatNumber(pos.shares()),
                        "price", format(quote.price()), "margin", economy.format(lost))));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "强平做空失败 #" + pos.id(), e);
        }
    }

    public ShortResult openShort(OfflinePlayer player, String symbol, double shares) throws SQLException {
        if (!enabled()) return fail("messages.trading-unavailable", "reason", "SHORT_DISABLED");
        Optional<StockQuote> quote = stocks.quote(symbol);
        if (quote.isEmpty()) return fail("messages.quote-unavailable");
        StockQuote q = quote.get();
        double value = q.price() * shares;
        double marginRate = Math.max(1, plugin.getConfig().getDouble("short-selling.margin.required-percent", 50));
        double margin = value * marginRate / 100;
        if (!economy.available() || economy.balance(player) + 1e-6 < margin)
            return fail("messages.not-enough-money", "amount", economy.format(margin));
        double maxShares = plugin.getConfig().getDouble("short-selling.max-shares-per-position", 10000);
        if (maxShares > 0 && shares > maxShares) return fail("messages.trade-limit");
        double interestRate = plugin.getConfig().getDouble("short-selling.interest-percent-per-day", 0.0);
        if (interestRate > 0) {
            double interest = value * interestRate / 100;
            if (economy.balance(player) + 1e-6 < margin + interest)
                return fail("messages.not-enough-money", "amount", economy.format(margin + interest));
            if (!economy.withdraw(player, interest)) return fail("messages.withdraw-failed");
        }
        if (!economy.withdraw(player, margin)) return fail("messages.withdraw-failed");
        long id = storage.openShort(player.getUniqueId(), symbol, shares, q.price(), margin);
        storage.audit(player.getName(), "SHORT_OPEN", symbol + " x" + shares + " @" + q.price());
        return ok("messages.short-opened", "id", id, "symbol", symbol, "shares", TradeService.formatNumber(shares),
                "price", economy.format(q.price()), "margin", economy.format(margin));
    }

    public ShortResult coverShort(OfflinePlayer player, long id, double shares) throws SQLException {
        if (!enabled()) return fail("messages.trading-unavailable", "reason", "SHORT_DISABLED");
        Optional<ShortPosition> posOpt = storage.shortPosition(id, player.getUniqueId());
        if (posOpt.isEmpty()) return fail("messages.short-not-found");
        ShortPosition pos = posOpt.get();
        if (shares <= 0 || shares > pos.shares()) shares = pos.shares();
        Optional<StockQuote> quote = stocks.quote(pos.symbol());
        if (quote.isEmpty()) return fail("messages.quote-unavailable");
        double cost = quote.get().price() * shares;
        if (!economy.available() || economy.balance(player) + 1e-6 < cost)
            return fail("messages.not-enough-money", "amount", economy.format(cost));
        if (!economy.withdraw(player, cost)) return fail("messages.withdraw-failed");
        double pnl = pos.liability(quote.get().price()) * (shares / pos.shares()) - cost;
        double marginReturn = pos.margin() * (shares / pos.shares());
        economy.deposit(player, marginReturn);
        if (shares + 1e-6 >= pos.shares()) {
            storage.closeShort(pos.id(), player.getUniqueId());
        } else {
            // Partial cover: reopen with reduced shares
            storage.closeShort(pos.id(), player.getUniqueId());
            storage.openShort(player.getUniqueId(), pos.symbol(), pos.shares() - shares, pos.borrowedPrice(), pos.margin() * (pos.shares() - shares) / pos.shares());
        }
        storage.audit(player.getName(), "SHORT_COVER", pos.symbol() + " x" + shares + " @" + quote.get().price());
        return ok("messages.short-covered", "symbol", pos.symbol(), "shares", TradeService.formatNumber(shares),
                "pnl", economy.format(pnl));
    }

    public List<ShortPosition> openShorts(UUID playerId) throws SQLException { return storage.openShorts(playerId); }

    private ShortResult fail(String key, Object... vars) {
        return new ShortResult(false, language.text(key, language.vars(vars)));
    }
    private ShortResult ok(String key, Object... vars) {
        return new ShortResult(true, language.text(key, language.vars(vars)));
    }
    public static String format(double v) { return String.format("%.2f", v); }

    public record ShortResult(boolean success, String message) {}
}
