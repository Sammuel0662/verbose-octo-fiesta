package app.scenebox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 使用教程：把"这东西怎么用、边界在哪、出问题怎么退"讲清楚。
 * 玩家的真实问题往往不是不会点按钮，而是不知道点了会发生什么。
 */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 返回") }
        Text("使用教程", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Step(
            "第 1 步",
            "检查权限",
            "点首页的「一键检测权限」。\n" +
                "· T0/T1：能看教程、校验、预览，不能导入\n" +
                "· T2：导出、导入、快照全可用\n" +
                "root 授权在 KernelSU/Magisk 管理器里给。",
        )
        Step(
            "第 2 步",
            "导出自己的配置",
            "点「导出 .sbox」。会读取 Scene 的调度方案、每应用档位、游戏列表、帧率覆盖，\n" +
                "打包成一个 .sbox 文件，存在 App 私有目录里（列表会显示出来）。",
        )
        Step(
            "第 3 步",
            "看别人的包（导入前必做）",
            "在「已导出的配置」里点任意 .sbox 就能预览：\n" +
                "· 适配对比：模板机型/芯片 vs 你的\n" +
                "· 写入清单：到底会改哪些节点、写成什么值\n" +
                "· 本机校验：频率超限、不在频点表、动温控都会被标出来\n" +
                "拦截级别是硬性的——芯片平台不匹配直接不允许导入。",
        )
        Step(
            "第 4 步",
            "导入到 Scene",
            "确认没问题再点「导入到 Scene（自动备份）」。\n" +
                "导入会先备份原 profile.json，再原子替换；\n" +
                "导完需要让 Scene 重新应用档位或重启才完全生效。",
        )
        Step(
            "第 5 步",
            "快照 A/B（解决忘恢复）",
            "A = 游戏调度，B = 日常默认。\n" +
                "调好游戏参数后「保存 A」；恢复日常状态后「保存 B」。\n" +
                "以后进游戏点「恢复 A」，退出点「恢复 B」，不用再手动抄参数。",
        )
        Step(
            "想退回去？",
            "清理与还原",
            "首页底部「清理与还原」可以：\n" +
                "· 删除导出的配置包\n" +
                "· 删除快照\n" +
                "· 撤销导入：把 Scene 配置还原到最近一次导入前的备份",
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("边界与免责", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "· 本工具是 Scene 的辅助工具，不内置调度引擎\n" +
                        "· 只读写系统侧调度/温控参数，不碰游戏进程、不改内存、不做画质破解\n" +
                        "· 读取的是 Scene 自己存储的配置文件，不修改 Scene 本体\n" +
                        "· 激进方案可能过热锁核，配置由上传者负责，导入前请务必看校验结果",
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun Step(step: String, title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(step, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, fontSize = 12.sp)
        }
    }
}
