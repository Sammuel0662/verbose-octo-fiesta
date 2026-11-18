package app.scenebox.core

import android.content.Context

/**
 * 权限层级。等级越高包含越低层的能力。
 */
enum class Tier(val label: String, val hint: String, val rank: Int) {
    NONE("T0 · 无特权", "只能读公开系统信息，可做校验/预览/社区", 0),
    SHIZUKU("T1 · Shizuku", "shell 权限：能读内核调参和帧率，但写不了调度节点", 1),
    ROOT("T2 · root", "可读写调度节点与 Scene 配置，导出/导入/快照全可用", 2),
    MODULE("T3 · 模块级 root", "额外可持久化（开机脚本 / overlay / chattr 锁节点）", 3)
}

/**
 * 首启能力探测的结果。UI 必须按它禁用按钮，而不是让用户点了才发现没反应。
 */
data class Capability(
    val tier: Tier,
    val channel: String,
    val sceneInstalled: Boolean,
    val shizukuInstalled: Boolean,
    val shizukuGranted: Boolean,
    val rootAvailable: Boolean,
    val canReadSysfs: Boolean,
    val canReadKernelTunables: Boolean,
    val canWriteSysfs: Boolean,
    val canReadSceneConfig: Boolean,
    val canDumpsys: Boolean,
    val moduleRoot: String,
    val evidence: List<String>,
) {
    val canValidate: Boolean get() = true
    val canExportScene: Boolean get() = canReadSceneConfig
    val canApplyConfig: Boolean get() = canWriteSysfs && canReadSceneConfig
    val canSnapshotRestore: Boolean get() = canWriteSysfs
    val canRecordFps: Boolean get() = canDumpsys
}

object CapabilityProbe {
    private const val CPU = "/sys/devices/system/cpu/cpufreq"
    private const val SCENE_PKG = "com.omarea.vtools"
    private const val SCENE_PROFILE = "/data/data/$SCENE_PKG/files/profile.json"
    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    fun probe(context: Context, tryRoot: Boolean): Capability {
        val evidence = mutableListOf<String>()

        // 选通道：能 root 就用 root，其次 Shizuku，最后裸跑。
        // 超时必须给足：KernelSU/Magisk 首次授权会弹窗，用户还要看清再点"允许"，
        // 6 秒根本不够，之前就是这里把已授权的用户判成了没授权。
        val suResult = if (tryRoot) SuShell.exec("id -u", 20L) else null
        val root = suResult?.text() == "0"
        val granted = ShizukuBridge.granted()
        val channel: ShellChannel = when {
            root -> SuShell
            granted -> ShizukuShell()
            else -> AppShell
        }
        evidence += "通道：${channel.name}"
        if (suResult != null) {
            evidence += when {
                root -> "su 探测成功（uid 0）"
                // 实测：KernelSU 未授权时 su 会"正常退出但什么也不做"，
                // 输出为空、exit=0，不会报错也不会弹窗。
                suResult.code == 0 && !suResult.has() ->
                    "su 无输出：未授权。请到 KernelSU/Magisk 管理器里勾选 SceneBox"
                suResult.code == -1 -> "su 执行失败或超时（20s）"
                else -> "su 返回异常结果：${suResult.text().take(40)}"
            }
        }

        // 1) 读频点表（T0 就能读，0444）
        val freq = channel.exec("cat $CPU/policy0/scaling_available_frequencies")
        val canReadSysfs = freq.code == 0 && freq.text().isNotEmpty()
        if (canReadSysfs) {
            val n = freq.text().split(' ', '\n').count { it.isNotBlank() }
            evidence += "读频点表 OK：policy0 共 $n 个频点"
        } else {
            evidence += "读频点表失败"
        }

        // 2) 读内核调参节点（T1 起可读）
        val kernel = channel.exec("cat /proc/sys/walt/sched_boost")
        val canReadKernel = kernel.code == 0 && kernel.text().isNotEmpty()
        if (canReadKernel) evidence += "读 sched_boost = ${kernel.text()}"

        // 3) 写 sysfs（T2 才可能；test -w 不会真的改值）
        val writable = channel.exec("test -w $CPU/policy0/scaling_max_freq && echo Y")
        val canWriteSysfs = writable.yes()
        evidence += if (canWriteSysfs) "可写 scaling_max_freq" else "不可写 scaling_max_freq"

        // 4) 读 Scene 私有配置（T2 才能；Shizuku 也进不去）
        val scene = channel.execHost("test -r $SCENE_PROFILE && echo Y")
        val canReadScene = scene.yes()
        evidence += if (canReadScene) "可读 Scene profile.json" else "不可读 Scene 私有配置"

        // 5) dumpsys 取帧率（T1 起可用）
        //    坑：无权限时 dumpsys 会把 "Permission Denial: ..." 打到 stdout 且 exit=0，
        //    只看"有没有输出"会假阳性。--latency 的第一行必须是纯数字（刷新周期 ns）。
        val dump = channel.exec("dumpsys SurfaceFlinger --latency 2>&1 | head -n 1")
        val dumpLine = dump.text().lineSequence().firstOrNull()?.trim() ?: ""
        val canDumpsys = dump.code == 0 && dumpLine.isNotEmpty() && dumpLine.all { it.isDigit() }
        if (canDumpsys) {
            evidence += "dumpsys SurfaceFlinger 可用"
        } else if (dumpLine.isNotEmpty()) {
            evidence += "dumpsys 被拒：${dumpLine.take(38)}"
        }

        // 6) 是否模块级 root（Magisk / KernelSU / APatch）
        val modules = channel.exec("ls -d /data/adb/magisk /data/adb/ksu /data/adb/ap 2>/dev/null")
        val moduleRoot = if (modules.code == 0) {
            modules.text().lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        } else {
            ""
        }
        if (moduleRoot.isNotEmpty()) evidence += "检测到 root 管理器：$moduleRoot"

        val tier = when {
            canWriteSysfs && canReadScene -> if (moduleRoot.isNotEmpty()) Tier.MODULE else Tier.ROOT
            canReadKernel || canDumpsys -> Tier.SHIZUKU
            else -> Tier.NONE
        }

        return Capability(
            tier = tier,
            channel = channel.name,
            sceneInstalled = isInstalled(context, SCENE_PKG),
            shizukuInstalled = isInstalled(context, SHIZUKU_PKG),
            shizukuGranted = granted,
            rootAvailable = root,
            canReadSysfs = canReadSysfs,
            canReadKernelTunables = canReadKernel,
            canWriteSysfs = canWriteSysfs,
            canReadSceneConfig = canReadScene,
            canDumpsys = canDumpsys,
            moduleRoot = moduleRoot,
            evidence = evidence,
        )
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (t: Throwable) {
        false
    }
}
