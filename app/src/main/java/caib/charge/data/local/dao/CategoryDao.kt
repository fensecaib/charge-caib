package caib.charge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import caib.charge.data.local.entity.CategoryEntity
import caib.charge.domain.model.EntryType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE (:type IS NULL OR type = :type) AND (:includeArchived = 1 OR is_archived = 0) ORDER BY type, sort_order, name")
    fun observe(type: EntryType?, includeArchived: Boolean): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type, sort_order, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE type = :type AND is_archived = 0 ORDER BY sort_order, name")
    suspend fun getActiveByType(type: EntryType): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories WHERE type = :type AND normalized_name = :normalizedName AND id != :excludeId")
    suspend fun countNamed(type: EntryType, normalizedName: String, excludeId: String): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM categories WHERE type = :type AND is_archived = 0")
    suspend fun maxActiveSortOrder(type: EntryType): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity): Int

    @Query("UPDATE categories SET sort_order = :sortOrder, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long): Int
}
