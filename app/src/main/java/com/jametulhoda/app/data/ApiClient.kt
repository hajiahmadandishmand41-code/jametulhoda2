package com.jametulhoda.app.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * کلاینت HTTP اپ: فقط HTTPS، توکن Bearer اختیاری، timeout کوتاه،
 * بدون هیچ رمز/کلیدی در کد یا BuildConfig.
 */
class ApiClient(baseUrl: String) {

    private val root: String = baseUrl.trimEnd('/')

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun buildUrl(path: String, query: Map<String, String> = {}): String {
        val builder = (root + path).toHttpUrl().newBuilder()
        for ((k, v) in query) {
            if (v.isNotEmpty()) builder.addQueryParameter(k, v)
        }
        return builder.build().toString()
    }

    suspend fun get(path: String, query: Map<String, String> = {}, token: String? = null): JSONObject {
        val request = Request.Builder()
            .url(buildUrl(path, query))
            .header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .get()
            .build()
        return execute(request)
    }

    suspend fun post(path: String, body: JSONObject, token: String? = null): JSONObject {
        val request = Request.Builder()
            .url(buildUrl(path))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=utf-8")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return execute(request)
    }

    /** GET خام برای دانلود فایل (پیوست/ PDF / رسانه). */
    fun rawCall(url: String): Call {
        return http.newCall(
            Request.Builder()
                .url(url)
                .header("Accept", "*/*")
                .get()
                .build()
        )
    }

    private suspend fun execute(request: Request): JSONObject =
        suspendCancellableCoroutine { cont ->
            val call = http.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!cont.isActive) return
                    cont.resumeWithException(NetworkException("ارتباط با سرور برقرار نشد.", e))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!cont.isActive) {
                        response.close()
                        return
                    }
                    try {
                        val text = response.body?.string() ?: ""
                        val root = try {
                            JSONObject(text)
                        } catch (e: Exception) {
                            JSONObject().put("ok", false).put("error", "invalid_response")
                                .put("message", "پاسخ سرور نامعتبر است.")
                        }
                        if (!response.isSuccessful && !root.has("error")) {
                            root.put("error", "http_" + response.code)
                            root.put("message", "سرور پاسخ مناسبی نداد (" + response.code + ").")
                        }
                        root.put("_http_status", response.code)
                        cont.resume(root)
                    } catch (t: Throwable) {
                        cont.resumeWithException(NetworkException("خواندن پاسخ سرور ممکن نشد.", t))
                    } finally {
                        response.close()
                    }
                }
            })
        }

    /** وضعیت HTTP کنار پاسخ قرار می‌گیرد تا پارسرها ساده بمانند. */
    companion object {
        fun statusOf(root: JSONObject): Int = root.optInt("_http_status", 200)
    }
}
