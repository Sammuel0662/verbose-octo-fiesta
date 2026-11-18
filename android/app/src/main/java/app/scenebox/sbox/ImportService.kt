package app.scenebox.sbox

import app.scenebox.core.Capability
import app.scenebox.core.DeviceFingerprint
import app.scenebox.core.SuShell
import app.scenebox.core.shQuote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把 .sbox 里的调度方案写回 Scene。
 *
 * 安全策略：
 *  1. 只写 files/profile.json（每应用档位 powercfg.xml 是 SharedPreferences，
 *     运行时被 Scene 进程持有，直接改会被覆盖甚至写坏，v0.3 再做）；
 *  2. 写之前强制备份到 profile.json.sboxbak-<时间戳>；
 *  3. 先写临时文件再 mv，避免写一半断电留下残文件；
 *  4. 保留原文件属主与权限，防止 Scene 重启后读写失败；
 *  5. 路径在应用命名空间里被隐藏，必须走 nsenter（execHost）。
 */
object ImportService {

    private const val TARGET = "/data/data/com.omarea.vtools/files/profile.json"
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    data class Result(
        val ok: Boolean,
        val message: String,
        val backupName: String = "",
        val sizeText: String = "",
    )

    fun apply(
        bundle: SboxBundle,
        fingerprint: DeviceFingerprint?,
        capability: Capability,
    ): Result {
        if (!capability.canApplyConfig) {
            return Result(false, "需要 root：当前层级 ${capability.tier.label}")
        }

        val profile = bundle.payload.profile
            ?: return Result(false, "这个包里没有调度方案（profile 为空），没有可导入的内容")

        // 导入前按本机再校验一次，绝不盲写
        if (fingerprint != null) {
            val verdict = SboxValidator.validate(bundle, fingerprint)
            if (verdict.verdict == Verdict.BLOCK) {
                val reason = verdict.issues.firstOrNull { it.level == Level.BLOCK }?.message ?: "不兼容"
                return Result(false, "已拦截：$reason")
            }
        }

        val text = pretty.encodeToString(JsonObject.serializer(), profile)
        val base64 = android.util.Base64.encodeToString(text.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val script = "set -e; " +
            "T=${shQuote(TARGET)}; " +
            "B=\"\$T.sboxbak-$stamp\"; " +
            "cp -f \"\$T\" \"\$B\"; " +
            "printf %s ${shQuote(base64)} | base64 -d > \"\$T.tmp\"; " +
            "chown \$(stat -c %u:%g \"\$T\") \"\$T.tmp\"; " +
            "chmod \$(stat -c %a \"\$T\") \"\$T.tmp\"; " +
            "mv -f \"\$T.tmp\" \"\$T\"; " +
            "echo IMPORTED; " +
            "wc -c < \"\$T\""

        val result = SuShell.execHost(script, 30L)
        val out = result.text()
        if (!out.contains("IMPORTED")) {
            return Result(false, "写入失败：${out.take(120).ifBlank { "无输出" }}")
        }
        val size = out.lineSequence().lastOrNull()?.trim() ?: "?"

        return Result(
            ok = true,
            message = "已导入到 Scene（${size} 字节）",
            backupName = "profile.json.sboxbak-$stamp",
            sizeText = "$size",
        )
    }
}
