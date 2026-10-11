package com.jametulhoda.app

import com.jametulhoda.app.data.ApiParser
import com.jametulhoda.app.util.PersianDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** منطق صفحه‌بندی + وضعیت پسند + تاریخ فارسی. */
class PaginationAndDateTest {

    @Test
    fun hasMoreFollowsServerMeta() {
        val first = ApiParser.parseList(
            JSONObject().put("ok", true).put("items", org.json.JSONArray())
                .put("meta", JSONObject().put("page", 1).put("per_page", 20).put("total", 137).put("has_more", true)),
        )
        val last = ApiParser.parseList(
            JSONObject().put("ok", true).put("items", org.json.JSONArray())
                .put("meta", JSONObject().put("page", 7).put("per_page", 20).put("total", 137).put("has_more", false)),
        )
        assertTrue(first.hasMore)
        assertFalse(last.hasMore)
        assertEquals(137, first.total)
    }

    @Test
    fun likeToggleStateIsServerDerived() {
        // حالت «پسندیده» و شمارنده هر دو از پاسخ سرور می‌آیند.
        val before = ApiParser.parseLikeState(
            JSONObject().put("ok", true).put(
                "data",
                JSONObject().put("ref_type", "post").put("ref_id", 1).put("liked", false).put("like_count", 10),
            ),
        )
        val after = ApiParser.parseLikeState(
            JSONObject().put("ok", true).put(
                "data",
                JSONObject().put("ref_type", "post").put("ref_id", 1).put("liked", true).put("like_count", 11),
            ),
        )
        assertEquals(java.lang.Boolean.FALSE, before.liked)
        assertEquals(java.lang.Boolean.TRUE, after.liked)
        assertEquals(10, before.likeCount)
        assertEquals(11, after.likeCount)
    }

    @Test
    fun persianDateFormatsIso() {
        val formatted = PersianDate.format("2026-09-25 10:00:00")
        assertTrue(formatted.contains("2026"))
        assertTrue(formatted.contains("سپتامبر"))
        assertEquals("2026/09/25", PersianDate.short("2026-09-25"))
    }

    @Test
    fun durationFormats() {
        assertEquals("00:00", PersianDate.duration(0))
        assertEquals("01:05", PersianDate.duration(65_000))
        assertEquals("12:34", PersianDate.duration(754_000))
    }
}
