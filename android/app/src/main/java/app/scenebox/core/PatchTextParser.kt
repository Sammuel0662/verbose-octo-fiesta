package app.scenebox.core

/**
 * 自定义参数解析：把"人话"翻译成要写的 sysfs 键值。
 *
 * 支持两类写法：
 *   语义化：上限 80% / 下限 30% / governor schedutil / gpu 608 / clamp 256 1024
 *   直写：  /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq=1200000
 *
 * 簇可以用 p0..pN 指定，不写就是全部簇。
 */
object PatchTextParser {

    data class Result(
        val patch: List<Pair<String, String>>,
        val notes: List<String>,
        val errors: List<String>,
    ) {
        val ok: Boolean get() = errors.isEmpty() && patch.isNotEmpty()
    }

    private val governorRe = Regex("^(?:governor|gov|调度器?)\\s*[:= ]\\s*([a-zA-Z_]+)$", RegexOption.IGNORE_CASE)
    private val maxAllRe = Regex("^(?:max|上限)\\s*[:= ]?\\s*(\\d{1,3})\\s*%?$", RegexOption.IGNORE_CASE)
    private val maxOneRe = Regex("^(?:max|上限)\\s*p(\\d)\\s*[:= ]?\\s*(\\d{1,3})\\s*%?$", RegexOption.IGNORE_CASE)
    private val minAllRe = Regex("^(?:min|下限)\\s*[:= ]?\\s*(\\d{1,3})\\s*%?$", RegexOption.IGNORE_CASE)
    private val minOneRe = Regex("^(?:min|下限)\\s*p(\\d)\\s*[:= ]?\\s*(\\d{1,3})\\s*%?$", RegexOption.IGNORE_CASE)
    private val limitsRe = Regex("^(?:limits|限制器)\\s*[:= ]?\\s*(off|on|关|开)$", RegexOption.IGNORE_CASE)
    private val pathRe = Regex("^(?:path\\s+)?(/sys/\\S+|/proc/\\S+)\\s*[=:]?\\s+(\\S+)$")

    // 其他可 DIY 的项
    private val gpuRe = Regex("^(?:gpu|显卡)\\s*[:= ]?\\s*(\\d{3,4})$", RegexOption.IGNORE_CASE)
    private val gpuMinRe = Regex("^(?:gpu_min|显卡下限)\\s*[:= ]?\\s*(\\d{3,4})$", RegexOption.IGNORE_CASE)
    private val clampRe = Regex("^clamp\\s+(\\d{1,4})\\s+(\\d{1,4})$", RegexOption.IGNORE_CASE)
    private val boostRe = Regex("^(?:boost|sched_boost|加速)\\s*[:= ]?\\s*(\\d)$", RegexOption.IGNORE_CASE)
    private val coreMinRe = Regex("^(?:core_min|大核下限数)\\s*(?:p(\\d))?\\s*[:= ]?\\s*(\\d)$", RegexOption.IGNORE_CASE)
    private val coreMaxRe = Regex("^(?:core_max|大核上限数)\\s*(?:p(\\d))?\\s*[:= ]?\\s*(\\d)$", RegexOption.IGNORE_CASE)
    private val hispeedRe = Regex("^(?:hispeed|升频阈值)\\s*(?:p(\\d))?\\s*[:= ]?\\s*(\\d{5,8})$", RegexOption.IGNORE_CASE)
    private val targetLoadRe = Regex("^(?:target_load|目标负载)\\s*(?:p(\\d))?\\s*[:= ]?\\s*([0-9: ]+)$", RegexOption.IGNORE_CASE)

