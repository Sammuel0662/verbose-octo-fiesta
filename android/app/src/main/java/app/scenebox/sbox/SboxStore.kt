package app.scenebox.sbox

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * .sbox 的本地存放位置：/sdcard/Android/data/app.scenebox/files/sbox/
 * 导出写这里，预览也从这里读，不经过 SAF，方便调试与分享。
 */
object SboxStore {

    fun dir(context: Context): File = File(context.getExternalFilesDir(null), "sbox")

    /** 从外部（聊天/浏览器/社区）拿到的配置包，和"我导出的"分开放，方便一键清理 */
    fun downloadsDir(context: Context): File = File(context.getExternalFilesDir(null), "downloads")

    fun listDownloads(context: Context): List<File> {
        val d = downloadsDir(context)
        if (!d.exists()) return emptyList()
        return d.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".sbox", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** 把用户选中的外部文件复制进 downloads，返回落地文件 */
    fun copyIn(context: Context, uri: Uri): File? {
        val dir = downloadsDir(context)
        if (!dir.exists() && !dir.mkdirs()) return null
        val display = queryDisplayName(context, uri)
        val safe = (display ?: "imported-${System.currentTimeMillis()}.sbox")
            .substringAfterLast('/')
            .trim()
        val name = if (safe.endsWith(".sbox", true)) safe else "$safe.sbox"
        val target = File(dir, name)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.exists()) target else null
        } catch (t: Throwable) {
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (t: Throwable) {
        null
    }

    fun list(context: Context): List<File> {
        val d = dir(context)
        if (!d.exists()) return emptyList()
        return d.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".sbox", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun load(file: File): SboxBundle = SboxIO.parseFile(file)

    fun sizeText(file: File): String {
        val kb = file.length() / 1024.0
        return if (kb < 1024) String.format("%.0f KB", kb) else String.format("%.1f MB", kb / 1024)
    }
}
