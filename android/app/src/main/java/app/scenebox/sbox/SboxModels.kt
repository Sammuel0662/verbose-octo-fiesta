package app.scenebox.sbox

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * .sbox v1 数据模型。规范见 spec/sbox-v1.md。
 *
 * 灵活性优先：payload 里 Scene 相关结构用 JsonObject/JsonElement 承载，
 * 避免 Scene 升级多加一个字段就解析失败。
 */
@Serializable
data class SboxBundle(
    val format: String = "",
    @SerialName("format_version") val formatVersion: Int = 1,
    val id: String = "",
    val meta: SboxMeta = SboxMeta(),
    val target: SboxTarget = SboxTarget(),
    val payload: SboxPayload = SboxPayload(),
    val analysis: SboxAnalysis? = null,
    val checksum: SboxChecksum? = null,
)

@Serializable
data class SboxMeta(
    val name: String = "",
    val author: String = "",
    val description: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val generator: String = "",
    val tags: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class SboxTarget(
    val device: SboxDevice = SboxDevice(),
    val soc: SboxSoc = SboxSoc(),
    val os: SboxOs = SboxOs(),
    val scene: SboxScene = SboxScene(),
    val cpu: SboxCpu = SboxCpu(),
)

@Serializable
data class SboxDevice(
    val brand: String = "",
    val model: String = "",
    val device: String = "",
    val manufacturer: String = "",
)

@Serializable
data class SboxSoc(
    val model: String = "",
    val platform: String = "",
    val manufacturer: String = "",
)

@Serializable
data class SboxOs(
    val android: String = "",
    val sdk: String = "",
    val rom: String = "",
    val kernel: String = "",
)

@Serializable
data class SboxScene(
    val daemon: String = "",
    @SerialName("cloud_profile_version") val cloudProfileVersion: JsonElement? = null,
    @SerialName("working_mode") val workingMode: String = "",
)

@Serializable
data class SboxCpu(
    val policies: Map<String, SboxPolicy> = emptyMap(),
)

@Serializable
data class SboxPolicy(
    val min: Long = 0,
    val max: Long = 0,
    val avail: List<Long> = emptyList(),
)

@Serializable
data class SboxPayload(
    val components: Map<String, Boolean> = emptyMap(),
    val profile: JsonObject? = null,
    @SerialName("app_policy") val appPolicy: JsonObject? = null,
    val games: List<String> = emptyList(),
    @SerialName("refresh_rate") val refreshRate: List<JsonObject> = emptyList(),
    @SerialName("per_app") val perApp: List<JsonObject> = emptyList(),
    val thermal: List<String> = emptyList(),
)

@Serializable
data class SboxAnalysis(
    val risk: String = "unknown",
    val items: List<SboxIssue> = emptyList(),
    val stats: JsonObject? = null,
)

@Serializable
data class SboxIssue(
    val level: String = "info",
    val code: String = "",
    val message: String = "",
)

@Serializable
data class SboxChecksum(
    val algo: String = "",
    val value: String = "",
)

object SboxIO {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    fun parse(text: String): SboxBundle = json.decodeFromString(SboxBundle.serializer(), text)

    fun parseFile(file: java.io.File): SboxBundle = parse(file.readText())

    /**
     * 校验完整性。
     *
     * TODO(v0.2)：canonical JSON 的 sha256 需要在 Kotlin 侧复刻 Python 引擎的规则
     * （sort_keys + 紧凑分隔符 + UTF-8），当前先返回 "未校验"。
     */
    fun verifyChecksum(bundle: SboxBundle): String {
        val c = bundle.checksum ?: return "无校验和"
        return if (c.value.isBlank()) "无校验和" else "有校验和（v0.1 暂未本地比对）"
    }
}
