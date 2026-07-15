package cn.superstocks;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ConfigMigrator {
    private final JavaPlugin plugin;

    public ConfigMigrator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void migrate() {
        if (plugin.getResource("config.yml") == null) {
            return;
        }
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(
                plugin.getResource("config.yml"), StandardCharsets.UTF_8));
        boolean changed = copyMissing(defaults, "");
        int targetVersion = defaults.getInt("config-version", 1);
        if (plugin.getConfig().getInt("config-version", 0) < targetVersion) {
            plugin.getConfig().set("config-version", targetVersion);
            changed = true;
        }
        if (changed) {
            plugin.saveConfig();
            plugin.getLogger().info("已补齐新版本配置项，请检查 config.yml 中的新设置。");
        }
    }

    private boolean copyMissing(ConfigurationSection source, String prefix) {
        boolean changed = false;
        for (String key : source.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (source.isConfigurationSection(key)) {
                ConfigurationSection child = source.getConfigurationSection(key);
                if (child != null) {
                    changed |= copyMissing(child, path);
                }
            } else if (!plugin.getConfig().contains(path)) {
                plugin.getConfig().set(path, source.get(key));
                changed = true;
            }
        }
        return changed;
    }
}
