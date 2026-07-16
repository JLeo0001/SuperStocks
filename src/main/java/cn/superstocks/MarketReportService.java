package cn.superstocks;

import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.StockQuote;
import cn.superstocks.stock.StockService;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MarketReportService {
    private final JavaPlugin plugin; private final StockService stocks; private final LanguageManager language;
    private final Map<UUID, BossBar> pinnedBars = new ConcurrentHashMap<>();
    private final Map<UUID, String> pinnedSymbols = new ConcurrentHashMap<>();
    private BukkitTask reportTask, bossBarTask;

    public MarketReportService(JavaPlugin plugin, StockService stocks, LanguageManager language) {
        this.plugin = plugin; this.stocks = stocks; this.language = language;
    }

    public void start() {
        stop();
        if (plugin.getConfig().getBoolean("market-report.enabled", false)) {
            long interval = Math.max(1, plugin.getConfig().getLong("market-report.interval-hours", 24)) * 3600L * 20L;
            reportTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::publishReport, 600L, interval);
        }
        bossBarTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::refreshBossBars, 40L, 40L);
    }

    public void stop() {
        if (reportTask != null) { reportTask.cancel(); reportTask = null; }
        if (bossBarTask != null) { bossBarTask.cancel(); bossBarTask = null; }
        pinnedBars.values().forEach(b -> { b.removeAll(); });
        pinnedBars.clear(); pinnedSymbols.clear();
    }

    public void pin(Player player, String symbol) {
        Optional<StockQuote> quote = stocks.quote(symbol);
        if (quote.isEmpty()) { player.sendMessage(language.text("messages.quote-unavailable")); return; }
        BossBar old = pinnedBars.remove(player.getUniqueId());
        if (old != null) old.removeAll();
        pinnedSymbols.put(player.getUniqueId(), symbol);
        BossBar bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
        bar.addPlayer(player); bar.setVisible(true);
        pinnedBars.put(player.getUniqueId(), bar);
        player.sendMessage(language.text("messages.pin-set", language.vars("symbol", symbol)));
    }

    public void unpin(Player player) {
        BossBar bar = pinnedBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
        pinnedSymbols.remove(player.getUniqueId());
        player.sendMessage(language.text("messages.pin-removed"));
    }

    private void refreshBossBars() {
        List<UUID> toRemove = new ArrayList<>();
        Map<UUID, BossBarUpdate> updates = new HashMap<>();
        pinnedBars.forEach((uuid, bar) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                toRemove.add(uuid);
                return;
            }
            String symbol = pinnedSymbols.get(uuid);
            if (symbol == null) return;
            stocks.quote(symbol).ifPresent(q -> {
                String arrow = q.change() >= 0 ? language.text("bossbar.arrow-up") : language.text("bossbar.arrow-down");
                String title = language.text("bossbar.pin-format", language.vars(
                        "name", q.name(),
                        "price", format(q.price()),
                        "arrow", arrow,
                        "change_percent", format(Math.abs(q.changePercent()))
                ));
                BarColor color = q.change() >= 0 ? BarColor.GREEN : BarColor.RED;
                double progress = Math.max(0.05, Math.min(1.0, (q.changePercent() + 10) / 20));
                updates.put(uuid, new BossBarUpdate(bar, title, color, progress));
            });
        });
        // BossBar API must be called on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID uuid : toRemove) {
                BossBar bar = pinnedBars.remove(uuid);
                if (bar != null) bar.removeAll();
                pinnedSymbols.remove(uuid);
            }
            updates.values().forEach(u -> {
                u.bar.setTitle(u.title);
                u.bar.setColor(u.color);
                u.bar.setProgress(u.progress);
            });
        });
    }

    private record BossBarUpdate(BossBar bar, String title, BarColor color, double progress) {}

    private void publishReport() {
        Map<String, Double> prices = stocks.priceSnapshot();
        if (prices.isEmpty()) return;
        String bestName = "", worstName = "";
        double bestChg = Double.NEGATIVE_INFINITY, worstChg = Double.POSITIVE_INFINITY;
        for (var e : prices.entrySet()) {
            var q = stocks.quote(e.getKey());
            if (q.isPresent()) {
                if (q.get().changePercent() > bestChg) { bestChg = q.get().changePercent(); bestName = q.get().name(); }
                if (q.get().changePercent() < worstChg) { worstChg = q.get().changePercent(); worstName = q.get().name(); }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(language.text("messages.report-header")).append("\n");
        for (String market : stocks.marketNames().keySet()) {
            double sum = 0; int count = 0;
            for (String s : stocks.symbolsForMarket(market)) { Double p = prices.get(s); if (p != null) { sum += p; count++; } }
            if (count > 0) sb.append(language.text("messages.report-avg", language.vars("market", stocks.marketName(market), "avg", format(sum / count)))).append("  ");
        }
        if (!bestName.isEmpty()) sb.append("\n").append(language.text("messages.report-top", language.vars("name", bestName, "change", format(bestChg))));
        if (!worstName.isEmpty()) sb.append("\n").append(language.text("messages.report-bottom", language.vars("name", worstName, "change", format(worstChg))));
        String msg = LanguageManager.color(sb.toString());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(msg));
    }

    public String generateReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(language.text("messages.report-header")).append("\n");
        Map<String, Double> prices = stocks.priceSnapshot();
        if (prices.isEmpty()) return language.text("messages.quote-unavailable");
        for (String market : stocks.marketNames().keySet()) {
            double sum = 0; int count = 0;
            for (String s : stocks.symbolsForMarket(market)) { Double p = prices.get(s); if (p != null && p > 0) { sum += p; count++; } }
            if (count > 0) sb.append(language.text("messages.report-avg", language.vars("market", stocks.marketName(market), "avg", format(sum / count)))).append("  ");
        }
        String bestName = "", worstName = "";
        double bestChg = Double.NEGATIVE_INFINITY, worstChg = Double.POSITIVE_INFINITY;
        for (var e : prices.entrySet()) {
            var q = stocks.quote(e.getKey());
            if (q.isPresent()) {
                if (q.get().changePercent() > bestChg) { bestChg = q.get().changePercent(); bestName = q.get().name(); }
                if (q.get().changePercent() < worstChg) { worstChg = q.get().changePercent(); worstName = q.get().name(); }
            }
        }
        if (!bestName.isEmpty()) sb.append("\n").append(language.text("messages.report-top", language.vars("name", bestName, "change", format(bestChg))));
        if (!worstName.isEmpty()) sb.append("\n").append(language.text("messages.report-bottom", language.vars("name", worstName, "change", format(worstChg))));
        return sb.toString();
    }

    private static String format(double v) { return String.format("%.2f", v); }
}
