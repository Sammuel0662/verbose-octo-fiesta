package app.scenebox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scenebox.core.Capability
import app.scenebox.core.DeviceFingerprint
import app.scenebox.core.Diagnostics

@Composable
fun MonitorScreen(
    cap: Capability?,
    fp: DeviceFingerprint?,
    liveStatus: String,
    onRefreshStatus: () -> Unit,
    sampling: Boolean,
    sampleCount: Int,
    sampleFps: List<Int>,
    sampleSummary: String,
    onStartSample: () -> Unit,
    checks: List<Diagnostics.Check>,
    diagBusy: Boolean,
    onRunDiagnostics: () -> Unit,
    logLines: List<String>,
    onRefreshLog: () -> Unit,
    onClearLog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "性能监测",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp),
        )

        Card(title = "实时状态") {
            if (liveStatus.isBlank()) {
                Text("读取中…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(liveStatus, fontSize = 11.sp, lineHeight = 16.sp)
            }
            TextButton(onClick = onRefreshStatus) { Text("刷新一次", fontSize = 12.sp) }
        }

        Card(title = "帧率 / 温度采样") {
            if (sampleFps.size >= 2) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                ) {
                    val maxFps = (sampleFps.maxOrNull() ?: 60).coerceAtLeast(30)
                    val stepX = size.width / (sampleFps.size - 1)
                    var prev: Offset? = null
                    sampleFps.forEachIndexed { index, value ->
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
                    "峰值 ${sampleFps.maxOrNull() ?: 0} · 最低 ${sampleFps.minOrNull() ?: 0} · 均值 ${sampleFps.average().toInt()}",
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
            Button(onClick = onStartSample, enabled = !sampling, modifier = Modifier.fillMaxWidth()) {
                Text(if (sampling) "采样中 $sampleCount/30…" else "开始采样（30 秒）", fontSize = 13.sp)
            }
            if (sampleSummary.isNotBlank()) {
                Text(sampleSummary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(title = "环境检测") {
            Button(
                onClick = onRunDiagnostics,
                enabled = !diagBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (diagBusy) "检测中…" else "运行完整检测", fontSize = 13.sp)
            }
            if (checks.isEmpty()) {
                Text(
                    "检查 root、Shizuku、Scene 状态、频点表、可写性、温控与 GPU 节点",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (check in checks) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            if (check.ok) "✓" else "✗",
                            fontSize = 12.sp,
                            color = if (check.ok) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(check.name, fontSize = 12.sp)
                            Text(
                                check.detail,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Card(title = "操作日志") {
            if (logLines.isEmpty()) {
                Text("还没有操作记录", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                for (line in logLines) {
                    Text(line, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefreshLog) { Text("刷新", fontSize = 12.sp) }
                TextButton(onClick = onClearLog) { Text("清空日志", fontSize = 12.sp) }
            }
        }

        Text(
            if (cap?.canReadSceneConfig == true) "Scene 配置可读" else "Scene 配置不可读",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
internal fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
