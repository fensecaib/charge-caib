package caib.charge.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import caib.charge.data.preferences.DefaultThemeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.caibAppearanceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DefaultThemeRepository.DATA_STORE_NAME,
)

@Module
@InstallIn(SingletonComponent::class)
object ThemePreferencesModule {
    @Provides
    @Singleton
    fun provideThemePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.caibAppearanceDataStore
}
