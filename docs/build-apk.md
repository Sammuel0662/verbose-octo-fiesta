# 出包完整教程（从手机上的代码 → 能装的 APK）

> 适用对象：手里只有 `scenebox/` 这份代码，想拿到 `app-debug.apk`。
> 三条路，按你的条件选一条就行，不用全看。

| 你的情况 | 走哪条 | 耗时 | 成功率 |
|---|---|---|---|
| 有 Windows/Mac 电脑 | **路线 A：Android Studio** | 40~70 分钟（首次下载多） | 最高 |
| 有电脑但不想装 IDE，或想自动化 | 路线 B：命令行 | 30~50 分钟 | 高 |
| 只有手机 / 想让服务器编 | **路线 C：GitHub Actions** | 15~30 分钟 | 中（取决于 GitHub 能不能上） |
| 只有手机且上不了 GitHub | 路线 D：Termux 本机 | 1~2 小时 | 低，不推荐 |

**构建产物只有一个东西**：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 第 0 步：把代码弄到电脑（路线 A/B 必做）

代码现在在手机上：`/storage/emulated/0/DK/scenebox/`。

三种搬运方式，任选：

1. **数据线 + 文件管理**：手机连电脑，选「传输文件」，进 `内部存储/DK/`，把 `scenebox` 整个文件夹拷到电脑。
2. **微信/QQ 传文件**：直接发 `scenebox-src.zip` 给自己（已经在手机根目录 `DK/` 下打好了，
   65 KB，解压出来就是一个 `scenebox/` 文件夹）。注：微信会拦 `.apk`，但 `.zip` 不会被拦。
3. **adb pull**（电脑已装 adb）：
   ```bash
   adb pull /storage/emulated/0/DK/scenebox ./scenebox
   ```

> ⚠️ 别把 `scenebox/data/` 一起带上——里面是你这台机器的 sysfs 快照，没意义还占地方。
> 我打的 zip 已经排除了它。

---

## 路线 A：Android Studio（推荐）

### A1. 装 Android Studio

- 下载：<https://developer.android.com/studio>（国内可搜「Android Studio 下载 镜像」）
- 版本要求：**Ladybug 2024.2.1 或更新**（因为工程用的是 AGP 8.7.3）。
  版本太老会在同步时直接报 `The project is using an incompatible version of AGP`。
- 安装时勾选「Android Virtual Device」可以不要，其它默认。
- Android Studio 自带 JDK，**不用单独装 Java**。

### A2. 打开工程

`File → Open` → 选到 **`scenebox/android`** 这一层（不是 `scenebox`，也不是 `android/app`）。

打开后右下角会开始 **Gradle Sync**，第一次会下载：

| 下载内容 | 大小 |
|---|---|
| Gradle 8.9 发行包 | ~130 MB |
| Android SDK Platform 35 + Build-Tools | ~300 MB |
| AGP / Kotlin / Compose / Shizuku 依赖 | ~400 MB |

总计 **1 GB 上下**，网慢的话这一步最花时间。

### A3. 首次同步可能卡住的三个地方

**① 卡在 `Downloading gradle-8.9-bin.zip`**
换镜像：编辑 `android/gradle/wrapper/gradle-wrapper.properties`，把
`distributionUrl` 改成腾讯镜像：

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip
```

**② 卡在依赖解析（一直转圈）**
给 `android/settings.gradle.kts` 的两个 `repositories` 块**最前面**加上阿里云镜像：

```kotlin
pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
}
```

**③ 报 `SDK location not found`**
在 `android/` 下新建 `local.properties`（这个文件不要提交）：

```properties
# Windows
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
# macOS
# sdk.dir=/Users/你的用户名/Library/Android/sdk
```

### A4. 构建

菜单 `Build → Build Bundle(s) / APK(s) → Build APK(s)`。

等右下角弹出「APK(s) generated successfully」，点 **locate** 直接跳到产物目录。

### A5. 装到手机

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或者把 `app-debug.apk` 传到手机，点开安装（需要允许「安装未知来源应用」）。

---

## 路线 B：纯命令行（不装 IDE）

### B1. 装 JDK 17

- Windows：<https://adoptium.net/> 下 `.msi`，装完 `java -version` 应显示 17.x
- macOS：`brew install --cask temurin@17`
- Linux：`sudo apt install openjdk-17-jdk`

### B2. 装 Android 命令行工具

1. 下载 commandline-tools：<https://developer.android.com/studio#command-tools>
   （选 `commandlinetools-*-latest.zip`）
2. 解压到 `~/android-sdk/cmdline-tools/latest/`（注意目录层级必须是 `cmdline-tools/latest/bin/sdkmanager`）
3. 装需要的包：

```bash
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

sdkmanager --licenses            # 一路 y
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

### B3. 指定 SDK 路径

