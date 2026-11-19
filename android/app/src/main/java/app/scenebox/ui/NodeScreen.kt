package app.scenebox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scenebox.core.NodeBrowser

/**
 * 节点编辑：把所有可写的内核节点摊开，直接改。
 * 这是"能调的东西太少"的终极答案——不再受预设限制。
 */
@Composable
fun NodeScreen(
    groups: List<NodeBrowser.Group>,
    nodes: List<NodeBrowser.Node>,
    loading: Boolean,
    message: String,
    onLoadGroup: (String) -> Unit,
    onApply: (String, String) -> Unit,
) {
    var editing by remember { mutableStateOf<NodeBrowser.Node?>(null) }
    var editValue by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }

    editing?.let { node ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("修改节点", fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(node.path, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("当前值：${node.value.ifBlank { "（空）" }}", fontSize = 11.sp)
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { editValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "写前会记原值，写后回读；不一致会自动回退。",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onApply(node.path, editValue)
                    editing = null
                }) { Text("写入") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "节点编辑",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "选一类节点加载，点任意一行即可改值。标 R 的是只读。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NodeCard(title = "选择节点分类") {
            for (group in groups) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLoadGroup(group.prefix) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(group.name, fontSize = 12.sp)
                    Text(group.prefix, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (loading) {
            Text("加载中…", fontSize = 12.sp)
        }

        if (nodes.isNotEmpty()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("过滤节点名", fontSize = 11.sp) },
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = true,
            )
            NodeCard(title = "共 ${nodes.size} 个节点") {
                val filtered = if (search.isBlank()) nodes
                else nodes.filter { it.path.contains(search, ignoreCase = true) }
                for (node in filtered.take(80)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = node.writable) {
                                editing = node
                                editValue = node.value
                            }
                            .padding(vertical = 5.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            if (node.writable) "W" else "R",
                            fontSize = 9.sp,
                            color = if (node.writable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "  " + node.path.substringAfterLast('/'),
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            node.value.ifBlank { "—" },
                            fontSize = 10.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (filtered.size > 80) {
                    Text("只显示前 80 个，用上面的过滤缩小范围", fontSize = 10.sp)
                }
            }
        }

        if (message.isNotBlank()) {
            Text(message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.padding(bottom = 24.dp)) {}
    }
}

@Composable
private fun NodeCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
