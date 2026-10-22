package io.github.deserthouse.sdgun.core.designsystem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private data class TabSpec(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/** 一级导航固定四项（A70-02：设置不进底栏——设置属于"我的"，入口在个人中心工具栏） */
private val TABS = listOf(
    TabSpec("聚焦", Icons.Filled.Article, Icons.Outlined.Article),
    TabSpec("社区", Icons.Filled.Forum, Icons.Outlined.Forum),
    TabSpec("消息", Icons.Filled.Chat, Icons.Outlined.Chat),
    TabSpec("我的", Icons.Filled.Person, Icons.Outlined.Person),
)

/** 底栏（A70-02 单一选中源）：activeIdx 由导航当前 route 推导，
 *  -1=无选中（兼容 WebView 页等不在 tab 模型内的目的地）；
 *  设置页传 3（设置属于"我的"），保证任意时刻恰好一个选中项。 */
@Composable
fun AppBottomBar(
    activeIdx: Int,
    onTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        TABS.forEachIndexed { i, tab ->
            NavigationBarItem(
                selected = activeIdx == i,
                onClick = { onTab(i) },
                icon = {
                    Icon(
                        if (activeIdx == i) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                    )
                },
                label = { Text(tab.label) },
            )
        }
    }
}
