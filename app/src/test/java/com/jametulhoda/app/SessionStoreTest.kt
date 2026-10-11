package com.jametulhoda.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jametulhoda.app.data.AuthSession
import com.jametulhoda.app.data.SessionStore
import com.jametulhoda.app.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ذخیرهٔ امن نشست: رمزنگاری AES، بارگذاری، و حذف کامل هنگام خروج. */
@RunWith(RobolectricTestRunner::class)
class SessionStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun sampleSession() = AuthSession(
        token = "jh1_deadbeef",
        tokenType = "Bearer",
        expiresAt = "2026-12-01T00:00:00Z",
        user = UserProfile(
            id = 42,
            fullName = "کاربر آزمایشی",
            username = "user",
            email = "user@example.com",
            phone = "09120000000",
            country = "ایران",
            role = "user",
            mustChangePassword = false,
            createdAt = "2026-01-01 00:00:00",
        ),
    )

    @Test
    fun saveLoadClearRoundTrip() {
        val store = SessionStore(context)
        assertNull(store.load())

        store.save(sampleSession())

        val loaded = store.load()
        assertEquals("jh1_deadbeef", loaded?.token)
        assertEquals("کاربر آزمایشی", loaded?.user?.fullName)
        assertEquals("user@example.com", loaded?.user?.email)
        assertEquals(42, loaded?.user?.id)

        store.clear()
        assertNull(store.load())
    }

    @Test
    fun clearOnEmptyIsSafe() {
        val store = SessionStore(context)
        store.clear()
        assertNull(store.load())
    }
}
