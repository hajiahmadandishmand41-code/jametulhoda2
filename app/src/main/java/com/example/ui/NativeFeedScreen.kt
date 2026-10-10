package com.example.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.LinkedHashMap
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

private const val FEED_API = "https://jametulhoda.vercel.app/api/mobile-feed"
private const val PREFS = "jhd_native_feed"
private const val CACHED_JSON = "feed"
private val JhdGreen = Color(0xFF145C4B)
private val JhdPale = Color(0xFFE6F3ED)
private val JhdBg = Color(0xFFF3F7F4)
private val JhdMuted = Color(0xFF68776F)

private data class FeedItem(
    val id: String, val source: String, val type: String, val title: String,
    val summary: String, val content: String, val author: String,
    val collection: String, val date: String, val url: String, val image: String
)
private data class FeedData(val items: List<FeedItem>, val raw: String)
private enum class FeedTab(val label: String) { HOME("خانه"), NEWS("اخبار"), RESEARCH("پژوهش"), BOOKS("کتابخانه"), MORE("بیشتر") }

private object FeedRepository {
    fun cached(context: Context): FeedData? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CACHED_JSON, null) ?: return null
        return runCatching { parse(raw) }.getOrNull()
    }
    fun save(context: Context, raw: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CACHED_JSON, raw).apply()
    }
    fun fetch(): FeedData {
        val connection = URL(FEED_API).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 6500
            connection.readTimeout = 9000
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("سرور پاسخ مناسب نداد ($status).")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parse(body)
        } finally { connection.disconnect() }
    }
    private fun parse(raw: String): FeedData {
        val root = JSONObject(raw)
        if (!root.optBoolean("ok", false)) throw IOException("پاسخ فید معتبر نیست.")
        val array = root.optJSONArray("items") ?: JSONArray()
        val list = buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val title = row.optString("title").trim()
                if (title.isBlank()) continue
                val summary = row.optString("summary").trim()
                add(FeedItem(
                    id = row.optString("id", "item-$i"), source = row.optString("source", "website"),
                    type = row.optString("type", "news"), title = title, summary = summary,
                    content = row.optString("content", summary).trim().ifBlank { summary },
                    author = row.optString("author").trim(), collection = row.optString("collection_title").trim(),
                    date = row.optString("created_at").trim(), url = row.optString("url").trim(),
                    image = row.optString("image_url").trim()
                ))
            }
        }
        return FeedData(list, raw)
    }
}

@Composable
fun NativeFeedScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { FeedRepository.cached(context) }
    var feed by remember { mutableStateOf(cache?.items ?: emptyList()) }
    var tab by remember { mutableStateOf(FeedTab.HOME) }
    var selected by remember { mutableStateOf<FeedItem?>(null) }
    var search by remember { mutableStateOf("") }
    var searchShown by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            val result = runCatching { withContext(Dispatchers.IO) { FeedRepository.fetch() } }
            result.onSuccess {
                feed = it.items
                FeedRepository.save(context, it.raw)
                if (it.items.isEmpty()) error = "در حال حاضر محتوای منتشرشده‌ای برای نمایش پیدا نشد."
            }.onFailure {
                error = if (feed.isEmpty()) "اتصال به فید برقرار نشد. اینترنت را بررسی کنید و دوباره تلاش نمایید."
                else "به‌روزرسانی ممکن نشد؛ آخرین مطالب ذخیره‌شده نمایش داده می‌شود."
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }
    BackHandler(enabled = selected != null) { selected = null }

    val visible = remember(feed, tab, search) {
        feed.filter { item ->
            val tabMatch = when (tab) {
                FeedTab.HOME -> true
                FeedTab.NEWS -> item.type in setOf("news", "announcement", "event")
                FeedTab.RESEARCH -> item.type in setOf("article", "research", "report", "speech", "qa", "program")
                FeedTab.BOOKS -> item.type == "book"
                FeedTab.MORE -> false
            }
            val q = search.trim()
            tabMatch && (q.isBlank() || item.title.contains(q, true) || item.summary.contains(q, true) || item.author.contains(q, true))
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = JhdBg,
            topBar = {
                NativeHeader(
                    detail = selected != null, searchShown = searchShown, search = search, loading = loading,
                    onBack = { selected = null }, onSearchToggle = {
                        searchShown = !searchShown
                        if (!searchShown) search = ""
                    }, onSearchChange = { search = it }, onRefresh = { refresh() },
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "مدرسه جامعه‌الهدی\nhttps://jametulhoda.vercel.app/")
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری")) }
                    }
                )
            },
            bottomBar = { if (selected == null) NativeNavigation(tab) { tab = it; search = ""; if (it != FeedTab.MORE) selected = null } }
        ) { padding ->
            when {
                selected != null -> DetailPage(
                    item = selected!!, modifier = Modifier.padding(padding),
                    onOpen = { openExternal(context, selected!!.url) },
                    onShare = {
                        val item = selected!!
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, listOf(item.title, item.summary, item.url).filter { it.isNotBlank() }.joinToString("\n\n"))
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "اشتراک‌گذاری مطلب")) }
                    }
                )
                tab == FeedTab.MORE -> MorePage(
                    modifier = Modifier.padding(padding),
                    items = feed.filter { it.type in setOf("course", "lesson", "topic", "audio", "video") },
                    loading = loading,
                    onSite = { openExternal(context, "https://jametulhoda.vercel.app/") },
                    onRefresh = { refresh() },
                    onOpen = { selected = it }
                )
                else -> MainFeed(
                    modifier = Modifier.padding(padding), items = visible, tab = tab,
                    hasCached = feed.isNotEmpty(), loading = loading, error = error,
                    query = search, onRetry = { refresh() }, onOpen = { selected = it },
                )
            }
        }
    }
}

