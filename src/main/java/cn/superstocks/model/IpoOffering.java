package cn.superstocks.model;

public record IpoOffering(int id, String symbol, String name, String market, double price,
                          double totalShares, double maxPerPlayer, long openedAt, long closesAt, String status) {
    public boolean open() { return "OPEN".equals(status); }
    public boolean closed() { return "CLOSED".equals(status); }
    public boolean allocated() { return "ALLOCATED".equals(status); }
}
