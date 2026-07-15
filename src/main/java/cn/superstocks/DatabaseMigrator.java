package cn.superstocks;

import cn.superstocks.storage.StockStorage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class DatabaseMigrator {
    private final StockStorage storage;
    private final Logger logger;
    private static final int TARGET_VERSION = 1;

    public DatabaseMigrator(StockStorage storage, Logger logger) {
        this.storage = storage;
        this.logger = logger;
    }

    public void migrate() {
        try {
            int current = currentVersion();
            if (current < TARGET_VERSION) {
                logger.info("数据库升级: v" + current + " → v" + TARGET_VERSION);
                for (int v = current + 1; v <= TARGET_VERSION; v++) {
                    applyMigration(v);
                }
                storage.saveMetadata("schema-version", String.valueOf(TARGET_VERSION));
                logger.info("数据库升级完成，当前版本 v" + TARGET_VERSION);
            }
        } catch (SQLException e) {
            logger.warning("数据库升级检查失败: " + e.getMessage());
        }
    }

    public List<String> healthCheck() {
        List<String> result = new ArrayList<>();
        try {
            int version = currentVersion();
            result.add("  数据库版本: v" + version + (version < TARGET_VERSION ? " (需升级)" : " (最新)"));
            result.add("  数据库连接: 正常");
            // Row counts
            try {
                int holdings = storage.allHoldings().size();
                long players = storage.allHoldings().stream().map(h -> h.playerId()).distinct().count();
                result.add("  持仓记录: " + holdings + " 条 / " + players + " 名玩家");
            } catch (SQLException ignored) {
                result.add("  持仓记录: 读取失败");
            }
        } catch (SQLException e) {
            result.add("  数据库健康检查失败: " + e.getMessage());
        }
        return result;
    }

    private int currentVersion() throws SQLException {
        return storage.metadata("schema-version")
                .map(v -> { try { return Integer.parseInt(v); } catch (NumberFormatException e) { return 0; } })
                .orElse(0);
    }

    private void applyMigration(int version) throws SQLException {
        switch (version) {
            case 1 -> {
                // Initial schema is created by SqliteStockStorage.init() via CREATE TABLE IF NOT EXISTS
                // No additional migration needed for v1
                logger.info("  迁移 v1: 基础表结构已就绪");
            }
            // Future migrations:
            // case 2 -> { ... }
            default -> logger.warning("  未知迁移版本: v" + version);
        }
    }
}