@Composable
private fun NativeHeader(
    detail: Boolean, searchShown: Boolean, search: String, loading: Boolean,
    onBack: () -> Unit, onSearchToggle: () -> Unit, onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit, onShare: () -> Unit
) {
    Surface(color = Color.White, shadowElevation = 2.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (detail) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") }
                else Image(
                    painter = painterResource(R.drawable.ic_jametulhoda_logo),
                    contentDescription = "لوگوی جامعه‌الهدی",
                    modifier = Modifier.size(43.dp),
                    contentScale = ContentScale.Fit
                )
                Column(Modifier.weight(1f)) {
                    Text(if (detail) "جزئیات مطلب" else "جامعه‌الهدی", color = JhdGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (!detail) Text("مرکز علمی، آموزشی و پژوهشی", color = JhdMuted, style = MaterialTheme.typography.labelSmall)
                }
                if (detail) {
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "اشتراک‌گذاری", tint = JhdGreen) }
                } else {
                    IconButton(onClick = onSearchToggle) { Icon(Icons.Default.Search, contentDescription = "جستجو", tint = JhdGreen) }
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        if (loading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "تازه‌سازی", tint = JhdGreen)
                    }
                }
            }
            if (!detail && searchShown) {
                OutlinedTextField(
                    value = search, onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    placeholder = { Text("جستجوی خبر، مقاله یا کتاب") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = JhdGreen)
            HorizontalDivider(color = Color(0xFFE5EBE7))
        }
    }
}

@Composable
private fun NativeNavigation(selected: FeedTab, onSelect: (FeedTab) -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            FeedTab.values().forEach { tab ->
                val icon = when (tab) {
                    FeedTab.HOME -> Icons.Default.Home
                    FeedTab.NEWS -> Icons.Default.Article
                    FeedTab.RESEARCH -> Icons.Default.School
                    FeedTab.BOOKS -> Icons.Default.MenuBook
                    FeedTab.MORE -> Icons.Default.MoreHoriz
                }
                NavigationBarItem(
                    selected = tab == selected, onClick = { onSelect(tab) },
                    icon = { Icon(icon, contentDescription = tab.label) },
                    label = { Text(tab.label, maxLines = 1, fontSize = 10.sp) }
                )
            }
        }
    }
}

