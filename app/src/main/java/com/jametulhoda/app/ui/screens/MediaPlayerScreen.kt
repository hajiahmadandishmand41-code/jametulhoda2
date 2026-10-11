package com.jametulhoda.app.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jametulhoda.app.R
import com.jametulhoda.app.ui.ErrorBox
import com.jametulhoda.app.ui.NetImage
import com.jametulhoda.app.util.Downloader
import com.jametulhoda.app.util.PersianDate
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * پخش‌کنندهٔ صوت/ویدیو با Media3 + نمایش‌گر PDF داخلی اندروید.
 * همهٔ حالت‌ها: پخش جریانی، دانلود فایل، بازکردن و اشتراک‌گذاری.
 */
@Composable
fun PlaybackScreen(
    playerKind: String,
    url: String,
    title: String,
    snackbar: SnackbarHostState,
) {
    when (playerKind) {
        "pdf" -> PdfViewerScreen(url, title, snackbar)
        "video" -> VideoPlayerScreen(url, title, snackbar)
        else -> AudioPlayerScreen(url, title, snackbar)
    }
}

@Composable
private fun DownloadBar(
    url: String,
    title: String,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf<File?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = {
            if (busy) return@Button
            busy = true
            scope.launch {
                try {
                    val result = Downloader.download(context, url, title)
                    downloaded = result.file
                    snackbar.showSnackbar("فایل ذخیره شد.")
                } catch (e: Exception) {
                    snackbar.showSnackbar(e.message ?: "دانلود ناموفق بود.")
                } finally {
                    busy = false
                }
            }
        }, enabled = !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("دانلود")
        }
        val file = downloaded
        if (file != null) {
            Button(onClick = {
                val d = toDownloaded(context, file)
                Downloader.open(context, d)
            }) { Text("باز کردن") }
            Button(onClick = {
                val d = toDownloaded(context, file)
                Downloader.share(context, d)
            }) { Text("اشتراک") }
        }
    }
}

private fun toDownloaded(context: android.content.Context, file: File): Downloader.Downloaded =
    Downloader.Downloaded(
        file,
        androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file),
        Downloader.mimeOf(file.name),
    )

@Composable
private fun AudioPlayerScreen(
    url: String,
    title: String,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            playing = player.isPlaying
            delay(500)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        NetImage(
            url = "",
            contentDescription = title,
            placeholder = R.drawable.ic_audio,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(160.dp).clip(RoundedCornerShape(24.dp)),
        )
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Slider(
            value = if (duration > 0) position.toFloat() / duration else 0f,
            onValueChange = { fraction ->
                if (duration > 0) player.seekTo((fraction * duration).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(PersianDate.duration(position), style = MaterialTheme.typography.bodySmall)
            Text(PersianDate.duration(duration), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { player.seekBack() }) {
                Text("⟲", style = MaterialTheme.typography.headlineSmall)
            }
            Button(onClick = {
                if (player.isPlaying) player.pause() else player.play()
            }) {
                Text(if (player.isPlaying) "⏸" else "▶", style = MaterialTheme.typography.headlineSmall)
            }
            IconButton(onClick = { player.seekForward() }) {
                Text("⟳", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        DownloadBar(url, title, snackbar)
    }
}

@Composable
private fun VideoPlayerScreen(
    url: String,
    title: String,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            DownloadBar(url, title, snackbar)
        }
    }
}

@Composable
private fun PdfViewerScreen(
    url: String,
    title: String,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadPdf() {
        loading = true
        error = null
        try {
            val file = withContext(Dispatchers.IO) {
                Downloader.download(context, url, title).file
            }
            pages = withContext(Dispatchers.IO) { renderPdfPages(file) }
            loading = false
        } catch (e: Exception) {
            error = e.message ?: "PDF باز نشد."
            loading = false
        }
    }

    LaunchedEffect(url) { loadPdf() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        DownloadBar(url, title, snackbar)
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("در حال آماده‌سازی PDF…")
            }
            error != null -> ErrorBox(message = error ?: "", onRetry = { scope.launch { loadPdf() } })
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(pages) { index, bitmap ->
                    Column {
                        Text("صفحهٔ ${index + 1}", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "صفحهٔ ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }
}

/** رندر صفحات PDF به Bitmap (موتور داخلی اندروید — بدون سرویس خارجی). */
private fun renderPdfPages(file: File, maxPages: Int = 40): List<Bitmap> {
    val result = mutableListOf<Bitmap>()
    val descriptor: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            val count = renderer.pageCount.coerceAtMost(maxPages)
            for (i in 0 until count) {
                renderer.openPage(i).use { page ->
                    val scale = (1080f / page.width).coerceIn(1f, 3f)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888,
                    )
                    android.graphics.Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result.add(bitmap)
                }
            }
        }
    }
    return result
}
