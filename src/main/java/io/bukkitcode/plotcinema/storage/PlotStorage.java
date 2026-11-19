package io.bukkitcode.plotcinema.storage;

import io.bukkitcode.plotcinema.Plotcinema;
import io.bukkitcode.plotcinema.model.Plot;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 剧情存储层。
 * 所有剧情统一保存在插件目录下的 plots.yml 中。
 * 内存中维护一份缓存，避免每次播放都读磁盘。
 */
public class PlotStorage {

    private final Plotcinema plugin;
    private final File file;
    private final Map<String, Plot> plots = new LinkedHashMap<>();

    public PlotStorage(Plotcinema plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plots.yml");
    }

    /**
     * 从磁盘加载全部剧情到内存。
     */
    public void loadAll() {
        plots.clear();
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("plots");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(name);
            if (sec == null) {
                continue;
            }
            plots.put(name.toLowerCase(), Plot.fromSection(name, sec));
        }
    }

    /**
     * 把内存中的全部剧情写回磁盘。
     */
    public void saveAll() {
        FileConfiguration cfg = new YamlConfiguration();
        ConfigurationSection root = cfg.createSection("plots");
        for (Plot plot : plots.values()) {
            ConfigurationSection sec = root.createSection(plot.getName());
            plot.toSection(sec);
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("无法创建插件数据目录");
            }
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("保存 plots.yml 失败: " + e.getMessage());
        }
    }

    public Plot get(String name) {
        return plots.get(name.toLowerCase());
    }

    public boolean exists(String name) {
        return plots.containsKey(name.toLowerCase());
    }

    public void put(Plot plot) {
        plots.put(plot.getName().toLowerCase(), plot);
    }

    public boolean remove(String name) {
        return plots.remove(name.toLowerCase()) != null;
    }

    public Collection<Plot> getAll() {
        return plots.values();
    }

    public Collection<String> getNames() {
        return plots.keySet();
    }
}
