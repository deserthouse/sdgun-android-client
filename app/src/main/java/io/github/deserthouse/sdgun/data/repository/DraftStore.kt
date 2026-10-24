package io.github.deserthouse.sdgun.data.repository

import android.content.Context

/**
 * 回复草稿（G4/A24-03）：按 所有者+帖子+回复目标 三元隔离。
 * - owner=guest | u<userId>：换号/登出不互见（A 的草稿对 B 隐藏）
 * - replyPid：引用楼层 id；根回复为空
 * - 旧版无主键（draft_reply_<tid>）**有意不迁移**——静默归给首个新账号即 A24-03 反例，
 *   旧草稿保留在库但不再读取（历史草稿量少，丢失影响可接受；如需找回可经 run-as 手工取）
 */
object DraftStore {

    private const val FILE = "sdgun_drafts"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 纯函数（JVM 可测）：草稿存储键 */
    fun key(owner: String, tid: Long, replyPid: Long?): String =
        "draft_${owner}_${tid}_${replyPid ?: "root"}"

    /** 会话态 → 所有者标识（登录=u<id>；游客=guest） */
    fun ownerOf(userId: Long): String = if (userId > 0L) "u$userId" else "guest"

    fun load(context: Context, owner: String, tid: Long, replyPid: Long? = null): String =
        sp(context).getString(key(owner, tid, replyPid), "") ?: ""

    fun save(context: Context, owner: String, tid: Long, text: String, replyPid: Long? = null) {
        sp(context).edit().putString(key(owner, tid, replyPid), text).apply()
    }

    fun clear(context: Context, owner: String, tid: Long, replyPid: Long? = null) {
        sp(context).edit().remove(key(owner, tid, replyPid)).apply()
    }
}
