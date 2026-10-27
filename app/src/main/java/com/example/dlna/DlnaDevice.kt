package com.example.dlna

data class DlnaDevice(
    val id: String,
    val name: String,
    val modelName: String = "",
    val manufacturer: String = "",
    val locationUrl: String,
    val controlUrl: String,
    val renderingControlUrl: String? = null,
    val iconUrl: String? = null
)
