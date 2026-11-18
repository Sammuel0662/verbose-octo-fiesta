package app.scenebox.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * 实时状态：判断"方案有没有生效"的第一手数据。
 *
 * 看三件事：
 *  1. governor 有没有变成方案里写的（schedutil / 原样）
 *  2. scaling_max_freq 的上限有没有降到方案的百分比档位
 *  3. 温度有没有变化（需要持续观察，单点没意义）
 */
object LiveStatus {

    data class PolicyState(val name: String, val cur: String, val max: String, val gov: String)

    data class Snapshot(
        val policies: List<PolicyState>,
        val temperature: String,
    ) {
        fun text(): String = buildString {
            for (p in policies) {
                append(p.name)
                append("  ")
                append(p.cur)
                append(" / ")
                append(p.max)
                append("  ")
                append(p.gov)
                append('\n')
            }
            append("温度 ")
            append(temperature)
        }
    }

    fun read(context: Context, fingerprint: DeviceFingerprint): Snapshot {
        val policies = mutableListOf<PolicyState>()
        if (fingerprint.policies.isNotEmpty()) {
            val script = fingerprint.policies.joinToString("; ") { policy ->
                val dir = "/sys/devices/system/cpu/cpufreq/${policy.name}"
                "echo \"${policy.name} \$(cat $dir/scaling_cur_freq 2>/dev/null) " +
                    "\$(cat $dir/scaling_max_freq 2>/dev/null) \$(cat $dir/scaling_governor 2>/dev/null)\""
            }
            val result = SuShell.exec(script, 15L)
            for (line in result.text().lineSequence()) {
                val parts = line.trim().split(" ")
                if (parts.size >= 4) {
                    policies += PolicyState(parts[0], parts[1], parts[2], parts[3])
                }
            }
        }
        return Snapshot(policies, temperature(context))
    }

    private fun temperature(context: Context): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (tenths > 0) String.format("%.1f℃", tenths / 10.0) else "—"
    }
}
