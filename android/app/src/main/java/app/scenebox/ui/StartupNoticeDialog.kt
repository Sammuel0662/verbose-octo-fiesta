package app.scenebox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 启动公告：必须等 3 秒才能点「确定」，用于确保用户真的看过免责声明。
 * 不能点外部关闭、不能按返回键关闭（onDismissRequest 空实现）。
 */
@Composable
fun StartupNoticeDialog(onAccept: () -> Unit) {
    var remain by remember { mutableStateOf(3) }
    LaunchedEffect(Unit) {
        while (remain > 0) {
            delay(1000)
            remain -= 1
        }
    }

    AlertDialog(
        onDismissRequest = { /* 必须点确定，不允许点外部或返回关闭 */ },
        title = { Text("使用须知", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "SceneBox 是 Scene 的配套调度配置分享工具，完整功能需要 Root 运行。\n" +
                        "支持配置导入导出、快照一键切换、兼容校验。",
                    fontSize = 13.sp,
                )
                Text(
                    "⚠️ 本工具仅读写 Scene 配置，无游戏修改功能；激进调度有发热风险，请谨慎使用。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                )
                Text("本工具与 Scene 官方无关，禁止用作违规用途。", fontSize = 13.sp)

                Text(
                    "补充说明",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "· 配置包与快照只保存在本机 App 目录，不上传任何服务器\n" +
                        "· 广场里的配置由上传者负责，导入前请先看「本机校验」结果\n" +
                        "· 导入会先自动备份 profile.json，可在「清理与还原」里一键撤销\n" +
                        "· 不提供绕过检测、外挂或账号相关能力，也不对这些用途的后果负责",
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAccept,
                enabled = remain == 0,
            ) {
                Text(if (remain > 0) "请阅读（${remain}s）" else "确定")
            }
        },
    )
}
