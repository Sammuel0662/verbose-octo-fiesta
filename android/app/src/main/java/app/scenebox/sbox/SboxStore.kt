package app.scenebox.sbox

import android.content.Context
import java.io.File

/**
 * .sbox 的本地存放位置：/sdcard/Android/data/app.scenebox/files/sbox/
 * 导出写这里，预览也从这里读，不经过 SAF，方便调试与分享。
 */
object SboxStore {

    fun dir(context: Context): File = File(context.getExternalFilesDir(null), "sbox")

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
