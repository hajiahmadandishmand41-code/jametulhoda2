package com.jametulhoda.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * مدل‌های قرارداد `/api/mobile/*` + پارس ایمن JSON.
 *
 * همهٔ پارسرها توابع خالص‌اند و در تست‌های JVM با نمونه JSON واقعی سنجیده
 * می‌شوند؛ هیچ وابستگی به Android در این فایل نیست.
 */
data class ContentItem(
    val id: Int,
    val key: String,
    val kind: String,
    val type: String,
    val slug: String,
    val title: String,
    val summary: String,
    val author: String,
    val speaker: String = "",
    val categoryName: String = "",
    val publishedAt: String = "",
    val createdAt: String = "",
    val imageUrl: String = "",
    val url: String = "",
    val contentText: String = "",
    val contentHtml: String = "",
    val sources: String = "",
    val gallery: List<GalleryImage> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val topics: List<TopicRef> = emptyList(),
    val likeCount: Int = 0,
    val liked: Boolean? = null,
    // کتاب
    val translator: String = "",
    val publisher: String = "",
    val publishYear: String = "",
    val pages: Int = 0,
    val toc: String = "",
    val pdfUrl: String = "",
    val wordUrl: String = "",
    // درس
    val subject: String = "",
    val collectionTitle: String = "",
    val volumeTitle: String = "",
    val lessonNumber: Int = 0,
    val audioUrl: String = "",
    val videoUrl: String = "",
    // رسانه
    val mediaUrl: String = "",
    val parentTitle: String = "",
    val fileSize: Long = 0L,
    // موضوع
    val children: List<ContentItem> = emptyList(),
)

data class GalleryImage(val id: Int, val url: String, val alt: String)

data class Attachment(val id: Int, val kind: String, val title: String, val url: String, val size: Long)

data class TopicRef(val id: Int, val name: String, val slug: String)

data class PagedItems(val items: List<ContentItem>, val total: Int, val page: Int, val perPage: Int, val hasMore: Boolean) {
    val totalPages: Int get() = if (perPage <= 0) 0 else (total + perPage - 1) / perPage
}

data class SiteInfo(
    val name: String,
    val slogan: String,
    val description: String,
    val logoUrl: String,
    val url: String,
    val email: String,
    val phone: String,
    val address: String,
)

data class HomeData(
    val site: SiteInfo,
    val banners: List<Banner>,
    val featured: List<ContentItem>,
    val sections: Map<String, List<ContentItem>>,
)

data class Banner(
    val id: Int,
    val title: String,
    val content: String,
    val imageUrl: String,
    val linkUrl: String,
    val linkText: String,
)

data class Category(val id: Int, val name: String, val slug: String, val description: String, val postType: String)

data class Collection(
    val id: Int,
    val title: String,
    val slug: String,
    val summary: String,
    val imageUrl: String,
    val lessonCount: Int,
    val volumes: List<Volume>,
)

data class Volume(val id: Int, val title: String, val slug: String)

data class LikeState(val refType: String, val refId: Int, val likeCount: Int, val liked: Boolean?)

data class UserProfile(
    val id: Int,
    val fullName: String,
    val username: String,
    val email: String,
    val phone: String,
    val country: String,
    val role: String,
    val mustChangePassword: Boolean,
    val createdAt: String,
)

data class AuthSession(val token: String, val tokenType: String, val expiresAt: String, val user: UserProfile)

data class AboutPage(
    val title: String,
    val name: String,
    val slogan: String,
    val description: String,
    val founder: String,
    val logoUrl: String,
    val contact: ContactInfo,
)

data class ContactInfo(val email: String, val phone: String, val address: String)

data class ContactPage(val title: String, val email: String, val phone: String, val address: String)

/** خطای ساخت‌یافتهٔ API (بدون هیچ اطلاعات داخلی سرور). */
class ApiException(val code: String, message: String, val status: Int) : Exception(message)

