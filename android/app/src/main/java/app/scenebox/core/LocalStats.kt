package app.scenebox.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本机统计：完全离线，只记录这台设备上的使用情况。
 *
 * 不联网、不上报、不需要任何服务端。数据存在 SharedPreferences 里，
 * 清数据/重装即归零——这是本机统计，不是"全网用户数"。
 */
object LocalStats {

    private fun prefs(context: Context) =
        context.getSharedPreferences("local_stats", Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun now(): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date())

    /** 每次启动调用：记录首次使用、最近使用、活跃天数 */
    fun touch(context: Context) {
        val p = prefs(context)
        val editor = p.edit()
        if (!p.contains("first_use")) editor.putString("first_use", today())
        editor.putString("last_use", now())

        val days = p.getString("days", "").orEmpty().split(',').filter { it.isNotBlank() }.toMutableSet()
        days += today()
        editor.putString("days", days.takeLast(400).joinToString(","))
        editor.apply()
    }

    /** 计数：tag 与操作日志的分类一致 */
    fun bump(context: Context, tag: String) {
        val p = prefs(context)
        val key = "count_" + tag
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    fun count(context: Context, tag: String): Int = prefs(context).getInt("count_$tag", 0)

    data class Summary(
        val firstUse: String,
        val lastUse: String,
        val activeDays: Int,
        val applies: Int,
        val samples: Int,
        val snapshots: Int,
        val transfer: Int,
    )

    fun summary(context: Context): Summary {
        val p = prefs(context)
        val days = p.getString("days", "").orEmpty().split(',').filter { it.isNotBlank() }.size
        val applies = count(context, "广场") + count(context, "逐项") + count(context, "自定义")
        val samples = count(context, "采样") + count(context, "对比采样")
        val snapshots = count(context, "快照保存") + count(context, "快照恢复")
        val transfer = count(context, "导出") + count(context, "导入")
        return Summary(
            firstUse = p.getString("first_use", "—") ?: "—",
            lastUse = p.getString("last_use", "—") ?: "—",
            activeDays = days,
            applies = applies,
            samples = samples,
            snapshots = snapshots,
            transfer = transfer,
        )
    }
}
