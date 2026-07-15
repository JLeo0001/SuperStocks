package cn.superstocks;

import cn.superstocks.economy.VaultEconomyHook;
import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.Holding;
import cn.superstocks.model.StockCertificate;
import cn.superstocks.model.StockQuote;
import cn.superstocks.stock.StockService;
import cn.superstocks.storage.StockStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.*;

public final class CertificateService implements Listener {
    private final JavaPlugin plugin; private final StockStorage storage; private final StockService stocks;
    private final VaultEconomyHook economy; private final LanguageManager language;
    private final org.bukkit.NamespacedKey certIdKey;

    public CertificateService(JavaPlugin plugin, StockStorage storage, StockService stocks,
                              VaultEconomyHook economy, LanguageManager language) {
        this.plugin = plugin; this.storage = storage; this.stocks = stocks;
        this.economy = economy; this.language = language;
        this.certIdKey = new org.bukkit.NamespacedKey(plugin, "certificate_id");
    }

    public ItemStack createCertificateItem(OfflinePlayer player, String symbol, double shares) throws SQLException {
        Optional<StockQuote> quote = stocks.quote(symbol);
        double price = quote.map(StockQuote::price).orElse(0D);
        long id = storage.issueCertificate(player.getUniqueId(), symbol, shares, price);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(language.text("gui.certificate.name", language.vars("id", id)));
        meta.setLore(language.list("gui.certificate.lore", language.vars(
                "symbol", symbol,
                "shares", TradeService.formatNumber(shares),
                "price", price > 0 ? String.format("%.2f", price) : "-"
        )));
        meta.getPersistentDataContainer().set(certIdKey, PersistentDataType.LONG, id);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Only respond to right-click (air or block), as stated in the item lore
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        Long certId = data.get(certIdKey, PersistentDataType.LONG);
        if (certId == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        try {
            Optional<StockCertificate> cert = storage.certificate(certId, player.getUniqueId());
            if (cert.isEmpty() || !cert.get().active()) {
                player.sendMessage(language.text("messages.certificate-invalid"));
                return;
            }
            StockCertificate c = cert.get();
            Optional<Holding> current = storage.holding(player.getUniqueId(), c.symbol());
            double currentShares = current.map(Holding::shares).orElse(0D);
            double avg = current.map(Holding::averageCost).orElse(c.issuePrice());
            double newAvg = currentShares <= 0 ? c.issuePrice() : (currentShares * avg + c.shares() * c.issuePrice()) / (currentShares + c.shares());
            storage.restoreHolding(player.getUniqueId(), c.symbol(), currentShares + c.shares(), newAvg);
            storage.redeemCertificate(certId, player.getUniqueId());
            storage.audit(player.getName(), "CERT_REDEEM", c.symbol() + " x" + c.shares());
            item.setAmount(item.getAmount() - 1);
            player.sendMessage(language.text("messages.certificate-redeemed",
                    language.vars("symbol", c.symbol(), "shares", TradeService.formatNumber(c.shares()))));
        } catch (SQLException ex) {
            player.sendMessage(language.text("messages.trade-failed"));
        }
    }

    public List<StockCertificate> list(OfflinePlayer player) throws SQLException {
        return storage.certificates(player.getUniqueId());
    }

    private LanguageManager lang() { return language; }
}
