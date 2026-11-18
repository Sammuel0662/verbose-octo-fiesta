package app.scenebox.core

/**
 * 还原系统配置：把 CPU 频率上下限和各调度开关写回**系统默认值**。
 *
 * 和另外三个动作的区别：
 *  - 撤销导入：还原 Scene 的 profile.json（配置层）
 *  - 快照恢复：写回某次保存的 sysfs 快照（任意状态）
 *  - 还原系统配置：写回内核默认值（不管之前是什么状态）
 *
 * 默认值来源：cpuinfo_min_freq / cpuinfo_max_freq 和几个已知开关的出厂值。
 */
object SystemReset {

    data class Result(val ok: Boolean, val message: String)

    private val tunables = linkedMapOf(
        "/sys/module/perfmgr/parameters/perfmgr_enable" to "1",
        "/sys/module/migt/parameters/glk_disable" to "0",
        "/sys/module/migt/parameters/glk_freq_limit_walt" to "1",
        "/proc/game_opt/disable_cpufreq_limit" to "0",
        "/proc/sys/walt/sched_boost" to "0",
        "/proc/sys/kernel/sched_util_clamp_min" to "1024",
        "/proc/sys/kernel/sched_util_clamp_max" to "1024",
    )

    fun apply(fingerprint: DeviceFingerprint): Result {
        if (fingerprint.policies.isEmpty()) {
            return Result(false, "读不到 CPU 频点表，无法还原")
        }

        val sb = StringBuilder()
        for (policy in fingerprint.policies) {
            val dir = "/sys/devices/system/cpu/cpufreq/${policy.name}"
            sb.append("chmod 0664 $dir/scaling_min_freq 2>/dev/null; ")
            sb.append("echo ${policy.min} > $dir/scaling_min_freq 2>/dev/null; ")
            sb.append("chmod 0664 $dir/scaling_max_freq 2>/dev/null; ")
            sb.append("echo ${policy.max} > $dir/scaling_max_freq 2>/dev/null; ")
        }
        for ((path, value) in tunables) {
            sb.append("if [ -e $path ]; then chmod 0664 $path 2>/dev/null; ")
            sb.append("echo $value > $path 2>/dev/null; fi; ")
        }
        // 回读校验
        for (policy in fingerprint.policies) {
            val dir = "/sys/devices/system/cpu/cpufreq/${policy.name}"
            sb.append("echo \"MAX ${policy.name}=\$(cat $dir/scaling_max_freq 2>/dev/null)\"; ")
        }

        val result = SuShell.exec(sb.toString(), 30L)
        if (!result.has()) {
            return Result(false, "执行失败：请确认 root 授权")
        }

        var okCount = 0
        for (line in result.text().lineSequence()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("MAX ")) continue
            val payload = trimmed.removePrefix("MAX ")
            val name = payload.substringBefore('=')
            val value = payload.substringAfter('=', "").toLongOrNull() ?: continue
            val policy = fingerprint.policies.firstOrNull { it.name == name } ?: continue
            if (value == policy.max) okCount++
        }

        return Result(
            ok = true,
            message = "已还原系统默认：${fingerprint.policies.size} 组 CPU 频率 + ${tunables.size} 个开关\n" +
                "回读确认 $okCount/${fingerprint.policies.size} 组频率已生效",
        )
    }
}
