# PlotCinema

剧情镜头演出插件 —— 通过不断传送玩家视角，实现"剧情动画"效果。

适用于 Paper / Spigot **1.20.x**。

---

## 这是什么

PlotCinema 不是任务插件，也不是对话插件。它做的事情只有一件：

> 把玩家的视角按预设的"节点"依次传送过去，节点之间平滑平移，配合标题、音效、旁白、BossBar 字幕、指令等动作，拼出一段剧情动画。

你可以把它理解成"用玩家自己的视角当摄像机"。

---

## 核心概念

| 概念 | 说明 |
| --- | --- |
| 剧情（Plot） | 一段完整的镜头演出，有名字、录制世界、若干节点 |
| 节点（Node） | 一个机位：坐标 + 朝向 + 停留时长 + 过渡时长 + 前延迟 + 后延迟 + 动作列表 |
| 过渡（Transition） | 从上一节点平移到本节点所用的时长，位置与朝向一起线性插值 |
| 前延迟（Pre-Delay） | 进入本节点前等待的时间 |
| 后延迟（Post-Delay） | 本节点停留结束后额外等待的时间 |
| 动作（Action） | 节点上挂的演出：执行指令、Title、ActionBar、旁白、音效、BossBar |
| 录制（Record） | 现场把玩家走过的路径记录成节点 |
| 播放（Play） | 把节点依次传送给玩家，形成动画 |

---

## 播放流程（四阶段）

每个节点的播放分四个阶段：

1. **前延迟**：等待 `pre-delay` 个 tick
2. **过渡**：从上一节点位置线性插值到本节点位置，位置 + yaw + pitch 一起动，持续 `transition` 个 tick
3. **停留**：到达本节点位置后原地停留，持续 `duration` 个 tick
4. **后延迟**：额外等待 `post-delay` 个 tick

**第一个节点特殊处理**：播放开始时先瞬移到节点 1 的位置，然后进入前延迟，延迟结束才真正开始节点 1。

动作分两个时机触发：

- `on-start`：过渡开始那一刻
- `on-arrive`：过渡完成、到达机位那一刻

---

## 播放期间效果

播放期间自动给玩家：

- **隐身**（结束后移除）
- **攻速归零**（结束后还原）

播放结束后：

- 立刻传送回播放前的位置
- 恢复播放前的视角（yaw / pitch 都还原）

以上效果可在 `config.yml` 的 `play.effects` 段开关。

---

## 命令

主命令 `/plot`，别名 `/plotcinema`、`/pc`。

