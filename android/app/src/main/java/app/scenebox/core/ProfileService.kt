package app.scenebox.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 把广场方案"补"进 Scene 的 profile，而不是覆盖它。
 *
 * 做法：
 *  1. 读 Scene 的真实 profile（字段一个不动）；
 *  2. alias 里追加本机真实 policy 的路径（如 sbox_max_p0 -> policy0/scaling_max_freq）；
 *  3. schemes.balance.app 末尾追加这批写入（后写的会覆盖先写的）；
 *  4. 其余 features / apps / games / presets 原样保留。
 *
 * 这样好处：结构永远合法；用本机真实 policy（Scene 自带 profile 里引用的是
 * policy6，本机根本没有这个簇，直接改那些值等于没写）。
 */
object ProfileService {

    data class Result(val ok: Boolean, val message: String)

    data class Patched(
        val profile: JsonObject,
        val changes: List<String>,
    )

    private fun freqFor(policy: CpuPolicy, pct: Int, isMax: Boolean): Long? {
        val target = policy.max * pct / 100
        return if (isMax) {
            policy.avail.filter { it <= target }.maxOrNull() ?: policy.avail.minOrNull()
        } else {
            policy.avail.filter { it >= target }.minOrNull() ?: policy.avail.maxOrNull()
        }
    }

    fun patch(
        source: JsonObject,
        patch: List<Pair<String, String>>,
        fingerprint: DeviceFingerprint,
    ): Patched {
        val policies = fingerprint.policies
        val changes = mutableListOf<String>()

        // 1) alias 追加本机路径
        val aliasMap = (source["alias"] as? JsonObject)
            ?.entries?.associate { it.key to it.value }?.toMutableMap() ?: mutableMapOf()
        for ((index, policy) in policies.withIndex()) {
            aliasMap["sbox_max_p$index"] =
                JsonPrimitive("/sys/devices/system/cpu/cpufreq/${policy.name}/scaling_max_freq")
            aliasMap["sbox_min_p$index"] =
                JsonPrimitive("/sys/devices/system/cpu/cpufreq/${policy.name}/scaling_min_freq")
        }

        // 2) 组装要追加的写入
        val appended = mutableListOf<JsonElement>()
        var governor: String? = null
        val maxPct = mutableMapOf<Int, Int>()
        val minPct = mutableMapOf<Int, Int>()
        var limitsOff = false

        for ((key, value) in patch) {
            when {
                key == "governor" -> governor = value
                key == "limits" && value == "off" -> limitsOff = true
                key.startsWith("cpu_max_pct_p") ->
                    key.removePrefix("cpu_max_pct_p").toIntOrNull()?.let {
                        value.toIntOrNull()?.let { pct -> maxPct[it] = pct }
                    }
                key.startsWith("cpu_min_pct_p") ->
                    key.removePrefix("cpu_min_pct_p").toIntOrNull()?.let {
                        value.toIntOrNull()?.let { pct -> minPct[it] = pct }
                    }
            }
        }

        if (governor != null) {
            for (policy in policies) {
                appended.add(buildJsonArray {
                    add(JsonPrimitive("/sys/devices/system/cpu/cpufreq/${policy.name}/scaling_governor"))
                    add(JsonPrimitive(governor))
                })
                changes += "${policy.name} governor=$governor"
            }
        }
        for ((index, pct) in maxPct) {
            val policy = policies.getOrNull(index) ?: continue
            val freq = freqFor(policy, pct, true) ?: continue
            appended.add(buildJsonArray {
                add(JsonPrimitive("\$sbox_max_p$index"))
                add(JsonPrimitive(freq.toString()))
            })
            changes += "${policy.name} 上限=$freq(${pct}%)"
        }
        for ((index, pct) in minPct) {
            val policy = policies.getOrNull(index) ?: continue
            val freq = freqFor(policy, pct, false) ?: continue
            appended.add(buildJsonArray {
                add(JsonPrimitive("\$sbox_min_p$index"))
                add(JsonPrimitive(freq.toString()))
            })
            changes += "${policy.name} 下限=$freq(${pct}%)"
        }
        if (limitsOff) {
            appended.add(buildJsonArray {
                add(JsonPrimitive("/sys/module/perfmgr/parameters/perfmgr_enable"))
                add(JsonPrimitive("0"))
            })
            appended.add(buildJsonArray {
                add(JsonPrimitive("/sys/module/migt/parameters/glk_disable"))
                add(JsonPrimitive("1"))
            })
            appended.add(buildJsonArray {
                add(JsonPrimitive("/proc/game_opt/disable_cpufreq_limit"))
                add(JsonPrimitive("1"))
            })
            changes += "解除系统限制器"
        }

        // 3) 合并进 schemes.balance.app（保留原有条目，追加在后）
        val schemes = (source["schemes"] as? JsonObject)
            ?.entries?.associate { it.key to it.value }?.toMutableMap() ?: mutableMapOf()
        val balance = (schemes["balance"] as? JsonObject)
            ?.entries?.associate { it.key to it.value }?.toMutableMap() ?: mutableMapOf()
        val appOps = (balance["app"] as? JsonArray)?.toList()?.toMutableList() ?: mutableListOf()
        appOps += appended
        balance["app"] = JsonArray(appOps)
        schemes["balance"] = JsonObject(balance)

        // 4) 重建 profile：未涉及字段原样保留
        val rebuilt = buildJsonObject {
            for ((key, value) in source) {
                when (key) {
                    "alias" -> put("alias", JsonObject(aliasMap))
                    "schemes" -> put("schemes", JsonObject(schemes))
                    else -> put(key, value)
                }
            }
            // 极少数情况下原文件没有 alias/schemes
            if (source["alias"] == null) put("alias", JsonObject(aliasMap))
            if (source["schemes"] == null) put("schemes", JsonObject(schemes))
        }

        return Patched(rebuilt, changes)
    }
}
