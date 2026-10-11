package com.jametulhoda.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jametulhoda.app.R
import com.jametulhoda.app.data.AppRepository
import com.jametulhoda.app.data.ContentItem
import com.jametulhoda.app.data.PagedItems
import com.jametulhoda.app.ui.ContentCard
import com.jametulhoda.app.ui.EmptyBox
import com.jametulhoda.app.ui.ErrorBox
import com.jametulhoda.app.ui.LoadingBox
import com.jametulhoda.app.ui.NetImage
import kotlinx.coroutines.launch

/** ماشین صفحه‌بندی مشترک برای همهٔ فهرست‌ها (load-more + تشخیص انتهای لیست). */
class PagingState(val load: suspend (page: Int) -> PagedItems) {
    var items by mutableStateOf<List<ContentItem>>(emptyList())
    var page by mutableStateOf(1)
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasMore by mutableStateOf(false)

    suspend fun refresh() {
        if (loading) return
        loading = true
        error = null
        try {
            val result = load(1)
            items = result.items
            page = 1
            hasMore = result.hasMore
        } catch (e: Exception) {
            if (items.isEmpty()) error = e.message ?: "خطا در دریافت اطلاعات."
        } finally {
            loading = false
        }
    }

    suspend fun loadMore() {
        if (loadingMore || loading || !hasMore) return
        loadingMore = true
        try {
            val result = load(page + 1)
            items = items + result.items
            page += 1
            hasMore = result.hasMore
        } catch (e: Exception) {
            // خطای بارگذاری تکمیلی بی‌صدا می‌ماند؛ دکمهٔ تلاش مجدد فعال است.
        } finally {
            loadingMore = false
        }
    }
}

@Composable
fun rememberPaging(load: suspend (page: Int) -> PagedItems): PagingState {
    val state = remember { PagingState(load) }
    LaunchedEffect(Unit) { state.refresh() }
    return state
}

/** رفتار «بارگذاری بیشتر» هنگام رسیدن به انتهای لیست. */
@Composable
fun InfiniteScrollEffect(state: LazyListState, paging: PagingState) {
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            scope.launch { paging.loadMore() }
        }
    }
}

@Composable
fun PagingFooter(paging: PagingState) {
    when {
        paging.loadingMore -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        paging.hasMore -> {
            val scope = rememberCoroutineScope()
            Button(
                onClick = { scope.launch { paging.loadMore() } },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("نمایش موارد بیشتر") }
        }
    }
}

private fun refresh(scope: kotlinx.coroutines.CoroutineScope, paging: PagingState) {
    scope.launch { paging.refresh() }
}

/** فهرست پست‌ها (خبر/مقاله/پژوهش/اطلاعیه/رویداد) با صفحه‌بندی و فیلتر. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScreen(
    repo: AppRepository,
    type: String,
    onItem: (ContentItem) -> Unit,
) {
    // برای تب اخبار: زیرفیلتر خبر/اطلاعیه/رویداد (همان دسته‌بندی سایت).
    var filter by remember { mutableStateOf(type) }

    Column(Modifier.fillMaxSize()) {
        if (type == "news") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = filter == "news", onClick = { filter = "news" }, label = { Text("خبرها") })
                FilterChip(selected = filter == "announcement", onClick = { filter = "announcement" }, label = { Text("اطلاعیه‌ها") })
                FilterChip(selected = filter == "event", onClick = { filter = "event" }, label = { Text("رویدادها") })
            }
        }

        androidx.compose.runtime.key(filter) {
            val paging = rememberPaging { page -> repo.posts(filter, page) }
            val scope = rememberCoroutineScope()
            val listState = rememberLazyListState()
            InfiniteScrollEffect(listState, paging)

            when {
                paging.loading && paging.items.isEmpty() -> LoadingBox()
                paging.error != null && paging.items.isEmpty() -> ErrorBox(
                    message = paging.error ?: "",
                    onRetry = { refresh(scope, paging) },
                )
                paging.items.isEmpty() -> EmptyBox("محتوایی در این بخش یافت نشد.")
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(paging.items, key = { _, it -> it.key }) { _, item ->
                        ContentCard(item = item, onClick = { onItem(item) })
                    }
                    item { PagingFooter(paging) }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

/** فهرست کتاب‌ها با صفحه‌بندی. */
@Composable
fun BookListScreen(
    repo: AppRepository,
    onItem: (ContentItem) -> Unit,
) {
    val paging = rememberPaging { page -> repo.books(page) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    InfiniteScrollEffect(listState, paging)

    when {
        paging.loading && paging.items.isEmpty() -> LoadingBox()
        paging.error != null && paging.items.isEmpty() -> ErrorBox(
            message = paging.error ?: "",
            onRetry = { refresh(scope, paging) },
        )
        paging.items.isEmpty() -> EmptyBox("کتابی یافت نشد.")
        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(paging.items, key = { _, it -> it.key }) { _, book ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    ContentCard(item = book, onClick = { onItem(book) })
                }
            }
            item { PagingFooter(paging) }
        }
    }
}

