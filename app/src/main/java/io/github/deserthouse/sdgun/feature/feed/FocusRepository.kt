package io.github.deserthouse.sdgun.feature.feed

import io.github.deserthouse.sdgun.data.repository.NewsItem
import io.github.deserthouse.sdgun.data.repository.NewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 聚焦页频道模型（G2-A02）：固定频道表，值经 2026-10-22 游客探针逐项实测
 * （cat_id 1~5 全部 success=true 且有内容）。频道即 mag1 的内容分类，
 * 与论坛板块（fid）是两套体系，ID 不互换。
 */
enum class FocusChannel(val catId: Int, val label: String) {
    LATEST(1, "最新"),
    SHOWCASE(2, "晒照"),
    MARKET(3, "市场"),
    GEAR(4, "装备"),
    HELP(5, "求助");

    companion object {
        fun byCatId(id: Int): FocusChannel = entries.firstOrNull { it.catId == id } ?: LATEST
    }
}

/**
 * 聚焦页 Repository：频道内容流的单一事实源。
 * 分页/失败语义沿用 NewsRepository（total_page 权威、失败保留旧数据）。
 */
class FocusRepository {

    suspend fun loadChannel(catId: Int, page: Int): List<NewsItem>? =
        withContext(Dispatchers.IO) {
            NewsRepository.fetchPage(catId, page)
        }
}
