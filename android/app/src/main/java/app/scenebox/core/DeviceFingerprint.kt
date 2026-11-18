package app.scenebox.core

import android.os.Build
import java.io.File

data class CpuPolicy(
    val name: String,
    val min: Long,
    val max: Long,
    val avail: List<Long>,
)

/**
 * 本机指纹。全部走公开可读路径，T0 就能采集完整（这也是"无 root 也能校验"的前提）。
 */
data class DeviceFingerprint(
    val brand: String,
    val model: String,
    val device: String,
    val socModel: String,
    val socPlatform: String,
    val android: String,
    val sdk: Int,
    val rom: String,
    val policies: List<CpuPolicy>,
) {
    /** 本机所有可用频点的并集 */
    val allFreqs: Set<Long>
        get() = policies.flatMap { it.avail }.toSet()

    /** 本机频点上限（取最大的 cpuinfo_max_freq） */
    val maxFreq: Long
        get() = policies.maxOfOrNull { it.max } ?: 0L

    companion object {
        fun collect(): DeviceFingerprint {
            val freqDir = File("/sys/devices/system/cpu/cpufreq")
            val policies = (freqDir.listFiles() ?: emptyArray<File>())
                .filter { it.isDirectory && it.name.startsWith("policy") }
                .sortedBy { it.name }
                .mapNotNull { dir ->
                    val avail = readLongs(File(dir, "scaling_available_frequencies"))
                    if (avail.isEmpty()) return@mapNotNull null
                    val min = readLong(File(dir, "cpuinfo_min_freq")).takeIf { it > 0 } ?: avail.first()
                    val max = readLong(File(dir, "cpuinfo_max_freq")).takeIf { it > 0 } ?: avail.last()
                    CpuPolicy(dir.name, min, max, avail)
                }

            val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL ?: ""
            } else {
                ""
            }

            return DeviceFingerprint(
                brand = Build.BRAND ?: "",
                model = Build.MODEL ?: "",
                device = Build.DEVICE ?: "",
                socModel = socModel,
                socPlatform = Build.BOARD ?: "",
                android = Build.VERSION.RELEASE ?: "",
                sdk = Build.VERSION.SDK_INT,
                rom = Build.DISPLAY ?: "",
                policies = policies,
            )
        }

        private fun readTextSafe(f: File): String = try {
            f.readText().trim()
        } catch (t: Throwable) {
            ""
        }

        private fun readLong(f: File): Long = readTextSafe(f).toLongOrNull() ?: 0L

        private fun readLongs(f: File): List<Long> =
            readTextSafe(f).split(' ', '\n').mapNotNull { it.trim().toLongOrNull() }
    }
}
