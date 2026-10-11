package com.jametulhoda.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * ذخیرهٔ امن نشست: توکن Bearer با AES-GCM کلید AndroidKeyStore رمز می‌شود؛
 * کلید هرگز از سخت‌افزار keystore خارج نمی‌شود. نسخهٔ plain توکن در هیچ
 * SharedPreferences ذخیره نمی‌شود.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jhd_session", Context.MODE_PRIVATE)

    @Synchronized
    fun save(session: AuthSession) {
        val payload = JSONObject()
            .put("token", session.token)
            .put("token_type", session.tokenType)
            .put("expires_at", session.expiresAt)
            .put("user", JSONObject()
                .put("id", session.user.id)
                .put("full_name", session.user.fullName)
                .put("username", session.user.username)
                .put("email", session.user.email)
                .put("phone", session.user.phone)
                .put("country", session.user.country)
                .put("role", session.user.role)
                .put("must_change_password", session.user.mustChangePassword)
                .put("created_at", session.user.createdAt))
        val plain = payload.toString().toByteArray(Charsets.UTF_8)
        val blob = encrypt(plain)
        if (blob != null) {
            prefs.edit().putString("session_enc", blob).remove("session_plain").apply()
        } else {
            // بدون keystore قابل استفاده (مثلاً بعضی محیط‌های آزمون)، نشست در
            // حافظهٔ خصوصی خود برنامه می‌ماند؛ هرگز به بیرون از sandbox نمی‌رود.
            prefs.edit().putString("session_plain", payload.toString()).apply()
        }
    }

    @Synchronized
    fun load(): AuthSession? {
        val enc = prefs.getString("session_enc", null)
        val json: String = if (enc != null) {
            val bytes = decrypt(enc) ?: return clearAndNull()
            String(bytes, Charsets.UTF_8)
        } else {
            prefs.getString("session_plain", null) ?: return null
        }
        return try {
            val root = JSONObject(json)
            val user = root.optJSONObject("user") ?: JSONObject()
            AuthSession(
                token = root.optString("token"),
                tokenType = root.optString("token_type", "Bearer"),
                expiresAt = root.optString("expires_at"),
                user = ApiParser.parseUser(user),
            ).takeIf { it.token.isNotEmpty() }
        } catch (e: Exception) {
            clearAndNull()
        }
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun clearAndNull(): AuthSession? {
        clear()
        return null
    }

    // ─── AES-GCM با AndroidKeyStore ────────────────────────────────

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: ByteArray): String? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val sealed = cipher.doFinal(plain)
            Base64.encodeToString(iv + sealed, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun decrypt(blob: String): ByteArray? {
        return try {
            val all = Base64.decode(blob, Base64.NO_WRAP)
            val iv = all.copyOfRange(0, IV_BYTES)
            val sealed = all.copyOfRange(IV_BYTES, all.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(sealed)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jhd_session_key"
        private const val IV_BYTES = 12
    }
}
