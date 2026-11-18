package app.scenebox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scenebox.core.DeviceFingerprint
import app.scenebox.sbox.SboxBundle
import app.scenebox.sbox.SboxExpander
import app.scenebox.sbox.SboxValidator
import app.scenebox.sbox.Verdict
import kotlinx.serialization.json.JsonObject

/**
 * 导入前预览：把 .sbox 里的调度方案展开成真实写入，并对本机做强校验。
 * 全程不需要 root —— 校验只依赖本机公开可读的频点表。
 */
@Composable
fun PreviewScreen(
    bundle: SboxBundle,
    fingerprint: DeviceFingerprint?,
    fileName: String,
    canImport: Boolean,
    importBusy: Boolean,
    importMessage: String,
    importedOk: Boolean,
    importBackup: String,
    importSize: String,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onRollback: () -> Unit,
) {
    val expanded = remember(bundle) {
        bundle.payload.profile?.let { SboxExpander.expand(it) } ?: emptyList()
    }
    val result = remember(bundle, fingerprint) {
        fingerprint?.let { SboxValidator.validate(bundle, it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("← 返回") }
        }

        Text(
            bundle.meta.name.ifBlank { "(未命名配置)" },
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "$fileName · ${bundle.meta.generator}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Section("适配对比") {
            KeyValue("模板机型", "${bundle.target.device.brand} ${bundle.target.device.model}")
            KeyValue("模板芯片", "${bundle.target.soc.platform} ${bundle.target.soc.model}")
            KeyValue("模板系统", "Android ${bundle.target.os.android}")
            if (fingerprint != null) {
                KeyValue("本机机型", "${fingerprint.brand} ${fingerprint.model}")
                KeyValue("本机芯片", "${fingerprint.socPlatform} ${fingerprint.socModel}")
            }
        }

        Section("方案规模") {
            KeyValue("写入 sysfs", "${expanded.count { it.path != null }} 条")
            KeyValue("原生操作", "${expanded.count { it.path == null }} 条")
            KeyValue("涉及节点", "${expanded.mapNotNull { it.path }.distinct().size} 个")
            val schemes = bundle.payload.profile?.get("schemes") as? JsonObject
            KeyValue("档位数", "${schemes?.size ?: 0}")
        }

        Section("写入清单（前 10 条）") {
            if (expanded.isEmpty()) {
                Text("该配置不含调度方案（profile 为空）", fontSize = 12.sp)
            } else {
                for (write in expanded.take(10)) {
                    val path = write.path ?: write.op ?: "-"
                    Text("$path  ←  ${write.value}", fontSize = 11.sp)
                }
                if (expanded.size > 10) {
                    Text("…… 其余 ${expanded.size - 10} 条", fontSize = 11.sp)
                }
            }
        }

        if (importedOk) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "✓",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Text(
                            "  导入成功",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (importSize.isNotBlank()) {
                        Text("已写入 Scene 的 profile.json（$importSize 字节）", fontSize = 12.sp)
                    }
                    if (importBackup.isNotBlank()) {
                        Text(
                            "原配置备份：$importBackup",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "下一步：回 Scene 重新应用一次档位或重启，让新方案生效。",
                        fontSize = 12.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBack) { Text("完成", fontSize = 13.sp) }
                        TextButton(onClick = onRollback) { Text("撤销导入", fontSize = 13.sp) }
                    }
                }
            }
        }

        Section("导入到 Scene") {
            Text(
                if (canImport) "会先备份原 profile.json，再整体替换；Scene 需重新应用档位后生效"
                else "当前权限不足，导入需要 root",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onImport, enabled = canImport && !importBusy) {
                Text(if (importBusy) "导入中…" else "导入到 Scene（自动备份）")
            }
            if (importMessage.isNotBlank()) {
                Text(importMessage, fontSize = 12.sp)
            }
        }

        Section("本机校验") {
            if (result == null) {
                Text("缺少本机指纹，无法校验", fontSize = 12.sp)
            } else {
                val color = when (result.verdict) {
                    Verdict.PASS -> MaterialTheme.colorScheme.primary
                    Verdict.CONFIRM -> MaterialTheme.colorScheme.tertiary
                    Verdict.BLOCK -> MaterialTheme.colorScheme.error
                }
                Text(
                    result.verdict.label,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                for (issue in result.issues) {
                    Text("· [${issue.code}] ${issue.message}", fontSize = 12.sp)
                }
                if (result.issues.isEmpty()) {
                    Text("· 未发现不兼容项", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            content()
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp)
    }
}
