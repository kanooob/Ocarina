package com.example.service

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object StreamUrlResolver {

    private const val TAG = "StreamUrlResolver"
    private const val TWITCH_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"

    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Pattern for Twitch channels (e.g., https://twitch.tv/username, twitch.tv/username, https://www.twitch.tv/username)
    private val TWITCH_CHANNEL_REGEX = Pattern.compile(
        """^(?:https?://)?(?:www\.|m\.)?twitch\.tv/([a-zA-Z0-9_]{3,30})(?:/.*)?$""",
        Pattern.CASE_INSENSITIVE
    )

    suspend fun resolveStreamUrl(rawUrl: String): String = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank()) return@withContext cleanUrl

        // Check if input is a Twitch channel URL
        val twitchMatcher = TWITCH_CHANNEL_REGEX.matcher(cleanUrl)
        if (twitchMatcher.matches()) {
            val channelName = twitchMatcher.group(1)?.lowercase()
            if (!channelName.isNullOrBlank() && !isTwitchReservedWord(channelName)) {
                Log.d(TAG, "Resolving Twitch live stream for channel '$channelName'")
                val twitchStreamUrl = resolveTwitchLiveStream(channelName)
                if (!twitchStreamUrl.isNullOrBlank()) {
                    return@withContext twitchStreamUrl
                }
            }
        }

        // If URL doesn't have http/https or rtsp protocol, return as is
        val lower = cleanUrl.lowercase()
        val hasScheme = lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("rtsp://")
        if (!hasScheme) {
            return@withContext cleanUrl
        }

        // RTSP, DASH (.mpd), and direct video files (.mp4, .mkv, .webm, .ts, .flv) are handled directly by ExoPlayer
        if (lower.startsWith("rtsp://") || lower.endsWith(".mpd") || lower.contains(".mpd?") ||
            lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") ||
            lower.endsWith(".ts") || lower.endsWith(".flv")) {
            return@withContext cleanUrl
        }

        // If it's an HLS stream (.m3u8), check if it has a direct audio-only stream we can extract
        if (lower.endsWith(".m3u8") || lower.contains(".m3u8?")) {
            val audioOnlyUrl = tryExtractAudioOnlyFromHls(cleanUrl)
            return@withContext audioOnlyUrl ?: cleanUrl
        }

        // If it looks like a playlist file (.pls or .m3u)
        val isPls = lower.endsWith(".pls") || lower.contains("/pls")
        val isM3u = (lower.endsWith(".m3u") && !lower.endsWith(".m3u8")) || lower.contains("/m3u")

        if (isPls || isM3u) {
            try {
                val request = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", "Ocarina/2.1 (Android; VLC Compatible)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val resolved = parsePlaylistBody(body)
                        if (!resolved.isNullOrBlank()) {
                            Log.d(TAG, "Resolved playlist $cleanUrl -> $resolved")
                            // Recursively check if resolved URL is Twitch or HLS audio-only
                            return@withContext resolveStreamUrl(resolved)
                        }
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                Log.d(TAG, "No network connection to resolve playlist for $cleanUrl")
            } catch (e: java.net.ConnectException) {
                Log.d(TAG, "Connection failed to resolve playlist for $cleanUrl: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve playlist file: ${e.message}")
            }
        }

        return@withContext cleanUrl
    }

    /**
     * Resolves a Twitch live channel to its HLS stream, prioritizing the 'audio_only' rendition.
     */
    private fun resolveTwitchLiveStream(channel: String): String? {
        try {
            // Step 1: Request playback access token from Twitch GraphQL
            val gqlUrl = "https://gql.twitch.tv/gql"
            val query = """query PlaybackAccessToken(${'$'}login: String!, ${'$'}playerType: String!) { streamPlaybackAccessToken(channelName: ${'$'}login, params: {platform: "web", playerBackend: "mediaplayer", playerType: ${'$'}playerType}) { value signature } }"""
            val jsonPayload = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject().apply {
                    put("login", channel)
                    put("playerType", "embed")
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            val tokenRequest = Request.Builder()
                .url(gqlUrl)
                .post(requestBody)
                .header("Client-Id", TWITCH_CLIENT_ID)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            var sig: String? = null
            var tokenValue: String? = null

            client.newCall(tokenRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Twitch GQL error code: ${response.code}")
                    return null
                }
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                val tokenObj = json.optJSONObject("data")?.optJSONObject("streamPlaybackAccessToken")
                sig = tokenObj?.optString("signature")
                tokenValue = tokenObj?.optString("value")
            }

            if (sig.isNullOrBlank() || tokenValue.isNullOrBlank()) {
                Log.w(TAG, "Could not obtain Twitch playback access token for '$channel'")
                return null
            }

            // Step 2: Fetch Usher master HLS playlist
            val encodedToken = URLEncoder.encode(tokenValue, "UTF-8")
            val usherUrl = "https://usher.ttvnw.net/api/channel/hls/$channel.m3u8" +
                    "?sig=$sig&token=$encodedToken&allow_source=true&allow_audio_only=true&fast_bread=true"

            val usherRequest = Request.Builder()
                .url(usherUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            client.newCall(usherRequest).execute().use { response ->
                if (response.code == 404) {
                    Log.i(TAG, "Twitch channel '$channel' is currently offline")
                    throw IllegalStateException("La chaîne Twitch '$channel' n'est pas en direct actuellement.")
                }
                if (!response.isSuccessful) {
                    Log.w(TAG, "Twitch Usher error HTTP ${response.code}")
                    return null
                }

                val playlistContent = response.body?.string() ?: return null
                val audioOnlyStream = extractAudioOnlyFromTwitchPlaylist(playlistContent)
                if (!audioOnlyStream.isNullOrBlank()) {
                    Log.d(TAG, "Selected dedicated Twitch audio_only stream for '$channel'")
                    return audioOnlyStream
                }

                // If dedicated audio_only line is not separated, return master usher URL
                return usherUrl
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving Twitch stream for '$channel': ${e.message}")
        }
        return null
    }

    /**
     * Extracts the specific 'audio_only' URL from a Twitch master playlist if present.
     */
    private fun extractAudioOnlyFromTwitchPlaylist(m3u8Content: String): String? {
        val lines = m3u8Content.lines()
        var isAudioOnlyNext = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) &&
                trimmed.contains("audio_only", ignoreCase = true)) {
                isAudioOnlyNext = true
                continue
            }
            if (isAudioOnlyNext && trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                return trimmed
            }
            if (isAudioOnlyNext && trimmed.startsWith("#EXT")) {
                isAudioOnlyNext = false
            }
        }
        return null
    }

    /**
     * Inspects a generic HLS master playlist to see if an explicit audio-only rendition URL exists.
     */
    private fun tryExtractAudioOnlyFromHls(masterUrl: String): String? {
        try {
            val request = Request.Builder()
                .url(masterUrl)
                .header("User-Agent", "Ocarina/2.1 (Android; VLC Compatible)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null

                // Check for explicit audio_only variant
                val lines = body.lines()
                var isAudioOnly = false
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) &&
                        (trimmed.contains("audio_only", ignoreCase = true) || (trimmed.contains("audio", ignoreCase = true) && !trimmed.contains("RESOLUTION")))) {
                        isAudioOnly = true
                        continue
                    }
                    if (isAudioOnly && trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                            trimmed
                        } else {
                            resolveRelativeUrl(masterUrl, trimmed)
                        }
                    }
                    if (isAudioOnly && trimmed.startsWith("#EXT")) {
                        isAudioOnly = false
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore, let ExoPlayer handle the original URL directly
        }
        return null
    }

    private fun resolveRelativeUrl(baseUrl: String, relative: String): String {
        return try {
            val baseUri = Uri.parse(baseUrl)
            val fullUri = Uri.withAppendedPath(baseUri.buildUpon().path(null).build(), relative)
            fullUri.toString()
        } catch (e: Exception) {
            baseUrl
        }
    }

    private fun parsePlaylistBody(content: String): String? {
        val lines = content.lines()
        // Check PLS format: File1=http://...
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("File", ignoreCase = true) && trimmed.contains("=")) {
                val url = trimmed.substringAfter("=").trim()
                if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                    return url
                }
            }
        }

        // Check M3U format: lines starting with http
        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("#") && (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true))) {
                return trimmed
            }
        }

        return null
    }

    private fun isTwitchReservedWord(word: String): Boolean {
        return word in listOf(
            "directory", "p", "videos", "downloads", "jobs", "turbo", "prime",
            "settings", "subscriptions", "inventory", "wallet", "login", "signup"
        )
    }
}
