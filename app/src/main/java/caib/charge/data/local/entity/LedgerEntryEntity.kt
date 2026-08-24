package caib.charge.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import caib.charge.domain.model.EntrySource
import caib.charge.domain.model.EntryType

@Entity(
    tableName = "ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["import_batch_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["import_batch_id"]),
        Index(value = ["entry_date_epoch_day"]),
        Index(value = ["type", "entry_date_epoch_day"]),
        Index(value = ["content_fingerprint"]),
    ],
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EntryType,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "entry_date_epoch_day") val entryDateEpochDay: Long,
    val note: String,
    @ColumnInfo(name = "normalized_note") val normalizedNote: String,
    val source: EntrySource,
    @ColumnInfo(name = "import_batch_id") val importBatchId: String?,
    @ColumnInfo(name = "content_fingerprint") val contentFingerprint: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
