package app.scenebox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 底部悬浮胶囊导航：四页（调度 / 节点 / 监测 / 设置）。
 * 圆角底板 + 选中项胶囊高亮，和页面内的卡片风格保持一致。
 */
@Composable
fun MainTabs(
    tab: Int,
    onTabChange: (Int) -> Unit,
    home: @Composable () -> Unit,
    nodes: @Composable () -> Unit,
    monitor: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            Crossfade(
                targetState = tab,
                animationSpec = tween(durationMillis = 220),
                label = "tab",
            ) { current ->
                when (current) {
                    0 -> home()
                    1 -> nodes()
                    2 -> monitor()
                    else -> settings()
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                ),
                shadowElevation = 10.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabItem("◎", "调度", tab == 0, Modifier.weight(1f)) { onTabChange(0) }
                    TabItem("⌗", "节点", tab == 1, Modifier.weight(1f)) { onTabChange(1) }
                    TabItem("▤", "监测", tab == 2, Modifier.weight(1f)) { onTabChange(2) }
                    TabItem("⚙", "设置", tab == 3, Modifier.weight(1f)) { onTabChange(3) }
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    icon: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant

    val tint by animateColorAsState(
        targetValue = if (selected) accent else idle,
        animationSpec = tween(durationMillis = 200),
        label = "tint",
    )
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "bg",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "scale",
    )

    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clip(RoundedCornerShape(percent = 50))
                .background(background)
                .padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 15.sp, color = tint)
            }
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = tint,
                textAlign = TextAlign.Center,
            )
        }
    }
}
