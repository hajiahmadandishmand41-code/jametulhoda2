package com.jametulhoda.app.data

import org.json.JSONObject

/**
 * سرویس تایپ‌شدهٔ /api/mobile — همان قرارداد `docs/MOBILE-API.md`.
 * هیچ مسیری خارج از allowlist سرور استفاده نمی‌شود.
 */
class ApiService(private val client: ApiClient) {

    private fun ok(root: JSONObject): JSONObject {
        ApiParser.ensureOk(ApiClient.statusOf(root), root)
        return root
    }

    suspend fun health(): Boolean {
        return try {
            ok(client.get("/api/mobile/health")).getJSONObject("data").optBoolean("database", false)
        } catch (e: ApiException) {
            false
        }
    }

    suspend fun home(token: String?): HomeData = ApiParser.parseHome(ok(client.get("/api/mobile/home", mapOf("limit" to "8"), token)))

    suspend fun posts(type: String, page: Int, perPage: Int, search: String = "", topic: Int = 0, category: Int = 0, token: String? = null): PagedItems {
        val query = HashMap<String, String>()
        query["type"] = type
        query["page"] = page.toString()
        query["per_page"] = perPage.toString()
        if (search.isNotEmpty()) query["search"] = search
        if (topic > 0) query["topic"] = topic.toString()
        if (category > 0) query["category"] = category.toString()
        return ApiParser.parseList(ok(client.get("/api/mobile/posts", query, token)))
    }

    suspend fun post(id: Int = 0, slug: String = "", token: String? = null): ContentItem {
        val query = HashMap<String, String>()
        if (id > 0) query["id"] = id.toString() else query["slug"] = slug
        return ApiParser.parseItemPayload(ok(client.get("/api/mobile/post", query, token)))
    }

    suspend fun books(page: Int, perPage: Int, search: String = "", topic: Int = 0, token: String? = null): PagedItems {
        val query = HashMap<String, String>()
        query["page"] = page.toString()
        query["per_page"] = perPage.toString()
        if (search.isNotEmpty()) query["search"] = search
        if (topic > 0) query["topic"] = topic.toString()
        return ApiParser.parseList(ok(client.get("/api/mobile/books", query, token)))
    }

    suspend fun book(id: Int = 0, slug: String = "", token: String? = null): ContentItem {
        val query = HashMap<String, String>()
        if (id > 0) query["id"] = id.toString() else query["slug"] = slug
        return ApiParser.parseItemPayload(ok(client.get("/api/mobile/book", query, token)))
    }

    suspend fun collections(): List<Collection> = ApiParser.parseCollections(ok(client.get("/api/mobile/collections")))

    suspend fun lessons(page: Int, perPage: Int, collection: Int = 0, volume: Int = 0, search: String = "", token: String? = null): PagedItems {
        val query = HashMap<String, String>()
        query["page"] = page.toString()
        query["per_page"] = perPage.toString()
        if (collection > 0) query["collection"] = collection.toString()
        if (volume > 0) query["volume"] = volume.toString()
        if (search.isNotEmpty()) query["search"] = search
        return ApiParser.parseList(ok(client.get("/api/mobile/lessons", query, token)))
    }

    suspend fun lesson(id: Int = 0, slug: String = "", token: String? = null): ContentItem {
        val query = HashMap<String, String>()
        if (id > 0) query["id"] = id.toString() else query["slug"] = slug
        return ApiParser.parseItemPayload(ok(client.get("/api/mobile/lesson", query, token)))
    }

    suspend fun topics(): List<ContentItem> {
        val root = ok(client.get("/api/mobile/topics"))
        return ApiParser.parseList(root).items
    }

    suspend fun topic(id: Int = 0, slug: String = "", token: String? = null): ContentItem {
        val query = HashMap<String, String>()
        if (id > 0) query["id"] = id.toString() else query["slug"] = slug
        return ApiParser.parseItemPayload(ok(client.get("/api/mobile/topic", query, token)))
    }

    suspend fun media(kind: String, page: Int, perPage: Int, token: String? = null): PagedItems {
        val query = HashMap<String, String>()
        query["kind"] = kind
        query["page"] = page.toString()
        query["per_page"] = perPage.toString()
        return ApiParser.parseList(ok(client.get("/api/mobile/media", query, token)))
    }

