package app.scenebox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scenebox.core.DeviceFingerprint

@Composable
fun SettingsScreen(
    fp: DeviceFingerprint?,
    deviceInfo: List<Pair<String, String>>,
    hzModes: List<Int>,
    currentHz: Int,
    hzMessage: String,
    onSetHz: (Int) -> Unit,
    onResetHz: () -> Unit,
    onOpenGuide: () -> Unit,
    onClearLog: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "设置与说明",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp),
        )

        if (fp != null) {
            SectionCard(title = "本机信息") {
                RowLine("机型", "${fp.brand} ${fp.model}".trim())
                RowLine("芯片", "${fp.socModel} ${fp.socPlatform}")
                RowLine("系统", "Android ${fp.android} (SDK ${fp.sdk})")
                for ((key, value) in deviceInfo) RowLine(key, value)
                Text(
                    "频点：" + fp.policies.joinToString("  ") { "${it.name}:${it.max / 1000}MHz" },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "刷新率") {
            Text(
                "当前 ${currentHz}Hz。锁定后切一次应用生效；部分机型需要亮屏一次。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hzModes.isEmpty()) {
                Text("读不到面板支持的刷新率", fontSize = 11.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (hz in hzModes) {
                        OutlinedButton(
                            onClick = { onSetHz(hz) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("${hz}Hz", fontSize = 11.sp)
                        }
                    }
                }
            }
            TextButton(onClick = onResetHz) {
                Text("恢复自动刷新率", fontSize = 12.sp)
            }
            if (hzMessage.isNotBlank()) {
                Text(hzMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionCard(title = "声明与边界") {
            Text(
                "1. 本工具不修改 Scene 的 APK、不绕过它的授权；Scene Pro 请通过官方渠道购买。\n\n" +
                    "2. 自定义参数直接写入内核节点，属于高风险操作：可能引起发热、掉帧、耗电增加，"
                    + "极端情况下触发降频保护或重启。写入的值重启后不一定保留。\n\n" +
                    "3. 不提供任何游戏内画质破解、内存修改、反检测/隐藏功能。本工具只读写系统调度参数，"
                    + "不接触游戏进程。\n\n" +
                    "4. 部分游戏可能对 root 环境有额外限制，这是游戏自身策略，本工具不做规避。\n\n" +
                    "5. 使用本工具的全部风险由你自己承担；出问题请用「还原系统配置」或重启恢复。",
                fontSize = 11.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "教程与维护") {
            OutlinedButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) {
                Text("查看使用教程", fontSize = 13.sp)
            }
            TextButton(onClick = onClearLog) {
                Text("清空操作日志", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RowLine(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 11.sp, maxLines = 1)
    }
}

@androidx.compose.runtime.Composable
private fun SectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
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
