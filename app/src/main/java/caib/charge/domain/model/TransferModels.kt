package caib.charge.domain.model

import java.time.LocalDate

enum class ImportRowStatus {
    VALID,
    DUPLICATE,
    INVALID,
}

data class ImportPreviewRow(
    val rowNumber: Int,
    val entryDate: LocalDate? = null,
    val type: EntryType? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val amountCents: Long? = null,
    val note: String = "",
    val normalizedNote: String = "",
    val fingerprint: String? = null,
    val status: ImportRowStatus,
    val mappedToOther: Boolean = false,
    val message: String? = null,
) {
    val includedByDefault: Boolean get() = status == ImportRowStatus.VALID
}

data class ImportPreviewBatch(
    val fileName: String,
    val fileSha256: String,
    val detectedCharset: String,
    val delimiter: Char,
    val rows: List<ImportPreviewRow>,
) {
    val totalRows: Int get() = rows.size
    val validRows: Int get() = rows.count { it.status == ImportRowStatus.VALID }
    val duplicateRows: Int get() = rows.count { it.status == ImportRowStatus.DUPLICATE }
    val invalidRows: Int get() = rows.count { it.status == ImportRowStatus.INVALID }
}

data class ImportCommitResult(
    val batchId: String,
    val insertedRows: Int,
    val skippedDuplicateRows: Int,
    val invalidRows: Int,
)

sealed interface CsvExportScope {
    data object All : CsvExportScope
    data class Month(val year: Int, val month: Int) : CsvExportScope {
        init {
            require(year in EntryDateLimits.MIN_DATE.year..LocalDate.MAX.year) { "导出年份超出范围" }
            require(month in 1..12) { "导出月份必须在 1 到 12 之间" }
        }
    }

    data class Year(val year: Int) : CsvExportScope {
        init {
            require(year in EntryDateLimits.MIN_DATE.year..LocalDate.MAX.year) { "导出年份超出范围" }
        }
    }
}

data class CsvExportResult(
    val bytes: ByteArray,
    val rowCount: Int,
)
