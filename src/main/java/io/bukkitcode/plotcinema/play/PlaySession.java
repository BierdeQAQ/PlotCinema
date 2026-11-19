package io.bukkitcode.plotcinema.play;

import io.bukkitcode.plotcinema.Plotcinema;
import io.bukkitcode.plotcinema.model.CameraFrame;
import io.bukkitcode.plotcinema.model.FrameAction;
import io.bukkitcode.plotcinema.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * 单个玩家的剧情播放会话。
 *
 * 播放状态机（四阶段）：
 *   PRE_DELAY  - 节点前延迟，等待 preDelayTicks 个 tick
 *   TRANSITION - 从上一节点位置线性插值到本节点位置，持续 transitionTicks 个 tick
 *   HOLD       - 到达本节点位置后原地停留，持续 durationTicks 个 tick
 *   POST_DELAY - 节点后延迟，等待 postDelayTicks 个 tick
 *
 * 第一个节点特殊处理：
 *   播放开始时先瞬移到节点1位置，然后进入 PRE_DELAY，延迟结束才真正开始节点1。
 *
 * 播放期间效果：
 *   隐身、攻速归零。结束后全部还原，并恢复播放前的位置与视角。
 */
public class PlaySession {

    private enum Phase {
        PRE_DELAY,
        TRANSITION,
        HOLD,
        POST_DELAY
    }

    private final Plotcinema plugin;
    private final Player player;
    private final Plot plot;
    private final List<CameraFrame> frames;

    /** 播放前的位置（含视角），用于结束后还原 */
    private final Location originLocation;

    /** 相对播放的偏移量 */
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;

    private int frameIndex = -1;
    private Phase phase = Phase.PRE_DELAY;
    private int phaseTick = 0;

    /** 当前节点的过渡起点 */
    private Location transitionStart;
    /** 当前节点的过渡终点 */
    private Location transitionTarget;
    /** 当前节点过渡总时长（tick） */
    private int transitionTotal;

    private BukkitTask task;
    private BossBar bossBar;
    private boolean finished = false;

    /** 播放前的攻速基础值，用于还原 */
    private double originalAttackSpeed = -1;

