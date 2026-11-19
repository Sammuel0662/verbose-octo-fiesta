package app.scenebox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.scenebox.core.CpuPolicy
import app.scenebox.core.RecipeStore

/**
 * 逐项自定义调度：每个可调项一个独立控件，配好后一次应用。
 *
 * 和首页那个文本框的区别：这里每项都有明确标签、范围提示和默认值，
 * 不需要记语法；填了哪几项就只应用哪几项。
 */
@Composable
fun ScheduleEditorScreen(
    policies: List<CpuPolicy>,
    governors: List<String>,
    currentGovernor: String,
    recipes: List<RecipeStore.Recipe>,
    onSaveRecipe: (String, String) -> Unit,
    onApplyRecipe: (String) -> Unit,
    onDeleteRecipe: (String) -> Unit,
    message: String,
    busy: Boolean,
    onBack: () -> Unit,
    onApply: (List<Pair<String, String>>, String) -> Unit,
) {
    var governor by remember { mutableStateOf<String?>(null) }
    val maxPct = remember { mutableStateListOf("", "", "", "") }
    val minPct = remember { mutableStateListOf("", "", "", "") }
    var gpuMhz by remember { mutableStateOf("") }
    var clampMin by remember { mutableStateOf("") }
    var clampMax by remember { mutableStateOf("") }
    var coreMin by remember { mutableStateOf("") }
    var coreMax by remember { mutableStateOf("") }
    var boostOn by remember { mutableStateOf<Boolean?>(null) }
    var limitsOff by remember { mutableStateOf<Boolean?>(null) }
    var advanced by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var recipeName by remember { mutableStateOf("") }

    fun buildPatch(): Pair<List<Pair<String, String>>, String> {
        val patch = mutableListOf<Pair<String, String>>()
        val notes = mutableListOf<String>()

        governor?.let {
            patch += "governor" to it
            notes += "调度器=$it"
        }
        for (i in policies.indices) {
            maxPct.getOrNull(i)?.toIntOrNull()?.let {
                patch += "cpu_max_pct_p$i" to it.toString()
                notes += "p$i 上限 $it%"
            }
            minPct.getOrNull(i)?.toIntOrNull()?.let {
                patch += "cpu_min_pct_p$i" to it.toString()
                notes += "p$i 下限 $it%"
            }
        }
        gpuMhz.toIntOrNull()?.let {
            patch += "raw:/sys/class/kgsl/kgsl-3d0/devfreq/max_freq" to (it.toLong() * 1_000_000).toString()
            notes += "GPU $it MHz"
        }
        clampMin.toIntOrNull()?.let {
            patch += "raw:/proc/sys/kernel/sched_util_clamp_min" to it.toString()
            notes += "clamp_min=$it"
        }
        clampMax.toIntOrNull()?.let {
            patch += "raw:/proc/sys/kernel/sched_util_clamp_max" to it.toString()
            notes += "clamp_max=$it"
        }
        coreMin.toIntOrNull()?.let { value ->
            for (p in policies) {
                val cpu = p.name.removePrefix("policy")
                patch += "raw:/sys/devices/system/cpu/cpu$cpu/core_ctl/min_cpus" to value.toString()
            }
            notes += "core_ctl min=$value"
        }
        coreMax.toIntOrNull()?.let { value ->
            for (p in policies) {
                val cpu = p.name.removePrefix("policy")
                patch += "raw:/sys/devices/system/cpu/cpu$cpu/core_ctl/max_cpus" to value.toString()
            }
            notes += "core_ctl max=$value"
        }
        boostOn?.let {
            patch += "raw:/proc/sys/walt/sched_boost" to if (it) "1" else "0"
            notes += "sched_boost=${if (it) 1 else 0}"
        }
        limitsOff?.let {
            patch += "limits" to if (it) "off" else "on"
            notes += if (it) "关限制器" else "开限制器"
        }
        for (line in advanced.split('\n', ',')) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val idx = t.indexOf('=')
            if (idx > 0 && t.startsWith("/")) {
                patch += "raw:${t.substring(0, idx).trim()}" to t.substring(idx + 1).trim()
                notes += t
            }
        }
        return patch to notes.joinToString("、")
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存配方", fontSize = 15.sp) },
            text = {
                OutlinedTextField(
                    value = recipeName,
                    onValueChange = { recipeName = it.take(16) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如 我的均衡", fontSize = 11.sp) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val (p, _) = buildPatch()
                    if (recipeName.isNotBlank() && p.isNotEmpty()) {
                        onSaveRecipe(recipeName.trim(), RecipeStore.encode(p))
                    }
                    showSaveDialog = false
                    recipeName = ""
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("取消") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) { Text("← 返回", fontSize = 13.sp) }
            Text(
                "逐项自定义调度",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        EditorCard("调度器（governor）", "决定用哪套调频算法。当前生效：${currentGovernor.ifBlank { "未知" }}") {
            val options = governors.ifEmpty { listOf("walt", "schedutil") }
            options.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (g in pair) {
                        GovernorTile(
                            name = g,
                            desc = governorDesc(g),
                            selected = governor == g,
                            modifier = Modifier.weight(1f),
                        ) { governor = if (governor == g) null else g }
                    }
                    if (pair.size == 1) Box(modifier = Modifier.weight(1f))
                }
            }
            if (governor != null) {
                Text(
                    "已选 $governor，再点一次取消",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        EditorCard("CPU 频率上限（%）", "每簇单独设置，留空表示不改") {
            for (i in policies.indices) {
                val policy = policies[i]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${policy.name} (${policy.max / 1000}MHz)", fontSize = 11.sp, modifier = Modifier.weight(1f))
                    PctField(maxPct.getOrNull(i) ?: "") { v ->
                        if (i < maxPct.size) maxPct[i] = v
                    }
                }
            }
        }

        EditorCard("CPU 频率下限（%）", "抬高下限会让频率更稳、更跟手，但更耗电") {
            for (i in policies.indices) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(policies[i].name, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    PctField(minPct.getOrNull(i) ?: "") { v ->
                        if (i < minPct.size) minPct[i] = v
                    }
                }
            }
        }

        EditorCard("GPU 上限（MHz）", "Adreno 常见档位 400~900，留空不改") {
            OutlinedTextField(
                value = gpuMhz,
                onValueChange = { gpuMhz = it.filter { c -> c.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("例如 608", fontSize = 11.sp) },
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }

        EditorCard("调度细节", "clamp 限制 util 范围；core_ctl 控制大核常驻数量") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = clampMin,
                    onValueChange = { clampMin = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("clamp_min", fontSize = 10.sp) },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = clampMax,
                    onValueChange = { clampMax = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("clamp_max", fontSize = 10.sp) },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = coreMin,
                    onValueChange = { coreMin = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("core_ctl min", fontSize = 10.sp) },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = coreMax,
                    onValueChange = { coreMax = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("core_ctl max", fontSize = 10.sp) },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }
        }

        EditorCard("开关", "sched_boost 打开会短暂拉高频；关限制器会解除温控与频率限制") {
            ToggleRow("sched_boost", boostOn ?: false, boostOn != null) {
                boostOn = when (boostOn) {
                    null -> true
                    true -> false
                    else -> null
                }
            }
            ToggleRow("关闭系统限制器", limitsOff ?: false, limitsOff != null) {
                limitsOff = when (limitsOff) {
                    null -> true
                    true -> false
                    else -> null
                }
            }
        }

        EditorCard("高级：任意节点", "一行一个，格式 路径=值；会走同样的核验与回退") {
            OutlinedTextField(
                value = advanced,
                onValueChange = { advanced = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                placeholder = { Text("/proc/sys/walt/sched_boost=0", fontSize = 10.sp) },
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }

        EditorCard("我的配方", "把当前这几项存下来，以后一键套用；也能给「按应用自动切换」引用") {
            if (recipes.isEmpty()) {
                Text("还没有保存的配方", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                recipes.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (recipe in pair) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onApplyRecipe(recipe.name) }) {
                                    Text(recipe.name, fontSize = 11.sp, maxLines = 1)
                                }
                                Text(
                                    "✕",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { onDeleteRecipe(recipe.name) },
                                )
                            }
                        }
                        if (pair.size == 1) Box(modifier = Modifier.weight(1f))
                    }
                }
            }
            TextButton(onClick = { showSaveDialog = true }) {
                Text("＋ 保存当前设置为配方", fontSize = 12.sp)
            }
        }

        val (patch, summary) = buildPatch()
        Button(
            onClick = { onApply(patch, summary) },
            enabled = !busy && patch.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (busy) "应用中…"
                else if (patch.isEmpty()) "还没有修改项"
                else "应用这 ${patch.size} 项",
                fontSize = 14.sp,
            )
        }
        if (message.isNotBlank()) {
            Text(message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.padding(bottom = 24.dp)) {}
    }
}

@Composable
private fun PctField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(3)) },
        modifier = Modifier.fillMaxWidth(0.28f),
        singleLine = true,
        placeholder = { Text("%", fontSize = 10.sp) },
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, active: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "$label ${if (active) "" else "（未设置）"}",
            fontSize = 11.sp,
            color = if (active) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun EditorCard(title: String, hint: String, content: @Composable ColumnScope.() -> Unit) {
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
            Text(hint, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

private fun governorDesc(name: String): String = when (name.lowercase()) {
    "walt" -> "高通默认，响应快"
    "schedutil" -> "主线通用，功耗稳"
    "performance" -> "锁高频，最激进"
    "powersave" -> "锁最低频，最省电"
    "conservative" -> "渐进升频，偏保守"
    "interactive" -> "老式交互调频"
    else -> "可用调度器"
}

@Composable
private fun GovernorTile(
    name: String,
    desc: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) accent.copy(alpha = 0.7f) else Color.Transparent,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                )
                if (selected) {
                    Text("  ✓", fontSize = 12.sp, color = accent)
                }
            }
            Text(
                desc,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
