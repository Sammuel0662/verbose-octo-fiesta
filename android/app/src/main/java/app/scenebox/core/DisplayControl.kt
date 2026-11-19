package app.scenebox.core

import android.content.Context

/**
 * 刷新率控制。
 *
 * 做法：读面板支持的模式，写 `settings put system peak_refresh_rate/min_refresh_rate`。
 * 这是系统级"最高/最低刷新率"开关，不依赖厂商私有节点，比较通用；
 * 部分机型生效需要亮屏变化一次（切应用）才刷新。
 */
object DisplayControl {

    fun modes(context: Context): List<Int> {
        val rates = try {
            context.display?.supportedModes?.map { it.refreshRate.toInt() } ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        return rates.distinct().sorted()
    }

    fun current(context: Context): Int = try {
        context.display?.refreshRate?.toInt() ?: 0
    } catch (_: Throwable) {
        0
    }

    fun setHz(hz: Int): String {
        if (hz <= 0) return "刷新率无效"
        val cmd = "settings put system peak_refresh_rate $hz; " +
            "settings put system min_refresh_rate $hz"
        val result = SuShell.exec(cmd, 15L)
        if (!result.has() && result.code != 0) return "写入失败，需要 root"
        val read = SuShell.exec("settings get system peak_refresh_rate", 10L).text().trim()
        return if (read.toIntOrNull() == hz) "已锁定 $hz Hz（切一次应用生效）"
        else "写入后回读为 $read，可能被系统覆盖"
    }

    fun reset(): String {
        val result = SuShell.exec(
            "settings delete system peak_refresh_rate; settings delete system min_refresh_rate",
            15L,
        )
        return if (result.has() || result.code == 0) "已恢复系统自动刷新率" else "恢复失败，需要 root"
    }
}
