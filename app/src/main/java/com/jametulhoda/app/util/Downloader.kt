package com.jametulhoda.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * دانلود فایل (PDF، صوت، ویدیو، سند) به فضای خصوصی برنامه + بازکردن/اشتراک با
 * FileProvider — بدون هیچ مجوز ذخیره‌سازی و بدون دسترسی به حافظهٔ مشترک.
 */
object Downloader {

    private val http = OkHttpClient()

    data class Downloaded(val file: File, val uri: Uri, val mime: String)

    fun downloadDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "Download")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun download(context: Context, url: String, label: String): Downloaded =
        withContext(Dispatchers.IO) {
            val name = sanitizeFileName(label, url)
            val target = File(downloadDir(context), name)
            if (target.exists() && target.length() > 0) {
                return@withContext toDownloaded(context, target)
            }
            val request = Request.Builder().url(url).header("Accept", "*/*").get().build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("سرور فایل را برنگرداند (" + response.code + ").")
                val body = response.body ?: throw IOException("محتوای فایل خالی است.")
                val tmp = File(target.parentFile, target.name + ".part")
                tmp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                        }
                    }
                }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            }
            toDownloaded(context, target)
        }

    private fun toDownloaded(context: Context, file: File): Downloaded {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return Downloaded(file, uri, mimeOf(file.name))
    }

    fun open(context: Context, downloaded: Downloaded) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(downloaded.uri, downloaded.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "باز کردن فایل").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun share(context: Context, downloaded: Downloaded) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = downloaded.mime
            putExtra(Intent.EXTRA_STREAM, downloaded.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> {
                try {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                } catch (e: Throwable) {
                    "application/octet-stream"
                }
            }
        }
    }

    private fun sanitizeFileName(label: String, url: String): String {
        val fromUrl = url.substringAfterLast('/').substringBefore('?').take(60)
        val base = (label.ifBlank { fromUrl }).replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(60)
        val ext = fromUrl.substringAfterLast('.', "")
        val name = if (base.contains('.')) base else if (ext.isNotEmpty()) "$base.$ext" else base
        return name.ifBlank { "file-" + System.currentTimeMillis() }
    }
}
