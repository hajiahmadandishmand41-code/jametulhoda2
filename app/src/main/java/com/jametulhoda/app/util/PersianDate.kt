package com.jametulhoda.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** تاریخ فارسی/میلادی برای نمایش محتوا (بدون وابستگی به Android). */
object PersianDate {

    private val monthNames = listOf(
        "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
        "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر",
    )

    /** نمایش «۱۲ سپتامبر ۲۰۲۶» از رشتهٔ ISO-8601 سرور. */
    fun format(iso: String): String {
        if (iso.isBlank()) return ""
        val parsed = parseIso(iso) ?: return iso.take(10)
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.time = parsed
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val month = monthNames[cal.get(java.util.Calendar.MONTH)]
        val year = cal.get(java.util.Calendar.YEAR)
        return "$day $month $year"
    }

    /** فرمت کوتاه برای کارت‌ها: «۲۰۲۶/۰۹/۱۰». */
    fun short(iso: String): String {
        if (iso.isBlank()) return ""
        val parsed = parseIso(iso) ?: return iso.take(10)
        val format = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(parsed)
    }

    private fun parseIso(iso: String): Date? {
        val candidates = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
        )
        for (pattern in candidates) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                return format.parse(iso)
            } catch (e: Exception) {
                // الگوی بعدی
            }
        }
        return null
    }

    /** زمان پخش صوت/ویدیو به دقیقه:ثانیه. */
    fun duration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
