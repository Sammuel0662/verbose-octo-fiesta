package app.scenebox.core

import android.content.Context
import java.io.File

/**
 * 用户配方：把自定义参数文本存下来复用，也能复制分享。
 */
object RecipeStore {

    data class Recipe(val name: String, val body: String)

    private fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "recipes").apply { mkdirs() }

    fun list(context: Context): List<Recipe> = try {
        dir(context).listFiles { f -> f.name.endsWith(".txt") }
            ?.sortedBy { it.name }
            ?.map { Recipe(it.nameWithoutExtension, it.readText()) }
            ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    fun save(context: Context, name: String, body: String): Boolean = try {
        val safe = name.trim().ifBlank { "未命名" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(24)
        File(dir(context), "$safe.txt").writeText(body)
        true
    } catch (_: Throwable) {
        false
    }

    fun delete(context: Context, name: String): Boolean = try {
        File(dir(context), "$name.txt").delete()
    } catch (_: Throwable) {
        false
    }

    /** 把补丁列表序列化成配方文本（一行 key=value） */
    fun encode(patch: List<Pair<String, String>>): String =
        patch.joinToString("\n") { (k, v) -> "$k=$v" }

    /** 配方文本解析回补丁列表 */
    fun decode(body: String): List<Pair<String, String>> =
        body.lineSequence().mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }.toList()
}
