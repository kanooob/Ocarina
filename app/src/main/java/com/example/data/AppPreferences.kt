package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppLanguage {
    SYSTEM,
    FRENCH,
    ENGLISH
}

enum class ArtworkResolution {
    DISABLED,
    RES_300,
    RES_600
}

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ondelive_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _skipStreamReload = MutableStateFlow(loadSkipStreamReload())
    val skipStreamReload: StateFlow<Boolean> = _skipStreamReload.asStateFlow()

    private val _artworkResolution = MutableStateFlow(loadArtworkResolution())
    val artworkResolution: StateFlow<ArtworkResolution> = _artworkResolution.asStateFlow()

    private val _totalListeningSeconds = MutableStateFlow(loadTotalListeningSeconds())
    val totalListeningSeconds: StateFlow<Long> = _totalListeningSeconds.asStateFlow()

    private val _recentSearches = MutableStateFlow(loadRecentSearches())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    private fun loadLanguage(): AppLanguage {
        val name = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name) ?: AppLanguage.SYSTEM.name
        return try {
            AppLanguage.valueOf(name)
        } catch (e: Exception) {
            AppLanguage.SYSTEM
        }
    }

    private fun loadSkipStreamReload(): Boolean {
        return prefs.getBoolean(KEY_SKIP_STREAM_RELOAD, true)
    }

    private fun loadArtworkResolution(): ArtworkResolution {
        val name = prefs.getString(KEY_ARTWORK_RESOLUTION, ArtworkResolution.RES_600.name) ?: ArtworkResolution.RES_600.name
        return try {
            ArtworkResolution.valueOf(name)
        } catch (e: Exception) {
            ArtworkResolution.RES_600
        }
    }

    private fun loadTotalListeningSeconds(): Long {
        return prefs.getLong(KEY_TOTAL_LISTENING_SECONDS, 0L)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, lang.name).apply()
        _language.value = lang
    }

    fun setSkipStreamReload(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_STREAM_RELOAD, enabled).apply()
        _skipStreamReload.value = enabled
    }

    fun setArtworkResolution(resolution: ArtworkResolution) {
        prefs.edit().putString(KEY_ARTWORK_RESOLUTION, resolution.name).apply()
        _artworkResolution.value = resolution
    }

    fun addListeningSeconds(seconds: Long) {
        val updated = _totalListeningSeconds.value + seconds
        prefs.edit().putLong(KEY_TOTAL_LISTENING_SECONDS, updated).apply()
        _totalListeningSeconds.value = updated
    }

    fun resetListeningTime() {
        prefs.edit().putLong(KEY_TOTAL_LISTENING_SECONDS, 0L).apply()
        _totalListeningSeconds.value = 0L
    }

    fun setTotalListeningSeconds(seconds: Long) {
        val nonNegative = seconds.coerceAtLeast(0L)
        prefs.edit().putLong(KEY_TOTAL_LISTENING_SECONDS, nonNegative).apply()
        _totalListeningSeconds.value = nonNegative
    }

    private fun loadRecentSearches(): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").map { it.trim() }.filter { it.isNotEmpty() }.take(3)
    }

    private fun saveRecentSearches(list: List<String>) {
        val raw = list.take(3).joinToString("|||")
        prefs.edit().putString(KEY_RECENT_SEARCHES, raw).apply()
    }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val result = current.take(3)
        saveRecentSearches(result)
        _recentSearches.value = result
    }

    fun removeRecentSearch(query: String) {
        val current = _recentSearches.value.toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }
        saveRecentSearches(current)
        _recentSearches.value = current
    }

    fun clearRecentSearches() {
        saveRecentSearches(emptyList())
        _recentSearches.value = emptyList()
    }

    companion object {
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_LANGUAGE = "key_language"
        private const val KEY_SKIP_STREAM_RELOAD = "key_skip_stream_reload"
        private const val KEY_ARTWORK_RESOLUTION = "key_artwork_resolution"
        private const val KEY_TOTAL_LISTENING_SECONDS = "key_total_listening_seconds"
        private const val KEY_RECENT_SEARCHES = "key_recent_searches"

        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = AppPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
