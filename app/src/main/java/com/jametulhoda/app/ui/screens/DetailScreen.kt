package com.jametulhoda.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jametulhoda.app.R
import com.jametulhoda.app.data.ApiException
import com.jametulhoda.app.data.AppRepository
import com.jametulhoda.app.data.Attachment
import com.jametulhoda.app.data.ContentItem
import com.jametulhoda.app.ui.ContentCard
import com.jametulhoda.app.ui.EmptyBox
import com.jametulhoda.app.ui.ErrorBox
import com.jametulhoda.app.ui.HtmlText
import com.jametulhoda.app.ui.LikeButton
import com.jametulhoda.app.ui.LoadingBox
import com.jametulhoda.app.ui.NetImage
import com.jametulhoda.app.ui.SectionHeader
import com.jametulhoda.app.ui.TypeBadge
import com.jametulhoda.app.ui.typeLabelOf
import com.jametulhoda.app.util.Downloader
import com.jametulhoda.app.util.PersianDate
import kotlinx.coroutines.launch

private fun shareContent(context: android.content.Context, title: String, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, if (url.isNotBlank()) "$title\n$url" else title)
    }
    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
}

@Composable
private fun EngagementBar(
    liked: Boolean?,
    likeCount: Int,
    onLike: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LikeButton(liked = liked, count = likeCount, onToggle = onLike)
        IconButton(onClick = onShare) {
            Icon(Icons.Default.Share, contentDescription = "اشتراک‌گذاری", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** پیوست‌ها: صوت/ویدیو داخل برنامه پخش می‌شوند؛ اسناد دانلود/باز/اشتراک. */
@Composable
private fun AttachmentSection(
    attachments: List<Attachment>,
    onPlayback: (playerKind: String, url: String, title: String) -> Unit,
) {
    if (attachments.isEmpty()) return
    Text("فایل‌های پیوست", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    Spacer(Modifier.height(8.dp))
    attachments.forEach { att ->
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(att.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        kindLabel(att.kind) + (if (att.size > 0) " • ${sizeLabel(att.size)}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (att.kind) {
                    "audio", "video" -> TextButton(onClick = {
                        onPlayback(att.kind, att.url, att.title)
                    }) { Text("پخش") }
                    else -> DocumentActions(att.url, att.title)
                }
            }
        }
    }
}

@Composable
private fun DocumentActions(url: String, title: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarLocal = androidx.compose.material3.SnackbarHostState()
    Row {
        TextButton(onClick = {
            scope.launch {
                try {
                    val d = Downloader.download(context, url, title)
                    Downloader.open(context, d)
                } catch (e: Exception) {
                    // بازکردن ناموفق: سکوت
                }
            }
        }) { Text("باز کردن") }
        TextButton(onClick = {
            scope.launch {
                try {
                    val d = Downloader.download(context, url, title)
                    Downloader.share(context, d)
                } catch (e: Exception) {
                    // اشتراک ناموفق: سکوت
                }
            }
        }) { Text("اشتراک") }
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "audio" -> "فایل صوتی"
    "video" -> "فایل ویدیویی"
    "image" -> "تصویر"
    else -> "سند"
}

private fun sizeLabel(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    return if (kb >= 1024) String.format(java.util.Locale.US, "%.1f مگابایت", kb / 1024.0)
    else String.format(java.util.Locale.US, "%.0f کیلوبایت", kb)
}

/**
 * صفحهٔ نمایش کامل محتوا برای همهٔ انواع:
 * post (خبر/مقاله/پژوهش/اطلاعیه/رویداد)، book، lesson، topic (زیرموضوع‌ها + محتوای مرتبط).
 */
@Composable
fun DetailScreen(
    repo: AppRepository,
    kind: String,
    id: Int,
    slug: String,
    onPlayback: (playerKind: String, url: String, title: String) -> Unit,
    onItem: (ContentItem) -> Unit,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    var item by remember { mutableStateOf<ContentItem?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var liked by remember { mutableStateOf<Boolean?>(null) }
    var likeCount by remember { mutableStateOf(0) }
    val context = LocalContext.current

    suspend fun load() {
        loading = true
        error = null
        try {
            val d = when (kind) {
                "book" -> repo.bookDetail(slug, id)
                "lesson" -> repo.lessonDetail(slug, id)
                "topic" -> repo.topicDetail(slug, id)
                else -> repo.postDetail(slug, id)
            }
            item = d
            liked = d.liked
            likeCount = d.likeCount
            loading = false
        } catch (e: Exception) {
            error = e.message ?: "محتوا دریافت نشد."
            loading = false
        }
    }

    LaunchedEffect(kind, id, slug) { load() }

    val d = item
    when {
        loading -> LoadingBox()
        error != null -> ErrorBox(message = error ?: "", onRetry = { scope.launch { load() } })
        d != null -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(typeLabelOf(d))
                Spacer(Modifier.width(8.dp))
                if (d.publishedAt.isNotBlank()) {
                    Text(
                        PersianDate.format(d.publishedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(d.title, style = MaterialTheme.typography.headlineSmall)
            if (d.author.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(d.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (d.summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(d.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (kind != "topic") {
                Spacer(Modifier.height(12.dp))
                EngagementBar(
                    liked = liked,
                    likeCount = likeCount,
                    onLike = {
                        scope.launch {
                            try {
                                val state = repo.setLike(kind, d.id, liked != true)
                                liked = state.liked
                                likeCount = state.likeCount
                            } catch (e: ApiException) {
                                if (e.status == 401) {
                                    snackbar.showSnackbar("برای پسندیدن محتوا ابتدا وارد حساب کاربری شوید.")
                                } else {
                                    snackbar.showSnackbar(e.message.ifBlank { "خطا در ثبت پسند." })
                                }
                            } catch (e: Exception) {
                                snackbar.showSnackbar("اتصال به سرور برقرار نشد؛ بعداً تلاش کنید.")
                            }
                        }
                    },
                    onShare = { shareContent(context, d.title, d.url) },
                )
            }

            // فایل‌های اصلی (PDF/صوت/ویدیو) برای کتاب و درس
            val primaryFiles = mutableListOf<Triple<String, String, String>>()
            if (d.pdfUrl.isNotBlank()) primaryFiles.add(Triple("pdf", d.pdfUrl, "فایل PDF"))
            if (d.audioUrl.isNotBlank()) primaryFiles.add(Triple("audio", d.audioUrl, "فایل صوتی"))
            if (d.videoUrl.isNotBlank()) primaryFiles.add(Triple("video", d.videoUrl, "فایل ویدیویی"))
            if (d.wordUrl.isNotBlank()) primaryFiles.add(Triple("doc", d.wordUrl, "فایل Word"))
            if (primaryFiles.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                primaryFiles.forEach { (playerKind, url, label) ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            if (playerKind == "doc") {
                                DocumentActions(url, d.title)
                            } else {
                                TextButton(onClick = { onPlayback(playerKind, url, d.title) }) {
                                    Text(if (playerKind == "pdf") "مشاهده" else "پخش")
                                }
                            }
                        }
                    }
                }
            }

            if (d.contentHtml.isNotBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                HtmlText(html = d.contentHtml)
            } else if (d.contentText.isNotBlank()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(d.contentText, style = MaterialTheme.typography.bodyLarge)
            }

            if (d.toc.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("فهرست مطالب", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(d.toc, style = MaterialTheme.typography.bodyMedium)
            }

            if (d.sources.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("منابع", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(d.sources, style = MaterialTheme.typography.bodyMedium)
            }

            AttachmentSection(d.attachments, onPlayback)

            // گالری تصاویر
            if (d.gallery.isNotEmpty()) {
                Text("گالری تصاویر", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(8.dp))
                d.gallery.forEach { image ->
                    NetImage(
                        url = image.url,
                        contentDescription = image.alt,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(200.dp),
                    )
                }
            }

            // موضوع‌ها
            if (d.topics.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.topics.take(4).forEach { topic -> TypeBadge(topic.name) }
                }
            }

            // زیرموضوع‌ها/محتوای مرتبط (برای topic)
            if (d.children.isNotEmpty()) {
                SectionHeader("محتوای مرتبط")
                d.children.forEach { child ->
                    ContentCard(item = child, onClick = { onItem(child) })
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (d.pages > 0) {
                Spacer(Modifier.height(8.dp))
                Text("${d.pages} صفحه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }

        else -> EmptyBox("محتوایی یافت نشد.")
    }
}