@Composable
private fun MainFeed(
    modifier: Modifier, items: List<FeedItem>, tab: FeedTab, hasCached: Boolean,
    loading: Boolean, error: String?, query: String,
    onRetry: () -> Unit, onOpen: (FeedItem) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(13.dp, 14.dp, 13.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { IntroCard(tab = tab, count = items.size) }
        if (!error.isNullOrBlank()) item { StatusCard(error, !hasCached, onRetry) }
        if (items.isEmpty()) item { EmptyCard(loading, query.isNotBlank(), tab, onRetry) }
        else items(items, key = { it.id }) { row -> PostCard(row) { onOpen(row) } }
        item {
            Text("نمایش بومی • محتوای عمومی و منتشرشده", Modifier.fillMaxWidth().padding(6.dp),
                color = JhdMuted, style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun IntroCard(tab: FeedTab, count: Int) {
    val title = when (tab) {
        FeedTab.HOME -> "به جامعه‌الهدی خوش آمدید"
        FeedTab.NEWS -> "خبرها و اطلاعیه‌ها"
        FeedTab.RESEARCH -> "دانش و پژوهش"
        FeedTab.BOOKS -> "کتابخانهٔ آموزشی"
        FeedTab.MORE -> "بیشتر"
    }
    val sub = when (tab) {
        FeedTab.HOME -> "آخرین مطالب مدرسه را در یک محیط سبک دنبال کنید."
        FeedTab.NEWS -> "رویدادها، اطلاعیه‌ها و نوشته‌های تازه."
        FeedTab.RESEARCH -> "مقاله‌ها، گزارش‌ها و محتوای علمی."
        FeedTab.BOOKS -> "کتاب‌ها و منابع آموزشی."
        FeedTab.MORE -> "راه‌های دسترسی و وضعیت اتصال."
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = JhdGreen)) {
        Row(Modifier.fillMaxWidth().padding(19.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(sub, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
                Text(if (count > 0) "$count مطلب در این بخش" else "محتوا از فید سرور دریافت می‌شود", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(
                    when (tab) {
                        FeedTab.HOME -> Icons.Default.Home
                        FeedTab.NEWS -> Icons.Default.Article
                        FeedTab.RESEARCH -> Icons.Default.School
                        FeedTab.BOOKS -> Icons.Default.MenuBook
                        FeedTab.MORE -> Icons.Default.LibraryBooks
                    }, contentDescription = null, tint = Color.White, modifier = Modifier.size(29.dp)
                )
            }
        }
    }
}

@Composable
private fun PostCard(item: FeedItem, onOpen: () -> Unit) {
    when (item.type.lowercase(Locale.ROOT)) {
        "news", "announcement", "event" -> NewsCard(item, onOpen)
        "article", "research", "report", "speech", "program", "qa" -> ReadingCard(item, onOpen)
        "book" -> BookCard(item, onOpen)
        "course", "lesson" -> LessonCard(item, onOpen)
        "topic" -> TopicCard(item, onOpen)
        "audio", "video" -> MediaCard(item, onOpen)
        else -> GeneralContentCard(item, onOpen)
    }
}

@Composable
private fun NewsCard(item: FeedItem, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            if (item.image.isNotBlank()) {
                FeedImage(item.image, item.title, Modifier.fillMaxWidth().height(178.dp), Icons.Default.Article)
            } else {
                Box(
                    Modifier.fillMaxWidth().height(62.dp).background(JhdPale),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = JhdGreen, modifier = Modifier.padding(end = 20.dp).size(31.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContentBadge(typeLabel(item.type), JhdPale, JhdGreen)
                    Spacer(Modifier.weight(1f))
                    if (item.date.isNotBlank()) Text(readableDate(item.date), color = JhdMuted, fontSize = 11.sp, maxLines = 1)
                }
                Text(item.title, color = Color(0xFF203A30), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (item.summary.isNotBlank()) {
                    Text(item.summary, color = Color(0xFF4E5E55), style = MaterialTheme.typography.bodyMedium, lineHeight = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                CardActionLine(label = if (item.type == "event") "جزئیات رویداد" else if (item.type == "announcement") "مشاهدهٔ اطلاعیه" else "ادامهٔ خبر", icon = Icons.Default.ArrowForward)
            }
        }
    }
}

@Composable
private fun ReadingCard(item: FeedItem, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                FeedImage(
                    item.image, item.title,
                    Modifier.width(96.dp).height(116.dp),
                    if (item.type == "speech") Icons.Default.Mic else Icons.Default.Article
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ContentBadge(typeLabel(item.type), Color(0xFFEAF0FF), Color(0xFF244A8F))
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = Color(0xFF244A8F), modifier = Modifier.size(19.dp))
                    }
                    Text(item.title, color = Color(0xFF213653), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (item.summary.isNotBlank()) Text(item.summary, color = JhdMuted, style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = Color(0xFFE9EDF5))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (item.author.isNotBlank()) {
                    Icon(Icons.Default.PersonOutline, contentDescription = null, tint = JhdMuted, modifier = Modifier.size(15.dp))
                    Text(item.author, color = JhdMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.author.isNotBlank() && item.date.isNotBlank()) Text("•", color = JhdMuted, fontSize = 11.sp)
                if (item.date.isNotBlank()) Text(readableDate(item.date), color = JhdMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Spacer(Modifier.weight(1f))
                CardActionLine(label = "مطالعه", icon = Icons.Default.ArrowForward)
            }
        }
    }
}

@Composable
private fun BookCard(item: FeedItem, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeedImage(item.image, item.title, Modifier.width(104.dp).height(145.dp), Icons.Default.MenuBook)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContentBadge("کتابخانه", Color(0xFFF8F0DC), Color(0xFF7A5A20))
                Text(item.title, color = Color(0xFF3A3021), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (item.author.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.PersonOutline, contentDescription = null, tint = Color(0xFF8A754D), modifier = Modifier.size(16.dp))
                        Text(item.author, color = JhdMuted, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (item.summary.isNotBlank()) Text(item.summary, color = Color(0xFF655B4B), style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                CardActionLine(label = "مشخصات کتاب", icon = Icons.Default.ArrowForward)
            }
        }
    }
}

@Composable
private fun LessonCard(item: FeedItem, onOpen: () -> Unit) {
    val isCourse = item.type == "course"
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(5.dp).fillMaxHeight().defaultMinSize(minHeight = 138.dp).background(JhdGreen))
            Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(JhdPale), contentAlignment = Alignment.Center) {
                        Icon(if (isCourse) Icons.Default.LibraryBooks else Icons.Default.School, contentDescription = null, tint = JhdGreen, modifier = Modifier.size(23.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        ContentBadge(if (isCourse) "مجموعهٔ درسی" else "درس آموزشی", JhdPale, JhdGreen)
                        if (item.collection.isNotBlank() && !isCourse) {
                            Text(item.collection, color = JhdMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (item.date.isNotBlank()) Text(readableDate(item.date), color = JhdMuted, fontSize = 10.sp, maxLines = 1)
                }
                Text(item.title, color = Color(0xFF203A30), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (item.summary.isNotBlank()) Text(item.summary, color = Color(0xFF4E5E55), style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (item.author.isNotBlank()) Text("مدرس: " + item.author, color = JhdMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                CardActionLine(label = if (isCourse) "ورود به مجموعه" else "مشاهدهٔ درس", icon = Icons.Default.ArrowForward)
            }
        }
    }
}

@Composable
private fun TopicCard(item: FeedItem, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF0E9FA)), contentAlignment = Alignment.Center) {
                if (item.image.isNotBlank()) {
                    FeedImage(item.image, item.title, Modifier.fillMaxSize(), Icons.Default.LibraryBooks)
                } else {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = Color(0xFF6F4C91), modifier = Modifier.size(29.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContentBadge("موضوع علمی", Color(0xFFF0E9FA), Color(0xFF6F4C91))
                Text(item.title, color = Color(0xFF39294A), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.summary.isNotBlank()) Text(item.summary, color = JhdMuted, style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                CardActionLine(label = "معرفی موضوع", icon = Icons.Default.ArrowForward)
            }
        }
    }
}

@Composable
private fun MediaCard(item: FeedItem, onOpen: () -> Unit) {
    val isVideo = item.type == "video"
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(112.dp).height(94.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFE7EFFA)), contentAlignment = Alignment.Center) {
                    if (item.image.isNotBlank()) FeedImage(item.image, item.title, Modifier.fillMaxSize(), if (isVideo) Icons.Default.Videocam else Icons.Default.Headphones)
                    else Icon(if (isVideo) Icons.Default.Videocam else Icons.Default.Headphones, contentDescription = null, tint = Color(0xFF315C8C), modifier = Modifier.size(34.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ContentBadge(if (isVideo) "ویدیوی آموزشی" else "فایل صوتی", Color(0xFFE7EFFA), Color(0xFF315C8C))
                    Text(item.title, color = Color(0xFF253B52), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (item.author.isNotBlank()) Text(item.author, color = JhdMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.content.isNotBlank()) {
                Text(
                    (if (isVideo) "ویدیوی مرتبط با: " else "صوت مرتبط با: ") + item.content,
                    color = JhdMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = Color(0xFFE8EDF3))
            CardActionLine(label = "جزئیات و پیوند رسانه", icon = Icons.Default.OpenInNew)
        }
    }
}

@Composable
private fun GeneralContentCard(item: FeedItem, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContentBadge(typeLabel(item.type), JhdPale, JhdGreen)
                Spacer(Modifier.weight(1f))
                if (item.date.isNotBlank()) Text(readableDate(item.date), color = JhdMuted, style = MaterialTheme.typography.labelSmall)
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF203A30), maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (item.summary.isNotBlank()) Text(item.summary, color = JhdMuted, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            CardActionLine(label = "جزئیات مطلب", icon = Icons.Default.ArrowForward)
        }
    }
}

@Composable
private fun ContentBadge(label: String, background: Color, foreground: Color) {
    Surface(color = background, shape = RoundedCornerShape(8.dp)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = foreground, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CardActionLine(label: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = JhdGreen, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Icon(icon, contentDescription = null, tint = JhdGreen, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DetailPage(item: FeedItem, modifier: Modifier, onOpen: () -> Unit, onShare: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text(typeLabel(item.type), color = JhdGreen, fontWeight = FontWeight.Bold)
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val byline = listOf(item.author, readableDate(item.date)).filter { it.isNotBlank() }.joinToString(" • ")
                    if (byline.isNotBlank()) Text(byline, color = JhdMuted, style = MaterialTheme.typography.labelMedium)
                    if (item.image.isNotBlank()) FeedImage(item.image, item.title, Modifier.fillMaxWidth().height(230.dp))
                    HorizontalDivider(color = Color(0xFFE6ECE8))
                    Text(item.content.ifBlank { item.summary.ifBlank { "متن کامل در منبع اصلی منتشر شده است." } }, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF30483D), lineHeight = 28.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpen, enabled = item.url.startsWith("https://"), colors = ButtonDefaults.buttonColors(containerColor = JhdGreen)) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("مشاهدهٔ منبع اصلی")
                        }
                        TextButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("اشتراک") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(loading: Boolean, searching: Boolean, tab: FeedTab, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(27.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator(color = JhdGreen) else Icon(Icons.Default.LibraryBooks, null, tint = JhdGreen, modifier = Modifier.size(42.dp))
            Text(when {
                loading -> "در حال دریافت محتوا…"
                searching -> "مطلبی با این عبارت پیدا نشد."
                tab == FeedTab.BOOKS -> "هنوز کتابی برای نمایش دریافت نشده است."
                else -> "در این بخش فعلاً مطلبی موجود نیست."
            }, color = JhdMuted, style = MaterialTheme.typography.bodyMedium)
            if (!loading) Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = JhdGreen)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("تلاش دوباره") }
        }
    }
}

@Composable
private fun StatusCard(message: String, showRetry: Boolean, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5DF))) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(message, color = Color(0xFF684B13), style = MaterialTheme.typography.bodySmall)
            if (showRetry) TextButton(onClick = onRetry) { Text("تلاش دوباره") }
        }
    }
}

