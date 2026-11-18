package app.scenebox.core

/**
 * 广场方案的「参数补丁」执行器。
 *
 * 设计要点：**不碰 Scene 的 profile.json**。
 * 补丁只描述意图（governor、频率上限百分比、开关），由这里按本机频点表
 * 换算成具体频率值再写 sysfs，所以跨机型天然适配，也不会把别人的绝对频率写进来。
 */
object PatchService {

    data class Result(val ok: Boolean, val message: String)

    private const val SCHED_BOOST = "/proc/sys/walt/sched_boost"
    private const val PERFMGR = "/sys/module/perfmgr/parameters/perfmgr_enable"
    private const val GLK = "/sys/module/migt/parameters/glk_disable"
    private const val CPUFREQ_LIMIT = "/proc/game_opt/disable_cpufreq_limit"

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

        for ((key, value) in patch) {
            when {
                key == "governor" -> {
                    for (policy in policies) {
                        ops += "chmod 0664 /sys/devices/system/cpu/cpufreq/${policy.name}/scaling_governor 2>/dev/null; " +
                            "echo $value > /sys/devices/system/cpu/cpufreq/${policy.name}/scaling_governor 2>/dev/null"
                    }
                    detail += "governor=$value"
                }

                key == "sched_boost" -> {
                    ops += "if [ -e $SCHED_BOOST ]; then chmod 0664 $SCHED_BOOST 2>/dev/null; echo $value > $SCHED_BOOST 2>/dev/null; fi"
                    detail += "sched_boost=$value"
                }

                key == "limits" -> {
                    val off = value == "off"
                    // 显式写值，避免在字符串模板里做三元表达式
                    ops += "if [ -e $PERFMGR ]; then chmod 0664 $PERFMGR 2>/dev/null; echo ${if (off) "0" else "1"} > $PERFMGR 2>/dev/null; fi"
                    ops += "if [ -e $GLK ]; then chmod 0664 $GLK 2>/dev/null; echo ${if (off) "1" else "0"} > $GLK 2>/dev/null; fi"
                    ops += "if [ -e $CPUFREQ_LIMIT ]; then chmod 0664 $CPUFREQ_LIMIT 2>/dev/null; echo ${if (off) "1" else "0"} > $CPUFREQ_LIMIT 2>/dev/null; fi"
                    detail += "限制器=${if (off) "关闭" else "开启"}"
                }

                key.startsWith("cpu_max_pct_p") -> {
                    val index = key.removePrefix("cpu_max_pct_p").toIntOrNull() ?: continue
                    val policy = policies.getOrNull(index) ?: continue
                    val pct = value.toIntOrNull() ?: continue
                    val target = policy.max * pct / 100
                    val freq = largestAtMost(policy.avail, target) ?: continue
                    ops += "chmod 0664 /sys/devices/system/cpu/cpufreq/${policy.name}/scaling_max_freq 2>/dev/null; " +
                        "echo $freq > /sys/devices/system/cpu/cpufreq/${policy.name}/scaling_max_freq 2>/dev/null"
                    detail += "${policy.name}上限=$freq(${pct}%)"
                }

                key.startsWith("cpu_min_pct_p") -> {
                    val index = key.removePrefix("cpu_min_pct_p").toIntOrNull() ?: continue
                    val policy = policies.getOrNull(index) ?: continue
                    val pct = value.toIntOrNull() ?: continue
                    val target = policy.max * pct / 100
                    val freq = smallestAtLeast(policy.avail, target) ?: continue
                    ops += "chmod 0664 /sys/devices/system/cpu/cpufreq/${policy.name}/scaling_min_freq 2>/dev/null; " +
                        "echo $freq > /sys/devices/system/cpu/cpufreq/${policy.name}/scaling_min_freq 2>/dev/null"
                    detail += "${policy.name}下限=$freq(${pct}%)"
                }
            }
        }

        if (ops.isEmpty()) return Result(false, "没有可执行的补丁项")

        val result = SuShell.exec(ops.joinToString("; "), 30L)
        if (result.code != 0 && !result.has()) {
            return Result(false, "写入失败：确认 root 授权")
        }

        // 回读校验
        val read = SuShell.exec(
            policies.joinToString("; ") {
                "echo \"${it.name}=\$(cat /sys/devices/system/cpu/cpufreq/${it.name}/scaling_max_freq 2>/dev/null)\""
            },
            15L,
        )
        val applied = read.text().lineSequence()
            .mapNotNull { line ->
                val name = line.substringBefore('=')
                val value = line.substringAfter('=', "").trim().toLongOrNull()
                if (name.isBlank() || value == null) null else name to value
            }
            .toMap()

        val summary = detail.take(4).joinToString("、")
        val check = policies.joinToString("，") { p ->
            "${p.name}=${applied[p.name] ?: "?"}"
        }
        return Result(
            ok = true,
            message = "已应用：$summary\n回读：$check\n可用「还原系统配置」一键回退",
        )
    }
}
