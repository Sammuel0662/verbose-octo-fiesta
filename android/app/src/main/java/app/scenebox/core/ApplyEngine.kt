package app.scenebox.core

/**
 * 统一的应用入口：根据 Scene 是否在自动调度，选择写入方式。
 *
 * - Scene 会下发 → 写 profile 补丁，让它自己执行（避免被覆盖）
 * - Scene 不下发 → 直接写内核，写完回读核验，不一致自动回退
 */
object ApplyEngine {

    data class Result(val ok: Boolean, val message: String)

    fun apply(patch: List<Pair<String, String>>, fingerprint: DeviceFingerprint): Result {
        if (patch.isEmpty()) return Result(false, "没有要应用的项")

        return if (SceneState.detect().willApply) {
            val raw = SceneFiles.profile(SuShell)
            if (raw == null) {
                Result(false, "读不到 Scene 的 profile（需要 root）")
            } else {
                val patched = ProfileService.patch(raw, patch, fingerprint)
                if (patched.changes.isEmpty()) {
                    Result(false, "没有可应用的项")
                } else {
                    val written = ImportService.writeProfile(patched.profile)
                    if (written.ok) {
                        Result(true, "已写入 Scene 方案：${patched.changes.take(3).joinToString("、")}")
                    } else {
                        Result(false, written.message)
                    }
                }
            }
        } else {
            val r = SysfsApply.apply(patch, fingerprint)
            Result(r.ok, r.message)
        }
    }
}