@Composable
private fun MorePage(
    modifier: Modifier,
    items: List<FeedItem>,
    loading: Boolean,
    onSite: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (FeedItem) -> Unit
) {
    var selectedType by remember { mutableStateOf("all") }
    val filters = listOf(
        "all" to "همه",
        "lesson" to "درس‌ها",
        "course" to "دوره‌ها",
        "topic" to "موضوعات",
        "audio" to "صوت",
        "video" to "ویدیو"
    )
    val visibleItems = remember(items, selectedType) {
        if (selectedType == "all") items else items.filter { it.type == selectedType }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(13.dp, 14.dp, 13.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { IntroCard(FeedTab.MORE, visibleItems.size) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters, key = { it.first }) { filter ->
                    FilterChip(
                        selected = selectedType == filter.first,
                        onClick = { selectedType = filter.first },
                        label = { Text(filter.second) }
                    )
                }
            }
        }
        if (visibleItems.isEmpty()) {
            item { EmptyCard(loading, false, FeedTab.MORE, onRefresh) }
        } else {
            items(visibleItems, key = { it.id }) { item -> PostCard(item) { onOpen(item) } }
        }
        item { MoreCard("وب‌سایت رسمی جامعه‌الهدی", "برای دسترسی به همهٔ صفحه‌ها و اطلاعات تکمیلی، وب‌سایت رسمی را باز کنید.", "باز کردن وب‌سایت", onSite) }
        item { MoreCard("تازه‌سازی محتوا", "آخرین فید موفق در تلفن نگهداری می‌شود تا متن‌های قبلی هنگام قطع اینترنت در دسترس بماند.", "تلاش برای به‌روزرسانی", onRefresh) }
        item {
            Text("این برنامه محتوای عمومی منتشرشدهٔ جامعه‌الهدی را از API وب‌سایت دریافت می‌کند.", Modifier.padding(4.dp), color = JhdMuted, style = MaterialTheme.typography.bodySmall, lineHeight = 22.sp)
        }
    }
}@Composable
private fun MoreCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, color = JhdMuted, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
            TextButton(onClick = onClick) { Text(action); Spacer(Modifier.width(5.dp)); Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun FeedImage(
    url: String,
    description: String,
    modifier: Modifier = Modifier,
    placeholder: ImageVector = Icons.Default.Article
) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = if (url.startsWith("https://", true)) {
            withContext(Dispatchers.IO) { runCatching { TinyImageCache.get(url) }.getOrNull() }
        } else null
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(11.dp)).background(JhdPale),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = description, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(placeholder, contentDescription = description, tint = JhdGreen.copy(alpha = .8f), modifier = Modifier.size(31.dp))
        }
    }
}