/** خطای شبکه/زمان‌بندی؛ UI برای آن حالت «اینترنت» یا «تلاش مجدد» نشان می‌دهد. */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ApiParser {
    private fun JSONObject.str(key: String): String = optString(key, "").trim()
    private fun JSONObject.int(key: String, def: Int = 0): Int = optInt(key, def)
    private fun JSONObject.long(key: String, def: Long = 0L): Long = optLong(key, def)

    fun parseItem(o: JSONObject): ContentItem {
        val gallery = ArrayList<GalleryImage>()
        val gArr = o.optJSONArray("gallery")
        if (gArr != null) {
            for (i in 0 until gArr.length()) {
                val g = gArr.optJSONObject(i) ?: continue
                val url = g.str("url")
                if (url.isEmpty()) continue
                gallery.add(GalleryImage(g.int("id"), url, g.str("alt")))
            }
        }
        val attachments = ArrayList<Attachment>()
        val aArr = o.optJSONArray("attachments")
        if (aArr != null) {
            for (i in 0 until aArr.length()) {
                val a = aArr.optJSONObject(i) ?: continue
                val url = a.str("url")
                if (url.isEmpty()) continue
                attachments.add(Attachment(a.int("id"), a.str("kind"), a.str("title"), url, a.long("size")))
            }
        }
        val topics = ArrayList<TopicRef>()
        val tArr = o.optJSONArray("topics")
        if (tArr != null) {
            for (i in 0 until tArr.length()) {
                val t = tArr.optJSONObject(i) ?: continue
                topics.add(TopicRef(t.int("id"), t.str("name"), t.str("slug")))
            }
        }
        val children = ArrayList<ContentItem>()
        val cArr = o.optJSONArray("children")
        if (cArr != null) {
            for (i in 0 until cArr.length()) {
                cArr.optJSONObject(i)?.let { children.add(parseItem(it)) }
            }
        }
        val liked = if (o.isNull("liked")) null else o.optBoolean("liked", false)
        return ContentItem(
            id = o.int("id"),
            key = o.str("key").ifEmpty { "item-" + o.int("id") },
            kind = o.str("kind").ifEmpty { "post" },
            type = o.str("type").ifEmpty { "news" },
            slug = o.str("slug"),
            title = o.str("title"),
            summary = o.str("summary"),
            author = o.str("author"),
            speaker = o.str("speaker"),
            categoryName = o.optJSONObject("category")?.str("name") ?: "",
            publishedAt = o.str("published_at"),
            createdAt = o.str("created_at"),
            imageUrl = o.str("image_url"),
            url = o.str("url"),
            contentText = o.str("content_text"),
            contentHtml = o.str("content_html"),
            sources = o.str("sources"),
            gallery = gallery,
            attachments = attachments,
            topics = topics,
            likeCount = o.int("like_count"),
            liked = liked,
            translator = o.str("translator"),
            publisher = o.str("publisher"),
            publishYear = o.str("publish_year"),
            pages = o.int("pages"),
            toc = o.str("toc"),
            pdfUrl = o.str("pdf_url"),
            wordUrl = o.str("word_url"),
            subject = o.str("subject"),
            collectionTitle = o.str("collection_title"),
            volumeTitle = o.str("volume_title"),
            lessonNumber = o.optInt("lesson_number", 0),
            audioUrl = o.str("audio_url"),
            videoUrl = o.str("video_url"),
            mediaUrl = o.str("media_url"),
            parentTitle = o.optJSONObject("parent")?.str("title") ?: "",
            fileSize = o.long("size"),
            children = children,
        )
    }

    fun parseList(root: JSONObject): PagedItems {
        val items = ArrayList<ContentItem>()
        val arr = root.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { items.add(parseItem(it)) }
        }
        val meta = root.optJSONObject("meta") ?: JSONObject()
        return PagedItems(
            items = items,
            total = meta.int("total"),
            page = meta.int("page", 1),
            perPage = meta.int("per_page", 20),
            hasMore = meta.optBoolean("has_more", false),
        )
    }

    fun parseItemPayload(root: JSONObject): ContentItem {
        val data = root.optJSONObject("data") ?: throw ApiException("invalid_response", "پاسخ نامعتبر است.", 500)
        return parseItem(data)
    }

    fun parseHome(root: JSONObject): HomeData {
        val data = root.optJSONObject("data") ?: JSONObject()
        val siteObj = data.optJSONObject("site") ?: JSONObject()
        val site = SiteInfo(
            name = siteObj.str("name"),
            slogan = siteObj.str("slogan"),
            description = siteObj.str("description"),
            logoUrl = siteObj.str("logo_url"),
            url = siteObj.str("url"),
            email = siteObj.str("email"),
            phone = siteObj.str("phone"),
            address = siteObj.str("address"),
        )
        val banners = ArrayList<Banner>()
        val bArr = data.optJSONArray("banners")
        if (bArr != null) {
            for (i in 0 until bArr.length()) {
                val b = bArr.optJSONObject(i) ?: continue
                banners.add(
                    Banner(
                        id = b.int("id"),
                        title = b.str("title"),
                        content = b.str("content"),
                        imageUrl = b.str("image_url"),
                        linkUrl = b.str("link_url"),
                        linkText = b.str("link_text"),
                    )
                )
            }
        }
        val featured = ArrayList<ContentItem>()
        val fArr = data.optJSONArray("featured")
        if (fArr != null) {
            for (i in 0 until fArr.length()) fArr.optJSONObject(i)?.let { featured.add(parseItem(it)) }
        }
        val sections = HashMap<String, List<ContentItem>>()
        val sObj = data.optJSONObject("sections")
        if (sObj != null) {
            for (name in sObj.keys()) {
                val list = ArrayList<ContentItem>()
                val arr = sObj.optJSONArray(name)
                if (arr != null) {
                    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(parseItem(it)) }
                }
                sections[name] = list
            }
        }
        return HomeData(site, banners, featured, sections)
    }

    fun parseCategories(root: JSONObject): List<Category> {
        val out = ArrayList<Category>()
        val arr = root.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Category(o.int("id"), o.str("name"), o.str("slug"), o.str("description"), o.str("post_type")))
        }
        return out
    }

    fun parseCollections(root: JSONObject): List<Collection> {
        val out = ArrayList<Collection>()
        val arr = root.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val volumes = ArrayList<Volume>()
            val vArr = o.optJSONArray("volumes")
            if (vArr != null) {
                for (j in 0 until vArr.length()) {
                    val v = vArr.optJSONObject(j) ?: continue
                    volumes.add(Volume(v.int("id"), v.str("title"), v.str("slug")))
                }
            }
            out.add(
                Collection(
                    id = o.int("id"),
                    title = o.str("title"),
                    slug = o.str("slug"),
                    summary = o.str("summary"),
                    imageUrl = o.str("image_url"),
                    lessonCount = o.int("lesson_count"),
                    volumes = volumes,
                )
            )
        }
        return out
    }

    fun parseLikeState(root: JSONObject): LikeState {
        val data = root.optJSONObject("data") ?: JSONObject()
        val liked = if (data.isNull("liked")) null else data.optBoolean("liked", false)
        return LikeState(
            refType = data.str("ref_type"),
            refId = data.int("ref_id"),
            likeCount = data.int("like_count"),
            liked = liked,
        )
    }

    fun parseAuth(root: JSONObject): AuthSession {
        val data = root.optJSONObject("data") ?: JSONObject()
        val user = data.optJSONObject("user") ?: JSONObject()
        return AuthSession(
            token = data.str("token").ifEmpty { data.str("access_token") },
            tokenType = data.str("token_type").ifEmpty { "Bearer" },
            expiresAt = data.str("expires_at"),
            user = parseUser(user),
        )
    }

    fun parseUser(o: JSONObject): UserProfile {
        return UserProfile(
            id = o.int("id"),
            fullName = o.str("full_name"),
            username = o.str("username"),
            email = o.str("email"),
            phone = o.str("phone"),
            country = o.str("country"),
            role = o.str("role").ifEmpty { "user" },
            mustChangePassword = o.optBoolean("must_change_password", false),
            createdAt = o.str("created_at"),
        )
    }

    fun parseAbout(root: JSONObject): AboutPage {
        val data = root.optJSONObject("data") ?: JSONObject()
        val contact = data.optJSONObject("contact") ?: JSONObject()
        return AboutPage(
            title = data.str("title"),
            name = data.str("name"),
            slogan = data.str("slogan"),
            description = data.str("description"),
            founder = data.str("founder"),
            logoUrl = data.str("logo_url"),
            contact = ContactInfo(contact.str("email"), contact.str("phone"), contact.str("address")),
        )
    }

    fun parseContact(root: JSONObject): ContactPage {
        val data = root.optJSONObject("data") ?: JSONObject()
        return ContactPage(data.str("title"), data.str("email"), data.str("phone"), data.str("address"))
    }

    /** آیا پاسخ سرور «ok» است؟ خطا را به صورت ساخت‌یافته بالا می‌اندازد. */
    fun ensureOk(status: Int, root: JSONObject) {
        if (status in 200..299 && root.optBoolean("ok", false)) return
        val code = root.optString("error", if (status == 0) "network" else "server_error")
        val message = root.optString("message", "پاسخ مناسبی از سرور دریافت نشد.")
        throw ApiException(code, message, status)
    }
}
