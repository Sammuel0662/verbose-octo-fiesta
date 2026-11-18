# 权限分级：Shizuku / root / 模块级 root 各自能干什么

> 全部基于本机实测：Redmi 25053RT47C（SM8735 / sun）/ Android 16 / Scene 8.1.5，
> root 由 KernelSU 提供，Shizuku 已安装并在运行（`shizuku_server`，root 启动）。

## 0. 四层，从下往上

| 层 | 拿到什么 | 怎么来的 | 用户门槛 |
|---|---|---|---|
| **T0 普通 App** | 自己的沙箱 | 装个 APK | 0 |
| **T1 Shizuku** | **uid 2000(shell)**，等同于 `adb shell` | 无线调试/电脑 adb 激活一次，重启后（Android 11+ 无线调试）需重新激活 | 低 |
| **T2 root** | **uid 0**，任意文件/节点读写 | Magisk/KernelSU（一般需要解锁 BL） | 高 |
| **T3 模块级 root** | T2 + 开机脚本 + overlay/bind mount + 可替换 vendor 文件 | Magisk/KernelSU 模块 | 很高 |

注意一个容易混淆的点：**T2 和 T3 通常都意味着已经解锁 BL**。市面上"不解锁 BL 的 root"基本只剩漏洞利用，不可作为产品依赖。
所以你说的"不需要 KernelSU / 解锁 BL"，准确表述应该是：**本产品不依赖 T3，但 T2 是硬门槛**。

## 1. 实测证据（最硬的一条）

把 Scene 方案里 `alias` 直连的 **11 个节点**逐个用 `test -w` 探：

```
uid 2000 (shell / Shizuku)  可写: 0 / 11
uid 0    (root)             可写: 7 / 11   （其余 4 个是只读节点或本机不存在）
```

原因不是玄学，是文件属主：

| 节点 | 属主:权限 | shell 能不能写 |
|---|---|---|
| `/proc/sys/walt/sched_boost` | `root:root 0644` | ❌ |
| `/sys/module/perfmgr/parameters/perfmgr_enable` | `root:root 0664` | ❌（不在 root 组） |
| `/sys/module/migt/parameters/glk_disable` | `root:root 0664` | ❌ |
| `/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq` | `system:system 0664` | ❌（不在 system 组） |
| `/proc/sys/walt/input_boost/sched_boost_on_input` | `root:root` | ❌ |

**uid 2000 既不是 root，也不在 root/system 组 → 调度写入这条路在 Shizuku 层是死的。**
这跟 SELinux 无关，是 DAC 权限，换任何 ROM 都一样。

## 2. 能力矩阵

| 能力 | T0 普通 | T1 Shizuku | T2 root | T3 模块 root |
|---|---|---|---|---|
| 读本机机型/芯片/系统版本 | ✅ | ✅ | ✅ | ✅ |
| 读 CPU 频点表 `scaling_available_frequencies`（0444） | ✅ 实测可读 | ✅ | ✅ | ✅ |
| 读温度 `thermal_zone*/temp` | ✅ 实测可读 | ✅ | ✅ | ✅ |
| 读内核调参节点 `sched_boost` / `perfmgr_enable` | ❌ 实测拒绝 | ✅ 只读 | ✅ 读写 | ✅ |
| `dumpsys SurfaceFlinger --latency` 取帧率 | ❌ | ✅ | ✅ | ✅ |
| `settings get/put` 系统设置 | ❌ | ✅ | ✅ | ✅ |
| 读 Scene 私有配置 `/data/data/com.omarea.vtools` | ❌ | ❌ | ✅ | ✅ |
| 写 sysfs 调度节点 | ❌ | ❌ 实测 0/11 | ✅ 实测 7/11 | ✅ |
| 写 Scene 配置（导入） | ❌ | ❌ | ✅ | ✅ |
| 快照保存 | ❌ | ✅ 仅读 | ✅ | ✅ |
| 快照恢复 | ❌ | ❌ | ✅ | ✅ |
| `chattr +i` 锁死节点防系统回写 | ❌ | ❌ | ✅ | ✅ |
| `mount --bind` 隐藏篡改（Scene 的 `hide_value` 就是这招） | ❌ | ❌ | ⚠️ 部分 ROM 需额外策略 | ✅ |
| 替换 vendor 下的 thermal-engine / 温控配置 | ❌ | ❌ | ⚠️ 只读分区，需 overlay | ✅ |
| 开机自动恢复（不依赖 App 存活） | ❌ | ❌ | ❌ | ✅ |
| App 被杀 / 重启后仍生效 | ❌ | ❌ | ❌ | ✅ |

补充说明两条容易误解的：

- **T1 读 Scene 配置也是不行的**。`/data/data/com.omarea.vtools` 目录属主是 `u0_a436`，
  shell 进不去；SELinux 还有 `app_data_file` 一道。
  我在本机用 `su 2000` 能读到 `profile.json`，但那是 KernelSU 的 `u:r:ksu:s0` 域，
  **不能拿来证明 Shizuku 可行**——这点必须写死在设计里，否则上线就是"点了没反应"。
- **T3 不是功能，是稳定性**。它不给你多一个按钮，它让"改了能留住、重启还在、
  系统回写被摁住"这三件事成立。产品可以不依赖它，但不能假装它不存在。

## 3. 功能 × 分层（App 里应该怎么降级）

| 功能 | T0 | T1 | T2 | 不支持时的行为 |
|---|---|---|---|---|
| 浏览 / 搜索配置包 | ✅ | ✅ | ✅ | — |
| 预览参数（展开写入清单） | ✅ | ✅ | ✅ | — |
| **本机适配校验**（频点/机型/风险） | ✅ | ✅ | ✅ | 这是产品的地基，**无 root 也能用** |
| 帧率 / 温度记录对比 | ⚠️ 只能读温度 | ✅ | ✅ | T0 显示"需要 Shizuku 才能采帧率" |
| 导出本机 Scene 配置 | ❌ | ❌ | ✅ | 灰掉按钮 + "需要 root"，不要假装能点 |
| 导入配置 | ❌ | ❌ | ✅ | 校验照做（可只读预览+导出差异报告） |
| 快照 A/B 保存 | ❌ | ⚠️ 只读快照（有价值的降级：能存不能恢复） | ✅ | — |
| 快照恢复 / 自动切换 | ❌ | ❌ | ✅ | 明说"自动恢复需要 root" |
| 开机自动恢复 | ❌ | ❌ | ❌ | 仅 T3 |

**结论：这是一个"T2 才能闭环、T0/T1 也能用一半"的产品。**
好消息是：T0/T1 能用的那一半（校验 + 预览 + 社区）恰好就是你现在最痛的那部分
——"网上抄的参数到底适不适合我这台机器"。写入反而是低频动作。

## 4. 对产品文案的直接影响

1. 别写"纯 Shizuku 全功能"，改成 **"不 root 也能校验、预览、逛社区；导入/快照/自动恢复需要 root"**。
2. 首启必须有**能力探测**（本文件 §1 的探测方法已写成 `CapabilityProbe`），
   按结果动态禁用按钮，而不是让用户点完才发现没反应。
3. 商店描述里"不需要解锁 BL"要删掉——root 本身就意味着大概率已解锁 BL，
   写这句会引来"那你怎么写 sysfs"的质疑。
4. 真正能打的口号是：**"先判断能不能用，再决定要不要 root。"**
