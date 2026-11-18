package app.scenebox.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * 帧率 + 温度采样。
 *
 * FPS 取 SurfaceFlinger 的真实出帧时间戳：`--latency <layer>` 会返回最近一批
 * "期望/实际/就绪" 三个时间戳，用实际时间戳的首尾跨度和帧数算真帧率，
 * 比读刷新率或估算靠谱。
 */
object FpsSampler {

    /** 挑一个正在出帧的图层：优先 SurfaceView（游戏基本都用它） */
    fun detectLayer(): String? {
        val result = SuShell.exec("dumpsys SurfaceFlinger --list 2>/dev/null", 12L)
        if (!result.has()) return null
        val layers = result.text().lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Display") }
            .toList()
        if (layers.isEmpty()) return null
        return layers.firstOrNull { it.contains("SurfaceView", ignoreCase = true) }
            ?: layers.lastOrNull()
    }

    fun fps(layer: String): Int? {
        val quoted = "'" + layer.replace("'", "'\\''") + "'"
        val result = SuShell.exec("dumpsys SurfaceFlinger --latency $quoted 2>/dev/null", 12L)
        val lines = result.text().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size < 3) return null

        val stamps = lines.drop(1).mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 3) parts[1].toLongOrNull() else null
        }.filter { it > 0 && it < Long.MAX_VALUE / 2 }

        if (stamps.size < 2) return null
        val span = stamps.last() - stamps.first()
        if (span <= 0) return null
        val fps = ((stamps.size - 1).toDouble() * 1_000_000_000.0 / span).toInt()
        return fps.coerceIn(0, 240)
    }

    fun temperature(context: Context): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (tenths > 0) tenths / 10f else 0f
    }

    /** 大核当前频率（kHz），用来佐证"有没有降频" */
    fun bigCoreFreq(fingerprint: DeviceFingerprint): Int {
        val last = fingerprint.policies.lastOrNull() ?: return 0
        val result = SuShell.exec(
            "cat /sys/devices/system/cpu/cpufreq/${last.name}/scaling_cur_freq 2>/dev/null",
            8L,
        )
        return result.text().trim().toIntOrNull() ?: 0
    }
}
