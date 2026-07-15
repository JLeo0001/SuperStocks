package cn.superstocks.model;

import java.util.UUID;

public record StockCertificate(long id, UUID playerId, String symbol, double shares,
                               double issuePrice, long issuedAt, String status) {
    public boolean active() { return "ACTIVE".equals(status); }
}
