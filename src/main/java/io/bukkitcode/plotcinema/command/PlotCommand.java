package io.bukkitcode.plotcinema.command;

import io.bukkitcode.plotcinema.Plotcinema;
import io.bukkitcode.plotcinema.model.CameraFrame;
import io.bukkitcode.plotcinema.model.FrameAction;
import io.bukkitcode.plotcinema.model.Plot;
import io.bukkitcode.plotcinema.record.RecordManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 主命令处理器。
 * 命令：/plot <子命令>
 * 子命令：
 *   play <剧情名> [玩家...]  - 播放剧情（可指定多人同步）
 *   record <剧情名> [auto|manual] [帧时长tick] - 开始录制
 *   mark                     - 手动打点（手动录制模式下）
 *   stop                     - 结束录制并保存 / 终止播放
 *   skip                     - 跳过当前镜头
 *   cancel                   - 取消录制
 *   list                     - 列出所有剧情
 *   delete <剧情名>          - 删除剧情
 *   reload                   - 重载配置
 */
public class PlotCommand implements CommandExecutor, TabCompleter {

    private final Plotcinema plugin;
    private final NodeEditor nodeEditor;

    public PlotCommand(Plotcinema plugin) {
        this.plugin = plugin;
        this.nodeEditor = new NodeEditor(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "play":
                return handlePlay(sender, args);
            case "record":
                return handleRecord(sender, args);
            case "mark":
                return handleMark(sender);
            case "action":
                return handleAction(sender, args);
            case "transition":
                return handleTransition(sender, args);
            case "edit":
                return handleEdit(sender, args);
            case "node":
                return handleNode(sender, args);
            case "save":
                return handleSave(sender, args);
            case "stop":
                return handleStop(sender);
            case "skip":
                return handleSkip(sender);
            case "cancel":
                return handleCancel(sender);
            case "list":
                return handleList(sender);
            case "delete":
                return handleDelete(sender, args);
            case "reload":
                return handleReload(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handlePlay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以播放剧情。");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("plotcinema.play")) {
            player.sendMessage("§c你没有权限播放剧情。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§c用法: /plot play <剧情名> [玩家1 玩家2 ...]");
            return true;
        }
        String plotName = args[1];
        Plot plot = plugin.getPlotStorage().get(plotName);
        if (plot == null) {
            player.sendMessage("§c找不到剧情 §e" + plotName + "§c。");
            return true;
        }

        // 若指定了其他玩家，则多人同步播放
        if (args.length > 2) {
            List<Player> targets = new ArrayList<>();
            targets.add(player);
            for (int i = 2; i < args.length; i++) {
                Player target = Bukkit.getPlayerExact(args[i]);
                if (target != null && !targets.contains(target)) {
                    targets.add(target);
                }
            }
            int count = plugin.getPlayManager().playMulti(targets, plot);
            player.sendMessage("§a已开始多人同步播放剧情 §e" + plot.getName() + "§a，共 §e" + count + " §a人。");
        } else {
            plugin.getPlayManager().play(player, plot);
        }
        return true;
    }

    private boolean handleRecord(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以录制剧情。");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("plotcinema.record")) {
            player.sendMessage("§c你没有权限录制剧情。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§c用法: /plot record <剧情名> [auto|manual] [帧时长tick]");
            return true;
        }
        if (plugin.getRecordManager().isRecording(player)) {
            player.sendMessage("§c你已经在录制中了，先 /plot stop 或 /plot cancel。");
            return true;
        }
        if (plugin.getPlayManager().isPlaying(player)) {
            player.sendMessage("§c剧情播放中无法录制。");
            return true;
        }

        String plotName = args[1];
        RecordManager.Mode mode = RecordManager.Mode.AUTO;
        if (args.length >= 3) {
            if (args[2].equalsIgnoreCase("manual")) {
                mode = RecordManager.Mode.MANUAL;
            } else if (args[2].equalsIgnoreCase("auto")) {
                mode = RecordManager.Mode.AUTO;
            }
        }
        int duration = 40;
        if (args.length >= 4) {
            try {
                duration = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                player.sendMessage("§c帧时长必须是数字（tick）。");
                return true;
            }
        }

        if (plugin.getRecordManager().start(player, plotName, mode, duration)) {
            player.sendMessage("§a开始录制剧情 §e" + plotName + "§a，模式: §e" + mode.name());
            if (mode == RecordManager.Mode.AUTO) {
                player.sendMessage("§7自动采样中，每 §e" + plugin.getConfigManager().getRecordIntervalTicks()
                        + " §7tick 记录一次。输入 §e/plot stop §7结束保存。");
            } else {
                player.sendMessage("§7手动打点模式，走到位置后输入 §e/plot mark §7记录一帧，§e/plot stop §7结束保存。");
            }
        } else {
            player.sendMessage("§c录制启动失败。");
        }
        return true;
    }

