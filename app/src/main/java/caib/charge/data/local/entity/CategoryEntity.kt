package caib.charge.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import caib.charge.domain.model.EntryType

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["type", "normalized_name"], unique = true),
        Index(value = ["type", "is_archived", "sort_order"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val type: EntryType,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "color_key") val colorKey: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
