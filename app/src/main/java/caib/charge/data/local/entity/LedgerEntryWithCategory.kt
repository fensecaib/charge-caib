package caib.charge.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class LedgerEntryWithCategory(
    @Embedded val entry: LedgerEntryEntity,
    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: CategoryEntity,
)