/** فهرست درس‌ها با صفحه‌بندی. */
@Composable
fun LessonsScreen(
    repo: AppRepository,
    onItem: (ContentItem) -> Unit,
) {
    val paging = rememberPaging { page -> repo.lessons(page) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    InfiniteScrollEffect(listState, paging)

    when {
        paging.loading && paging.items.isEmpty() -> LoadingBox()
        paging.error != null && paging.items.isEmpty() -> ErrorBox(
            message = paging.error ?: "",
            onRetry = { refresh(scope, paging) },
        )
        paging.items.isEmpty() -> EmptyBox("درسی یافت نشد.")
        else -> LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(paging.items, key = { _, it -> it.key }) { _, lesson ->
                ContentCard(item = lesson, onClick = { onItem(lesson) })
            }
            item { PagingFooter(paging) }
        }
    }
}

/** تب‌های رسانه: صوت / ویدیو. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaTabsScreen(
    repo: AppRepository,
    onPlay: (ContentItem) -> Unit,
) {
    var kind by remember { mutableStateOf("audio") }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = kind == "audio", onClick = { kind = "audio" }, label = { Text("صوت‌ها") })
            FilterChip(selected = kind == "video", onClick = { kind = "video" }, label = { Text("ویدیوها") })
        }

        androidx.compose.runtime.key(kind) {
            val paging = rememberPaging { page -> repo.media(kind, page) }
            val scope = rememberCoroutineScope()
            val listState = rememberLazyListState()
            InfiniteScrollEffect(listState, paging)

            when {
                paging.loading && paging.items.isEmpty() -> LoadingBox()
                paging.error != null && paging.items.isEmpty() -> ErrorBox(
                    message = paging.error ?: "",
                    onRetry = { refresh(scope, paging) },
                )
                paging.items.isEmpty() -> EmptyBox("رسانه‌ای یافت نشد.")
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(paging.items, key = { _, it -> it.key }) { _, media ->
                        ContentCard(item = media, onClick = { onPlay(media) })
                    }
                    item { PagingFooter(paging) }
                }
            }
        }
    }
}

/** فهرست موضوعات (Topic). */
@Composable
fun TopicsListScreen(
    repo: AppRepository,
    onTopic: (ContentItem) -> Unit,
) {
    var topics by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            topics = repo.topics()
            loading = false
        } catch (e: Exception) {
            error = e.message ?: "خطا در دریافت موضوعات."
            loading = false
        }
    }

    when {
        loading -> LoadingBox()
        error != null -> ErrorBox(message = error ?: "", onRetry = {
            scope.launch {
                loading = true
                error = null
                try {
                    topics = repo.topics()
                    loading = false
                } catch (e: Exception) {
                    error = e.message ?: "خطا در دریافت موضوعات."
                    loading = false
                }
            }
        })
        topics.isEmpty() -> EmptyBox("موضوعی ثبت نشده است.")
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(topics, key = { _, it -> it.key }) { _, topic ->
                Card(
                    onClick = { onTopic(topic) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetImage(
                            url = topic.imageUrl,
                            contentDescription = topic.title,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                            placeholder = R.drawable.ic_topic,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(topic.title, style = MaterialTheme.typography.titleSmall)
                            if (topic.summary.isNotBlank()) {
                                Text(
                                    topic.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
