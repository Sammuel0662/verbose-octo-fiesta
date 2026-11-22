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
import androidx.compose.foundation.layout.statusBarsPadding
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
import app.scenebox.core.ReportExporter

/**
 * A/B 对比报告：两条曲线叠加 + 差值表 + 导出成图。
 */
@Composable
fun ReportScreen(
    device: String,
    a: ReportExporter.Sample?,
    b: ReportExporter.Sample?,
    message: String,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("← 返回", fontSize = 13.sp) }
            Text("帧率对比", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("", fontSize = 12.sp)
        }

        ReportCard("曲线") {
            if (a == null || b == null || a.fps.size < 2 || b.fps.size < 2) {
                Text(
                    "需要两段采样才能对比。先在「监测」里点「对比 A」，调参后再点「对比 B」。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                ) {
                    val maxFps = maxOf(a.fps.maxOrNull() ?: 60, b.fps.maxOrNull() ?: 60, 60)
                    fun drawSeries(series: List<Int>, color: Color) {
                        var prev: Offset? = null
                        series.forEachIndexed { i, v ->
                            val x = size.width * i / (series.size - 1).coerceAtLeast(1)
                            val y = size.height - size.height * (v.toFloat() / maxFps)
                            val point = Offset(x, y)
                            prev?.let {
                                drawLine(color = color, start = it, end = point, strokeWidth = 4f)
                            }
                            prev = point
                        }
                    }
                    drawSeries(a.fps, Color(0xFF7CE38B))
                    drawSeries(b.fps, Color(0xFFFF9800))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("■ A ${a.label}", fontSize = 10.sp, color = Color(0xFF7CE38B))
                    Text("■ B ${b.label}", fontSize = 10.sp, color = Color(0xFFFF9800))
                }
            }
        }

        if (a != null && b != null) {
            ReportCard("差值") {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("指标", fontSize = 11.sp, modifier = Modifier.weight(1.1f))
                    Text("A", fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("B", fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("差值", fontSize = 11.sp, modifier = Modifier.weight(1f))
                }
                DeltaRow("均值 FPS", "${a.avg}", "${b.avg}", "${b.avg - a.avg}")
                DeltaRow("1% low", "${a.low}", "${b.low}", "${b.low - a.low}")
                DeltaRow(
                    "jank %",
                    String.format("%.2f", a.jank),
                    String.format("%.2f", b.jank),
                    String.format("%+.2f", b.jank - a.jank),
                )
                DeltaRow(
                    "最高温",
                    String.format("%.1f℃", a.maxTemp),
                    String.format("%.1f℃", b.maxTemp),
                    String.format("%+.1f", b.maxTemp - a.maxTemp),
                )
                Text(
                    "A：${a.governor} / ${a.maxFreqs}\nB：${b.governor} / ${b.maxFreqs}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                Text("导出对比图（保存到相册）", fontSize = 13.sp)
            }
        }

        if (message.isNotBlank()) {
            Text(message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.padding(bottom = 24.dp)) {}
    }
}

@Composable
private fun DeltaRow(name: String, va: String, vb: String, delta: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(name, fontSize = 11.sp, modifier = Modifier.weight(1.1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(va, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(vb, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(
            delta,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
