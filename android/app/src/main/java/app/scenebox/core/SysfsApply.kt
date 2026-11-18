package app.scenebox.core

/**
 * 直接写 sysfs 的兜底通道。
 *
 * 用在 Scene 自动调度关闭时：这时候没有别的进程在写这些节点
 * （实测手工写入 10 秒以上未被改回），所以写了就是生效的。
 * 一旦用户把 Scene 的自动调度打开，就该改用 profile 补丁，否则会被 Scene 覆盖。
 */
object SysfsApply {

    data class Result(val ok: Boolean, val message: String)

    private fun largestAtMost(avail: List<Long>, target: Long): Long? =
        avail.filter { it <= target }.maxOrNull() ?: avail.minOrNull()

    private fun smallestAtLeast(avail: List<Long>, target: Long): Long? =
        avail.filter { it >= target }.minOrNull() ?: avail.maxOrNull()

    fun apply(patch: List<Pair<String, String>>, fingerprint: DeviceFingerprint): Result {
        if (patch.isEmpty()) return Result(false, "补丁为空")
        val policies = fingerprint.policies
        if (policies.isEmpty()) return Result(false, "读不到频点表")

        val ops = mutableListOf<String>()
        val detail = mutableListOf<String>()
        var governor: String? = null
        var limitsOff = false

        for ((key, value) in patch) {
            when {
                key == "governor" -> governor = value
                key == "limits" -> limitsOff = value == "off"
                key == "sched_boost" ->
                    ops += "if [ -e /proc/sys/walt/sched_boost ]; then " +
                        "chmod 0664 /proc/sys/walt/sched_boost 2>/dev/null; " +
                        "echo $value > /proc/sys/walt/sched_boost 2>/dev/null; fi"
                key.startsWith("cpu_max_pct_p") -> {
                    val index = key.removePrefix("cpu_max_pct_p").toIntOrNull() ?: continue
                    val policy = policies.getOrNull(index) ?: continue
                    val pct = value.toIntOrNull() ?: continue
                    val freq = largestAtMost(policy.avail, policy.max * pct / 100) ?: continue
                    ops += write("/sys/devices/system/cpu/cpufreq/${policy.name}/scaling_max_freq", freq)
                    detail += "${policy.name}=$freq"
                }
                key.startsWith("cpu_min_pct_p") -> {
                    val index = key.removePrefix("cpu_min_pct_p").toIntOrNull() ?: continue
                    val policy = policies.getOrNull(index) ?: continue
                    val pct = value.toIntOrNull() ?: continue
                    val freq = smallestAtLeast(policy.avail, policy.max * pct / 100) ?: continue
                    ops += write("/sys/devices/system/cpu/cpufreq/${policy.name}/scaling_min_freq", freq)
                }
            }
        }

        if (governor != null) {
            for (policy in policies) {
                ops += write("/sys/devices/system/cpu/cpufreq/${policy.name}/scaling_governor", governor)
            }
            detail += "governor=$governor"
        }
        if (limitsOff) {
            ops += write("/sys/module/perfmgr/parameters/perfmgr_enable", "0")
            ops += write("/sys/module/migt/parameters/glk_disable", "1")
            ops += write("/proc/game_opt/disable_cpufreq_limit", "1")
            detail += "限制器=off"
        }

        if (ops.isEmpty()) return Result(false, "没有可执行项")
        SuShell.exec(ops.joinToString("; "), 30L)

        // 回读：只报上限，用户最关心这个
        val read = SuShell.exec(
            policies.joinToString("; ") { policy ->
                "echo \"${policy.name} \$(cat /sys/devices/system/cpu/cpufreq/${policy.name}/scaling_max_freq 2>/dev/null)\""
            },
            15L,
        ).text()

        return Result(
            ok = true,
            message = "已直接写入内核：${detail.take(5).joinToString("、")}\n当前上限：${read.replace("\n", "  ")}",
        )
    }

    private fun write(path: String, value: Long): String = write(path, value.toString())

    private fun write(path: String, value: String): String =
        "if [ -e $path ]; then chmod 0664 $path 2>/dev/null; echo $value > $path 2>/dev/null; fi"
}
