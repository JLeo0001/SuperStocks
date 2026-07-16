package cn.superstocks.gui;

import cn.superstocks.SuperStocksPlugin;
import cn.superstocks.TradeService;
import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.Holding;
import cn.superstocks.model.RankingEntry;
import cn.superstocks.model.StockQuote;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
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
import java.util.function.IntFunction;
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
        openMain(player, 0);
    }

    public void openMain(Player player, int requestedPage) {
        Inventory inventory = createInventory(size("gui.main-size", 45), lang().text("gui.main.title"));
        decorate(inventory);
        List<Integer> marketSlots = plugin.getConfig().getIntegerList("gui.layout.main-market-slots");
        if (marketSlots.isEmpty()) {
            marketSlots = contentSlots(inventory.getSize());
        }
        List<Map.Entry<String, String>> markets = new ArrayList<>(plugin.stockService().marketNames().entrySet());
        int pageSize = Math.max(1, marketSlots.size());
        int pages = Math.max(1, (markets.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int start = page * pageSize;
        for (int index = 0; index < pageSize && start + index < markets.size(); index++) {
            Map.Entry<String, String> entry = markets.get(start + index);
            inventory.setItem(marketSlots.get(index), marketItem(entry.getKey(), entry.getValue()));
        }
        addPageControls(inventory, page, pages, GuiAction::mainPage);
        inventory.setItem(slot("gui.layout.main-stats-slot", 13), statsItem());
        inventory.setItem(slot("gui.layout.main-winners-slot", 29), item(material("gui.winners-material", Material.NETHER_STAR),
                lang().text("gui.main.winners-name"),
                lang().list("gui.main.winners-lore"),
                GuiAction.ranking("winners")));
        inventory.setItem(slot("gui.layout.main-portfolio-slot", 31), item(material("gui.portfolio-material", Material.CHEST),
                lang().text("gui.main.portfolio-name"),
                lang().list("gui.main.portfolio-lore", commonStatsVars()),
                GuiAction.portfolio()));
        inventory.setItem(slot("gui.layout.main-losers-slot", 33), item(material("gui.losers-material", Material.WITHER_SKELETON_SKULL),
                lang().text("gui.main.losers-name"),
                lang().list("gui.main.losers-lore"),
                GuiAction.ranking("losers")));
        inventory.setItem(slot("gui.layout.main-watchlist-slot", 40), item(material("gui.watchlist-material", Material.SPYGLASS),
                lang().text("gui.main.watchlist-name"),
                lang().list("gui.main.watchlist-lore"),
                GuiAction.watchlist()));
        player.openInventory(inventory);
    }

    public void openMarket(Player player, String market) {
        openMarket(player, market, 0);
    }

    public void openMarket(Player player, String market, int requestedPage) {
        String title = lang().text("gui.market.title", lang().vars("market", plugin.stockService().marketName(market)));
        Inventory inventory = createInventory(size("gui.market-size", 54), title);
        decorate(inventory);
        inventory.setItem(slot("gui.layout.market-stats-slot", 4), marketStatsItem(market));
        List<Integer> contentSlots = contentSlots(inventory.getSize());
        List<String> symbols = plugin.stockService().symbolsForMarket(market);
        int pageSize = Math.max(1, contentSlots.size());
        int pages = Math.max(1, (symbols.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int start = page * pageSize;
        for (int index = 0; index < pageSize && start + index < symbols.size(); index++) {
            String symbol = symbols.get(start + index);
            int contentSlot = contentSlots.get(index);
            Optional<StockQuote> quote = plugin.stockService().quote(symbol);
            if (quote.isPresent()) {
                inventory.setItem(contentSlot, quoteItem(quote.get()));
            } else {
                inventory.setItem(contentSlot, item(material("gui.quote-missing-material", Material.GRAY_DYE),
                        lang().text("gui.market.missing-name", lang().vars("symbol", symbol)),
                        lang().list("gui.market.missing-lore"),
                        GuiAction.stock(symbol)));
            }
        }
        addPageControls(inventory, page, pages, next -> GuiAction.marketPage(market, next));
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
                    List<Integer> contentSlots = contentSlots(inventory.getSize());
                    int index = 0;
                    for (Holding holding : holdings) {
                        if (index >= contentSlots.size()) {
                            break;
                        }
                        Optional<StockQuote> quote = plugin.stockService().quote(holding.symbol());
                        inventory.setItem(contentSlots.get(index++), holdingItem(holding, quote.orElse(null)));
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

    public void openWatchlist(Player player, int requestedPage) {
        Inventory inventory = createInventory(size("gui.watchlist-size", 54), lang().text("gui.watchlist.title"));
        decorate(inventory);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<String> symbols = new ArrayList<>(plugin.automation().watchlist(player.getUniqueId()));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    List<Integer> slots = contentSlots(inventory.getSize());
                    int pageSize = Math.max(1, slots.size());
                    int pages = Math.max(1, (symbols.size() + pageSize - 1) / pageSize);
                    int page = Math.max(0, Math.min(requestedPage, pages - 1));
                    int start = page * pageSize;
                    for (int index = 0; index < pageSize && start + index < symbols.size(); index++) {
                        String symbol = symbols.get(start + index);
                        Optional<StockQuote> quote = plugin.stockService().quote(symbol);
                        inventory.setItem(slots.get(index), quote.map(this::quoteItem).orElseGet(() -> item(
                                material("gui.quote-missing-material", Material.GRAY_DYE),
                                lang().text("gui.market.missing-name", lang().vars("symbol", symbol)),
                                lang().list("gui.market.missing-lore"), GuiAction.stock(symbol))));
                    }
                    if (symbols.isEmpty()) {
                        inventory.setItem(firstContentEmpty(inventory), plainItem(Material.BARRIER, lang().text("gui.watchlist.empty-name")));
                    }
                    addPageControls(inventory, page, pages, GuiAction::watchlist);
                    inventory.setItem(slot("gui.layout.watchlist-back-slot", 49), item(material("gui.back-material", Material.ARROW),
                            lang().text("gui.common.back-name"), List.of(), GuiAction.market("__main__")));
                    player.openInventory(inventory);
                });
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "读取自选股失败", ex);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(lang().text("messages.watchlist-load-failed")));
            }
        });
    }

    public void openRanking(Player player, String type) {
        boolean winners = !"losers".equalsIgnoreCase(type);
        String titleKey = winners ? "gui.ranking.winners-title" : "gui.ranking.losers-title";
        Inventory inventory = createInventory(size("gui.ranking-size", 54), lang().text(titleKey));
        decorate(inventory);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<RankingEntry> entries = plugin.tradeService().rankings(plugin.stockService().priceSnapshot(), winners, plugin.getConfig().getInt("gui.ranking-limit", 10));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    List<Integer> contentSlots = contentSlots(inventory.getSize());
                    int index = 0;
                    for (RankingEntry entry : entries) {
                        if (index >= contentSlots.size()) {
                            break;
                        }
                        inventory.setItem(contentSlots.get(index), rankingItem(entry, index + 1, winners));
                        index++;
                    }
                    inventory.setItem(slot("gui.layout.ranking-back-slot", 49), item(material("gui.back-material", Material.ARROW), lang().text("gui.common.back-name"), List.of(), GuiAction.market("__main__")));
                    player.openInventory(inventory);
                });
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "读取排行榜失败", ex);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(lang().text("messages.ranking-load-failed")));
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
            case MAIN_PAGE -> openMain(player, parsePage(value));
            case MARKET_PAGE -> {
                String[] parts = value.split("\\|", 2);
                if (parts.length == 2) {
                    openMarket(player, parts[0], parsePage(parts[1]));
                }
            }
            case STOCK -> openStock(player, value);
            case PORTFOLIO -> openPortfolio(player);
            case WATCHLIST -> openWatchlist(player, parsePage(value));
            case RANKING -> openRanking(player, value);
            case BUY -> requestTrade(player, value, amount, true);
            case SELL -> requestTrade(player, value, amount, false);
            case CONFIRM_BUY -> executeTrade(player, value, amount, true);
            case CONFIRM_SELL -> executeTrade(player, value, amount, false);
        }
    }

    private void requestTrade(Player player, String symbol, double amount, boolean buy) {
        Optional<StockQuote> maybeQuote = plugin.stockService().quote(symbol);
        if (maybeQuote.isEmpty()) {
            player.sendMessage(lang().text("messages.quote-unavailable"));
            return;
        }
        double gross = maybeQuote.get().price() * amount;
        boolean confirmationEnabled = plugin.getConfig().getBoolean("gui.trade-confirmation.enabled", true);
        double threshold = Math.max(0.0D, plugin.getConfig().getDouble("gui.trade-confirmation.minimum-gross", 1000.0D));
        if (!confirmationEnabled || gross < threshold) {
            executeTrade(player, symbol, amount, buy);
            return;
        }
        openTradeConfirmation(player, maybeQuote.get(), amount, buy);
    }

    private void openTradeConfirmation(Player player, StockQuote quote, double shares, boolean buy) {
        Inventory inventory = createInventory(27, lang().text("gui.confirm.title"));
        decorate(inventory);
        Map<String, String> vars = lang().vars(
                "name", quote.name(),
                "symbol", quote.symbol(),
                "shares", TradeService.formatNumber(shares),
                "price", format(quote.price()),
                "gross", format(quote.price() * shares)
        );
        inventory.setItem(13, item(material("gui.quote-flat-material", Material.PAPER),
                lang().text("gui.confirm.summary-name", vars), lang().list("gui.confirm.summary-lore", vars), GuiAction.stock(quote.symbol())));
        inventory.setItem(11, item(Material.LIME_CONCRETE, lang().text("gui.confirm.accept-name"),
                lang().list("gui.confirm.accept-lore"), buy ? GuiAction.confirmBuy(quote.symbol(), shares) : GuiAction.confirmSell(quote.symbol(), shares)));
        inventory.setItem(15, item(Material.RED_CONCRETE, lang().text("gui.confirm.cancel-name"),
                lang().list("gui.confirm.cancel-lore"), GuiAction.stock(quote.symbol())));
        player.openInventory(inventory);
    }

    private void addPageControls(Inventory inventory, int page, int pages, IntFunction<GuiAction> actionFactory) {
        if (pages <= 1) {
            return;
        }
        int previousSlot = validSlot(plugin.getConfig().getInt("gui.layout.previous-page-slot", inventory.getSize() - 7), inventory.getSize(), inventory.getSize() - 7);
        int nextSlot = validSlot(plugin.getConfig().getInt("gui.layout.next-page-slot", inventory.getSize() - 3), inventory.getSize(), inventory.getSize() - 3);
        int pageSlot = validSlot(plugin.getConfig().getInt("gui.layout.page-info-slot", inventory.getSize() - 4), inventory.getSize(), inventory.getSize() - 4);
        Map<String, String> vars = lang().vars("page", page + 1, "pages", pages);
        inventory.setItem(pageSlot, plainItem(Material.PAPER, lang().text("gui.common.page-name", vars)));
        if (page > 0) {
            inventory.setItem(previousSlot, item(Material.ARROW, lang().text("gui.common.previous-page-name"), List.of(), actionFactory.apply(page - 1)));
        }
        if (page + 1 < pages) {
            inventory.setItem(nextSlot, item(Material.ARROW, lang().text("gui.common.next-page-name"), List.of(), actionFactory.apply(page + 1)));
        }
    }

    private int parsePage(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
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
        return item(materialName(plugin.stockService().marketIcon(market), Material.EMERALD), lang().text("gui.format.market-name", lang().vars("name", name)),
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
        List<String> lore = new ArrayList<>(lang().list("gui.stock.quote-lore", lang().vars(
                "market", plugin.stockService().marketName(quote.market()),
                "symbol", quote.symbol(),
                "price", format(quote.price()),
                "change", format(quote.change()),
                "change_percent", format(quote.changePercent()),
                "change_color", quote.change() >= 0 ? "&a" : "&c",
                "updated_at", TIME_FORMAT.format(quote.updatedAt())
        )));
        List<cn.superstocks.model.PricePoint> history = plugin.stockService().history(
                quote.symbol(), System.currentTimeMillis() - 86_400_000L, 16);
        if (history.size() >= 2) {
            double first = history.get(0).price();
            double change24h = first <= 0.0D ? 0.0D : (quote.price() - first) / first * 100.0D;
            lore.addAll(lang().list("gui.stock.history-lore", lang().vars(
                    "change_24h", format(change24h),
                    "trend", trend(history)
            )));
        }
        return item(material, lang().text("gui.format.stock-name", lang().vars("name", quote.name())), lore, GuiAction.stock(quote.symbol()));
    }

    private String trend(List<cn.superstocks.model.PricePoint> points) {
        String levels = "▁▂▃▄▅▆▇█";
        double min = points.stream().mapToDouble(cn.superstocks.model.PricePoint::price).min().orElse(0.0D);
        double max = points.stream().mapToDouble(cn.superstocks.model.PricePoint::price).max().orElse(min);
        StringBuilder result = new StringBuilder();
        for (cn.superstocks.model.PricePoint point : points) {
            int index = max <= min ? 3 : (int) Math.round((point.price() - min) / (max - min) * (levels.length() - 1));
            result.append(levels.charAt(Math.max(0, Math.min(levels.length() - 1, index))));
        }
        return result.toString();
    }

    private ItemStack rankingItem(RankingEntry entry, int rank, boolean winners) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(entry.playerId());
        String name = player.getName() == null ? entry.playerId().toString().substring(0, 8) : player.getName();
        String key = winners ? "gui.ranking.winner-lore" : "gui.ranking.loser-lore";
        Material material = winners ? material("gui.winners-material", Material.NETHER_STAR) : material("gui.losers-material", Material.WITHER_SKELETON_SKULL);
        return item(material, lang().text("gui.ranking.entry-name", lang().vars("rank", rank, "player", name)),
                lang().list(key, lang().vars(
                        "player", name,
                        "rank", rank,
                        "value", format(entry.portfolioValue()),
                        "profit", format(entry.profit()),
                        "profit_percent", format(entry.profitPercent()),
                        "profit_color", entry.profit() >= 0 ? "&a" : "&c"
                )), GuiAction.market("__main__"));
    }

    private ItemStack holdingItem(Holding holding, StockQuote quote) {
        if (quote == null) {
            return item(material("gui.holding-material", Material.PAPER), lang().text("gui.format.stock-symbol", lang().vars("symbol", holding.symbol())), lang().list("gui.portfolio.holding-missing-lore", lang().vars(
                    "symbol", holding.symbol(),
                    "shares", TradeService.formatNumber(holding.shares()),
                    "average_cost", format(holding.averageCost())
            )), GuiAction.portfolio());
        }
        return item(material("gui.holding-material", Material.PAPER), lang().text("gui.format.stock-name", lang().vars("name", quote.name())), lang().list("gui.portfolio.holding-lore", lang().vars(
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

    private List<Integer> contentSlots(int size) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            if (!isBorderSlot(slot, size) && slot != 4) {
                slots.add(slot);
            }
        }
        return slots;
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

    private int firstContentEmpty(Inventory inventory) {
        for (int slot : contentSlots(inventory.getSize())) {
            if (inventory.getItem(slot) == null) {
                return slot;
            }
        }
        return 13;
    }

    private int slot(String path, int fallback) {
        return plugin.getConfig().getInt(path, fallback);
    }

    private int validSlot(int configured, int size, int fallback) {
        return configured >= 0 && configured < size ? configured : fallback;
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
