package app.scenebox.community

import android.content.Context
import app.scenebox.sbox.SboxStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 配置广场的数据源：一个托管在仓库里的静态 index.json（无后端）。
 * 走 jsDelivr CDN，失败再退回 raw.githubusercontent，国内可用性更好一点。
 */
object PlazaRepo {

    data class Item(
        val id: String,
        val name: String,
        val game: String,
        val soc: String,
        val author: String,
        val desc: String,
        val risk: String,
        val file: String,
    )

    data class Guide(val title: String, val source: String, val url: String)

    data class Index(val notice: String, val items: List<Item>, val guides: List<Guide>)

    private const val REPO = "Sammuel0662/verbose-octo-fiesta"
    private val indexUrls = listOf(
        "https://cdn.jsdelivr.net/gh/$REPO@main/community/index.json",
        "https://raw.githubusercontent.com/$REPO/main/community/index.json",
    )

    fun fetch(): Index {
        var last = "网络不可用"
        for (url in indexUrls) {
            try {
                return parse(httpGet(url, 15000))
            } catch (t: Throwable) {
                last = t.message ?: t.javaClass.simpleName
            }
        }
        throw IllegalStateException("无法获取配置广场：$last")
    }

    /** 下载某个配置包到 downloads 目录，返回落地文件 */
    fun download(context: Context, item: Item): File {
        val bytes = httpGetBytes(item.file, 30000)
        val dir = SboxStore.downloadsDir(context)
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, "${item.id}.sbox")
        target.writeBytes(bytes)
        return target
    }

    private fun httpGet(url: String, timeoutMs: Int): String =
        String(httpGetBytes(url, timeoutMs), Charsets.UTF_8)

    private fun httpGetBytes(url: String, timeoutMs: Int): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String): Index {
        val root = Json.parseToJsonElement(text) as? JsonObject
            ?: throw IllegalStateException("索引格式错误")
        fun str(obj: JsonObject, key: String): String =
            (obj[key] as? JsonPrimitive)?.contentOrNull ?: ""

        val items = (root["items"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.map {
                Item(
                    id = str(it, "id"),
                    name = str(it, "name"),
                    game = str(it, "game"),
                    soc = str(it, "soc"),
                    author = str(it, "author"),
                    desc = str(it, "desc"),
                    risk = str(it, "risk"),
                    file = str(it, "file"),
                )
            } ?: emptyList()

        val guides = (root["guides"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.map { Guide(str(it, "title"), str(it, "source"), str(it, "url")) }
            ?: emptyList()

        return Index(str(root, "notice"), items, guides)
    }
}
