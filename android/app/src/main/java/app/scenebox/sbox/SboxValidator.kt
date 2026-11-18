package app.scenebox.sbox

import app.scenebox.core.DeviceFingerprint
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

enum class Level(val label: String) {
    BLOCK("拦截"),
    CONFIRM("需确认"),
    WARN("注意"),
    INFO("提示"),
}

enum class Verdict(val label: String) {
    PASS("可以导入"),
    CONFIRM("需用户二次确认"),
    BLOCK("禁止导入"),
}

data class Issue(val level: Level, val code: String, val message: String)

data class ValidationResult(val verdict: Verdict, val issues: List<Issue>)

/**
 * 导入前强校验：拿"模板声明的目标机"和"当前真机"逐项对。
 *
 * 与 engine/analyze.py 的规则保持一致；v0.1 的频率扫描是"全文扫描 + 去重"的粗粒度实现，
 * TODO(v0.2) 移植完整展开逻辑（@preset / @values 递归）后按 policy 精确比对。
 */
object SboxValidator {
    private val freqRange = 100_000L..4_000_000L

    private val riskyKeywords = listOf(
        "disable_cpufreq_limit",
        "perfmgr_enable",
        "glk_disable",
        "glk_freq_limit",
        "thermal",
        "sched_fmax_cap",
    )

    fun validate(bundle: SboxBundle, fp: DeviceFingerprint): ValidationResult {
        val issues = mutableListOf<Issue>()
        val target = bundle.target

        // 1) 芯片平台：跨平台必须拦
        if (target.soc.platform.isNotEmpty() && fp.socPlatform.isNotEmpty() &&
            target.soc.platform != fp.socPlatform
        ) {
            issues += Issue(
                Level.BLOCK,
                "SOC_MISMATCH",
                "芯片平台不匹配：模板 ${target.soc.platform}/${target.soc.model}，" +
                    "本机 ${fp.socPlatform}/${fp.socModel}",
            )
        } else if (target.soc.model.isNotEmpty() && fp.socModel.isNotEmpty() &&
            target.soc.model != fp.socModel
        ) {
            issues += Issue(
                Level.WARN,
                "SOC_MODEL_DIFF",
                "同平台不同芯片：模板 ${target.soc.model} / 本机 ${fp.socModel}，频点表可能不同",
            )
        }

        // 2) 机型
        if (target.device.model.isNotEmpty() && fp.model.isNotEmpty() &&
            target.device.model != fp.model
        ) {
            issues += Issue(
                Level.WARN,
                "DEVICE_DIFF",
                "机型不同：模板 ${target.device.brand} ${target.device.model} / " +
                    "本机 ${fp.brand} ${fp.model}",
            )
        }

        // 3) 系统版本
        if (target.os.android.isNotEmpty() && fp.android.isNotEmpty() &&
            target.os.android != fp.android
        ) {
            issues += Issue(
                Level.WARN,
                "ANDROID_DIFF",
                "Android 版本不同：模板 ${target.os.android} / 本机 ${fp.android}",
            )
        }

        // 4) 频点表比对：只看"真正会被写入的数值"，与 engine/analyze.py 的 validate 一致
        val expanded = bundle.payload.profile?.let { SboxExpander.expand(it) } ?: emptyList()
        val freqToken = Regex("^[#^@$]?\\d{5,8}$")

        if (fp.allFreqs.isNotEmpty() && expanded.isNotEmpty()) {
            val above = sortedSetOf<Long>()
            val unknown = sortedSetOf<Long>()
            val localFreqs = fp.allFreqs
            val localMax = fp.maxFreq
            for (write in expanded) {
                if (write.path == null) continue
                val value = write.value.trim()
                if (!freqToken.matches(value)) continue
                val number = value.trimStart('#', '^', '@', '$').toLongOrNull() ?: continue
                if (number !in freqRange) continue
                if (number > localMax) {
                    above += number
                } else if (number !in localFreqs) {
                    unknown += number
                }
            }
            if (above.isNotEmpty()) {
                issues += Issue(
                    Level.WARN,
                    "FREQ_ABOVE",
                    "${above.size} 处频率高于本机上限（$localMax），会被内核钳制：" +
                        above.take(4).joinToString(),
                )
            }
            if (unknown.isNotEmpty()) {
                issues += Issue(
                    Level.WARN,
                    "FREQ_UNKNOWN",
                    "${unknown.size} 处频率不在本机频点表中，将被就近钳制：" +
                        unknown.take(4).joinToString(),
                )
            }
        }

        // 5) 限制器 / 温控：看写入路径，不看数值
        val hits = expanded.mapNotNull { it.path }
            .filter { path -> riskyKeywords.any { path.contains(it) } }
            .distinct()
        if (hits.isNotEmpty()) {
            issues += Issue(
                Level.CONFIRM,
                "AGGRESSIVE",
                "方案涉及 ${hits.size} 个限制器/温控节点，可能导致过热锁核",
            )
        }

        val verdict = when {
            issues.any { it.level == Level.BLOCK } -> Verdict.BLOCK
            issues.any { it.level == Level.CONFIRM } -> Verdict.CONFIRM
            else -> Verdict.PASS
        }
        return ValidationResult(verdict, issues)
    }
}
