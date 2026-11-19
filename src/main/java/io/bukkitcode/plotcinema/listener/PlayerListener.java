package io.bukkitcode.plotcinema.listener;

import io.bukkitcode.plotcinema.Plotcinema;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家事件监听器。
 * 在剧情播放期间锁定玩家的移动、攻击、交互等行为，避免破坏镜头演出。
 *
 * 性能说明：
 * PlayerMoveEvent 是高频事件，这里只做一次 Map 查找 + 布尔判断，
 * 未在播放中的玩家会立即返回，开销极小。
 */
public class PlayerListener implements Listener {

    private final Plotcinema plugin;

    public PlayerListener(Plotcinema plugin) {
        this.plugin = plugin;
    }

    /**
     * 锁定移动：把玩家拉回镜头位置。
     * 注意：PlaySession 每 tick 会主动 teleport，这里只是兜底防止玩家自己跑掉。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getPlayManager().isPlaying(player)) {
            return;
        }
        if (!plugin.getConfigManager().isLockMovement()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // 只允许朝向变化，位置变化一律拉回
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location locked = from.clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
        }
    }

    /**
     * 锁定交互。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfigManager().isLockInteract()) {
            return;
        }
        if (plugin.getPlayManager().isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * 锁定方块破坏。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfigManager().isLockInteract()) {
            return;
        }
        if (plugin.getPlayManager().isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * 锁定方块放置。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfigManager().isLockInteract()) {
            return;
        }
        if (plugin.getPlayManager().isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * 锁定攻击。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigManager().isLockInteract()) {
            return;
        }
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (plugin.getPlayManager().isPlaying(player)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 剧情期间免疫伤害，避免被打断。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (plugin.getPlayManager().isPlaying(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * 剧情期间禁止执行其他命令（跳过/终止命令除外）。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getPlayManager().isPlaying(player)) {
            return;
        }
        String msg = event.getMessage().toLowerCase();
        // 放行本插件自己的命令（跳过、终止、编辑菜单等）
        if (msg.startsWith("/plot ") || msg.equals("/plot")
                || msg.startsWith("/plotcinema ") || msg.equals("/plotcinema")
                || msg.startsWith("/pc ") || msg.equals("/pc")) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§c剧情播放中，无法执行其他命令。使用 §e/plot skip §c跳过当前镜头，§e/plot stop §c结束剧情。");
    }

    /**
     * 玩家退出时清理播放会话。
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPlayManager().isPlaying(player)) {
            plugin.getPlayManager().stop(player);
        }
        if (plugin.getRecordManager().isRecording(player)) {
            plugin.getRecordManager().cancel(player);
        }
    }
}