    suspend fun mediaItem(kind: String, id: Int, token: String? = null): ContentItem {
        val query = HashMap<String, String>()
        query["kind"] = kind
        query["id"] = id.toString()
        return ApiParser.parseItemPayload(ok(client.get("/api/mobile/media-item", query, token)))
    }

    suspend fun search(q: String, type: String, page: Int, perPage: Int, sort: String = "relevance", token: String? = null): PagedItems {
        val query = HashMap<String, String>()
        query["q"] = q
        query["type"] = type
        query["page"] = page.toString()
        query["per_page"] = perPage.toString()
        query["sort"] = sort
        return ApiParser.parseList(ok(client.get("/api/mobile/search", query, token)))
    }

    suspend fun categories(): List<Category> = ApiParser.parseCategories(ok(client.get("/api/mobile/categories")))

    suspend fun about(): AboutPage = ApiParser.parseAbout(ok(client.get("/api/mobile/pages", mapOf("page" to "about"))))

    suspend fun contactPage(): ContactPage = ApiParser.parseContact(ok(client.get("/api/mobile/pages", mapOf("page" to "contact"))))

    suspend fun sendContact(name: String, email: String, phone: String, subject: String, message: String) {
        val body = JSONObject()
            .put("name", name)
            .put("email", email)
            .put("phone", phone)
            .put("subject", subject)
            .put("message", message)
        ok(client.post("/api/mobile/contact", body))
    }

    // ─── احراز هویت ────────────────────────────────────────────────

    suspend fun register(
        fullName: String,
        country: String,
        phone: String,
        email: String,
        password: String,
        passwordConfirm: String,
        deviceLabel: String,
    ): AuthSession {
        val body = JSONObject()
            .put("full_name", fullName)
            .put("country", country)
            .put("phone", phone)
            .put("email", email)
            .put("password", password)
            .put("password_confirm", passwordConfirm)
            .put("agreed_terms", true)
            .put("device_label", deviceLabel)
        return ApiParser.parseAuth(ok(client.post("/api/mobile/auth/register", body)))
    }

    suspend fun login(identifier: String, password: String, deviceLabel: String): AuthSession {
        val body = JSONObject()
            .put("identifier", identifier)
            .put("password", password)
            .put("device_label", deviceLabel)
        return ApiParser.parseAuth(ok(client.post("/api/mobile/auth/login", body)))
    }

    suspend fun me(token: String): UserProfile {
        val root = ok(client.get("/api/mobile/auth/me", token = token))
        return ApiParser.parseUser(root.getJSONObject("data").getJSONObject("user"))
    }

    suspend fun updateProfile(token: String, fullName: String, email: String, phone: String): UserProfile {
        val body = JSONObject()
        if (fullName.isNotEmpty()) body.put("full_name", fullName)
        if (email.isNotEmpty()) body.put("email", email)
        body.put("phone", phone)
        val root = ok(client.post("/api/mobile/auth/profile", body, token))
        return ApiParser.parseUser(root.getJSONObject("data").getJSONObject("user"))
    }

    suspend fun changePassword(token: String, current: String, next: String, confirm: String) {
        val body = JSONObject()
            .put("current_password", current)
            .put("new_password", next)
            .put("new_password_confirm", confirm)
        ok(client.post("/api/mobile/auth/change-password", body, token))
    }

    suspend fun logout(token: String) {
        ok(client.post("/api/mobile/auth/logout", JSONObject(), token))
    }

    // ─── لایک ──────────────────────────────────────────────────────

    suspend fun likeState(refType: String, refId: Int, token: String?): LikeState {
        val query = mapOf("ref_type" to refType, "ref_id" to refId.toString())
        return ApiParser.parseLikeState(ok(client.get("/api/mobile/likes", query, token)))
    }

    suspend fun setLike(token: String, refType: String, refId: Int, like: Boolean): LikeState {
        val body = JSONObject()
            .put("ref_type", refType)
            .put("ref_id", refId)
            .put("action", if (like) "like" else "unlike")
        return ApiParser.parseLikeState(ok(client.post("/api/mobile/likes", body, token)))
    }
}
