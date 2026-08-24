package caib.charge.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import caib.charge.domain.model.CaibThemeMode
import caib.charge.domain.repository.ThemeRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DefaultThemeRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ThemeRepository {
    override val themeMode: Flow<CaibThemeMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> CaibThemeMode.fromStorageValue(preferences[THEME_MODE_KEY]) }
        .distinctUntilChanged()

    override suspend fun setThemeMode(mode: CaibThemeMode) {
        dataStore.edit { preferences -> preferences[THEME_MODE_KEY] = mode.storageValue }
    }

    internal companion object {
        const val DATA_STORE_NAME = "appearance_settings"
        const val THEME_MODE_KEY_NAME = "theme_mode"
        val THEME_MODE_KEY = stringPreferencesKey(THEME_MODE_KEY_NAME)
    }
}
