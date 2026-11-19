package io.bukkitcode.plotcinema.model;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个镜头帧。
 * 表示剧情播放过程中的一个"机位"：玩家被传送到这里，看向指定方向，停留一段时间。
 *
 * 帧间过渡：
 *   transitionTicks 表示从上一帧位置平移到本帧位置所需的 tick 数。
 *   0 表示瞬移；大于 0 则位置与朝向一起线性插值过去。
 *
 * 动作：
 *   onStartActions  - 过渡开始那一刻触发
 *   onArriveActions - 过渡完成、到达机位那一刻触发
 */
public class CameraFrame {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    /** 该帧停留时长（tick）。1 秒 = 20 tick。 */
    private int durationTicks;

    /** 从上一帧平移到本帧的过渡时长（tick）。0 = 瞬移。 */
    private int transitionTicks;

    /** 是否显式设置过过渡时长。未设置时播放阶段会回退到全局默认值。 */
    private boolean transitionSet;

    /** 节点前延迟（tick）。进入本节点前等待的时间。 */
    private int preDelayTicks;

    /** 节点后延迟（tick）。本节点停留结束后额外等待的时间。 */
    private int postDelayTicks;

    /** 过渡开始时触发的动作。 */
    private final List<FrameAction> onStartActions = new ArrayList<>();

    /** 到达机位时触发的动作。 */
    private final List<FrameAction> onArriveActions = new ArrayList<>();

    public CameraFrame(double x, double y, double z, float yaw, float pitch, int durationTicks) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.durationTicks = Math.max(1, durationTicks);
        this.transitionTicks = 0;
    }

    /**
     * 从玩家当前位置构造一个镜头帧。
     */
    public static CameraFrame fromLocation(Location loc, int durationTicks) {
        return new CameraFrame(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), durationTicks);
    }

    /**
     * 从 yml 配置节反序列化。
     */
    public static CameraFrame fromSection(ConfigurationSection sec) {
        CameraFrame frame = new CameraFrame(
                sec.getDouble("x"),
                sec.getDouble("y"),
                sec.getDouble("z"),
                (float) sec.getDouble("yaw"),
                (float) sec.getDouble("pitch"),
                sec.getInt("duration", 40)
        );
        frame.transitionTicks = Math.max(0, sec.getInt("transition", 0));
        frame.transitionSet = sec.contains("transition");
        frame.preDelayTicks = Math.max(0, sec.getInt("pre-delay", 0));
        frame.postDelayTicks = Math.max(0, sec.getInt("post-delay", 0));

        List<?> startList = sec.getList("on-start");
        if (startList != null) {
            for (Object obj : startList) {
                if (obj instanceof ConfigurationSection) {
                    frame.onStartActions.add(FrameAction.fromSection((ConfigurationSection) obj));
                }
            }
        }

        List<?> arriveList = sec.getList("on-arrive");
        if (arriveList != null) {
            for (Object obj : arriveList) {
                if (obj instanceof ConfigurationSection) {
                    frame.onArriveActions.add(FrameAction.fromSection((ConfigurationSection) obj));
                }
            }
        }
        return frame;
    }

    /**
     * 序列化到 yml 配置节。
     */
    public void toSection(ConfigurationSection sec) {
        sec.set("x", x);
        sec.set("y", y);
        sec.set("z", z);
        sec.set("yaw", (double) yaw);
        sec.set("pitch", (double) pitch);
        sec.set("duration", durationTicks);
        if (transitionTicks > 0) {
            sec.set("transition", transitionTicks);
        }
        if (preDelayTicks > 0) {
            sec.set("pre-delay", preDelayTicks);
        }
        if (postDelayTicks > 0) {
            sec.set("post-delay", postDelayTicks);
        }

        if (!onStartActions.isEmpty()) {
            List<ConfigurationSection> list = new ArrayList<>();
            for (FrameAction action : onStartActions) {
                ConfigurationSection as = sec.createSection("on-start." + list.size());
                action.toSection(as);
                list.add(as);
            }
        }
        if (!onArriveActions.isEmpty()) {
            List<ConfigurationSection> list = new ArrayList<>();
            for (FrameAction action : onArriveActions) {
                ConfigurationSection as = sec.createSection("on-arrive." + list.size());
                action.toSection(as);
                list.add(as);
            }
        }
    }

    /**
     * 生成一个位于指定世界、带偏移量的 Location。
     */
    public Location toLocation(org.bukkit.World world, double offsetX, double offsetY, double offsetZ) {
        return new Location(world, x + offsetX, y + offsetY, z + offsetZ, yaw, pitch);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public void setDurationTicks(int durationTicks) {
        this.durationTicks = Math.max(1, durationTicks);
    }

    public int getTransitionTicks() {
        return transitionTicks;
    }

    public void setTransitionTicks(int transitionTicks) {
        this.transitionTicks = Math.max(0, transitionTicks);
        this.transitionSet = true;
    }

    public boolean isTransitionSet() {
        return transitionSet;
    }

    public int getPreDelayTicks() {
        return preDelayTicks;
    }

    public void setPreDelayTicks(int preDelayTicks) {
        this.preDelayTicks = Math.max(0, preDelayTicks);
    }

    public int getPostDelayTicks() {
        return postDelayTicks;
    }

    public void setPostDelayTicks(int postDelayTicks) {
        this.postDelayTicks = Math.max(0, postDelayTicks);
    }

    public List<FrameAction> getOnStartActions() {
        return onStartActions;
    }

    public List<FrameAction> getOnArriveActions() {
        return onArriveActions;
    }
}
