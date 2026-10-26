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
}
