package cn.superstocks.model;

import java.util.UUID;

public record PlayerRanking(UUID playerId, double investedCost, double marketValue, double profit) {
    public double profitPercent() {
        if (investedCost <= 0.0D) {
            return 0.0D;
        }
        return profit / investedCost * 100.0D;
    }
}
