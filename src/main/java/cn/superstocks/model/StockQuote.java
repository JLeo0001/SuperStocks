package cn.superstocks.model;

import java.time.Instant;

public record StockQuote(
        String symbol,
        String name,
        String market,
        double price,
        double change,
        double changePercent,
        Instant updatedAt
) {
    public boolean valid() {
        return price > 0.0D;
    }
}
