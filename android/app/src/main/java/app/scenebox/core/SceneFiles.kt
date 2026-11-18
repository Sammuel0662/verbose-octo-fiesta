package app.scenebox.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 读取 Scene（com.omarea.vtools）的配置文件。
 * 全部经 ShellChannel 走 cat，因此在 T2(root) 下可用，T1 下会被拒。
 */
object SceneFiles {
    private const val DIR = "/data/data/com.omarea.vtools"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun read(channel: ShellChannel, relative: String): String? {
        val result = channel.execHost("cat $DIR/$relative")
        return if (result.code == 0 && result.has()) result.out else null
    }

    fun profile(channel: ShellChannel): JsonObject? = try {
        val text = read(channel, "files/profile.json")
        if (text.isNullOrBlank()) null
        else json.parseToJsonElement(text) as? JsonObject
    } catch (t: Throwable) {
        null
    }

    /** powercfg.xml / global.xml 这类 `<string name="k">v</string>` 的映射 */
    fun prefsStringMap(channel: ShellChannel, name: String): Map<String, String> {
        val text = read(channel, "shared_prefs/$name.xml") ?: return emptyMap()
        return Regex("<string name=\"([^\"]+)\">([^<]*)</string>")
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    /** games.xml 这类只关心 bool=true 的包名列表 */
    fun prefsTrueKeys(channel: ShellChannel, name: String): List<String> {
        val text = read(channel, "shared_prefs/$name.xml") ?: return emptyList()
        return Regex("<boolean name=\"([^\"]+)\" value=\"true\"")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
    }

    /** REFRESH_RATE.CONF：每行 `包名 最小帧 最大帧` */
    fun refreshRate(channel: ShellChannel): List<List<String>> {
        val text = read(channel, "files/REFRESH_RATE.CONF") ?: return emptyList()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.split(" ", "\t").filter { part -> part.isNotBlank() } }
            .filter { it.size == 3 }
            .map { it }
            .toList()
    }

    /** miui-thermal 下的温控预设名 */
    fun thermalPresets(channel: ShellChannel): List<String> {
        val result = channel.execHost("ls -1 $DIR/files/miui-thermal 2>/dev/null")
        if (result.code != 0 || !result.has()) return emptyList()
        return result.text().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }
}
