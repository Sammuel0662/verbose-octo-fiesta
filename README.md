# SceneBox · 地基 v0.1

> Scene 的配置分发层：打包、校验、快照、分享。不内置调度引擎，不碰游戏进程。
> 当前状态：**引擎已在本机跑通全链路**（读取 Scene 8.1.5 → 生成 .sbox → 预览 → 校验 → 快照保存/恢复），
> APK 尚未开始（本机缺 JDK/Android SDK，见 §5）。

## 一、先纠正三个假设（实测，不是推测）

跑在 Redmi 25053RT47C / SM8735 / Android 16 / Scene 8.1.5 上，逐条核对：

| 你原本的假设 | 实测结果 |
|---|---|
| "读取 Scene 的配置文件" | ✅ 成立，但要分清楚读的是哪个文件 |
| "Shizuku 就能读写 Scene 配置" | ❌ 不成立，见 §2 |
| "跨机型导入会变砖" | ⚠️ 说重了，真实伤害是「静默失真 + 过热」，见 §2.3 |

### 1.1 Scene 的真实配置结构

全部在 `/data/data/com.omarea.vtools/`，**没有对外导出目录**（`/sdcard/Scene` 不存在，
`Android/data` 下也没有 Scene 的东西）。所以"分享"这件事今天只能靠截图——痛点是真的。

| 文件 | 内容 | 是否核心 |
|---|---|---|
| `files/profile.json` | **调度方案本体**：alias 路径表 + presets 写入组 + schemes 四档三阶段 | ★ 核心 |
| `shared_prefs/powercfg.xml` | 包名/Activity → powersave/balance/performance/fast/igoned | ★ 核心 |
| `shared_prefs/games.xml` | 哪些包被识别为游戏 | 一般 |
| `files/REFRESH_RATE.CONF` | 每应用帧率覆盖 | ★ 核心 |
| `databases/scene_config3` | 每应用 alone_light/freeze/方向 | 次要 |
| `files/miui-thermal/` | 15 套温控预设（cool 等，实体是几十个 conf） | 大件，默认不打包 |
| `files/kr-script/` | 内核脚本体系（含 thermal_conf3） | 后续 |

本机 profile.json 展开后是 **219 条 sysfs 写入 / 20 个节点 / 24 组预设 / 4 档 × 3 阶段**。
这就是玩家在酷安上互相截图抄的那份东西。

## 二、权限真相（这条直接影响产品定位）

### 2.1 读 Scene 配置需要 root

`/data/data/com.omarea.vtools` 是 app-private。Shizuku = shell(uid 2000)，
SELinux `app_data_file` + 目录属主 `u0_a436` 双重拦截。
本机实测 `su 2000 -c 'cat .../profile.json'` **能读**，但看 SELinux 上下文是
`u:r:ksu:s0`——那是 KernelSU 的 su 给的域，**不是 shell 域**，不能当作"纯 Shizuku 可行"的证据。

### 2.2 写调度必须 root

实测节点属主（shizuku 的 shell 用户写不了）：

| 节点 | 权限 | 谁能写 |
|---|---|---|
| `/proc/sys/walt/sched_boost` | `-rw-r--r-- root:root` | 只有 root |
| `/sys/module/perfmgr/parameters/perfmgr_enable` | `-rw-rw-r-- root:root` | 只有 root |
| `/sys/module/migt/parameters/glk_disable` | `-rw-rw-r-- root:root` | 只有 root |
| `/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq` | `-rw-rw-r-- system:system` | system/root（shell 不在 system 组） |

**结论：SceneBox 的核心写操作无法用纯 Shizuku 实现。** 这和你"纯 Shizuku 降低门槛"的
路线冲突，必须做能力分层，否则上线就会被差评"点了没反应"：

