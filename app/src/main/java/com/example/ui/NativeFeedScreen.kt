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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
    val date: String, val url: String, val image: String
)
private data class FeedMeta(val facebookConfigured: Boolean, val facebookConnected: Boolean, val facebookUrl: String)
private data class FeedData(val items: List<FeedItem>, val meta: FeedMeta, val raw: String)
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
                    author = row.optString("author").trim(), date = row.optString("created_at").trim(),
                    url = row.optString("url").trim(), image = row.optString("image_url").trim()
                ))
            }
        }
        val meta = root.optJSONObject("meta") ?: JSONObject()
        return FeedData(list, FeedMeta(
            meta.optBoolean("facebook_configured", false),
            meta.optBoolean("facebook_connected", false),
            meta.optString("facebook_page_url").trim()
        ), raw)
    }
}

@Composable
fun NativeFeedScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache = remember { FeedRepository.cached(context) }
    var feed by remember { mutableStateOf(cache?.items ?: emptyList()) }
    var meta by remember { mutableStateOf(cache?.meta ?: FeedMeta(false, false, "")) }
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
                meta = it.meta
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
                FeedTab.NEWS -> item.type in setOf("news", "announcement", "event", "facebook")
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
                    modifier = Modifier.padding(padding), meta = meta,
                    onSite = { openExternal(context, "https://jametulhoda.vercel.app/") },
                    onFacebook = {
                        if (meta.facebookUrl.isNotBlank()) openExternal(context, meta.facebookUrl)
                        else Toast.makeText(context, "نشانی صفحهٔ فیسبوک هنوز تنظیم نشده است.", Toast.LENGTH_SHORT).show()
                    },
                    onRefresh = { refresh() }
                )
                else -> MainFeed(
                    modifier = Modifier.padding(padding), items = visible, tab = tab,
                    hasCached = feed.isNotEmpty(), loading = loading, error = error, meta = meta,
                    query = search, onRetry = { refresh() }, onOpen = { selected = it },
                    onFacebook = {
                        if (meta.facebookUrl.isNotBlank()) openExternal(context, meta.facebookUrl)
                        else Toast.makeText(context, "نشانی صفحهٔ فیسبوک هنوز تنظیم نشده است.", Toast.LENGTH_SHORT).show()
                    }
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
                else Box(Modifier.size(43.dp).clip(CircleShape).background(JhdGreen), contentAlignment = Alignment.Center) {
                    Text("ج", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (detail) "جزئیات مطلب" else "مدرسه جامعه‌الهدی", color = JhdGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (!detail) Text("آموزش • پژوهش • اطلاع‌رسانی", color = JhdMuted, style = MaterialTheme.typography.labelSmall)
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
    loading: Boolean, error: String?, meta: FeedMeta, query: String,
    onRetry: () -> Unit, onOpen: (FeedItem) -> Unit, onFacebook: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(13.dp, 14.dp, 13.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { IntroCard(tab = tab, count = items.size) }
        if (tab == FeedTab.HOME && !meta.facebookConfigured) item { FacebookNote(onFacebook) }
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
private fun FacebookNote(onFacebook: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF))) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("اتصال صفحهٔ فیسبوک", color = Color(0xFF184F91), fontWeight = FontWeight.Bold)
            Text("پست‌های فیسبوک پس از تنظیم مجازِ صفحه در سرور فعال می‌شوند؛ رمز یا توکن در برنامه قرار نمی‌گیرد.", color = Color(0xFF33516E), style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp)
            TextButton(onClick = onFacebook) { Text("باز کردن فیسبوک") }
        }
    }
}

@Composable
private fun PostCard(item: FeedItem, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(39.dp).clip(CircleShape).background(if (item.source == "facebook") Color(0xFF1877F2) else JhdPale), contentAlignment = Alignment.Center) {
                    Text(if (item.source == "facebook") "f" else "ج", color = if (item.source == "facebook") Color.White else JhdGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (item.source == "facebook") "پست فیسبوک" else "مدرسه جامعه‌الهدی", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    val byline = listOf(item.author, readableDate(item.date)).filter { it.isNotBlank() }.joinToString(" • ")
                    Text(byline.ifBlank { typeLabel(item.type) }, color = JhdMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(typeLabel(item.type), Modifier.clip(RoundedCornerShape(8.dp)).background(JhdPale).padding(horizontal = 7.dp, vertical = 4.dp), color = JhdGreen, fontSize = 10.sp, maxLines = 1)
            }
            Text(item.title, Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF203A30), maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (item.image.isNotBlank()) FeedImage(item.image, item.title, Modifier.fillMaxWidth().height(195.dp).padding(top = 5.dp))
            if (item.summary.isNotBlank()) Text(item.summary, Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp), color = Color(0xFF46574F), style = MaterialTheme.typography.bodyMedium, lineHeight = 23.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = Color(0xFFE6ECE8))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpen) { Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("ادامهٔ مطلب") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpen) { Text("جزئیات") }
            }
        }
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
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(if (item.source == "facebook") "مشاهده در فیسبوک" else "منبع اصلی")
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
private fun MorePage(modifier: Modifier, meta: FeedMeta, onSite: () -> Unit, onFacebook: () -> Unit, onRefresh: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { IntroCard(FeedTab.MORE, 0) }
        item { MoreCard("وب‌سایت جامعه‌الهدی", "صفحه‌های کامل، درس‌ها و اطلاعات تکمیلی.", "باز کردن وب‌سایت", onSite) }
        item { MoreCard("صفحهٔ فیسبوک", if (meta.facebookConnected) "دریافت پست‌های صفحه از راه اتصال رسمی فعال است." else "خواندن خودکار پست‌ها به اتصال مجاز Meta در سرور نیاز دارد.", "باز کردن فیسبوک", onFacebook) }
        item { MoreCard("حالت آفلاین", "آخرین خوراک موفق در تلفن نگهداری می‌شود تا متن‌های قبلی هنگام قطع اینترنت در دسترس بماند.", "تازه‌سازی محتوا", onRefresh) }
        item {
            Text("این اپلیکیشن رابط بومی اندروید است، نه یک وب‌سایت داخل WebView. اطلاعات فیسبوک فقط از راه اتصال رسمی و امن سمت سرور خوانده می‌شود.", Modifier.padding(4.dp), color = JhdMuted, style = MaterialTheme.typography.bodySmall, lineHeight = 22.sp)
        }
    }
}

@Composable
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
private fun FeedImage(url: String, description: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) { runCatching { TinyImageCache.get(url) }.getOrNull() }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = description, modifier = modifier.clip(RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
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
    "facebook" -> "فیسبوک"; "news" -> "خبر"; "announcement" -> "اطلاعیه"; "event" -> "رویداد"
    "article" -> "مقاله"; "research" -> "پژوهش"; "report" -> "گزارش"; "speech" -> "سخنرانی"
    "program" -> "برنامه"; "qa" -> "پرسش‌وپاسخ"; "book" -> "کتاب"; else -> "مطلب"
}
private fun openExternal(context: Context, value: String) {
    if (!value.startsWith("https://", true)) {
        Toast.makeText(context, "پیوند امن در دسترس نیست.", Toast.LENGTH_SHORT).show(); return
    }
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value))) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "مرورگری پیدا نشد.", Toast.LENGTH_SHORT).show() }
    catch (_: Exception) { Toast.makeText(context, "باز کردن پیوند ممکن نشد.", Toast.LENGTH_SHORT).show() }
}
