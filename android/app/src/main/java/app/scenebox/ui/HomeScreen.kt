package app.scenebox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scenebox.core.Capability
import app.scenebox.core.DeviceFingerprint
import app.scenebox.core.Tier
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
    onOpenGuide: () -> Unit,
    onOpenPlaza: () -> Unit,
    snapshotMessage: String,
    onSnapshotSave: (String) -> Unit,
    onSnapshotRestore: (String) -> Unit,
    exports: List<File>,
    downloads: List<File>,
    onImportFile: () -> Unit,
    onOpenFile: (File) -> Unit,
    cleanupMessage: String,
    onCleanup: (String) -> Unit,
    liveStatus: String,
    onRefreshStatus: () -> Unit,
    sampling: Boolean,
    sampleCount: Int,
    sampleFps: List<Int>,
    sampleSummary: String,
    onStartSample: () -> Unit,
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
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Header(cap = cap, fp = fp, busy = busy, onProbeRoot = onProbeRoot)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                )
            }

            QuickActions(
                canExport = cap?.canExportScene == true,
                exporting = exportBusy,
                onExport = onExport,
                onPlaza = onOpenPlaza,
                onGuide = onOpenGuide,
            )

            StatusCard(cap)

            Card(title = "实时状态") {
                if (liveStatus.isBlank()) {
                    Text("读取中…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(liveStatus, fontSize = 11.sp, lineHeight = 16.sp)
                }
                TextButton(onClick = onRefreshStatus) { Text("刷新一次", fontSize = 12.sp) }
            }

            SnapshotCard(
                enabled = cap?.canSnapshotRestore == true,
                message = snapshotMessage,
                onSave = onSnapshotSave,
                onRestore = onSnapshotRestore,
            )

            FilesCard(
                exports = exports,
                downloads = downloads,
                onImportFile = onImportFile,
                onOpenFile = onOpenFile,
            )

            SampleCard(
                sampling = sampling,
                count = sampleCount,
                fps = sampleFps,
                summary = sampleSummary,
                onStart = onStartSample,
            )

            CleanupCard(
                canReset = cap?.canSnapshotRestore == true,
                canRestoreImport = cap?.canApplyConfig == true,
                message = cleanupMessage,
                onAction = { pendingCleanup = it },
            )

            DeviceCard(fp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onRefresh, enabled = !busy) {
                    Text("重新检测", fontSize = 12.sp)
                }
                TextButton(onClick = onRequestShizuku, enabled = !busy) {
                    Text("申请 Shizuku", fontSize = 12.sp)
                }
            }

            if (exportMessage.isNotBlank()) {
                Note(exportMessage)
            }
        }
    }
}

