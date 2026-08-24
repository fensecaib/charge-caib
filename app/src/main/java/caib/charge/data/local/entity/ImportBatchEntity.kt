package caib.charge.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "import_batches",
    indices = [Index(value = ["file_sha256"])],
)
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_sha256") val fileSha256: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "total_rows") val totalRows: Int,
    @ColumnInfo(name = "success_rows") val successRows: Int,
    @ColumnInfo(name = "duplicate_rows") val duplicateRows: Int,
    @ColumnInfo(name = "invalid_rows") val invalidRows: Int,
)
