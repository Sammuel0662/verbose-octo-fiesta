package app.scenebox.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * 帧率 + 温度采样。
 *
 * FPS 数据源：`dumpsys gfxinfo <包名>` 的 "Total frames rendered" 累计值。
 * 每次采样取一次累计帧数，用「两次差值 / 时间差」算真实 FPS。
 *
 * 为什么不用 SurfaceFlinger --latency：本机 Android 16 上它只返回刷新周期
 * （16666666），拿不到每帧时间戳，实测不可用。
 */
object FpsSampler {

    /** 当前前台应用包名 */
    fun foregroundPackage(): String? {
        val result = SuShell.exec("dumpsys window 2>/dev/null | grep -m1 mCurrentFocus", 12L)
        val text = result.text()
        if (text.isBlank()) return null
        // 形如 Window{abc u0 com.tencent.tmgp.sgame/com.tencent...Main}
        val match = Regex("([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+)/").find(text)
        return match?.groupValues?.get(1)
    }

    /** 累计渲染帧数（拿不到返回 -1） */
    fun totalFrames(pkg: String): Long {
        val result = SuShell.exec("dumpsys gfxinfo $pkg framestats 2>/dev/null", 15L)
        val match = Regex("Total frames rendered:\\s*(\\d+)").find(result.text())
        return match?.groupValues?.get(1)?.toLongOrNull() ?: -1
    }

    /** 帧耗时中位数（ms），用于交叉验证，拿不到返回 0 */
    fun medianFrameMs(pkg: String): Int {
        val result = SuShell.exec("dumpsys gfxinfo $pkg 2>/dev/null", 12L)
        val match = Regex("50th percentile:\\s*(\\d+)ms").find(result.text())
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    fun temperature(context: Context): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (tenths > 0) tenths / 10f else 0f
    }
}
