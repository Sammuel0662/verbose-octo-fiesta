package app.scenebox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    autoOn: Boolean,
    autoRules: String,
    onToggleAuto: (Boolean) -> Unit,
    onSaveAutoRules: (String) -> Unit,
    installedApps: List<String>,
    recipeNames: List<String>,
    onAddRule: (String, String) -> Unit,
    onOpenApps: () -> Unit,
    hudOn: Boolean,
    hudMessage: String,
    onToggleHud: () -> Unit,
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

        SectionCard(title = "按应用自动切换") {
            Text(
                "前台应用匹配到规则时，自动应用对应配方（配方在「逐项自定义」里保存）。" +
                    "需要开启悬浮控制保持后台运行。一行一条：包名=配方名",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (autoOn) "已开启" else "已关闭", fontSize = 12.sp)
                androidx.compose.material3.Switch(checked = autoOn, onCheckedChange = onToggleAuto)
            }
            var rulesText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(autoRules) }
    var showAddRule by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            OutlinedTextField(
                value = rulesText,
                onValueChange = { rulesText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
                placeholder = { Text("com.tencent.tmgp.sgame=我的性能", fontSize = 10.sp) },
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveAutoRules(rulesText) },
                    modifier = Modifier.weight(1f),
                ) { Text("保存规则", fontSize = 12.sp) }
                Button(
                    onClick = { showAddRule = true },
                    modifier = Modifier.weight(1f),
                    enabled = installedApps.isNotEmpty(),
                ) { Text("＋ 添加规则", fontSize = 12.sp) }
            }
            if (showAddRule) {
                AddRuleDialog(
                    apps = installedApps,
                    recipes = recipeNames,
                    onDismiss = { showAddRule = false },
                    onConfirm = { pkg, recipe ->
                        onAddRule(pkg, recipe)
                        showAddRule = false
                    },
                )
            }
        }

        SectionCard(title = "应用治理") {
            Text(
                "冻结/解冻应用、调整待机分组。只调用 pm / am 命令，不改应用数据。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
                Text("打开应用列表", fontSize = 13.sp)
            }
        }

        SectionCard(title = "悬浮控制") {
            Text(
                "开启后桌面会有一个可拖动的小面板：点档位直接改 CPU 频率，点 Hz 直接锁刷新率，不用回 App。" +
                    "面板只写系统调度参数，不注入游戏、不读其他应用。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onToggleHud, modifier = Modifier.fillMaxWidth()) {
                Text(if (hudOn) "关闭悬浮控制" else "开启悬浮控制", fontSize = 13.sp)
            }
            if (hudMessage.isNotBlank()) {
                Text(hudMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "首次使用需要「悬浮窗」权限；Android 13+ 还需要通知权限（用于常驻通知）。",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

@Composable
private fun AddRuleDialog(
    apps: List<String>,
    recipes: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var query by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var pickedApp by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var pickedRecipe by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自动切换规则", fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. 选择应用", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索包名", fontSize = 10.sp) },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                val filtered = if (query.isBlank()) apps.take(60)
                else apps.filter { it.contains(query, true) }.take(60)
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                ) {
                    items(filtered) { pkg ->
                        Text(
                            (if (pickedApp == pkg) "● " else "○ ") + pkg,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pickedApp = pkg }
                                .padding(vertical = 4.dp),
                            color = if (pickedApp == pkg) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Text("2. 选择配方", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (recipes.isEmpty()) {
                    Text("还没有配方，先去「逐项自定义」里保存一个", fontSize = 10.sp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (name in recipes.take(3)) {
                            Text(
                                name,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .clickable { pickedRecipe = name }
                                    .padding(4.dp),
                                color = if (pickedRecipe == name) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val a = pickedApp
                    val r = pickedRecipe
                    if (a != null && r != null) onConfirm(a, r)
                },
                enabled = pickedApp != null && pickedRecipe != null,
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
