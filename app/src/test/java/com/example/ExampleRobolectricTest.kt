package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Ocarina", appName)
  }

  @Test
  fun `test listening time tracking in preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.AppPreferences(context)
    prefs.resetListeningTime()
    assertEquals(0L, prefs.totalListeningSeconds.value)

    prefs.addListeningSeconds(45L)
    assertEquals(45L, prefs.totalListeningSeconds.value)

    prefs.addListeningSeconds(60L)
    assertEquals(105L, prefs.totalListeningSeconds.value)

    prefs.resetListeningTime()
    assertEquals(0L, prefs.totalListeningSeconds.value)
  }
}
