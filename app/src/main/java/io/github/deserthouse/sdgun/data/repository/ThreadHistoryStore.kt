package io.github.deserthouse.sdgun.data.repository

import org.json.JSONArray
import org.json.JSONObject

/**
 * N1/A2-4：本地帖子阅读历史（纯本机 SharedPreferences，与 myViewUser「访问过的用户」分模型——
 * A31-S05 认定两者不可混称）。纯函数 JVM 可测：去重按 tid、最新在前、封顶截断。
 */
object ThreadHistoryStore {

    data class Entry(val tid: Long, val title: String, val at: Long)

    const val MAX = 50

    /** 追加一条阅读记录：同 tid 去重（旧位置移除）、新条置顶、超 MAX 截尾 */
    fun addEntry(
        existing: List<Entry>,
        tid: Long,
        title: String,
        at: Long,
        max: Int = MAX,
    ): List<Entry> {
        if (tid <= 0 || title.isBlank()) return existing
        val rest = existing.filter { it.tid != tid }
        return (listOf(Entry(tid, title, at)) + rest).take(max)
    }

    fun toJson(entries: List<Entry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().put("tid", e.tid).put("title", e.title).put("at", e.at))
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<Entry> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val tid = o.optLong("tid", 0L)
            val title = o.optString("title")
            if (tid <= 0 || title.isBlank()) return@mapNotNull null
            Entry(tid, title, o.optLong("at", 0L))
        }
    } catch (e: Exception) {
        emptyList()
    }

// ── Context 便捷函数（装机侧；纯函数仅供测试与 UI 状态推导）──
private const val FILE = "sdgun_prefs"
private const val KEY = "thread_history"

/** 记录一次阅读（apply 异步落盘；A28-01 家族注意：进程被杀极端下可能丢最后一次，可接受） */
fun record(context: android.content.Context, tid: Long, title: String) {
    val sp = context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
    val list = fromJson(sp.getString(KEY, "[]") ?: "[]")
    sp.edit().putString(KEY, toJson(addEntry(list, tid, title, System.currentTimeMillis()))).apply()
}

fun load(context: android.content.Context): List<Entry> =
    fromJson(context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
        .getString(KEY, "[]") ?: "[]")

fun remove(context: android.content.Context, tid: Long) {
    val sp = context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
    val list = fromJson(sp.getString(KEY, "[]") ?: "[]")
    sp.edit().putString(KEY, toJson(list.filter { it.tid != tid })).apply()
}}

