# .sbox v1 配置包规范

> 状态：v1 草案，已在 Redmi 25053RT47C / SM8735 / Android 16 / Scene 8.1.5 上跑通
> 实现参考：`engine/sboxfile.py`（打包）、`engine/analyze.py`（校验）

## 1. 这个格式要解决什么

Scene 的调度配置（`profile.json`）本身是一段**机器强相关**的 sysfs 写入脚本集合：
里面写死了节点路径、频点数值、限制器开关。直接把它复制到另一台机器上，
轻则大量写入被内核静默钳制（用户以为生效了，其实没生效），重则关掉温控限制器后
长时间满载过热锁核。

`.sbox` 在原始配置外面套了三层：

1. **指纹（target）**——这份配置是从什么机器上抄下来的；
2. **组件拆分（payload）**——调度/档位/帧率/温控各自独立，可只带其中几项；
3. **预分析（analysis）**——生成时就把"关限制器""写温控"这类写入标出来。

导入端拿到这三层，才能在写入之前回答："这份配置在我的机器上会发生什么"。

## 2. 数据源映射（实测 Scene 8.1.5）

所有数据都在 `/data/data/com.omarea.vtools/` 下（app-private，无对外导出目录）：

| .sbox 组件 | Scene 数据源 | 说明 |
|---|---|---|
| `payload.profile` | `files/profile.json` | 调度方案本体：`alias` / `presets` / `schemes`，一个方案 219 条写入（本机实测） |
| `payload.app_policy` | `shared_prefs/powercfg.xml` | 包名/Activity → `powersave/balance/performance/fast/igoned` |
| `payload.games` | `shared_prefs/games.xml` | 被标记为游戏的包（值为 true） |
| `payload.refresh_rate` | `files/REFRESH_RATE.CONF` | 每行 `包名 最小帧 最大帧` |
| `payload.per_app` | `databases/scene_config3` | 每应用 `alone_light` / `light` / `freeze` / `screen_orientation` 等 |
| `payload.thermal` | `files/miui-thermal/*`、`files/kr-script/miui/thermal_conf3` | 温控预设名（15 套）；实体是几十个 `thermal-*.conf`，默认不打包 |

`files/_Games.json` 等分类级覆盖也读得到，但它是"分类模板"不是"用户配置"，
v1 先不纳入 payload（避免把作者意图和用户改动混在一起）。

## 3. 结构

```jsonc
{
  "format": "sbox",
  "format_version": 1,
  "id": "sbox_20261118_123036_2ca171",
  "meta": { "name": "...", "author": "...", "tags": { "game": ["三角洲行动"], "soc": ["SM8735"] } },
  "target": {
    "device": { "brand": "Redmi", "model": "25053RT47C" },
    "soc":    { "platform": "sun", "model": "SM8735" },
    "os":     { "android": "16", "sdk": "36" },
    "scene":  { "daemon": "2.4.4", "cloud_profile_version": 20250411 },
    "cpu":    { "policies": { "policy0": { "min": 364800, "max": 2016000, "avail": [...] } } }
  },
  "payload": { "components": {...}, "profile": {...}, "app_policy": {...}, ... },
  "analysis": { "risk": "high", "items": [...], "stats": {...} },
  "checksum": { "algo": "sha256", "value": "..." }
}
```

- `target.cpu.policies` 是**采集时**的频点表，用于"跨机迁移"时做数值映射；
  校验永远以**本机实时读到的表**为准，不信任包里的表。
- `checksum` 覆盖除自身外的全部字段（canonical JSON），防止社区里被二次编辑后
  冒充原作者。

## 4. 导入校验规则

三级结论：`pass` 直接导入 / `confirm` 需用户明确确认 / `block` 禁止导入。

| 级别 | code | 条件 | 为什么 |
|---|---|---|---|
| **block** | `SOC_MISMATCH` | `target.soc.platform` ≠ 本机 | 跨平台 sysfs 节点语义完全不同，写进去等于乱写 |
| **block** | `BAD_PATH` | 路径非绝对 或 含 `..` | 防止配置包变成任意文件写入的原语 |
| **block** | `BAD_ALIAS` | alias 值非法 | 同上 |
| **confirm** | `AGGRESSIVE` | 含关闭限制器/改温控的高风险写入 | 这类写入是"变热/锁核"的直接来源，必须显式确认 |
| **warn** | `SOC_MODEL_DIFF` | 同平台不同型号 | 频点表不同（本机实测：同平台也有 29 处频点不存在） |
| **warn** | `DEVICE_DIFF` | 机型不同 | 散热/温控阈值差异 |
| **warn** | `ANDROID_DIFF` | 系统大版本不同 | 节点位置可能变，缺失节点会被跳过 |
| **warn** | `FREQ_ABOVE` | 写入频率 > 本机上限 | 会被钳制，配置"看起来生效其实没有" |
| **warn** | `FREQ_UNKNOWN` | 频率不在本机频点表 | 同上，且效果不可预测 |
| **warn** | `MISSING_ALIAS` | 引用了未定义别名 | Scene 自带方案里就有 3 个（实测），daemon 用内置默认值兜底 |
| **info** | `EMPTY_COMPONENT` | 组件标记启用但内容为空 | 导入时跳过 |

### 风险判定（`analysis`）

| 路径特征 | 危险值 | 级别 | 含义 |
|---|---|---|---|
| `disable_cpufreq_limit` | `1` | high | 关掉内核 CPU 频率限制器 |
| `perfmgr_enable` | `0` | high | 关掉 perfmgr |
| `glk_disable` | `1` | high | 关掉 GLK 游戏内核限制 |
| `glk_freq_limit*` | `0` | medium | 关掉 GLK 频率限制 |
| `*thermal*` | 任意 | high | 动温控节点 |
| `sched_fmax_cap` | 任意 | medium | 改写调度上限 |

**只看路径会误报**：Scene 自带方案对 `disable_cpufreq_limit` 既会写 `1`（进游戏）
也会写 `0`（恢复），所以规则必须带值判断。

### 一个反直觉的实测结论

本机导入**自己导出的**方案，仍然报：

- 3 处频率高于本机上限（3283200 / 3513600 / 3801600）
- 29 处频率不在本机频点表

原因是 `profile.json` 是 Scene 云端下发的 **8GEN4 通用方案**（`platform: sun`），
而本机 SM8735 的频点表和它并不一致。也就是说：**同机型重装、同平台换机型，
配置都会静默失真**。这正是这个工具存在的理由——不是替 Scene 调度，而是让
"配置到底生没生效"这件事可见。

## 5. 安全边界

- 只读 Scene 自己的配置文件，不反编译、不 hook、不修改 Scene 本体。
- 只写系统侧 sysfs / proc 节点，不碰游戏进程、不碰内存、不做任何游戏内改动。
- 校验器强制路径白名单前缀（`/sys` `/proc` `/dev` `/data` `/cache`）。
- 所有写入前先做 `confirm` 判定，激进方案必须用户二次确认。

## 6. 版本策略

- `format_version` 为主版本，只增不改：v1 解析器遇到 v2 包直接拒绝并提示升级。
- 组件只增不删；新增组件必须默认 `false`，保证老解析器安全跳过。
- `analysis` 是"生成时快照"，导入端不信任它，会按本机规则重算一次。
