package app.scenebox.core

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 采样结果落盘 + 前后对比。
 *
 * 每次采样存一条记录（时间、包名、均值、1% low、jank、最高温、逐秒帧率），
 * 下次采样时和"同一应用的上一条"对比，给出差值。
 */
object SampleStore {

    data class Record(
        val id: String,
        val time: String,
        val pkg: String,
        val avgFps: Int,
        val lowFps: Int,
        val jankPercent: Float,
        val maxTemp: Float,
        val minTemp: Float,
        val governor: String,
        val maxFreqs: String,
    )

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.US)

    private fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "samples").apply { mkdirs() }

    fun save(context: Context, record: Record): Boolean = try {
        val obj = buildJsonObject {
            put("id", JsonPrimitive(record.id))
            put("time", JsonPrimitive(record.time))
            put("pkg", JsonPrimitive(record.pkg))
            put("avgFps", JsonPrimitive(record.avgFps))
            put("lowFps", JsonPrimitive(record.lowFps))
            put("jankPercent", JsonPrimitive(record.jankPercent))
            put("maxTemp", JsonPrimitive(record.maxTemp))
            put("minTemp", JsonPrimitive(record.minTemp))
            put("governor", JsonPrimitive(record.governor))
            put("maxFreqs", JsonPrimitive(record.maxFreqs))
        }
        File(dir(context), "${record.id}.json").writeText(json.encodeToString(JsonObject.serializer(), obj))
        true
    } catch (_: Throwable) {
        false
    }

    fun list(context: Context): List<Record> = try {
        dir(context).listFiles { f -> f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { parse(it) }
            ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    private fun parse(file: File): Record? = try {
        val obj = json.parseToJsonElement(file.readText()).jsonObject
        fun str(k: String) = obj[k]?.jsonPrimitive?.contentOrNull ?: ""
        fun int(k: String) = str(k).toIntOrNull() ?: 0
        fun fl(k: String) = str(k).toFloatOrNull() ?: 0f
        Record(
            id = str("id"),
            time = str("time"),
            pkg = str("pkg"),
            avgFps = int("avgFps"),
            lowFps = int("lowFps"),
            jankPercent = fl("jankPercent"),
            maxTemp = fl("maxTemp"),
            minTemp = fl("minTemp"),
            governor = str("governor"),
            maxFreqs = str("maxFreqs"),
        )
    } catch (_: Throwable) {
        null
    }

    /** 和同应用的上一条记录对比，返回可直接显示的文字 */
    fun compareWithPrevious(context: Context, current: Record): String {
        val previous = list(context).firstOrNull { it.pkg == current.pkg && it.id != current.id }
            ?: return "这是 ${current.pkg} 的第一条记录，暂无对比"
        val dAvg = current.avgFps - previous.avgFps
        val dLow = current.lowFps - previous.lowFps
        val dTemp = current.maxTemp - previous.maxTemp
        val dJank = current.jankPercent - previous.jankPercent
        fun sign(v: Number) = if (v.toFloat() >= 0) "+$v" else "$v"
        return buildString {
            append("对比上次（${previous.time}）\n")
            append("均值 ${previous.avgFps}→${current.avgFps} (${sign(dAvg)})\n")
            append("1% low ${previous.lowFps}→${current.lowFps} (${sign(dLow)})\n")
            append("jank ${previous.jankPercent}%→${current.jankPercent}% (${sign(dJank)})\n")
            append("最高温 ${previous.maxTemp}→${current.maxTemp}℃ (${sign(dTemp)})")
        }
    }
}
