package com.jametulhoda.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.jametulhoda.app.data.ApiClient
import com.jametulhoda.app.data.ApiService
import com.jametulhoda.app.data.AppRepository
import okhttp3.Cache
import okhttp3.OkHttpClient

/**
 * Application: ساخت singletonهای داده + پیکربندی کش تصویر/شبکهٔ محدود.
 * نسخهٔ Lite با BuildConfig.LIGHTWEIGHT کش و حافظهٔ کمتری می‌گیرد (همان قابلیت‌ها).
 */
class JametulHodaApp : Application(), ImageLoaderFactory {

    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val client = ApiClient(BuildConfig.API_BASE_URL)
        repository = AppRepository(ApiService(client), this)
    }

    override fun newImageLoader(): ImageLoader {
        val memoryPercent = BuildConfig.IMAGE_MEMORY_PERCENT / 100f
        val diskBytes = BuildConfig.IMAGE_DISK_CACHE_BYTES
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memoryPercent.toDouble())
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(diskBytes)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    companion object {
        /** نام سایت رسمی؛ فقط برای نمایش (بدون هیچ رمز/کلیدی). */
        const val SITE_URL = BuildConfig.API_BASE_URL
    }
}
