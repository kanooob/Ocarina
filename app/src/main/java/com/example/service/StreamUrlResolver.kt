package com.example.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object StreamUrlResolver {

    private const val TAG = "StreamUrlResolver"
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun resolveStreamUrl(rawUrl: String): String = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank()) return@withContext cleanUrl

        // If URL doesn't have http/https protocol, return as is
        if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext cleanUrl
        }

        val lower = cleanUrl.lowercase()
        // If it's already an HLS stream, pass as is
        if (lower.endsWith(".m3u8")) {
            return@withContext cleanUrl
        }

        // If it looks like a playlist file (.pls or .m3u)
        val isPls = lower.endsWith(".pls") || lower.contains("/pls")
        val isM3u = (lower.endsWith(".m3u") && !lower.endsWith(".m3u8")) || lower.contains("/m3u")

        if (isPls || isM3u) {
            try {
                val request = Request.Builder()
                    .url(cleanUrl)
                    .header("User-Agent", "Ocarina/1.0 (VLC Compatible)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val resolved = parsePlaylistBody(body)
                        if (!resolved.isNullOrBlank()) {
                            Log.d(TAG, "Resolved playlist $cleanUrl -> $resolved")
                            return@withContext resolved
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
}
