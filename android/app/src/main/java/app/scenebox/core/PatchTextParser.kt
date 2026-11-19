package app.scenebox.core

/**
 * 自定义参数的文本解析。
 *
 * 用户只写"想改什么"，比如：
 *   上限 80%
 *   上限 p0 60%
 *   下限 30%
 *   governor schedutil
 *   关限制器
 *   /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq=1200000
 *
 * 解析成和广场一样的补丁格式，复用同一套应用逻辑。
 */
object PatchTextParser {

    data class Result(
        val patch: List<Pair<String, String>>,
        val notes: List<String>,
        val errors: List<String>,
    ) {
        val ok: Boolean get() = errors.isEmpty() && patch.isNotEmpty()
    }

    private val governorRe = Regex("^(?:governor|gov|调度)\\s*[:= ]\\s*([a-zA-Z_]+)$", RegexOption.IGNORE_CASE)
    private val maxAllRe = Regex("^(?:max|上限)\\s*[:= ]?\\s*(\\d{1,3})\\s*%?$", RegexOption.IGNORE_CASE)
    private val maxOneRe = Regex("^(?:max|上限)\\s*p(\\d)\\s*[:= ]?\\s*(\\d{1,3})\\s*%?$", RegexOption.IGNORE_CASE)
    private val minAllRe = Regex("^(?:min|下限)\\s*[:= ]?\\s*(\\d{1,3})\\s*%?$", RegexOption.IGNORE_CASE)
    private val minOneRe = Regex("^(?:min|下限)\\s*p(\\d)\\s*[:= ]?\\s*(\\d{1,3})\\s*%?$", RegexOption.IGNORE_CASE)
    private val limitsRe = Regex("^(?:limits|限制器)\\s*[:= ]?\\s*(off|on|关|开)$", RegexOption.IGNORE_CASE)
    private val pathRe = Regex("^(?:path\\s+)?(/sys/\\S+|/proc/\\S+)\\s*[=:]?\\s+(\\S+)$")

    fun parse(text: String, policyCount: Int): Result {
        val patch = mutableListOf<Pair<String, String>>()
        val notes = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val lines = text.split('\n', '，', ',', '；', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            return Result(emptyList(), emptyList(), listOf("还没有输入内容"))
        }

        for (line in lines) {
            when {
                governorRe.matches(line) -> {
                    val gov = governorRe.find(line)!!.groupValues[1]
                    patch += "governor" to gov
                    notes += "governor=$gov"
                }

                maxOneRe.matches(line) -> {
                    val m = maxOneRe.find(line)!!
                    patch += "cpu_max_pct_p${m.groupValues[1]}" to m.groupValues[2]
                    notes += "p${m.groupValues[1]} 上限 ${m.groupValues[2]}%"
                }

                maxAllRe.matches(line) -> {
                    val pct = maxAllRe.find(line)!!.groupValues[1]
                    for (i in 0 until policyCount) patch += "cpu_max_pct_p$i" to pct
                    notes += "全部簇上限 $pct%"
                }

                minOneRe.matches(line) -> {
                    val m = minOneRe.find(line)!!
                    patch += "cpu_min_pct_p${m.groupValues[1]}" to m.groupValues[2]
                    notes += "p${m.groupValues[1]} 下限 ${m.groupValues[2]}%"
                }

                minAllRe.matches(line) -> {
                    val pct = minAllRe.find(line)!!.groupValues[1]
                    for (i in 0 until policyCount) patch += "cpu_min_pct_p$i" to pct
                    notes += "全部簇下限 $pct%"
                }

                limitsRe.matches(line) -> {
                    val v = limitsRe.find(line)!!.groupValues[1].lowercase()
                    val off = v == "off" || v == "关"
                    patch += "limits" to if (off) "off" else "on"
                    notes += if (off) "关闭系统限制器" else "开启系统限制器"
                }

                pathRe.matches(line) -> {
                    val m = pathRe.find(line)!!
                    patch += "raw:${m.groupValues[1]}" to m.groupValues[2]
                    notes += "${m.groupValues[1]} = ${m.groupValues[2]}"
                }

                else -> errors += "看不懂这一行：$line"
            }
        }
        return Result(patch, notes, errors)
    }
}
