package caib.charge.domain.model

/** User-selectable appearance mode. Persisted values are stable and independent of enum names. */
enum class CaibThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun resolveDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorageValue(value: String?): CaibThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
