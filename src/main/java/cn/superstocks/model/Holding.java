package cn.superstocks.model;

import java.util.UUID;

public record Holding(UUID playerId, String symbol, double shares, double averageCost) {
    public double marketValue(double price) {
        return shares * price;
    }

    public double profit(double price) {
        return marketValue(price) - shares * averageCost;
    }
}
