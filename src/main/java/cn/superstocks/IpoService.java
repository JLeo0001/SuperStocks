package cn.superstocks;

import cn.superstocks.economy.VaultEconomyHook;
import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.*;
import cn.superstocks.stock.StockService;
import cn.superstocks.storage.StockStorage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.*;

public final class IpoService {
    private final JavaPlugin plugin; private final StockStorage storage; private final StockService stocks;
    private final VaultEconomyHook economy; private final LanguageManager language; private BukkitTask task;

    public IpoService(JavaPlugin plugin, StockStorage storage, StockService stocks,
                      VaultEconomyHook economy, LanguageManager language) {
        this.plugin = plugin; this.storage = storage; this.stocks = stocks;
        this.economy = economy; this.language = language;
    }

    public void start() { stop(); long ticks = Math.max(200L, 120L * 20L);
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::processIpos, ticks, ticks); }
    public void stop() { if (task != null) { task.cancel(); task = null; } }

    private void processIpos() {
        try {
            long now = System.currentTimeMillis();
            for (IpoOffering ipo : storage.ipos()) {
                if (ipo.open() && now >= ipo.closesAt()) {
                    Bukkit.getScheduler().runTask(plugin, () -> allocateIpo(ipo));
                }
            }
        } catch (Exception ignored) {}
    }

    public IpoResult create(String symbol, String name, String market, double price, double totalShares,
                            double maxPerPlayer, long closesAt) throws SQLException {
        if (stocks.quote(symbol).isPresent()) return fail("messages.ipo-symbol-exists");
        int id = storage.createIpo(symbol, name, market, price, totalShares, maxPerPlayer, closesAt);
        storage.audit("ADMIN", "IPO_CREATE", symbol + " " + name + " price=" + price);
        return ok("messages.ipo-created", "id", id, "symbol", symbol, "name", name, "price", economy.format(price));
    }

    public IpoResult subscribe(OfflinePlayer player, int ipoId, double shares) throws SQLException {
        Optional<IpoOffering> ipo = storage.ipo(ipoId);
        if (ipo.isEmpty() || !ipo.get().open()) return fail("messages.ipo-not-found");
        if (System.currentTimeMillis() >= ipo.get().closesAt()) return fail("messages.ipo-closed");
        double cost = ipo.get().price() * shares;
        if (!economy.available() || economy.balance(player) + 1e-6 < cost)
            return fail("messages.not-enough-money", "amount", economy.format(cost));
        double existing = 0;
        for (IpoSubscription sub : storage.ipoSubscriptions(ipoId))
            if (sub.playerId().equals(player.getUniqueId())) existing += sub.shares();
        if (existing + shares > ipo.get().maxPerPlayer()) return fail("messages.ipo-limit");
        double totalSubbed = storage.ipoSubscriptions(ipoId).stream().mapToDouble(IpoSubscription::shares).sum();
        if (totalSubbed + shares > ipo.get().totalShares() * 1.5) return fail("messages.ipo-oversubscribed");
        if (!economy.withdraw(player, cost)) return fail("messages.withdraw-failed");
        storage.subscribeIpo(ipoId, player.getUniqueId(), shares);
        return ok("messages.ipo-subscribed", "shares", TradeService.formatNumber(shares), "symbol", ipo.get().symbol());
    }

    private void allocateIpo(IpoOffering ipo) {
        try {
            storage.allocateIpo(ipo.id());
            List<IpoSubscription> subs = storage.ipoSubscriptions(ipo.id());
            double totalSub = subs.stream().mapToDouble(IpoSubscription::shares).sum();
            if (totalSub <= 0) { refundAll(ipo, subs); return; }
            double ratio = Math.min(1.0, ipo.totalShares() / totalSub);
            for (IpoSubscription sub : subs) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(sub.playerId());
                double allocated = Math.floor(sub.shares() * ratio);
                double refund = (sub.shares() - allocated) * ipo.price();
                if (allocated > 0) {
                    storage.executeBuy(player.getUniqueId(), ipo.symbol(), allocated, ipo.price(), 0);
                    storage.audit(player.getName(), "IPO_ALLOCATED", ipo.symbol() + " x" + allocated);
                }
                if (refund > 0 && economy.available()) economy.deposit(player, refund);
                Player online = player.getPlayer();
                if (online != null) online.sendMessage(language.text("messages.ipo-allocated",
                        language.vars("symbol", ipo.symbol(), "shares", TradeService.formatNumber(allocated), "refund", economy.format(refund))));
            }
            Bukkit.broadcastMessage(language.text("messages.ipo-completed", language.vars("symbol", ipo.symbol(), "name", ipo.name())));
        } catch (Exception e) { plugin.getLogger().warning("IPO分配失败: " + ipo.symbol()); }
    }

    private void refundAll(IpoOffering ipo, List<IpoSubscription> subs) {
        for (IpoSubscription sub : subs) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(sub.playerId());
            if (economy.available()) economy.deposit(player, sub.shares() * ipo.price());
        }
    }

    public List<IpoOffering> list() throws SQLException { return storage.ipos(); }
    public Optional<IpoOffering> get(int id) throws SQLException { return storage.ipo(id); }
    public IpoResult cancel(int id) throws SQLException {
        Optional<IpoOffering> ipo = storage.ipo(id);
        if (ipo.isEmpty()) return fail("messages.ipo-not-found");
        storage.cancelIpo(id);
        refundAll(ipo.get(), storage.ipoSubscriptions(id));
        return ok("messages.ipo-cancelled", "symbol", ipo.get().symbol());
    }

    private IpoResult fail(String key, Object... vars) {
        return new IpoResult(false, language.text(key, language.vars(vars)));
    }
    private IpoResult ok(String key, Object... vars) {
        return new IpoResult(true, language.text(key, language.vars(vars)));
    }
    public record IpoResult(boolean success, String message) {}
}
