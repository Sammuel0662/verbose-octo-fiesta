package app.scenebox.core

/**
 * 直接写 sysfs 的执行器（Scene 自动调度关闭时走这里）。
 *
 * 相比早期版本加了「写入核验 + 失败自动回退」：
 *  1. 写之前记住每个节点的原值；
 *  2. 写完回读，逐项比对；
 *  3. 有任何一项对不上，就把已写的全部改回原值，并如实报告哪些没成。
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

        // 先算出要写的 (路径, 值)
        val writes = linkedMapOf<String, String>()
        var limitsOff = false

        for ((key, value) in patch) {
            when {
                key == "governor" -> {
                    for (p in policies) {
                        writes["/sys/devices/system/cpu/cpufreq/${p.name}/scaling_governor"] = value
                    }
                }
                key == "limits" -> limitsOff = value == "off"
                key == "sched_boost" -> writes["/proc/sys/walt/sched_boost"] = value
                key.startsWith("raw:") -> writes[key.removePrefix("raw:")] = value
                key.startsWith("cpu_max_pct_p") -> {
                    val i = key.removePrefix("cpu_max_pct_p").toIntOrNull() ?: continue
                    val p = policies.getOrNull(i) ?: continue
                    val pct = value.toIntOrNull() ?: continue
                    val freq = largestAtMost(p.avail, p.max * pct / 100) ?: continue
                    writes["/sys/devices/system/cpu/cpufreq/${p.name}/scaling_max_freq"] = freq.toString()
                }
                key.startsWith("cpu_min_pct_p") -> {
                    val i = key.removePrefix("cpu_min_pct_p").toIntOrNull() ?: continue
                    val p = policies.getOrNull(i) ?: continue
                    val pct = value.toIntOrNull() ?: continue
                    val freq = smallestAtLeast(p.avail, p.max * pct / 100) ?: continue
                    writes["/sys/devices/system/cpu/cpufreq/${p.name}/scaling_min_freq"] = freq.toString()
                }
            }
        }
        if (limitsOff) {
            writes["/sys/module/perfmgr/parameters/perfmgr_enable"] = "0"
            writes["/sys/module/migt/parameters/glk_disable"] = "1"
            writes["/proc/game_opt/disable_cpufreq_limit"] = "1"
        }
        if (writes.isEmpty()) return Result(false, "没有可执行项")

        // 1) 读原值
        val originals = readAll(writes.keys)

        // 2) 执行写入
        val script = writes.entries.joinToString("; ") { (path, value) ->
            "if [ -e $path ]; then chmod 0664 $path 2>/dev/null; echo $value > $path 2>/dev/null; fi"
        }
        SuShell.exec(script, 30L)

        // 3) 回读核验
        val after = readAll(writes.keys)
        val mismatches = writes.keys.filter { path ->
            val want = writes[path]
            val got = after[path]
            got != null && want != null && !same(got, want)
        }

        if (mismatches.isEmpty()) {
            val detail = writes.entries.take(4).joinToString("、") { (path, value) ->
                "${path.substringAfterLast('/').ifBlank { path }}=$value"
            }
            return Result(true, "已写入并核验通过（${writes.size} 项）：$detail")
        }

        // 4) 失败回退
        val rollback = originals.entries
            .filter { it.value.isNotBlank() && it.value != "N/A" }
            .joinToString("; ") { (path, value) ->
                "echo $value > $path 2>/dev/null"
            }
        SuShell.exec(rollback, 30L)

        val failed = mismatches.take(3).joinToString("、") { it.substringAfterLast('/').ifBlank { it } }
        return Result(
            false,
            "有 ${mismatches.size} 项写入后不一致（$failed），已自动回退到原值。" +
                "多半是内核拒绝该值（超出频点表或节点只读）。",
        )
    }

    private fun same(got: String, want: String): Boolean =
        got.trim() == want.trim() ||
            (got.trim().toDoubleOrNull() != null && got.trim().toDoubleOrNull() == want.trim().toDoubleOrNull())

    private fun readAll(paths: Collection<String>): Map<String, String> {
        if (paths.isEmpty()) return emptyMap()
        val script = paths.joinToString("; ") { path ->
            "echo \"$path|\$(cat $path 2>/dev/null)\""
        }
        val out = SuShell.exec(script, 20L).text()
        return out.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('|')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1).trim()
            }
            .toMap()
    }
}
