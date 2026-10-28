package caib.charge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import caib.charge.data.local.entity.SubscriptionPeriodEntity
import caib.charge.data.local.entity.SubscriptionProjectEntity
import caib.charge.data.local.entity.SubscriptionProjectWithPeriods
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Transaction
    @Query("SELECT * FROM subscription_projects ORDER BY id")
    fun observeProjects(): Flow<List<SubscriptionProjectWithPeriods>>

    @Transaction
    @Query("SELECT * FROM subscription_projects WHERE id = :id")
    suspend fun getProject(id: Long): SubscriptionProjectWithPeriods?

    @Query("SELECT * FROM subscription_projects WHERE id = :id")
    suspend fun getProjectRecord(id: Long): SubscriptionProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProject(project: SubscriptionProjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPeriod(period: SubscriptionPeriodEntity): Long

    @Update
    suspend fun updatePeriod(period: SubscriptionPeriodEntity): Int

    @Query("DELETE FROM subscription_periods WHERE project_id = :projectId AND id = :periodId")
    suspend fun deletePeriod(projectId: Long, periodId: Long): Int

    @Query("UPDATE subscription_projects SET name = :name, note = :note, is_stopped = :stopped, revision = revision + 1, updated_at = :now WHERE id = :id AND revision = :expectedRevision")
    suspend fun updateProject(id: Long, expectedRevision: Long, name: String, note: String, stopped: Boolean, now: Long): Int

    @Query("UPDATE subscription_projects SET icon_key = :iconKey, color_key = :colorKey, revision = revision + 1, updated_at = :now WHERE id = :id AND revision = :expectedRevision")
    suspend fun updateAppearance(id: Long, expectedRevision: Long, iconKey: String, colorKey: Int, now: Long): Int

    @Query("DELETE FROM subscription_projects WHERE id = :id AND revision = :expectedRevision")
    suspend fun deleteProject(id: Long, expectedRevision: Long): Int
}
