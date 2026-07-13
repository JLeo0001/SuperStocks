package cn.superstocks.lang;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LanguageManager {
    private final JavaPlugin plugin;
    private FileConfiguration language;
    private String currentLanguage;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        currentLanguage = plugin.getConfig().getString("language", "zh_CN");
        File folder = new File(plugin.getDataFolder(), "Language");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建 Language 目录");
        }
        File target = new File(folder, currentLanguage + ".yml");
        if (!target.exists()) {
            String resourcePath = "Language/" + currentLanguage + ".yml";
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            } else if (plugin.getResource("Language/zh_CN.yml") != null) {
                plugin.saveResource("Language/zh_CN.yml", false);
                target = new File(folder, "zh_CN.yml");
                currentLanguage = "zh_CN";
            }
        }
        language = YamlConfiguration.loadConfiguration(target);
    }

    public String text(String path) {
        return text(path, Map.of());
    }

    public String text(String path, Map<String, String> placeholders) {
        String value = language.getString(path, path);
        return color(replace(value, placeholders));
    }

    public List<String> list(String path) {
        return list(path, Map.of());
    }

    public List<String> list(String path, Map<String, String> placeholders) {
        List<String> values = language.getStringList(path);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(color(replace(value, placeholders)));
        }
        return result;
    }

    public Map<String, String> vars(Object... values) {
        Map<String, String> vars = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            vars.put(String.valueOf(values[i]), String.valueOf(values[i + 1]));
        }
        return vars;
    }

    public String currentLanguage() {
        return currentLanguage;
    }

    public static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private static String replace(String input, Map<String, String> placeholders) {
        String output = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }
}
