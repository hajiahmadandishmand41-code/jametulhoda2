package com.example.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

class MobileApiException(val code: String, val status: Int = 0) : IOException(code)

/** Native account client. The access token is encrypted under a non-exportable
 * Android Keystore AES key and stored outside Android's automatic backup area. */
class MobileAuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val tokenFile = File(appContext.noBackupFilesDir, "jhd-mobile-token.bin")

    fun hasToken(): Boolean = readToken() != null

    fun clearLocalToken() {
        runCatching { tokenFile.delete() }
    }

    fun currentUser(): JSONObject = send("GET", null, authenticated = true).getJSONObject("user")

    fun login(identifier: String, password: String): JSONObject {
        val response = send(
            "POST",
            JSONObject().put("action", "login")
                .put("identifier", identifier.trim())
                .put("password", password),
            authenticated = false
        )
        val token = response.optString("token")
        if (token.length != 64) throw MobileApiException("invalid_token_response")
        writeToken(token)
        return response
    }

    fun register(
        fullName: String, country: String, phone: String, email: String,
        password: String, passwordConfirm: String
    ): JSONObject {
        val response = send(
            "POST",
            JSONObject().put("action", "register")
                .put("full_name", fullName.trim())
                .put("country", country)
                .put("phone", phone.trim())
                .put("email", email.trim())
                .put("password", password)
                .put("password_confirm", passwordConfirm)
                .put("agreed_terms", true),
            authenticated = false
        )
        val token = response.optString("token")
        if (token.length != 64) throw MobileApiException("invalid_token_response")
        writeToken(token)
        return response
    }

    fun updateProfile(fullName: String, email: String): JSONObject = send(
        "POST",
        JSONObject().put("action", "update_profile")
            .put("full_name", fullName.trim())
            .put("email", email.trim()),
        authenticated = true
    )

    fun changePassword(currentPassword: String, newPassword: String, passwordConfirm: String): JSONObject {
        val response = send(
            "POST",
            JSONObject().put("action", "change_password")
                .put("current_password", currentPassword)
                .put("new_password", newPassword)
                .put("password_confirm", passwordConfirm),
            authenticated = true
        )
        clearLocalToken()
        return response
    }

    fun logout(): JSONObject {
        val response = send("POST", JSONObject().put("action", "logout"), authenticated = true)
        clearLocalToken()
        return response
    }

    private fun send(method: String, payload: JSONObject?, authenticated: Boolean): JSONObject {
        val connection = URL(API_URL).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (authenticated) {
                val token = readToken() ?: throw MobileApiException("unauthorized", 401)
                connection.setRequestProperty("Authorization", "Bearer " + token)
            }
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                throw MobileApiException("invalid_server_response", status)
            }
            if (status !in 200..299 || !json.optBoolean("ok", false)) {
                throw MobileApiException(json.optString("error", "request_failed"), status)
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun writeToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val temp = File(tokenFile.parentFile, tokenFile.name + ".tmp")
        DataOutputStream(FileOutputStream(temp)).use {
            it.writeInt(cipher.iv.size)
            it.write(cipher.iv)
            it.write(encrypted)
            it.flush()
        }
        if (!temp.renameTo(tokenFile)) {
            FileOutputStream(tokenFile).use { it.write(temp.readBytes()) }
            temp.delete()
        }
    }

    private fun readToken(): String? {
        if (!tokenFile.isFile) return null
        return try {
            DataInputStream(FileInputStream(tokenFile)).use { input ->
                val ivLength = input.readInt()
                if (ivLength !in 12..16) throw IOException("invalid_token_file")
                val iv = ByteArray(ivLength)
                input.readFully(iv)
                val encrypted = input.readBytes()
                if (encrypted.isEmpty()) throw IOException("empty_token_file")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(encrypted), Charsets.UTF_8).takeIf { it.length == 64 }
            }
        } catch (_: Exception) {
            clearLocalToken()
            null
        }
    }

    companion object {
        private const val API_URL = "https://jametulhoda.vercel.app/api/mobile-auth"
        private const val KEY_ALIAS = "jhd_mobile_token_aes_gcm_v1"
    }
}
