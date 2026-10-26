package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ArtworkRepository {

    private const val TAG = "ArtworkRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    // Cache up to 100 recent track artwork URLs
    private val memoryCache = LruCache<String, String>(100)

    /**
     * Searches for track album artwork with high-res 600x600 px priority.
     * Tries iTunes Search API first, then falls back to Deezer Public API.
     */
    suspend fun findArtworkUrl(trackTitle: String): String? = withContext(Dispatchers.IO) {
        val cleanQuery = sanitizeTrackTitleForSearch(trackTitle)
        if (cleanQuery.isBlank() || cleanQuery.length < 3) return@withContext null

        synchronized(memoryCache) {
            val cached = memoryCache.get(cleanQuery)
            if (cached != null) {
                return@withContext if (cached.isEmpty()) null else cached
            }
        }

        // 1. Try iTunes Search API (Free, no API key required)
        var artworkUrl = searchItunes(cleanQuery)

        // 2. Fallback to Deezer Search API if iTunes yields no results
        if (artworkUrl == null) {
            artworkUrl = searchDeezer(cleanQuery)
        }

        synchronized(memoryCache) {
            memoryCache.put(cleanQuery, artworkUrl ?: "")
        }

        return@withContext artworkUrl
    }

    private fun searchItunes(query: String): String? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Ocarina/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                val resultCount = json.optInt("resultCount", 0)
                if (resultCount > 0) {
                    val results = json.optJSONArray("results")
                    val firstItem = results?.optJSONObject(0)
                    val art100 = firstItem?.optString("artworkUrl100")
                    if (!art100.isNullOrBlank()) {
                        // Upgrade resolution to 600x600 for sharp display
                        return art100.replace("100x100bb.jpg", "600x600bb.jpg")
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "iTunes search failed for '$query': ${e.message}")
        }
        return null
    }

    private fun searchDeezer(query: String): String? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.deezer.com/search?q=$encodedQuery&limit=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Ocarina/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                val data = json.optJSONArray("data")
                if (data != null && data.length() > 0) {
                    val first = data.optJSONObject(0)
                    val album = first?.optJSONObject("album")
                    val coverXl = album?.optString("cover_xl")
                    val coverBig = album?.optString("cover_big")
                    val coverMed = album?.optString("cover_medium")
                    val chosen = when {
                        !coverXl.isNullOrBlank() -> coverXl
                        !coverBig.isNullOrBlank() -> coverBig
                        !coverMed.isNullOrBlank() -> coverMed
                        else -> null
                    }
                    if (chosen != null) return chosen
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Deezer search failed for '$query': ${e.message}")
        }
        return null
    }

    suspend fun fetchArtworkBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Ocarina/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val inputStream = response.body?.byteStream() ?: return@withContext null
                return@withContext BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to download artwork bitmap: ${e.message}")
            return@withContext null
        }
    }

    private fun sanitizeTrackTitleForSearch(raw: String): String {
        var clean = raw.trim()
        // Strip common suffixes
        clean = clean.replace(Regex("(?i)\\[(live|remastered|explicit|hq|hd|official|audio)\\]"), "")
        clean = clean.replace(Regex("(?i)\\((live|remastered|explicit|official video|radio edit)\\)"), "")
        // If there are multiple hyphens, keep only artist and title
        clean = clean.trim()
        return clean
    }
}
