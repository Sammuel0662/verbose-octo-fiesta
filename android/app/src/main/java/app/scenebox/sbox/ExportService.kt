package app.scenebox.sbox

import android.content.Context
import app.scenebox.core.DeviceFingerprint
import app.scenebox.core.SceneFiles
import app.scenebox.core.SuShell
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.text.Charsets

/**
 * 导出：读 Scene 当前配置 -> 打包 .sbox -> 落到 App 外部目录。
 * 目标：/sdcard/Android/data/app.scenebox/files/sbox/<id>.sbox
 */
object ExportService {

    data class Result(
        val ok: Boolean,
        val message: String,
        val path: String = "",
        val writes: Int = 0,
        val risk: String = "",
    )

    fun export(
        context: Context,
        fingerprint: DeviceFingerprint,
        name: String,
        game: String,
        style: String,
        description: String,
    ): Result {
        val profile = SceneFiles.profile(SuShell)
            ?: return Result(false, "读不到 Scene 的 profile.json：请先在 KernelSU/Magisk 里给 SceneBox 授权 root")

        val globals = SceneFiles.prefsStringMap(SuShell, "global")
        val appPolicy = SceneFiles.prefsStringMap(SuShell, "powercfg")
        val games = SceneFiles.prefsTrueKeys(SuShell, "games")
        val refresh = SceneFiles.refreshRate(SuShell)
        val analysis = SboxExpander.analyze(profile)

        val components = linkedMapOf(
            "profile" to true,
            "app_policy" to true,
            "games" to true,
            "refresh_rate" to true,
            "per_app" to false,
            "thermal" to false,
        )

        val bundle = SboxBuilder.build(
            name = name.ifBlank { "本机 Scene 调度备份" },
            author = "",
            description = description,
            game = game,
            style = style,
            fingerprint = fingerprint,
            sceneDaemon = globals["daemon_version_name"] ?: "",
            sceneWorkingMode = globals["working_mode"] ?: "",
            profile = profile,
            appPolicy = appPolicy,
            games = games,
            refreshRate = refresh,
            components = components,
            analysis = analysis,
        )

        val id = (bundle["id"] as? JsonPrimitive)?.content ?: SboxBuilder.newId()
        val dir = File(context.getExternalFilesDir(null), "sbox")
        if (!dir.exists() && !dir.mkdirs()) {
            return Result(false, "无法创建目录：${dir.absolutePath}")
        }
        val file = File(dir, "$id.sbox")
        return try {
            file.writeText(SboxBuilder.serialize(bundle), Charsets.UTF_8)
            Result(
                ok = true,
                message = "已导出 → ${file.absolutePath}\n" +
                    "写入 ${analysis.writes} 条 / 节点 ${analysis.paths} 个 / 风险 ${analysis.risk}",
                path = file.absolutePath,
                writes = analysis.writes,
                risk = analysis.risk,
            )
        } catch (t: Throwable) {
            Result(false, "写入失败：${t.message}")
        }
    }
}