private object TinyImageCache {
    private val cache = LinkedHashMap<String, Bitmap>(6, .75f, true)
    fun get(url: String): Bitmap? {
        if (!url.startsWith("https://", true)) return null
        synchronized(cache) { cache[url]?.let { return it } }
        val bitmap = download(url) ?: return null
        synchronized(cache) {
            cache[url] = bitmap
            while (cache.size > 4) cache.remove(cache.keys.iterator().next())
        }
        return bitmap
    }
    private fun download(url: String): Bitmap? {
        val connection = URL(url).openConnection() as? HttpsURLConnection ?: return null
        return try {
            connection.connectTimeout = 4500
            connection.readTimeout = 6000
            connection.instanceFollowRedirects = true
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0 || total + count > 3 * 1024 * 1024) break
                    output.write(buffer, 0, count); total += count
                }
                val bytes = output.toByteArray()
                if (bytes.isEmpty()) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                var sample = 1
                while (bounds.outWidth / sample > 1100 || bounds.outHeight / sample > 1100) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                    inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565
                })
            }
        } catch (_: Exception) { null } finally { connection.disconnect() }
    }
}

private fun readableDate(value: String): String = value.takeIf { it.isNotBlank() }?.replace('T', ' ')?.take(10).orEmpty()
private fun typeLabel(type: String): String = when (type.lowercase(Locale.ROOT)) {
    "news" -> "خبر"; "announcement" -> "اطلاعیه"; "event" -> "رویداد"
    "article" -> "مقاله"; "research" -> "پژوهش"; "report" -> "گزارش"; "speech" -> "سخنرانی"
    "program" -> "برنامه"; "qa" -> "پرسش‌وپاسخ"; "book" -> "کتاب"
    "course" -> "دوره"; "lesson" -> "درس"; "topic" -> "موضوع"; "audio" -> "صوت"; "video" -> "ویدیو"
    else -> "مطلب"
}
private fun openExternal(context: Context, value: String) {
    if (!value.startsWith("https://", true)) {
        Toast.makeText(context, "پیوند امن در دسترس نیست.", Toast.LENGTH_SHORT).show(); return
    }
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "مرورگری پیدا نشد.", Toast.LENGTH_SHORT).show() }
    catch (_: Exception) { Toast.makeText(context, "باز کردن پیوند ممکن نشد.", Toast.LENGTH_SHORT).show() }
}