@Composable
private fun Header(
    cap: Capability?,
    fp: DeviceFingerprint?,
    busy: Boolean,
    onProbeRoot: () -> Unit,
) {
    val tier = cap?.tier
    val accent = when (tier) {
        Tier.ROOT, Tier.MODULE -> MaterialTheme.colorScheme.primary
        Tier.SHIZUKU -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF16241C), MaterialTheme.colorScheme.background),
                ),
            )
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "SceneBox",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
            ) {
                Text(
                    tier?.label ?: "检测中…",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent,
                )
            }
            fp?.let {
                Text(
                    "${it.socPlatform.uppercase()} · ${it.socModel} · 上限 ${it.maxFreq}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        Button(
            onClick = onProbeRoot,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(if (busy) "检测中…" else "一键检测权限", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuickActions(
    canExport: Boolean,
    exporting: Boolean,
    onExport: () -> Unit,
    onPlaza: () -> Unit,
    onGuide: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionTile(
            glyph = "⇩",
            label = if (exporting) "导出中" else "导出配置",
            enabled = canExport && !exporting,
            modifier = Modifier.weight(1f),
            onClick = onExport,
        )
        ActionTile(
            glyph = "☰",
            label = "配置广场",
            enabled = true,
            modifier = Modifier.weight(1f),
            onClick = onPlaza,
        )
        ActionTile(
            glyph = "?",
            label = "使用教程",
            enabled = true,
            modifier = Modifier.weight(1f),
            onClick = onGuide,
        )
    }
}

@Composable
private fun ActionTile(
    glyph: String,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.35f
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                glyph,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            )
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}

@Composable
private fun StatusCard(cap: Capability?) {
    Card(title = "可用能力") {
        if (cap == null) {
            Text("检测中…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("校验", true)
                Pill("预览", true)
                Pill("导出", cap.canExportScene)
                Pill("导入", cap.canApplyConfig)
                Pill("快照", cap.canSnapshotRestore)
            }
            Text(
                "su " + (if (cap.rootAvailable) "已授权" else "未授权") +
                    " · Scene " + (if (cap.canReadSceneConfig) "可读" else "不可读"),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SnapshotCard(
    enabled: Boolean,
    message: String,
    onSave: (String) -> Unit,
    onRestore: (String) -> Unit,
) {
    Card(title = "调度快照") {
        SnapshotRow("A", "游戏", enabled, onSave, onRestore)
        SnapshotRow("B", "日常", enabled, onSave, onRestore)
        if (message.isNotBlank()) Note(message)
    }
}

@Composable
private fun SnapshotRow(
    label: String,
    desc: String,
    enabled: Boolean,
    onSave: (String) -> Unit,
    onRestore: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(desc, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onSave(label) }, enabled = enabled) {
            Text("保存", fontSize = 12.sp)
        }
        TextButton(onClick = { onRestore(label) }, enabled = enabled) {
            Text("恢复", fontSize = 12.sp)
        }
    }
}

@Composable
private fun FilesCard(
    exports: List<File>,
    downloads: List<File>,
    onImportFile: () -> Unit,
    onOpenFile: (File) -> Unit,
) {
    Card(title = "配置包") {
        FileGroup("我的导出", exports, onOpenFile)
        FileGroup("下载的配置", downloads, onOpenFile)
        Button(
            onClick = onImportFile,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        ) {
            Text("从文件导入 .sbox", fontSize = 13.sp)
        }
    }
}

@Composable
private fun FileGroup(title: String, files: List<File>, onOpenFile: (File) -> Unit) {
    Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (files.isEmpty()) {
        Text("暂无", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
    } else {
        for (file in files.take(3)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenFile(file) }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    file.name,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    SboxStore.sizeText(file),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CleanupCard(
    canReset: Boolean,
    canRestoreImport: Boolean,
    message: String,
    onAction: (String) -> Unit,
) {
    Card(title = "清理与还原") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("清空下载", true, Modifier.weight(1f)) { onAction("downloads") }
            SmallButton("清空快照", true, Modifier.weight(1f)) { onAction("snapshots") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("还原系统配置", canReset, Modifier.weight(1f)) { onAction("system") }
            SmallButton("撤销导入", canRestoreImport, Modifier.weight(1f)) { onAction("restore") }
        }
        if (message.isNotBlank()) Note(message)
    }
}

@Composable
private fun SmallButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(if (enabled) 1f else 0.35f),
            )
        }
    }
}

@Composable
private fun DeviceCard(fp: DeviceFingerprint?) {
    if (fp == null) return
    Card(title = "本机") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${fp.brand} ${fp.model}".trim(), fontSize = 12.sp)
            Text(
                "Android ${fp.android}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (policy in fp.policies) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            policy.name,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("${policy.max}", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleCard(
    sampling: Boolean,
    count: Int,
    fps: List<Int>,
    summary: String,
    onStart: () -> Unit,
) {
    Card(title = "帧率 / 温度采样") {
        if (fps.size >= 2) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
            ) {
                val maxFps = (fps.maxOrNull() ?: 60).coerceAtLeast(30)
                val stepX = size.width / (fps.size - 1)
                var prev: Offset? = null
                fps.forEachIndexed { index, value ->
                    val point = Offset(
                        x = index * stepX,
                        y = size.height - (value.toFloat() / maxFps) * size.height,
                    )
                    prev?.let {
                        drawLine(
                            color = Color(0xFF7CE38B),
                            start = it,
                            end = point,
                            strokeWidth = 4f,
                        )
                    }
                    prev = point
                }
            }
            Text(
                "峰值 ${fps.maxOrNull() ?: 0} · 最低 ${fps.minOrNull() ?: 0} · 均值 ${fps.average().toInt()}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "进游戏后点开始，采样 30 秒，出帧率曲线和温度",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(onClick = onStart, enabled = !sampling, modifier = Modifier.fillMaxWidth()) {
            Text(if (sampling) "采样中 $count/30…" else "开始采样（30 秒）", fontSize = 13.sp)
        }
        if (summary.isNotBlank()) {
            Text(summary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun Pill(label: String, ok: Boolean) {
    val color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
    ) {
        Text(
            "$label ${if (ok) "✓" else "✕"}",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = color,
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ConfirmDialog(
    action: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val (title, text) = when (action) {
        "downloads" -> "清空下载的配置" to "将删除所有从 SceneBox 下载/接收的配置包（自己导出的会保留）。"
        "snapshots" -> "清空快照" to "将删除所有调度快照，删除后无法再一键恢复。"
        "system" -> "还原系统配置" to "将把 CPU 频率上下限和调度开关写回系统默认值，会覆盖当前正在生效的调参。"
        else -> "撤销导入" to "将把 Scene 的 profile.json 还原到最近一次导入前的备份。"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
