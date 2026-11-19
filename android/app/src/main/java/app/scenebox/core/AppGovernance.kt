package app.scenebox.core

/**
 * 应用治理：冻结/解冻（pm suspend）与待机分组（am set-standby-bucket）。
 * 全部走 root/pm 命令，不常驻、不监听。
 */
object AppGovernance {

    data class App(val pkg: String, val label: String)

    fun listUserApps(): List<App> {
        val out = SuShell.exec("pm list packages -3 2>/dev/null", 25L).text()
        return out.lineSequence()
            .mapNotNull { it.trim().removePrefix("package:") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { App(it, it) }
            .toList()
    }

    fun suspend(pkg: String): String {
        val r = SuShell.exec("pm suspend $pkg 2>&1", 15L)
        return if (r.code == 0 && !r.text().contains("Error")) "已冻结 $pkg" else "冻结失败：${r.text().take(60)}"
    }

    fun unsuspend(pkg: String): String {
        val r = SuShell.exec("pm unsuspend $pkg 2>&1", 15L)
        return if (r.code == 0 && !r.text().contains("Error")) "已解冻 $pkg" else "解冻失败：${r.text().take(60)}"
    }

    fun setStandby(pkg: String, bucket: String): String {
        val r = SuShell.exec("am set-standby-bucket $pkg $bucket 2>&1", 15L)
        return if (r.code == 0 && !r.text().contains("Error")) "已设为 $bucket" else "设置失败：${r.text().take(60)}"
    }
}
