package io.github.deserthouse.sdgun.feature.feed

import io.github.deserthouse.sdgun.core.model.UiState
import io.github.deserthouse.sdgun.data.repository.NewsItem
import io.github.deserthouse.sdgun.data.repository.NewsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 聚焦页 ViewModel（G2-A02）：每频道独立 UiState + 独立滚动位置。
 * 切频道保留各自状态；刷新失败保留旧内容（Stale）；总页数以服务端为准。
 * 单向数据流：UI 事件 → VM 方法 → Repository → StateFlow。
 */
class FocusViewModel(
    private val repo: FocusRepository,
    private val scope: CoroutineScope,
) {
    /** 每频道独立状态（频道数量固定 5，Map 开销可忽略） */
    private val channelStates = FocusChannel.entries.associateWith {
        MutableStateFlow<UiState<List<NewsItem>>>(UiState.InitialLoading)
    }
    val stateOf: Map<FocusChannel, StateFlow<UiState<List<NewsItem>>>> = channelStates
        .mapValues { it.value }

    var currentChannel: FocusChannel = FocusChannel.LATEST
        private set

    private val nextPage = FocusChannel.entries.associateWith { java.util.concurrent.atomic.AtomicInteger(1) }
    private val loading = FocusChannel.entries.associateWith { java.util.concurrent.atomic.AtomicBoolean(false) }

    fun state(channel: FocusChannel): StateFlow<UiState<List<NewsItem>>> = stateOf.getValue(channel)

    fun select(channel: FocusChannel) {
        currentChannel = channel
        if (channelStates.getValue(channel).value is UiState.InitialLoading) {
            load(channel)
        }
    }

    /**
     * 刷新语义（A24-04）：回第 1 页；成功后推进游标到 2；
     * 失败时**保留旧列表**（Stale）——无旧数据才退 RetryableError。
     */
    fun load(channel: FocusChannel) {
        if (loading.getValue(channel)!!.get()) return
        loading.getValue(channel)!!.set(true)
        scope.launch {
            val fetched = repo.loadChannel(channel.catId, 1)
            val old = channelStates.getValue(channel).value
            channelStates.getValue(channel).value = when {
                fetched == null -> if (old is UiState.Data<*>) {
                    @Suppress("UNCHECKED_CAST")
                    UiState.Stale(old.value as List<io.github.deserthouse.sdgun.data.repository.NewsItem>, "刷新失败，显示已缓存内容")
                } else {
                    UiState.RetryableError("资讯拉取失败，请检查网络后重试")
                }
                fetched.isEmpty() -> UiState.Empty("该频道暂无内容")
                else -> {
                    nextPage.getValue(channel)!!.set(2)  // A24-04：推进游标（原缺陷=停在 1）
                    UiState.Data(fetched)
                }
            }
            loading.getValue(channel)!!.set(false)
        }
    }

    /** 是否还有下一页可取（A24-04：UI 据此渲染加载更多入口） */
    fun hasMore(channel: FocusChannel): Boolean =
        nextPage.getValue(channel)!!.get() <= MAX_PAGES

    /** 加载更多在途标志（UI 按钮态） */
    fun loadingMore(channel: FocusChannel): Boolean =
        loading.getValue(channel)!!.get()

    fun loadMore(channel: FocusChannel) {
        val st = channelStates.getValue(channel).value
        if (st !is UiState.Data<*>) return
        @Suppress("UNCHECKED_CAST")
        val oldList = st.value as List<io.github.deserthouse.sdgun.data.repository.NewsItem>
        if (loading.getValue(channel)!!.get()) return
        val page = nextPage.getValue(channel)!!.get()
        if (page > MAX_PAGES) return
        loading.getValue(channel)!!.set(true)
        scope.launch {
            val fetched = repo.loadChannel(channel.catId, page)
            loading.getValue(channel)!!.set(false)
            when {
                fetched == null -> {
                    // 失败保留旧数据，不推进游标
                    channelStates.getValue(channel).value = UiState.Stale(oldList, "加载失败，可重试")
                }
                fetched.isEmpty() -> {
                    nextPage.getValue(channel)!!.set(MAX_PAGES + 1)
                    channelStates.getValue(channel).value = UiState.Data(oldList)
                }
                else -> {
                    val (merged, added) = NewsRepository.mergeById(oldList, fetched)
                    channelStates.getValue(channel).value = if (added) {
                        nextPage.getValue(channel)!!.set(page + 1)
                        UiState.Data(merged)
                    } else {
                        nextPage.getValue(channel)!!.set(MAX_PAGES + 1)
                        UiState.Data(merged)
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_PAGES = 10
    }
}
