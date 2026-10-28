package caib.charge.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "subscription_projects")
data class SubscriptionProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String,
    @ColumnInfo(name = "is_stopped") val isStopped: Boolean,
    val revision: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "icon_key", defaultValue = "'auto'") val iconKey: String = "auto",
    @ColumnInfo(name = "color_key", defaultValue = "0") val colorKey: Int = 0,
)

@Entity(
    tableName = "subscription_periods",
    foreignKeys = [ForeignKey(
        entity = SubscriptionProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["project_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["project_id", "sort_order"], unique = true)],
)
data class SubscriptionPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "project_id") val projectId: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "start_epoch_day") val startEpochDay: Long,
    @ColumnInfo(name = "end_epoch_day") val endEpochDay: Long,
    @ColumnInfo(name = "duration_value") val durationValue: Int?,
    @ColumnInfo(name = "duration_unit") val durationUnit: String?,
    @ColumnInfo(name = "amount_cents") val amountCents: Long?,
    val note: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

data class SubscriptionProjectWithPeriods(
    @Embedded val project: SubscriptionProjectEntity,
    @Relation(parentColumn = "id", entityColumn = "project_id")
    val periods: List<SubscriptionPeriodEntity>,
)
