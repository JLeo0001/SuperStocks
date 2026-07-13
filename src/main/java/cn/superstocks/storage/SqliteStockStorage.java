package cn.superstocks.storage;

import cn.superstocks.model.Holding;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqliteStockStorage implements StockStorage {
    private final String jdbcUrl;
    private Connection connection;

    public SqliteStockStorage(JavaPlugin plugin) {
        File database = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "stocks.db"));
        this.jdbcUrl = "jdbc:sqlite:" + database.getAbsolutePath();
    }

    @Override
    public void init() throws SQLException {
        connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS holdings (player_uuid TEXT NOT NULL, symbol TEXT NOT NULL, shares REAL NOT NULL, average_cost REAL NOT NULL, PRIMARY KEY (player_uuid, symbol))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS trades (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, symbol TEXT NOT NULL, side TEXT NOT NULL, shares REAL NOT NULL, price REAL NOT NULL, created_at INTEGER NOT NULL)");
        }
    }

    @Override
    public List<Holding> holdings(UUID playerId) throws SQLException {
        List<Holding> holdings = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT symbol, shares, average_cost FROM holdings WHERE player_uuid = ? ORDER BY symbol")) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    holdings.add(new Holding(playerId, rs.getString("symbol"), rs.getDouble("shares"), rs.getDouble("average_cost")));
                }
            }
        }
        return holdings;
    }

    @Override
    public Optional<Holding> holding(UUID playerId, String symbol) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT shares, average_cost FROM holdings WHERE player_uuid = ? AND symbol = ?")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, symbol);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Holding(playerId, symbol, rs.getDouble("shares"), rs.getDouble("average_cost")));
            }
        }
    }

    @Override
    public void buy(UUID playerId, String symbol, double shares, double price) throws SQLException {
        connection.setAutoCommit(false);
        try {
            Optional<Holding> current = holding(playerId, symbol);
            if (current.isPresent()) {
                Holding holding = current.get();
                double totalShares = holding.shares() + shares;
                double average = ((holding.shares() * holding.averageCost()) + (shares * price)) / totalShares;
                try (PreparedStatement statement = connection.prepareStatement("UPDATE holdings SET shares = ?, average_cost = ? WHERE player_uuid = ? AND symbol = ?")) {
                    statement.setDouble(1, totalShares);
                    statement.setDouble(2, average);
                    statement.setString(3, playerId.toString());
                    statement.setString(4, symbol);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO holdings(player_uuid, symbol, shares, average_cost) VALUES (?, ?, ?, ?)")) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, symbol);
                    statement.setDouble(3, shares);
                    statement.setDouble(4, price);
                    statement.executeUpdate();
                }
            }
            insertTrade(playerId, symbol, "BUY", shares, price);
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    public boolean sell(UUID playerId, String symbol, double shares, double price) throws SQLException {
        connection.setAutoCommit(false);
        try {
            Optional<Holding> current = holding(playerId, symbol);
            if (current.isEmpty() || current.get().shares() + 0.000001D < shares) {
                connection.rollback();
                return false;
            }
            double remaining = current.get().shares() - shares;
            if (remaining <= 0.000001D) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM holdings WHERE player_uuid = ? AND symbol = ?")) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, symbol);
                    statement.executeUpdate();
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE holdings SET shares = ? WHERE player_uuid = ? AND symbol = ?")) {
                    statement.setDouble(1, remaining);
                    statement.setString(2, playerId.toString());
                    statement.setString(3, symbol);
                    statement.executeUpdate();
                }
            }
            insertTrade(playerId, symbol, "SELL", shares, price);
            connection.commit();
            return true;
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void insertTrade(UUID playerId, String symbol, String side, double shares, double price) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO trades(player_uuid, symbol, side, shares, price, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, symbol);
            statement.setString(3, side);
            statement.setDouble(4, shares);
            statement.setDouble(5, price);
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
