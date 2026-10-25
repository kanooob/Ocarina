package com.example.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object IcyMetadataExtractor {

    private const val TAG = "IcyMetadataExtractor"
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val titlePattern = Pattern.compile("StreamTitle='(.*?)';", Pattern.CASE_INSENSITIVE)

    suspend fun fetchCurrentTrackTitle(streamUrl: String): String? = withContext(Dispatchers.IO) {
        val cleanUrl = streamUrl.trim()
        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true))) {
            return@withContext null
        }
        try {
            val request = Request.Builder()
                .url(cleanUrl)
                .header("Icy-MetaData", "1")
                .header("User-Agent", "OndeLive/1.0 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val metaIntHeader = response.header("icy-metaint")
                val metaInt = metaIntHeader?.toIntOrNull() ?: return@withContext response.header("icy-name")

                val body = response.body ?: return@withContext null
                val stream: InputStream = body.byteStream()

                // Skip the first block of audio bytes up to metaInt
                var bytesSkipped = 0L
                while (bytesSkipped < metaInt) {
                    val skipped = stream.skip(metaInt - bytesSkipped)
                    if (skipped <= 0) {
                        val b = stream.read()
                        if (b == -1) return@withContext null
                        bytesSkipped += 1
                    } else {
                        bytesSkipped += skipped
                    }
                }

                // Read metadata length byte: length = byteValue * 16
                val lenByte = stream.read()
                if (lenByte <= 0) return@withContext null
                val metaLength = lenByte * 16
                val metaBuffer = ByteArray(metaLength)
                var bytesRead = 0
                while (bytesRead < metaLength) {
                    val read = stream.read(metaBuffer, bytesRead, metaLength - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }

                val metaString = String(metaBuffer, 0, bytesRead, Charsets.UTF_8)
                val matcher = titlePattern.matcher(metaString)
                if (matcher.find()) {
                    val title = matcher.group(1)?.trim()
                    if (!title.isNullOrEmpty()) {
                        return@withContext title
                    }
                }
            }
        } catch (e: java.net.UnknownHostException) {
            Log.d(TAG, "No network connection to fetch ICY metadata")
        } catch (e: java.net.ConnectException) {
            Log.d(TAG, "Connection failed to fetch ICY metadata: ${e.message}")
        } catch (e: Exception) {
            Log.d(TAG, "Could not fetch ICY metadata: ${e.message}")
        }
        return@withContext null
    }
}
