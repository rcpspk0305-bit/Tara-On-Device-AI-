package dev.tara.settingsdata

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class TaraSettings(val theme: ThemeMode = ThemeMode.SYSTEM, val onboardingComplete: Boolean = false)

private val Context.taraDataStore by preferencesDataStore(
    name = "tara_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.taraDataStore
    val settings: Flow<TaraSettings> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        TaraSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == preferences[Theme] } ?: ThemeMode.SYSTEM,
            onboardingComplete = preferences[OnboardingComplete] ?: false,
        )
    }

    suspend fun setTheme(mode: ThemeMode) { store.edit { it[Theme] = mode.name } }
    suspend fun completeOnboarding() { store.edit { it[OnboardingComplete] = true } }
    suspend fun resetOnboarding() { store.edit { it[OnboardingComplete] = false } }

    private companion object {
        val Theme = stringPreferencesKey("theme")
        val OnboardingComplete = booleanPreferencesKey("onboarding_complete")
    }
}
