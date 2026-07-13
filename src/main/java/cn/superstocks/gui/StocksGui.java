package cn.superstocks.gui;

import cn.superstocks.SuperStocksPlugin;
import cn.superstocks.TradeService;
import cn.superstocks.model.Holding;
import cn.superstocks.model.StockQuote;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public final class StocksGui implements Listener {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SuperStocksPlugin plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;
    private final NamespacedKey amountKey;

    public StocksGui(SuperStocksPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.valueKey = new NamespacedKey(plugin, "gui_value");
        this.amountKey = new NamespacedKey(plugin, "gui_amount");
    }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, color(plugin.getConfig().getString("gui.main-title", "SuperStocks")));
        int slot = 10;
        for (var entry : plugin.stockService().marketNames().entrySet()) {
            inventory.setItem(slot, item(Material.EMERALD, "&a" + entry.getValue(), List.of("&7点击查看该市场股票"), GuiAction.market(entry.getKey())));
            slot += 2;
        }
        inventory.setItem(22, item(Material.CHEST, "&e我的持仓", List.of("&7查看当前持仓和盈亏"), GuiAction.portfolio()));
        player.openInventory(inventory);
    }

    public void openMarket(Player player, String market) {
        String title = color(plugin.getConfig().getString("gui.market-title-prefix", "市场 - ") + plugin.stockService().marketName(market));
        Inventory inventory = Bukkit.createInventory(null, 54, title);
        int slot = 0;
        for (String symbol : plugin.stockService().symbolsForMarket(market)) {
            if (slot >= 45) {
                break;
            }
            Optional<StockQuote> quote = plugin.stockService().quote(symbol);
            if (quote.isPresent()) {
                inventory.setItem(slot++, quoteItem(quote.get()));
            } else {
                inventory.setItem(slot++, item(Material.GRAY_DYE, "&7" + symbol, List.of("&c暂无行情缓存", "&7等待下一次同步"), GuiAction.stock(symbol)));
            }
        }
        inventory.setItem(49, item(Material.ARROW, "&f返回", List.of(), GuiAction.market("__main__")));
        player.openInventory(inventory);
    }

    public void openStock(Player player, String symbol) {
        Optional<StockQuote> maybeQuote = plugin.stockService().quote(symbol);
        if (maybeQuote.isEmpty()) {
            player.sendMessage(color("&c该股票暂无行情，暂时不能交易。"));
            return;
        }
        StockQuote quote = maybeQuote.get();
        Inventory inventory = Bukkit.createInventory(null, 27, color("股票 - " + quote.name()));
        inventory.setItem(4, quoteItem(quote));
        inventory.setItem(10, item(Material.LIME_DYE, "&a买入 1 股", List.of("&7按当前缓存价成交"), GuiAction.buy(symbol, 1.0D)));
        inventory.setItem(11, item(Material.LIME_DYE, "&a买入 10 股", List.of("&7按当前缓存价成交"), GuiAction.buy(symbol, 10.0D)));
        inventory.setItem(12, item(Material.LIME_DYE, "&a买入 100 股", List.of("&7按当前缓存价成交"), GuiAction.buy(symbol, 100.0D)));
        inventory.setItem(14, item(Material.RED_DYE, "&c卖出 1 股", List.of("&7按当前缓存价成交"), GuiAction.sell(symbol, 1.0D)));
        inventory.setItem(15, item(Material.RED_DYE, "&c卖出 10 股", List.of("&7按当前缓存价成交"), GuiAction.sell(symbol, 10.0D)));
        inventory.setItem(16, item(Material.RED_DYE, "&c卖出 100 股", List.of("&7按当前缓存价成交"), GuiAction.sell(symbol, 100.0D)));
        inventory.setItem(22, item(Material.ARROW, "&f返回主界面", List.of(), GuiAction.market("__main__")));
        player.openInventory(inventory);
    }

    public void openPortfolio(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, color(plugin.getConfig().getString("gui.portfolio-title", "我的持仓")));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Holding> holdings = plugin.tradeService().holdings(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int slot = 0;
                    for (Holding holding : holdings) {
                        if (slot >= 45) {
                            break;
                        }
                        Optional<StockQuote> quote = plugin.stockService().quote(holding.symbol());
                        inventory.setItem(slot++, holdingItem(holding, quote.orElse(null)));
                    }
                    inventory.setItem(49, item(Material.ARROW, "&f返回", List.of(), GuiAction.market("__main__")));
                    player.openInventory(inventory);
                });
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "读取持仓失败", ex);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(color("&c读取持仓失败，请联系管理员。")));
            }
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getCurrentItem();
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String action = data.get(actionKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        event.setCancelled(true);
        String value = data.getOrDefault(valueKey, PersistentDataType.STRING, "");
        double amount = data.getOrDefault(amountKey, PersistentDataType.DOUBLE, 0.0D);
        switch (GuiAction.ActionType.valueOf(action)) {
            case MARKET -> {
                if ("__main__".equals(value)) {
                    openMain(player);
                } else {
                    openMarket(player, value);
                }
            }
            case STOCK -> openStock(player, value);
            case PORTFOLIO -> openPortfolio(player);
            case BUY -> executeTrade(player, value, amount, true);
            case SELL -> executeTrade(player, value, amount, false);
        }
    }

    private void executeTrade(Player player, String symbol, double amount, boolean buy) {
        Optional<StockQuote> maybeQuote = plugin.stockService().quote(symbol);
        if (maybeQuote.isEmpty()) {
            player.sendMessage(color("&c该股票暂无行情，暂时不能交易。"));
            return;
        }
        StockQuote quote = maybeQuote.get();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                TradeService.TradeResult result = buy
                        ? plugin.tradeService().buy(player, quote, amount)
                        : plugin.tradeService().sell(player, quote, amount);
                player.sendMessage(color((result.success() ? "&a" : "&c") + result.message()));
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "交易失败", ex);
                player.sendMessage(color("&c交易失败，请联系管理员。"));
            }
        });
    }

    private ItemStack quoteItem(StockQuote quote) {
        Material material = quote.change() >= 0.0D ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        List<String> lore = new ArrayList<>();
        lore.add("&7代码: &f" + quote.symbol());
        lore.add("&7价格: &f" + format(quote.price()));
        lore.add("&7涨跌: " + (quote.change() >= 0 ? "&a" : "&c") + format(quote.change()) + " (" + format(quote.changePercent()) + "%)");
        lore.add("&7更新时间: &f" + TIME_FORMAT.format(quote.updatedAt()));
        lore.add("&e点击交易");
        return item(material, "&f" + quote.name(), lore, GuiAction.stock(quote.symbol()));
    }

    private ItemStack holdingItem(Holding holding, StockQuote quote) {
        List<String> lore = new ArrayList<>();
        lore.add("&7代码: &f" + holding.symbol());
        lore.add("&7数量: &f" + TradeService.formatNumber(holding.shares()));
        lore.add("&7成本: &f" + format(holding.averageCost()));
        if (quote != null) {
            lore.add("&7现价: &f" + format(quote.price()));
            lore.add("&7市值: &f" + format(holding.marketValue(quote.price())));
            lore.add("&7盈亏: " + (holding.profit(quote.price()) >= 0 ? "&a" : "&c") + format(holding.profit(quote.price())));
        } else {
            lore.add("&c暂无行情缓存");
        }
        return item(Material.PAPER, "&f" + holding.symbol(), lore, quote == null ? GuiAction.portfolio() : GuiAction.stock(holding.symbol()));
    }

    private ItemStack item(Material material, String name, List<String> lore, GuiAction action) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(StocksGui::color).toList());
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(actionKey, PersistentDataType.STRING, action.type().name());
        data.set(valueKey, PersistentDataType.STRING, action.value());
        data.set(amountKey, PersistentDataType.DOUBLE, action.amount());
        stack.setItemMeta(meta);
        return stack;
    }

    public static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }
}
