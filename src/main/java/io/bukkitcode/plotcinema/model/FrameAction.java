package io.bukkitcode.plotcinema.model;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 帧动作。
 * 表示镜头帧在某个时机要执行的一个动作，例如执行指令、播放 title、显示 actionbar 等。
 *
 * 动作类型：
 *   COMMAND   - 以控制台身份执行指令，支持 %player% 占位
 *   TITLE     - 发送标题，内容格式 "主标题|副标题"
 *   ACTIONBAR - 发送动作栏文字
 *   CHAT      - 发送聊天栏文字
 *   SOUND     - 播放音效，内容为音效枚举名
 *   BOSSBAR   - 设置 BossBar 字幕
 */
public class FrameAction {

    public enum Type {
        COMMAND,
        TITLE,
        ACTIONBAR,
        CHAT,
        SOUND,
        BOSSBAR
    }

    private final Type type;
    private final String value;

    public FrameAction(Type type, String value) {
        this.type = type;
        this.value = value;
    }

    /**
     * 从 yml 配置节反序列化。配置节需包含 type 与 value。
     */
    public static FrameAction fromSection(ConfigurationSection sec) {
        String typeStr = sec.getString("type", "COMMAND");
        Type type;
        try {
            type = Type.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = Type.COMMAND;
        }
        return new FrameAction(type, sec.getString("value", ""));
    }

    /**
     * 序列化到 yml 配置节。
     */
    public void toSection(ConfigurationSection sec) {
        sec.set("type", type.name());
        sec.set("value", value);
    }

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }
}
