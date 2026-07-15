package cn.superstocks;

import cn.superstocks.economy.VaultEconomyHook;
import cn.superstocks.model.Holding;
import cn.superstocks.stock.StockService;
import cn.superstocks.storage.StockStorage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class LossPenaltyService {
    private final JavaPlugin plugin;
    private final StockStorage storage;
    private final StockService stockService;
    private final VaultEconomyHook economy;

    private BukkitTask task;

    public LossPenaltyService(JavaPlugin plugin, StockStorage storage, StockService stockService, VaultEconomyHook economy) {
        this.plugin = plugin;
        this.storage = storage;
        this.stockService = stockService;
        this.economy = economy;
    }

    public void start() {
        stop();
        if (!enabled()) {
            return;
        }
        long intervalTicks = Math.max(20L, plugin.getConfig().getLong("loss-penalty.check-interval-seconds", 600L) * 20L);
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::runSafely, intervalTicks, intervalTicks);
    }

    public void reload() {
        start();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("loss-penalty.enabled", false);
    }

    private void runSafely() {
        try {
            run();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "执行亏损代价任务失败", ex);
        }
    }

    private void run() throws SQLException {
        if (!enabled()) {
            return;
        }
        long cooldownMillis = Math.max(0L, plugin.getConfig().getLong("loss-penalty.player-cooldown-seconds", 3600L) * 1000L);
        long now = System.currentTimeMillis();

        Map<String, Double> prices = stockService.priceSnapshot();
        Map<UUID, LossSnapshot> losses = calculateLosses(prices);
        for (Map.Entry<UUID, LossSnapshot> entry : losses.entrySet()) {
            UUID playerId = entry.getKey();
            LossSnapshot snapshot = entry.getValue();
            PenaltyTier tier = tierFor(snapshot);
            if (tier == null || snapshot.cost < plugin.getConfig().getDouble("loss-penalty.minimum-portfolio-cost", 1000.0D)
                    || snapshot.loss < plugin.getConfig().getDouble("loss-penalty.minimum-loss-amount", 100.0D)) {
                continue;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
            Player online = offline.getPlayer();
            if (plugin.getConfig().getBoolean("loss-penalty.target.online-only", true) && (online == null || !online.isOnline())) continue;
            if (online != null && online.hasPermission("superstocks.penalty.exempt")) continue;
            if (now - storage.penaltyCooldown(playerId) < cooldownMillis) continue;
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
                    applyVaultPenalty(player, snapshot, tier);
                    runCommands(player, snapshot, tier);
                    notifyPlayer(player, snapshot, tier);
                    storage.savePenalty(playerId, tier.id, snapshot.loss, snapshot.lossPercent, snapshot.vaultPenalty, now);
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.WARNING, "保存亏损代价记录失败", ex);
                }
            });
        }
    }

    private Map<UUID, LossSnapshot> calculateLosses(Map<String, Double> prices) throws SQLException {
        Map<UUID, LossSnapshot> snapshots = new HashMap<>();
        for (Holding holding : storage.allHoldings()) {
            double price = prices.getOrDefault(holding.symbol(), 0.0D);
            if (price <= 0.0D) {
                continue;
            }
            double cost = holding.shares() * holding.averageCost();
            double value = holding.marketValue(price);
            LossSnapshot snapshot = snapshots.computeIfAbsent(holding.playerId(), ignored -> new LossSnapshot());
            snapshot.cost += cost;
            snapshot.value += value;
        }
        for (LossSnapshot snapshot : snapshots.values()) {
            snapshot.loss = Math.max(0.0D, snapshot.cost - snapshot.value);
            snapshot.lossPercent = snapshot.cost <= 0.0D ? 0.0D : snapshot.loss / snapshot.cost * 100.0D;
        }
        return snapshots;
    }

    private PenaltyTier tierFor(LossSnapshot snapshot) {
        if (snapshot.loss <= 0.0D) {
            return null;
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("loss-penalty.tiers");
        if (section == null) {
            return null;
        }
        PenaltyTier selected = null;
        for (String key : section.getKeys(false)) {
            ConfigurationSection tier = section.getConfigurationSection(key);
            if (tier == null || !tier.getBoolean("enabled", true)) {
                continue;
            }
            double min = Math.max(0.0D, tier.getDouble("min-loss-percent", 0.0D));
            double max = tier.getDouble("max-loss-percent", -1.0D);
            boolean inRange = snapshot.lossPercent >= min && (max < 0.0D || snapshot.lossPercent < max);
            if (!inRange) {
                continue;
            }
            if (selected == null || min > selected.minLossPercent) {
                selected = new PenaltyTier(
                        key,
                        min,
                        tier.getDouble("fixed-amount", 0.0D),
                        tier.getDouble("percent-of-loss", 0.0D),
                        tier.getDouble("percent-of-portfolio-value", 0.0D),
                        tier.getDouble("max-amount", 0.0D)
                );
            }
        }
        return selected;
    }

    private void applyVaultPenalty(OfflinePlayer player, LossSnapshot snapshot, PenaltyTier tier) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("loss-penalty.actions.vault-withdraw");
        if (section == null || !section.getBoolean("enabled", false) || !economy.available()) {
            return;
        }
        boolean neverNegative = section.getBoolean("never-negative-balance", true);
        double amount = tier.amount(snapshot);
        if (amount <= 0.0D) {
            return;
        }
        if (neverNegative) {
            amount = Math.min(amount, economy.balance(player));
        }
        if (amount > 0.0D) {
            economy.withdraw(player, amount);
            snapshot.vaultPenalty = amount;
        }
    }

    private void runCommands(OfflinePlayer player, LossSnapshot snapshot, PenaltyTier tier) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("loss-penalty.actions.commands");
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }
        List<String> commands = section.getStringList("list");
        if (commands.isEmpty()) {
            return;
        }
        for (String command : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replace(command, player, snapshot, tier));
        }
    }

    private void notifyPlayer(OfflinePlayer offlinePlayer, LossSnapshot snapshot, PenaltyTier tier) {
        if (!plugin.getConfig().getBoolean("loss-penalty.actions.notify.enabled", true)) {
            return;
        }
        Player player = offlinePlayer.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendMessage(replace(plugin.getConfig().getString("loss-penalty.actions.notify.message", "&cYour portfolio is losing money."), offlinePlayer, snapshot, tier).replace("&", "§"));
    }

    private String replace(String input, OfflinePlayer player, LossSnapshot snapshot, PenaltyTier tier) {
        return input
                .replace("{player}", player.getName() == null ? player.getUniqueId().toString() : player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{tier}", tier.id)
                .replace("{loss}", format(snapshot.loss))
                .replace("{loss_percent}", format(snapshot.lossPercent))
                .replace("{portfolio_value}", format(snapshot.value))
                .replace("{cost}", format(snapshot.cost))
                .replace("{vault_penalty}", format(snapshot.vaultPenalty));
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    private static final class PenaltyTier {
        private final String id;
        private final double minLossPercent;
        private final double fixedAmount;
        private final double percentOfLoss;
        private final double percentOfPortfolioValue;
        private final double maxAmount;

        private PenaltyTier(String id, double minLossPercent, double fixedAmount, double percentOfLoss, double percentOfPortfolioValue, double maxAmount) {
            this.id = id;
            this.minLossPercent = minLossPercent;
            this.fixedAmount = Math.max(0.0D, fixedAmount);
            this.percentOfLoss = Math.max(0.0D, percentOfLoss);
            this.percentOfPortfolioValue = Math.max(0.0D, percentOfPortfolioValue);
            this.maxAmount = Math.max(0.0D, maxAmount);
        }

        private double amount(LossSnapshot snapshot) {
            double amount = fixedAmount
                    + snapshot.loss * percentOfLoss / 100.0D
                    + snapshot.value * percentOfPortfolioValue / 100.0D;
            if (maxAmount > 0.0D) {
                amount = Math.min(amount, maxAmount);
            }
            return Math.max(0.0D, amount);
        }
    }

    private static final class LossSnapshot {
        private double cost;
        private double value;
        private double loss;
        private double lossPercent;
        private double vaultPenalty;
    }
}
