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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jametulhoda.app.R
import com.jametulhoda.app.data.AppRepository
import com.jametulhoda.app.data.ContentItem
import com.jametulhoda.app.data.HomeData
import com.jametulhoda.app.ui.ContentCard
import com.jametulhoda.app.ui.EmptyBox
import com.jametulhoda.app.ui.ErrorBox
import com.jametulhoda.app.ui.LoadingBox
import com.jametulhoda.app.ui.NetImage
import com.jametulhoda.app.ui.Route
import com.jametulhoda.app.ui.SectionHeader
import kotlinx.coroutines.launch

/**
 * خانه: بنرها، معرفی سایت، و بخش‌های تجمیعی `sections` از `/api/mobile/home`
 * (خبر، مقاله، پژوهش، اطلاعیه، رویداد، سخنرانی، کتاب، درس، موضوع، رسانه).
 */
@Composable
fun HomeScreen(
    repo: AppRepository,
    onItem: (ContentItem) -> Unit,
    onRoute: (Route) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var home by remember { mutableStateOf<HomeData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                home = repo.home()
                loading = false
            } catch (e: Exception) {
                error = e.message ?: "خطا در دریافت اطلاعات."
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val data = home
    when {
        loading && data == null -> LoadingBox()
        error != null && data == null -> ErrorBox(message = error ?: "", onRetry = ::load)
        data != null -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // بنرهای فعال سایت
            items(data.banners, key = { "bn${it.id}" }) { banner ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(banner.title, style = MaterialTheme.typography.titleMedium)
                        if (banner.content.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                banner.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            // معرفی کوتاه سایت
            if (data.site.description.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(data.site.name, style = MaterialTheme.typography.titleLarge)
                            if (data.site.slogan.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    data.site.slogan,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(data.site.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // میان‌برهای بخش‌ها
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShortcutButton("درباره ما") { onRoute(Route.About) }
                    ShortcutButton("تماس") { onRoute(Route.Contact) }
                    ShortcutButton("جستجو") { onRoute(Route.Search) }
                }
            }

            // مطالب ویژه
            val featured = data.featured
            if (featured.isNotEmpty()) {
                item { SectionHeader("ویژه") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(featured, key = { "f${it.key}" }) { item ->
                            FeaturedCard(item, Modifier.width(260.dp)) { onItem(item) }
                        }
                    }
                }
            }

            section("news", "آخرین اخبار", data, onItem, onRoute, Route.PostList("news", "اخبار"))
            section("articles", "مقالات", data, onItem, onRoute, Route.PostList("article", "مقالات"))
            section("research", "پژوهش‌ها", data, onItem, onRoute, Route.PostList("research", "پژوهش‌ها"))
            section("speeches", "سخنرانی‌ها", data, onItem, onRoute, Route.PostList("speech", "سخنرانی‌ها"))

            // کتاب‌ها
            val books = data.sections["books"].orEmpty()
            if (books.isNotEmpty()) {
                item { SectionHeader("کتاب‌های برگزیده") { onRoute(Route.Books) } }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(books, key = { "b${it.key}" }) { book ->
                            FeaturedCard(book, Modifier.width(150.dp)) { onItem(book) }
                        }
                    }
                }
            }

            // درس‌ها
            val lessons = data.sections["lessons"].orEmpty()
            if (lessons.isNotEmpty()) {
                item { SectionHeader("درس‌های اخیر") { onRoute(Route.Lessons) } }
                items(lessons, key = { "l${it.key}" }) { lesson ->
                    ContentCard(item = lesson, onClick = { onItem(lesson) })
                }
            }

            // رسانه (صوت/ویدیو)
            val media = data.sections["media"].orEmpty()
            if (media.isNotEmpty()) {
                item { SectionHeader("تازه‌ترین رسانه‌ها") { onRoute(Route.MediaTabs) } }
                items(media, key = { "m${it.key}" }) { m ->
                    ContentCard(item = m, onClick = { onItem(m) })
                }
            }

            section("announcements", "اطلاعیه‌ها", data, onItem, onRoute, Route.PostList("announcement", "اطلاعیه‌ها"))
            section("events", "برنامه‌ها و رویدادها", data, onItem, onRoute, Route.PostList("event", "رویدادها"))

            item { Spacer(Modifier.height(16.dp)) }
        }

        else -> EmptyBox("هنوز محتوایی منتشر نشده است.")
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    key: String,
    title: String,
    data: HomeData,
    onItem: (ContentItem) -> Unit,
    onRoute: (Route) -> Unit,
    moreRoute: Route,
) {
    val list = data.sections[key].orEmpty()
    if (list.isEmpty()) return
    item { SectionHeader(title) { onRoute(moreRoute) } }
    items(list, key = { "$key-${it.key}" }) { item ->
        ContentCard(item = item, onClick = { onItem(item) })
    }
}

@Composable
private fun ShortcutButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
    ) { Text(text) }
}

@Composable
fun FeaturedCard(item: ContentItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column {
            NetImage(
                url = item.imageUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                placeholder = if (item.kind == "book") R.drawable.ic_book else R.drawable.ic_image,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
            Column(Modifier.padding(12.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                if (item.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
