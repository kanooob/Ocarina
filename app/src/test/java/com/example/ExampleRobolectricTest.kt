package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    val circleName = context.getString(R.string.widget_circle_name)
    val rectangleName = context.getString(R.string.widget_rectangle_name)
    org.junit.Assert.assertTrue(circleName == "Ocarina Circle" || circleName == "Ocarina Cercle")
    assertEquals("Ocarina Rectangle", rectangleName)

    assertEquals("fr.galaxie.ocarina.widget.ACTION_TOGGLE", com.example.widget.OcarinaWidgetHelper.ACTION_TOGGLE)
    assertEquals("fr.galaxie.ocarina.widget.ACTION_NEXT", com.example.widget.OcarinaWidgetHelper.ACTION_NEXT)
  }

  @Test
  @Config(qualifiers = "fr")
  fun `test widget strings in french`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Ocarina Cercle", context.getString(R.string.widget_circle_name))
    assertEquals("Ocarina Rectangle", context.getString(R.string.widget_rectangle_name))
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

  @Test
  fun `test version 213 labels`() {
    val frStrings = com.example.ui.FrenchStrings
    val enStrings = com.example.ui.EnglishStrings
    org.junit.Assert.assertTrue(frStrings.appVersionLabel.contains("213"))
    org.junit.Assert.assertTrue(enStrings.appVersionLabel.contains("213"))
  }

  @Test
  fun `test media app intents and audio category`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pm = context.packageManager

    // Test that MainActivity responds to APP_MUSIC intent
    val musicIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
      addCategory(android.content.Intent.CATEGORY_APP_MUSIC)
    }
    val resolveList = pm.queryIntentActivities(musicIntent, 0)
    val hasOcarina = resolveList.any { it.activityInfo.packageName == context.packageName }
    org.junit.Assert.assertTrue("MainActivity should resolve CATEGORY_APP_MUSIC", hasOcarina)

    // Test that MediaButtonReceiver resolves ACTION_MEDIA_BUTTON
    val mediaButtonIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON)
    val receivers = pm.queryBroadcastReceivers(mediaButtonIntent, 0)
    val hasMediaReceiver = receivers.any { it.activityInfo.packageName == context.packageName }
    org.junit.Assert.assertTrue("MediaButtonReceiver should resolve ACTION_MEDIA_BUTTON", hasMediaReceiver)
  }
}
