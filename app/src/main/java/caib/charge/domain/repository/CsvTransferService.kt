package caib.charge.domain.repository

import caib.charge.domain.model.CsvExportResult
import caib.charge.domain.model.CsvExportScope
import caib.charge.domain.model.ImportCommitResult
import caib.charge.domain.model.ImportPreviewBatch

interface CsvTransferService {
    companion object {
        const val MAX_IMPORT_BYTES: Int = 5 * 1024 * 1024
    }

    suspend fun previewImport(fileName: String, bytes: ByteArray): ImportPreviewBatch

    /**
     * [selectedRowNumbers] defaults to all valid rows. Duplicate rows are only inserted when their
     * row number is present in both [selectedRowNumbers] and [duplicateOverrides].
     */
    suspend fun commitImport(
        batch: ImportPreviewBatch,
        selectedRowNumbers: Set<Int> = batch.rows.filter { it.includedByDefault }.mapTo(mutableSetOf()) { it.rowNumber },
        duplicateOverrides: Set<Int> = emptySet(),
    ): ImportCommitResult

    suspend fun export(scope: CsvExportScope = CsvExportScope.All): CsvExportResult
}
