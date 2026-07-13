package cn.superstocks.gui;

import cn.superstocks.SuperStocksPlugin;
import cn.superstocks.TradeService;
import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.Holding;
import cn.superstocks.model.StockQuote;
import org.bukkit.Bukkit;
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
import java.util.Map;
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
        Inventory inventory = createInventory(size("gui.main-size", 27), lang().text("gui.main.title"));
        decorate(inventory);
        List<Integer> marketSlots = plugin.getConfig().getIntegerList("gui.layout.main-market-slots");
        int index = 0;
        for (Map.Entry<String, String> entry : plugin.stockService().marketNames().entrySet()) {
            int slot = index < marketSlots.size() ? marketSlots.get(index) : firstEmpty(inventory);
            inventory.setItem(slot, marketItem(entry.getKey(), entry.getValue()));
            index++;
        }
        inventory.setItem(slot("gui.layout.main-stats-slot", 4), statsItem());
        inventory.setItem(slot("gui.layout.main-portfolio-slot", 22), item(material("gui.portfolio-material", Material.CHEST),
                lang().text("gui.main.portfolio-name"),
                lang().list("gui.main.portfolio-lore", commonStatsVars()),
                GuiAction.portfolio()));
        player.openInventory(inventory);
    }

    public void openMarket(Player player, String market) {
        String title = lang().text("gui.market.title", lang().vars("market", plugin.stockService().marketName(market)));
        Inventory inventory = createInventory(size("gui.market-size", 54), title);
        decorate(inventory);
        inventory.setItem(slot("gui.layout.market-stats-slot", 4), marketStatsItem(market));
        int slot = 9;
        for (String symbol : plugin.stockService().symbolsForMarket(market)) {
            if (slot >= inventory.getSize() - 9) {
                break;
            }
            Optional<StockQuote> quote = plugin.stockService().quote(symbol);
            if (quote.isPresent()) {
                inventory.setItem(slot, quoteItem(quote.get()));
            } else {
                inventory.setItem(slot, item(material("gui.quote-missing-material", Material.GRAY_DYE),
                        lang().text("gui.market.missing-name", lang().vars("symbol", symbol)),
                        lang().list("gui.market.missing-lore"),
                        GuiAction.stock(symbol)));
            }
            slot++;
        }
        inventory.setItem(slot("gui.layout.market-back-slot", 49), item(material("gui.back-material", Material.ARROW), lang().text("gui.common.back-name"), List.of(), GuiAction.market("__main__")));
        player.openInventory(inventory);
    }

    public void openStock(Player player, String symbol) {
        Optional<StockQuote> maybeQuote = plugin.stockService().quote(symbol);
        if (maybeQuote.isEmpty()) {
            player.sendMessage(lang().text("messages.quote-unavailable"));
            return;
        }
        StockQuote quote = maybeQuote.get();
        Inventory inventory = createInventory(size("gui.stock-size", 27), lang().text("gui.stock.title", lang().vars("name", quote.name())));
        decorate(inventory);
        inventory.setItem(slot("gui.layout.stock-quote-slot", 4), quoteItem(quote));
        List<Double> amounts = tradeAmounts();
        int[] buySlots = {10, 11, 12};
        int[] sellSlots = {14, 15, 16};
        for (int i = 0; i < amounts.size() && i < 3; i++) {
            double shares = amounts.get(i);
            inventory.setItem(buySlots[i], item(material("gui.buy-material", Material.LIME_DYE),
                    lang().text("gui.stock.buy-name", lang().vars("shares", TradeService.formatNumber(shares))),
                    lang().list("gui.stock.buy-lore", lang().vars("amount", format(quote.price() * shares))),
                    GuiAction.buy(symbol, shares)));
            inventory.setItem(sellSlots[i], item(material("gui.sell-material", Material.RED_DYE),
                    lang().text("gui.stock.sell-name", lang().vars("shares", TradeService.formatNumber(shares))),
                    lang().list("gui.stock.sell-lore", lang().vars("amount", format(quote.price() * shares))),
                    GuiAction.sell(symbol, shares)));
        }
        inventory.setItem(slot("gui.layout.stock-back-slot", 22), item(material("gui.back-material", Material.ARROW), lang().text("gui.common.back-main-name"), List.of(), GuiAction.market("__main__")));
        player.openInventory(inventory);
    }

    public void openPortfolio(Player player) {
        Inventory inventory = createInventory(size("gui.portfolio-size", 54), lang().text("gui.portfolio.title"));
        decorate(inventory);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Holding> holdings = plugin.tradeService().holdings(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int slot = 9;
                    for (Holding holding : holdings) {
                        if (slot >= inventory.getSize() - 9) {
                            break;
                        }
                        Optional<StockQuote> quote = plugin.stockService().quote(holding.symbol());
                        inventory.setItem(slot++, holdingItem(holding, quote.orElse(null)));
                    }
                    inventory.setItem(slot("gui.layout.portfolio-back-slot", 49), item(material("gui.back-material", Material.ARROW), lang().text("gui.common.back-name"), List.of(), GuiAction.market("__main__")));
                    player.openInventory(inventory);
                });
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "读取持仓失败", ex);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(lang().text("messages.portfolio-load-failed")));
            }
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof StocksInventoryHolder)) {
            return;
        }
        event.setCancelled(true);
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
            player.sendMessage(lang().text("messages.quote-unavailable"));
            return;
        }
        StockQuote quote = maybeQuote.get();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                TradeService.TradeResult result = buy
                        ? plugin.tradeService().buy(player, quote, amount)
                        : plugin.tradeService().sell(player, quote, amount);
                player.sendMessage((result.success() ? "" : "") + result.message());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "交易失败", ex);
                player.sendMessage(lang().text("messages.trade-failed"));
            }
        });
    }

    private ItemStack marketItem(String market, String name) {
        return item(materialName(plugin.stockService().marketIcon(market), Material.EMERALD), "&a" + name,
                lang().list("gui.main.market-lore", lang().vars(
                        "market", name,
                        "configured", plugin.stockService().configuredCount(market),
                        "loaded", plugin.stockService().loadedCount(market)
                )), GuiAction.market(market));
    }

    private ItemStack statsItem() {
        return item(material("gui.stats-material", Material.KNOWLEDGE_BOOK), lang().text("gui.main.stats-name"), lang().list("gui.main.stats-lore", commonStatsVars()), GuiAction.market("__main__"));
    }

    private ItemStack marketStatsItem(String market) {
        return item(material("gui.stats-material", Material.KNOWLEDGE_BOOK), lang().text("gui.market.stats-name", lang().vars("market", plugin.stockService().marketName(market))),
                lang().list("gui.market.stats-lore", lang().vars(
                        "configured", plugin.stockService().configuredCount(market),
                        "loaded", plugin.stockService().loadedCount(market),
                        "provider", plugin.stockService().providerName()
                )), GuiAction.market(market));
    }

    private ItemStack quoteItem(StockQuote quote) {
        Material material = quote.change() > 0.0D
                ? material("gui.quote-up-material", Material.LIME_CONCRETE)
                : quote.change() < 0.0D ? material("gui.quote-down-material", Material.RED_CONCRETE) : material("gui.quote-flat-material", Material.WHITE_CONCRETE);
        return item(material, "&f" + quote.name(), lang().list("gui.stock.quote-lore", lang().vars(
                "market", plugin.stockService().marketName(quote.market()),
                "symbol", quote.symbol(),
                "price", format(quote.price()),
                "change", format(quote.change()),
                "change_percent", format(quote.changePercent()),
                "change_color", quote.change() >= 0 ? "&a" : "&c",
                "updated_at", TIME_FORMAT.format(quote.updatedAt())
        )), GuiAction.stock(quote.symbol()));
    }

    private ItemStack holdingItem(Holding holding, StockQuote quote) {
        if (quote == null) {
            return item(material("gui.holding-material", Material.PAPER), "&f" + holding.symbol(), lang().list("gui.portfolio.holding-missing-lore", lang().vars(
                    "symbol", holding.symbol(),
                    "shares", TradeService.formatNumber(holding.shares()),
                    "average_cost", format(holding.averageCost())
            )), GuiAction.portfolio());
        }
        return item(material("gui.holding-material", Material.PAPER), "&f" + quote.name(), lang().list("gui.portfolio.holding-lore", lang().vars(
                "symbol", holding.symbol(),
                "shares", TradeService.formatNumber(holding.shares()),
                "average_cost", format(holding.averageCost()),
                "price", format(quote.price()),
                "market_value", format(holding.marketValue(quote.price())),
                "profit", format(holding.profit(quote.price())),
                "profit_color", holding.profit(quote.price()) >= 0 ? "&a" : "&c"
        )), GuiAction.stock(holding.symbol()));
    }

    private void decorate(Inventory inventory) {
        if (!plugin.getConfig().getBoolean("gui.fill-empty-slots", true)) {
            return;
        }
        ItemStack filler = plainItem(material("gui.filler-material", Material.GRAY_STAINED_GLASS_PANE), lang().text("gui.common.filler-name"));
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isBorderSlot(i, inventory.getSize())) {
                inventory.setItem(i, filler);
            }
        }
    }

    private boolean isBorderSlot(int slot, int size) {
        int row = slot / 9;
        int col = slot % 9;
        int rows = size / 9;
        return row == 0 || row == rows - 1 || col == 0 || col == 8;
    }

    private Inventory createInventory(int size, String title) {
        StocksInventoryHolder holder = new StocksInventoryHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.inventory(inventory);
        return inventory;
    }

    private ItemStack item(Material material, String name, List<String> lore, GuiAction action) {
        ItemStack stack = plainItem(material, name);
        ItemMeta meta = stack.getItemMeta();
        meta.setLore(lore);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(actionKey, PersistentDataType.STRING, action.type().name());
        data.set(valueKey, PersistentDataType.STRING, action.value());
        data.set(amountKey, PersistentDataType.DOUBLE, action.amount());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack plainItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(LanguageManager.color(name));
        stack.setItemMeta(meta);
        return stack;
    }

    private Map<String, String> commonStatsVars() {
        return lang().vars(
                "provider", plugin.stockService().providerName(),
                "configured_total", plugin.stockService().configuredTotal(),
                "loaded_total", plugin.stockService().loadedTotal(),
                "last_fetched", plugin.stockService().lastFetchedCount(),
                "refresh_seconds", plugin.getConfig().getLong("stock-provider.refresh-seconds", 300L),
                "last_sync", plugin.stockService().lastSync() == null ? "-" : TIME_FORMAT.format(plugin.stockService().lastSync())
        );
    }

    private List<Double> tradeAmounts() {
        List<Double> amounts = new ArrayList<>();
        for (Object raw : plugin.getConfig().getList("gui.trade-amounts", List.of(1, 10, 100))) {
            if (raw instanceof Number number) {
                amounts.add(number.doubleValue());
                continue;
            }
            try {
                amounts.add(Double.parseDouble(String.valueOf(raw)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (amounts.isEmpty()) {
            amounts.addAll(List.of(1.0D, 10.0D, 100.0D));
        }
        return amounts;
    }

    private int firstEmpty(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                return i;
            }
        }
        return 0;
    }

    private int slot(String path, int fallback) {
        return plugin.getConfig().getInt(path, fallback);
    }

    private int size(String path, int fallback) {
        int value = plugin.getConfig().getInt(path, fallback);
        if (value < 9 || value > 54 || value % 9 != 0) {
            return fallback;
        }
        return value;
    }

    private Material material(String path, Material fallback) {
        return materialName(plugin.getConfig().getString(path, fallback.name()), fallback);
    }

    private Material materialName(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        return material == null ? fallback : material;
    }

    private LanguageManager lang() {
        return plugin.language();
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }
}
