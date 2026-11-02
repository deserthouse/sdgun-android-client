package io.github.deserthouse.sdgun.feature.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat

/**
 * A4-6（N 批收尾）：本地通知投递基础——渠道创建+投递+点击路由。
 * 推送服务端不在范围（外部依赖，如实）；本对象只承载「本地事件→系统通知」通道，
 * 点击经 deep-link intent 落 App 内路由（A29 深链体系）。
 */
object LocalNotifier {

    const val CHANNEL_ID = "sdgun_community"
    private const val CHANNEL_NAME = "社区通知"
    private const val CHANNEL_DESC = "回复/赞/系统通知等社区事件提醒"

    /** 幂等：渠道已存在时更新描述不报错（API 26+ 必须，minSdk 31 全覆盖） */
    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = CHANNEL_DESC }
        mgr.createNotificationChannel(ch)
    }

    /**
     * 投递一条本地通知。deepLink 形如 shuidan://forumThreadList?fid=..（A29 已注册 scheme）；
     * notifyId 由调用方给稳定值（去重靠同 id 覆盖）。
     */
    fun post(context: Context, notifyId: Int, title: String, text: String, deepLink: String?) {
        ensureChannel(context)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context, notifyId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)  // 品牌图标=资源批补
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(notifyId, n)
    }
}
