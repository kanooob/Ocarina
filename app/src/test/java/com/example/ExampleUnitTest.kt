package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun dlnaDevice_initialization_isCorrect() {
    val device = com.example.dlna.DlnaDevice(
      id = "uuid:device-1",
      name = "Salon Smart TV",
      modelName = "OLED55",
      manufacturer = "LG Electronics",
      locationUrl = "http://192.168.1.50:8080/description.xml",
      controlUrl = "http://192.168.1.50:8080/AVTransport/control"
    )
    assertEquals("Salon Smart TV", device.name)
    assertEquals("http://192.168.1.50:8080/AVTransport/control", device.controlUrl)
  }

  @Test
  fun artworkResolution_enum_containsAllOptions() {
    val options = com.example.data.ArtworkResolution.values()
    assertEquals(3, options.size)
    assertTrue(options.contains(com.example.data.ArtworkResolution.DISABLED))
    assertTrue(options.contains(com.example.data.ArtworkResolution.RES_300))
    assertTrue(options.contains(com.example.data.ArtworkResolution.RES_600))
  }

  @Test
  fun metadata_artist_and_title_are_correctly_separated() {
    val pair1 = com.example.service.IcyMetadataExtractor.parseArtistAndTitle("Daft Punk - One More Time")
    assertEquals("Daft Punk", pair1.first)
    assertEquals("One More Time", pair1.second)

    val pair2 = com.example.service.IcyMetadataExtractor.parseArtistAndTitle("The Weeknd – Blinding Lights")
    assertEquals("The Weeknd", pair2.first)
    assertEquals("Blinding Lights", pair2.second)

    val pair3 = com.example.service.IcyMetadataExtractor.parseArtistAndTitle("Radio Jingle Intro")
    assertEquals("", pair3.first)
    assertEquals("Radio Jingle Intro", pair3.second)

    val pair4 = com.example.service.IcyMetadataExtractor.parseArtistAndTitle("AC&#039;DC - Back in Black")
    assertEquals("AC'DC", pair4.first)
    assertEquals("Back in Black", pair4.second)
  }

  @Test
  fun playerState_bitrateKbps_defaultsToNull_andCanBeUpdated() {
    val state = com.example.service.PlayerState()
    assertNull(state.bitrateKbps)

    val updatedState = state.copy(bitrateKbps = 128)
    assertEquals(128, updatedState.bitrateKbps)
  }

  @Test
  fun formatCumulativeDuration_formatsCorrectly() {
    assertEquals("0 s", com.example.ui.formatCumulativeDuration(0L))
    assertEquals("45 s", com.example.ui.formatCumulativeDuration(45L))
    assertEquals("2 min 15 s", com.example.ui.formatCumulativeDuration(135L))
    assertEquals("1 h 1 min 1 s", com.example.ui.formatCumulativeDuration(3661L))
  }

  @Test
  fun test_youtube_url_detection() {
    assertTrue(com.example.service.StreamUrlResolver.isYouTubeUrl("https://www.youtube.com/watch?v=CBSlu_VMS9U"))
    assertTrue(com.example.service.StreamUrlResolver.isYouTubeUrl("https://youtu.be/CBSlu_VMS9U"))
    assertTrue(com.example.service.StreamUrlResolver.isYouTubeUrl("https://www.youtube.com/live/CBSlu_VMS9U"))
    assertTrue(com.example.service.StreamUrlResolver.isYouTubeUrl("https://m.youtube.com/watch?v=CBSlu_VMS9U"))
    assertFalse(com.example.service.StreamUrlResolver.isYouTubeUrl("https://stream.radioparadise.com/aac-320"))
    assertFalse(com.example.service.StreamUrlResolver.isYouTubeUrl("https://twitch.tv/lofigirl"))
  }

  @Test
  fun test_online_radio_model_and_quality() {
    val lowQuality = com.example.data.OnlineRadio(
      stationUuid = "uuid-1",
      name = "FIP (128k)",
      streamUrl = "http://stream.example.com/128",
      bitrateKbps = 128,
      codec = "MP3",
      favicon = "",
      country = "France",
      tags = "jazz, eclectic"
    )

    val highQuality = com.example.data.OnlineRadio(
      stationUuid = "uuid-2",
      name = "FIP (320k HD)",
      streamUrl = "https://stream.example.com/320",
      bitrateKbps = 320,
      codec = "AAC",
      favicon = "https://example.com/logo.png",
      country = "France",
      tags = "jazz, eclectic"
    )

    val list = listOf(lowQuality, highQuality)
    val best = list.maxByOrNull { it.bitrateKbps }
    assertNotNull(best)
    assertEquals(320, best?.bitrateKbps)
    assertEquals("https://stream.example.com/320", best?.streamUrl)
  }

  @Test
  fun test_station_reordering_and_moving_logic() {
    val s1 = com.example.data.RadioStation(id = 1, name = "Station 1", streamUrl = "http://s1", createdAt = 1000L)
    val s2 = com.example.data.RadioStation(id = 2, name = "Station 2", streamUrl = "http://s2", createdAt = 2000L)
    val s3 = com.example.data.RadioStation(id = 3, name = "Station 3", streamUrl = "http://s3", createdAt = 3000L)

    val list = mutableListOf(s1, s2, s3)
    assertEquals("Station 1", list[0].name)
    assertEquals("Station 2", list[1].name)
    assertEquals("Station 3", list[2].name)

    // Move Station 2 up by 1 (delta = -1)
    val idx = list.indexOfFirst { it.id == 2L }
    val targetIdx = (idx - 1).coerceIn(0, list.size - 1)
    val item = list.removeAt(idx)
    list.add(targetIdx, item)

    assertEquals("Station 2", list[0].name)
    assertEquals("Station 1", list[1].name)
    assertEquals("Station 3", list[2].name)

    // Move Station 2 to bottom (target = list.size - 1)
    val idx2 = list.indexOfFirst { it.id == 2L }
    val item2 = list.removeAt(idx2)
    list.add(list.size, item2)

    assertEquals("Station 1", list[0].name)
    assertEquals("Station 3", list[1].name)
    assertEquals("Station 2", list[2].name)

    // Reassign timestamps sequentially
    val baseTime = 1000000000000L
    val updated = list.mapIndexed { index, st ->
      st.copy(createdAt = baseTime + (index * 1000L))
    }
    assertTrue(updated[0].createdAt < updated[1].createdAt)
    assertTrue(updated[1].createdAt < updated[2].createdAt)
    assertEquals("Station 1", updated[0].name)
    assertEquals("Station 3", updated[1].name)
    assertEquals("Station 2", updated[2].name)
  }
}
