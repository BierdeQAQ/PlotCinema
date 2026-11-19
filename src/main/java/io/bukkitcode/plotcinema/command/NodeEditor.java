package io.bukkitcode.plotcinema.command;

import io.bukkitcode.plotcinema.Plotcinema;
import io.bukkitcode.plotcinema.model.CameraFrame;
import io.bukkitcode.plotcinema.model.FrameAction;
import io.bukkitcode.plotcinema.model.Plot;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 聊天框点击式节点编辑器。
 * 通过可点击的聊天消息，让玩家在聊天框里完成节点增删改、动作配置、延迟配置等操作。
 *
 * 说明：聊天框无法直接弹出输入框，因此"需要输入内容"的项会通过
 * ClickEvent.Action.SUGGEST_COMMAND 把命令前缀填进输入框，玩家补完内容后回车执行。
 */
public class NodeEditor {

    private final Plotcinema plugin;

    public NodeEditor(Plotcinema plugin) {
        this.plugin = plugin;
    }

    /**
     * 打开剧情编辑主菜单。
     */
    public void openPlotMenu(Player player, Plot plot) {
        player.sendMessage("§6===== 编辑剧情: §e" + plot.getName() + " §6=====");
        player.sendMessage("§7世界: §f" + plot.getWorldName() + " §7| 节点数: §f" + plot.getFrames().size());

        // 添加节点
        TextComponent addNode = button("§a[+ 添加节点]",
                "在当前站位添加一个节点",
                ClickEvent.Action.RUN_COMMAND,
                "/plot node add " + plot.getName());
        player.spigot().sendMessage(addNode);

        // 节点列表
        List<CameraFrame> frames = plot.getFrames();
        if (frames.isEmpty()) {
            player.sendMessage("§7（还没有任何节点）");
        } else {
            player.sendMessage("§7点击节点进入编辑：");
            for (int i = 0; i < frames.size(); i++) {
                CameraFrame f = frames.get(i);
                String label = String.format("§e[节点 %d] §7(%.1f, %.1f, %.1f) §8前延迟:%d 后延迟:%d 过渡:%d 停留:%d",
                        i + 1, f.getX(), f.getY(), f.getZ(),
                        f.getPreDelayTicks(), f.getPostDelayTicks(),
                        f.getTransitionTicks(), f.getDurationTicks());
                TextComponent nodeBtn = button(label,
                        "点击编辑节点 " + (i + 1),
                        ClickEvent.Action.RUN_COMMAND,
                        "/plot node edit " + plot.getName() + " " + (i + 1));
                player.spigot().sendMessage(nodeBtn);
            }
        }

        // 保存 / 删除
        TextComponent save = button("§a[保存录像]",
                "把当前剧情写入 plots.yml",
                ClickEvent.Action.RUN_COMMAND,
                "/plot save " + plot.getName());
        player.spigot().sendMessage(save);

        TextComponent delete = button("§c[删除剧情]",
                "删除整段剧情（不可恢复）",
                ClickEvent.Action.RUN_COMMAND,
                "/plot delete " + plot.getName());
        player.spigot().sendMessage(delete);
    }

    /**
     * 打开单个节点的编辑菜单。
     */
    public void openNodeMenu(Player player, Plot plot, int index) {
        List<CameraFrame> frames = plot.getFrames();
        if (index < 1 || index > frames.size()) {
            player.sendMessage("§c节点序号无效。");
            return;
        }
        CameraFrame f = frames.get(index - 1);
        String plotName = plot.getName();

        player.sendMessage("§6===== 节点 " + index + " / " + frames.size() + " =====");
        player.sendMessage(String.format("§7坐标: §f%.2f, %.2f, %.2f §7朝向: §fyaw=%.1f pitch=%.1f",
                f.getX(), f.getY(), f.getZ(), f.getYaw(), f.getPitch()));

        // 前延迟
        TextComponent preDelay = button("§e[前延迟: " + f.getPreDelayTicks() + " tick] §7点击修改",
                "进入本节点前等待的时间（20 tick = 1 秒）",
                ClickEvent.Action.SUGGEST_COMMAND,
                "/plot node predelay " + plotName + " " + index + " ");
        player.spigot().sendMessage(preDelay);

        // 后延迟
        TextComponent postDelay = button("§e[后延迟: " + f.getPostDelayTicks() + " tick] §7点击修改",
                "本节点停留结束后额外等待的时间",
                ClickEvent.Action.SUGGEST_COMMAND,
                "/plot node postdelay " + plotName + " " + index + " ");
        player.spigot().sendMessage(postDelay);

        // 过渡
        TextComponent transition = button("§e[过渡: " + f.getTransitionTicks() + " tick] §7点击修改",
                "从上一节点平移到本节点所用的时间，0 = 瞬移",
                ClickEvent.Action.SUGGEST_COMMAND,
                "/plot node transition " + plotName + " " + index + " ");
        player.spigot().sendMessage(transition);

        // 停留
        TextComponent duration = button("§e[停留: " + f.getDurationTicks() + " tick] §7点击修改",
                "到达本节点后原地停留的时间",
                ClickEvent.Action.SUGGEST_COMMAND,
                "/plot node duration " + plotName + " " + index + " ");
        player.spigot().sendMessage(duration);

        // 动作
        player.sendMessage("§7--- 动作 ---");
        TextComponent addAction = button("§a[+ 添加动作]",
                "给本节点添加一个动作",
                ClickEvent.Action.SUGGEST_COMMAND,
                "/plot node action " + plotName + " " + index + " arrive ");
        player.spigot().sendMessage(addAction);

        List<FrameAction> startActions = f.getOnStartActions();
        List<FrameAction> arriveActions = f.getOnArriveActions();
        if (startActions.isEmpty() && arriveActions.isEmpty()) {
            player.sendMessage("§7（还没有任何动作）");
        } else {
            for (int i = 0; i < startActions.size(); i++) {
                FrameAction a = startActions.get(i);
                TextComponent btn = button("§b[开始] §f" + a.getType().name() + " §7-> §f" + a.getValue() + " §c[删除]",
                        "点击删除这个动作",
                        ClickEvent.Action.RUN_COMMAND,
                        "/plot node delaction " + plotName + " " + index + " start " + i);
                player.spigot().sendMessage(btn);
            }
            for (int i = 0; i < arriveActions.size(); i++) {
                FrameAction a = arriveActions.get(i);
                TextComponent btn = button("§b[到达] §f" + a.getType().name() + " §7-> §f" + a.getValue() + " §c[删除]",
                        "点击删除这个动作",
                        ClickEvent.Action.RUN_COMMAND,
                        "/plot node delaction " + plotName + " " + index + " arrive " + i);
                player.spigot().sendMessage(btn);
            }
        }

        // 删除节点 / 返回
        TextComponent delNode = button("§c[删除此节点]",
                "删除节点 " + index,
                ClickEvent.Action.RUN_COMMAND,
                "/plot node del " + plotName + " " + index);
        player.spigot().sendMessage(delNode);

        TextComponent back = button("§7[返回剧情菜单]",
                "回到剧情编辑主菜单",
                ClickEvent.Action.RUN_COMMAND,
                "/plot edit " + plotName);
        player.spigot().sendMessage(back);
    }

    /**
     * 构造一个可点击的文本按钮。
     */
    private TextComponent button(String text, String hover, ClickEvent.Action action, String value) {
        TextComponent component = new TextComponent(TextComponent.fromLegacyText(text));
        component.setClickEvent(new ClickEvent(action, value));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(hover).create()));
        return component;
    }
}
