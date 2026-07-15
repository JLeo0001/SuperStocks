package cn.superstocks.command;

import cn.superstocks.SuperStocksPlugin;
import cn.superstocks.TradeService;
import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.Holding;
import cn.superstocks.model.PricePoint;
import cn.superstocks.model.RankingEntry;
import cn.superstocks.model.StockOrder;
import cn.superstocks.model.StockQuote;
import cn.superstocks.storage.StockStorage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StocksCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SuperStocksPlugin plugin;

    public StocksCommand(SuperStocksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            openGui(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "open", "gui" -> openGui(sender);
            case "buy" -> buy(sender, args);
            case "sell" -> sell(sender, args);
            case "quote", "price" -> quote(sender, args);
            case "portfolio", "holdings" -> portfolio(sender);
            case "rank", "ranking", "top" -> ranking(sender, args);
            case "history" -> history(sender, args);
            case "stats" -> stats(sender);
            case "watch" -> watch(sender, args);
            case "alert" -> alert(sender, args);
            case "order" -> order(sender, args);
            case "penalty" -> penalty(sender, args);
            case "admin" -> admin(sender, args);
            case "status" -> status(sender);
            case "pause" -> pause(sender, true);
            case "resume" -> pause(sender, false);
            case "reload" -> reload(sender);
            case "sync" -> sync(sender);
            case "short" -> shortStock(sender, args);
            case "cover" -> coverShort(sender, args);
            case "competition", "comp" -> competitionCmd(sender, args);
            case "ipo" -> ipoCmd(sender, args);
            case "certificate", "cert" -> certificateCmd(sender, args);
            case "index" -> indexCmd(sender, args);
            case "pin" -> pinCmd(sender, args);
            case "unpin" -> unpinCmd(sender);
            case "report" -> reportCmd(sender);
            default -> {
                sender.sendMessage(plugin.language().text("commands.unknown-subcommand"));
                sendHelp(sender);
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        for (String line : plugin.language().list("commands.help")) {
            sender.sendMessage(line);
        }
        for (String line : plugin.language().list("commands.help-advanced")) {
            sender.sendMessage(line);
        }
    }

    private void openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().text("commands.player-only"));
            return;
        }
        if (!player.hasPermission("superstocks.use")) {
            player.sendMessage(plugin.language().text("commands.no-permission"));
            return;
        }
        plugin.gui().openMain(player);
    }

    private void buy(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        if (args.length < 3) {
            sender.sendMessage(plugin.language().text("commands.buy-usage"));
            return;
        }
        Optional<StockQuote> quote = plugin.stockService().quote(args[1]);
        if (quote.isEmpty()) {
            sender.sendMessage(plugin.language().text("messages.quote-unavailable"));
            return;
        }
        Double shares = parsePositive(args[2]);
        if (shares == null) {
            sender.sendMessage(plugin.language().text("commands.invalid-amount"));
            return;
        }
        try {
            sender.sendMessage(plugin.tradeService().buy(player, quote.get(), shares).message());
        } catch (SQLException ex) {
            plugin.getLogger().warning("Command buy failed: " + ex.getMessage());
            sender.sendMessage(plugin.language().text("messages.trade-failed"));
        }
    }

    private void sell(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        if (args.length < 3) {
            sender.sendMessage(plugin.language().text("commands.sell-usage"));
            return;
        }
        Optional<StockQuote> quote = plugin.stockService().quote(args[1]);
        if (quote.isEmpty()) {
            sender.sendMessage(plugin.language().text("messages.quote-unavailable"));
            return;
        }
        Double shares = parsePositive(args[2]);
        if (shares == null) {
            sender.sendMessage(plugin.language().text("commands.invalid-amount"));
            return;
        }
        try {
            sender.sendMessage(plugin.tradeService().sell(player, quote.get(), shares).message());
        } catch (SQLException ex) {
            plugin.getLogger().warning("Command sell failed: " + ex.getMessage());
            sender.sendMessage(plugin.language().text("messages.trade-failed"));
        }
    }

    private void quote(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.language().text("commands.quote-usage"));
            return;
        }
        Optional<StockQuote> quote = plugin.stockService().quote(args[1]);
        if (quote.isEmpty()) {
            sender.sendMessage(plugin.language().text("commands.quote-not-found"));
            return;
        }
        StockQuote q = quote.get();
        Map<String, String> vars = plugin.language().vars(
                "name", q.name(),
                "symbol", q.symbol(),
                "market", plugin.stockService().marketName(q.market()),
                "price", format(q.price()),
                "change", format(q.change()),
                "change_percent", format(q.changePercent()),
                "updated_at", TIME_FORMAT.format(q.updatedAt())
        );
        for (String line : plugin.language().list("commands.quote-info", vars)) {
            sender.sendMessage(line);
        }
    }

    private void portfolio(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Holding> holdings = plugin.tradeService().holdings(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> sendPortfolio(sender, holdings));
            } catch (SQLException ex) {
                plugin.getLogger().warning("Command portfolio failed: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.language().text("messages.portfolio-load-failed")));
            }
        });
    }

    private void sendPortfolio(CommandSender sender, List<Holding> holdings) {
        sender.sendMessage(plugin.language().text("commands.portfolio-header"));
        if (holdings.isEmpty()) {
            sender.sendMessage(plugin.language().text("commands.portfolio-empty"));
            return;
        }
        for (Holding holding : holdings) {
            Optional<StockQuote> quote = plugin.stockService().quote(holding.symbol());
            double price = quote.map(StockQuote::price).orElse(0.0D);
            double value = price <= 0.0D ? 0.0D : holding.marketValue(price);
            double profit = price <= 0.0D ? 0.0D : holding.profit(price);
            sender.sendMessage(plugin.language().text("commands.portfolio-line", plugin.language().vars(
                    "symbol", holding.symbol(),
                    "shares", TradeService.formatNumber(holding.shares()),
                    "average_cost", format(holding.averageCost()),
                    "price", price <= 0.0D ? "-" : format(price),
                    "value", price <= 0.0D ? "-" : format(value),
                    "profit", price <= 0.0D ? "-" : format(profit)
            )));
        }
    }

    private void ranking(CommandSender sender, String[] args) {
        boolean winners = args.length < 2 || !args[1].equalsIgnoreCase("losers");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<RankingEntry> entries = plugin.tradeService().rankings(plugin.stockService().priceSnapshot(), winners, plugin.getConfig().getInt("gui.ranking-limit", 10));
                Bukkit.getScheduler().runTask(plugin, () -> sendRanking(sender, winners, entries));
            } catch (SQLException ex) {
                plugin.getLogger().warning("Command rank failed: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.language().text("messages.ranking-load-failed")));
            }
        });
    }

    private void sendRanking(CommandSender sender, boolean winners, List<RankingEntry> entries) {
        sender.sendMessage(plugin.language().text(winners ? "commands.rank-winners-header" : "commands.rank-losers-header"));
        if (entries.isEmpty()) {
            sender.sendMessage(plugin.language().text("commands.rank-empty"));
            return;
        }
        int rank = 1;
        for (RankingEntry entry : entries) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.playerId());
            String name = offlinePlayer.getName() == null ? entry.playerId().toString().substring(0, 8) : offlinePlayer.getName();
            sender.sendMessage(plugin.language().text("commands.rank-line", plugin.language().vars(
                    "rank", rank,
                    "player", name,
                    "value", format(entry.portfolioValue()),
                    "profit", format(entry.profit()),
                    "profit_percent", format(entry.profitPercent())
            )));
            rank++;
        }
    }

    private void history(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.language().text("commands.history-usage"));
            return;
        }
        long since = System.currentTimeMillis() - 24L * 3600L * 1000L;
        List<PricePoint> points = plugin.stockService().history(args[1], since, 24);
        if (points.isEmpty()) {
            sender.sendMessage(plugin.language().text("commands.history-empty"));
            return;
        }
        sender.sendMessage(plugin.language().text("commands.history-header", plugin.language().vars("symbol", args[1])));
        for (PricePoint point : points) {
            sender.sendMessage(plugin.language().text("commands.history-line", plugin.language().vars(
                    "time", TIME_FORMAT.format(Instant.ofEpochMilli(point.recordedAt())),
                    "price", format(point.price())
            )));
        }
    }

    private void stats(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var stats = plugin.tradeService().stats(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(plugin.language().text("commands.stats-header"));
                    sender.sendMessage(plugin.language().text("commands.stats-line", plugin.language().vars(
                            "realized_profit", format(stats.realizedProfit()),
                            "fees", format(stats.totalFees()),
                            "dividends", format(stats.totalDividends()),
                            "highest", format(stats.highestTotalValue()),
                            "trades", stats.tradeCount()
                    )));
                });
            } catch (SQLException ex) {
                String msg = plugin.language().text("messages.portfolio-load-failed");
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
            }
        });
    }

    private void watch(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (args.length >= 3 && args[1].equalsIgnoreCase("add")) {
                    plugin.automation().watch(player.getUniqueId(), args[2]);
                    String msg = plugin.language().text("commands.watch-added", plugin.language().vars("symbol", args[2]));
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
                    return;
                }
                if (args.length >= 3 && args[1].equalsIgnoreCase("remove")) {
                    plugin.automation().unwatch(player.getUniqueId(), args[2]);
                    String msg = plugin.language().text("commands.watch-removed", plugin.language().vars("symbol", args[2]));
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
                    return;
                }
                String header = plugin.language().text("commands.watch-header");
                List<String> lines = new ArrayList<>();
                lines.add(header);
                for (String symbol : plugin.automation().watchlist(player.getUniqueId())) {
                    lines.add(plugin.language().text("commands.watch-line", plugin.language().vars("symbol", symbol)));
                }
                Bukkit.getScheduler().runTask(plugin, () -> lines.forEach(sender::sendMessage));
            } catch (SQLException ex) {
                String msg = plugin.language().text("messages.trade-failed");
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
            }
        });
    }

    private void alert(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        if (args.length < 4) {
            sender.sendMessage(plugin.language().text("commands.alert-usage"));
            return;
        }
        Double price = parsePositive(args[3]);
        if (price == null) {
            sender.sendMessage(plugin.language().text("commands.invalid-amount"));
            return;
        }
        String direction = args[2].equalsIgnoreCase("below") ? "BELOW" : "ABOVE";
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long id = plugin.automation().alert(player.getUniqueId(), args[1], direction, price);
                String msg = plugin.language().text("commands.alert-created", plugin.language().vars("id", id, "symbol", args[1], "price", format(price)));
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
            } catch (SQLException ex) {
                String msg = plugin.language().text("messages.trade-failed");
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
            }
        });
    }

    private void order(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    String header = plugin.language().text("commands.order-header");
                    List<String> lines = new ArrayList<>();
                    lines.add(header);
                    for (StockOrder order : plugin.automation().orders(player.getUniqueId())) {
                        lines.add(plugin.language().text("commands.order-line", plugin.language().vars(
                                "id", order.id(), "type", order.type(), "symbol", order.symbol(), "shares", TradeService.formatNumber(order.shares()), "price", format(order.triggerPrice())
                        )));
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> lines.forEach(sender::sendMessage));
                } catch (SQLException ex) {
                    String msg = plugin.language().text("messages.trade-failed");
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
                }
            });
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("cancel")) {
            Long id = parseLong(args[2]);
            if (id == null) {
                sender.sendMessage(plugin.language().text("commands.invalid-amount"));
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    boolean ok = plugin.automation().cancelOrder(id, player.getUniqueId());
                    String msg = plugin.language().text(ok ? "commands.order-cancelled" : "commands.order-not-found", plugin.language().vars("id", id));
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
                } catch (SQLException ex) {
                    String msg = plugin.language().text("messages.trade-failed");
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
                }
            });
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(plugin.language().text("commands.order-usage"));
            return;
        }
        StockOrder.Type type = parseOrderType(args[1]);
        Double shares = parsePositive(args[3]);
        Double price = parsePositive(args[4]);
        if (type == null || shares == null || price == null) {
            sender.sendMessage(plugin.language().text("commands.order-usage"));
            return;
        }
        long duration = args.length >= 6 ? Math.max(0L, parseLong(args[5]) == null ? 0L : parseLong(args[5])) : 0L;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long id = plugin.automation().createOrder(player, args[2], type, shares, price, duration);
                String key = id < 0 ? "commands.order-limit" : "commands.order-created";
                String msg = plugin.language().text(key, plugin.language().vars("id", id));
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
            } catch (SQLException ex) {
                String msg = plugin.language().text("messages.trade-failed");
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
            }
        });
    }

    private void penalty(CommandSender sender, String[] args) {
        if (!sender.hasPermission("superstocks.admin")) {
            sender.sendMessage(plugin.language().text("commands.no-permission"));
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("history")) {
            sender.sendMessage(plugin.language().text("commands.penalty-usage"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String header = plugin.language().text("commands.penalty-header", plugin.language().vars("player", target.getName() == null ? target.getUniqueId() : target.getName()));
                List<String> lines = new ArrayList<>();
                lines.add(header);
                for (StockStorage.PenaltyRecord record : plugin.storage().penaltyHistory(target.getUniqueId(), 10)) {
                    lines.add(plugin.language().text("commands.penalty-line", plugin.language().vars(
                            "tier", record.tier(), "loss", format(record.loss()), "loss_percent", format(record.lossPercent()), "amount", format(record.amount()), "time", TIME_FORMAT.format(Instant.ofEpochMilli(record.createdAt()))
                    )));
                }
                Bukkit.getScheduler().runTask(plugin, () -> lines.forEach(sender::sendMessage));
            } catch (SQLException ex) {
                String msg = plugin.language().text("messages.trade-failed");
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
            }
        });
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("superstocks.admin")) {
            sender.sendMessage(plugin.language().text("commands.no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.language().text("commands.admin-usage"));
            return;
        }
        if (args[1].equalsIgnoreCase("panel")) {
            if (sender instanceof Player player) plugin.adminGui().open(player);
            else sender.sendMessage(plugin.language().text("commands.player-only"));
            return;
        }
        if (args[1].equalsIgnoreCase("audit")) {
            adminAudit(sender, args);
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.language().text("commands.admin-usage"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (args[1].equalsIgnoreCase("portfolio")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    List<Holding> holdings = plugin.storage().holdings(target.getUniqueId());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(plugin.language().text("commands.admin-portfolio-header", plugin.language().vars("player", displayName(target))));
                        sendPortfolio(sender, holdings);
                    });
                } catch (SQLException ex) {
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.language().text("messages.portfolio-load-failed")));
                }
            });
            return;
        }
        if (args[1].equalsIgnoreCase("clear")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    for (Holding holding : plugin.storage().holdings(target.getUniqueId())) {
                        plugin.storage().restoreHolding(target.getUniqueId(), holding.symbol(), 0.0D, holding.averageCost());
                    }
                    plugin.storage().audit(sender.getName(), "ADMIN_CLEAR", displayName(target));
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.language().text("commands.admin-cleared", plugin.language().vars("player", displayName(target)))));
                } catch (SQLException ex) {
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.language().text("messages.trade-failed")));
                }
            });
            return;
        }
        if (args.length < 5 || (!args[1].equalsIgnoreCase("give") && !args[1].equalsIgnoreCase("remove"))) {
            sender.sendMessage(plugin.language().text("commands.admin-usage"));
            return;
        }
        Double shares = parsePositive(args[4]);
        Optional<StockQuote> quote = plugin.stockService().quote(args[3]);
        if (shares == null || quote.isEmpty()) {
            sender.sendMessage(plugin.language().text("messages.quote-unavailable"));
            return;
        }
        boolean give = args[1].equalsIgnoreCase("give");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Optional<Holding> current = plugin.storage().holding(target.getUniqueId(), quote.get().symbol());
                double currentShares = current.map(Holding::shares).orElse(0.0D);
                double nextShares = give ? currentShares + shares : Math.max(0.0D, currentShares - shares);
                double average = current.map(Holding::averageCost).orElse(quote.get().price());
                plugin.storage().restoreHolding(target.getUniqueId(), quote.get().symbol(), nextShares, average);
                plugin.storage().audit(sender.getName(), give ? "ADMIN_GIVE" : "ADMIN_REMOVE",
                        displayName(target) + " " + quote.get().symbol() + " " + shares);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.language().text(
                        give ? "commands.admin-given" : "commands.admin-removed", plugin.language().vars(
                                "player", displayName(target), "symbol", quote.get().symbol(), "shares", TradeService.formatNumber(shares)))));
            } catch (SQLException ex) {
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.language().text("messages.trade-failed")));
            }
        });
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private void status(CommandSender sender) {
        if (!sender.hasPermission("superstocks.admin")) {
            sender.sendMessage(plugin.language().text("commands.no-permission"));
            return;
        }
        sender.sendMessage(plugin.language().text("commands.status-header"));
        sender.sendMessage(plugin.language().text("commands.status-line", plugin.language().vars(
                "provider", plugin.stockService().providerName(),
                "configured", plugin.stockService().configuredTotal(),
                "loaded", plugin.stockService().loadedTotal(),
                "last_sync", plugin.stockService().lastSync() == null ? "-" : TIME_FORMAT.format(plugin.stockService().lastSync()),
                "paused", plugin.stockService().paused(),
                "vault", plugin.economy().available()
        )));
    }

    private void pause(CommandSender sender, boolean paused) {
        if (!sender.hasPermission("superstocks.admin")) {
            sender.sendMessage(plugin.language().text("commands.no-permission"));
            return;
        }
        plugin.stockService().setPaused(paused);
        sender.sendMessage(plugin.language().text(paused ? "commands.paused" : "commands.resumed"));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("superstocks.admin")) {
            sender.sendMessage(plugin.language().text("commands.no-permission"));
            return;
        }
        plugin.reloadPlugin();
        sender.sendMessage(plugin.language().text("commands.reloaded"));
    }

    private void sync(CommandSender sender) {
        if (!sender.hasPermission("superstocks.admin")) {
            sender.sendMessage(plugin.language().text("commands.no-permission"));
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.stockService().refreshNow();
            String msg = plugin.language().text("commands.sync-started");
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
        });
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().text("commands.player-only"));
            return null;
        }
        return player;
    }

    private boolean requireUse(Player player) {
        if (!player.hasPermission("superstocks.use")) {
            player.sendMessage(plugin.language().text("commands.no-permission"));
            return false;
        }
        return true;
    }

    private void shortStock(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        if (args.length < 3) { sender.sendMessage(lang("commands.short-usage")); return; }
        Double shares = parsePositive(args[2]);
        if (shares == null) { sender.sendMessage(lang("commands.invalid-amount")); return; }
        try { sender.sendMessage(plugin.shortSelling().openShort(player, args[1], shares).message()); }
        catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
    }

    private void coverShort(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        if (args.length < 3) { sender.sendMessage(lang("commands.cover-usage")); return; }
        Long id = parseLong(args[1]);
        Double shares = args.length >= 3 ? parsePositive(args[2]) : null;
        if (id == null) { sender.sendMessage(lang("commands.invalid-amount")); return; }
        try { sender.sendMessage(plugin.shortSelling().coverShort(player, id, shares == null ? 0 : shares).message()); }
        catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
    }

    private void competitionCmd(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(lang("commands.competition-usage")); return; }
        Player player = sender instanceof Player p ? p : null;
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "list" -> {
                try { for (var c : plugin.competition().list()) sender.sendMessage(LanguageManager.color("&e#" + c.id() + " &f" + c.name() + " &7" + c.status())); }
                catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
            }
            case "create" -> {
                if (!sender.hasPermission("superstocks.admin")) { sender.sendMessage(lang("commands.no-permission")); return; }
                if (args.length < 5) { sender.sendMessage(lang("commands.competition-usage")); return; }
                String name = args[2]; Integer days = parseInt(args[3]); Double capital = parsePositive(args[4]);
                if (days == null || capital == null) { sender.sendMessage(lang("commands.invalid-amount")); return; }
                try { sender.sendMessage(plugin.competition().create(name, days, capital).message()); }
                catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
            }
            case "end" -> {
                if (!sender.hasPermission("superstocks.admin")) { sender.sendMessage(lang("commands.no-permission")); return; }
                if (args.length < 3) { sender.sendMessage(lang("commands.competition-usage")); return; }
                Integer id = parseInt(args[2]); if (id == null) return;
                try { sender.sendMessage(plugin.competition().end(id).message()); }
                catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
            }
            case "join" -> {
                if (player == null || !requireUse(player)) return;
                if (args.length < 3) { sender.sendMessage(lang("commands.competition-usage")); return; }
                Integer id = parseInt(args[2]); if (id == null) return;
                try { sender.sendMessage(plugin.competition().join(player, id).message()); }
                catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
            }
            case "buy", "sell" -> {
                if (player == null || !requireUse(player)) return;
                if (args.length < 5) { sender.sendMessage(lang("commands.competition-usage")); return; }
                Integer id = parseInt(args[2]); Double shares = parsePositive(args[4]);
                if (id == null || shares == null) return;
                try { var r = sub.equals("buy") ? plugin.competition().buy(player, id, args[3], shares) : plugin.competition().sell(player, id, args[3], shares); sender.sendMessage(r.message()); }
                catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
            }
            default -> sender.sendMessage(lang("commands.competition-usage"));
        }
    }

    private void ipoCmd(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(lang("commands.ipo-usage")); return; }
        Player player = sender instanceof Player p ? p : null;
        switch (args[1].toLowerCase()) {
            case "list" -> {
                try { for (var ipo : plugin.ipo().list()) sender.sendMessage(LanguageManager.color("&e#" + ipo.id() + " &f" + ipo.symbol() + " " + ipo.name() + " &7@" + format(ipo.price()) + " &7" + ipo.status())); }
                catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
            }
            case "create" -> {
                if (!sender.hasPermission("superstocks.admin")) { sender.sendMessage(lang("commands.no-permission")); return; }
                if (args.length < 8) { sender.sendMessage(lang("commands.ipo-usage")); return; }
                String symbol = args[2], name = args[3], market = args[4];
                Double price = parsePositive(args[5]), total = parsePositive(args[6]), maxPer = parsePositive(args[7]);
                if (price == null || total == null || maxPer == null) { sender.sendMessage(lang("commands.invalid-amount")); return; }
                long closesAt = System.currentTimeMillis() + 86400000L; // default 24h
                try { sender.sendMessage(plugin.ipo().create(symbol, name, market, price, total, maxPer, closesAt).message()); }
                catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
            }
            case "cancel" -> {
                if (!sender.hasPermission("superstocks.admin")) { sender.sendMessage(lang("commands.no-permission")); return; }
                if (args.length < 3) { sender.sendMessage(lang("commands.ipo-usage")); return; }
                Integer id = parseInt(args[2]); if (id == null) return;
                try { sender.sendMessage(plugin.ipo().cancel(id).message()); }
                catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
            }
            case "subscribe" -> {
                if (player == null || !requireUse(player)) return;
                if (args.length < 4) { sender.sendMessage(lang("commands.ipo-usage")); return; }
                Integer id = parseInt(args[2]); Double shares = parsePositive(args[3]);
                if (id == null || shares == null) return;
                try { sender.sendMessage(plugin.ipo().subscribe(player, id, shares).message()); }
                catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
            }
            default -> sender.sendMessage(lang("commands.ipo-usage"));
        }
    }

    private void certificateCmd(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        if (args.length >= 3 && args[1].equalsIgnoreCase("issue")) {
            Double shares = parsePositive(args[2]);
            if (shares == null) { sender.sendMessage(lang("commands.invalid-amount")); return; }
            String symbol = args.length >= 4 ? args[3] : "";
            if (symbol.isEmpty()) { sender.sendMessage(lang("commands.cert-usage")); return; }
            try {
                var holding = plugin.storage().holding(player.getUniqueId(), symbol);
                if (holding.isEmpty() || holding.get().shares() < shares) { sender.sendMessage(lang("messages.not-enough-shares")); return; }
                plugin.storage().restoreHolding(player.getUniqueId(), symbol, holding.get().shares() - shares, holding.get().averageCost());
                var item = plugin.certificate().createCertificateItem(player, symbol, shares);
                player.getInventory().addItem(item);
                sender.sendMessage(plugin.language().text("messages.certificate-issued", plugin.language().vars("symbol", symbol, "shares", TradeService.formatNumber(shares))));
            } catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            try { for (var c : plugin.certificate().list(player)) sender.sendMessage(LanguageManager.color("&e#" + c.id() + " &f" + c.symbol() + " x" + TradeService.formatNumber(c.shares()))); }
            catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
            return;
        }
        sender.sendMessage(lang("commands.cert-usage"));
    }

    private void indexCmd(CommandSender sender, String[] args) {
        Map<String, Double> prices = plugin.stockService().priceSnapshot();
        sender.sendMessage(lang("commands.index-header"));
        for (String market : plugin.stockService().marketNames().keySet()) {
            double sum = 0; int count = 0;
            for (String s : plugin.stockService().symbolsForMarket(market)) {
                Double p = prices.get(s);
                if (p != null && p > 0) { sum += p; count++; }
            }
            if (count > 0) sender.sendMessage(plugin.language().text("commands.index-line", plugin.language().vars("market", plugin.stockService().marketName(market), "index", format(sum / count), "count", count)));
        }
    }

    private void pinCmd(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        if (args.length < 2) { sender.sendMessage(lang("commands.pin-usage")); return; }
        plugin.marketReport().pin(player, args[1]);
    }

    private void unpinCmd(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !requireUse(player)) return;
        plugin.marketReport().unpin(player);
    }

    private void reportCmd(CommandSender sender) {
        sender.sendMessage(LanguageManager.color(plugin.marketReport().generateReport()));
    }

    private void adminAudit(CommandSender sender, String[] args) {
        String actor = args.length >= 3 ? args[2] : null;
        String action = args.length >= 4 ? args[3] : null;
        int limit = 20;
        try { for (var r : plugin.storage().auditLog(actor, action, limit, 0)) sender.sendMessage(LanguageManager.color("&7" + TF().format(Instant.ofEpochMilli(r.createdAt())) + " &f" + r.actor() + " &e" + r.action() + " &7" + r.detail())); }
        catch (SQLException ex) { sender.sendMessage(lang("messages.trade-failed")); }
    }

    private DateTimeFormatter TF() { return DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()); }

    private Integer parseInt(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; } }
    private String lang(String key) { return plugin.language().text(key); }

    private Double parsePositive(String input) {
        try {
            double value = Double.parseDouble(input);
            return value > 0.0D ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseLong(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private StockOrder.Type parseOrderType(String input) {
        return switch (input.toLowerCase()) {
            case "buy", "limit_buy", "limit-buy" -> StockOrder.Type.LIMIT_BUY;
            case "sell", "limit_sell", "limit-sell" -> StockOrder.Type.LIMIT_SELL;
            case "stop", "stop_loss", "stop-loss" -> StockOrder.Type.STOP_LOSS;
            case "take", "take_profit", "take-profit" -> StockOrder.Type.TAKE_PROFIT;
            default -> null;
        };
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "open", "buy", "sell", "quote", "portfolio", "rank", "history", "stats", "watch", "alert", "order", "short", "cover", "competition", "ipo", "certificate", "index", "pin", "unpin", "report"));
            if (sender.hasPermission("superstocks.admin")) {
                options.addAll(List.of("status", "pause", "resume", "reload", "sync", "penalty", "admin"));
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && List.of("buy", "sell", "quote", "price", "history").contains(args[0].toLowerCase())) {
            return filter(new ArrayList<>(plugin.stockService().symbols()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("order")) {
            return filter(List.of("buy", "sell", "stop-loss", "take-profit", "list", "cancel"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("watch")) {
            return filter(List.of("add", "remove", "list"), args[1]);
        }
        if (args.length == 2 && List.of("rank", "ranking", "top").contains(args[0].toLowerCase())) {
            return filter(List.of("winners", "losers"), args[1]);
        }
        if (args.length == 3 && List.of("buy", "sell").contains(args[0].toLowerCase())) {
            return filter(List.of("1", "10", "100"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }
}
