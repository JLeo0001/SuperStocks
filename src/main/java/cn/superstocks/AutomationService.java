package cn.superstocks;

import cn.superstocks.economy.VaultEconomyHook;
import cn.superstocks.model.Holding;
import cn.superstocks.model.StockOrder;
import cn.superstocks.model.StockQuote;
import cn.superstocks.stock.StockService;
import cn.superstocks.storage.StockStorage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class AutomationService {
    private final SuperStocksPlugin plugin;
    private final StockStorage storage;
    private final StockService stocks;
    private final TradeService trades;
    private final VaultEconomyHook economy;
    private BukkitTask task;

    public AutomationService(SuperStocksPlugin plugin, StockStorage storage, StockService stocks,
                             TradeService trades, VaultEconomyHook economy) {
        this.plugin = plugin;
        this.storage = storage;
        this.stocks = stocks;
        this.trades = trades;
        this.economy = economy;
    }

    public void start() {
        stop();
        long ticks = Math.max(20L, plugin.getConfig().getLong("automation.check-seconds", 30L) * 20L);
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::runSafe, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void runSafe() {
        try {
            processOrders();
            processAlerts();
            processDividends();
            updatePlayerHighs();
            cleanupPriceHistory();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "自动化任务执行失败", ex);
        }
    }

    private void processOrders() throws SQLException {
        if (!plugin.getConfig().getBoolean("orders.enabled", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        for (StockOrder order : storage.openOrders()) {
            if (order.expired(now)) {
                Bukkit.getScheduler().runTask(plugin, () -> expireOrder(order));
                continue;
            }
            Optional<StockQuote> quote = stocks.quote(order.symbol());
            if (quote.isEmpty() || !order.triggered(quote.get().price()) || !storage.claimOrder(order.id())) {
                continue;
            }
            Bukkit.getScheduler().runTask(plugin, () -> executeOrder(order, quote.get()));
        }
    }

    private void executeOrder(StockOrder order, StockQuote quote) {
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(order.playerId());
            TradeService.TradeResult result = order.type() == StockOrder.Type.LIMIT_BUY
                    ? trades.buyReserved(player, quote, order.shares(), order.reservedCash())
                    : trades.sell(player, quote, order.shares(), true);
            if (!result.success()) {
                storage.reopenOrder(order.id());
                return;
            }
            storage.completeOrder(order.id());
            storage.audit(playerName(player), "ORDER_EXECUTED", "#" + order.id() + " " + order.type() + " " + order.symbol());
            Player online = player.getPlayer();
            if (online != null) {
                online.sendMessage(plugin.language().text("messages.order-executed", plugin.language().vars(
                        "id", order.id(), "symbol", order.symbol(), "price", format(quote.price()))));
            }
        } catch (Exception ex) {
            try {
                storage.reopenOrder(order.id());
            } catch (SQLException reopenError) {
                ex.addSuppressed(reopenError);
            }
            plugin.getLogger().log(Level.WARNING, "订单执行失败 #" + order.id(), ex);
        }
    }

    private void expireOrder(StockOrder order) {
        try {
            if (!storage.closeOrder(order.id(), order.playerId(), "EXPIRED")) {
                return;
            }
            refundOrder(order);
            storage.audit(order.playerId().toString(), "ORDER_EXPIRED", "#" + order.id());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "订单过期处理失败 #" + order.id(), ex);
        }
    }

    private void processAlerts() throws SQLException {
        if (!plugin.getConfig().getBoolean("alerts.enabled", true)) {
            return;
        }
        for (StockStorage.PriceAlert alert : storage.alerts()) {
            Optional<StockQuote> quote = stocks.quote(alert.symbol());
            if (quote.isEmpty()) {
                continue;
            }
            boolean hit = "ABOVE".equalsIgnoreCase(alert.direction())
                    ? quote.get().price() >= alert.targetPrice()
                    : quote.get().price() <= alert.targetPrice();
            if (!hit) {
                continue;
            }
            storage.deleteAlert(alert.id());
            Player player = Bukkit.getPlayer(alert.playerId());
            if (player != null) {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(plugin.language().text(
                        "messages.alert-triggered", plugin.language().vars(
                                "symbol", alert.symbol(), "price", format(quote.get().price())))));
            }
        }
    }

    private void processDividends() throws SQLException {
        if (!plugin.getConfig().getBoolean("dividends.enabled", false) || !economy.available()) {
            return;
        }
        long interval = Math.max(1L, plugin.getConfig().getLong("dividends.interval-hours", 168L)) * 3_600_000L;
        long now = System.currentTimeMillis();
        long last = storage.metadata("last-dividend-at").map(value -> {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }).orElse(0L);
        if (now - last < interval) {
            return;
        }
        storage.saveMetadata("last-dividend-at", String.valueOf(now));
        double percent = Math.max(0.0D, plugin.getConfig().getDouble("dividends.yield-percent", 0.5D));
        long minimumAge = Math.max(0L, plugin.getConfig().getLong("dividends.minimum-holding-hours", 24L)) * 3_600_000L;
        for (Holding holding : storage.allHoldings()) {
            if (minimumAge > 0L && now - storage.holdingSince(holding.playerId(), holding.symbol()) < minimumAge) {
                continue;
            }
            Optional<StockQuote> quote = stocks.quote(holding.symbol());
            if (quote.isEmpty()) {
                continue;
            }
            double amount = holding.marketValue(quote.get().price()) * percent / 100.0D;
            if (amount <= 0.0D) {
                continue;
            }
            OfflinePlayer player = Bukkit.getOfflinePlayer(holding.playerId());
            Bukkit.getScheduler().runTask(plugin, () -> economy.deposit(player, amount));
            storage.recordDividend(holding.playerId(), amount);
        }
    }

    private void cleanupPriceHistory() throws SQLException {
        if (!plugin.getConfig().getBoolean("price-history.enabled", true)) {
            return;
        }
        long now = System.currentTimeMillis();
        long lastCleanup = storage.metadata("last-price-history-cleanup").map(value -> {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }).orElse(0L);
        if (now - lastCleanup < 86_400_000L) {
            return;
        }
        int retentionDays = Math.max(1, plugin.getConfig().getInt("price-history.retention-days", 30));
        storage.deletePriceHistoryBefore(now - retentionDays * 86_400_000L);
        storage.saveMetadata("last-price-history-cleanup", String.valueOf(now));
    }

    private void updatePlayerHighs() throws SQLException {
        if (!economy.available()) {
            return;
        }
        Set<UUID> processed = new HashSet<>();
        for (Holding holding : storage.allHoldings()) {
            UUID playerId = holding.playerId();
            if (!processed.add(playerId)) {
                continue;
            }
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
            double total = economy.balance(player);
            for (Holding playerHolding : storage.holdings(playerId)) {
                total += stocks.quote(playerHolding.symbol())
                        .map(quote -> playerHolding.marketValue(quote.price()))
                        .orElse(0.0D);
            }
            storage.updateHighestValue(playerId, total);
        }
    }

    public long createOrder(OfflinePlayer player, String symbol, StockOrder.Type type, double shares,
                            double triggerPrice, long durationSeconds) throws SQLException {
        if (!plugin.getConfig().getBoolean("orders.enabled", true)
                || shares <= 0.0D || triggerPrice <= 0.0D || stocks.quote(symbol).isEmpty()) {
            return -1L;
        }
        if (storage.playerOrders(player.getUniqueId()).size() >= plugin.getConfig().getInt("orders.max-open-per-player", 10)) {
            return -1L;
        }
        double reservedCash = 0.0D;
        double reservedShares = 0.0D;
        double reservedAverage = 0.0D;
        long reservedSince = 0L;
        if (type == StockOrder.Type.LIMIT_BUY) {
            double taxPercent = Math.max(0.0D, plugin.getConfig().getDouble("economy.transaction-tax-percent", 0.5D));
            reservedCash = triggerPrice * shares * (1.0D + taxPercent / 100.0D);
            if (!economy.available() || economy.balance(player) + 1.0E-6D < reservedCash || !economy.withdraw(player, reservedCash)) {
                return -2L;
            }
        } else {
            Optional<Holding> holding = storage.holding(player.getUniqueId(), symbol);
            double alreadyReserved = storage.reservedShares(player.getUniqueId(), symbol);
            if (holding.isEmpty() || holding.get().shares() - alreadyReserved + 1.0E-6D < shares) {
                return -3L;
            }
            reservedShares = shares;
            reservedAverage = holding.get().averageCost();
            reservedSince = storage.holdingSince(player.getUniqueId(), symbol);
        }
        long expiresAt = durationSeconds <= 0L ? 0L : System.currentTimeMillis() + durationSeconds * 1000L;
        try {
            long id = storage.createOrder(player.getUniqueId(), symbol, type, shares, triggerPrice, expiresAt,
                    reservedCash, reservedShares, reservedAverage, reservedSince);
            storage.audit(playerName(player), "ORDER_CREATED", "#" + id + " " + type + " " + symbol);
            return id;
        } catch (SQLException ex) {
            if (reservedCash > 0.0D) {
                economy.deposit(player, reservedCash);
            }
            throw ex;
        }
    }

    public List<StockOrder> orders(UUID playerId) throws SQLException {
        return storage.playerOrders(playerId);
    }

    public boolean cancelOrder(long id, UUID owner) throws SQLException {
        Optional<StockOrder> order = storage.order(id, owner);
        if (order.isEmpty() || !storage.closeOrder(id, owner, "CANCELLED")) {
            return false;
        }
        refundOrder(order.get());
        storage.audit(owner.toString(), "ORDER_CANCELLED", "#" + id);
        return true;
    }

    private void refundOrder(StockOrder order) {
        if (order.reservedCash() > 0.0D && economy.available()) {
            economy.deposit(Bukkit.getOfflinePlayer(order.playerId()), order.reservedCash());
        }
    }

    public Set<String> watchlist(UUID playerId) throws SQLException {
        return storage.watchlist(playerId);
    }

    public void watch(UUID playerId, String symbol) throws SQLException {
        if (stocks.quote(symbol).isEmpty()) {
            throw new IllegalArgumentException("Unknown stock symbol: " + symbol);
        }
        storage.addWatch(playerId, symbol);
    }

    public void unwatch(UUID playerId, String symbol) throws SQLException {
        storage.removeWatch(playerId, symbol);
    }

    public long alert(UUID playerId, String symbol, String direction, double price) throws SQLException {
        if (stocks.quote(symbol).isEmpty()) {
            return -1L;
        }
        return storage.createAlert(playerId, symbol, direction, price);
    }

    private static String playerName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }
}
