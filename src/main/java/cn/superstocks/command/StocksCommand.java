package cn.superstocks.command;

import cn.superstocks.SuperStocksPlugin;
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
                sender.sendMessage(plugin.language().text("commands.no-permission"));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(plugin.language().text("commands.reloaded"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("sync")) {
            if (!sender.hasPermission("superstocks.admin")) {
                sender.sendMessage(plugin.language().text("commands.no-permission"));
                return true;
            }
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.stockService().refreshNow();
                sender.sendMessage(plugin.language().text("commands.sync-started"));
            });
            return true;
        }
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
