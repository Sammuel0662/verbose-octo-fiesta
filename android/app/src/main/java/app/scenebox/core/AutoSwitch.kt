package app.scenebox.core

import android.content.Context
import java.io.File

/**
 * 按应用自动切换：前台应用匹配到规则时，自动应用对应配方。
 *
 * 规则存 files/autoswitch.txt，一行一条：包名=配方名
 * 开关存 files/auto.on
 */
object AutoSwitch {

    data class Rule(val pkg: String, val recipe: String)

    private fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "auto").apply { mkdirs() }

    private fun rulesFile(context: Context) = File(dir(context), "autoswitch.txt")
    private fun flagFile(context: Context) = File(dir(context), "auto.on")

    fun enabled(context: Context): Boolean = try {
        flagFile(context).readText().trim() == "1"
    } catch (_: Throwable) {
        false
    }

    fun setEnabled(context: Context, on: Boolean) {
        runCatching { flagFile(context).writeText(if (on) "1" else "0") }
    }

    fun rules(context: Context): List<Rule> = try {
        val f = rulesFile(context)
        if (!f.exists()) emptyList()
        else f.readLines().mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null
            else Rule(line.substring(0, idx).trim(), line.substring(idx + 1).trim())
        }
    } catch (_: Throwable) {
        emptyList()
    }

    fun saveRules(context: Context, text: String) {
        runCatching { rulesFile(context).writeText(text) }
    }

    fun rulesText(context: Context): String = try {
        val f = rulesFile(context)
        if (f.exists()) f.readText() else ""
    } catch (_: Throwable) {
        ""
    }

    fun match(context: Context, pkg: String): String? =
        rules(context).firstOrNull { it.pkg == pkg }?.recipe
}
