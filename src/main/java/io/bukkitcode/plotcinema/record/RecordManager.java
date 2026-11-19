package io.bukkitcode.plotcinema.record;

import io.bukkitcode.plotcinema.Plotcinema;
import io.bukkitcode.plotcinema.model.CameraFrame;
import io.bukkitcode.plotcinema.model.Plot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 录制管理器。
 * 支持两种录制模式：
 * 1. 自动采样：每隔 N tick 自动记录玩家当前位置，连续流畅。
 * 2. 手动打点：玩家走到位置后输入指令记录一个关键帧。
 * 每个玩家同时只能有一个录制会话。
 */
public class RecordManager {

    /** 录制模式 */
    public enum Mode {
        AUTO, MANUAL
    }

    /** 单个玩家的录制会话 */
    public static class RecordSession {
        private final UUID playerId;
        private final Plot plot;
        private final Mode mode;
        private BukkitTask autoTask;
        private int frameDurationTicks;

        public RecordSession(UUID playerId, Plot plot, Mode mode, int frameDurationTicks) {
            this.playerId = playerId;
            this.plot = plot;
            this.mode = mode;
            this.frameDurationTicks = frameDurationTicks;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public Plot getPlot() {
            return plot;
        }

        public Mode getMode() {
            return mode;
        }

        public BukkitTask getAutoTask() {
            return autoTask;
        }

        public void setAutoTask(BukkitTask autoTask) {
            this.autoTask = autoTask;
        }

        public int getFrameDurationTicks() {
            return frameDurationTicks;
        }

        public void setFrameDurationTicks(int frameDurationTicks) {
            this.frameDurationTicks = Math.max(1, frameDurationTicks);
        }
    }

    private final Plotcinema plugin;
    private final Map<UUID, RecordSession> sessions = new HashMap<>();

    public RecordManager(Plotcinema plugin) {
        this.plugin = plugin;
    }

    /**
     * 开始录制。
     *
     * @param player      录制者
     * @param plotName    剧情名
     * @param mode        录制模式
     * @param durationTicks 每帧时长（自动采样模式下即采样间隔；手动模式下为每帧默认时长）
     * @return 是否成功开始
     */
    public boolean start(Player player, String plotName, Mode mode, int durationTicks) {
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) {
            return false;
        }

        Plot plot = new Plot(plotName);
        plot.setWorldName(player.getWorld().getName());

        RecordSession session = new RecordSession(id, plot, mode, durationTicks);
        sessions.put(id, session);

        if (mode == Mode.AUTO) {
            int interval = plugin.getConfigManager().getRecordIntervalTicks();
            BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null || !p.isOnline()) {
                    return;
                }
                Location loc = p.getLocation();
                plot.getFrames().add(CameraFrame.fromLocation(loc, durationTicks));
            }, interval, interval);
            session.setAutoTask(task);
        }
        return true;
    }

    /**
     * 手动打点：记录当前玩家位置为一个关键帧。
     */
    public boolean mark(Player player) {
        RecordSession session = sessions.get(player.getUniqueId());
        if (session == null || session.getMode() != Mode.MANUAL) {
            return false;
        }
        session.getPlot().getFrames().add(
                CameraFrame.fromLocation(player.getLocation(), session.getFrameDurationTicks()));
        return true;
    }

    /**
     * 结束录制并返回录好的剧情。若帧数为 0 则返回 null。
     */
    public Plot stop(Player player) {
        RecordSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return null;
        }
        if (session.getAutoTask() != null) {
            session.getAutoTask().cancel();
        }
        Plot plot = session.getPlot();
        if (plot.isEmpty()) {
            return null;
        }
        return plot;
    }

    /**
     * 取消录制，丢弃所有已录帧。
     */
    public boolean cancel(Player player) {
        RecordSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }
        if (session.getAutoTask() != null) {
            session.getAutoTask().cancel();
        }
        return true;
    }

    public boolean isRecording(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public RecordSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /**
     * 插件关闭时清理所有录制任务。
     */
    public void shutdown() {
        for (RecordSession session : sessions.values()) {
            if (session.getAutoTask() != null) {
                session.getAutoTask().cancel();
            }
        }
        sessions.clear();
    }
}
