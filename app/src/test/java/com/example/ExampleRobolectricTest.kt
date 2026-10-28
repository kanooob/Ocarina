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

  @Test
  fun `test widget strings and constants`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Ocarina Cercle", context.getString(R.string.widget_circle_name))
    assertEquals("Ocarina Rectangle", context.getString(R.string.widget_rectangle_name))

    assertEquals("fr.galaxie.ocarina.widget.ACTION_TOGGLE", com.example.widget.OcarinaWidgetHelper.ACTION_TOGGLE)
    assertEquals("fr.galaxie.ocarina.widget.ACTION_NEXT", com.example.widget.OcarinaWidgetHelper.ACTION_NEXT)
  }

  @Test
  fun `test dominant color extraction`() {
    val bitmap = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.RED)

    val color = com.example.widget.OcarinaWidgetHelper.extractDominantColor(bitmap)
    // Should return a valid non-zero color
    org.junit.Assert.assertNotEquals(0, color)
  }
}
