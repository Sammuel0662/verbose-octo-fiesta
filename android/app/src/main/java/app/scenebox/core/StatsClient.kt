package app.scenebox.core

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 匿名统计（在线 / 今日活跃 / 累计用户）。
 *
 * 隐私设计：
 *  - 只上报一个本机生成的短 ID（SHA-256(ANDROID_ID + 包名) 取前 16 位），不含任何可识别信息
 *  - 默认关闭，用户主动开启才上报
 *  - 没有配置服务端地址时全部静默跳过
 */
object StatsClient {

    /** 部署完 Worker 后把地址填这里，例如 https://scenebox-stats.xxx.workers.dev */
    const val ENDPOINT = ""

    data class Stats(val online: Int, val dau: Int, val total: Int)

    fun configured(): Boolean = ENDPOINT.startsWith("http")

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("scenebox", Context.MODE_PRIVATE)
            .getBoolean("stats_opt_in", false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences("scenebox", Context.MODE_PRIVATE)
            .edit().putBoolean("stats_opt_in", on).apply()
    }

    fun deviceId(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull() ?: "unknown"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((androidId + "|" + context.packageName).toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    fun heartbeat(context: Context): Boolean {
        if (!configured() || !enabled(context)) return false
        return runCatching {
            val conn = URL("$ENDPOINT/hb").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().apply {
                put("id", deviceId(context))
                put("v", "0.4.5")
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        }.getOrDefault(false)
    }

    fun fetch(): Stats? {
        if (!configured()) return null
        return runCatching {
            val conn = URL("$ENDPOINT/stats").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val obj = JSONObject(text)
            Stats(obj.optInt("online"), obj.optInt("dau"), obj.optInt("total"))
        }.getOrNull()
    }
}
