package app.scenebox.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 操作日志。所有会改动系统或文件的操作都记一条，出问题能回溯。
 * 存 App 外部目录 logs/actions.log，一行一条，最新在最上面读取时倒序。
 */
object ActionLog {

    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    private fun file(context: Context): File =
        File(context.getExternalFilesDir(null), "logs/actions.log")

    fun append(context: Context, tag: String, message: String) {
        try {
            val f = file(context)
            f.parentFile?.mkdirs()
            val line = "${stamp.format(Date())} [$tag] ${message.replace("\n", " ")}\n"
            f.appendText(line)
        } catch (_: Throwable) {
            // 日志失败不能影响主流程
        }
    }

    fun recent(context: Context, limit: Int = 12): List<String> {
        return try {
            val f = file(context)
            if (!f.exists()) emptyList()
            else f.readLines().filter { it.isNotBlank() }.takeLast(limit).reversed()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun clear(context: Context): Boolean = try {
        val f = file(context)
        if (f.exists()) f.delete() else true
    } catch (_: Throwable) {
        false
    }
}
