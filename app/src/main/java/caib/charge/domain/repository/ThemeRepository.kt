package caib.charge.domain.repository

import caib.charge.domain.model.CaibThemeMode
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    val themeMode: Flow<CaibThemeMode>

    suspend fun setThemeMode(mode: CaibThemeMode)
}
