package io.github.deserthouse.sdgun.data.repository

import android.content.Context

/**
 * 回复草稿（G4）：按 tid 隔离（键=draft_reply_<tid>）——跨帖不串（扩展 E70 反例=草稿串帖）。
 * 提交成功即清除；退出编辑器保留（下次进帖恢复）。
 */
object DraftStore {

    private const val FILE = "sdgun_drafts"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context, tid: Long): String =
        sp(context).getString("draft_reply_$tid", "") ?: ""

    fun save(context: Context, tid: Long, text: String) {
        sp(context).edit().putString("draft_reply_$tid", text).apply()
    }

    fun clear(context: Context, tid: Long) {
        sp(context).edit().remove("draft_reply_$tid").apply()
    }
}
