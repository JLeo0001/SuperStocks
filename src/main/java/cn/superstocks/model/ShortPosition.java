package cn.superstocks.model;

import java.util.UUID;

public record ShortPosition(long id, UUID playerId, String symbol, double shares,
                            double borrowedPrice, double margin, long openedAt, String status) {
    public double marketValue(double currentPrice) { return shares * currentPrice; }
    public double liability(double currentPrice) { return shares * borrowedPrice; }
    public double unrealizedPnl(double currentPrice) { return liability(currentPrice) - marketValue(currentPrice); }
    public boolean open() { return "OPEN".equals(status); }
}
