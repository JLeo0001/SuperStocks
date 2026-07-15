package cn.superstocks.storage;

import cn.superstocks.model.Holding;
import cn.superstocks.model.PlayerStats;
import cn.superstocks.model.PricePoint;
import cn.superstocks.model.ShortPosition;
import cn.superstocks.model.Competition;
import cn.superstocks.model.CompetitionEntry;
import cn.superstocks.model.IpoOffering;
import cn.superstocks.model.IpoSubscription;
import cn.superstocks.model.StockCertificate;
import cn.superstocks.model.StockOrder;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface StockStorage extends AutoCloseable {
    void init() throws SQLException;
    List<Holding> holdings(UUID playerId) throws SQLException;
    List<Holding> allHoldings() throws SQLException;
    Optional<Holding> holding(UUID playerId, String symbol) throws SQLException;
    void buy(UUID playerId, String symbol, double shares, double price) throws SQLException;
    boolean sell(UUID playerId, String symbol, double shares, double price) throws SQLException;
    void executeBuy(UUID playerId, String symbol, double shares, double price, double fee) throws SQLException;
    boolean executeSell(UUID playerId, String symbol, double shares, double price, double realizedProfit, double fee) throws SQLException;
    void restoreHolding(UUID playerId, String symbol, double shares, double averageCost) throws SQLException;
    long holdingSince(UUID playerId, String symbol) throws SQLException;
    void recordTradeAccounting(UUID playerId, double realizedProfit, double fee) throws SQLException;
    PlayerStats playerStats(UUID playerId) throws SQLException;
    void recordDividend(UUID playerId, double amount) throws SQLException;
    void updateHighestValue(UUID playerId, double value) throws SQLException;
    Optional<StockQuoteState> quoteState(String symbol) throws SQLException;
    void saveQuoteState(StockQuoteState state) throws SQLException;
    void recordPrice(String symbol, double price, long recordedAt) throws SQLException;
    List<PricePoint> priceHistory(String symbol, long since, int limit) throws SQLException;
    void deletePriceHistoryBefore(long cutoff) throws SQLException;
    long createOrder(UUID playerId, String symbol, StockOrder.Type type, double shares, double triggerPrice,
                     long expiresAt, double reservedCash, double reservedShares, double reservedAverageCost,
                     long reservedSince) throws SQLException;
    List<StockOrder> openOrders() throws SQLException;
    List<StockOrder> playerOrders(UUID playerId) throws SQLException;
    Optional<StockOrder> order(long id, UUID owner) throws SQLException;
    double reservedShares(UUID playerId, String symbol) throws SQLException;
    boolean closeOrder(long id, UUID owner, String status) throws SQLException;
    boolean claimOrder(long id) throws SQLException;
    void completeOrder(long id) throws SQLException;
    void reopenOrder(long id) throws SQLException;
    Set<String> watchlist(UUID playerId) throws SQLException;
    void addWatch(UUID playerId, String symbol) throws SQLException;
    void removeWatch(UUID playerId, String symbol) throws SQLException;
    long createAlert(UUID playerId, String symbol, String direction, double targetPrice) throws SQLException;
    List<PriceAlert> alerts() throws SQLException;
    void deleteAlert(long id) throws SQLException;
    long penaltyCooldown(UUID playerId) throws SQLException;
    void savePenalty(UUID playerId, String tier, double loss, double lossPercent, double amount, long createdAt) throws SQLException;
    List<PenaltyRecord> penaltyHistory(UUID playerId, int limit) throws SQLException;
    void audit(String actor, String action, String detail) throws SQLException;
    List<AuditRecord> auditLog(String actor, String action, int limit, long before) throws SQLException;
    Optional<String> metadata(String key) throws SQLException;
    void saveMetadata(String key, String value) throws SQLException;
    @Override void close() throws SQLException;

    record StockQuoteState(String symbol, String name, String market, double price, double previousPrice, double change,
                           double changePercent, long updatedAt, String regime, long regimeUntil, long frozenUntil) {}
    record PriceAlert(long id, UUID playerId, String symbol, String direction, double targetPrice) {}
    record PenaltyRecord(String tier, double loss, double lossPercent, double amount, long createdAt) {}
    record AuditRecord(String actor, String action, String detail, long createdAt) {}

    // Short positions
    long openShort(UUID playerId, String symbol, double shares, double price, double margin) throws SQLException;
    List<ShortPosition> openShorts(UUID playerId) throws SQLException;
    List<ShortPosition> allOpenShorts() throws SQLException;
    Optional<ShortPosition> shortPosition(long id, UUID playerId) throws SQLException;
    boolean closeShort(long id, UUID playerId) throws SQLException;
    double totalShortMargin(UUID playerId) throws SQLException;
    double reservedShortShares(UUID playerId, String symbol) throws SQLException;

    // Competitions
    int createCompetition(String name, double startCapital, long endsAt) throws SQLException;
    List<Competition> competitions() throws SQLException;
    Optional<Competition> competition(int id) throws SQLException;
    boolean joinCompetition(int competitionId, UUID playerId, double fee) throws SQLException;
    Optional<CompetitionEntry> competitionEntry(int competitionId, UUID playerId) throws SQLException;
    List<CompetitionEntry> competitionEntries(int competitionId) throws SQLException;
    boolean competitionBuy(int competitionId, UUID playerId, String symbol, double shares, double price, double cost) throws SQLException;
    boolean competitionSell(int competitionId, UUID playerId, String symbol, double shares, double price, double revenue) throws SQLException;
    List<Holding> competitionHoldings(int competitionId, UUID playerId) throws SQLException;
    List<Holding> allCompetitionHoldings(int competitionId) throws SQLException;
    void endCompetition(int id) throws SQLException;

    // IPOs
    int createIpo(String symbol, String name, String market, double price, double totalShares, double maxPerPlayer, long closesAt) throws SQLException;
    List<IpoOffering> ipos() throws SQLException;
    Optional<IpoOffering> ipo(int id) throws SQLException;
    boolean subscribeIpo(int ipoId, UUID playerId, double shares) throws SQLException;
    List<IpoSubscription> ipoSubscriptions(int ipoId) throws SQLException;
    void allocateIpo(int id) throws SQLException;
    void cancelIpo(int id) throws SQLException;

    // Stock certificates
    long issueCertificate(UUID playerId, String symbol, double shares, double price) throws SQLException;
    List<StockCertificate> certificates(UUID playerId) throws SQLException;
    Optional<StockCertificate> certificate(long id, UUID playerId) throws SQLException;
    boolean redeemCertificate(long id, UUID playerId) throws SQLException;
}
