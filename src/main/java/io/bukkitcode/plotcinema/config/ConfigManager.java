package io.bukkitcode.plotcinema.config;

import io.bukkitcode.plotcinema.Plotcinema;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 配置管理器。
 * 负责读取 config.yml，并提供带默认值的访问方法。
 * 同时负责配置文件版本校验：当配置结构升级后，旧配置文件会在 OP 进服时收到提示。
 */
public class ConfigManager {

    /** 当前代码期望的配置文件版本号。每次配置结构变更都要 +1。 */
    public static final int CONFIG_VERSION = 3;

    private final Plotcinema plugin;

    // 缓存常用配置项，避免频繁读取
    private int recordIntervalTicks;
    private boolean allowRelativePlay;
    private boolean lockMovement;
    private boolean lockRotation;
    private boolean lockInteract;
    private boolean hideHud;
    private boolean hideOtherPlayers;
    private int interpolateTicks;
    private int defaultTransitionTicks;
    private boolean effectInvisibility;
    private boolean effectZeroAttackSpeed;
    private boolean notifyOutdatedConfig;

    public ConfigManager(Plotcinema plugin) {
        this.plugin = plugin;
    }

    /**
     * 加载（或重载）配置。若 config.yml 不存在则从 jar 内释放默认配置。
     */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        // 若配置文件缺少 version 键，说明是旧版或手写配置，补上当前版本
        if (!cfg.contains("version")) {
            cfg.set("version", CONFIG_VERSION);
            plugin.saveConfig();
        }

        this.recordIntervalTicks = Math.max(1, cfg.getInt("record.interval-ticks", 2));
        this.allowRelativePlay = cfg.getBoolean("play.allow-relative-play", false);
        this.interpolateTicks = Math.max(1, cfg.getInt("play.interpolate-ticks", 3));
        this.defaultTransitionTicks = Math.max(0, cfg.getInt("play.default-transition-ticks", 20));
        this.effectInvisibility = cfg.getBoolean("play.effects.invisibility", true);
        this.effectZeroAttackSpeed = cfg.getBoolean("play.effects.zero-attack-speed", true);
        this.notifyOutdatedConfig = cfg.getBoolean("play.notify-outdated-config", true);

        this.lockMovement = cfg.getBoolean("lock.movement", true);
        this.lockRotation = cfg.getBoolean("lock.rotation", true);
        this.lockInteract = cfg.getBoolean("lock.interact", true);
        this.hideHud = cfg.getBoolean("lock.hide-hud", true);
        this.hideOtherPlayers = cfg.getBoolean("lock.hide-other-players", false);
    }

    /**
     * 判断当前磁盘上的配置文件版本是否与代码期望版本一致。
     */
    public boolean isConfigOutdated() {
        return plugin.getConfig().getInt("version", 0) != CONFIG_VERSION;
    }

    public int getRecordIntervalTicks() {
        return recordIntervalTicks;
    }

    public boolean isAllowRelativePlay() {
        return allowRelativePlay;
    }

    public int getInterpolateTicks() {
        return interpolateTicks;
    }

    public int getDefaultTransitionTicks() {
        return defaultTransitionTicks;
    }

    public boolean isEffectInvisibility() {
        return effectInvisibility;
    }

    public boolean isEffectZeroAttackSpeed() {
        return effectZeroAttackSpeed;
    }

    public boolean isNotifyOutdatedConfig() {
        return notifyOutdatedConfig;
    }

    public boolean isLockMovement() {
        return lockMovement;
    }

    public boolean isLockRotation() {
        return lockRotation;
    }

    public boolean isLockInteract() {
        return lockInteract;
    }

    public boolean isHideHud() {
        return hideHud;
    }

    public boolean isHideOtherPlayers() {
        return hideOtherPlayers;
    }
}
