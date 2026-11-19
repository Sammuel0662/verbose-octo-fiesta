package app.scenebox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.scenebox.core.AppGovernance

/**
 * 应用治理：冻结/解冻与待机分组。
 * 只调用 pm / am 命令，不常驻监听，也不修改应用数据。
 */
@Composable
fun AppsScreen(
    apps: List<String>,
    loading: Boolean,
    message: String,
    onRefresh: () -> Unit,
    onSuspend: (String) -> Unit,
    onUnsuspend: (String) -> Unit,
    onStandby: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("← 返回", fontSize = 13.sp) }
            Text("应用治理", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh) { Text("刷新", fontSize = 12.sp) }
        }

        Text(
            if (loading) "加载中…" else "共 ${apps.size} 个用户应用",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("搜索包名", fontSize = 11.sp) },
            textStyle = MaterialTheme.typography.bodySmall,
        )

        if (message.isNotBlank()) {
            Text(
                message,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        val filtered = if (query.isBlank()) apps else apps.filter { it.contains(query, true) }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filtered) { pkg ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(pkg, fontSize = 11.sp, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onSuspend(pkg) }) { Text("冻结", fontSize = 11.sp) }
                        TextButton(onClick = { onUnsuspend(pkg) }) { Text("解冻", fontSize = 11.sp) }
                        TextButton(onClick = { onStandby(pkg, "rare") }) { Text("待机限制", fontSize = 11.sp) }
                        TextButton(onClick = { onStandby(pkg, "active") }) { Text("保活", fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}
