package cn.superstocks.command;

import cn.superstocks.SuperStocksPlugin;
import cn.superstocks.TradeService;
import cn.superstocks.model.Holding;
import cn.superstocks.model.RankingEntry;
import cn.superstocks.model.StockQuote;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
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
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.language().text("commands.player-only"));
                return true;
            }
            if (!player.hasPermission("superstocks.use")) {
                player.sendMessage(plugin.language().text("commands.no-permission"));
                return true;
            }
            plugin.gui().openMain(player);
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
            case "reload" -> reload(sender);
            case "sync" -> sync(sender);
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().text("commands.player-only"));
            return;
        }
        if (!player.hasPermission("superstocks.use")) {
            player.sendMessage(plugin.language().text("commands.no-permission"));
            return;
        }
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
            TradeService.TradeResult result = plugin.tradeService().buy(player, quote.get(), shares);
            sender.sendMessage(result.message());
        } catch (SQLException ex) {
            plugin.getLogger().warning("Command buy failed: " + ex.getMessage());
            sender.sendMessage(plugin.language().text("messages.trade-failed"));
        }
    }

    private void sell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().text("commands.player-only"));
            return;
        }
        if (!player.hasPermission("superstocks.use")) {
            player.sendMessage(plugin.language().text("commands.no-permission"));
            return;
        }
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
            TradeService.TradeResult result = plugin.tradeService().sell(player, quote.get(), shares);
            sender.sendMessage(result.message());
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.language().text("commands.player-only"));
            return;
        }
        if (!player.hasPermission("superstocks.use")) {
            player.sendMessage(plugin.language().text("commands.no-permission"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Holding> holdings = plugin.tradeService().holdings(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
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
                });
            } catch (SQLException ex) {
                plugin.getLogger().warning("Command portfolio failed: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.language().text("messages.portfolio-load-failed")));
            }
        });
    }

    private void ranking(CommandSender sender, String[] args) {
        boolean winners = args.length < 2 || !args[1].equalsIgnoreCase("losers");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<RankingEntry> entries = plugin.tradeService().rankings(plugin.stockService().priceSnapshot(), winners, plugin.getConfig().getInt("gui.ranking-limit", 10));
                Bukkit.getScheduler().runTask(plugin, () -> {
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
                });
            } catch (SQLException ex) {
                plugin.getLogger().warning("Command rank failed: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.language().text("messages.ranking-load-failed")));
            }
        });
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
            sender.sendMessage(plugin.language().text("commands.sync-started"));
        });
    }

    private Double parsePositive(String input) {
        try {
            double value = Double.parseDouble(input);
            return value > 0.0D ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "open", "buy", "sell", "quote", "portfolio", "rank"));
            if (sender.hasPermission("superstocks.admin")) {
                options.add("reload");
                options.add("sync");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && List.of("buy", "sell", "quote", "price").contains(args[0].toLowerCase())) {
            return filter(new ArrayList<>(plugin.stockService().priceSnapshot().keySet()), args[1]);
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
