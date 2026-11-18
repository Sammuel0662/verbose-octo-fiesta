package app.scenebox.sbox

import android.content.Context
import app.scenebox.core.SuShell
import app.scenebox.core.shQuote
import java.io.File

/**
 * 清理与还原。用户最怕的是"导入了乱七八糟的配置，不知道怎么退回去"，
 * 所以这里必须能一键撤销，而不是让用户去翻文件管理器。
 */
object CleanupService {

    data class Result(val ok: Boolean, val message: String)

    private const val TARGET = "/data/data/com.omarea.vtools/files/profile.json"

    /**
     * 一键清理：只删"从 SceneBox 下载/接收"的配置包（downloads 目录），
     * 用户自己导出的包留在 sbox 目录里不动。
     */
    fun clearDownloads(context: Context): Result {
        val dir = File(context.getExternalFilesDir(null), "downloads")
        val files = dir.listFiles()?.filter { it.name.endsWith(".sbox", true) } ?: emptyList()
        if (files.isEmpty()) return Result(true, "没有下载过的配置包")
        var removed = 0
        for (f in files) if (f.delete()) removed++
        return Result(true, "已清理 $removed 个下载的配置包")
    }

    /** 清空快照 */
    fun clearSnapshots(context: Context): Result {
        val dir = File(context.getExternalFilesDir(null), "snapshots")
        val files = dir.listFiles()?.filter { it.name.endsWith(".json", true) } ?: emptyList()
        if (files.isEmpty()) return Result(true, "没有可删除的快照")
        var removed = 0
        for (f in files) if (f.delete()) removed++
        return Result(true, "已删除 $removed 个快照")
    }

    /** 列出 Scene 里导入时留下的备份 */
    fun listBackups(): List<String> {
        val result = SuShell.execHost("ls -1t ${shQuote(TARGET)}.sboxbak-* 2>/dev/null", 10L)
        if (!result.has()) return emptyList()
        return result.text().lineSequence().map { it.trim() }.filter { it.startsWith("/") }.toList()
    }

    /**
     * 撤销导入：把 Scene 的 profile.json 还原成最近一次导入前的备份。
     * 同样走宿主命名空间 + 保留属主权限 + 原子替换。
     */
    fun restoreLatestBackup(): Result {
        val backups = listBackups()
        if (backups.isEmpty()) {
            return Result(false, "没有找到导入前的备份（可能从未导入过）")
        }
        val script = "set -e; " +
            "T=${shQuote(TARGET)}; " +
            "B=${shQuote(backups.first())}; " +
            "cp -f \"\$B\" \"\$T.tmp\"; " +
            "chown \$(stat -c %u:%g \"\$T\") \"\$T.tmp\"; " +
            "chmod \$(stat -c %a \"\$T\") \"\$T.tmp\"; " +
            "mv -f \"\$T.tmp\" \"\$T\"; " +
            "echo RESTORED"
        val result = SuShell.execHost(script, 30L)
        if (!result.text().contains("RESTORED")) {
            return Result(false, "还原失败：${result.text().take(100).ifBlank { "无输出" }}")
        }
        val name = backups.first().substringAfterLast('/')
        return Result(true, "已还原到导入前的配置\n备份文件：$name")
    }
}
