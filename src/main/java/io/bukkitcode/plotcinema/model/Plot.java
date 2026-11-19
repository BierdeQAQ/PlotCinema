package io.bukkitcode.plotcinema.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 一段剧情。
 * 包含：剧情名、录制所在世界、镜头帧列表、可选的结束点坐标。
 */
public class Plot {

    private final String name;
    private String worldName;
    private final List<CameraFrame> frames = new ArrayList<>();

    /** 剧情结束后的落点（可选）。若为 null 则回到播放前位置。 */
    private Double endX;
    private Double endY;
    private Double endZ;
    private Float endYaw;
    private Float endPitch;

    public Plot(String name) {
        this.name = name;
    }

    /**
     * 从 yml 配置节反序列化。
     */
    public static Plot fromSection(String name, ConfigurationSection sec) {
        Plot plot = new Plot(name);
        plot.worldName = sec.getString("world", "world");

        List<?> rawFrames = sec.getList("frames");
        if (rawFrames != null) {
            for (Object obj : rawFrames) {
                if (obj instanceof ConfigurationSection) {
                    plot.frames.add(CameraFrame.fromSection((ConfigurationSection) obj));
                }
            }
        }

        if (sec.contains("end.x")) {
            plot.endX = sec.getDouble("end.x");
            plot.endY = sec.getDouble("end.y");
            plot.endZ = sec.getDouble("end.z");
            plot.endYaw = (float) sec.getDouble("end.yaw");
            plot.endPitch = (float) sec.getDouble("end.pitch");
        }
        return plot;
    }

    /**
     * 序列化到 yml 配置节。
     */
    public void toSection(ConfigurationSection sec) {
        sec.set("world", worldName);
        List<ConfigurationSection> frameSections = new ArrayList<>();
        for (CameraFrame frame : frames) {
            ConfigurationSection fs = sec.createSection("frames." + frameSections.size());
            frame.toSection(fs);
            frameSections.add(fs);
        }
        if (endX != null) {
            sec.set("end.x", endX);
            sec.set("end.y", endY);
            sec.set("end.z", endZ);
            sec.set("end.yaw", (double) endYaw);
            sec.set("end.pitch", (double) endPitch);
        }
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public List<CameraFrame> getFrames() {
        return frames;
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    public boolean hasEndPoint() {
        return endX != null;
    }

    public Double getEndX() {
        return endX;
    }

    public Double getEndY() {
        return endY;
    }

    public Double getEndZ() {
        return endZ;
    }

    public Float getEndYaw() {
        return endYaw;
    }

    public Float getEndPitch() {
        return endPitch;
    }

    public void setEndPoint(double x, double y, double z, float yaw, float pitch) {
        this.endX = x;
        this.endY = y;
        this.endZ = z;
        this.endYaw = yaw;
        this.endPitch = pitch;
    }
}
