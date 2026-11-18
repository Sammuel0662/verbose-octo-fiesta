# SceneBox Android 工程（v0.1 骨架）

> 目标：能编、能装、能跑起来看到真实的能力分级。
> 当前只做了「首启能力探测 + 本机指纹 + .sbox 解析/校验」的骨架，
> 导出/导入/快照按 T2 能力逐个补。

## 1. 环境

| 项 | 版本 |
|---|---|
| JDK | 17 |
| Gradle | 8.9（`gradle/wrapper/gradle-wrapper.properties` 已指定；仓库未带 wrapper jar） |
| AGP | 8.7.3 |
| Kotlin | 2.0.21（Compose Compiler 插件同版本） |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| Compose BOM | 2024.10.01 |
| Shizuku API | 13.1.5 |

## 2. 三种构建方式

**A. Android Studio**（推荐）
```
File → Open → 选择 scenebox/android 目录
```
Android Studio 会自动下载 Gradle 与 SDK（首次会提示接受 SDK 许可）。

**B. 命令行**（需要本机已装 JDK 17 + Android SDK）
```bash
cd scenebox/android
# 仓库没带 gradle-wrapper.jar，先补一个（只需一次）
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

**C. GitHub Actions**（不装任何东西）
推仓库即可，`.github/workflows/android.yml` 会产出 `scenebox-debug-apk` artifact。

## 3. 现在打开 App 会看到什么

1. 启动即跑能力探测 → 显示 `T0 / T1 / T2 / T3` 之一，并列出逐项证据
   （能读频点表、能读 sched_boost、能否写 scaling_max_freq、能否读 Scene 私有配置、dumpsys 是否可用）；
2. 「功能可用性」按探测结果置灰：不满足的按钮直接标"不可用"，不会让用户点了没反应；
3. 「申请 Shizuku」走 Shizuku 授权；「检测 root」会真正执行一次 `su -c id -u`（Magisk/KSU 会弹授权框）。

判定逻辑见 `core/Capability.kt`，与 `docs/permissions.md` 的矩阵一一对应。

## 4. 代码地图

```
app/src/main/java/app/scenebox/
  MainActivity.kt            入口 + 状态编排
  ui/HomeScreen.kt           首页（能力卡片 / 功能可用性 / 设备信息）
  core/Shell.kt              三层通道 AppShell / ShizukuShell / SuShell
  core/Capability.kt         Tier 定义 + 能力探测（对应 permissions.md §1）
  core/DeviceFingerprint.kt  机型/芯片/频点表（全部公开可读，T0 也能采）
  sbox/SboxModels.kt         .sbox v1 数据模型 + 宽松解析
  sbox/SboxValidator.kt      导入前强校验（平台/机型/频点/风险）
```

## 5. 已知缺口（下一步）

- [ ] **频率校验要精确到 policy**：现在是把 profile.json 里所有数字扫一遍去重比对，
      误报率高于 Python 引擎（`engine/analyze.py` 里有完整 `@preset`/`@values` 递归展开），
      需要移植 `expandWrites()`。
- [ ] **checksum 本地校验**：Kotlin 侧要复刻 canonical JSON（sort_keys + 紧凑分隔符），
      现在只提示"有校验和"。
- [ ] 导出/导入：T2 下读写 `/data/data/com.omarea.vtools`（需要 `su` 通道，Shizuku 不够）。
- [ ] 快照：`sysfs` 批量读写的 Kotlin 实现（Python 引擎里已验证 47/47 写回）。
- [ ] SAF 导入 `.sbox`（`ACTION_OPEN_DOCUMENT`）+ 预览页 + 一键应用。
- [ ] 帧率采样：`dumpsys SurfaceFlinger --latency` 解析 + 温度曲线。

## 6. 发布前必须改的文案

见 `docs/permissions.md` §4：不要写"纯 Shizuku 全功能"，不要写"不需要解锁 BL"。
