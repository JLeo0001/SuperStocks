package cn.superstocks.gui;

import cn.superstocks.SuperStocksPlugin;
import cn.superstocks.TradeService;
import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.*;
import cn.superstocks.storage.StockStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

public final class AdminGui {
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final SuperStocksPlugin plugin;

    public AdminGui(SuperStocksPlugin plugin) { this.plugin = plugin; }

    public void open(Player player) {
        if (!player.hasPermission("superstocks.admin")) { player.sendMessage(lang().text("commands.no-permission")); return; }
        Inventory inv = Bukkit.createInventory(new StocksInventoryHolder(), 54, lang().text("gui.admin.title"));
        decorate(inv);
        int idx = 10;
        inv.setItem(idx++, statItem(Material.EMERALD, "gui.admin.total-holdings", "holdings", countHoldings()));
        inv.setItem(idx++, statItem(Material.GOLD_INGOT, "gui.admin.total-players", "players", countActivePlayers()));
        inv.setItem(idx++, statItem(Material.PAPER, "gui.admin.total-trades", "trades", "?"));
        inv.setItem(idx++, statItem(Material.CLOCK, "gui.admin.last-sync", "time", plugin.stockService().lastSync() == null ? "-" : TF.format(plugin.stockService().lastSync())));
        inv.setItem(idx++, statItem(Material.REDSTONE, "gui.admin.paused", "state", plugin.stockService().paused() ? "YES" : "NO"));
        inv.setItem(idx++, statItem(Material.DIAMOND, "gui.admin.vault", "state", plugin.economy().available() ? "OK" : "MISSING"));
        inv.setItem(49, item(Material.ARROW, lang().text("gui.common.back-name"), List.of(), GuiAction.market("__main__")));
        inv.setItem(53, item(Material.BARRIER, lang().text("gui.admin.close-name"), List.of(), GuiAction.market("__main__")));
        player.openInventory(inv);
    }

    private int countHoldings() { try { return plugin.storage().allHoldings().size(); } catch (Exception ignored) { return 0; } }
    private int countActivePlayers() { try { Set<UUID> set = new HashSet<>(); for (Holding h : plugin.storage().allHoldings()) set.add(h.playerId()); return set.size(); } catch (Exception ignored) { return 0; } }

    private ItemStack statItem(Material mat, String key, String label, Object value) {
        String name = lang().text(key);
        List<String> lore = List.of(lang().text("gui.admin.stat-format", lang().vars("label", label, "value", String.valueOf(value))));
        return item(mat, name, lore, GuiAction.market("__main__"));
    }

    private ItemStack item(Material mat, String name, List<String> lore, GuiAction action) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(LanguageManager.color(name));
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private void decorate(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta(); fm.setDisplayName(" "); filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) if (i / 9 == 0 || i / 9 == 5 || i % 9 == 0 || i % 9 == 8) inv.setItem(i, filler);
    }

    private LanguageManager lang() { return plugin.language(); }
}