    fun parse(text: String, fingerprint: DeviceFingerprint): Result {
        val patch = mutableListOf<Pair<String, String>>()
        val notes = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val policies = fingerprint.policies
        val n = policies.size

        val lines = text.split('\n', '，', ',', '；', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) return Result(emptyList(), emptyList(), listOf("还没有输入内容"))

        fun policyDir(index: Int): String {
            val p = policies.getOrNull(index) ?: policies.first()
            return "/sys/devices/system/cpu/cpufreq/${p.name}"
        }

        for (line in lines) {
            when {
                governorRe.matches(line) -> {
                    val gov = governorRe.find(line)!!.groupValues[1]
                    patch += "governor" to gov
                    notes += "调度器 = $gov"
                }

                maxOneRe.matches(line) -> {
                    val m = maxOneRe.find(line)!!
                    patch += "cpu_max_pct_p${m.groupValues[1]}" to m.groupValues[2]
                    notes += "p${m.groupValues[1]} 上限 ${m.groupValues[2]}%"
                }

                maxAllRe.matches(line) -> {
                    val pct = maxAllRe.find(line)!!.groupValues[1]
                    for (i in 0 until n) patch += "cpu_max_pct_p$i" to pct
                    notes += "全部簇上限 $pct%"
                }

                minOneRe.matches(line) -> {
                    val m = minOneRe.find(line)!!
                    patch += "cpu_min_pct_p${m.groupValues[1]}" to m.groupValues[2]
                    notes += "p${m.groupValues[1]} 下限 ${m.groupValues[2]}%"
                }

                minAllRe.matches(line) -> {
                    val pct = minAllRe.find(line)!!.groupValues[1]
                    for (i in 0 until n) patch += "cpu_min_pct_p$i" to pct
                    notes += "全部簇下限 $pct%"
                }

                limitsRe.matches(line) -> {
                    val off = limitsRe.find(line)!!.groupValues[1].lowercase().let { it == "off" || it == "关" }
                    patch += "limits" to if (off) "off" else "on"
                    notes += if (off) "关闭系统限制器" else "开启系统限制器"
                }

                gpuRe.matches(line) -> {
                    val mhz = gpuRe.find(line)!!.groupValues[1]
                    patch += "raw:/sys/class/kgsl/kgsl-3d0/devfreq/max_freq" to (mhz.toLong() * 1_000_000).toString()
                    notes += "GPU 上限 ${mhz}MHz"
                }

                gpuMinRe.matches(line) -> {
                    val mhz = gpuMinRe.find(line)!!.groupValues[1]
                    patch += "raw:/sys/class/kgsl/kgsl-3d0/devfreq/min_freq" to (mhz.toLong() * 1_000_000).toString()
                    notes += "GPU 下限 ${mhz}MHz"
                }

                clampRe.matches(line) -> {
                    val m = clampRe.find(line)!!
                    patch += "raw:/proc/sys/kernel/sched_util_clamp_min" to m.groupValues[1]
                    patch += "raw:/proc/sys/kernel/sched_util_clamp_max" to m.groupValues[2]
                    notes += "util clamp ${m.groupValues[1]}~${m.groupValues[2]}"
                }

                boostRe.matches(line) -> {
                    val v = boostRe.find(line)!!.groupValues[1]
                    patch += "raw:/proc/sys/walt/sched_boost" to v
                    notes += "sched_boost=$v"
                }

                coreMinRe.matches(line) -> {
                    val m = coreMinRe.find(line)!!
                    val idx = m.groupValues[1]
                    val value = m.groupValues[2]
                    if (idx.isBlank()) {
                        for (i in policies.indices) {
                            val cpu = policies[i].name.removePrefix("policy")
                            patch += "raw:/sys/devices/system/cpu/cpu$cpu/core_ctl/min_cpus" to value
                        }
                    } else {
                        val cpu = policies.getOrNull(idx.toInt())?.name?.removePrefix("policy")
                        if (cpu != null) patch += "raw:/sys/devices/system/cpu/cpu$cpu/core_ctl/min_cpus" to value
                    }
                    notes += "core_ctl 最小核数 = $value"
                }

                coreMaxRe.matches(line) -> {
                    val m = coreMaxRe.find(line)!!
                    val idx = m.groupValues[1]
                    val value = m.groupValues[2]
                    if (idx.isBlank()) {
                        for (i in policies.indices) {
                            val cpu = policies[i].name.removePrefix("policy")
                            patch += "raw:/sys/devices/system/cpu/cpu$cpu/core_ctl/max_cpus" to value
                        }
                    } else {
                        val cpu = policies.getOrNull(idx.toInt())?.name?.removePrefix("policy")
                        if (cpu != null) patch += "raw:/sys/devices/system/cpu/cpu$cpu/core_ctl/max_cpus" to value
                    }
                    notes += "core_ctl 最大核数 = $value"
                }

                hispeedRe.matches(line) -> {
                    val m = hispeedRe.find(line)!!
                    val idx = m.groupValues[1]
                    val value = m.groupValues[2]
                    if (idx.isBlank()) {
                        for (i in policies.indices) {
                            patch += "raw:${policyDir(i)}/walt/hispeed_freq" to value
                        }
                    } else {
                        patch += "raw:${policyDir(idx.toInt())}/walt/hispeed_freq" to value
                    }
                    notes += "升频阈值 hispeed_freq = $value"
                }

                targetLoadRe.matches(line) -> {
                    val m = targetLoadRe.find(line)!!
                    val idx = m.groupValues[1]
                    val value = m.groupValues[2].trim()
                    if (idx.isBlank()) {
                        for (i in policies.indices) {
                            patch += "raw:${policyDir(i)}/walt/target_loads" to value
                        }
                    } else {
                        patch += "raw:${policyDir(idx.toInt())}/walt/target_loads" to value
                    }
                    notes += "target_loads = $value"
                }

                pathRe.matches(line) -> {
                    val m = pathRe.find(line)!!
                    patch += "raw:${m.groupValues[1]}" to m.groupValues[2]
                    notes += "${m.groupValues[1]} = ${m.groupValues[2]}"
                }

                else -> errors += "看不懂：$line"
            }
        }
        return Result(patch, notes, errors)
    }
}
