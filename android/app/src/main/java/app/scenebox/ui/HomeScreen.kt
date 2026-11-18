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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scenebox.core.Capability
import app.scenebox.core.DeviceFingerprint
import app.scenebox.sbox.SboxStore
import java.io.File

@Composable
fun HomeScreen(
    cap: Capability?,
    fp: DeviceFingerprint?,
    busy: Boolean,
    exportBusy: Boolean,
    exportMessage: String,
    onRequestShizuku: () -> Unit,
    onProbeRoot: () -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    files: List<File>,
    onOpenFile: (File) -> Unit,
    snapshotMessage: String,
    onSnapshotSave: (String) -> Unit,
    onSnapshotRestore: (String) -> Unit,
    noticeDismissed: Boolean,
    onDismissNotice: () -> Unit,
    onOpenGuide: () -> Unit,
    cleanupMessage: String,
    onCleanup: (String) -> Unit,
) {
    var pendingCleanup by remember { mutableStateOf<String?>(null) }

    pendingCleanup?.let { action ->
        ConfirmDialog(
            action = action,
            onDismiss = { pendingCleanup = null },
            onConfirm = {
                onCleanup(action)
                pendingCleanup = null
            },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SceneBox", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Scene 配置分发 · 导入前校验 · 调度快照",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (!noticeDismissed) {
            Section("公告") {
                Text(
                    "v0.3.0：支持导出 .sbox、导入到 Scene、快照 A/B。\n" +
                        "导入前会自动备份原配置，随时可以在「清理与还原」里一键撤销。",
                    fontSize = 12.sp,
                )
                TextButton(onClick = onDismissNotice) {
                    Text("知道了，不再提示", fontSize = 12.sp)
                }
            }
        }

        TextButton(onClick = onOpenGuide) { Text("使用教程 →") }

        if (cap != null) {
            Section("权限层级") {
                Text(cap.tier.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    cap.tier.hint,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (line in cap.evidence) {
                    Text("· $line", fontSize = 12.sp)
                }
            }

            Section("功能可用性") {
                FeatureRow("本机适配校验", cap.canValidate)
                FeatureRow("预览配置参数", true)
                FeatureRow("导出 Scene 配置", cap.canExportScene)
                FeatureRow("导入 / 快照恢复", cap.canApplyConfig)
                FeatureRow("帧率采样", cap.canRecordFps)
            }

            Section("配置导出") {
                Text(
                    if (cap.canExportScene) {
                        "读取 Profile + 每应用档位 + 游戏列表 + 帧率覆盖，打包成 .sbox"
                    } else {
                        "需要 root：读不到 /data/data/com.omarea.vtools"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onExport,
                    enabled = cap.canExportScene && !exportBusy,
                ) {
                    Text(if (exportBusy) "导出中…" else "导出 .sbox")
                }
                if (exportMessage.isNotBlank()) {
                    Text(exportMessage, fontSize = 12.sp)
                }
            }

            Section("调度快照 A/B") {
                Text(
                    "A = 游戏调度，B = 日常默认。进游戏前恢复 A，退出后恢复 B。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (label in listOf("A", "B")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (label == "A") "快照 A · 游戏" else "快照 B · 日常",
                            fontSize = 13.sp,
                        )
                        TextButton(
                            onClick = { onSnapshotSave(label) },
                            enabled = cap.canSnapshotRestore,
                        ) { Text("保存", fontSize = 12.sp) }
                        TextButton(
                            onClick = { onSnapshotRestore(label) },
                            enabled = cap.canSnapshotRestore,
                        ) { Text("恢复", fontSize = 12.sp) }
                    }
                }
                if (snapshotMessage.isNotBlank()) {
                    Text(snapshotMessage, fontSize = 12.sp)
                }
            }

            Section("已导出的配置") {
                if (files.isEmpty()) {
                    Text(
                        "还没有导出过配置",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    for (file in files.take(8)) {
                        TextButton(onClick = { onOpenFile(file) }) {
                            Text("${file.name}  ·  ${SboxStore.sizeText(file)}", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            Section("权限层级") {
                Text("正在探测…", fontSize = 13.sp)
            }
        }

        if (fp != null) {
            Section("本机信息") {
                KeyValue("机型", "${fp.brand} ${fp.model}")
                KeyValue("芯片", "${fp.socPlatform} ${fp.socModel}".trim())
                KeyValue("系统", "Android ${fp.android}（SDK ${fp.sdk}）")
                KeyValue("频点上限", "${fp.maxFreq}")
                for (p in fp.policies) {
                    KeyValue(p.name, "${p.min} ~ ${p.max}（${p.avail.size} 档）")
                }
            }
        }

        Button(
            onClick = onProbeRoot,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "检测中…" else "一键检测权限")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onRequestShizuku, enabled = !busy) {
                Text("申请 Shizuku")
            }
            TextButton(onClick = onRefresh, enabled = !busy) {
                Text("重新探测")
            }
        }

        Section("清理与还原") {
            Text(
                "删除 App 内保存的配置包与快照；撤销导入可把 Scene 配置还原到导入前",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { pendingCleanup = "exports" }) { Text("清空导出包", fontSize = 12.sp) }
                TextButton(onClick = { pendingCleanup = "snapshots" }) { Text("清空快照", fontSize = 12.sp) }
            }
            Button(
                onClick = { pendingCleanup = "restore" },
                enabled = cap?.canApplyConfig == true,
            ) {
                Text("撤销导入（还原备份）")
            }
            if (cleanupMessage.isNotBlank()) {
                Text(cleanupMessage, fontSize = 12.sp)
            }
        }

        Text(
            "导出 / 导入 / 快照恢复需要 root；不 root 也能校验与预览。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConfirmDialog(
    action: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val (title, text) = when (action) {
        "exports" -> "清空导出包" to "将删除 App 目录下所有导出的 .sbox 配置包，不可恢复。"
        "snapshots" -> "清空快照" to "将删除所有调度快照，删除后无法再一键恢复。"
        else -> "撤销导入" to "将把 Scene 的 profile.json 还原到最近一次导入前的备份。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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
private fun FeatureRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp)
        Text(
            if (ok) "可用" else "不可用",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
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
