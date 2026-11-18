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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scenebox.core.Capability
import app.scenebox.core.DeviceFingerprint

@Composable
fun HomeScreen(
    cap: Capability?,
    fp: DeviceFingerprint?,
    busy: Boolean,
    onRequestShizuku: () -> Unit,
    onProbeRoot: () -> Unit,
    onRefresh: () -> Unit,
) {
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

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRequestShizuku, enabled = !busy) {
                Text("申请 Shizuku")
            }
            OutlinedButton(onClick = onProbeRoot, enabled = !busy) {
                Text("检测 root")
            }
            TextButton(onClick = onRefresh, enabled = !busy) {
                Text("重新探测")
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
