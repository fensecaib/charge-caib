package caib.charge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import caib.charge.ui.CaibApp
import caib.charge.ui.theme.CaibTheme
import caib.charge.ui.theme.ThemeUiState
import caib.charge.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeState by themeViewModel.uiState.collectAsStateWithLifecycle()
            val mode = (themeState as? ThemeUiState.Ready)?.mode ?: caib.charge.domain.model.CaibThemeMode.SYSTEM

            // SYSTEM matches every existing v1 install and avoids an empty launch frame while
            // DataStore performs its first read. A persisted override replaces it immediately.
            CaibTheme(themeMode = mode) {
                CaibApp(
                    themeMode = mode,
                    onThemeModeChange = themeViewModel::selectThemeMode,
                )
            }
        }
    }
}
