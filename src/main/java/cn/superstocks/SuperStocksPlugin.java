package cn.superstocks;

import cn.superstocks.command.StocksCommand;
import cn.superstocks.economy.VaultEconomyHook;
import cn.superstocks.gui.StocksGui;
import cn.superstocks.lang.LanguageManager;
import cn.superstocks.placeholder.SuperStocksExpansion;
import cn.superstocks.stock.StockService;
import cn.superstocks.storage.SqliteStockStorage;
import cn.superstocks.storage.StockStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public final class SuperStocksPlugin extends JavaPlugin {
    private VaultEconomyHook economy;
    private StockStorage storage;
    private LanguageManager language;
    private StockService stockService;
    private TradeService tradeService;
    private StocksGui gui;
    private SuperStocksExpansion expansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("无法创建插件数据目录");
        }

        language = new LanguageManager(this);
        language.load();

        economy = new VaultEconomyHook(this);
        if (!economy.setup()) {
            getLogger().warning("未找到 Vault 经济服务，交易功能将不可用。请安装 Vault 和 EssentialsX Economy 或其他经济插件。");
        }

        storage = new SqliteStockStorage(this);
        try {
            storage.init();
        } catch (SQLException ex) {
            getLogger().log(Level.SEVERE, "初始化 SQLite 数据库失败，插件将被禁用", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        stockService = new StockService(this);
        tradeService = new TradeService(
                economy,
                storage,
                language,
                getConfig().getDouble("economy.transaction-tax-percent", 0.5D),
                getConfig().getDouble("economy.min-shares", 1.0D)
        );
        gui = new StocksGui(this);

        StocksCommand command = new StocksCommand(this);
        PluginCommand stocksCommand = getCommand("stocks");
        if (stocksCommand != null) {
            stocksCommand.setExecutor(command);
            stocksCommand.setTabCompleter(command);
        }
        getServer().getPluginManager().registerEvents(gui, this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            expansion = new SuperStocksExpansion(this);
            expansion.register();
            getLogger().info("已注册 PlaceholderAPI 占位符。");
        }

        stockService.start();
        getServer().getScheduler().runTaskAsynchronously(this, stockService::refreshNow);
        getLogger().info("SuperStocks 已启用。");
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
        if (storage != null) {
            try {
                storage.close();
            } catch (SQLException ex) {
                getLogger().log(Level.WARNING, "关闭 SQLite 数据库失败", ex);
            }
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        language.load();
        stockService.reload();
        tradeService = new TradeService(
                economy,
                storage,
                language,
                getConfig().getDouble("economy.transaction-tax-percent", 0.5D),
                getConfig().getDouble("economy.min-shares", 1.0D)
        );
        getServer().getScheduler().runTaskAsynchronously(this, stockService::refreshNow);
    }

    public VaultEconomyHook economy() {
        return economy;
    }

    public StockStorage storage() {
        return storage;
    }

    public LanguageManager language() {
        return language;
    }

    public StockService stockService() {
        return stockService;
    }

    public TradeService tradeService() {
        return tradeService;
    }

    public StocksGui gui() {
        return gui;
    }
}
