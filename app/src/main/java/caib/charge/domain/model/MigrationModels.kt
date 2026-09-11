package caib.charge.domain.model

object MigrationLimits {
    const val MAX_CODE_CHARACTERS = 100_000
    const val MAX_PASTED_CHARACTERS = MAX_CODE_CHARACTERS + 4096
    const val MAX_RAW_BYTES = 8 * 1024 * 1024
    const val MAX_ROWS = 50_000
    const val MAX_CATEGORIES = 2000
}

data class MigrationCategory(
    val builtInId: String, val type: EntryType, val name: String,
    val icon: String, val color: String, val archived: Boolean,
)

data class MigrationEntry(
    val category: Int, val amount: Long, val day: Long, val note: String,
    val createdAt: Long, val updatedAt: Long,
)

data class MigrationData(
    val start: Long, val end: Long,
    val categories: List<MigrationCategory>, val entries: List<MigrationEntry>,
)

data class MigrationPreview(val data: MigrationData, val localToken: String, val replaced: Int)
