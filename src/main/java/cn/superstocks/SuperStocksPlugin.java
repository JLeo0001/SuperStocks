package cn.superstocks;

import cn.superstocks.command.StocksCommand;
import cn.superstocks.economy.VaultEconomyHook;
import cn.superstocks.gui.AdminGui;
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
    private VaultEconomyHook economy; private StockStorage storage; private LanguageManager language;
    private StockService stockService; private TradeService tradeService;
    private LossPenaltyService lossPenaltyService; private AutomationService automationService;
    private ShortSellingService shortSellingService; private CompetitionService competitionService;
    private IpoService ipoService; private CertificateService certificateService;
    private MarketReportService marketReportService; private AdminGui adminGui;
    private StocksGui gui; private SuperStocksExpansion expansion;

    @Override public void onEnable() {
        saveDefaultConfig(); new ConfigMigrator(this).migrate();
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        language = new LanguageManager(this); language.load();
        economy = new VaultEconomyHook(this);
        if (!economy.setup()) getLogger().warning("Vault not found, economy disabled.");
        storage = new SqliteStockStorage(this);
        try { storage.init(); } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Database init failed", e);
            getServer().getPluginManager().disablePlugin(this); return;
        }
        // Database migration and health check
        DatabaseMigrator dbMigrator = new DatabaseMigrator(storage, getLogger());
        dbMigrator.migrate();
        getLogger().info("========== SuperStocks 数据库自检 ==========");
        for (String line : dbMigrator.healthCheck()) {
            getLogger().info(line);
        }
        getLogger().info("============================================");
        stockService = new StockService(this, storage, language);
        tradeService = new TradeService(this, economy, storage, language, stockService);
        lossPenaltyService = new LossPenaltyService(this, storage, stockService, economy);
        automationService = new AutomationService(this, storage, stockService, tradeService, economy);
        shortSellingService = new ShortSellingService(this, storage, stockService, economy, language);
        competitionService = new CompetitionService(this, storage, stockService, economy, language);
        ipoService = new IpoService(this, storage, stockService, economy, language);
        certificateService = new CertificateService(this, storage, stockService, economy, language);
        marketReportService = new MarketReportService(this, stockService, language);
        adminGui = new AdminGui(this);
        gui = new StocksGui(this);
        StocksCommand command = new StocksCommand(this);
        PluginCommand stocks = getCommand("stocks");
        if (stocks != null) { stocks.setExecutor(command); stocks.setTabCompleter(command); }
        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(certificateService, this);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            expansion = new SuperStocksExpansion(this); expansion.register();
        }
        new ConfigValidator(this, language).log();
        startServices();
        getLogger().info("SuperStocks enabled.");
    }

    private void startServices() {
        stockService.start(); lossPenaltyService.start(); automationService.start();
        shortSellingService.start(); ipoService.start(); marketReportService.start();
        getServer().getScheduler().runTaskAsynchronously(this, stockService::refreshNow);
    }

    @Override public void onDisable() {
        if (marketReportService != null) marketReportService.stop();
        if (ipoService != null) ipoService.stop();
        if (shortSellingService != null) shortSellingService.stop();
        if (automationService != null) automationService.stop();
        if (lossPenaltyService != null) lossPenaltyService.stop();
        if (stockService != null) stockService.stopTasks();
        if (expansion != null) expansion.unregister();
        if (storage != null) try { storage.close(); } catch (SQLException e) { getLogger().log(Level.WARNING, "Close DB failed", e); }
    }

    public void reloadPlugin() {
        if (marketReportService != null) marketReportService.stop();
        if (ipoService != null) ipoService.stop();
        if (shortSellingService != null) shortSellingService.stop();
        if (automationService != null) automationService.stop();
        if (lossPenaltyService != null) lossPenaltyService.stop();
        if (stockService != null) stockService.stopTasks();
        reloadConfig(); new ConfigMigrator(this).migrate(); language.load();
        // Database health on reload
        DatabaseMigrator dbMigrator = new DatabaseMigrator(storage, getLogger());
        dbMigrator.migrate();
        stockService.reload();
        tradeService = new TradeService(this, economy, storage, language, stockService);
        lossPenaltyService = new LossPenaltyService(this, storage, stockService, economy);
        automationService = new AutomationService(this, storage, stockService, tradeService, economy);
        shortSellingService = new ShortSellingService(this, storage, stockService, economy, language);
        competitionService = new CompetitionService(this, storage, stockService, economy, language);
        ipoService = new IpoService(this, storage, stockService, economy, language);
        certificateService = new CertificateService(this, storage, stockService, economy, language);
        marketReportService = new MarketReportService(this, stockService, language);
        new ConfigValidator(this, language).log();
        startServices();
    }

    public VaultEconomyHook economy() { return economy; }
    public StockStorage storage() { return storage; }
    public LanguageManager language() { return language; }
    public StockService stockService() { return stockService; }
    public TradeService tradeService() { return tradeService; }
    public AutomationService automation() { return automationService; }
    public ShortSellingService shortSelling() { return shortSellingService; }
    public CompetitionService competition() { return competitionService; }
    public IpoService ipo() { return ipoService; }
    public CertificateService certificate() { return certificateService; }
    public MarketReportService marketReport() { return marketReportService; }
    public AdminGui adminGui() { return adminGui; }
    public StocksGui gui() { return gui; }
}
