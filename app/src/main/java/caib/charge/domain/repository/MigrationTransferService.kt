package caib.charge.domain.repository

import caib.charge.domain.model.MigrationPreview
import java.time.LocalDate

interface MigrationTransferService {
    suspend fun export(start: LocalDate, end: LocalDate): Pair<String, Int>
    suspend fun preview(code: String): MigrationPreview
    suspend fun commit(preview: MigrationPreview): Int
}
