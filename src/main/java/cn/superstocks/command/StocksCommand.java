package cn.superstocks.command;

import cn.superstocks.SuperStocksPlugin;
import cn.superstocks.gui.StocksGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class StocksCommand implements CommandExecutor, TabCompleter {
    private final SuperStocksPlugin plugin;

    public StocksCommand(SuperStocksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("superstocks.admin")) {
                sender.sendMessage(StocksGui.color("&c你没有权限执行该命令。"));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(StocksGui.color("&aSuperStocks 已重载。"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("sync")) {
            if (!sender.hasPermission("superstocks.admin")) {
                sender.sendMessage(StocksGui.color("&c你没有权限执行该命令。"));
                return true;
            }
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.stockService().refreshNow();
                sender.sendMessage(StocksGui.color("&a行情同步任务已执行。"));
            });
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the stock GUI.");
            return true;
        }
        if (!player.hasPermission("superstocks.use")) {
            player.sendMessage(StocksGui.color("&c你没有权限使用股市。"));
            return true;
        }
        plugin.gui().openMain(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("superstocks.admin")) {
            return List.of();
        }
        List<String> completions = new ArrayList<>();
        for (String option : List.of("reload", "sync")) {
            if (option.startsWith(args[0].toLowerCase())) {
                completions.add(option);
            }
        }
        return completions;
    }
}