### 播放与录制

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/plot play <剧情名> [玩家...]` | 播放剧情；指定玩家则多人同步播放 | `plotcinema.play` |
| `/plot record <剧情名> [auto\|manual] [帧时长]` | 开始录制 | `plotcinema.record` |
| `/plot mark` | 手动打点，记录当前机位为一个节点 | `plotcinema.record` |
| `/plot stop` | 结束录制并保存 / 终止正在播放的剧情 | `plotcinema.play` |
| `/plot skip` | 跳过当前节点 | `plotcinema.play` |
| `/plot cancel` | 取消录制，丢弃已录节点 | `plotcinema.record` |
| `/plot list` | 列出所有剧情 | `plotcinema.play` |

### 节点编辑

| 命令 | 说明 |
| --- | --- |
| `/plot edit <剧情名>` | 打开聊天框点击式编辑菜单 |
| `/plot node add <剧情名>` | 在当前站位添加一个节点 |
| `/plot node edit <剧情名> <序号>` | 打开指定节点的编辑菜单 |
| `/plot node del <剧情名> <序号>` | 删除指定节点 |
| `/plot node predelay <剧情名> <序号> <tick>` | 设置节点前延迟 |
| `/plot node postdelay <剧情名> <序号> <tick>` | 设置节点后延迟 |
| `/plot node transition <剧情名> <序号> <tick>` | 设置节点过渡时长 |
| `/plot node duration <剧情名> <序号> <tick>` | 设置节点停留时长 |
| `/plot node action <剧情名> <序号> <start\|arrive> <类型> <内容>` | 给节点添加动作 |
| `/plot node delaction <剧情名> <序号> <start\|arrive> <动作序号>` | 删除节点动作 |
| `/plot save <剧情名>` | 保存剧情到 plots.yml |
| `/plot delete <剧情名>` | 删除剧情 |
| `/plot reload` | 重载配置与剧情 |

---

## 聊天框点击式编辑

`/plot edit <剧情名>` 会打开一个可点击的菜单：

```
===== 编辑剧情: 开场动画 =====
世界: world | 节点数: 3
[+ 添加节点]
[节点 1] (100.5, 65.0, 200.5) 前延迟:0 后延迟:0 过渡:20 停留:40
[节点 2] (110.5, 70.0, 210.5) 前延迟:10 后延迟:0 过渡:40 停留:60
[节点 3] (120.5, 75.0, 220.5) 前延迟:0 后延迟:20 过渡:30 停留:40
[保存录像]
[删除剧情]
```

点击节点进入节点编辑菜单：

```
===== 节点 1 / 3 =====
坐标: 100.50, 65.00, 200.50 朝向: yaw=90.0 pitch=0.0
[前延迟: 0 tick] 点击修改
[后延迟: 0 tick] 点击修改
[过渡: 20 tick] 点击修改
[停留: 40 tick] 点击修改
--- 动作 ---
[+ 添加动作]
[到达] TITLE -> &6第一章|&7命运的起点 [删除]
[开始] SOUND -> ENTITY_ENDERMAN_TELEPORT [删除]
[删除此节点]
[返回剧情菜单]
```

**说明**：聊天框无法弹出输入框，所以"需要输入内容"的项（延迟、动作等）点击后会把命令前缀填进输入框，你补完内容后回车执行。

---

## 动作类型

| 类型 | 内容格式 | 说明 |
| --- | --- | --- |
| `command` | 指令文本 | 以控制台身份执行，支持 `%player%` 占位 |
| `title` | `主标题\|副标题` | 发送标题，支持 `&` 颜色代码 |
| `actionbar` | 文字 | 发送动作栏文字 |
| `chat` | 文字 | 发送旁白 |
| `sound` | 音效枚举名 | 如 `ENTITY_ENDERMAN_TELEPORT` |
| `bossbar` | 文字 | 设置 BossBar 字幕 |

示例：

```
/plot node action 开场动画 1 arrive title &6第一章|&7命运的起点
/plot node action 开场动画 1 arrive command give %player% diamond 1
/plot node action 开场动画 2 start sound ENTITY_ENDERMAN_TELEPORT
```

---

## 录制流程

### 自动采样模式

```
/plot record 开场动画 auto 40
```

- 插件每隔 `record.interval-ticks`（默认 2）tick 自动记录一次你的位置
- 输入 `/plot stop` 结束并保存

### 手动打点模式

```
/plot record 开场动画 manual 60
```

- 走到想要的机位，输入 `/plot mark` 记录一个节点
- 用 `/plot edit 开场动画` 打开菜单，给节点配延迟、过渡、动作
- 输入 `/plot stop` 结束并保存

> 所有时长单位都是 tick，20 tick = 1 秒。

---

## 配置文件

`plugins/PlotCinema/config.yml`，关键项：

| 配置项 | 说明 |
| --- | --- |
| `version` | 配置版本号，请勿手动修改 |
| `record.interval-ticks` | 自动采样间隔（tick） |
| `play.default-transition-ticks` | 默认节点过渡时长（tick），节点未单独设置时使用 |
| `play.allow-relative-play` | 玩家不在录制世界时，是否按相对坐标偏移播放 |
| `play.notify-outdated-config` | OP 进服时是否提示配置过旧 |
| `play.effects.invisibility` | 播放期间是否隐身 |
| `play.effects.zero-attack-speed` | 播放期间是否攻速归零 |
| `lock.movement` | 播放期间禁止移动 |
| `lock.rotation` | 播放期间锁定视角 |
| `lock.interact` | 播放期间禁止攻击/交互 |

---

## 数据存储

所有剧情保存在 `plugins/PlotCinema/plots.yml`。

结构示例：

```yaml
plots:
  开场动画:
    world: world
    frames:
      '0':
        x: 100.5
        y: 65.0
        z: 200.5
        yaw: 90.0
        pitch: 0.0
        duration: 40
        transition: 20
        pre-delay: 0
        post-delay: 0
        on-start:
          '0':
            type: SOUND
            value: ENTITY_ENDERMAN_TELEPORT
        on-arrive:
          '0':
            type: TITLE
            value: '&6第一章|&7命运的起点'
          '1':
            type: COMMAND
            value: 'give %player% diamond 1'
      '1':
        x: 110.5
        y: 70.0
        z: 210.5
        yaw: 180.0
        pitch: -10.0
        duration: 60
        transition: 40
        pre-delay: 10
```

你可以直接手改 `plots.yml` 精修，改完 `/plot reload` 生效。

---

## 已知限制

1. **传送视角会有轻微抖动**，这是原版客户端渲染决定的，无法完全消除。建议过渡时长不低于 10 tick。
2. **反作弊插件可能误判**。剧情期间玩家位置被插件强制改变，AAC / Matrix 这类可能判定为异常移动。建议给剧情玩家加白名单，或临时关闭检测。
3. **相对坐标播放有穿模风险**。默认关闭，开启后镜头可能与地形不匹配。
4. `lock.hide-hud` 与 `lock.hide-other-players` 目前为预留配置，尚未实现实际效果。
5. **多人同步观看时性能开销线性增长**。每个玩家每 tick 都在 teleport，10 人同看约每秒 200 次 teleport。Paper 能扛，但别一次拉太多人。
6. **聊天框编辑无法弹输入框**，需要输入内容的项只能填前缀后手动补全。

---

## 作者

Bierder
