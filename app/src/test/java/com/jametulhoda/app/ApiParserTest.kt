package com.jametulhoda.app

import com.jametulhoda.app.data.ApiException
import com.jametulhoda.app.data.ApiParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** تست‌های خالص تجزیهٔ JSON قرارداد `/api/mobile` با نمونه‌های واقعی. */
class ApiParserTest {

    private val postItemJson = JSONObject(
        """{
            "id": 12,
            "key": "post-12",
            "kind": "post",
            "type": "news",
            "slug": "khabar-1",
            "title": "خبر آزمایشی",
            "summary": "خلاصه خبر",
            "image_url": "https://example.com/a.jpg",
            "url": "https://jametulhoda.vercel.app/post/khabar-1",
            "published_at": "2026-09-25 10:00:00",
            "like_count": 7,
            "liked": null
        }""",
    )

    @Test
    fun parsesListEnvelope() {
        val json = JSONObject()
            .put("ok", true)
            .put("items", JSONArray().put(postItemJson))
            .put("meta", JSONObject().put("page", 1).put("per_page", 20).put("total", 25).put("has_more", true))
        val list = ApiParser.parseList(json)
        assertEquals(1, list.items.size)
        assertEquals("khabar-1", list.items[0].slug)
        assertEquals(7, list.items[0].likeCount)
        assertEquals("news", list.items[0].type)
        assertEquals("post", list.items[0].kind)
        assertTrue(list.hasMore)
        assertEquals(25, list.total)
        assertEquals(1, list.page)
        assertEquals(20, list.perPage)
    }

    @Test
    fun parsesListWithoutMetaDefaultsToNoMore() {
        val json = JSONObject().put("ok", true).put("items", JSONArray())
        val list = ApiParser.parseList(json)
        assertTrue(list.items.isEmpty())
        assertFalse(list.hasMore)
    }

    @Test
    fun parsesPostDetailPayload() {
        val json = JSONObject()
            .put("ok", true)
            .put(
                "data",
                JSONObject()
                    .put("id", 12)
                    .put("key", "post-12")
                    .put("kind", "post")
                    .put("type", "news")
                    .put("slug", "khabar-1")
                    .put("title", "خبر آزمایشی")
                    .put("summary", "خلاصه")
                    .put("content_html", "<p>متن <strong>کامل</strong> خبر</p>")
                    .put("content_text", "متن کامل خبر")
                    .put("url", "https://x/post/khabar-1")
                    .put("published_at", "2026-09-25")
                    .put("like_count", 7)
                    .put("liked", true)
                    .put(
                        "attachments",
                        JSONArray().put(
                            JSONObject()
                                .put("id", 3).put("kind", "document").put("title", "سخنرانی")
                                .put("url", "https://x/s.pdf").put("size", 100),
                        ),
                    )
                    .put(
                        "topics",
                        JSONArray().put(JSONObject().put("id", 2).put("name", "ماه محرم").put("slug", "mah")),
                    ),
            )
        val d = ApiParser.parseItemPayload(json)
        assertEquals("خبر آزمایشی", d.title)
        assertTrue(d.liked == true)
        assertEquals(7, d.likeCount)
        assertEquals(1, d.attachments.size)
        assertEquals("document", d.attachments[0].kind)
        assertEquals(100L, d.attachments[0].size)
        assertEquals(1, d.topics.size)
        assertEquals("ماه محرم", d.topics[0].name)
        assertTrue(d.contentHtml.contains("<strong>"))
    }

    @Test
    fun parsesLikeStateFromServerCount() {
        // شمارنده فقط از سرور می‌آید؛ مشتری هرگز آن را جعل نمی‌کند.
        val json = JSONObject()
            .put("ok", true)
            .put("data", JSONObject().put("ref_type", "post").put("ref_id", 12).put("liked", false).put("like_count", 3))
        val like = ApiParser.parseLikeState(json)
        assertEquals(java.lang.Boolean.FALSE, like.liked)
        assertEquals(3, like.likeCount)
        assertEquals("post", like.refType)
    }

    @Test
    fun parsesLikeStateAnonymous() {
        val json = JSONObject()
            .put("ok", true)
            .put("data", JSONObject().put("ref_type", "book").put("ref_id", 5).put("liked", JSONObject.NULL).put("like_count", 10))
        val like = ApiParser.parseLikeState(json)
        assertNull(like.liked)
        assertEquals(10, like.likeCount)
    }

    @Test
    fun parsesHomeSections() {
        val json = JSONObject()
            .put("ok", true)
            .put(
                "data",
                JSONObject()
                    .put("site", JSONObject().put("name", "جامعه‌الهدی").put("slogan", "s").put("description", "d"))
                    .put("banners", JSONArray().put(JSONObject().put("id", 1).put("title", "بنر")))
                    .put("featured", JSONArray().put(postItemJson))
                    .put("sections", JSONObject().put("news", JSONArray().put(postItemJson))),
            )
        val home = ApiParser.parseHome(json)
        assertEquals("جامعه‌الهدی", home.site.name)
        assertEquals(1, home.banners.size)
        assertEquals(1, home.featured.size)
        assertEquals(1, home.sections["news"]?.size)
    }

    @Test
    fun ensureOkThrowsApiExceptionWithMessage() {
        val json = JSONObject().put("ok", false).put("error", "invalid_credentials").put("message", "ایمیل یا رمز نادرست است.")
        try {
            ApiParser.ensureOk(401, json)
            throw AssertionError("باید استثنا پرتاب می‌شد")
        } catch (e: ApiException) {
            assertEquals(401, e.status)
            assertEquals("invalid_credentials", e.code)
            assertEquals("ایمیل یا رمز نادرست است.", e.message)
        }
    }

    @Test
    fun ensureOkTreatsHttpErrorWithoutJson() {
        try {
            ApiParser.ensureOk(500, JSONObject())
            throw AssertionError("باید استثنا پرتاب می‌شد")
        } catch (e: ApiException) {
            assertEquals(500, e.status)
        }
    }

    @Test
    fun parsesEmptyNullableFieldsSafely() {
        val json = JSONObject()
            .put("ok", true)
            .put("items", JSONArray().put(JSONObject().put("id", 1).put("type", "article").put("title", "t")))
        val item = ApiParser.parseList(json).items[0]
        assertEquals("", item.slug)
        assertEquals(0, item.likeCount)
        assertNull(item.liked)
    }
}
