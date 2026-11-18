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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scenebox.community.PlazaRepo

/**
 * 配置广场：远程索引里的配置包 + 公开教程。
 * 所有条目都带来源，不伪造"热门方案"。
 */
@Composable
fun PlazaScreen(
    index: PlazaRepo.Index?,
    loading: Boolean,
    message: String,
    onRefresh: () -> Unit,
    onDownload: (PlazaRepo.Item) -> Unit,
    onOpenGuide: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← 返回") }
            TextButton(onClick = onRefresh, enabled = !loading) {
                Text(if (loading) "刷新中…" else "刷新")
            }
        }
        Text("配置广场", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (message.isNotBlank()) {
            Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (index == null) {
            Section("加载中") {
                Text(
                    if (loading) "正在拉取索引…" else "还没有加载到数据，点右上角刷新",
                    fontSize = 12.sp,
                )
            }
        } else {
            if (index.notice.isNotBlank()) {
                Section("说明") { Text(index.notice, fontSize = 12.sp) }
            }

            for (category in index.categories) {
                Section("${category.name}（${category.items.size}/${category.slots}）") {
                    if (category.desc.isNotBlank()) {
                        Text(
                            category.desc,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    for (item in category.items) {
                        Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${item.game} · ${item.soc} · ${item.author} · 风险 ${item.risk}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.desc.isNotBlank()) Text(item.desc, fontSize = 12.sp)
                        Button(onClick = { onDownload(item) }) { Text("下载并预览") }
                    }
                    val empty = (category.slots - category.items.size).coerceAtLeast(0)
                    if (empty > 0) {
                        Text(
                            "还有 $empty 个位置待投稿（网上的热门方案目前只有截图，没有可下载文件）",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Section("公开教程（${index.guides.size}）") {
                Text(
                    "网上的方案大多是截图和教程，这里收录来源可查的教程链接",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (guide in index.guides) {
                    TextButton(onClick = { onOpenGuide(guide.url) }) {
                        Text("${guide.title}  ·  ${guide.source}", fontSize = 12.sp)
                    }
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
