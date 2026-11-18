package app.scenebox.sbox

import app.scenebox.core.DeviceFingerprint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * .sbox 打包。checksum 的算法必须和 engine/sboxfile.py 一致：
 * 除 checksum 外全部字段 -> 按 key 排序 -> 紧凑分隔符 -> UTF-8 -> sha256。
 */
object SboxBuilder {
    const val FORMAT = "sbox"
    const val FORMAT_VERSION = 1
    const val GENERATOR = "SceneBox 0.2.0 (android)"

    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun canonical(element: JsonElement, sb: StringBuilder = StringBuilder()): String {
        when (element) {
            is JsonObject -> {
                sb.append('{')
                element.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
                    if (index > 0) sb.append(',')
                    sb.append(JsonPrimitive(entry.key).toString())
                    sb.append(':')
                    canonical(entry.value, sb)
                }
                sb.append('}')
            }

            is JsonArray -> {
                sb.append('[')
                element.forEachIndexed { index, child ->
                    if (index > 0) sb.append(',')
                    canonical(child, sb)
                }
                sb.append(']')
            }

            is JsonPrimitive -> sb.append(element.toString())
            else -> sb.append(element.toString())
        }
        return sb.toString()
    }

    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    fun newId(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val rand = (0 until 3).joinToString("") {
            Integer.toHexString((Math.random() * 256).toInt() and 0xFF).padStart(2, '0')
        }
        return "sbox_${stamp}_$rand"
    }

    fun build(
        name: String,
        author: String,
        description: String,
        game: String,
        style: String,
        fingerprint: DeviceFingerprint,
        sceneDaemon: String,
        sceneWorkingMode: String,
        profile: JsonObject?,
        appPolicy: Map<String, String>,
        games: List<String>,
        refreshRate: List<List<String>>,
        components: Map<String, Boolean>,
        analysis: ProfileAnalysis?,
    ): JsonObject {
        val manifest = buildJsonObject {
            put("format", FORMAT)
            put("format_version", FORMAT_VERSION)
            put("id", newId())

            putJsonObject("meta") {
                put("name", name)
                put("author", author)
                put("description", description)
                put("created_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()))
                put("generator", GENERATOR)
                putJsonObject("tags") {
                    putJsonArray("game") { if (game.isNotBlank()) add(game) }
                    putJsonArray("style") { if (style.isNotBlank()) add(style) }
                    putJsonArray("soc") { if (fingerprint.socModel.isNotBlank()) add(fingerprint.socModel) }
                    putJsonArray("device") { if (fingerprint.model.isNotBlank()) add(fingerprint.model) }
                }
            }

            putJsonObject("target") {
                putJsonObject("device") {
                    put("brand", fingerprint.brand)
                    put("model", fingerprint.model)
                    put("device", fingerprint.device)
                    put("manufacturer", fingerprint.brand)
                }
                putJsonObject("soc") {
                    put("model", fingerprint.socModel)
                    put("platform", fingerprint.socPlatform)
                    put("manufacturer", "")
                }
                putJsonObject("os") {
                    put("android", fingerprint.android)
                    put("sdk", fingerprint.sdk.toString())
                    put("rom", fingerprint.rom)
                    put("kernel", "")
                }
                putJsonObject("scene") {
                    put("daemon", sceneDaemon)
                    put("cloud_profile_version", 0)
                    put("working_mode", sceneWorkingMode)
                }
                putJsonObject("cpu") {
                    putJsonObject("policies") {
                        fingerprint.policies.forEach { policy ->
                            putJsonObject(policy.name) {
                                put("min", policy.min)
                                put("max", policy.max)
                                putJsonArray("avail") { policy.avail.forEach { add(it) } }
                            }
                        }
                    }
                }
            }

            putJsonObject("payload") {
                putJsonObject("components") {
                    components.forEach { (key, value) -> put(key, value) }
                }
                put("profile", profile ?: JsonNull)
                putJsonObject("app_policy") {
                    appPolicy.forEach { (key, value) -> put(key, value) }
                }
                putJsonArray("games") { games.forEach { add(it) } }
                putJsonArray("refresh_rate") {
                    refreshRate.forEach { row ->
                        putJsonObject {
                            put("package", row.getOrElse(0) { "" })
                            put("min", row.getOrElse(1) { "0" }.toIntOrNull() ?: 0)
                            put("max", row.getOrElse(2) { "0" }.toIntOrNull() ?: 0)
                        }
                    }
                }
                putJsonArray("per_app") { }
                putJsonArray("thermal") { }
            }

            if (analysis != null) {
                putJsonObject("analysis") {
                    put("risk", analysis.risk)
                    putJsonArray("items") {
                        analysis.items.forEach { (level, message) ->
                            putJsonObject {
                                put("level", level)
                                put("code", "ANDROID_" + level.uppercase(Locale.US))
                                put("message", message)
                            }
                        }
                    }
                    putJsonObject("stats") {
                        put("writes", analysis.writes)
                        put("paths", analysis.paths)
                        put("presets", analysis.presets)
                        put("schemes", analysis.schemes)
                        put("ops", 0)
                    }
                }
            }
        }

        val checksum = sha256(canonical(manifest))
        val merged = manifest.toMutableMap()
        merged["checksum"] = buildJsonObject {
            put("algo", "sha256")
            put("value", checksum)
        }
        return JsonObject(merged)
    }

    fun serialize(bundle: JsonObject): String =
        pretty.encodeToString(JsonObject.serializer(), bundle)
}
