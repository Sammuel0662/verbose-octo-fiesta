package app.scenebox.sbox

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 展开后的一条写操作：要么写某个 sysfs 路径，要么是 Scene daemon 的原生操作。 */
data class WriteOp(
    val source: String,
    val path: String?,
    val alias: String?,
    val value: String,
    val op: String? = null,
    val args: List<String> = emptyList(),
)

data class ProfileAnalysis(
    val risk: String,
    val writes: Int,
    val ops: Int,
    val paths: Int,
    val presets: Int,
    val schemes: Int,
    val items: List<Pair<String, String>>,
)

/**
 * 把 Scene 的 profile.json 展开成"到底会往哪写什么值"。
 * 与 engine/analyze.py 的 expand() 等价：
 *   $alias      -> 查 alias 表
 *   @preset     -> 递归展开预设组
 *   @values     -> 按 presets[name] 的路径列表逐条对应
 *   @governor / @cpuset / @limiters 等 -> 记为 Scene 原生操作
 */
object SboxExpander {

    fun expand(profile: JsonObject): List<WriteOp> {
        val aliases = profile["alias"] as? JsonObject ?: JsonObject(emptyMap())
        val presets = profile["presets"] as? JsonObject ?: JsonObject(emptyMap())
        val out = mutableListOf<WriteOp>()

        fun resolve(target: String): Pair<String, String?> {
            if (!target.startsWith("$")) return target to null
            val key = target.substring(1)
            val path = (aliases[key] as? JsonPrimitive)?.contentOrNull
            return (path ?: "<missing_alias:$key>") to key
        }

        fun walk(ops: JsonElement?, source: String, depth: Int, seen: Set<String>) {
            val arr = ops as? JsonArray ?: return
            if (depth > 6) return
            for (el in arr) {
                val item = el as? JsonArray ?: continue
                val head = (item.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: continue

                when {
                    head == "@preset" -> {
                        val presetName = (item.getOrNull(1) as? JsonPrimitive)?.contentOrNull
                            ?: continue
                        if (presetName !in seen) {
                            walk(
                                presets[presetName],
                                "$source>@preset:$presetName",
                                depth + 1,
                                seen + presetName,
                            )
                        }
                    }

                    head == "@values" -> {
                        val presetName = (item.getOrNull(1) as? JsonPrimitive)?.contentOrNull
                            ?: continue
                        val values = item.drop(2)
                            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        val targets = presets[presetName] as? JsonArray
                        if (targets == null) {
                            out += WriteOp(
                                source = "$source>@values:$presetName",
                                path = null,
                                alias = null,
                                value = values.joinToString(" "),
                                op = "@values",
                                args = values,
                            )
                            continue
                        }
                        targets.forEachIndexed { index, targetEl ->
                            val target = (targetEl as? JsonArray)?.getOrNull(0)
                                ?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@forEachIndexed
                            val value = values.getOrNull(index) ?: return@forEachIndexed
                            val (path, alias) = resolve(target)
                            out += WriteOp(
                                source = "$source>@values:$presetName",
                                path = path,
                                alias = alias,
                                value = value,
                            )
                        }
                    }

                    head.startsWith("@") -> {
                        val args = item.drop(1)
                            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        out += WriteOp(
                            source = source,
                            path = null,
                            alias = null,
                            value = args.joinToString(" "),
                            op = head,
                            args = args,
                        )
                    }

                    else -> {
                        val value = (item.getOrNull(1) as? JsonPrimitive)?.contentOrNull
                            ?: continue
                        val (path, alias) = resolve(head)
                        out += WriteOp(
                            source = source,
                            path = path,
                            alias = alias,
                            value = value,
                        )
                    }
                }
            }
        }

        presets.forEach { (presetName, ops) ->
            walk(ops, "preset:$presetName", 0, setOf(presetName))
        }
        (profile["schemes"] as? JsonObject)?.forEach { (scheme, phases) ->
            (phases as? JsonObject)?.forEach { (phase, ops) ->
                walk(ops, "scheme:$scheme/$phase", 0, emptySet())
            }
        }
        return out
    }

    private val riskyPaths = listOf(
        "disable_cpufreq_limit",
        "perfmgr_enable",
        "glk_disable",
        "glk_freq_limit",
        "thermal",
        "sched_fmax_cap",
    )

    /** 静态风险分析：只是给用户一个"这方案有多猛"的直观判断。 */
    fun analyze(profile: JsonObject): ProfileAnalysis {
        val writes = expand(profile)
        val pathWrites = writes.filter { it.path != null }
        val items = mutableListOf<Pair<String, String>>()

        val hits = pathWrites.mapNotNull { it.path }
            .filter { p -> riskyPaths.any { p.contains(it) } }
            .distinct()
        for (p in hits) {
            items += "high" to "涉及限制器/温控节点：$p"
        }

        val risk = when {
            hits.isNotEmpty() -> "high"
            pathWrites.any { it.path?.contains("min_freq") == true } -> "medium"
            else -> "low"
        }
        if (items.isEmpty()) items += "info" to "未发现危险写入"

        return ProfileAnalysis(
            risk = risk,
            writes = pathWrites.size,
            ops = writes.size - pathWrites.size,
            paths = pathWrites.mapNotNull { it.path }.distinct().size,
            presets = (profile["presets"] as? JsonObject)?.size ?: 0,
            schemes = (profile["schemes"] as? JsonObject)?.size ?: 0,
            items = items,
        )
    }
}