```bash
cd scenebox/android
echo "sdk.dir=$HOME/android-sdk" > local.properties
```

### B4. 构建

工程**没带 gradle-wrapper.jar**（我这边没有 gradle 环境生成）。二选一：

```bash
# 方式 1：本机已装 gradle（8.9+）
gradle wrapper --gradle-version 8.9   # 只跑一次，生成 gradlew 和 wrapper jar
./gradlew assembleDebug

# 方式 2：直接用 gradle 构建，不要 wrapper
gradle assembleDebug --no-daemon
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

---

## 路线 C：GitHub Actions（零环境，手机也能完成）

适合：没有电脑，或者想把"编包"这件事彻底交给服务器。

### C1. 建仓库

手机浏览器打开 <https://github.com/new>，仓库名随便（如 `scenebox`），
选 **Private**（避免把设备信息传公开），勾不勾 README 都行。

### C2. 上传代码

用 GitHub 网页版「Add file → Upload files」，把 **`scenebox` 文件夹里的内容**传上去
（注意：要传的是 `scenebox/` 里面的 `android/`、`.github/`、`engine/`、`spec/`、`docs/`，
不是把 `scenebox` 这层套进去，否则工作流路径对不上）。

如果用 git：

```bash
cd scenebox
git init && git add . && git commit -m "init"
git remote add origin https://github.com/你的用户名/scenebox.git
git branch -M main && git push -u origin main
```

### C3. 让它自动编

`.github/workflows/android.yml` 已经写好了，push 上去后仓库顶部 **Actions** 标签页
会自动跑 `build-apk`（也可以点 `Run workflow` 手动触发）。

### C4. 下载 APK

跑完后进这次运行记录 → 页面底部 **Artifacts** → 下载 `scenebox-debug-apk.zip`，
解压就是 `app-debug.apk`。

### C5. 国内网络注意

- GitHub 访问不稳时，Actions 页面可能刷不出来，但工作流通常还在跑，过会儿再看；
- 私有仓库的 Actions 每月有免费额度（2000 分钟），一次构建约 3~5 分钟，够用很久；
- 如果 GitHub 完全上不去，改用 Gitee 的「流水线」需要自己改写 yml，我没有验证过。

---

## 路线 D：Termux 本机构建（不推荐）

原理：Termux 里装 `openjdk-17` + `gradle`，再补 aarch64 的 build-tools。
**卡点在 `aapt2`**：Google 官方 build-tools 只有 x86_64，Android 上得用别人移植的版本，
版本一错就是 `aapt2: Exec format error` 或者资源编译失败。

如果你一定要试：

```bash
pkg update && pkg install openjdk-17 gradle git
# 还需要 aapt2 / d8 / apksigner 的 aarch64 版本，来源不统一，这里不给具体链接避免踩坑
```

**建议**：真没电脑就用路线 C，别在 Termux 上耗。

---

## 装好之后：验证清单

打开 App，应该看到：

| 检查项 | 预期（你这台 Redmi 25053RT47C） |
|---|---|
| 权限层级 | **T2 · root**（点过「检测 root」并在 Magisk/KSU 授权后） |
| 首次进入 | T0 或 T1（还没点检测 root 时） |
| 机型 | `Redmi 25053RT47C` |
| 芯片 | `sun SM8735` |
| 系统 | `Android 16（SDK 36）` |
| 频点上限 | `3206400` |
| policy 列表 | `policy0 / policy2 / policy5 / policy7` 四组 |
| 功能可用性 | 「本机适配校验」「预览配置参数」= 可用；导出/导入在 T2 下也应可用 |
| 证据区 | 应出现 `读频点表 OK`、`可写 scaling_max_freq`、`可读 Scene profile.json` |

如果「机型/芯片」显示为空，说明 `Build` 字段被 ROM 改过，不影响核心功能；
如果「可读 Scene profile.json」是"不可读"但你确实是 root，检查 Magisk 是否给 SceneBox 授了 root。

---

## 报错速查表

| 报错 | 原因 | 解决 |
|---|---|---|
| `The project is using an incompatible version of AGP` | Android Studio 太老 | 升级 AS 到 Ladybug+；或把根目录 AGP 降到 `8.5.2`、`gradle-wrapper` 降到 `8.7` |
| `SDK location not found` | 没 `local.properties` | 见 A3-③ |
| `Failed to install the following Android SDK packages ... licenses` | 许可没接受 | `sdkmanager --licenses` 全 y |
| `Could not resolve dev.rikka.shizuku:api:13.1.5` | 镜像没同步这个库 | 保留 `mavenCentral()` 在最后，不要只留阿里云 |
| `Unsupported class file major version 65` | 用 JDK 21 跑了 AGP 8.7 | 切到 JDK 17（`File → Settings → Build → Gradle → Gradle JDK`） |
| `error: resource mipmap/ic_launcher not found` | 图标引用不匹配 | 工程已改用 `@drawable/ic_launcher`，若你自己改过，改回来 |
| `Unresolved reference: Shizuku` | 依赖没下全 | `File → Invalidate Caches → 清空并重启`，再 Sync |
| App 装上但一开就闪退 | 多半是 Shizuku provider 初始化 | 看 logcat：`adb logcat -s AndroidRuntime` |
| 「检测 root」没反应 | Magisk 没弹授权 / `su` 不在 PATH | 先在别的 root App 里确认 `su` 可用；部分 ROM 的 su 路径不是 `/system/bin/su` |

---

## 后续：release 签名（现在不用做）

debug 包用的是自动生成的调试证书，能装能测，但不能上架、也不能覆盖安装正式版。
等要发布时再生成：

```bash
keytool -genkey -v -keystore scenebox.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias scenebox
```

然后在 `android/app/build.gradle.kts` 里加 `signingConfigs`。
**keystore 和密码丢了就永远无法覆盖升级**，务必单独备份。

---

## 一句话总结

有电脑 → 路线 A（装 Android Studio，打开 `android/` 目录，点 Build APK）。
没电脑 → 路线 C（传 GitHub，Actions 编好，下 artifact）。

两条路的产物是同一个 `app-debug.apk`，装上去第一件事是看「权限层级」是不是 T2。

---

## 附：为什么手机上的 IDE（CodeAssist / AIDE）编不了这个工程

手机上能装到的 IDE 基本是「Gradle-less 构建系统」：自己带 `javac`/`aapt2`/`d8`，
手工走一遍 `编译 → 资源合并 → dex → 打包 → 签名`。这条路对**纯 Java + 手写 XML** 的工程有效，
但这个工程会连撞四关：

| 关卡 | 为什么过不去 |
|---|---|
| **1. Compose 编译器插件** | Compose 不是普通库，编译期必须由 Kotlin 编译器插件把 `@Composable` 函数改写成带 `$composer` 参数的调用。没有 Gradle 的编译器插件管线，`kotlinc` 编出来直接跑不了。这是硬门槛，不是配置问题。 |
| **2. kotlinx.serialization** | 同样是**编译器插件** + Gradle 插件生成 `serializer()`。去掉它就得手写 JSON 解析。 |
| **3. AAR 依赖与资源合并** | AndroidX / Shizuku 都是 `.aar`，里面带 `res/`、`AndroidManifest.xml`、`R.txt`，需要 AGP 做资源合并和 `R` 生成。手写构建系统要么不支持，要么碰到 `R` 冲突就崩。 |
| **4. manifest 占位符** | Shizuku provider 的 `android:authorities="${applicationId}.shizuku"` 依赖 AGP 的 manifest 占位符替换，纯 aapt2 打包会原样留下 `${applicationId}`，装上直接崩。 |

所以结论不是「CodeAssist 不好用」，而是**这个工程的技术栈和它不匹配**。
换成 Android Studio / 命令行 / GitHub Actions，这些关卡都由 AGP 处理掉了。

如果哪天必须在手机上编，只有一条可行路线：把工程降级成 **Java + 原生 View + 手写 JSON**，
去掉 Compose 和两个编译器插件。那是为了迁就工具链牺牲架构，**不建议作为主线**。

---

## 已跑通记录（2026-11-18，真机验证）

第一次出包的真实过程，留档避免重复踩：

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| 1 | `refusing to allow a Personal Access Token to create or update workflow` | 经典 PAT 没勾 `workflow` 权限 | 补勾 `workflow`（token 值不变，编辑即可） |
| 2 | CI：`Cannot access 'static fun newProcess(...)': it is private` | Shizuku **13.1.5 把 `newProcess` 改成了 private**，公开 API 只剩 `getBinder()`，而 `ShizukuRemoteProcess` 构造器是包内可见 | `Shell.kt` 改反射调用，失败自动降级到 AppShell |
| 3 | 无特权时也显示「dumpsys 可用」，层级误判成 T1 | **dumpsys 无权限时把 `Permission Denial:` 打到 stdout 且 `exit=0`** | 校验首行必须是纯数字（--latency 的刷新周期），否则算失败 |
| 4 | 第二次装 APK 报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 每次 CI 都是全新 runner，`debug.keystore` 每次重新生成 → 签名不同 | workflow 里缓存 `~/.android/debug.keystore` |
| 5 | 标题被状态栏压住 | `targetSdk 35` 强制 edge-to-edge | Compose 根布局加 `systemBarsPadding()` |

验证结果：APK 9.3 MB，装上后正确显示 `T0 · 无特权 / 通道 app / 频点上限 3206400 / policy0~7 四组`，
未授权时明确列出「dumpsys 被拒：Permission Denial」而不是假装可用。
