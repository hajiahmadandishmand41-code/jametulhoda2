package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("جامعه‌الهدی", appName)
  }

  @Test
  fun `official logo resource exists`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertNotNull("Official Jameatul Huda logo should be packaged", context.getDrawable(R.drawable.ic_jametulhoda_logo))
  }

  @Test
  fun `app declares internet permission for the published feed`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals(
      PackageManager.PERMISSION_GRANTED,
      context.packageManager.checkPermission(Manifest.permission.INTERNET, context.packageName)
    )
  }
}
