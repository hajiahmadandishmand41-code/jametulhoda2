package com.jametulhoda.app

import com.jametulhoda.app.data.ApiClient
import com.jametulhoda.app.util.Downloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ساخت URL مسیرها + نام/نوع فایل دانلود (بدون شبکه). */
class DownloaderAndUrlTest {

    @Test
    fun buildUrlKeepsBaseAndEncodesQuery() {
        val client = ApiClient("https://jametulhoda.vercel.app/")
        val url = client.buildUrl("/api/mobile/posts", mapOf("type" to "news", "page" to "2", "empty" to ""))
        assertTrue(url.startsWith("https://jametulhoda.vercel.app/api/mobile/posts?"))
        assertTrue(url.contains("type=news"))
        assertTrue(url.contains("page=2"))
        // پارامتر خالی اضافه نمی‌شود
        assertTrue(!url.contains("empty"))
    }

    @Test
    fun mimeOfMapsCommonExtensions() {
        assertEquals("application/pdf", Downloader.mimeOf("kitab.pdf"))
        assertEquals("audio/mpeg", Downloader.mimeOf("sokhanrari.mp3"))
        assertEquals("video/mp4", Downloader.mimeOf("clip.mp4"))
        assertEquals("image/png", Downloader.mimeOf("tasvir.png"))
        assertTrue(Downloader.mimeOf("file").isNotEmpty())
    }
}