| 能力 | 无权限 | Shizuku | root |
|---|---|---|---|
| 浏览/搜索/分享配置包 | ✅ | ✅ | ✅ |
| 预览、风险分析 | ✅ | ✅ | ✅ |
| **本机适配校验**（读频点表 0444、dumpsys） | ✅ | ✅ | ✅ |
| 帧率/温度记录对比 | ⚠️ 需投屏 | ✅ | ✅ |
| 导出本机 Scene 配置 | ❌ | ❌ | ✅ |
| 导入 / 快照恢复 | ❌ | ❌ | ✅ |

建议：**明说"导出/导入/快照需要 root；不 root 也能用校验和社区"**。
这比含糊承诺"纯 Shizuku 全功能"更抗投诉，也正好把无 root 用户导向"先看配置合不合适"。

### 2.3 真实的跨机风险不是"变砖"

本机导入**自己导出的**方案，校验器仍然报：

- 3 处频率高于本机上限（3283200 / 3513600 / 3801600）→ 被内核钳制
- 29 处频率不在本机频点表 → 被就近钳制

原因：Scene 云端下发的是 `platform: sun` 的 **8GEN4 通用方案**，而 SM8735 的频点表并不一样。
也就是说**同平台换机型就会静默失真**，用户以为抄到手了，其实一半没生效。
这才是产品真正的价值点：不是"防变砖"，是**让"配置到底生没生效"可见**。

## 三、现在能跑什么

```bash
cd /sdcard/DK/scenebox/engine
su -c "python3 cli.py doctor"                     # 环境自检 + 能力探测
su -c "python3 cli.py export --name '三角洲 均衡' --game 三角洲行动 --style balance"
su -c "python3 cli.py preview out/xxx.sbox --writes"   # 预览 + 展开成 256 条真实写入
su -c "python3 cli.py validate out/xxx.sbox"      # pass / confirm / block
su -c "python3 cli.py snapshot save daily --note 日常"
su -c "python3 cli.py snapshot restore daily"
su -c "python3 cli.py snapshot diff A B"
```

实测输出（本机）：

```
=== 导入校验：⚠️ 需要确认 ===
🟡 [FREQ_ABOVE] 3 处频率高于本机上限（3206400）：3283200, 3513600, 3801600
🟡 [FREQ_UNKNOWN] 29 处频率不在本机频点表中：1017600, 1152000, 1555200, 1958400
⚠️  [AGGRESSIVE] 该方案含 3 项高风险写入（关闭限制器/改温控）
结论：需用户二次确认后导入
```

伪造一个 `platform: taro` 的包 → `⛔ SOC_MISMATCH → 禁止导入`（exit 3）。
快照恢复实测：改坏 `scaling_max_freq` 后 `restore daily`，47/47 写回，回读 2016000 复原。

## 四、目录

```
scenebox/
  engine/          Python 引擎（原型，也是 App 端要移植的逻辑规格）
    shell.py       特权通道抽象（root 直连 / su 回落，App 里换成 Shizuku/rish）
    sceneread.py   Scene 数据读取 + 本机指纹
    analyze.py     profile.json 展开 / 风险分析 / 导入校验
    sboxfile.py    打包、读写、预览渲染
    snapshot.py    快照 A/B、批量读、批量写、回读校验
    cli.py         命令行入口
  spec/
    sbox-v1.md     .sbox 格式规范（含校验规则表）
    sbox-v1.schema.json
  docs/
    permissions.md 权限分级 T0/T1/T2/T3：能力矩阵 + 实测证据 + 功能降级
  android/         Kotlin 工程（Compose + Shizuku 通道 + 能力探测 + 校验器）
  out/             导出的 .sbox
  samples/         测试用样本
  data/snapshots/  快照
```

## 四点二、Android 端已实现并真机验证的功能

