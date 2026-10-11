package com.jametulhoda.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * لایهٔ دسترسی داده با کش محدود:
 *  - کش حافظه برای نشست جاری (خانه/فهرست‌ها)
 *  - کش دیسک کوچک (SharedPreferences) برای آخرین پاسخ موفق هر مسیر، تا در
 *    قطع اینترنت آخرین محتوای دریافت‌شده قابل مشاهده بماند.
 * اندازهٔ کش‌ها محدود است و با «پاکسازی حافظه موقت» پاک می‌شود.
 */
class AppRepository(private val api: ApiService, context: Context) {

    private val cache: SharedPreferences =
        context.applicationContext.getSharedPreferences("jhd_cache", Context.MODE_PRIVATE)
    private val memory = HashMap<String, String>()

    var session: AuthSession?
        get() = sessionStore.load()
        set(value) {
            if (value == null) sessionStore.clear() else sessionStore.save(value)
        }

    private val sessionStore = SessionStore(context)

    val token: String? get() = sessionStore.load()?.token

    private fun cacheKey(path: String, query: Map<String, String>): String {
        return path + "?" + query.toSortedMap().entries.joinToString("&") { it.key + "=" + it.value }
    }

    private fun remember(key: String, raw: String) {
        if (raw.length > MAX_CACHE_ENTRY) return
        memory[key] = raw
        val editor = cache.edit()
        editor.putString(key, raw)
        // سقف تعداد ورودی‌های کش: قدیمی‌ترین‌ها حذف می‌شوند.
        var keys = cache.getStringSet("keys", emptySet())?.toMutableSet() ?: mutableSetOf()
        keys.add(key)
        while (keys.size > MAX_CACHE_ENTRIES) {
            val drop = keys.firstOrNull() ?: break
            keys.remove(drop)
            editor.remove(drop)
        }
        editor.putStringSet("keys", keys)
        editor.apply()
    }

    private fun cached(key: String): JSONObject? {
        val raw = memory[key] ?: cache.getString(key, null) ?: return null
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        memory.clear()
        cache.edit().clear().apply()
    }

    /**
     * فراخوانی با کش: در موفقیت، پاسخ تازه ذخیره می‌شود؛ در خطای شبکه، آخرین
     * پاسخ موفق (در صورت وجود) با پرچم `stale` برگردانده می‌شود.
     */
    private suspend fun <T> withCache(
        path: String,
        query: Map<String, String>,
        parse: (JSONObject) -> T,
        fetch: suspend () -> JSONObject,
    ): T {
        val key = cacheKey(path, query)
        return try {
            val root = fetch()
            remember(key, root.toString())
            parse(root)
        } catch (e: NetworkException) {
            val stale = cached(key) ?: throw e
            parse(stale)
        } catch (e: ApiException) {
            if (e.status in 500..599) {
                val stale = cached(key) ?: throw e
                parse(stale)
            } else {
                throw e
            }
        }
    }

    // ─── محتوا ─────────────────────────────────────────────────────

    suspend fun home(): HomeData = withCache("home", emptyMap(), ApiParser::parseHome) {
        api.home(token)
    }

    suspend fun posts(type: String, page: Int, search: String = "", topic: Int = 0): PagedItems {
        val query = mapOf("type" to type, "page" to page.toString(), "search" to search, "topic" to topic.toString())
        return withCache("posts", query, ApiParser::parseList) {
            api.posts(type, page, PAGE_SIZE, search, topic, token = token)
        }
    }

    suspend fun postDetail(slug: String, id: Int = 0): ContentItem =
        api.post(id, slug, token)

    suspend fun books(page: Int, search: String = "", topic: Int = 0): PagedItems {
        val query = mapOf("page" to page.toString(), "search" to search, "topic" to topic.toString())
        return withCache("books", query, ApiParser::parseList) {
            api.books(page, PAGE_SIZE, search, topic, token)
        }
    }

    suspend fun bookDetail(slug: String, id: Int = 0): ContentItem = api.book(id, slug, token)

    suspend fun collections(): List<Collection> = withCache("collections", emptyMap(), ApiParser::parseCollections) {
        api.collections()
    }

