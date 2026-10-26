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
                .header("User-Agent", "Ocarina/1.0 (Android)")
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

                val metaString = decodeMetadataBytes(metaBuffer, bytesRead)
                val matcher = titlePattern.matcher(metaString)
                if (matcher.find()) {
                    val rawTitle = matcher.group(1)?.trim()
                    if (!rawTitle.isNullOrEmpty()) {
                        return@withContext sanitizeTitle(rawTitle)
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

    private fun decodeMetadataBytes(buffer: ByteArray, length: Int): String {
        // Attempt UTF-8 first
        val utf8 = String(buffer, 0, length, Charsets.UTF_8)
        // If UTF-8 produced replacement characters or common UTF-8 mojibake, fall back to Windows-1252 / ISO-8859-1
        if (utf8.contains('\uFFFD') || (utf8.contains("Ã©") || utf8.contains("Ã¨") || utf8.contains("Ã "))) {
            return try {
                String(buffer, 0, length, java.nio.charset.Charset.forName("Windows-1252"))
            } catch (e: Exception) {
                String(buffer, 0, length, Charsets.ISO_8859_1)
            }
        }
        return utf8
    }

    fun sanitizeTitle(title: String): String {
        var result = title.trim()
        result = result.replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&#34;", "\"")
            .replace("&#38;", "&")
        // Remove trailing or leading quotes
        if (result.startsWith("\"") && result.endsWith("\"") && result.length > 2) {
            result = result.substring(1, result.length - 1).trim()
        }
        return result.replace(Regex("\\s+"), " ")
    }
}
