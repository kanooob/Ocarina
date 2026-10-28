package com.example.data

import org.json.JSONArray
import org.json.JSONObject

data class ExportedStation(
    val name: String,
    val streamUrl: String,
    val changeSongUrl: String = ""
)

data class BackupData(
    val totalListeningSeconds: Long,
    val stations: List<ExportedStation>,
    val exportedAt: Long = System.currentTimeMillis()
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", exportedAt)
        root.put("totalListeningSeconds", totalListeningSeconds)
        root.put("dureeEcouteGlobaleSecondes", totalListeningSeconds)

        val stationsArray = JSONArray()
        for (station in stations) {
            val stationObj = JSONObject()
            stationObj.put("name", station.name)
            stationObj.put("streamUrl", station.streamUrl)
            stationObj.put("changeSongUrl", station.changeSongUrl)
            stationObj.put("skipUrl", station.changeSongUrl)
            stationsArray.put(stationObj)
        }
        root.put("stations", stationsArray)

        return root.toString(2)
    }

    companion object {
        fun fromJsonString(jsonString: String): BackupData {
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("[")) {
                // Direct array of stations
                val array = JSONArray(trimmed)
                val stations = parseStationsArray(array)
                return BackupData(totalListeningSeconds = 0L, stations = stations)
            }

            val root = JSONObject(trimmed)
            val totalSeconds = when {
                root.has("totalListeningSeconds") -> root.optLong("totalListeningSeconds", 0L)
                root.has("total_listening_seconds") -> root.optLong("total_listening_seconds", 0L)
                root.has("dureeEcouteGlobaleSecondes") -> root.optLong("dureeEcouteGlobaleSecondes", 0L)
                root.has("duree_ecoute_globale") -> root.optLong("duree_ecoute_globale", 0L)
                root.has("duree_ecoute_secondes") -> root.optLong("duree_ecoute_secondes", 0L)
                root.has("dureeEcoute") -> root.optLong("dureeEcoute", 0L)
                root.has("listeningSeconds") -> root.optLong("listeningSeconds", 0L)
                root.has("listening_seconds") -> root.optLong("listening_seconds", 0L)
                else -> 0L
            }

            val stationsArray = when {
                root.has("stations") -> root.optJSONArray("stations")
                root.has("radios") -> root.optJSONArray("radios")
                root.has("webradios") -> root.optJSONArray("webradios")
                else -> null
            } ?: JSONArray()

            val stations = parseStationsArray(stationsArray)
            val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

            return BackupData(
                totalListeningSeconds = totalSeconds.coerceAtLeast(0L),
                stations = stations,
                exportedAt = exportedAt
            )
        }

        private fun parseStationsArray(array: JSONArray): List<ExportedStation> {
            val list = mutableListOf<ExportedStation>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name").ifBlank {
                    item.optString("nom").ifBlank {
                        item.optString("stationName").ifBlank {
                            item.optString("station_name").ifBlank { "Station ${i + 1}" }
                        }
                    }
                }
                val streamUrl = item.optString("streamUrl").ifBlank {
                    item.optString("stream_url").ifBlank {
                        item.optString("url_flux").ifBlank {
                            item.optString("urlFlux").ifBlank {
                                item.optString("url")
                            }
                        }
                    }
                }
                val changeSongUrl = item.optString("changeSongUrl").ifBlank {
                    item.optString("change_song_url").ifBlank {
                        item.optString("skipUrl").ifBlank {
                            item.optString("skip_url").ifBlank {
                                item.optString("url_skip").ifBlank {
                                    item.optString("urlSkip")
                                }
                            }
                        }
                    }
                }
                if (streamUrl.isNotBlank()) {
                    list.add(
                        ExportedStation(
                            name = name.trim(),
                            streamUrl = streamUrl.trim(),
                            changeSongUrl = changeSongUrl.trim()
                        )
                    )
                }
            }
            return list
        }
    }
}
