package app.scenebox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 导入成功后的结果页：告诉用户"写进哪、备份在哪、下一步做什么"，
 * 并直接给出「撤销导入」这个后悔药。
 */
@Composable
fun ImportSuccessDialog(
    configName: String,
    sizeText: String,
    backupName: String,
    onDone: () -> Unit,
    onRollback: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* 必须选择下一步 */ },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "✓",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "导入成功",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(configName, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "已写入 Scene 的 profile.json（$sizeText 字节）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (backupName.isNotBlank()) {
                    Text(
                        "原配置已备份：$backupName",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "下一步：回到 Scene 重新应用一次档位，或重启 App，让新方案真正生效。",
                    fontSize = 12.sp,
                )
                Text(
                    "不满意可以立即撤销，会把 profile.json 还原到导入前的备份。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onRollback) { Text("撤销导入") }
        },
    )
}