    public PlaySession(Plotcinema plugin, Player player, Plot plot, Location originLocation,
                       double offsetX, double offsetY, double offsetZ) {
        this.plugin = plugin;
        this.player = player;
        this.plot = plot;
        this.frames = plot.getFrames();
        this.originLocation = originLocation.clone();
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    /**
     * 开始播放。
     */
    public void start() {
        if (frames.isEmpty()) {
            finish(false);
            return;
        }

        // 播放期间效果：隐身 + 攻速归零
        applyPlayEffects();

        this.bossBar = Bukkit.createBossBar(" ", BarColor.WHITE, BarStyle.SOLID);
        this.bossBar.setVisible(false);
        this.bossBar.addPlayer(player);

        // 第一个节点：先瞬移到节点1位置，再进入前延迟
        CameraFrame first = frames.get(0);
        this.frameIndex = 0;
        this.transitionTarget = first.toLocation(player.getWorld(), offsetX, offsetY, offsetZ);
        player.teleport(transitionTarget);
        this.phase = Phase.PRE_DELAY;
        this.phaseTick = 0;

        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    /**
     * 每 tick 调用一次，推进剧情。
     */
    private void tick() {
        if (finished) {
            return;
        }
        if (!player.isOnline()) {
            finish(false);
            return;
        }

        CameraFrame frame = frames.get(frameIndex);

        switch (phase) {
            case PRE_DELAY: {
                phaseTick++;
                if (phaseTick >= frame.getPreDelayTicks()) {
                    beginTransition(frame);
                }
                break;
            }
            case TRANSITION: {
                if (transitionTotal <= 0) {
                    teleportCamera(transitionTarget);
                    enterHold(frame);
                    break;
                }
                double progress = Math.min(1.0, (double) phaseTick / transitionTotal);
                double nx = lerp(transitionStart.getX(), transitionTarget.getX(), progress);
                double ny = lerp(transitionStart.getY(), transitionTarget.getY(), progress);
                double nz = lerp(transitionStart.getZ(), transitionTarget.getZ(), progress);
                float nyaw = lerpAngle(transitionStart.getYaw(), transitionTarget.getYaw(), (float) progress);
                float npitch = (float) lerp(transitionStart.getPitch(), transitionTarget.getPitch(), (float) progress);

                teleportCamera(new Location(player.getWorld(), nx, ny, nz, nyaw, npitch));

                phaseTick++;
                if (phaseTick >= transitionTotal) {
                    teleportCamera(transitionTarget);
                    enterHold(frame);
                }
                break;
            }
            case HOLD: {
                phaseTick++;
                if (phaseTick >= frame.getDurationTicks()) {
                    enterPostDelay(frame);
                }
                break;
            }
            case POST_DELAY: {
                phaseTick++;
                if (phaseTick >= frame.getPostDelayTicks()) {
                    if (!enterNextFrame()) {
                        finish(true);
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * 开始当前节点的过渡阶段。
     */
    private void beginTransition(CameraFrame frame) {
        this.transitionStart = player.getLocation().clone();
        this.transitionTarget = frame.toLocation(player.getWorld(), offsetX, offsetY, offsetZ);
        this.transitionTotal = frame.isTransitionSet()
                ? frame.getTransitionTicks()
                : plugin.getConfigManager().getDefaultTransitionTicks();
        this.phase = Phase.TRANSITION;
        this.phaseTick = 0;

        runActions(frame.getOnStartActions());

        if (transitionTotal <= 0) {
            teleportCamera(transitionTarget);
            enterHold(frame);
        }
    }

    /**
     * 进入停留阶段，并触发到达动作。
     */
    private void enterHold(CameraFrame frame) {
        this.phase = Phase.HOLD;
        this.phaseTick = 0;
        runActions(frame.getOnArriveActions());
    }

    /**
     * 进入节点后延迟阶段。
     */
    private void enterPostDelay(CameraFrame frame) {
        this.phase = Phase.POST_DELAY;
        this.phaseTick = 0;
    }

    /**
     * 进入下一节点。返回 false 表示剧情已结束。
     */
    private boolean enterNextFrame() {
        frameIndex++;
        if (frameIndex >= frames.size()) {
            return false;
        }
        CameraFrame frame = frames.get(frameIndex);
        this.phase = Phase.PRE_DELAY;
        this.phaseTick = 0;
        // 若下一节点没有前延迟，立即开始过渡
        if (frame.getPreDelayTicks() <= 0) {
            beginTransition(frame);
        }
        return true;
    }

    /**
     * 应用播放期间效果：隐身 + 攻速归零。
     */
    private void applyPlayEffects() {
        // 隐身
        if (plugin.getConfigManager().isEffectInvisibility()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE, 0, false, false, false));
        }

        // 攻速归零
        if (plugin.getConfigManager().isEffectZeroAttackSpeed()) {
            AttributeInstance attr = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (attr != null) {
                this.originalAttackSpeed = attr.getBaseValue();
                attr.setBaseValue(0.0);
            }
        }
    }

    /**
     * 还原播放期间效果。
     */
    private void restorePlayEffects() {
        if (!player.isOnline()) {
            return;
        }
        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        if (originalAttackSpeed >= 0) {
            AttributeInstance attr = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
            if (attr != null) {
                attr.setBaseValue(originalAttackSpeed);
            }
        }
    }

    /**
     * 执行一组动作。
     */
    private void runActions(List<FrameAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (FrameAction action : actions) {
            executeAction(action);
        }
    }

    /**
     * 执行单个动作。
     */
    private void executeAction(FrameAction action) {
        String value = action.getValue() == null ? "" : action.getValue();
        switch (action.getType()) {
            case COMMAND: {
                String cmd = value.replace("%player%", player.getName());
                if (cmd.startsWith("/")) {
                    cmd = cmd.substring(1);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                break;
            }
            case TITLE: {
                String[] parts = value.split("\\|", 2);
                String title = colorize(parts[0]);
                String subtitle = parts.length > 1 ? colorize(parts[1]) : "";
                player.sendTitle(title, subtitle, 10, 40, 10);
                break;
            }
            case ACTIONBAR: {
                player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(colorize(value)));
                break;
            }
            case CHAT: {
                player.sendMessage(colorize(value));
                break;
            }
            case SOUND: {
                try {
                    Sound sound = Sound.valueOf(value.toUpperCase());
                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                } catch (IllegalArgumentException ignored) {
                    // 音效名无效，忽略
                }
                break;
            }
            case BOSSBAR: {
                if (bossBar != null) {
                    bossBar.setTitle(colorize(value));
                    bossBar.setVisible(true);
                    bossBar.setProgress(1.0);
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * 跳过当前节点，直接进入下一节点。
     */
    public void skipFrame() {
        if (finished) {
            return;
        }
        if (!enterNextFrame()) {
            finish(true);
        }
    }

    /**
     * 结束播放。
     *
     * @param restore 是否还原位置、视角与效果
     */
    public void finish(boolean restore) {
        if (finished) {
            return;
        }
        finished = true;

        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        restorePlayEffects();

        if (restore && player.isOnline()) {
            // 优先使用剧情配置的结束点
            if (plot.hasEndPoint()) {
                World world = Bukkit.getWorld(plot.getWorldName());
                if (world != null) {
                    Location end = new Location(world,
                            plot.getEndX() + offsetX,
                            plot.getEndY() + offsetY,
                            plot.getEndZ() + offsetZ,
                            plot.getEndYaw(), plot.getEndPitch());
                    player.teleport(end);
                } else {
                    player.teleport(originLocation);
                }
            } else {
                // 回到播放前的位置，并恢复原视角
                player.teleport(originLocation);
            }
        }

        plugin.getPlayManager().onSessionEnd(this);
    }

    /**
     * 把玩家传送到镜头位置。
     * 若配置关闭了视角锁定（lock.rotation = false），则保留玩家当前朝向，只移动位置。
     */
    private void teleportCamera(Location target) {
        if (plugin.getConfigManager().isLockRotation()) {
            player.teleport(target);
        } else {
            Location keepRotation = target.clone();
            keepRotation.setYaw(player.getLocation().getYaw());
            keepRotation.setPitch(player.getLocation().getPitch());
            player.teleport(keepRotation);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * 角度插值，处理 yaw 的 360 度环绕，走最短路径。
     */
    private static float lerpAngle(float a, float b, float t) {
        float diff = b - a;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;
        float result = a + diff * t;
        while (result > 180) result -= 360;
        while (result < -180) result += 360;
        return result;
    }

    private static String colorize(String s) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isFinished() {
        return finished;
    }
}
