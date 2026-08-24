package caib.charge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import caib.charge.data.local.entity.ImportBatchEntity

@Dao
interface ImportBatchDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(batch: ImportBatchEntity)
}
