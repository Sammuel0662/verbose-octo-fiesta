package app.scenebox.core

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * 调度快照 A/B：把当前内核调度状态整批读下来存成 JSON，需要时整批写回。
 *
 * 解决的真实痛点：玩家调完游戏调度忘了恢复，日常刷视频又烫又费电。
 * 快照不依赖 Scene 进程存活，存的是 sysfs 真实值。
 */
object SnapshotService {

    data class Result(val ok: Boolean, val message: String)

    /** 与场景无关、但恢复时也要一起兜住的节点 */
    private val baseGlobs = listOf(
        "/sys/devices/system/cpu/cpufreq/policy*/scaling_governor",
        "/sys/devices/system/cpu/cpufreq/policy*/scaling_min_freq",
        "/sys/devices/system/cpu/cpufreq/policy*/scaling_max_freq",
        "/sys/devices/system/cpu/cpufreq/policy*/walt/hispeed_freq",
        "/sys/devices/system/cpu/cpufreq/policy*/walt/target_loads",
        "/sys/devices/system/cpu/cpufreq/boost",
        "/sys/devices/system/cpu/cpu*/core_ctl/min_cpus",
        "/sys/devices/system/cpu/cpu*/core_ctl/max_cpus",
        "/sys/devices/system/cpu/cpu*/core_ctl/enable",
        "/proc/sys/walt/sched_boost",
        "/proc/sys/walt/input_boost/sched_boost_on_input",
        "/proc/sys/kernel/sched_util_clamp_min",
        "/proc/sys/kernel/sched_util_clamp_max",
        "/sys/module/perfmgr/parameters/perfmgr_enable",
        "/sys/module/migt/parameters/glk_disable",
        "/proc/game_opt/disable_cpufreq_limit",
        "/sys/class/thermal/thermal_message/cpu_limits",
        "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
    )

    private val json = Json { prettyPrint = true }

    fun dir(context: Context): File = File(context.getExternalFilesDir(null), "snapshots")

    fun list(context: Context): List<String> =
        dir(context).listFiles()
            ?.filter { it.name.endsWith(".json") }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    private fun shellQuote(raw: String): String = "'" + raw.replace("'", "'\\''") + "'"

    /** 收集要跟踪的节点：固定清单按 glob 展开 + 方案 alias 直连节点 */
    private fun trackedPaths(channel: ShellChannel, profile: JsonObject?): List<String> {
        val paths = linkedSetOf<String>()
        // 注意：这里不能加引号，否则 shell 不会展开 policy* 这类通配符
        val globCmd = baseGlobs.joinToString(" ")
        val result = channel.exec("ls -d $globCmd 2>/dev/null")
        if (result.has()) {
            result.text().lineSequence().map { it.trim() }.filter { it.startsWith("/") }
                .forEach { paths += it }
        }
        profile?.get("alias")?.let { aliases ->
            (aliases as? JsonObject)?.values?.forEach { value ->
                val path = (value as? JsonPrimitive)?.content ?: return@forEach
                if (path.startsWith("/")) paths += path
            }
        }
        return paths.toList().sorted()
    }

    /** 一次 shell 批量读，避免几百次进程启动 */
    private fun readMany(channel: ShellChannel, paths: List<String>): Map<String, String> {
        if (paths.isEmpty()) return emptyMap()
        val list = paths.joinToString(" ") { shellQuote(it) }
        val script = "for p in $list; do if [ -f \"\$p\" ]; then " +
            "v=\$(cat \"\$p\" 2>/dev/null | tr '\\n' ' '); printf '%s\\t%s\\n' \"\$p\" \"\$v\"; fi; done"
        val result = channel.exec(script, 40L)
        if (!result.has()) return emptyMap()
        val out = linkedMapOf<String, String>()
        for (line in result.text().lineSequence()) {
            val idx = line.indexOf('\t')
            if (idx > 0) out[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
        return out
    }

    fun save(context: Context, channel: ShellChannel, label: String, profile: JsonObject?): Result {
        val paths = trackedPaths(channel, profile)
        if (paths.isEmpty()) return Result(false, "没有可跟踪的节点（需要 root）")
        val values = readMany(channel, paths)
        if (values.isEmpty()) return Result(false, "节点读取失败，请确认 root 授权")

        val obj = buildJsonObject {
            put("label", label)
            put("savedAt", System.currentTimeMillis())
            put("count", values.size)
            putJsonObject("values") {
                values.forEach { (path, value) -> put(path, value) }
            }
        }
        val d = dir(context)
        if (!d.exists() && !d.mkdirs()) return Result(false, "无法创建快照目录")
        return try {
            File(d, "$label.json").writeText(json.encodeToString(JsonObject.serializer(), obj))
            Result(true, "快照 $label 已保存：${values.size} 个节点")
        } catch (t: Throwable) {
            Result(false, "保存失败：${t.message}")
        }
    }

    fun restore(context: Context, channel: ShellChannel, label: String): Result {
        val file = File(dir(context), "$label.json")
        if (!file.exists()) return Result(false, "快照 $label 不存在")
        val root = try {
            Json.parseToJsonElement(file.readText()) as JsonObject
        } catch (t: Throwable) {
            return Result(false, "快照文件损坏：${t.message}")
        }
        val valuesObj = root["values"] as? JsonObject ?: return Result(false, "快照无数据")
        val values = valuesObj.entries.mapNotNull { (k, v) ->
            val text = (v as? JsonPrimitive)?.content ?: return@mapNotNull null
            if (text.isBlank()) null else k to text
        }
        if (values.isEmpty()) return Result(false, "快照无可写节点")

        var written = 0
        val failed = mutableListOf<String>()
        val chunk = 40
        for (part in values.chunked(chunk)) {
            val cmds = part.map { (path, value) ->
                val q = shellQuote(path)
                val v = shellQuote(value)
                "if [ -e $q ]; then chmod 0664 $q 2>/dev/null; " +
                    "echo $v > $q 2>/dev/null && echo \"OK $q\" || echo \"FAIL $q\"; " +
                    "else echo \"MISS $q\"; fi"
            }
            val result = channel.exec(cmds.joinToString("; "), 40L)
            for (line in result.text().lineSequence()) {
                when {
                    line.startsWith("OK ") -> written++
                    line.startsWith("FAIL ") || line.startsWith("MISS ") ->
                        failed += line.substring(5)
                }
            }
        }

        val after = readMany(channel, values.map { it.first })
        val mismatch = values.count { (path, value) -> after[path] != null && after[path] != value }
        val msg = buildString {
            append("快照 $label 已恢复：写入 $written/${values.size}")
            if (failed.isNotEmpty()) append("，跳过 ${failed.size}")
            if (mismatch > 0) append("，$mismatch 个被内核改回")
        }
        return Result(true, msg)
    }
}
