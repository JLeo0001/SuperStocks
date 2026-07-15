package cn.superstocks.model;

import java.util.UUID;

public record StockOrder(long id, UUID playerId, String symbol, Type type, double shares, double triggerPrice,
                         long expiresAt, long createdAt, double reservedCash, double reservedShares,
                         double reservedAverageCost, long reservedSince) {
    public boolean expired(long now) { return expiresAt > 0L && now >= expiresAt; }
    public boolean triggered(double price) {
        return switch (type) {
            case LIMIT_BUY -> price <= triggerPrice;
            case LIMIT_SELL, TAKE_PROFIT -> price >= triggerPrice;
            case STOP_LOSS -> price <= triggerPrice;
        };
    }
    public enum Type { LIMIT_BUY, LIMIT_SELL, STOP_LOSS, TAKE_PROFIT }
}
