package io.github.deserthouse.sdgun.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

/** 站外链接处理：浏览器打开 / 复制链接 / 取消（不自动跳转，用户决策） */
@Composable
fun ExternalLinkDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("外部链接") },
        text = { Text(url, maxLines = 4, overflow = TextOverflow.Ellipsis) },
        confirmButton = {
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                onDismiss()
            }) { Text("浏览器打开") }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("link", url)))
                }
                onDismiss()
            }) { Text("复制链接") }
        },
    )
}
