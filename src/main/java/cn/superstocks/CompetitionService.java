package cn.superstocks;

import cn.superstocks.economy.VaultEconomyHook;
import cn.superstocks.lang.LanguageManager;
import cn.superstocks.model.*;
import cn.superstocks.stock.StockService;
import cn.superstocks.storage.StockStorage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.*;

public final class CompetitionService {
    private final JavaPlugin plugin; private final StockStorage storage; private final StockService stocks;
    private final VaultEconomyHook economy; private final LanguageManager language;

    public CompetitionService(JavaPlugin plugin, StockStorage storage, StockService stocks,
                              VaultEconomyHook economy, LanguageManager language) {
        this.plugin = plugin; this.storage = storage; this.stocks = stocks;
        this.economy = economy; this.language = language;
    }

    public boolean enabled() { return plugin.getConfig().getBoolean("competition.enabled", true); }

    public CompetitionResult create(String name, int durationDays, double startCapital) throws SQLException {
        if (!enabled()) return fail("messages.trading-unavailable", "reason", "COMPETITION_DISABLED");
        long endsAt = System.currentTimeMillis() + Math.max(1, durationDays) * 86_400_000L;
        int id = storage.createCompetition(name, startCapital, endsAt);
        storage.audit("ADMIN", "COMPETITION_CREATE", name + " capital=" + startCapital + " days=" + durationDays);
        return ok("messages.competition-created", "id", id, "name", name, "capital", economy.format(startCapital));
    }

    public CompetitionResult join(OfflinePlayer player, int compId) throws SQLException {
        if (!enabled()) return fail("messages.trading-unavailable", "reason", "COMPETITION_DISABLED");
        Optional<Competition> comp = storage.competition(compId);
        if (comp.isEmpty() || !comp.get().active()) return fail("messages.competition-not-found");
        if (storage.competitionEntry(compId, player.getUniqueId()).isPresent())
            return fail("messages.competition-already-joined");
        double fee = comp.get().startCapital();
        if (!economy.available() || economy.balance(player) + 1e-6 < fee)
            return fail("messages.not-enough-money", "amount", economy.format(fee));
        if (!economy.withdraw(player, fee)) return fail("messages.withdraw-failed");
        storage.joinCompetition(compId, player.getUniqueId(), fee);
        return ok("messages.competition-joined", "name", comp.get().name(), "capital", economy.format(fee));
    }

    public CompetitionResult buy(OfflinePlayer player, int compId, String symbol, double shares) throws SQLException {
        if (!enabled()) return fail("messages.trading-unavailable", "reason", "COMPETITION_DISABLED");
        Optional<Competition> comp = storage.competition(compId);
        if (comp.isEmpty() || !comp.get().active()) return fail("messages.competition-not-found");
        if (storage.competitionEntry(compId, player.getUniqueId()).isEmpty())
            return fail("messages.competition-not-joined");
        Optional<StockQuote> quote = stocks.quote(symbol);
        if (quote.isEmpty()) return fail("messages.quote-unavailable");
        double cost = quote.get().price() * shares;
        var entry = storage.competitionEntry(compId, player.getUniqueId()).get();
        if (entry.cash() + 1e-6 < cost) return fail("messages.not-enough-money", "amount", format(cost));
        storage.competitionBuy(compId, player.getUniqueId(), symbol, shares, quote.get().price(), cost);
        return ok("messages.competition-bought", "symbol", symbol, "shares", TradeService.formatNumber(shares), "cost", format(cost));
    }

    public CompetitionResult sell(OfflinePlayer player, int compId, String symbol, double shares) throws SQLException {
        if (!enabled()) return fail("messages.trading-unavailable", "reason", "COMPETITION_DISABLED");
        Optional<Competition> comp = storage.competition(compId);
        if (comp.isEmpty() || !comp.get().active()) return fail("messages.competition-not-found");
        Optional<StockQuote> quote = stocks.quote(symbol);
        if (quote.isEmpty()) return fail("messages.quote-unavailable");
        double revenue = quote.get().price() * shares;
        storage.competitionSell(compId, player.getUniqueId(), symbol, shares, quote.get().price(), revenue);
        return ok("messages.competition-sold", "symbol", symbol, "shares", TradeService.formatNumber(shares), "revenue", format(revenue));
    }

    public CompetitionResult end(int compId) throws SQLException {
        Optional<Competition> comp = storage.competition(compId);
        if (comp.isEmpty() || comp.get().ended()) return fail("messages.competition-not-found");
        storage.endCompetition(compId);
        Map<UUID, Double> totals = new LinkedHashMap<>();
        for (CompetitionEntry e : storage.competitionEntries(compId)) {
            double total = e.cash();
            for (Holding h : storage.competitionHoldings(compId, e.playerId()))
                total += stocks.quote(h.symbol()).map(q -> h.marketValue(q.price())).orElse(0D);
            totals.put(e.playerId(), total);
        }
        List<Map.Entry<UUID, Double>> ranked = new ArrayList<>(totals.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> rewards = plugin.getConfig().getStringList("competition.rewards");
        for (int i = 0; i < Math.min(ranked.size(), rewards.size()); i++) {
            String[] parts = rewards.get(i).split(":");
            if (parts.length < 2) continue;
            try {
                double reward = Double.parseDouble(parts[1].trim());
                OfflinePlayer winner = Bukkit.getOfflinePlayer(ranked.get(i).getKey());
                if (economy.available() && reward > 0) economy.deposit(winner, reward);
                Player online = winner.getPlayer();
                if (online != null) online.sendMessage(language.text("messages.competition-reward",
                        language.vars("rank", i + 1, "amount", economy.format(reward), "name", comp.get().name())));
            } catch (NumberFormatException ignored) {}
        }
        Bukkit.broadcastMessage(language.text("messages.competition-ended", language.vars("name", comp.get().name())));
        return ok("messages.competition-admin-ended", "name", comp.get().name());
    }

    public List<Competition> list() throws SQLException { return storage.competitions(); }
    public Optional<Competition> get(int id) throws SQLException { return storage.competition(id); }
    public Optional<CompetitionEntry> entry(int compId, UUID playerId) throws SQLException { return storage.competitionEntry(compId, playerId); }
    public List<Holding> holdings(int compId, UUID playerId) throws SQLException { return storage.competitionHoldings(compId, playerId); }

    private CompetitionResult fail(String key, Object... vars) {
        return new CompetitionResult(false, language.text(key, language.vars(vars)));
    }
    private CompetitionResult ok(String key, Object... vars) {
        return new CompetitionResult(true, language.text(key, language.vars(vars)));
    }
    private static String format(double v) { return String.format("%.2f", v); }
    public record CompetitionResult(boolean success, String message) {}
}