| 功能 | 状态 | 真机证据 |
|---|---|---|
| 权限分层探测（T0~T3） | ✅ | 一键检测权限，自动区分无特权/Shizuku/root/模块级 |
| **配置导出 .sbox** | ✅ | App 导出 25 KB 包，Python 引擎解析通过，**checksum 匹配** |
| **导入前预览** | ✅ | 打开包显示 256 条展开、20 节点、4 档位 |
| **强校验** | ✅ | FREQ_ABOVE 3 处、FREQ_UNKNOWN 5 处，与 Python 对同一文件结果一致 |
| **调度快照 A/B** | ✅ | 快照 42 节点；改 `scaling_max_freq` 1785600→1516800 后恢复，值复原 |
| 一键检测权限 | ✅ | 单按钮跑完 su / Shizuku / Scene 可读性 / 写权限 |

实现过程中踩到、且已经修掉的真机坑（都写进了代码注释）：

1. **Shizuku 13.x 的 `newProcess` 是 private** → 反射调用 + 失败降级；
2. **dumpsys 无权限时 stdout 输出 `Permission Denial` 且 exit=0** → 校验输出首行必须是纯数字；
3. **应用 mount namespace 里 `/data/data/com.omarea.vtools` 被整个隐藏**（读到的是 No such file，
   即使有 root）→ 用 `nsenter -t 1 -m` 进宿主命名空间读取；
4. **快照的 glob 不能加引号**，否则 `policy*` 不展开，只抓到 11 个节点（修好后 42 个）；
5. **CI 签名**：AGP 不把 key 放 `~/.android/debug.keystore`，改为 keytool 生成并缓存固定 key，
   现在覆盖安装不再要求卸载（不会丢 root 授权）。

## 四点五、Kotlin 与 Python 引擎的一致性（实测）

用 Python 引擎导出的 `out/*.sbox` 喂给 App 的预览页，两边对同一份输入的结果：

| 指标 | Python 引擎 | Android(Kotlin) |
|---|---|---|
| 展开总数 | 256 | 256 |
| 其中写 sysfs | 219 | 219 |
| 涉及节点 | 20 | 20 |
| 频率超上限 | 3（3283200/3513600/3801600） | 3（同） |
| 频率不在本机频点表 | 5（1017600/1152000/1555200/1958400/1996800） | 5（同） |

说明 `.sbox` 的展开语义和校验规则在两条链路上是等价的，不是"两套实现各说各话"。

## 五、APK：已选 B 方案

工程在 `android/`（Kotlin + Compose + Shizuku 13.1.5），构建方式见 `android/README.md`：
Android Studio 直接打开，或 `gradle assembleDebug`，或推 GitHub 走 `.github/workflows/android.yml`。

本机是**写代码、看数据的地方，不是编译机**：没有 JDK / Android SDK / gradle，
`com.termux` 是空壳（`usr/bin` 0 个文件），AIDE Pro 也没有工程。所以流程是
「本机写代码 + 本机用 Python 引擎验证逻辑 → 你在电脑/CI 出包」。

首启能力探测（T0/T1/T2/T3）已经实现，这是产品地基——层级判断错了，
后面所有功能都会变成"点了没反应"。

## 六、TODO（按优先级）

1. **Shizuku 通道实现**：`rish` 调用替换 `shell.py` 的 `su` 分支，做能力探测（第一次启动跑一遍
   §2 的权限矩阵，自动降级功能而不是点了没反应）。
2. **导入落地**：把 profile.json 写回 Scene 私有目录（需 root），或生成"Scene 可识别的脚本"
   走 Scene 自己的执行通道（更安全，避免我们直接写 sysfs）。
3. **快照自动化**：监听游戏进程前后台切换（UsageStats/无障碍）→ 自动切 A/B，这才是"忘记恢复"的解药。
4. **帧率对比**：`dumpsys SurfaceFlinger --latency` 采样 + 温度，生成导入前后对比图。
5. **社区后端**：先静态 JSON（GitHub raw / OSS），按 soc+device 分目录，客户端本地索引 + 校验和。
6. **温控组件**：`miui-thermal` 整套是几十个 conf，要单独设计"温控包"子格式，别塞进 .sbox 主体。
