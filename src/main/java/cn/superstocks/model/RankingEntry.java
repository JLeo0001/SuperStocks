package cn.superstocks.model;

import java.util.UUID;

public record RankingEntry(UUID playerId, double portfolioValue, double profit, double profitPercent) {
}