    private boolean handleMark(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以打点。");
            return true;
        }
        Player player = (Player) sender;
        if (plugin.getRecordManager().mark(player)) {
            int count = plugin.getRecordManager().getSession(player).getPlot().getFrames().size();
            player.sendMessage("§a已记录第 §e" + count + " §a帧。");
        } else {
            player.sendMessage("§c你当前不在手动录制模式中。");
        }
        return true;
    }

    /**
     * /plot action <start|arrive> <类型> <内容>
     * 给录制中的最后一帧挂动作。
     * 类型：command / title / actionbar / chat / sound / bossbar
     */
    private boolean handleAction(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令。");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("plotcinema.record")) {
            player.sendMessage("§c你没有权限配置动作。");
            return true;
        }
        if (!plugin.getRecordManager().isRecording(player)) {
            player.sendMessage("§c你当前没有在录制，无法给帧挂动作。");
            return true;
        }
        if (args.length < 4) {
            player.sendMessage("§c用法: /plot action <start|arrive> <类型> <内容>");
            player.sendMessage("§7类型: command / title / actionbar / chat / sound / bossbar");
            player.sendMessage("§7title 内容格式: 主标题|副标题");
            player.sendMessage("§7command 内容支持 %player% 占位");
            return true;
        }

        String timing = args[1].toLowerCase();
        if (!timing.equals("start") && !timing.equals("arrive")) {
            player.sendMessage("§c时机只能是 start 或 arrive。");
            return true;
        }

        FrameAction.Type type;
        try {
            type = FrameAction.Type.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c未知的动作类型: §e" + args[2]);
            player.sendMessage("§7可用: command / title / actionbar / chat / sound / bossbar");
            return true;
        }

        // 内容可能包含空格，从第 4 个参数开始拼接
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            if (i > 3) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        String value = sb.toString();

        Plot plot = plugin.getRecordManager().getSession(player).getPlot();
        if (plot.getFrames().isEmpty()) {
            player.sendMessage("§c还没有录制任何帧，先打点或走动录一帧。");
            return true;
        }
        CameraFrame lastFrame = plot.getFrames().get(plot.getFrames().size() - 1);
        FrameAction action = new FrameAction(type, value);
        if (timing.equals("start")) {
            lastFrame.getOnStartActions().add(action);
        } else {
            lastFrame.getOnArriveActions().add(action);
        }
        player.sendMessage("§a已给第 §e" + plot.getFrames().size() + " §a帧添加 §e" + timing
                + " §a动作: §e" + type.name() + " §7-> §f" + value);
        return true;
    }

    /**
     * /plot transition <tick>
     * 设置录制中最后一帧的过渡时长（从上一帧平移到本帧用多少 tick）。
     */
    private boolean handleTransition(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令。");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("plotcinema.record")) {
            player.sendMessage("§c你没有权限配置过渡。");
            return true;
        }
        if (!plugin.getRecordManager().isRecording(player)) {
            player.sendMessage("§c你当前没有在录制。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§c用法: /plot transition <tick>  （20 tick = 1 秒，0 = 瞬移）");
            return true;
        }
        int ticks;
        try {
            ticks = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c过渡时长必须是数字（tick）。");
            return true;
        }
        if (ticks < 0) {
            player.sendMessage("§c过渡时长不能为负数。");
            return true;
        }

        Plot plot = plugin.getRecordManager().getSession(player).getPlot();
        if (plot.getFrames().isEmpty()) {
            player.sendMessage("§c还没有录制任何帧。");
            return true;
        }
        CameraFrame lastFrame = plot.getFrames().get(plot.getFrames().size() - 1);
        lastFrame.setTransitionTicks(ticks);
        player.sendMessage("§a已设置第 §e" + plot.getFrames().size() + " §a帧的过渡时长为 §e"
                + ticks + " §atick（约 " + String.format("%.2f", ticks / 20.0) + " 秒）。");
        return true;
    }

    /**
     * /plot edit <剧情名>
     * 打开剧情编辑主菜单（聊天框点击式）。
     */
    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令。");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("plotcinema.record")) {
            player.sendMessage("§c你没有权限编辑剧情。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§c用法: /plot edit <剧情名>");
            return true;
        }
        Plot plot = plugin.getPlotStorage().get(args[1]);
        if (plot == null) {
            player.sendMessage("§c找不到剧情 §e" + args[1] + "§c。");
            return true;
        }
        nodeEditor.openPlotMenu(player, plot);
        return true;
    }

    /**
     * /plot save <剧情名>
     * 把剧情写入 plots.yml。
     */
    private boolean handleSave(CommandSender sender, String[] args) {
        if (!sender.hasPermission("plotcinema.record")) {
            sender.sendMessage("§c你没有权限保存剧情。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§c用法: /plot save <剧情名>");
            return true;
        }
        Plot plot = plugin.getPlotStorage().get(args[1]);
        if (plot == null) {
            sender.sendMessage("§c找不到剧情 §e" + args[1] + "§c。");
            return true;
        }
        plugin.getPlotStorage().saveAll();
        sender.sendMessage("§a剧情 §e" + plot.getName() + " §a已保存到 plots.yml。");
        return true;
    }

    /**
     * /plot node <子命令> ...
     * 节点编辑相关命令。
     */
    private boolean handleNode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令。");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("plotcinema.record")) {
            player.sendMessage("§c你没有权限编辑节点。");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§c用法: /plot node <add|edit|del|predelay|postdelay|transition|duration|action|delaction> ...");
            return true;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "add":
                return handleNodeAdd(player, args);
            case "edit":
                return handleNodeEdit(player, args);
            case "del":
                return handleNodeDel(player, args);
            case "predelay":
                return handleNodeNumber(player, args, "predelay");
            case "postdelay":
                return handleNodeNumber(player, args, "postdelay");
            case "transition":
                return handleNodeNumber(player, args, "transition");
            case "duration":
                return handleNodeNumber(player, args, "duration");
            case "action":
                return handleNodeAction(player, args);
            case "delaction":
                return handleNodeDelAction(player, args);
            default:
                player.sendMessage("§c未知的节点操作: §e" + action);
                return true;
        }
    }

    /**
     * /plot node add <剧情名>
     * 在玩家当前站位添加一个节点。
     */
    private boolean handleNodeAdd(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /plot node add <剧情名>");
            return true;
        }
        Plot plot = plugin.getPlotStorage().get(args[2]);
        if (plot == null) {
            player.sendMessage("§c找不到剧情 §e" + args[2] + "§c。");
            return true;
        }
        CameraFrame frame = CameraFrame.fromLocation(player.getLocation(), 40);
        plot.getFrames().add(frame);
        plugin.getPlotStorage().saveAll();
        player.sendMessage("§a已在当前站位添加节点 §e" + plot.getFrames().size()
                + " §a（剧情 §e" + plot.getName() + "§a）。");
        nodeEditor.openNodeMenu(player, plot, plot.getFrames().size());
        return true;
    }

    /**
     * /plot node edit <剧情名> <序号>
     */
    private boolean handleNodeEdit(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§c用法: /plot node edit <剧情名> <序号>");
            return true;
        }
        Plot plot = plugin.getPlotStorage().get(args[2]);
        if (plot == null) {
            player.sendMessage("§c找不到剧情 §e" + args[2] + "§c。");
            return true;
        }
        int index = parseInt(player, args[3]);
        if (index < 0) {
            return true;
        }
        nodeEditor.openNodeMenu(player, plot, index);
        return true;
    }

    /**
     * /plot node del <剧情名> <序号>
     */
    private boolean handleNodeDel(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§c用法: /plot node del <剧情名> <序号>");
            return true;
        }
        Plot plot = plugin.getPlotStorage().get(args[2]);
        if (plot == null) {
            player.sendMessage("§c找不到剧情 §e" + args[2] + "§c。");
            return true;
        }
        int index = parseInt(player, args[3]);
        if (index < 0) {
            return true;
        }
        if (index < 1 || index > plot.getFrames().size()) {
            player.sendMessage("§c节点序号无效。");
            return true;
        }
        plot.getFrames().remove(index - 1);
        plugin.getPlotStorage().saveAll();
        player.sendMessage("§a已删除节点 §e" + index + "§a，当前剩余 §e" + plot.getFrames().size() + " §a个节点。");
        nodeEditor.openPlotMenu(player, plot);
        return true;
    }

    /**
     * /plot node <predelay|postdelay|transition|duration> <剧情名> <序号> <数值>
     */
    private boolean handleNodeNumber(Player player, String[] args, String field) {
        if (args.length < 5) {
            player.sendMessage("§c用法: /plot node " + field + " <剧情名> <序号> <tick>");
            return true;
        }
        Plot plot = plugin.getPlotStorage().get(args[2]);
        if (plot == null) {
            player.sendMessage("§c找不到剧情 §e" + args[2] + "§c。");
            return true;
        }
        int index = parseInt(player, args[3]);
        if (index < 0) {
            return true;
        }
        if (index < 1 || index > plot.getFrames().size()) {
            player.sendMessage("§c节点序号无效。");
            return true;
        }
        int value = parseInt(player, args[4]);
        if (value < 0) {
            return true;
        }
        CameraFrame frame = plot.getFrames().get(index - 1);
        switch (field) {
            case "predelay":
                frame.setPreDelayTicks(value);
                break;
            case "postdelay":
                frame.setPostDelayTicks(value);
                break;
            case "transition":
                frame.setTransitionTicks(value);
                break;
            case "duration":
                frame.setDurationTicks(value);
                break;
            default:
                break;
        }
        plugin.getPlotStorage().saveAll();
        player.sendMessage("§a节点 §e" + index + " §a的 §e" + field + " §a已设为 §e" + value
                + " §atick（约 " + String.format("%.2f", value / 20.0) + " 秒）。");
        nodeEditor.openNodeMenu(player, plot, index);
        return true;
    }

    /**
     * /plot node action <剧情名> <序号> <start|arrive> <类型> <内容>
     */
    private boolean handleNodeAction(Player player, String[] args) {
        if (args.length < 6) {
            player.sendMessage("§c用法: /plot node action <剧情名> <序号> <start|arrive> <类型> <内容>");
            player.sendMessage("§7类型: command / title / actionbar / chat / sound / bossbar");
            return true;
        }
        Plot plot = plugin.getPlotStorage().get(args[2]);
        if (plot == null) {
            player.sendMessage("§c找不到剧情 §e" + args[2] + "§c。");
            return true;
        }
        int index = parseInt(player, args[3]);
        if (index < 0) {
            return true;
        }
        if (index < 1 || index > plot.getFrames().size()) {
            player.sendMessage("§c节点序号无效。");
            return true;
        }
        String timing = args[4].toLowerCase();
        if (!timing.equals("start") && !timing.equals("arrive")) {
            player.sendMessage("§c时机只能是 start 或 arrive。");
            return true;
        }
        FrameAction.Type type;
        try {
            type = FrameAction.Type.valueOf(args[5].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c未知的动作类型: §e" + args[5]);
            return true;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 6; i < args.length; i++) {
            if (i > 6) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        String value = sb.toString();

        CameraFrame frame = plot.getFrames().get(index - 1);
        FrameAction action = new FrameAction(type, value);
        if (timing.equals("start")) {
            frame.getOnStartActions().add(action);
        } else {
            frame.getOnArriveActions().add(action);
        }
        plugin.getPlotStorage().saveAll();
        player.sendMessage("§a已给节点 §e" + index + " §a添加 §e" + timing + " §a动作: §e"
                + type.name() + " §7-> §f" + value);
        nodeEditor.openNodeMenu(player, plot, index);
        return true;
    }

    /**
     * /plot node delaction <剧情名> <序号> <start|arrive> <动作序号>
     */
    private boolean handleNodeDelAction(Player player, String[] args) {
        if (args.length < 6) {
            player.sendMessage("§c用法: /plot node delaction <剧情名> <序号> <start|arrive> <动作序号>");
            return true;
        }
        Plot plot = plugin.getPlotStorage().get(args[2]);
        if (plot == null) {
            player.sendMessage("§c找不到剧情 §e" + args[2] + "§c。");
            return true;
        }
        int index = parseInt(player, args[3]);
        if (index < 0) {
            return true;
        }
        if (index < 1 || index > plot.getFrames().size()) {
            player.sendMessage("§c节点序号无效。");
            return true;
        }
        String timing = args[4].toLowerCase();
        int actionIndex = parseInt(player, args[5]);
        if (actionIndex < 0) {
            return true;
        }
        CameraFrame frame = plot.getFrames().get(index - 1);
        List<FrameAction> list = timing.equals("start")
                ? frame.getOnStartActions()
                : frame.getOnArriveActions();
        if (actionIndex >= list.size()) {
            player.sendMessage("§c动作序号无效。");
            return true;
        }
        list.remove(actionIndex);
        plugin.getPlotStorage().saveAll();
        player.sendMessage("§a已删除节点 §e" + index + " §a的 §e" + timing + " §a动作 §e" + actionIndex + "§a。");
        nodeEditor.openNodeMenu(player, plot, index);
        return true;
    }

    /**
     * 解析整数，失败时给玩家提示并返回 -1。
     */
    private int parseInt(Player player, String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的数字: §e" + s);
            return -1;
        }
    }

    private boolean handleStop(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令。");
            return true;
        }
        Player player = (Player) sender;

        // 优先处理录制结束
        if (plugin.getRecordManager().isRecording(player)) {
            Plot plot = plugin.getRecordManager().stop(player);
            if (plot == null) {
                player.sendMessage("§c录制结束，但没有记录到任何镜头，已丢弃。");
                return true;
            }
            plugin.getPlotStorage().put(plot);
            plugin.getPlotStorage().saveAll();
            player.sendMessage("§a录制完成！剧情 §e" + plot.getName() + " §a共 §e"
                    + plot.getFrames().size() + " §a帧，已保存。");
            return true;
        }

        // 其次处理播放终止
        if (plugin.getPlayManager().isPlaying(player)) {
            plugin.getPlayManager().stop(player);
            player.sendMessage("§a已结束剧情播放。");
            return true;
        }

        player.sendMessage("§c你当前既没有录制也没有在播放剧情。");
        return true;
    }

    private boolean handleSkip(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令。");
            return true;
        }
        Player player = (Player) sender;
        if (plugin.getPlayManager().skip(player)) {
            player.sendMessage("§a已跳过当前镜头。");
        } else {
            player.sendMessage("§c你当前没有在观看剧情。");
        }
        return true;
    }

    private boolean handleCancel(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以使用此命令。");
            return true;
        }
        Player player = (Player) sender;
        if (plugin.getRecordManager().cancel(player)) {
            player.sendMessage("§a已取消录制，所有已录帧已丢弃。");
        } else {
            player.sendMessage("§c你当前没有在录制。");
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("plotcinema.play")) {
            sender.sendMessage("§c你没有权限查看剧情列表。");
            return true;
        }
        if (plugin.getPlotStorage().getNames().isEmpty()) {
            sender.sendMessage("§7当前没有任何剧情。");
            return true;
        }
        sender.sendMessage("§6===== 剧情列表 =====");
        for (String name : plugin.getPlotStorage().getNames()) {
            Plot plot = plugin.getPlotStorage().get(name);
            sender.sendMessage("§e- " + plot.getName() + " §7(世界: " + plot.getWorldName()
                    + ", 帧数: " + plot.getFrames().size() + ")");
        }
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("plotcinema.admin")) {
            sender.sendMessage("§c你没有权限删除剧情。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§c用法: /plot delete <剧情名>");
            return true;
        }
        if (plugin.getPlotStorage().remove(args[1])) {
            plugin.getPlotStorage().saveAll();
            sender.sendMessage("§a已删除剧情 §e" + args[1] + "§a。");
        } else {
            sender.sendMessage("§c找不到剧情 §e" + args[1] + "§c。");
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("plotcinema.admin")) {
            sender.sendMessage("§c你没有权限重载配置。");
            return true;
        }
        plugin.getConfigManager().load();
        plugin.getPlotStorage().loadAll();
        sender.sendMessage("§a配置与剧情已重载。");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6===== PlotCinema 剧情插件 =====");
        sender.sendMessage("§e/plot play <剧情名> [玩家...] §7- 播放剧情（多人同步）");
        sender.sendMessage("§e/plot record <剧情名> [auto|manual] [帧时长] §7- 开始录制");
        sender.sendMessage("§e/plot mark §7- 手动打点");
        sender.sendMessage("§e/plot transition <tick> §7- 设置最后一帧的过渡时长（20 tick = 1 秒）");
        sender.sendMessage("§e/plot action <start|arrive> <类型> <内容> §7- 给最后一帧挂动作");
        sender.sendMessage("§7  类型: command / title / actionbar / chat / sound / bossbar");
        sender.sendMessage("§e/plot stop §7- 结束录制保存 / 终止播放");
        sender.sendMessage("§e/plot skip §7- 跳过当前镜头");
        sender.sendMessage("§e/plot cancel §7- 取消录制");
        sender.sendMessage("§e/plot list §7- 列出所有剧情");
        sender.sendMessage("§e/plot edit <剧情名> §7- 打开聊天框点击式编辑菜单");
        sender.sendMessage("§e/plot node add <剧情名> §7- 在当前站位添加节点");
        sender.sendMessage("§e/plot node edit <剧情名> <序号> §7- 编辑指定节点");
        sender.sendMessage("§e/plot node del <剧情名> <序号> §7- 删除指定节点");
        sender.sendMessage("§e/plot node <predelay|postdelay|transition|duration> <剧情名> <序号> <tick>");
        sender.sendMessage("§e/plot node action <剧情名> <序号> <start|arrive> <类型> <内容>");
        sender.sendMessage("§e/plot node delaction <剧情名> <序号> <start|arrive> <动作序号>");
        sender.sendMessage("§e/plot save <剧情名> §7- 保存剧情到 plots.yml");
        sender.sendMessage("§e/plot delete <剧情名> §7- 删除剧情");
        sender.sendMessage("§e/plot reload §7- 重载配置");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("play", "record", "mark", "transition", "action",
                    "edit", "node", "save", "stop", "skip", "cancel", "list", "delete", "reload");
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("play") || sub.equals("delete") || sub.equals("edit") || sub.equals("save")) {
                return filter(new ArrayList<>(plugin.getPlotStorage().getNames()), args[1]);
            }
            if (sub.equals("record")) {
                return filter(new ArrayList<>(), args[1]);
            }
            if (sub.equals("action")) {
                return filter(Arrays.asList("start", "arrive"), args[1]);
            }
            if (sub.equals("node")) {
                return filter(Arrays.asList("add", "edit", "del", "predelay", "postdelay",
                        "transition", "duration", "action", "delaction"), args[1]);
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("record")) {
                return filter(Arrays.asList("auto", "manual"), args[2]);
            }
            if (sub.equals("play")) {
                List<String> names = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).collect(Collectors.toList());
                return filter(names, args[2]);
            }
            if (sub.equals("action")) {
                return filter(Arrays.asList("command", "title", "actionbar", "chat", "sound", "bossbar"), args[2]);
            }
            if (sub.equals("node")) {
                // /plot node <操作> <剧情名>
                return filter(new ArrayList<>(plugin.getPlotStorage().getNames()), args[2]);
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("node")) {
            // /plot node <操作> <剧情名> <序号>
            return filter(Arrays.asList("1", "2", "3", "4", "5"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("node")) {
            String op = args[1].toLowerCase();
            if (op.equals("action") || op.equals("delaction")) {
                return filter(Arrays.asList("start", "arrive"), args[4]);
            }
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("action")) {
            return filter(Arrays.asList("command", "title", "actionbar", "chat", "sound", "bossbar"), args[5]);
        }
        if (args.length > 3 && args[0].equalsIgnoreCase("play")) {
            List<String> names = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).collect(Collectors.toList());
            return filter(names, args[args.length - 1]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> source, String prefix) {
        String lower = prefix.toLowerCase();
        return source.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
