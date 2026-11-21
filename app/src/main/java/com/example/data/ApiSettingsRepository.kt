package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "viral_clipper_settings"
)

data class GeminiSettings(
    val apiKey: String = "",
    val modelName: String = DEFAULT_GEMINI_MODEL
) {
    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        val PRESET_MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-pro"
        )
    }
}

class ApiSettingsRepository(private val context: Context) {

    private object Keys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
    }

    val settingsFlow: Flow<GeminiSettings> = context.settingsDataStore.data.map { prefs ->
        GeminiSettings(
            apiKey = prefs[Keys.GEMINI_API_KEY].orEmpty(),
            modelName = prefs[Keys.GEMINI_MODEL]?.takeIf { it.isNotBlank() }
                ?: GeminiSettings.DEFAULT_GEMINI_MODEL
        )
    }

    suspend fun saveSettings(apiKey: String, modelName: String) {
        val cleanKey = apiKey.trim()
        val cleanModel = modelName.trim().ifBlank { GeminiSettings.DEFAULT_GEMINI_MODEL }
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.GEMINI_API_KEY] = cleanKey
            prefs[Keys.GEMINI_MODEL] = cleanModel
        }
    }

    suspend fun clearApiKey() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.GEMINI_API_KEY)
        }
    }
}
