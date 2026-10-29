package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class OnlineRadio(
    val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val bitrateKbps: Int,
    val codec: String,
    val favicon: String,
    val country: String,
    val tags: String,
    val votes: Int = 0
)

/**
 * Service client pour l'annuaire libre et open-source RadioBrowser (radio-browser.info).
 * Permet la découverte de webradios mondiales avec sélection automatique du flux audio
 * ayant la meilleure qualité disponible (débit / bitrate le plus élevé).
 */
object RadioBrowserService {

    private const val TAG = "RadioBrowserService"

    // Miroirs communautaires officiels pour haute disponibilité
    private val SERVERS = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Recherche des stations par mot-clé (nom, pays ou tags)
     * et groupe par station pour ne retenir que le flux avec la meilleure qualité audio disponible.
     */
    suspend fun searchRadios(query: String, limit: Int = 50): List<OnlineRadio> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val encoded = try {
            URLEncoder.encode(trimmed, "UTF-8")
        } catch (e: Exception) {
            trimmed
        }

        for (server in SERVERS) {
            try {
                val url = "$server/json/stations/search?name=$encoded&limit=$limit&hidebroken=true&order=clickcount&reverse=true"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Ocarina/1.0 (Android Open Source Webradio)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val parsed = parseAndSelectBestQuality(body)
                    if (parsed.isNotEmpty()) {
                        return@withContext parsed
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur sur le miroir RadioBrowser $server: ${e.message}")
            }
        }
        return@withContext emptyList()
    }

    /**
     * Récupère les radios les plus populaires du moment sur l'annuaire RadioBrowser.
     */
    suspend fun getTopStations(limit: Int = 20): List<OnlineRadio> = withContext(Dispatchers.IO) {
        for (server in SERVERS) {
            try {
                val url = "$server/json/stations/topclick?limit=$limit&hidebroken=true"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Ocarina/1.0 (Android Open Source Webradio)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val parsed = parseAndSelectBestQuality(body)
                    if (parsed.isNotEmpty()) {
                        return@withContext parsed
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erreur sur le miroir topclick $server: ${e.message}")
            }
        }
        return@withContext emptyList()
    }

    /**
     * Analyse le JSON et sélectionne le meilleur flux audio pour chaque nom de station.
     */
    private fun parseAndSelectBestQuality(jsonString: String): List<OnlineRadio> {
        val jsonArray = JSONArray(jsonString)
        val rawList = mutableListOf<OnlineRadio>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val uuid = obj.optString("stationuuid", "")
            val name = obj.optString("name", "").trim()
            val rawStream = obj.optString("url_resolved", "").ifBlank { obj.optString("url", "") }.trim()
            val bitrate = obj.optInt("bitrate", 0)
            val codec = obj.optString("codec", "MP3").uppercase()
            val favicon = obj.optString("favicon", "").trim()
            val country = obj.optString("country", "").trim()
            val tags = obj.optString("tags", "").trim()
            val votes = obj.optInt("votes", 0)

            if (name.isNotBlank() && rawStream.startsWith("http", ignoreCase = true)) {
                rawList.add(
                    OnlineRadio(
                        stationUuid = uuid,
                        name = name,
                        streamUrl = rawStream,
                        bitrateKbps = bitrate,
                        codec = codec,
                        favicon = favicon,
                        country = country,
                        tags = tags,
                        votes = votes
                    )
                )
            }
        }

        if (rawList.isEmpty()) return emptyList()

        // Normalisation des noms pour regrouper les doublons (ex: "FIP (192k)" et "FIP 128k" -> "FIP")
        val cleanRegex = Regex("""(?i)\s*[\(\[]?\b(320k|256k|192k|128k|96k|64k|32k|aac|mp3|ogg|flac|hifi|hd|stream|direct)\b[\)\]]?""")

        val grouped = rawList.groupBy {
            it.name.replace(cleanRegex, "").trim().lowercase()
        }

        val bestRadios = grouped.values.map { duplicates ->
            // Sélectionne le flux avec le plus fort bitrate disponible
            duplicates.maxWithOrNull(
                compareBy<OnlineRadio> { it.bitrateKbps }
                    .thenBy { if (it.streamUrl.startsWith("https://", ignoreCase = true)) 1 else 0 }
                    .thenBy { it.votes }
            ) ?: duplicates.first()
        }

        // Tri : les radios avec débit ou popularité élevée en tête
        return bestRadios.sortedWith(
            compareByDescending<OnlineRadio> { it.bitrateKbps > 0 }
                .thenByDescending { it.bitrateKbps }
                .thenBy { it.name.lowercase() }
        )
    }
}
