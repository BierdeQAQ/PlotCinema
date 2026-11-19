package io.bukkitcode.plotcinema;

import io.bukkitcode.plotcinema.command.PlotCommand;
import io.bukkitcode.plotcinema.config.ConfigManager;
import io.bukkitcode.plotcinema.listener.PlayerListener;
import io.bukkitcode.plotcinema.play.PlayManager;
import io.bukkitcode.plotcinema.record.RecordManager;
import io.bukkitcode.plotcinema.storage.PlotStorage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PlotCinema 主类。
 * 剧情镜头演出插件：通过传送玩家视角实现剧情动画效果。
 */
public final class Plotcinema extends JavaPlugin implements Listener {

    private ConfigManager configManager;
    private PlotStorage plotStorage;
    private RecordManager recordManager;
    private PlayManager playManager;

    @Override
    public void onEnable() {
        // 1. 加载配置
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        // 2. 加载剧情数据
        this.plotStorage = new PlotStorage(this);
        this.plotStorage.loadAll();

        // 3. 初始化管理器
        this.recordManager = new RecordManager(this);
        this.playManager = new PlayManager(this);

        // 4. 注册命令
        PlotCommand plotCommand = new PlotCommand(this);
        if (getCommand("plot") != null) {
            getCommand("plot").setExecutor(plotCommand);
            getCommand("plot").setTabCompleter(plotCommand);
        }

        // 5. 注册监听器
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("PlotCinema 已启用，共加载 " + plotStorage.getAll().size() + " 段剧情。");
    }

    @Override
    public void onDisable() {
        // 先结束所有播放与录制，避免玩家卡在剧情中
        if (playManager != null) {
            playManager.shutdown();
        }
        if (recordManager != null) {
            recordManager.shutdown();
        }
        if (plotStorage != null) {
            plotStorage.saveAll();
        }
        getLogger().info("PlotCinema 已关闭。");
    }

    /**
     * OP 进服时检查配置文件版本，若过旧则提示。
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp()) {
            return;
        }
        if (!configManager.isNotifyOutdatedConfig()) {
            return;
        }
        if (configManager.isConfigOutdated()) {
            player.sendMessage("§e[PlotCinema] §c检测到旧版配置文件！");
            player.sendMessage("§7当前配置版本: §e" + getConfig().getInt("version", 0)
                    + " §7，期望版本: §e" + ConfigManager.CONFIG_VERSION);
            player.sendMessage("§7请备份后删除 §eplugins/PlotCinema/config.yml §7并重启服务器以生成新配置。");
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlotStorage getPlotStorage() {
        return plotStorage;
    }

    public RecordManager getRecordManager() {
        return recordManager;
    }

    public PlayManager getPlayManager() {
        return playManager;
    }
}
