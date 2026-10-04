package io.github.deserthouse.sdgun.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.deserthouse.sdgun.web.UrlRules

private data class TabSpec(
    val label: String,
    val url: String?,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val TABS = listOf(
    TabSpec("首页", UrlRules.HOME, Icons.Filled.Forum, Icons.Outlined.Forum),
    TabSpec("消息", UrlRules.MESSAGES, Icons.Filled.Chat, Icons.Outlined.Chat),
    TabSpec("我的", UrlRules.PROFILE, Icons.Filled.Person, Icons.Outlined.Person),
)

/** 按 tab 序号取目标 URL（Home 的 onTab 回调用） */
internal fun tabUrl(index: Int): String? = TABS.getOrNull(index)?.url

/** 底栏（Home 与 Settings 两路由共用，C-5：设置页底栏常驻与其他 tab 一致）。
 *  activeIdx：0/1/2=三个站内 tab，3=设置。 */
@Composable
fun AppBottomBar(
    activeIdx: Int,
    onTab: (Int) -> Unit,
    onOpenSettings: () -> Unit,
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
        NavigationBarItem(
            selected = activeIdx == 3,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
            label = { Text("设置") },
        )
    }
}
