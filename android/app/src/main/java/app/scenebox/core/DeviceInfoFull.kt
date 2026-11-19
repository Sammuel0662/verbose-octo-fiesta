package app.scenebox.core

import android.content.Context
import android.os.Build

/**
 * 完整本机信息：除机型/芯片外，补内核、ABI、内存、存储、屏幕、GPU、温度传感器。
 * 全部走公开接口或公开 sysfs，不需要 root。
 */
object DeviceInfoFull {

    data class Info(
        val kernel: String,
        val abi: String,
        val memoryGb: String,
        val storageFreeGb: String,
        val resolution: String,
        val refreshRate: String,
        val gpu: String,
        val battery: String,
        val thermalZones: Int,
    ) {
        fun lines(): List<Pair<String, String>> = listOf(
            "内核" to kernel,
            "架构" to abi,
            "内存" to memoryGb,
            "可用存储" to storageFreeGb,
            "屏幕" to resolution,
            "刷新率" to refreshRate,
            "GPU" to gpu,
            "电池" to battery,
            "温控区" to "$thermalZones 个",
        )
    }

    fun collect(context: Context): Info {
        val kernel = shellText("uname -r").trim().ifBlank { "未知" }

        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "?"

        val memKb = shellText("grep MemTotal /proc/meminfo")
            .filter { it.isDigit() }.toLongOrNull() ?: 0
        val memory = if (memKb > 0) String.format("%.1f GB", memKb / 1024.0 / 1024.0) else "?"

        val dataDir = context.filesDir
        val freeGb = dataDir.usableSpace / 1024.0 / 1024.0 / 1024.0
        val storage = String.format("%.1f GB", freeGb)

        val metrics = context.resources.displayMetrics
        val resolution = "${metrics.widthPixels}×${metrics.heightPixels}"

        val refresh = try {
            val mode = context.display?.mode
            val hz = mode?.refreshRate ?: 0f
            if (hz > 0) String.format("%.0f Hz", hz) else "?"
        } catch (_: Throwable) {
            "?"
        }

        val gpu = shellText("cat /sys/class/kgsl/kgsl-3d0/gpu_model")
            .ifBlank { Build.HARDWARE ?: "?" }

        val battery = try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            "$pct%"
        } catch (_: Throwable) {
            "?"
        }

        val zones = shellText("ls -d /sys/class/thermal/thermal_zone* 2>/dev/null")
            .lines().count { it.isNotBlank() }

        return Info(kernel, abi, memory, storage, resolution, refresh, gpu, battery, zones)
    }

    private fun shellText(cmd: String): String = SuShell.exec(cmd, 8L).text()
}
