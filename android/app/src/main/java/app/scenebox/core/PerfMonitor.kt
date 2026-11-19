package app.scenebox.core

/**
 * 性能监测：一次性采集 CPU / GPU / 内存 / 温度 / 帧率，拼成可直接显示的文本。
 *
 * 数据来源全部是公开 sysfs 与 /proc，root 只是让读取更稳。
 */
object PerfMonitor {

    private const val KGSL = "/sys/class/kgsl/kgsl-3d0"

    fun read(fingerprint: DeviceFingerprint): String {
        val sb = StringBuilder()

        // CPU 各簇频率 + governor
        for (policy in fingerprint.policies) {
            val dir = "/sys/devices/system/cpu/cpufreq/${policy.name}"
            val cur = SuShell.exec("cat $dir/scaling_cur_freq 2>/dev/null", 6L).text().trim()
            val gov = SuShell.exec("cat $dir/scaling_governor 2>/dev/null", 6L).text().trim()
            val curMhz = cur.toLongOrNull()?.div(1000)
            sb.append(policy.name)
            sb.append("  ")
            sb.append(if (curMhz != null) "${curMhz}MHz" else "?")
            sb.append("/")
            sb.append(policy.max / 1000)
            sb.append("MHz  ")
            sb.append(gov)
            sb.append("\n")
        }

        // CPU 总利用率（/proc/stat 两次采样差分）
        val stat = SuShell.exec(
            "A=\$(head -1 /proc/stat); sleep 0.3; B=\$(head -1 /proc/stat); echo \"\$A|\$B\"",
            10L,
        ).text().trim()
        val parts = stat.split("|")
        if (parts.size == 2) {
            val a = parts[0].split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            val b = parts[1].split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
            if (a.size >= 4 && b.size >= 4) {
                val idleA = a[3] + (a.getOrElse(4) { 0 })
                val idleB = b[3] + (b.getOrElse(4) { 0 })
                val totalA = a.sum()
                val totalB = b.sum()
                val dTotal = totalB - totalA
                val dIdle = idleB - idleA
                if (dTotal > 0) {
                    val usage = ((dTotal - dIdle) * 100 / dTotal).coerceIn(0, 100)
                    sb.append("CPU 负载  ")
                    sb.append(usage)
                    sb.append("%\n")
                }
            }
        }

        // GPU
        val gpuMhz = SuShell.exec("cat $KGSL/gpuclk 2>/dev/null", 6L).text().trim()
            .toLongOrNull()?.div(1_000_000)
        val gpuLoad = SuShell.exec("cat $KGSL/gpu_busy_percentage 2>/dev/null", 6L).text().trim()
        val gpuMax = SuShell.exec("cat $KGSL/devfreq/max_freq 2>/dev/null", 6L).text().trim()
            .toLongOrNull()?.div(1_000_000)
        if (gpuMhz != null) {
            sb.append("GPU  ")
            sb.append(gpuMhz)
            sb.append("MHz")
            if (gpuMax != null) {
                sb.append("/")
                sb.append(gpuMax)
                sb.append("MHz")
            }
            if (gpuLoad.isNotBlank()) {
                sb.append("  负载 ")
                sb.append(gpuLoad.trim())
            }
            sb.append("\n")
        }

        // 内存
        val mem = SuShell.exec("grep -E 'MemTotal|MemAvailable' /proc/meminfo 2>/dev/null", 8L).text()
        val totalKb = Regex("MemTotal:\\s*(\\d+)").find(mem)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val availKb = Regex("MemAvailable:\\s*(\\d+)").find(mem)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        if (totalKb > 0) {
            val used = (totalKb - availKb) / 1024.0 / 1024.0
            val total = totalKb / 1024.0 / 1024.0
            sb.append(String.format("内存  %.1f/%.1f GB\n", used, total))
        }

        // 温度由 LiveStatus 负责（需要 Context 读电池广播），这里不重复
        return sb.toString().trimEnd()
    }
}
