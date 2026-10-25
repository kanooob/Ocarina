package com.example.service

import com.example.data.RadioStation

sealed interface PlaybackStatus {
    object Idle : PlaybackStatus
    object Buffering : PlaybackStatus
    object Playing : PlaybackStatus
    object Paused : PlaybackStatus
    object NoNetwork : PlaybackStatus
    data class Reconnecting(val secondsLeft: Int) : PlaybackStatus
    data class Error(val message: String) : PlaybackStatus
}

data class PlayerState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val currentStation: RadioStation? = null,
    val currentTrackTitle: String = "",
    val isChangingSong: Boolean = false,
    val message: String? = null
)
