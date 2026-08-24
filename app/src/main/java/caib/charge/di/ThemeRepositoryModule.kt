package caib.charge.di

import caib.charge.data.preferences.DefaultThemeRepository
import caib.charge.domain.repository.ThemeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ThemeRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindThemeRepository(implementation: DefaultThemeRepository): ThemeRepository
}
