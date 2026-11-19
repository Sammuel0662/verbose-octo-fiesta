package app.scenebox.core

import android.content.Context

/**
 * 环境自检：把"能不能用、哪里会失败"一次性列清楚。
 * 目的是让用户在看到"点了没反应"之前就知道原因。
 */
object Diagnostics {

    data class Check(val name: String, val ok: Boolean, val detail: String)

    fun run(context: Context, fingerprint: DeviceFingerprint?, capability: Capability?): List<Check> {
        val out = mutableListOf<Check>()

        val suId = SuShell.exec("id -u", 8L).text()
        out += Check(
            "root 权限",
            suId == "0",
            if (suId == "0") "已授权 (uid 0)" else "未授权，导出/导入/快照不可用",
        )

        val shizuku = ShizukuBridge.granted()
        out += Check(
            "Shizuku",
            shizuku,
            if (shizuku) "已授权，可读内核参数" else "未授权（可选，root 已覆盖大部分能力）",
        )

        val sceneDir = SceneFiles.profile(SuShell)
        out += Check(
            "Scene 配置可读",
            sceneDir != null,
            if (sceneDir != null) "profile.json 读取正常" else "读不到，检查 root 与 Scene 是否安装",
        )

        val state = SceneState.detect()
        out += Check(
            "Scene 自动调度",
            state.willApply,
            if (state.willApply) "daemon 在跑且 auto.enable=true（会下发档位）"
            else "未启用（App 会直接写内核，写了即生效）",
        )

        val policies = fingerprint?.policies ?: emptyList()
        if (policies.isEmpty()) {
            out += Check("CPU 频点表", false, "读不到 scaling_available_frequencies")
        } else {
            val gov = SuShell.exec(
                "cat /sys/devices/system/cpu/cpufreq/${policies.first().name}/scaling_available_governors",
                8L,
            ).text()
            val hasSchedutil = gov.contains("schedutil")
            out += Check(
                "调度器支持",
                hasSchedutil,
                if (hasSchedutil) "支持 schedutil（广场方案依赖它）" else "不支持 schedutil，方案会自动跳过该项",
            )
            out += Check(
                "CPU 簇",
                policies.size >= 2,
                "${policies.size} 个 policy：" + policies.joinToString(" ") { it.name },
            )
            val writable = SuShell.exec(
                "test -w /sys/devices/system/cpu/cpufreq/${policies.first().name}/scaling_max_freq && echo Y",
                8L,
            ).text() == "Y"
            out += Check(
                "频率节点可写",
                writable,
                if (writable) "scaling_max_freq 可写" else "不可写，需检查 root 授权",
            )
        }

        val thermalMsg = SuShell.exec("test -d /sys/class/thermal/thermal_message && echo Y", 8L).text() == "Y"
        out += Check(
            "温控节点",
            thermalMsg,
            if (thermalMsg) "存在 thermal_message（本机未改写它）" else "无统一温控节点，温控由厂商 HAL 管理",
        )

        val gpu = SuShell.exec("test -w /sys/class/kgsl/kgsl-3d0/devfreq/max_freq && echo Y", 8L).text() == "Y"
        out += Check(
            "GPU 节点",
            gpu,
            if (gpu) "kgsl devfreq 可写（当前方案未使用）" else "不可写或不适用",
        )

        val freeGb = context.filesDir.usableSpace / 1024.0 / 1024.0 / 1024.0
        out += Check(
            "存储空间",
            freeGb > 0.5,
            String.format("可用 %.1f GB", freeGb),
        )

        return out
    }
}
