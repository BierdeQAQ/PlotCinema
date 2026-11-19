package io.bukkitcode.plotcinema.play;

import io.bukkitcode.plotcinema.Plotcinema;
import io.bukkitcode.plotcinema.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 播放管理器。
 * 负责：
 * - 启动播放（单人 / 多人同步）
 * - 世界判断与相对坐标偏移计算
 * - 维护正在播放的会话
 */
public class PlayManager {

    private final Plotcinema plugin;
    private final Map<UUID, PlaySession> sessions = new HashMap<>();

    public PlayManager(Plotcinema plugin) {
        this.plugin = plugin;
    }

    /**
     * 为单个玩家播放剧情。
     *
     * @return 是否成功启动
     */
    public boolean play(Player player, Plot plot) {
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage("§c你已经在观看剧情了。");
            return false;
        }
        if (plot.isEmpty()) {
            player.sendMessage("§c这段剧情没有任何镜头，无法播放。");
            return false;
        }

        World recordWorld = Bukkit.getWorld(plot.getWorldName());
        if (recordWorld == null) {
            player.sendMessage("§c剧情录制所在的世界 §e" + plot.getWorldName() + " §c不存在，无法播放。");
            return false;
        }

        // 播放前位置必须在任何传送之前记录，否则结束后无法还原到真正的原始位置
        Location origin = player.getLocation().clone();

        double offsetX = 0, offsetY = 0, offsetZ = 0;
        boolean sameWorld = player.getWorld().equals(recordWorld);

        if (!sameWorld) {
            if (!plugin.getConfigManager().isAllowRelativePlay()) {
                // 默认：强制传送到录制世界
                player.teleport(recordWorld.getSpawnLocation());
                player.sendMessage("§7已传送到剧情录制世界 §e" + recordWorld.getName() + " §7播放。");
            } else {
                // 相对播放：以玩家当前位置为基准，按录制起点偏移
                Location first = plot.getFrames().get(0).toLocation(recordWorld, 0, 0, 0);
                Location now = player.getLocation();
                offsetX = now.getX() - first.getX();
                offsetY = now.getY() - first.getY();
                offsetZ = now.getZ() - first.getZ();
                player.sendMessage("§e[提示] §7当前为相对坐标播放，镜头可能与地形不匹配。");
            }
        }

        PlaySession session = new PlaySession(plugin, player, plot, origin, offsetX, offsetY, offsetZ);
        sessions.put(player.getUniqueId(), session);
        session.start();
        return true;
    }

    /**
     * 多人同步播放：以发起者为基准，其他人跟随发起者的相对位置。
     * 实现方式：所有人各自独立插值，但使用相同的偏移基准（发起者的位置）。
     */
    public int playMulti(List<Player> players, Plot plot) {
        if (players.isEmpty()) {
            return 0;
        }
        Player leader = players.get(0);
        World recordWorld = Bukkit.getWorld(plot.getWorldName());
        if (recordWorld == null) {
            leader.sendMessage("§c剧情录制所在的世界不存在，无法播放。");
            return 0;
        }

        // 以发起者位置为基准计算偏移
        Location first = plot.getFrames().get(0).toLocation(recordWorld, 0, 0, 0);
        Location leaderLoc = leader.getLocation();
        double baseOffsetX = leaderLoc.getX() - first.getX();
        double baseOffsetY = leaderLoc.getY() - first.getY();
        double baseOffsetZ = leaderLoc.getZ() - first.getZ();

        int count = 0;
        for (Player p : players) {
            if (sessions.containsKey(p.getUniqueId())) {
                p.sendMessage("§c你已经在观看剧情了，跳过。");
                continue;
            }
            // 播放前位置必须在任何传送之前记录
            Location origin = p.getLocation().clone();

            double ox = baseOffsetX, oy = baseOffsetY, oz = baseOffsetZ;
            if (!p.getWorld().equals(recordWorld)) {
                if (!plugin.getConfigManager().isAllowRelativePlay()) {
                    p.teleport(recordWorld.getSpawnLocation());
                    ox = 0;
                    oy = 0;
                    oz = 0;
                }
            }

            PlaySession session = new PlaySession(plugin, p, plot, origin, ox, oy, oz);
            sessions.put(p.getUniqueId(), session);
            session.start();
            count++;
        }
        return count;
    }

    /**
     * 跳过指定玩家当前帧。
     */
    public boolean skip(Player player) {
        PlaySession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        session.skipFrame();
        return true;
    }

    /**
     * 终止指定玩家的剧情播放。
     */
    public boolean stop(Player player) {
        PlaySession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }
        session.finish(true);
        return true;
    }

    public boolean isPlaying(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public PlaySession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /**
     * 会话结束时由 PlaySession 回调，从表中移除。
     */
    public void onSessionEnd(PlaySession session) {
        sessions.remove(session.getPlayer().getUniqueId());
    }

    /**
     * 获取所有正在播放的玩家。
     */
    public List<Player> getPlayingPlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID id : sessions.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    /**
     * 插件关闭时强制结束所有播放。
     */
    public void shutdown() {
        for (PlaySession session : new ArrayList<>(sessions.values())) {
            session.finish(true);
        }
        sessions.clear();
    }
}