    suspend fun lessons(page: Int, collection: Int = 0, volume: Int = 0, search: String = ""): PagedItems {
        val query = mapOf(
            "page" to page.toString(),
            "collection" to collection.toString(),
            "volume" to volume.toString(),
            "search" to search,
        )
        return withCache("lessons", query, ApiParser::parseList) {
            api.lessons(page, PAGE_SIZE, collection, volume, search, token)
        }
    }

    suspend fun lessonDetail(slug: String, id: Int = 0): ContentItem = api.lesson(id, slug, token)

    suspend fun topics(): List<ContentItem> = api.topics()

    suspend fun topicDetail(slug: String, id: Int = 0): ContentItem = api.topic(id, slug, token)

    suspend fun media(kind: String, page: Int): PagedItems {
        val query = mapOf("kind" to kind, "page" to page.toString())
        return withCache("media", query, ApiParser::parseList) {
            api.media(kind, page, PAGE_SIZE, token)
        }
    }

    suspend fun mediaItem(kind: String, id: Int): ContentItem = api.mediaItem(kind, id, token)

    suspend fun search(q: String, type: String, page: Int, sort: String = "relevance"): PagedItems {
        val query = mapOf("q" to q, "type" to type, "page" to page.toString(), "sort" to sort)
        return withCache("search", query, ApiParser::parseList) {
            api.search(q, type, page, PAGE_SIZE, sort, token)
        }
    }

    suspend fun categories(): List<Category> = withCache("categories", emptyMap(), ApiParser::parseCategories) {
        api.categories()
    }

    suspend fun about(): AboutPage = withCache("about", emptyMap(), ApiParser::parseAbout) {
        api.about()
    }

    suspend fun contactPage(): ContactPage = api.contactPage()

    suspend fun sendContact(name: String, email: String, phone: String, subject: String, message: String) =
        api.sendContact(name, email, phone, subject, message)

    // ─── حساب ──────────────────────────────────────────────────────

    suspend fun login(identifier: String, password: String): UserProfile {
        val auth = api.login(identifier, password, deviceLabel())
        session = auth
        return auth.user
    }

    suspend fun register(fullName: String, country: String, phone: String, email: String, password: String, confirm: String): UserProfile {
        val auth = api.register(fullName, country, phone, email, password, confirm, deviceLabel())
        session = auth
        return auth.user
    }

    suspend fun me(): UserProfile {
        val current = token ?: throw ApiException("unauthorized", "برای این عملیات باید وارد حساب شوید.", 401)
        return api.me(current)
    }

    suspend fun updateProfile(fullName: String, email: String, phone: String): UserProfile {
        val current = token ?: throw ApiException("unauthorized", "برای این عملیات باید وارد حساب شوید.", 401)
        val user = api.updateProfile(current, fullName, email, phone)
        session?.let { session = it.copy(user = user) }
        return user
    }

    suspend fun changePassword(current: String, next: String, confirm: String) {
        val t = token ?: throw ApiException("unauthorized", "برای این عملیات باید وارد حساب شوید.", 401)
        api.changePassword(t, current, next, confirm)
        // سرور همهٔ توکن‌ها را باطل می‌کند؛ نشست محلی هم پاک می‌شود.
        session = null
    }

    suspend fun logout() {
        val t = token
        session = null
        if (t != null) {
            try {
                api.logout(t)
            } catch (e: Exception) {
                // خروج محلی همیشه انجام می‌شود حتی اگر شبکه قطع باشد.
            }
        }
    }

    // ─── لایک ──────────────────────────────────────────────────────

    suspend fun setLike(refType: String, refId: Int, like: Boolean): LikeState {
        val t = token ?: throw ApiException("unauthorized", "برای پسندیدن ابتدا وارد حساب شوید.", 401)
        return api.setLike(t, refType, refId, like)
    }

    suspend fun likeState(refType: String, refId: Int): LikeState = api.likeState(refType, refId, token)

    private fun deviceLabel(): String {
        val model = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
        return model.take(60)
    }

    companion object {
        const val PAGE_SIZE = 20
        private const val MAX_CACHE_ENTRIES = 24
        private const val MAX_CACHE_ENTRY = 256 * 1024
    }
}
