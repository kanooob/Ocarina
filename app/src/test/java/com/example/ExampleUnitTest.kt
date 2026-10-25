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
}
