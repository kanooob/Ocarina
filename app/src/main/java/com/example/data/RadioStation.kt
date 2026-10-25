package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "radio_stations")
data class RadioStation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val streamUrl: String,
    val changeSongUrl: String = "",
    val isSelected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
