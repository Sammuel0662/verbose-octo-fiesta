package app.scenebox.core

/**
 * 节点浏览器：把"还有哪些东西能调"直接摊开给用户，而不是只给几个预设。
 *
 * 只读 + 可写标记来自真实 `[ -w ]` 检测，值来自 `cat`，不猜。
 */
object NodeBrowser {

    data class Node(val path: String, val value: String, val writable: Boolean)

    data class Group(val name: String, val prefix: String)

    val groups = listOf(
        Group("CPU 频率", "/sys/devices/system/cpu/cpufreq"),
        Group("CPU 核心", "/sys/devices/system/cpu/cpu0/core_ctl"),
        Group("GPU", "/sys/class/kgsl/kgsl-3d0"),
        Group("内核调度", "/proc/sys/kernel"),
        Group("WALT 调度", "/proc/sys/walt"),
        Group("模块参数", "/sys/module"),
        Group("温控", "/sys/class/thermal"),
        Group("充电/电池", "/sys/class/power_supply"),
    )

    /** limit：最多返回多少个节点，避免一次拉太多 */
    fun list(prefix: String, limit: Int = 120): List<Node> {
        val script = buildString {
            append("find ").append(prefix)
            append(" -maxdepth 3 -type f 2>/dev/null | head -").append(limit)
            append(" | while read f; do ")
            append("v=\$(head -c 40 \$f 2>/dev/null | tr '\\n' ' '); ")
            append("if [ -w \$f ]; then w=W; else w=R; fi; ")
            append("echo \"\$f|\$w|\$v\"; done")
        }
        val out = SuShell.exec(script, 25L).text()
        return out.lineSequence().mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size < 3) return@mapNotNull null
            val path = parts[0].trim()
            if (path.isEmpty()) return@mapNotNull null
            Node(
                path = path,
                value = parts[2].trim().take(40),
                writable = parts[1].trim() == "W",
            )
        }.toList()
    }
}
