package cn.superstocks.storage;

import cn.superstocks.model.Holding;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockStorage extends AutoCloseable {
    void init() throws SQLException;

    List<Holding> holdings(UUID playerId) throws SQLException;

    List<Holding> allHoldings() throws SQLException;

    Optional<Holding> holding(UUID playerId, String symbol) throws SQLException;

    void buy(UUID playerId, String symbol, double shares, double price) throws SQLException;

    boolean sell(UUID playerId, String symbol, double shares, double price) throws SQLException;

    @Override
    void close() throws SQLException;
}
